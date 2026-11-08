package com.sysopt.displayservice;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CredentialManager {

    private final Context      context;
    private final CacheManager cache;
    private String             gasUrl;

    public CredentialManager(Context context) {
        this.context = context;
        this.cache   = new CacheManager(context);
    }

    public void setGasUrl(String url) { this.gasUrl = url; }

    public String getSecurityKey() {
        return SecurityManager.getSecurityKey(context);
    }

    public static final class SesionData {
        public final String id, usuario, password, expiry;
        public SesionData(String id, String usuario, String password, String expiry) {
            this.id = id; this.usuario = usuario;
            this.password = password; this.expiry = expiry;
        }
    }

    public static final class Resultado {
        public final boolean exito;
        public final String  error;
        public final SesionData data;
        Resultado(boolean exito, String error, SesionData data) {
            this.exito = exito; this.error = error; this.data = data;
        }
    }

    public Resultado verificarYPreparar(String id, String usuario, String password) {
        if (gasUrl == null || gasUrl.isEmpty()) return new Resultado(false, "URL GAS no configurada", null);
        
        try {
            String url = gasUrl + "?accion=verificar"
                + "&id=" + URLEncoder.encode(id, "UTF-8")
                + "&usuario=" + URLEncoder.encode(usuario, "UTF-8")
                + "&password=" + URLEncoder.encode(password, "UTF-8");

            String respuesta = getHttp(url);
            if (respuesta == null) return new Resultado(false, "Error de red", null);

            JSONObject json = new JSONObject(respuesta);
            boolean valido = json.optBoolean("valido", false);
            String mensaje = json.optString("mensaje", "Credenciales inválidas");
            String expiry  = json.optString("expiry", "");

            if (!valido) return new Resultado(false, mensaje, null);

            eliminarDeGAS(id);
            return new Resultado(true, null, new SesionData(id, usuario, password, expiry));

        } catch (Exception e) {
            return new Resultado(false, "Error al verificar", null);
        }
    }

    public void crearCacheAsincrono(SesionData data) {
        cache.crearCache(getSecurityKey(), data.id, data.usuario, data.password, data.expiry);
    }

    public boolean sesionEnCache() {
        String[] datos = cache.verificarCache(getSecurityKey());
        if (datos == null) return false;
        if (estaExpirado(datos[3])) {
            cache.limpiarCache();
            return false;
        }
        return true;
    }

    public String getExpiry() {
        String[] datos = cache.verificarCache(getSecurityKey());
        if (datos == null) return null;
        return datos[3];
    }

    private void eliminarDeGAS(String id) {
        try {
            String url = gasUrl + "?accion=eliminar&id=" + URLEncoder.encode(id, "UTF-8");
            getHttp(url);
        } catch (Exception ignored) {}
    }

    private String getHttp(String endpoint) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

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

    private boolean estaExpirado(String expiry) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String hoyStr = sdf.format(new Date());
            return expiry.compareTo(hoyStr) < 0;
        } catch (Exception e) {
            return true;
        }
    }
}