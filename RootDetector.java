package com.sysopt.displayservice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Pattern;

public final class RootDetector {

    private RootDetector() {}

    // ─────────────────────────────────────────────────────────
    // Rutas de su a probar (cubre Magisk, KernelSU y APatch)
    // ─────────────────────────────────────────────────────────

    private static final String[] SU_PATHS = {

        // Resolución automática por PATH
        "su",

        // Rutas tradicionales
        "/system/bin/su",
        "/system/xbin/su",
        "/system/sbin/su",
        "/sbin/su",
        "/vendor/bin/su",

        // Magisk moderno
        "/debug_ramdisk/su",
        "/sbin/.magisk/busybox/su",
        "/data/adb/magisk/busybox/su"
    };

    private static final Pattern ROOT_PATTERN =
        Pattern.compile("(^|\\s)uid=0(?:\\(|\\s|$)");

    // Caché con doble verificación (thread-safe, API 21+)
    private static volatile String suCache = null;

    // ─────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────

    /**
     * Devuelve la primera ruta de su que tiene privilegios root,
     * con el timeout por defecto (3000 ms) por candidato.
     *
     * Para la primera comprobación en MainActivity conviene usar
     * encontrarSu(60000L) — el diálogo de Magisk/KernelSU puede
     * tardar en aparecer en gama baja o con animaciones.
     */
    public static String encontrarSu() {
        return encontrarSu(3000L);
    }

    /**
     * Igual que encontrarSu() pero con timeout personalizado por
     * candidato. Útil para la primera comprobación desde MainActivity,
     * donde el usuario tiene que responder al diálogo de root.
     */
    public static String encontrarSu(long timeoutMs) {

        String cache = suCache;
        if (cache != null) return cache;

        synchronized (RootDetector.class) {

            if (suCache != null) return suCache;

            for (String path : SU_PATHS) {
                if (comprobarRoot(path, timeoutMs)) {
                    suCache = path;
                    return path;
                }
            }
        }

        return null;
    }

    /** Fuerza una nueva búsqueda en la próxima llamada. */
    public static void invalidarCache() {
        suCache = null;
    }

    // ─────────────────────────────────────────────────────────
    // Resultado de ejecutar()
    // ─────────────────────────────────────────────────────────

    public static final class Resultado {
        public final String salida;
        public final int    codigo;
        public final boolean timeout;

        Resultado(String salida, int codigo, boolean timeout) {
            this.salida   = salida;
            this.codigo   = codigo;
            this.timeout  = timeout;
        }
    }

    // ─────────────────────────────────────────────────────────
    // Ejecutor de comandos con timeout (compatible API 21+)
    // ─────────────────────────────────────────────────────────

    /**
     * Ejecuta un comando externo con timeout en milisegundos.
     * Mezcla stdout y stderr. Nunca bloquea indefinidamente.
     * Devuelve null si el proceso no pudo iniciarse.
     */
    public static Resultado ejecutar(String[] comando, long timeoutMs) {

        Process proceso = null;

        try {

            proceso = new ProcessBuilder(comando)
                .redirectErrorStream(true)
                .start();

            final InputStream        entrada = proceso.getInputStream();
            final ByteArrayOutputStream salida = new ByteArrayOutputStream();

            Thread lector = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] buf = new byte[1024];
                        int    n;
                        while ((n = entrada.read(buf)) != -1) {
                            salida.write(buf, 0, n);
                        }
                    } catch (Exception ignorado) {}
                }
            });

            lector.setDaemon(true);
            lector.start();

            // Polling: compatible con API 21+
            // (waitFor(long, TimeUnit) solo existe en API 26+)
            long limite    = System.currentTimeMillis() + timeoutMs;
            boolean termino = false;
            int     codigo  = -1;

            while (!termino) {

                try {
                    codigo  = proceso.exitValue();
                    termino = true;

                } catch (IllegalThreadStateException aun) {

                    if (System.currentTimeMillis() >= limite) break;

                    try { Thread.sleep(50); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        proceso.destroy();
                        return null;
                    }
                }
            }

            if (!termino) {
                proceso.destroy();
                lector.join(200);
                return new Resultado(
                    leerCadena(salida), -1, true
                );
            }

            lector.join(500);
            return new Resultado(leerCadena(salida), codigo, false);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return null;

        } catch (Exception e) {

            return null;

        } finally {

            if (proceso != null) proceso.destroy();
        }
    }

    // ─────────────────────────────────────────────────────────
    // Internos
    // ─────────────────────────────────────────────────────────

    private static boolean comprobarRoot(String path, long timeoutMs) {
        Resultado r = ejecutar(new String[]{path, "-c", "id"}, timeoutMs);
        return r != null
            && !r.timeout
            && r.codigo == 0
            && ROOT_PATTERN.matcher(r.salida).find();
    }

    /**
     * Convierte bytes a String en UTF-8.
     * UTF-8 siempre está disponible en Android (API 1+).
     */
    private static String leerCadena(ByteArrayOutputStream baos) {
        try {
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return baos.toString();
        }
    }
}