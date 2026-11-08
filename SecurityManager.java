package com.sysopt.displayservice;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.UUID;

public final class SecurityManager {
    private static final String PREF_NAME = "trampero_sec_prefs";
    private static final String KEY_INSTALL_UUID = "install_uuid";

    public static String getSecurityKey(Context context) {
        // 1. Obtener el ANDROID_ID
        String androidId = Settings.Secure.getString(
            context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null || androidId.isEmpty()) {
            androidId = "fallback_android_id";
        }

        // 2. Obtener o generar el UUID de Instalación
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String installUuid = prefs.getString(KEY_INSTALL_UUID, null);
        
        if (installUuid == null) {
            installUuid = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALL_UUID, installUuid).apply();
        }

        // Retornar la llave combinada única
        return androidId + "_" + installUuid;
    }
}