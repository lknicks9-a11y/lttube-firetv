package com.lttube.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Everything LTTube keeps on the TV: subscriptions, history, resume points, settings. */
public final class Store {
    private static SharedPreferences p;

    public static void init(Context c) {
        p = c.getSharedPreferences("lttube", Context.MODE_PRIVATE);
    }

    // ---------- subscriptions ----------
    public static synchronized List<Item> subs() {
        return list("subs");
    }

    public static synchronized boolean isSubscribed(String channelUrl) {
        String id = channelId(channelUrl);
        for (Item i : subs()) if (channelId(i.url).equals(id)) return true;
        return false;
    }

    public static synchronized void subscribe(String url, String name, String thumb) {
        List<Item> l = subs();
        String id = channelId(url);
        for (Item i : l) if (channelId(i.url).equals(id)) return;
        Item c = new Item();
        c.type = Item.CHANNEL;
        c.url = url;
        c.title = name;
        c.channel = name;
        c.channelUrl = url;
        c.thumb = thumb == null ? "" : thumb;
        c.sub = "Subscribed";
        l.add(0, c);
        save("subs", l);
        p.edit().putLong("feedAt", 0).apply();
    }

    public static synchronized int addSubs(List<Item> add) {
        List<Item> l = subs();
        int n = 0;
        outer:
        for (Item a : add) {
            String id = channelId(a.url);
            for (Item i : l) if (channelId(i.url).equals(id)) continue outer;
            l.add(a);
            n++;
        }
        save("subs", l);
        p.edit().putLong("feedAt", 0).apply();
        return n;
    }

    public static synchronized void unsubscribe(String url) {
        List<Item> l = subs();
        String id = channelId(url);
        List<Item> out = new ArrayList<>();
        for (Item i : l) if (!channelId(i.url).equals(id)) out.add(i);
        save("subs", out);
    }

    /** "UCxxxx" from any channel URL, so different URL spellings match. */
    public static String channelId(String url) {
        if (url == null) return "";
        int i = url.indexOf("/channel/");
        String id = i >= 0 ? url.substring(i + 9) : url;
        int q = id.indexOf('?');
        if (q >= 0) id = id.substring(0, q);
        int s = id.indexOf('/');
        if (s >= 0) id = id.substring(0, s);
        return id;
    }

    // ---------- subscription feed cache ----------
    public static synchronized List<Item> feed() { return list("feed"); }
    public static synchronized long feedAge() { return System.currentTimeMillis() - p.getLong("feedAt", 0); }
    public static synchronized void saveFeed(List<Item> l) {
        save("feed", l);
        p.edit().putLong("feedAt", System.currentTimeMillis()).apply();
    }

    // ---------- history / continue watching ----------
    public static synchronized List<Item> history() { return list("history"); }

    public static synchronized void watched(Item v, long positionMs, long durationMs) {
        List<Item> l = history();
        List<Item> out = new ArrayList<>();
        Item copy = Item.fromJson(v.toJson());
        boolean done = durationMs > 0 && positionMs > durationMs - 15000;
        copy.position = done ? 0 : positionMs;
        if (durationMs > 0 && copy.duration <= 0) copy.duration = durationMs / 1000;
        out.add(copy);
        String id = v.videoId();
        for (Item i : l) if (!i.videoId().equals(id) && out.size() < 300) out.add(i);
        save("history", out);
    }

    public static synchronized long resumeAt(String videoId) {
        for (Item i : history()) if (i.videoId().equals(videoId)) return i.position;
        return 0;
    }

    public static synchronized void clearHistory() { save("history", new ArrayList<>()); }

    // ---------- search history ----------
    public static synchronized List<String> searches() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("searches", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    public static synchronized void searched(String q) {
        List<String> l = searches();
        l.remove(q);
        l.add(0, q);
        JSONArray a = new JSONArray();
        for (int i = 0; i < Math.min(20, l.size()); i++) a.put(l.get(i));
        p.edit().putString("searches", a.toString()).apply();
    }

    // ---------- settings ----------
    public static int maxHeight() { return p.getInt("maxHeight", 1080); }
    public static void setMaxHeight(int h) { p.edit().putInt("maxHeight", h).apply(); }
    public static boolean autoplay() { return p.getBoolean("autoplay", true); }
    /** Hide Shorts everywhere (Home rows, Subscriptions, channels, the player's list, the menu). */
    public static boolean hideShorts() { return p.getBoolean("hideShorts", false); }
    public static void setHideShorts(boolean b) { p.edit().putBoolean("hideShorts", b).apply(); }
    /** Side menu items you've hidden. */
    public static boolean navHidden(String key) { return ("," + p.getString("navHidden", "") + ",").contains("," + key + ","); }
    public static void setNavHidden(String key, boolean hide) {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        for (String k : p.getString("navHidden", "").split(",")) if (!k.isEmpty()) s.add(k);
        if (hide) s.add(key); else s.remove(key);
        p.edit().putString("navHidden", android.text.TextUtils.join(",", s)).apply();
    }
    public static void setAutoplay(boolean b) { p.edit().putBoolean("autoplay", b).apply(); }
    public static boolean sponsorBlock() { return p.getBoolean("sponsorblock", true); }
    public static void setSponsorBlock(boolean b) { p.edit().putBoolean("sponsorblock", b).apply(); }
    public static boolean captions() { return p.getBoolean("captions", false); }
    public static void setCaptions(boolean b) { p.edit().putBoolean("captions", b).apply(); }
    public static float speed() { return p.getFloat("speed", 1f); }
    public static void setSpeed(float s) { p.edit().putFloat("speed", s).apply(); }

    // ---------- helpers ----------
    private static List<Item> list(String key) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Item.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    private static void save(String key, List<Item> l) {
        JSONArray a = new JSONArray();
        for (Item i : l) a.put(i.toJson());
        p.edit().putString(key, a.toString()).apply();
    }

    private Store() { }
}
