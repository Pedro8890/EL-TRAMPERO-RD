package com.sysopt.displayservice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1234;
    private static final int REQ_NOTIF   = 4321;
    private static final int REQ_BATTERY = 5678;

    // API 33+ (Build.VERSION_CODES.TIRAMISU no existe en compileSdk 29)
    private static final int TIRAMISU = 33;

    // Timeout largo para la primera comprobación de root:
    // el diálogo de Magisk/KernelSU puede tardar en responder
    // en gama baja o con animaciones del sistema.
    private static final long ROOT_TIMEOUT_MS = 60000L;

    private boolean notifSolicitado  = false;
    private boolean batteryPedido    = false;

    // Control para diferir moveTaskToBack hasta onResume()
    // (algunos launchers como Samsung One UI procesan el back
    // de forma asíncrona; hacerlo desde onCreate falla).
    private boolean activityResumed    = false;
    private boolean pendienteMinimizar = false;

    // ─────────────────────────────────────────────────────────
    // Creación
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Root: se detecta en hilo de fondo para no bloquear UI.
        // Dispara el diálogo de Magisk/KernelSU y deja el resultado
        // en caché para que FloatingService lo reutilice.
        verificarRootEnSegundoPlano();

        // Overlay: obligatorio antes de arrancar el servicio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {

            startActivityForResult(
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                ),
                REQ_OVERLAY
            );

        } else {
            continuar();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Ciclo de vida (para diferir moveTaskToBack)
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;

        // Si el servicio ya está arrancado y quedó pendiente minimizar,
        // hacerlo ahora que la Activity está realmente resumida.
        if (pendienteMinimizar) {
            pendienteMinimizar = false;
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
    }

    // ─────────────────────────────────────────────────────────
    // Detección de root (hilo de fondo, API 21+)
    // ─────────────────────────────────────────────────────────

    private void verificarRootEnSegundoPlano() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                // Timeout largo: el usuario tiene que responder al
                // diálogo de Magisk/KernelSU la primera vez.
                final String su = RootDetector.encontrarSu(ROOT_TIMEOUT_MS);

                if (su == null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                MainActivity.this,
                                "⚠️ Root no detectado. La app requiere root.",
                                Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────
    // Cadena de permisos: batería → notificaciones → servicio
    // ─────────────────────────────────────────────────────────

    private void continuar() {

        // 1. Excluir de la optimización de batería (API 23+).
        //    Crítico en MIUI / ColorOS / Funtouch / One UI, donde
        //    el sistema mata el ForegroundService aunque esté activo.
        if (!batteryPedido && necesitaIgnorarBateria()) {

            batteryPedido = true;

            try {
                Toast.makeText(
                    this,
                    "Selecciona 'Permitir' para que la app no se cierre sola.",
                    Toast.LENGTH_LONG
                ).show();

                startActivityForResult(
                    new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())
                    ),
                    REQ_BATTERY
                );
                return;

            } catch (Exception e) {
                // Si el Intent no está disponible (raro), seguimos
                e.printStackTrace();
            }
        }

        // 2. POST_NOTIFICATIONS (API 33+)
        if (Build.VERSION.SDK_INT >= TIRAMISU
                && !notifSolicitado
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {

            notifSolicitado = true;

            requestPermissions(
                new String[]{"android.permission.POST_NOTIFICATIONS"},
                REQ_NOTIF
            );

            return;
        }

        // 3. Arrancar
        iniciarServicioFlotante();
    }

    private boolean necesitaIgnorarBateria() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null
            && !pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    // ─────────────────────────────────────────────────────────
    // Arrancar servicio
    // ─────────────────────────────────────────────────────────

    private void iniciarServicioFlotante() {

        Intent intent = new Intent(this, FloatingService.class);

        // startForegroundService requerido en API 26+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        // Minimizar solo cuando la Activity esté realmente resumida.
        // Si venimos de onCreate (aún no resumida), queda pendiente
        // y se ejecuta en el próximo onResume().
        pendienteMinimizar = true;
        if (activityResumed) {
            pendienteMinimizar = false;
            moveTaskToBack(true);
        }
    }

    // ─────────────────────────────────────────────────────────
    // Callbacks
    // ─────────────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(
        int     requestCode,
        String[] permissions,
        int[]    grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_NOTIF) {
            // CORRECCIÓN 10.1: Llamar a continuar() para mantener la cadena de permisos
            continuar();
        }
    }

    @Override
    protected void onActivityResult(
        int    requestCode,
        int    resultCode,
        Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_OVERLAY) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && Settings.canDrawOverlays(this)) {

                continuar();

            } else {
                // CORRECCIÓN 10.2: Diálogo para Reintentar o Salir
                new AlertDialog.Builder(this)
                    .setTitle("Permiso Requerido")
                    .setMessage("El permiso 'Mostrar sobre otras apps' es obligatorio para que funcione el menú flotante. ¿Deseas reintentar?")
                    .setPositiveButton("Reintentar", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivityForResult(
                                new Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())
                                ),
                                REQ_OVERLAY
                            );
                        }
                    })
                    .setNegativeButton("Salir", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setCancelable(false)
                    .show();
            }

        } else if (requestCode == REQ_BATTERY) {

            // El usuario haya aceptado o no, seguimos.
            // Si denegó, el sistema puede matar el servicio; es
            // su decisión y no podemos forzar más.
            continuar();
        }
    }
}