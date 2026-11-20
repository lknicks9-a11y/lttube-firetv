package com.lttube.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import okhttp3.Request;
import okhttp3.Response;

public class AppleProbeTest {
    static String get(String u) {
        try {
            Request r = new Request.Builder().url(u).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36").build();
            try (Response res = NpDownloader.get().client.newCall(r).execute()) { return res.body() == null ? "" : res.body().string(); }
        } catch (Throwable e) { return ""; }
    }
    static Object data(String html) throws Exception {
        java.util.regex.Matcher d = java.util.regex.Pattern.compile("<script[^>]*id=\"serialized-server-data\"[^>]*>(.*?)</script>", java.util.regex.Pattern.DOTALL).matcher(html);
        return d.find() ? new org.json.JSONTokener(d.group(1)).nextValue() : null;
    }
    static void sections(String name, String url) {
        try {
            Object root = data(get(url));
            java.util.List<JSONObject> secs = new java.util.ArrayList<>();
            find(root, secs, 0);
            System.out.println("APL " + name + " sections=" + secs.size());
            int k = 0;
            for (JSONObject s : secs) {
                if (k++ > 9) break;
                JSONArray items = s.optJSONArray("items");
                String title = "";
                JSONObject h = s.optJSONObject("header");
                if (h != null) { JSONObject it = h.optJSONObject("item"); title = it != null ? it.optString("title") : h.toString().substring(0, Math.min(80, h.toString().length())); }
                System.out.println("APL   SEC [" + title + "] id=" + s.optString("id") + " kind=" + s.optString("itemKind") + " n=" + (items == null ? 0 : items.length())
                        + " first=" + (items == null || items.length() == 0 ? "" : items.optJSONObject(0).toString().substring(0, Math.min(900, items.optJSONObject(0).toString().length()))));
            }
        } catch (Throwable e) { System.out.println("APL " + name + " FAILED " + e); }
    }
    static void find(Object n, java.util.List<JSONObject> out, int d) {
        if (n == null || d > 30) return;
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            if (o.has("itemKind") && o.has("items")) { out.add(o); return; }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) find(o.opt(it.next()), out, d + 1);
        } else if (n instanceof JSONArray) { JSONArray a = (JSONArray) n; for (int i = 0; i < a.length(); i++) find(a.opt(i), out, d + 1); }
    }
    @Test public void probe() {
        sections("ARTIST", "https://music.apple.com/us/artist/sofaygo/1456793166");
        sections("BROWSE", "https://music.apple.com/us/browse");
        sections("PLAYLIST", "https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb");
    }
}
