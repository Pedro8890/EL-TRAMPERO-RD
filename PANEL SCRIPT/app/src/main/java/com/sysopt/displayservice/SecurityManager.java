package com.sysopt.displayservice;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.UUID;

public final class SecurityManager {

    /*
     * PREF_NAME: nombre neutro para no dejar rastro en el sistema de archivos
     * de que esta app genera credenciales VIP ("trampero_sec_prefs" era
     * demasiado revelador si alguien inspeccionaba /data/data/).
     */
    private static final String PREF_NAME        = "sys_disp_cfg";
    private static final String KEY_INSTALL_UUID = "install_uuid";

    /**
     * Devuelve la clave de seguridad completa usada por CacheManager y por el binario C.
     *
     * Formato: ANDROID_ID + "_" + install_uuid
     *
     * ── IMPORTANTE ──────────────────────────────────────────────────────────
     * El UUID se genera una sola vez y se guarda en SharedPreferences.
     *
     * Si el usuario BORRA LOS DATOS DE LA APP (Configuración > Almacenamiento
     * > Borrar datos), el UUID cambia. Esto invalida TODAS las cachés previas
     * porque el magic device-specific ya no coincide. Ese es el comportamiento
     * DESEADO: reinstalar/limpiar datos = sesión nueva.
     *
     * El prefijo de archivo (computePrefix en CacheManager) usa los 2 primeros
     * bytes de la clave, que son los 2 primeros del ANDROID_ID (o del UUID como
     * fallback). Coincide exactamente con lo que calcula el binario C con
     * PREFIX_SEED.
     * ────────────────────────────────────────────────────────────────────────
     */
    public static String getSecurityKey(Context context) {
        String androidId = Settings.Secure.getString(
            context.getContentResolver(), Settings.Secure.ANDROID_ID);

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String installUuid = prefs.getString(KEY_INSTALL_UUID, null);

        if (installUuid == null) {
            installUuid = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALL_UUID, installUuid).apply();
        }

        // Fallback mejorado: si no hay ANDROID_ID, usamos parte del UUID como
        // "device key". Así el prefijo de archivo también cambia por instalación,
        // evitando que dos emuladores caigan en el mismo prefijo.
        if (androidId == null || androidId.length() < 4) {
            androidId = installUuid.substring(0, Math.min(16, installUuid.length()));
        }

        return androidId + "_" + installUuid;
    }

    /**
     * Invalida la sesión actual eliminando el UUID de instalación.
     *
     * Al borrarlo, la próxima llamada a getSecurityKey() generará un UUID nuevo
     * y todas las cachés existentes (magic device-specific) dejarán de coincidir.
     * El usuario tendrá que volver a loguearse.
     *
     * Útil para un botón de "Cerrar sesión" o "Revocar dispositivo".
     */
    public static void invalidateSecurityKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_INSTALL_UUID).apply();
    }
}