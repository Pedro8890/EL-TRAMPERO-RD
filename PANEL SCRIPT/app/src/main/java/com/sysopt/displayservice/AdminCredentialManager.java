package com.sysopt.displayservice;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class AdminCredentialManager {

    public static final class Credencial {
        public final String id, usuario, password, expiry;
        public Credencial(String id, String usuario, String password, String expiry) {
            this.id = id; this.usuario = usuario;
            this.password = password; this.expiry = expiry;
        }
    }

    public String subirAlGAS(String gasUrl, Credencial credOriginal) {
        Credencial cred = credOriginal;

        for (int intento = 0; intento < 3; intento++) {
            if (intento > 0) {
                try { Thread.sleep(3000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return "Interrumpido";
                }
            }
            try {
                String urlStr = gasUrl + "?accion=guardar"
                    + "&id="       + URLEncoder.encode(cred.id,       "UTF-8")
                    + "&usuario="  + URLEncoder.encode(cred.usuario,  "UTF-8")
                    + "&password=" + URLEncoder.encode(cred.password, "UTF-8")
                    + "&expiry="   + URLEncoder.encode(cred.expiry,   "UTF-8");

                String resp = getHttp(urlStr);
                if (resp == null) continue;

                JSONObject json = new JSONObject(resp);
                String mensaje = json.optString("mensaje", "");
                
                // Corrección 3.1: Si el ID ya existe, generamos uno nuevo y reintentamos
                if ("El ID ya existe".equals(mensaje)) {
                    String nuevoId = generarNuevoId();
                    cred = new Credencial(nuevoId, cred.usuario, cred.password, cred.expiry);
                    continue; 
                }

                if (json.optBoolean("ok", false)) return null; // Éxito
                
                return mensaje.isEmpty() ? "Error desconocido en GAS" : mensaje;

            } catch (Exception e) {
                if (intento == 2) {
                    return "Error de red: Verifica tu conexión.";
                }
            }
        }
        return "No se pudo conectar al servidor.";
    }

    // Genera un ID de 8 caracteres alfanuméricos por si hay colisión
    private String generarNuevoId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String getHttp(String endpoint) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/537.36");
            
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            if (code != 200) return null;

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();

        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}