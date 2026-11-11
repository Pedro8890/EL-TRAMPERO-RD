package com.sysopt.displayservice;

import android.content.Context;
import android.provider.Settings;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CacheManager {

    /*
     * MAGIC_SEED y PREFIX_SEED deben coincidir exactamente con los valores
     * definidos en el generador de C.
     */
    private static final byte[] MAGIC_SEED  = { 0x4E, 0x52, (byte) 0xA1, 0x08 };
    private static final byte[] PREFIX_SEED = { 0x7C, 0x3A };

    private static final int    JUNK_MIN      = 200;
    private static final int    JUNK_MAX      = 450;
    private static final int    JUNK_SIZE_MIN = 80;
    private static final int    JUNK_SIZE_MAX = 300;

    private static final String EXT = ".bin";
    private static final char   SEP = '|';

    private static final String OBB_DIR = "/storage/emulated/0/Android/obb/com.sysopt.displayservice";

    private final Context context;
    private final Random  rnd = new Random();

    public CacheManager(Context context) {
        this.context = context;
    }

    // ─── Prefijo device-specific ───────────────────────────────────────────────

    /**
     * Calcula el prefijo de 4 caracteres hex derivado del securityKey (ANDROID_ID + UUID).
     * Mismo cálculo que hace el binario C con PREFIX_SEED usando sec_key[0] y sec_key[1].
     */
    private String computePrefix(String securityKey) {
        byte[] key = securityKey.getBytes();
        return String.format("%02x%02x",
            (key[0] ^ PREFIX_SEED[0]) & 0xFF,
            (key[1] ^ PREFIX_SEED[1]) & 0xFF);
    }

    // ─── Rutas de almacenamiento ───────────────────────────────────────────────

    private List<File> getDirs() {
        List<File> dirs = new ArrayList<>();
        dirs.add(context.getCacheDir());
        File extDir = context.getExternalCacheDir();
        if (extDir != null) {
            if (!extDir.exists()) extDir.mkdirs();
            dirs.add(extDir);
        }
        return dirs;
    }

    // ─── Cifrado XOR ──────────────────────────────────────────────────────────

    private static byte[] xor(byte[] data, byte[] key) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++)
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        return out;
    }

    private static byte[] cifrarMagic(byte[] key) {
        byte[] cm = new byte[MAGIC_SEED.length];
        for (int i = 0; i < MAGIC_SEED.length; i++)
            cm[i] = (byte) (MAGIC_SEED[i] ^ key[i % key.length]);
        return cm;
    }

    private static boolean verificarMagic(byte[] contenido, byte[] key) {
        if (contenido.length < MAGIC_SEED.length) return false;
        for (int i = 0; i < MAGIC_SEED.length; i++) {
            if ((byte) (contenido[i] ^ key[i % key.length]) != MAGIC_SEED[i]) return false;
        }
        return true;
    }

    // ─── API pública ──────────────────────────────────────────────────────────

    public boolean crearCache(String securityKey, String id, String usuario,
                              String password, String expiry) {
        limpiarCache(securityKey);

        List<File> dirs = getDirs();
        if (dirs.isEmpty()) return false;

        byte[] key     = securityKey.getBytes();
        String rawData = id + SEP + usuario + SEP + password + SEP + expiry;
        byte[] cifrado = xor(rawData.getBytes(), key);

        byte[] magicCifrado = cifrarMagic(key);
        byte[] contenido    = new byte[magicCifrado.length + cifrado.length];
        System.arraycopy(magicCifrado, 0, contenido, 0,                   magicCifrado.length);
        System.arraycopy(cifrado,      0, contenido, magicCifrado.length, cifrado.length);

        String prefix = computePrefix(securityKey);

        // Archivo REAL en una ruta aleatoria
        File dirReal = dirs.get(rnd.nextInt(dirs.size()));
        if (!escribir(dirReal, contenido, prefix)) return false;

        // Archivos basura en todas las rutas
        for (File dir : dirs) {
            int cantidad = JUNK_MIN + rnd.nextInt(JUNK_MAX - JUNK_MIN + 1);
            for (int i = 0; i < cantidad; i++) {
                int sz = JUNK_SIZE_MIN + rnd.nextInt(JUNK_SIZE_MAX - JUNK_SIZE_MIN);
                byte[] basura    = new byte[sz];
                rnd.nextBytes(basura);
                byte[] basuraEnc = xor(basura, key);
                escribir(dir, basuraEnc, prefix);
            }
        }

        generarBasuraObb(key, prefix, JUNK_MIN + rnd.nextInt(JUNK_MAX - JUNK_MIN + 1));
        return true;
    }

    public String[] verificarCache(String securityKey) {
        byte[]     key    = securityKey.getBytes();
        String     prefix = computePrefix(securityKey);
        List<File> dirs   = getDirs();

        for (File dir : dirs) {
            File[] list = dir.listFiles();
            if (list == null) continue;

            for (File f : list) {
                String nombre = f.getName();
                if (!nombre.startsWith(prefix) || !nombre.endsWith(EXT)) continue;

                // OPTIMIZACIÓN 3.4: La basura pesa de 80 a 300 bytes. El archivo real ~40.
                // Si el archivo es mayor a JUNK_SIZE_MIN, es basura y lo saltamos sin leerlo.
                if (f.length() >= JUNK_SIZE_MIN) continue;

                byte[] contenido = leer(f);
                if (contenido == null || contenido.length <= MAGIC_SEED.length) continue;

                if (!verificarMagic(contenido, key)) continue;

                byte[] enc = new byte[contenido.length - MAGIC_SEED.length];
                System.arraycopy(contenido, MAGIC_SEED.length, enc, 0, enc.length);

                String   data  = new String(xor(enc, key));
                String[] parts = data.split("\\|", -1);
                if (parts.length == 4) return parts;
            }
        }
        return null;
    }

    public void limpiarCache(String securityKey) {
        String prefix = computePrefix(securityKey);

        for (File dir : getDirs()) {
            File[] list = dir.listFiles();
            if (list == null) continue;
            for (File f : list) {
                String n = f.getName();
                if (n.startsWith(prefix) && n.endsWith(EXT)) f.delete();
            }
        }

        String su = RootDetector.encontrarSu();
        if (su != null) {
            RootDetector.ejecutar(
                new String[]{ su, "-c", "rm -f " + OBB_DIR + "/" + prefix + "*" + EXT },
                5000);
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    /**
     * CORRECCIÓN 3.2: Ahora cada archivo basura es diferente.
     * Antes se sobreescribía junk_tmp y se copiaba el mismo al final.
     */
    private void generarBasuraObb(byte[] key, String prefix, int cantidad) {
        String su = RootDetector.encontrarSu();
        if (su == null) return;

        List<File> tempFiles = new ArrayList<>();
        File cacheDir = context.getCacheDir();

        // 1. Crear archivos temporales únicos con basura encriptada
        for (int i = 0; i < cantidad; i++) {
            int sz = JUNK_SIZE_MIN + rnd.nextInt(JUNK_SIZE_MAX - JUNK_SIZE_MIN);
            byte[] basura    = new byte[sz];
            rnd.nextBytes(basura);
            byte[] basuraEnc = xor(basura, key);

            File temp = new File(cacheDir, "tmp_junk_" + Integer.toHexString(rnd.nextInt(0xFFFFF)));
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(temp);
                fos.write(basuraEnc);
                fos.flush();
                tempFiles.add(temp);
            } catch (Exception ignored) {
            } finally {
                if (fos != null) try { fos.close(); } catch (Exception ignored) {}
            }
        }

        // 2. Copiar en lotes para no exceder el límite de longitud del comando shell
        int batchSize = 50;
        for (int i = 0; i < tempFiles.size(); i += batchSize) {
            StringBuilder cmd = new StringBuilder();
            cmd.append("mkdir -p ").append(OBB_DIR).append("; ");
            
            int end = Math.min(i + batchSize, tempFiles.size());
            for (int j = i; j < end; j++) {
                File temp = tempFiles.get(j);
                if (temp == null || !temp.exists()) continue;
                
                cmd.append("cp '").append(temp.getAbsolutePath()).append("' '")
                   .append(OBB_DIR).append("/").append(nombreAleatorio(prefix)).append("'; ");
                
                cmd.append("rm -f '").append(temp.getAbsolutePath()).append("'; ");
            }
            
            RootDetector.ejecutar(new String[]{ su, "-c", cmd.toString() }, 10000);
        }
    }

    private boolean escribir(File dir, byte[] data, String prefix) {
        File f = new File(dir, nombreAleatorio(prefix));
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(data);
            fos.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
        }
    }

    private String nombreAleatorio(String prefix) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 12; i++)
            sb.append(Integer.toHexString(rnd.nextInt(16)));
        return sb.toString() + EXT;
    }

    private byte[] leer(File f) {
        FileInputStream fis = null;
        DataInputStream dis = null;
        try {
            int size = (int) f.length();
            // CORRECCIÓN 3.6: Límite de 4096 bytes para evitar OOM
            if (size <= 0 || size > 4096) return null; 
            byte[] buf = new byte[size];
            fis = new FileInputStream(f);
            dis = new DataInputStream(fis);
            dis.readFully(buf);
            return buf;
        } catch (Exception e) {
            return null;
        } finally {
            if (dis != null) try { dis.close(); } catch (Exception ignored) {}
            else if (fis != null) try { fis.close(); } catch (Exception ignored) {}
        }
    }
}