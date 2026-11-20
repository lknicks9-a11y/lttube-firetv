package com.lttube.tv.music;

import com.lttube.tv.NpDownloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Apple Music's catalogue (Apple's public iTunes lookup — no account needed), for complete discographies: every
 * official album, single and EP an artist has, and each album's full track list. The songs themselves play from
 * YouTube Music: each track is matched to its official release there.
 */
public final class AppleMusic {
    private static final String COUNTRY = "US";

    private static JSONArray get(HttpUrl u) throws Exception {
        Request r = new Request.Builder().url(u).header("User-Agent", "LTMusic/1.0").build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("Apple Music HTTP " + res.code());
            return new JSONObject(res.body().string()).optJSONArray("results");
        }
    }

    /** Apple's id for an artist, by name (the one whose name matches best). */
    private static final java.util.Map<String, String> ids = new java.util.concurrent.ConcurrentHashMap<>();

    public static String artistId(String name) {
        if (name == null) return null;
        String hit = ids.get(name);
        if (hit != null) return hit.isEmpty() ? null : hit;
        String id = artistIdLookup(name);
        ids.put(name, id == null ? "" : id);
        return id;
    }

    private static String artistIdLookup(String name) {
        try {
            JSONArray a = get(HttpUrl.parse("https://itunes.apple.com/search").newBuilder().addQueryParameter("term", name)
                    .addQueryParameter("entity", "musicArtist").addQueryParameter("limit", "5").addQueryParameter("country", COUNTRY).build());
            if (a == null) return null;
            String want = norm(name);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (norm(o.optString("artistName")).equals(want)) return String.valueOf(o.optLong("artistId"));
            }
            return a.length() > 0 ? String.valueOf(a.optJSONObject(0).optLong("artistId")) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static class Discography {
        public final List<MItem> albums = new ArrayList<>(), singles = new ArrayList<>(), appearsOn = new ArrayList<>();
    }

    /** Every album, and every single / EP, newest first. */
    public static Discography discography(String appleArtistId) throws Exception {
        JSONArray a = get(HttpUrl.parse("https://itunes.apple.com/lookup").newBuilder().addQueryParameter("id", appleArtistId)
                .addQueryParameter("entity", "album").addQueryParameter("limit", "200").addQueryParameter("country", COUNTRY).build());
        Discography d = new Discography();
        if (a == null) return d;
        List<JSONObject> l = new ArrayList<>();
        String me = "";
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if ("artist".equals(o.optString("wrapperType"))) me = norm(o.optString("artistName"));
            if ("collection".equals(o.optString("wrapperType"))) l.add(o);
        }
        l.sort((x, y) -> y.optString("releaseDate").compareTo(x.optString("releaseDate")));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JSONObject o : l) {
            String name = o.optString("collectionName");
            boolean single = name.endsWith(" - Single") || name.endsWith(" - EP");
            String clean = name.replaceAll(" - (Single|EP)$", "");
            // The clean and explicit copies of one record show once (the explicit one, listed first).
            String key = norm(clean) + "|" + single;
            if (!seen.add(key)) continue;
            // Theirs: their own records, and joint ones with them named as an artist ("X & Them").
            // Appears On: other people's records that name them as featured. Anything else (compilations,
            // soundtracks that just include a song of theirs) isn't shown.
            String by = o.optString("artistName");
            boolean primary = String.valueOf(o.optLong("artistId")).equals(appleArtistId);
            boolean joint = !primary && !me.isEmpty() && splitNames(by).contains(me);
            boolean featured = !primary && !joint && !me.isEmpty() && norm(name).contains(me);
            if (!primary && !joint && !featured) continue;
            boolean theirs = primary || joint;
            MItem m = new MItem();
            m.type = MItem.ALBUM;
            m.browseId = "AM:" + o.optLong("collectionId");
            m.title = clean;
            m.artist = o.optString("artistName");
            String year = o.optString("releaseDate").length() >= 4 ? o.optString("releaseDate").substring(0, 4) : "";
            m.subtitle = (single ? (name.endsWith(" - EP") ? "EP" : "Single") : "Album") + (year.isEmpty() ? "" : " • " + year);
            m.thumb = o.optString("artworkUrl100").replace("100x100bb", "600x600bb");
            (!theirs ? d.appearsOn : single ? d.singles : d.albums).add(m);
        }
        return d;
    }

    /** An album's page: Apple's full track list, each matched to the official song on YouTube Music. */
    private static final java.util.Map<String, Object[]> albums = new java.util.concurrent.ConcurrentHashMap<>();

    /** An album (kept for 20 minutes, so going back and forth is instant). */
    public static Ytm.Page album(String id) throws Exception {
        Object[] hit = albums.get(id);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 20 * 60_000L) return (Ytm.Page) hit[1];
        Ytm.Page p = albumFetch(id);
        p.shelves.addAll(AppleWeb.albumExtras(id.startsWith("AM:") ? id.substring(3) : id));
        albums.put(id, new Object[]{System.currentTimeMillis(), p});
        return p;
    }

    private static Ytm.Page albumFetch(String id) throws Exception {
        String appleId = id.startsWith("AM:") ? id.substring(3) : id;
        JSONArray a = get(HttpUrl.parse("https://itunes.apple.com/lookup").newBuilder().addQueryParameter("id", appleId)
                .addQueryParameter("entity", "song").addQueryParameter("limit", "200").addQueryParameter("country", COUNTRY).build());
        if (a == null || a.length() == 0) throw new Exception("Apple Music doesn't have that album");
        Ytm.Page p = new Ytm.Page();
        List<JSONObject> tracks = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if ("collection".equals(o.optString("wrapperType"))) {
                String name = o.optString("collectionName");
                p.title = name.replaceAll(" - (Single|EP)$", "");
                String year = o.optString("releaseDate").length() >= 4 ? o.optString("releaseDate").substring(0, 4) : "";
                p.subtitle = o.optString("artistName") + "  •  " + (name.endsWith(" - Single") ? "Single" : name.endsWith(" - EP") ? "EP" : "Album")
                        + (year.isEmpty() ? "" : "  •  " + year) + "  •  " + o.optInt("trackCount") + " songs";
                p.thumb = o.optString("artworkUrl100").replace("100x100bb", "1000x1000bb");
                p.description = o.optString("copyright");
            } else if ("track".equals(o.optString("wrapperType"))) tracks.add(o);
        }
        tracks.sort((x, y) -> x.optInt("discNumber") != y.optInt("discNumber") ? x.optInt("discNumber") - y.optInt("discNumber") : x.optInt("trackNumber") - y.optInt("trackNumber"));
        // The tracks straight from Apple Music: each finds its recording on YouTube Music only when it plays
        // (and the next few ahead of it), so the album opens at once.
        Ytm.Shelf s = new Ytm.Shelf();
        s.list = true;
        for (JSONObject t : tracks) {
            MItem m = AppleWeb.song(t);
            m.album = p.title;
            m.thumb = p.thumb.replace("1000x1000bb", "300x300bb");
            s.items.add(m);
        }
        p.shelves.add(s);
        return p;
    }

    private static java.util.Set<String> splitNames(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String p : s.split(",|&| and | x | X ")) out.add(norm(p));
        return out;
    }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]", ""); }

    private AppleMusic() { }
}
