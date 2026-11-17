package com.lttube.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Google sign-in the way YouTube's own TV app does it (code at yt.be/activate),
 * with several Google accounts and their channels as profiles, like "Who's watching?".
 */
public final class Account {
    static final String TV_UA =
            "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)";
    private static final String FALLBACK_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";
    private static final String FALLBACK_SECRET = "SboVhoG9s0rNafixCSGGKXAT";
    private static final MediaType JSON = MediaType.get("application/json");

    private static SharedPreferences p;

    /** One "who's watching" choice: a Google account, or one of its channels. */
    public static class Profile {
        public String acct = "", pageId = "", name = "", photo = "", handle = "";

        JSONObject json() {
            JSONObject o = new JSONObject();
            try { o.put("acct", acct).put("pageId", pageId).put("name", name).put("photo", photo).put("handle", handle); }
            catch (Exception ignored) { }
            return o;
        }

        static Profile of(JSONObject o) {
            Profile pr = new Profile();
            pr.acct = o.optString("acct");
            pr.pageId = o.optString("pageId");
            pr.name = o.optString("name");
            pr.photo = o.optString("photo");
            pr.handle = o.optString("handle");
            return pr;
        }
    }

    public static void init(Context c) {
        p = c.getSharedPreferences("account", Context.MODE_PRIVATE);
        migrate();
    }

    /** Earlier versions kept one account; turn it into the first profile. */
    private static void migrate() {
        String old = p.getString("refresh", "");
        if (old.isEmpty()) return;
        try {
            JSONObject accts = accounts();
            JSONObject a = new JSONObject().put("refresh", old).put("access", p.getString("access", ""))
                    .put("exp", p.getLong("exp", 0)).put("cid", p.getString("cid", FALLBACK_ID))
                    .put("csec", p.getString("csec", FALLBACK_SECRET));
            accts.put("a1", a);
            Profile pr = new Profile();
            pr.acct = "a1";
            pr.name = p.getString("name", "");
            pr.photo = p.getString("photo", "");
            List<Profile> l = profiles();
            l.add(pr);
            p.edit().remove("refresh").remove("access").remove("exp").remove("name").remove("photo")
                    .putString("accounts", accts.toString()).putInt("active", l.size() - 1).apply();
            saveProfiles(l);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ profiles
    public static synchronized List<Profile> profiles() {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("profiles", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Profile.of(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    private static synchronized void saveProfiles(List<Profile> l) {
        JSONArray a = new JSONArray();
        for (Profile pr : l) a.put(pr.json());
        p.edit().putString("profiles", a.toString()).apply();
    }

    public static synchronized Profile active() {
        List<Profile> l = profiles();
        int i = p.getInt("active", -1);
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    public static synchronized void setActive(int index) {
        p.edit().putInt("active", index).apply();
    }

    public static void watchAsGuest() { setActive(-1); }

    public static boolean signedIn() { return active() != null; }
    public static String name() { Profile a = active(); return a == null ? "" : a.name; }
    public static String photo() { Profile a = active(); return a == null ? "" : a.photo; }
    public static String handle() { Profile a = active(); return a == null ? "" : a.handle; }
    public static String pageId() { Profile a = active(); return a == null ? "" : a.pageId; }
    public static String activeKey() { Profile a = active(); return a == null ? "" : a.acct; }

    /** Replace all profiles of one Google account (main account + its channels). */
    public static synchronized void setProfilesFor(String acct, List<Profile> list, boolean makeActive) {
        List<Profile> l = profiles();
        Profile cur = active();
        List<Profile> out = new ArrayList<>();
        for (Profile pr : l) if (!pr.acct.equals(acct)) out.add(pr);
        int firstNew = out.size();
        out.addAll(list);
        saveProfiles(out);
        int active = -1;
        if (makeActive) active = firstNew;
        else if (cur != null) for (int i = 0; i < out.size(); i++) {
            Profile pr = out.get(i);
            if (pr.acct.equals(cur.acct) && pr.pageId.equals(cur.pageId)) active = i;
        }
        p.edit().putInt("active", active).apply();
    }

    static synchronized void saveProfile(String name, String photo) {
        List<Profile> l = profiles();
        int i = p.getInt("active", -1);
        if (i < 0 || i >= l.size()) return;
        if (name != null && !name.isEmpty()) l.get(i).name = name;
        if (photo != null && !photo.isEmpty()) l.get(i).photo = photo;
        saveProfiles(l);
    }

    /** Signs the current Google account out of this TV (removes it and its channels). */
    public static synchronized void signOut() {
        Profile a = active();
        if (a != null) removeAccount(a.acct);
    }

    public static synchronized void removeAccount(String acct) {
        List<Profile> out = new ArrayList<>();
        for (Profile pr : profiles()) if (!pr.acct.equals(acct)) out.add(pr);
        saveProfiles(out);
        JSONObject accts = accounts();
        accts.remove(acct);
        p.edit().putString("accounts", accts.toString()).putInt("active", -1).apply();
    }

    private static synchronized JSONObject accounts() {
        try { return new JSONObject(p.getString("accounts", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    private static synchronized void saveAccount(String key, JSONObject a) {
        JSONObject all = accounts();
        try { all.put(key, a); } catch (Exception ignored) { }
        p.edit().putString("accounts", all.toString()).apply();
    }

    // ------------------------------------------------------------------ device code sign-in
    public static class DeviceCode {
        public String deviceCode, userCode, url;
        public int interval = 5;
        public long expiresAt;
        String cid, csec;
    }

    /** Step 1: a code to show on the TV. Blocks. */
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
        d.cid = client[0];
        d.csec = client[1];
        return d;
    }

    static JSONObject requestCode(String clientId) throws Exception {
        JSONObject body = new JSONObject()
                .put("client_id", clientId)
                .put("scope", "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content")
                .put("device_id", UUID.randomUUID().toString())
                .put("device_model", "ytlr::");
        return post("https://www.youtube.com/o/oauth2/device/code", body);
    }

    /**
     * Step 2: has the code been entered? Returns the new account's key once signed in,
     * "pending" / "slow" while waiting; throws when it failed, expired or was cancelled.
     */
    public static String poll(DeviceCode d) throws Exception {
        if (System.currentTimeMillis() > d.expiresAt) throw new Exception("The code expired. Start again.");
        JSONObject body = new JSONObject()
                .put("client_id", d.cid)
                .put("client_secret", d.csec)
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
        String key = "a" + Long.toString(System.currentTimeMillis(), 36);
        saveAccount(key, new JSONObject()
                .put("refresh", r.getString("refresh_token"))
                .put("access", r.optString("access_token"))
                .put("exp", System.currentTimeMillis() + r.optLong("expires_in", 3600) * 1000 - 60_000)
                .put("cid", d.cid).put("csec", d.csec));
        return key;
    }

    /** A valid access token for the active profile's Google account. Null for guests. */
    public static String token() throws Exception {
        String k = activeKey();
        return k.isEmpty() ? null : tokenFor(k);
    }

    public static synchronized String tokenFor(String key) throws Exception {
        JSONObject a = accounts().optJSONObject(key);
        if (a == null) return null;
        if (System.currentTimeMillis() < a.optLong("exp") && !a.optString("access").isEmpty()) return a.optString("access");
        JSONObject body = new JSONObject()
                .put("client_id", a.optString("cid", FALLBACK_ID))
                .put("client_secret", a.optString("csec", FALLBACK_SECRET))
                .put("refresh_token", a.optString("refresh"))
                .put("grant_type", "refresh_token");
        JSONObject r = post("https://www.youtube.com/o/oauth2/token", body);
        if (!r.has("access_token")) {
            String e = r.optString("error", "");
            if ("invalid_grant".equals(e)) { removeAccount(key); throw new Exception("You were signed out of YouTube. Sign in again."); }
            throw new Exception("Couldn't refresh the sign-in: " + r.optString("error_description", e));
        }
        a.put("access", r.getString("access_token"));
        a.put("exp", System.currentTimeMillis() + r.optLong("expires_in", 3600) * 1000 - 60_000);
        saveAccount(key, a);
        return a.optString("access");
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
