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
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("deprecation")
public class FloatingService extends Service {

    private static final String CHANNEL_ID          = "sys_disp_svc";
    private static final int    NOTIF_ID            = 1001;
    
    // Configuración del Socket Abstracto para Zygisk
    private static final String SOCKET_NAME         = "eltrampero_rd";
    
    // URL del GAS (Ahora en Java, ya no en el binario)
    private static final String GAS_URL             = "https://script.google.com/macros/s/AKfycbwhiE_R667pWxTL_ITqzwuzKT9yndEcL0r_Tj_C1qWeboYkdKRTUeOg9ZlnNEN4zGQIAw/exec";
    
    // Contraseña del panel Admin (Cambiala si quieres)
    private static final String ADMIN_PASSWORD_JAVA = "122830";

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

    private volatile boolean internetDesactivado = false;
    private volatile boolean adminAbierto        = false;

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
        
        // Inyectamos la URL directamente en Java
        credManager.setGasUrl(GAS_URL);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            toast("❌ No se pudo obtener WindowManager", Toast.LENGTH_LONG);
            stopSelf();
            return;
        }

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

    // =========================================================
    // NUEVA LÓGICA: COMUNICACIÓN CON EL MÓDULO ZYGISK
    // =========================================================

    private String enviarComandoModulo(String comando) {
        android.net.LocalSocket socket = new android.net.LocalSocket();
        try {
            socket.connect(new android.net.LocalSocketAddress(
                SOCKET_NAME, android.net.LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(10000);
            
            OutputStream os = socket.getOutputStream();
            os.write((comando + "\n").getBytes());
            os.flush();
            
            InputStream is = socket.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[256]; int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            
            return bos.toString().trim();
        } catch (Exception e) {
            return null; // Módulo no instalado o juego no corriendo
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
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
                        // Enviamos el índice al módulo Zygisk por el socket abstracto
                        final String res = enviarComandoModulo(opcion);
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                if (res != null && res.contains("[+] Activado")) {
                                    sw.setText(nombre + " [🔥 ACTIVADO]");
                                } else {
                                    sw.setOnCheckedChangeListener(null);
                                    sw.setChecked(false);
                                    sw.setOnCheckedChangeListener(holder[0]);
                                    sw.setText(nombre);

                                    String msg;
                                    if (res == null) msg = "❌ Módulo no instalado o Free Fire no está abierto";
                                    else if (res.contains("VERSION_INCOMPATIBLE")) msg = "⚠️ Versión de Free Fire no soportada";
                                    else msg = "⚠️ Patron no encontrado — actualiza los codigos";
                                    
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
                    // Validamos la contraseña y generamos los datos en Java
                    if (!ADMIN_PASSWORD_JAVA.equals(password)) {
                        return "[-] ADMIN_FAIL\n[-] Contrasena incorrecta";
                    }
                    
                    // Generación de credenciales en Java
                    String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
                    String usuario = "VIP" + String.format("%05d", new Random().nextInt(100000));
                    String pass = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    String exp = (expiry == null || expiry.trim().isEmpty()) ? "2026-12-31" : expiry;
                    
                    return "[+] ADMIN_OK\n[+] ID=" + id + "\n[+] USUARIO=" + usuario + "\n[+] PASSWORD=" + pass + "\n[+] EXPIRY=" + exp;
                }
                
                @Override
                public String subirAlGAS(AdminCredentialManager.Credencial cred) {
                    return adminCredManager.subirAlGAS(GAS_URL, cred);
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