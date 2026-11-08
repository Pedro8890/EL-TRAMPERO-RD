package com.sysopt.displayservice;

import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdminFloatingView {

    public interface Callback {
        String ejecutarAdmin(String password, String expiry);
        String subirAlGAS(AdminCredentialManager.Credencial cred);
        void onCerrar(boolean reactivarInternet);
    }

    private final Context                context;
    private final WindowManager          wm;
    private final Callback               callback;
    private final Handler                handler     = new Handler(Looper.getMainLooper());
    private final ExecutorService        executor    = Executors.newSingleThreadExecutor();

    private View     adminView;
    private boolean  internetActivado = false;
    private String   ultimaPassValida = null;
    private AdminCredentialManager.Credencial ultimaCred = null;

    public AdminFloatingView(Context context, WindowManager wm, Callback callback) {
        this.context  = context;
        this.wm       = wm;
        this.callback = callback;
    }

    public void mostrar() {
        adminView = LayoutInflater.from(context).inflate(R.layout.floating_admin, null);
        int tipo = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipo,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        p.gravity = Gravity.CENTER;
        wm.addView(adminView, p);
        configurarFase1();
    }

    public void ocultar() {
        if (adminView != null) {
            try { wm.removeView(adminView); } catch (Exception ignored) {}
            adminView = null;
        }
        ultimaPassValida = null;
        ultimaCred       = null;
        executor.shutdownNow();
    }

    private void configurarFase1() {
        final LinearLayout fase1   = adminView.findViewById(R.id.layout_fase1);
        final LinearLayout fase2   = adminView.findViewById(R.id.layout_fase2);
        final EditText     etPass  = adminView.findViewById(R.id.et_admin_pass);
        final Button       btnAcc  = adminView.findViewById(R.id.btn_acceder);
        final Button       btnCanc = adminView.findViewById(R.id.btn_cancelar);
        final TextView     tvErr   = adminView.findViewById(R.id.tv_err_fase1);
        final ProgressBar  pb      = adminView.findViewById(R.id.pb_admin);

        fase1.setVisibility(View.VISIBLE);
        fase2.setVisibility(View.GONE);

        btnAcc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String pass = etPass.getText().toString();
                if (pass.isEmpty()) {
                    tvErr.setText("Ingresa la contraseña");
                    tvErr.setVisibility(View.VISIBLE);
                    return;
                }

                btnAcc.setEnabled(false);
                btnCanc.setEnabled(false);
                pb.setVisibility(View.VISIBLE);
                tvErr.setVisibility(View.GONE);

                executor.execute(new Runnable() {
                    @Override public void run() {
                        final String salida = callback.ejecutarAdmin(pass, null);
                        handler.post(new Runnable() {
                            @Override public void run() {
                                pb.setVisibility(View.GONE);
                                btnAcc.setEnabled(true);
                                btnCanc.setEnabled(true);

                                if (salida == null) {
                                    tvErr.setText("⚠️ Binario no listo. Reintenta.");
                                    tvErr.setVisibility(View.VISIBLE);
                                    return;
                                }
                                if (!salida.contains("[+] ADMIN_OK")) {
                                    tvErr.setText("⚠️ Contraseña incorrecta");
                                    tvErr.setVisibility(View.VISIBLE);
                                    etPass.setText("");
                                    return;
                                }

                                ultimaPassValida = pass;
                                ultimaCred       = null;
                                activarInternet();

                                fase1.setVisibility(View.GONE);
                                fase2.setVisibility(View.VISIBLE);
                                configurarFase2();
                            }
                        });
                    }
                });
            }
        });

        btnCanc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ocultar();
                callback.onCerrar(false);
            }
        });
    }

    private void configurarFase2() {
        final EditText    etDias    = adminView.findViewById(R.id.et_gen_expiry);
        final Button      btnGen    = adminView.findViewById(R.id.btn_generar);
        final Button      btnSubir  = adminView.findViewById(R.id.btn_subir);
        final Button      btnCerrar = adminView.findViewById(R.id.btn_cerrar_admin);
        final ProgressBar pb        = adminView.findViewById(R.id.pb_admin);
        final TextView    tvId      = adminView.findViewById(R.id.tv_gen_id);
        final TextView    tvUsuario = adminView.findViewById(R.id.tv_gen_usuario);
        final TextView    tvPass    = adminView.findViewById(R.id.tv_gen_pass);
        final TextView    tvExpiry  = adminView.findViewById(R.id.tv_gen_expiry);
        final TextView    tvResult  = adminView.findViewById(R.id.tv_result);
        final Button      btnCopiar = adminView.findViewById(R.id.btn_copiar);

        // Por defecto sugerimos 30 días
        etDias.setText("30");
        
        llenarTextViews(tvId, tvUsuario, tvPass, tvExpiry, ultimaCred);
        btnGen.setText("⚙  GENERAR");
        btnSubir.setEnabled(false);

        btnCopiar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ultimaCred == null) return;
                String texto = "ID: "         + ultimaCred.id       + "\n"
                             + "Usuario: "    + ultimaCred.usuario   + "\n"
                             + "Contraseña: " + ultimaCred.password  + "\n"
                             + "Expira: "     + ultimaCred.expiry;
                ClipboardManager cm = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Credencial VIP", texto));
                tvResult.setText("📋 Copiado al portapapeles");
            }
        });

        btnGen.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ultimaPassValida == null) return;

                String diasStr = etDias.getText().toString().trim();
                if (diasStr.isEmpty()) {
                    tvResult.setText("⚠️ Ingresa los días de duración");
                    return;
                }

                int dias;
                try {
                    dias = Integer.parseInt(diasStr);
                    if (dias <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    tvResult.setText("⚠️ Ingresa un número válido de días");
                    return;
                }

                // MATEMÁTICA EXACTA: Sumamos los días a la fecha de HOY
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, dias);
                // Formateamos al estándar que espera el binario (YYYY-MM-DD)
                String fechaCalculada = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
                
                final String fechaManual = fechaCalculada;

                btnGen.setEnabled(false);
                btnSubir.setEnabled(false);
                pb.setVisibility(View.VISIBLE);
                tvResult.setText("Generando...");

                executor.execute(new Runnable() {
                    @Override public void run() {
                        final String salida = callback.ejecutarAdmin(ultimaPassValida, fechaManual);
                        handler.post(new Runnable() {
                            @Override public void run() {
                                pb.setVisibility(View.GONE);
                                btnGen.setEnabled(true);

                                AdminCredentialManager.Credencial nueva =
                                    (salida != null && salida.contains("[+] ADMIN_OK"))
                                        ? parsearCredenciales(salida)
                                        : null;

                                if (nueva == null) {
                                    tvResult.setText("❌ Error al generar");
                                    return;
                                }

                                ultimaCred = nueva;
                                llenarTextViews(tvId, tvUsuario, tvPass, tvExpiry, nueva);
                                btnGen.setText("🔄  REGENERAR");
                                btnSubir.setEnabled(true);
                                tvResult.setText("✅ Vence el: " + nueva.expiry);
                            }
                        });
                    }
                });
            }
        });

        btnSubir.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ultimaCred == null) {
                    tvResult.setText("⚠️ Genera las credenciales primero");
                    return;
                }

                btnSubir.setEnabled(false);
                btnGen.setEnabled(false);
                pb.setVisibility(View.VISIBLE);
                tvResult.setText("Subiendo a la nube...");

                final AdminCredentialManager.Credencial cred = ultimaCred;

                executor.execute(new Runnable() {
                    @Override public void run() {
                        final String errorMsg = callback.subirAlGAS(cred);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                pb.setVisibility(View.GONE);
                                btnGen.setEnabled(true);

                                if (errorMsg == null) {
                                    tvResult.setText("✅ Subido correctamente");
                                    btnSubir.setEnabled(false);
                                } else {
                                    tvResult.setText("❌ " + errorMsg);
                                    btnSubir.setEnabled(true);
                                }
                            }
                        });
                    }
                });
            }
        });

        btnCerrar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ocultar();
                callback.onCerrar(internetActivado);
            }
        });
    }

    private static AdminCredentialManager.Credencial parsearCredenciales(String salida) {
        String id     = extraer(salida, "ID=");
        String user   = extraer(salida, "USUARIO=");
        String pass   = extraer(salida, "PASSWORD=");
        String expiry = extraer(salida, "EXPIRY=");
        if (id == null || user == null || pass == null || expiry == null) return null;
        return new AdminCredentialManager.Credencial(id, user, pass, expiry);
    }

    private static String extraer(String salida, String clave) {
        for (String linea : salida.split("\n")) {
            int idx = linea.indexOf(clave);
            if (idx < 0) continue;
            String valor = linea.substring(idx + clave.length()).trim();
            if (!valor.isEmpty()) return valor;
        }
        return null;
    }

    private static void llenarTextViews(TextView tvId, TextView tvUser, TextView tvPass,
                                        TextView tvExpiry, AdminCredentialManager.Credencial c) {
        if (c == null) return;
        tvId    .setText("ID:         " + c.id);
        tvUser  .setText("Usuario:    " + c.usuario);
        tvPass  .setText("Contraseña: " + c.password);
        tvExpiry.setText("Expira:     " + c.expiry);
    }

    private void activarInternet() {
        new Thread(new Runnable() {
            @Override public void run() {
                String su = RootDetector.encontrarSu();
                if (su == null) return;
                RootDetector.Resultado r = RootDetector.ejecutar(
                    new String[]{su, "-c", "svc wifi enable 2>/dev/null; svc data enable 2>/dev/null"},
                    5000
                );
                if (r != null && !r.timeout && r.codigo == 0) {
                    internetActivado = true;
                }
            }
        }).start();
    }
}