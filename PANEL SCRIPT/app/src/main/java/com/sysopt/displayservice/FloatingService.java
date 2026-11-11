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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public class FloatingService extends Service {

    private static final String CHANNEL_ID          = "sys_disp_svc";
    private static final int    NOTIF_ID            = 1001;
    
    private static final String DEV_DIR             = "/dev";
    private static final String BINARY_PREFIX       = ".sys_";
    
    private static final String ASSET_NAME          = "gfx_config.bin";
    private static final int    ANDROID_O           = Build.VERSION_CODES.O;
    private static final int    ANDROID_M           = Build.VERSION_CODES.M;
    private static final String GAME_PACKAGE        = "com.dts.freefireth";
    
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
    private final List<Switch>    allSwitches = new ArrayList<>();
    
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                activarInternet();
                String su = RootDetector.encontrarSu();
                if (su != null && binaryPath != null) {
                    // CORRECCIÓN 2.4: Solo rm -f, sin pkill
                    RootDetector.ejecutar(new String[]{su, "-c", "rm -f " + binaryPath}, 3000);
                }
            }
        }).start();

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
        allSwitches.clear();

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

    // CORRECCIÓN 2.1: Enviar binario por stdin sin tocar el disco
    private void prepararBinario() {
        binaryPath = DEV_DIR + "/" + BINARY_PREFIX + Long.toHexString(System.nanoTime());
        
        String su = RootDetector.encontrarSu();
        if (su == null) { toast("❌ Root no detectado", Toast.LENGTH_LONG); return; }

        Process proceso = null;
        try {
            proceso = new ProcessBuilder(su).redirectErrorStream(true).start();
            OutputStream os = proceso.getOutputStream();
            
            os.write(("cat > " + binaryPath + " && chmod 700 " + binaryPath + "\n").getBytes());
            os.flush();

            InputStream in = getAssets().open(ASSET_NAME);
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
            in.close();
            os.close();

            boolean ok = proceso.waitFor(10, TimeUnit.SECONDS) && proceso.exitValue() == 0;
            binarioListo = ok;
            if (!ok) toast("❌ No se pudo preparar el binario", Toast.LENGTH_LONG);
        } catch (Exception e) {
            binarioListo = false;
            toast("❌ No se pudo preparar el binario", Toast.LENGTH_LONG);
        } finally {
            if (proceso != null) proceso.destroy();
        }
    }

    // CORRECCIÓN 2.2: obtenerUrlGAS por stdin
    private String obtenerUrlGAS() {
        if (!binarioListo || binaryPath == null) return null;
        String su = RootDetector.encontrarSu();
        if (su == null) return null;
        
        String secKey = SecurityManager.getSecurityKey(this);
        String comando = "exec " + binaryPath + " --get-url '" + secKey + "'\n";
        
        String salida = ejecutarConStdin(su, comando, 5000);
        if (salida == null) return null;

        for (String linea : salida.split("\n")) {
            if (linea.contains("[+] URL=")) {
                return linea.substring(linea.indexOf("[+] URL=") + 8).trim();
            }
        }
        return null;
    }

    // CORRECCIÓN 2.3 y 2.4: Helper con timeout real
    private String ejecutarConStdin(String su, String comando, long timeoutMs) {
        Process proceso = null;
        try {
            proceso = new ProcessBuilder(su).redirectErrorStream(true).start();
            OutputStream os = proceso.getOutputStream();
            os.write(comando.getBytes());
            os.flush();
            os.close();

            final ByteArrayOutputStream salida = new ByteArrayOutputStream();
            final InputStream is = proceso.getInputStream();
            Thread lector = new Thread(new Runnable() {
                @Override public void run() {
                    try {
                        byte[] buf = new byte[4096]; int n;
                        while ((n = is.read(buf)) != -1) salida.write(buf, 0, n);
                    } catch (Exception ignored) {}
                }
            });
            lector.setDaemon(true);
            lector.start();

            long limite = System.currentTimeMillis() + timeoutMs;
            boolean terminado = false;
            while (System.currentTimeMillis() < limite) {
                try { proceso.exitValue(); terminado = true; break; }
                catch (IllegalThreadStateException e) { 
                    try { Thread.sleep(100); } catch (InterruptedException ie) { break; } 
                }
            }
            if (!terminado) { proceso.destroy(); return "TIMEOUT"; }
            try { lector.join(1000); } catch (InterruptedException ignored) {}
            return salida.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (proceso != null) proceso.destroy();
        }
    }

    private String ejecutarComandoMod(String opcion) {
        String su = RootDetector.encontrarSu();
        if (su == null) return "NO_ROOT";
        
        if (!new java.io.File(binaryPath).exists()) {
            prepararBinario();
        }
        if (!binarioListo) return "NO_BINARIO";

        String secKey = credManager.getSecurityKey();
        String comando = "exec " + binaryPath + " " + opcion + " '" + secKey + "'\n";
        
        String resultado = ejecutarConStdin(su, comando, 20000);
        
        if (resultado == null) return "ERROR";
        if (resultado.equals("TIMEOUT")) return "TIMEOUT";
        if (resultado.contains("Juego no encontrado") || resultado.contains("not found")) return "NOT_FOUND";
        if (resultado.contains("VERSION_INCOMPATIBLE")) return "VERSION_INCOMPATIBLE";
        if (resultado.contains("ACCESO DENEGADO")) return "SESION_EXPIRADA";
        if (resultado.contains("[+] Activado")) return "OK";
        
        return "PATRON_NO_ENCONTRADO";
    }

    private String ejecutarComandoAdmin(String password, String expiry) {
        String su = RootDetector.encontrarSu();
        if (su == null) return null;
        
        if (!new java.io.File(binaryPath).exists()) {
            prepararBinario();
        }
        if (!binarioListo) return null;

        String passEscapada = password.replace("'", "'\\''");
        String comando = "exec " + binaryPath + " --admin '" + passEscapada + "'";
        
        if (expiry != null && !expiry.trim().isEmpty()) {
            comando += " '" + expiry.trim().replace("'", "") + "'";
        }
        comando += "\n";

        return ejecutarConStdin(su, comando, 15000);
    }

    private void configurarSwitch(int id, final String opcion, final String nombre) {
        final Switch sw = floatingView.findViewById(id);
        if (sw == null) return;
        allSwitches.add(sw);

        final CompoundButton.OnCheckedChangeListener[] holder = new CompoundButton.OnCheckedChangeListener[1];
        holder[0] = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean activo) {
                if (!activo) {
                    sw.setText(nombre);
                    return;
                }
                
                for (Switch other : allSwitches) {
                    if (other != sw && other.isChecked()) {
                        other.setChecked(false);
                    }
                }

                progressBar.setVisibility(View.VISIBLE);
                sw.setText(nombre + " [Activando...]");

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (!new java.io.File(binaryPath).exists()) {
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
                                    else if ("VERSION_INCOMPATIBLE".equals(res)) msg = "⚠️ Versión de Free Fire no soportada";
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
                if (su != null && binaryPath != null) {
                    RootDetector.ejecutar(new String[]{su, "-c", "rm -f " + binaryPath}, 3000);
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
                    if (!new java.io.File(binaryPath).exists()) prepararBinario();
                    return ejecutarComandoAdmin(password, expiry);
                }
                @Override
                public String subirAlGAS(AdminCredentialManager.Credencial cred) {
                    if (urlGAS == null) return "Espera un momento. El servidor se está preparando.";
                    return adminCredManager.subirAlGAS(urlGAS, cred);
                }
                @Override
                public void onActivarInternet() {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            String su = RootDetector.encontrarSu();
                            if (su != null) {
                                RootDetector.ejecutar(new String[]{su, "-c", "svc wifi enable 2>/dev/null; svc data enable 2>/dev/null"}, 5000);
                                internetDesactivado = false; 
                            }
                        }
                    });
                }
                @Override
                public void onCerrar(boolean volverACortarInternet) {
                    adminFloatingView = null;
                    adminAbierto = false; 
                    if (volverACortarInternet) {
                        executor.execute(new Runnable() {
                            @Override public void run() { 
                                desactivarInternet(); 
                            }
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