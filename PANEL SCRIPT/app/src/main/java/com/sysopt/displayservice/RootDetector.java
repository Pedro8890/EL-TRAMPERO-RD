package com.sysopt.displayservice;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Pattern;

public final class RootDetector {

    private RootDetector() {}

    private static final String[] SU_PATHS = {
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/system/sbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/debug_ramdisk/su",
        "/sbin/.magisk/busybox/su",
        "/data/adb/magisk/busybox/su",
        // Corrección 4.1: Rutas para KernelSU y APatch modernos
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su"
    };

    private static final Pattern ROOT_PATTERN =
        Pattern.compile("(^|\\s)uid=0(?:\\(|\\s|$)");

    private static volatile String suCache = null;

    public static String encontrarSu() {
        return encontrarSu(3000L);
    }

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

    public static void invalidarCache() {
        suCache = null;
    }

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

    public static Resultado ejecutar(String[] comando, long timeoutMs) {
        Process proceso = null;
        try {
            proceso = new ProcessBuilder(comando).redirectErrorStream(true).start();

            final InputStream entrada = proceso.getInputStream();
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
                return new Resultado(leerCadena(salida), -1, true);
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

    private static boolean comprobarRoot(String path, long timeoutMs) {
        Resultado r = ejecutar(new String[]{path, "-c", "id"}, timeoutMs);
        return r != null
            && !r.timeout
            && r.codigo == 0
            && ROOT_PATTERN.matcher(r.salida).find();
    }

    private static String leerCadena(ByteArrayOutputStream baos) {
        try {
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return baos.toString();
        }
    }
}