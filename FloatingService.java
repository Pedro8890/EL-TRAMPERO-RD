package com.sysopt.displayservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class FloatingService extends Service {

    private static final String CHANNEL_ID          = "sys_disp_svc";   // ← genérico
    private static final int    NOTIF_ID            = 1001;
    // El binario se ejecuta desde code_cache con nombre de hilo de kernel
    private static final String BINARY_NAME         = ".kworker";
    // Nombre del asset dentro del APK (renombrado a algo neutro)
    private static final String ASSET_NAME          = "gfx_config.bin";
    private static final int    ANDROID_O           = Build.VERSION_CODES.O;
    private static final int    ANDROID_M           = Build.VERSION_CODES.M;
    private static final String GAME_PACKAGE        = "com.dts.freefireth";
    // Delay antes de cortar internet: dar tiempo al juego para conectarse al servidor.
    // Cortar internet demasiado rápido es una firma de detección conocida.
    // 45 segundos = tiempo suficiente para entrar a lobby. Para mods cosméticos
    // (skins) se puede poner en Long.MAX_VALUE para no cortar nunca.
    private static final long   INTERNET_CUT_DELAY_MS = 1_000L;
    private static final int    DRAG_UMBRAL_PX        = 10;

    private WindowManager              windowManager;
    private View                       floatingView;
    private WindowManager.LayoutParams params;
    private ProgressBar                progressBar;
    private LoginFloatingView          loginFloatingView;
    private AdminFloatingView          adminFloatingView;
    private CredentialManager          credManager;
    private AdminCredentialManager     adminCredManager;

    private final Handler         mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private String binaryPath;

    private volatile boolean binarioListo        = false;
    private volatile boolean internetDesactivado = false;
    private volatile boolean adminAbierto        = false;
    private volatile String  urlGAS              = null;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        iniciarForeground();

        if (Build.VERSION.SDK_INT >= ANDROID_M && !Settings.canDrawOverlays(this)) {
            toast("⚠️ Activa el permiso 'Mostrar sobre otras apps'", Toast.LENGTH_LONG);
            stopSelf();
            return;
        }

        credManager      = new CredentialManager(this);
        adminCredManager = new AdminCredentialManager();
        
        binaryPath = new File(getCodeCacheDir(), BINARY_NAME).getAbsolutePath();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            toast("❌ No se pudo obtener WindowManager", Toast.LENGTH_LONG);
            stopSelf();
            return;
        }

        executor.execute(new Runnable() {
            @Override public void run() {
                prepararBinario();
                if (binarioListo) {
                    urlGAS = obtenerUrlGAS();
                    credManager.setGasUrl(urlGAS);
                }
            }
        });

        iniciarLoginView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (loginFloatingView != null) { loginFloatingView.ocultar(); loginFloatingView = null; }
        if (adminFloatingView != null) { adminFloatingView.ocultar(); adminFloatingView = null; }
        
        if (windowManager != null && floatingView != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) { e.printStackTrace(); }
            floatingView = null;
        }

        Thread limpieza = new Thread(new Runnable() {
            @Override
            public void run() {
                activarInternet();
                String su = RootDetector.encontrarSu();
                if (su != null) {
                    // Limpieza profunda (mata y borra)
                    RootDetector.ejecutar(new String[]{su, "-c", "pkill -f '" + binaryPath + "' 2>/dev/null; rm -f " + binaryPath}, 3000);
                }
            }
        });
        limpieza.start();
        try { limpieza.join(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        executor.shutdownNow();
    }

    private void iniciarLoginView() {
        loginFloatingView = new LoginFloatingView(
            this, windowManager, credManager,
            new LoginFloatingView.Callback() {
                @Override public void onLoginExitoso(final CredentialManager.SesionData data) {
                    loginFloatingView = null;
                    iniciarMenuPrincipal();
                    
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            credManager.crearCacheAsincrono(data);
                        }
                    });
                }
                @Override public void onCacheValido() { loginFloatingView = null; validarSesionYProceder(); }
                @Override public void onCerrarLogin() { loginFloatingView = null; stopSelf(); }
                @Override public void onAbrirAdmin() { abrirPanelAdmin(); }
            }
        );
        loginFloatingView.mostrar();
    }

    private void validarSesionYProceder() {
        executor.execute(new Runnable() {
            @Override public void run() {
                final boolean sesionValida = credManager.sesionEnCache();
                final String  expiry       = sesionValida ? credManager.getExpiry() : null;

                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        if (!sesionValida) {
                            toast("⚠️ Tu sesión VIP expiró. Vuelve a iniciar sesión.", Toast.LENGTH_LONG);
                            iniciarLoginView();
                        } else {
                            if (expiry != null) toast("✅ Sesión VIP activa hasta: " + expiry, Toast.LENGTH_SHORT);
                            iniciarMenuPrincipal();
                        }
                    }
                });
            }
        });
    }

    private void iniciarMenuPrincipal() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_menu, null);
        progressBar = floatingView.findViewById(R.id.progressBar);

        TextView txtTitulo = floatingView.findViewById(R.id.txt_tiempo);
        if (txtTitulo != null) txtTitulo.setText("🔥 EL TRAMPERO RD - FF NORMAL 🔥");

        TextView tvExpiry = floatingView.findViewById(R.id.tv_expiry);
        if (tvExpiry != null) {
            String expiry = credManager.getExpiry();
            if (expiry != null && !expiry.isEmpty()) {
                tvExpiry.setText("⏳ VIP hasta: " + expiry);
                tvExpiry.setVisibility(View.VISIBLE);
            }
        }

        int layoutFlag = (Build.VERSION.SDK_INT >= ANDROID_O)
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100; params.y = 100;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            e.printStackTrace();
            floatingView = null;
            toast("❌ No se pudo mostrar el menú flotante", Toast.LENGTH_LONG);
            stopSelf();
            return;
        }

        configurarArrastre();

        Button btnCerrar = floatingView.findViewById(R.id.btn_cerrar);
        if (btnCerrar != null) btnCerrar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { matarProcesoYSalir(); }
        });

        Button btnAdmin = floatingView.findViewById(R.id.btn_admin);
        if (btnAdmin != null) btnAdmin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { abrirPanelAdmin(); }
        });

        configurarSwitch(R.id.switch_arana_,     "0", "TRADICIONALES");
        configurarSwitch(R.id.switch_colores_v1, "1", "BLANCO");
        configurarSwitch(R.id.switch_colores_v2, "2", "TRANSPARENTE");
        configurarSwitch(R.id.switch_colores_v3, "3", "NEGRO");
        configurarSwitch(R.id.switch_colores_v4, "4", "AZUL MARINO");
        configurarSwitch(R.id.switch_colores_v5, "5", "DINAMICO");
        configurarSwitch(R.id.switch_colores_v6, "6", "ROSA");
        configurarSwitch(R.id.switch_colores_v7, "7", "🗾BERMUDA🗾");
        configurarSwitch(R.id.switch_colores_v8, "8", "🗺️TODOS LOS MAPAS🗺️");

        abrirJuego();

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try { Thread.sleep(INTERNET_CUT_DELAY_MS); } catch (InterruptedException ignored) {}
                if (!adminAbierto) desactivarInternet();
            }
        });
    }

    private void iniciarForeground() {
        try {
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= ANDROID_O) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel canal = new NotificationChannel(CHANNEL_ID, "Display Service", NotificationManager.IMPORTANCE_LOW);
                    canal.setShowBadge(false);
                    nm.createNotificationChannel(canal);
                }
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
            }

            PendingIntent reopen = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification notif = builder
                .setContentTitle("Display Service")
                .setContentText("Running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(reopen)
                .build();

            startForeground(NOTIF_ID, notif);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void configurarArrastre() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean arrastrando;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        arrastrando = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (!arrastrando && Math.abs(dx) < DRAG_UMBRAL_PX && Math.abs(dy) < DRAG_UMBRAL_PX) return true;
                        arrastrando = true;
                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;
                        if (windowManager != null && floatingView != null) windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!arrastrando) v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void abrirJuego() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(GAME_PACKAGE);
            if (launch == null) {
                toast("⚠️ Free Fire no está instalado", Toast.LENGTH_SHORT);
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            pi.send();
        } catch (Exception e) {
            toast("❌ No se pudo abrir el juego", Toast.LENGTH_SHORT);
        }
    }

    private void desactivarInternet() {
        String su = RootDetector.encontrarSu();
        if (su == null) return;
        RootDetector.Resultado r = RootDetector.ejecutar(new String[]{su, "-c", "svc wifi disable 2>/dev/null; svc data disable 2>/dev/null"}, 5000);
        if (r != null && !r.timeout && r.codigo == 0) {
            internetDesactivado = true;
            toast("🔴 Internet desactivado", Toast.LENGTH_SHORT);
        }
    }

    private void activarInternet() {
        if (!internetDesactivado) return;
        String su = RootDetector.encontrarSu();
        if (su == null) return;
        RootDetector.ejecutar(new String[]{su, "-c", "svc wifi enable 2>/dev/null; svc data enable 2>/dev/null"}, 5000);
        internetDesactivado = false;
        toast("🟢 Internet reactivado", Toast.LENGTH_SHORT);
    }

    private void prepararBinario() {
        /*
         * Escribimos directamente en code_cache con el nombre camuflado.
         * Ya no hay archivo intermedio en filesDir con nombre obvio "trampero".
         *
         * Nota: necesitamos "su chmod 755" porque code_cache pertenece a la app
         * pero el kernel exige permisos de ejecución explícitos para binarios.
         */
        File destFile = new File(getCodeCacheDir(), BINARY_NAME);
        InputStream in = null; FileOutputStream out = null;
        try {
            in  = getAssets().open(ASSET_NAME);           // ← lee "gfx_config.bin"
            out = new FileOutputStream(destFile);
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        } catch (Exception e) {
            toast("❌ No se pudo extraer el binario", Toast.LENGTH_LONG); return;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }

        String su = RootDetector.encontrarSu();
        if (su == null) { toast("❌ Root no detectado", Toast.LENGTH_LONG); return; }

        // Solo necesitamos chmod 755 — el archivo ya está en su destino final
        RootDetector.Resultado r = RootDetector.ejecutar(
            new String[]{su, "-c", "chmod 755 '" + binaryPath + "'"}, 5000);

        binarioListo = (r != null && !r.timeout && r.codigo == 0);
        if (!binarioListo) { toast("❌ No se pudo preparar el binario", Toast.LENGTH_LONG); }
    }

    private String obtenerUrlGAS() {
        String su = RootDetector.encontrarSu();
        if (su == null || !binarioListo) return null;
        
        String secKey = credManager.getSecurityKey();
        RootDetector.Resultado r = RootDetector.ejecutar(
            new String[]{su, "-c", "'" + binaryPath + "' --get-url '" + secKey + "'"}, 5000);
        
        if (r == null || r.timeout || !r.salida.contains("[+] URL=")) return null;
        
        for (String linea : r.salida.split("\n")) {
            if (linea.contains("[+] URL=")) {
                return linea.substring(linea.indexOf("URL=") + 4).trim();
            }
        }
        return null;
    }

    private String ejecutarComandoMod(String opcion) {
        String su = RootDetector.encontrarSu();
        if (su == null)    return "NO_ROOT";
        if (!binarioListo) return "NO_BINARIO";

        String secKey = credManager.getSecurityKey();
        
        // Ejecutamos el binario camuflado
        RootDetector.Resultado r = RootDetector.ejecutar(
            new String[]{su, "-c", "'" + binaryPath + "' " + opcion + " '" + secKey + "'"}, 20000);

        // Auto-borrado para no dejar rastro en el disco (aunque sea code_cache)
        // Lo volvemos a extraer la próxima vez que se use.
        RootDetector.ejecutar(new String[]{su, "-c", "rm -f '" + binaryPath + "'"}, 2000);

        if (r == null)    return "ERROR";
        if (r.timeout)    return "TIMEOUT";

        String salida = r.salida;
        if (salida.contains("Juego no encontrado") || salida.contains("not found"))
            return "NOT_FOUND";
        if (salida.contains("ACCESO DENEGADO"))
            return "SESION_EXPIRADA";
        if (salida.contains("[+] Activado"))
            return "OK";

        return "PATRON_NO_ENCONTRADO";
    }

    private String ejecutarComandoAdmin(String password, String expiry) {
        String su = RootDetector.encontrarSu();
        if (su == null || !binarioListo) return null;

        String passEscapada = password.replace("'", "'\\''");
        String comando = "'" + binaryPath + "' --admin '" + passEscapada + "'";
        
        if (expiry != null && !expiry.trim().isEmpty()) {
            comando += " '" + expiry.trim().replace("'", "") + "'";
        }

        RootDetector.Resultado r = RootDetector.ejecutar(new String[]{su, "-c", comando}, 15000);
        
        // Auto-borrado
        RootDetector.ejecutar(new String[]{su, "-c", "rm -f '" + binaryPath + "'"}, 2000);

        if (r == null || r.timeout) return null;
        return r.salida;
    }

    private void configurarSwitch(int id, final String opcion, final String nombre) {
        final Switch sw = floatingView.findViewById(id);
        if (sw == null) return;

        final CompoundButton.OnCheckedChangeListener[] holder = new CompoundButton.OnCheckedChangeListener[1];
        holder[0] = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean activo) {
                if (!activo) {
                    sw.setText(nombre);
                    return;
                }
                progressBar.setVisibility(View.VISIBLE);
                sw.setText(nombre + " [Activando...]");

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        // Si el binario fue borrado por seguridad, lo volvemos a preparar
                        if (!new File(binaryPath).exists()) {
                            prepararBinario();
                        }
                        
                        final String res = ejecutarComandoMod(opcion);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                if ("OK".equals(res)) {
                                    sw.setText(nombre + " [🔥 ACTIVADO]");
                                } else {
                                    sw.setOnCheckedChangeListener(null);
                                    sw.setChecked(false);
                                    sw.setOnCheckedChangeListener(holder[0]);
                                    sw.setText(nombre);

                                    String msg;
                                    if ("NO_ROOT".equals(res)) msg = "❌ Root no detectado";
                                    else if ("NO_BINARIO".equals(res)) msg = "⏳ Binario no listo. Espera un momento.";
                                    else if ("NOT_FOUND".equals(res)) msg = "⚠️ Abre Free Fire primero";
                                    else if ("TIMEOUT".equals(res)) msg = "⏱️ Timeout — reinicia la app";
                                    else if ("PATRON_NO_ENCONTRADO".equals(res)) msg = "⚠️ Patron no encontrado — actualiza los codigos";
                                    else if ("SESION_EXPIRADA".equals(res)) msg = "⚠️ Sesión VIP expirada. Reinicia la app.";
                                    else msg = "❌ Error al aplicar: " + nombre;
                                    
                                    Toast.makeText(FloatingService.this, msg, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                });
            }
        };
        sw.setOnCheckedChangeListener(holder[0]);
    }

    private void matarProcesoYSalir() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String su = RootDetector.encontrarSu();
                if (su != null) {
                    RootDetector.ejecutar(new String[]{su, "-c", "rm -f '" + binaryPath + "'"}, 3000);
                    RootDetector.ejecutar(new String[]{su, "-c", "pkill -f '" + binaryPath + "'"}, 3000);
                }
                activarInternet();
                mainHandler.post(new Runnable() {
                    @Override public void run() { stopSelf(); }
                });
            }
        });
    }

    private void abrirPanelAdmin() {
        if (adminFloatingView != null) return;
        
        adminAbierto = true; 

        adminFloatingView = new AdminFloatingView(
            this, windowManager,
            new AdminFloatingView.Callback() {
                @Override
                public String ejecutarAdmin(String password, String expiry) {
                    if (!new File(binaryPath).exists()) prepararBinario();
                    return ejecutarComandoAdmin(password, expiry);
                }
                @Override
                public String subirAlGAS(AdminCredentialManager.Credencial cred) {
                    if (urlGAS == null) return "Espera un momento. El servidor se está preparando.";
                    return adminCredManager.subirAlGAS(urlGAS, cred);
                }
                @Override
                public void onCerrar(boolean reactivarInternet) {
                    adminFloatingView = null;
                    adminAbierto = false; 
                    if (reactivarInternet) {
                        executor.execute(new Runnable() {
                            @Override public void run() { desactivarInternet(); }
                        });
                    }
                }
            }
        );
        adminFloatingView.mostrar();
    }

    private void toast(final String msg, final int dur) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() { Toast.makeText(FloatingService.this, msg, dur).show(); }
        });
    }
}