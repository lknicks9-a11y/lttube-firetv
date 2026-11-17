package com.lttube.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Google sign-in the way YouTube's own TV app does it: the TV shows a code, you
 * enter it at yt.be/activate on your phone. (Same method SmartTube uses.)
 */
public final class Account {
    private static final String TV_UA =
            "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)";
    // YouTube TV's app identity, read from youtube.com/tv at sign-in; these are the fallback.
    private static final String FALLBACK_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";
    private static final String FALLBACK_SECRET = "SboVhoG9s0rNafixCSGGKXAT";
    private static final MediaType JSON = MediaType.get("application/json");

    private static SharedPreferences p;

    public static void init(Context c) {
        p = c.getSharedPreferences("account", Context.MODE_PRIVATE);
    }

    public static boolean signedIn() { return !p.getString("refresh", "").isEmpty(); }
    public static String name() { return p.getString("name", ""); }
    public static String photo() { return p.getString("photo", ""); }

    public static void signOut() {
        p.edit().clear().apply();
    }

    static void saveProfile(String name, String photo) {
        p.edit().putString("name", name == null ? "" : name).putString("photo", photo == null ? "" : photo).apply();
    }

    // ------------------------------------------------------------------ device code
    public static class DeviceCode {
        public String deviceCode, userCode, url;
        public int interval = 5;
        public long expiresAt;
    }

    /** Step 1: get a code to show on the TV. Blocks. */
    public static DeviceCode start() throws Exception {
        String[] client = clientIdentity();
        JSONObject r = requestCode(client[0]);
        if (!r.has("device_code")) throw new Exception(r.optString("error_description", r.optString("error", "Google didn't give a code")));
        DeviceCode d = new DeviceCode();
        d.deviceCode = r.getString("device_code");
        d.userCode = r.getString("user_code");
        d.url = r.optString("verification_url", "https://yt.be/activate");
        d.interval = Math.max(2, r.optInt("interval", 5));
        d.expiresAt = System.currentTimeMillis() + r.optLong("expires_in", 1800) * 1000;
        p.edit().putString("cid", client[0]).putString("csec", client[1]).apply();
        return d;
    }

    /**
     * Step 2: ask once whether the code has been entered.
     * Returns "ok", "pending", "slow", or throws when it failed / expired / was denied.
     */
    public static String poll(DeviceCode d) throws Exception {
        if (System.currentTimeMillis() > d.expiresAt) throw new Exception("The code expired. Start again.");
        JSONObject body = new JSONObject()
                .put("client_id", p.getString("cid", FALLBACK_ID))
                .put("client_secret", p.getString("csec", FALLBACK_SECRET))
                .put("code", d.deviceCode)
                .put("grant_type", "http://oauth.net/grant_type/device/1.0");
        JSONObject r = post("https://www.youtube.com/o/oauth2/token", body);
        String err = r.optString("error", "");
        if ("authorization_pending".equals(err)) return "pending";
        if ("slow_down".equals(err)) return "slow";
        if (!err.isEmpty()) {
            if ("access_denied".equals(err)) throw new Exception("Sign-in was cancelled on the phone.");
            if ("expired_token".equals(err)) throw new Exception("The code expired. Start again.");
            throw new Exception(r.optString("error_description", err));
        }
        if (!r.has("refresh_token")) throw new Exception("Google didn't finish the sign-in.");
        p.edit().putString("refresh", r.getString("refresh_token"))
                .putString("access", r.optString("access_token"))
                .putLong("exp", System.currentTimeMillis() + r.optLong("expires_in", 3600) * 1000 - 60_000)
                .apply();
        return "ok";
    }

    /** A valid access token, refreshed when needed. Blocks. Null when signed out. */
    public static synchronized String token() throws Exception {
        if (!signedIn()) return null;
        if (System.currentTimeMillis() < p.getLong("exp", 0) && !p.getString("access", "").isEmpty()) {
            return p.getString("access", "");
        }
        JSONObject body = new JSONObject()
                .put("client_id", p.getString("cid", FALLBACK_ID))
                .put("client_secret", p.getString("csec", FALLBACK_SECRET))
                .put("refresh_token", p.getString("refresh", ""))
                .put("grant_type", "refresh_token");
        JSONObject r = post("https://www.youtube.com/o/oauth2/token", body);
        if (!r.has("access_token")) {
            String e = r.optString("error", "");
            if ("invalid_grant".equals(e)) { signOut(); throw new Exception("You were signed out of YouTube. Sign in again."); }
            throw new Exception("Couldn't refresh the sign-in: " + r.optString("error_description", e));
        }
        p.edit().putString("access", r.getString("access_token"))
                .putLong("exp", System.currentTimeMillis() + r.optLong("expires_in", 3600) * 1000 - 60_000)
                .apply();
        return r.getString("access_token");
    }

    static JSONObject requestCode(String clientId) throws Exception {
        JSONObject body = new JSONObject()
                .put("client_id", clientId)
                .put("scope", "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content")
                .put("device_id", UUID.randomUUID().toString())
                .put("device_model", "ytlr::");
        return post("https://www.youtube.com/o/oauth2/device/code", body);
    }

    // ------------------------------------------------------------------ helpers
    /** Reads the TV app's client id/secret from youtube.com/tv, like SmartTube does. */
    static String[] clientIdentity() {
        try {
            String html = get("https://www.youtube.com/tv");
            Matcher m = Pattern.compile("<script id=\"base-js\" src=\"([^\"]+)\"").matcher(html);
            if (m.find()) {
                String src = m.group(1);
                if (src.startsWith("/")) src = "https://www.youtube.com" + src;
                String js = get(src);
                Matcher c = Pattern.compile("clientId:\"([-\\w]+\\.apps\\.googleusercontent\\.com)\",\\w+:\"([-\\w]+)\"").matcher(js);
                if (c.find()) return new String[]{c.group(1), c.group(2)};
            }
        } catch (Exception ignored) { }
        return new String[]{FALLBACK_ID, FALLBACK_SECRET};
    }

    private static String get(String url) throws Exception {
        Request r = new Request.Builder().url(url).header("User-Agent", TV_UA).build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            return res.body() == null ? "" : res.body().string();
        }
    }

    private static JSONObject post(String url, JSONObject body) throws Exception {
        Request r = new Request.Builder().url(url)
                .header("User-Agent", TV_UA)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            String text = res.body() == null ? "{}" : res.body().string();
            try { return new JSONObject(text); }
            catch (Exception e) { return new JSONObject().put("error", "HTTP " + res.code()); }
        }
    }

    private Account() { }
}
