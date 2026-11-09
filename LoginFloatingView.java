package com.sysopt.displayservice;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LoginFloatingView {

    public interface Callback {
        void onLoginExitoso(CredentialManager.SesionData data);
        void onCacheValido();
        void onCerrarLogin();
        void onAbrirAdmin();
    }

    private final Context           context;
    private final WindowManager     wm;
    private final CredentialManager credManager;
    private final Callback          callback;
    private final Handler           handler  = new Handler(Looper.getMainLooper());
    
    // CORRECCIÓN 7.2: Ya no es final, para poder recrearlo si se vuelve a abrir
    private ExecutorService         executor;

    private View loginView;

    public LoginFloatingView(Context context, WindowManager wm, CredentialManager cm, Callback callback) {
        this.context     = context;
        this.wm          = wm;
        this.credManager = cm;
        this.callback    = callback;
    }

    public void mostrar() {
        // Recreamos el executor si estaba apagado
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                final boolean cacheOk = credManager.sesionEnCache();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cacheOk) {
                            callback.onCacheValido();
                        } else {
                            mostrarVentana();
                        }
                    }
                });
            }
        });
    }

    public void ocultar() {
        if (loginView != null) {
            try { wm.removeView(loginView); } catch (Exception ignored) {}
            loginView = null;
        }
        // Destruimos el executor al cerrar
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void mostrarVentana() {
        if (loginView != null) return;
        loginView = LayoutInflater.from(context).inflate(R.layout.floating_login, null);

        int tipo = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipo,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        // CORRECCIÓN 8.2: Try-catch para evitar crashes si revocaron el permiso de overlay
        try {
            wm.addView(loginView, params);
        } catch (Exception e) {
            e.printStackTrace();
            loginView = null;
            callback.onCerrarLogin();
            return;
        }
        
        configurarUI();
    }

    private void configurarUI() {
        final EditText    etId       = loginView.findViewById(R.id.et_id);
        final EditText    etUsuario  = loginView.findViewById(R.id.et_usuario);
        final EditText    etPassword = loginView.findViewById(R.id.et_password);
        final Button      btnLogin   = loginView.findViewById(R.id.btn_login);
        final Button      btnCerrar  = loginView.findViewById(R.id.btn_cerrar_login);
        final Button      btnAdmin   = loginView.findViewById(R.id.btn_abrir_admin_login);
        final Button      btnPegar   = loginView.findViewById(R.id.btn_pegar_login);
        final ProgressBar pb         = loginView.findViewById(R.id.pb_login);
        final TextView    tvError    = loginView.findViewById(R.id.tv_error);

        // ── Lógica del botón PEGAR ──
        btnPegar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                
                // CORRECCIÓN 8.1: Null-check para evitar NullPointerException
                if (cm == null) {
                    tvError.setText("❌ Error al leer el portapapeles");
                    tvError.setTextColor(0xFFFF4444);
                    tvError.setVisibility(View.VISIBLE);
                    return;
                }

                if (cm.hasPrimaryClip() && cm.getPrimaryClip() != null) {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip.getItemCount() > 0) {
                        CharSequence clipText = clip.getItemAt(0).getText();
                        if (clipText == null) return;
                        String text = clipText.toString();
                        
                        // Detectar si es el formato del panel Admin
                        if (text.contains("ID:") && text.contains("Usuario:") && text.contains("Contraseña:")) {
                            try {
                                String[] lineas = text.split("\n");
                                String id = "", user = "", pass = "";
                                for (String l : lineas) {
                                    if (l.startsWith("ID:")) id = l.replace("ID:", "").trim();
                                    else if (l.startsWith("Usuario:")) user = l.replace("Usuario:", "").trim();
                                    else if (l.startsWith("Contraseña:")) pass = l.replace("Contraseña:", "").trim();
                                }
                                etId.setText(id);
                                etUsuario.setText(user);
                                etPassword.setText(pass);
                                tvError.setText("✅ Datos pegados");
                                tvError.setTextColor(0xFF00FF88);
                                tvError.setVisibility(View.VISIBLE);
                                return;
                            } catch (Exception ignored) {}
                        }
                        
                        // Si es texto normal, lo pega en el campo que tenga el foco
                        View focus = loginView.findFocus();
                        if (focus instanceof EditText) {
                            ((EditText) focus).setText(text);
                        } else {
                            etId.setText(text);
                        }
                    }
                } else {
                    tvError.setText("⚠️ Portapapeles vacío");
                    tvError.setTextColor(0xFFFF4444);
                    tvError.setVisibility(View.VISIBLE);
                }
            }
        });

        btnCerrar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ocultar();
                callback.onCerrarLogin();
            }
        });

        btnAdmin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                callback.onAbrirAdmin();
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String id       = etId.getText().toString().trim();
                final String usuario  = etUsuario.getText().toString().trim();
                final String password = etPassword.getText().toString().trim();

                if (id.isEmpty() || usuario.isEmpty() || password.isEmpty()) {
                    tvError.setText("Completa todos los campos");
                    tvError.setTextColor(0xFFFF4444);
                    tvError.setVisibility(View.VISIBLE);
                    return;
                }

                btnLogin.setEnabled(false);
                btnCerrar.setEnabled(false);
                btnAdmin.setEnabled(false);
                btnPegar.setEnabled(false);
                pb.setVisibility(View.VISIBLE);
                tvError.setVisibility(View.GONE);

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        final CredentialManager.Resultado resultado =
                            credManager.verificarYPreparar(id, usuario, password);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                pb.setVisibility(View.GONE);

                                if (resultado.exito) {
                                    ocultar();
                                    callback.onLoginExitoso(resultado.data);
                                } else {
                                    btnLogin.setEnabled(true);
                                    btnCerrar.setEnabled(true);
                                    btnAdmin.setEnabled(true);
                                    btnPegar.setEnabled(true);
                                    tvError.setText(resultado.error != null ? resultado.error : "Credenciales inválidas");
                                    tvError.setTextColor(0xFFFF4444);
                                    tvError.setVisibility(View.VISIBLE);
                                }
                            }
                        });
                    }
                });
            }
        });
    }
}