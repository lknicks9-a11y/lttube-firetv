package com.lttube.tv.music;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Playlists made from what you play on this TV:
 * Heavy Rotation — the songs you keep coming back to;
 * Your Mix — your favourite artists plus songs like theirs;
 * Discover — songs you haven't played yet, close to what you have.
 * Only official releases go in (no edits, covers or uploads).
 */
public final class Mixes {
    public static final String HEAVY = "MIX:heavy", MIX = "MIX:mix", DISCOVER = "MIX:discover";
    private static final long DAY = 24 * 3600_000L;

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("music_history", Context.MODE_PRIVATE); }

    /** A song started playing. */
    public static synchronized void played(Context c, MItem m) {
        if (m == null || !m.playable() || m.slop()) return;
        try {
            SharedPreferences p = prefs(c);
            JSONObject all = new JSONObject(p.getString("songs", "{}"));
            JSONObject e = all.optJSONObject(m.videoId);
            if (e == null) e = new JSONObject().put("n", 0);
            e.put("item", m.toJson()).put("n", e.optInt("n") + 1).put("last", System.currentTimeMillis());
            all.put(m.videoId, e);
            // Keep the 500 most recent.
            if (all.length() > 500) {
                List<String> keys = new ArrayList<>();
                Iterator<String> it = all.keys();
                while (it.hasNext()) keys.add(it.next());
                final JSONObject a = all;
                keys.sort((x, y) -> Long.compare(a.optJSONObject(x).optLong("last"), a.optJSONObject(y).optLong("last")));
                for (int i = 0; i < keys.size() - 500; i++) all.remove(keys.get(i));
            }
            p.edit().putString("songs", all.toString()).apply();
        } catch (Exception ignored) { }
    }

    static class Entry { MItem item; int n; long last; }

    static List<Entry> history(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONObject all = new JSONObject(prefs(c).getString("songs", "{}"));
            Iterator<String> it = all.keys();
            while (it.hasNext()) {
                JSONObject e = all.optJSONObject(it.next());
                Entry x = new Entry();
                x.item = MItem.fromJson(e.optJSONObject("item"));
                x.n = e.optInt("n");
                x.last = e.optLong("last");
                if (x.item.playable()) out.add(x);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** The cards for Home's "Made for you" row (once there's enough listening to go on). */
    public static List<MItem> cards(Context c) {
        List<Entry> h = history(c);
        List<MItem> out = new ArrayList<>();
        if (h.size() < 3) return out;
        h.sort((a, b) -> b.n != a.n ? b.n - a.n : Long.compare(b.last, a.last));
        out.add(card(HEAVY, "Heavy Rotation", "The songs you keep playing", h.get(0).item.thumb));
        List<Entry> recent = new ArrayList<>(h);
        recent.sort((a, b) -> Long.compare(b.last, a.last));
        out.add(card(MIX, "Your Mix", "Your artists and more like them", h.size() > 1 ? h.get(1).item.thumb : h.get(0).item.thumb));
        out.add(card(DISCOVER, "Discover", "New to you, picked from what you play", recent.get(0).item.thumb));
        return out;
    }

    private static MItem card(String id, String title, String sub, String thumb) {
        MItem m = new MItem();
        m.type = MItem.PLAYLIST;
        m.browseId = id;
        m.title = title;
        m.subtitle = "Playlist • " + sub;
        m.thumb = thumb;
        return m;
    }

    public static Ytm.Page page(Context c, String id) throws Exception {
        Ytm.Page p = new Ytm.Page();
        Ytm.Shelf s = new Ytm.Shelf();
        s.list = true;
        List<Entry> h = history(c);
        switch (id) {
            case HEAVY: {
                p.title = "Heavy Rotation";
                long since = System.currentTimeMillis() - 90 * DAY;
                List<Entry> l = new ArrayList<>();
                for (Entry e : h) if (e.last >= since) l.add(e);
                l.sort((a, b) -> b.n != a.n ? b.n - a.n : Long.compare(b.last, a.last));
                for (Entry e : l) if (s.items.size() < 50 && e.item.official()) s.items.add(e.item);
                p.description = "The songs you play most, freshest first when it's a tie.";
                break;
            }
            case MIX: {
                p.title = "Your Mix";
                // Your top artists (by plays), each with their most-played song as the seed for songs like it.
                Map<String, Integer> byArtist = new HashMap<>();
                Map<String, Entry> seed = new HashMap<>();
                for (Entry e : h) {
                    String a = e.item.artist.isEmpty() ? e.item.artistId : e.item.artist;
                    if (a.isEmpty()) continue;
                    byArtist.merge(a, e.n, Integer::sum);
                    Entry cur = seed.get(a);
                    if (cur == null || e.n > cur.n) seed.put(a, e);
                }
                List<String> top = new ArrayList<>(byArtist.keySet());
                top.sort((x, y) -> byArtist.get(y) - byArtist.get(x));
                List<List<MItem>> lanes = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (String a : top.subList(0, Math.min(5, top.size()))) {
                    List<MItem> lane = new ArrayList<>();
                    lane.add(seed.get(a).item);
                    seen.add(seed.get(a).item.videoId);
                    try {
                        for (MItem m : Ytm.queue(seed.get(a).item.videoId, null, true).items)
                            if (m.official() && seen.add(m.videoId) && lane.size() < 9) lane.add(m);
                    } catch (Exception ignored) { }
                    lanes.add(lane);
                }
                interleave(lanes, s.items, 50);
                p.description = "Your favourite artists, and songs like theirs.";
                break;
            }
            default: {
                p.title = "Discover";
                Set<String> played = new HashSet<>();
                for (Entry e : h) played.add(e.item.videoId);
                List<Entry> recent = new ArrayList<>(h);
                recent.sort((a, b) -> Long.compare(b.last, a.last));
                List<List<MItem>> lanes = new ArrayList<>();
                Set<String> seen = new HashSet<>(played);
                for (Entry e : recent.subList(0, Math.min(4, recent.size()))) {
                    List<MItem> lane = new ArrayList<>();
                    try {
                        for (MItem m : Ytm.queue(e.item.videoId, null, true).items)
                            if (m.official() && seen.add(m.videoId) && lane.size() < 12) lane.add(m);
                    } catch (Exception ignored) { }
                    Collections.shuffle(lane);
                    lanes.add(lane);
                }
                interleave(lanes, s.items, 40);
                p.description = "Songs you haven't played here yet, close to what you have.";
            }
        }
        p.subtitle = "Playlist • Made for you • " + s.items.size() + " songs";
        if (!s.items.isEmpty()) p.thumb = s.items.get(0).thumb;
        p.shelves.add(s);
        if (s.items.isEmpty()) throw new Exception("Play a few more songs and this fills in");
        return p;
    }

    private static void interleave(List<List<MItem>> lanes, List<MItem> out, int max) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; out.size() < max; i++) {
            boolean any = false;
            for (List<MItem> l : lanes) if (i < l.size()) { any = true; if (ids.add(l.get(i).videoId) && out.size() < max) out.add(l.get(i)); }
            if (!any) break;
        }
    }

    /** You opened or played an album, playlist, artist or mix: it goes to the front of "Jump back in". */
    public static synchronized void visited(Context c, MItem m) {
        if (m == null || m.playable() || MItem.MORE.equals(m.type) || MItem.CATEGORY.equals(m.type)) return;
        String key = !m.browseId.isEmpty() ? m.browseId : m.playlistId;
        if (key.isEmpty() || m.title.isEmpty()) return;
        try {
            org.json.JSONArray old = new org.json.JSONArray(prefs(c).getString("places", "[]"));
            org.json.JSONArray now = new org.json.JSONArray();
            now.put(m.toJson());
            for (int i = 0; i < old.length() && now.length() < 24; i++) {
                MItem o = MItem.fromJson(old.optJSONObject(i));
                String k = !o.browseId.isEmpty() ? o.browseId : o.playlistId;
                if (!k.equals(key)) now.put(old.optJSONObject(i));
            }
            prefs(c).edit().putString("places", now.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Takes a deleted playlist out of Recently Played. */
    public static synchronized void forget(Context c, String key) {
        try {
            org.json.JSONArray old = new org.json.JSONArray(prefs(c).getString("places", "[]")), now = new org.json.JSONArray();
            for (int i = 0; i < old.length(); i++) {
                MItem o = MItem.fromJson(old.optJSONObject(i));
                if (!key.equals(o.browseId) && !key.equals(o.playlistId)) now.put(old.optJSONObject(i));
            }
            prefs(c).edit().putString("places", now.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** "Jump back in": the albums, playlists, artists and mixes you went to last, newest first. */
    public static List<MItem> places(Context c) {
        List<MItem> out = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs(c).getString("places", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(MItem.fromJson(a.optJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    /** Artists you play most (for Home's "More from" rows). */
    public static List<MItem> topArtists(Context c, int n) {
        Map<String, Integer> plays = new LinkedHashMap<>();
        Map<String, MItem> any = new HashMap<>();
        for (Entry e : history(c)) {
            if (e.item.artistId.isEmpty()) continue;
            plays.merge(e.item.artistId, e.n, Integer::sum);
            any.put(e.item.artistId, e.item);
        }
        List<String> ids = new ArrayList<>(plays.keySet());
        ids.sort((x, y) -> plays.get(y) - plays.get(x));
        List<MItem> out = new ArrayList<>();
        for (String id : ids.subList(0, Math.min(n, ids.size()))) {
            MItem a = new MItem();
            a.type = MItem.ARTIST;
            a.browseId = id;
            a.artistId = id;
            a.title = any.get(id).artist;
            out.add(a);
        }
        return out;
    }

    private Mixes() { }
}
