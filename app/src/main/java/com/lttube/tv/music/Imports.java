package com.lttube.tv.music;

import android.content.Context;
import android.content.SharedPreferences;

import com.lttube.tv.NpDownloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Brings in a playlist from Spotify or Apple Music by its share link: reads the songs from the playlist's public
 * page, finds each one's official release on YouTube Music, and keeps the playlist (on your YouTube account when
 * you're signed in, otherwise on this TV).
 */
public final class Imports {
    public static class Track { public String title = "", artist = "", album = ""; public int seconds; }

    public static class Source {
        public String name = "", cover = "", service = "";
        public final List<Track> tracks = new ArrayList<>();
    }

    public interface Progress { void at(int done, int total, String now); }

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    // ------------------------------------------------------------------ reading the playlist
    public static Source read(String link) throws Exception {
        link = link.trim();
        if (link.contains("spotify")) return spotify(link);
        if (link.contains("music.apple.com") || link.contains("itunes.apple.com")) return apple(link);
        throw new Exception("That isn't a Spotify or Apple Music playlist link");
    }

    static Source spotify(String link) throws Exception {
        Matcher m = Pattern.compile("(playlist|album)[/:]([A-Za-z0-9]{10,})").matcher(link);
        if (!m.find()) throw new Exception("Couldn't find the playlist in that Spotify link");
        String html = get("https://open.spotify.com/embed/" + m.group(1) + "/" + m.group(2));
        Matcher d = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL).matcher(html);
        if (!d.find()) throw new Exception("Spotify didn't show that playlist (is it public?)");
        JSONObject root = new JSONObject(d.group(1));
        JSONObject entity = Ytm.findObj(root, "entity");
        Source s = new Source();
        s.service = "Spotify";
        if (entity != null) {
            s.name = entity.optString("name", entity.optString("title"));
            JSONObject cover = entity.optJSONObject("coverArt");
            JSONArray src = cover == null ? null : cover.optJSONArray("sources");
            if (src != null && src.length() > 0) s.cover = src.optJSONObject(src.length() - 1).optString("url");
            JSONArray list = entity.optJSONArray("trackList");
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject t = list.optJSONObject(i);
                if (t == null) continue;
                Track tr = new Track();
                tr.title = t.optString("title");
                tr.artist = t.optString("subtitle").split(",")[0].trim();
                tr.seconds = (int) (t.optLong("duration") / 1000);
                if (!tr.title.isEmpty()) s.tracks.add(tr);
            }
        }
        if (s.tracks.isEmpty()) throw new Exception("No songs found in that Spotify playlist (is it public?)");
        return s;
    }

    static Source apple(String link) throws Exception {
        String html = get(link);
        Source s = new Source();
        s.service = "Apple Music";
        Matcher og = Pattern.compile("<meta property=\"og:title\" content=\"([^\"]*)\"").matcher(html);
        if (og.find()) s.name = unescape(og.group(1)).replaceAll("\\s+(by|on)\\s+.*Apple Music.*$", "").trim();
        Matcher ogi = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]*)\"").matcher(html);
        if (ogi.find()) s.cover = ogi.group(1);
        // The page carries its data as JSON: every song is an object with a title and an artist name.
        Matcher d = Pattern.compile("<script[^>]*id=\"serialized-server-data\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
        if (d.find()) {
            Object root = new org.json.JSONTokener(d.group(1)).nextValue();
            java.util.Set<String> seen = new java.util.HashSet<>();
            walkApple(root, s, seen, 0);
        }
        if (s.tracks.isEmpty()) {
            // Older pages: the schema.org description lists the songs.
            Matcher ld = Pattern.compile("<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
            while (ld.find()) {
                try {
                    JSONObject o = new JSONObject(ld.group(1));
                    JSONArray tracks = o.optJSONArray("track");
                    if (tracks != null) for (int i = 0; i < tracks.length(); i++) {
                        JSONObject t = tracks.optJSONObject(i);
                        Track tr = new Track();
                        tr.title = t.optString("name");
                        JSONObject by = t.optJSONObject("byArtist");
                        if (by != null) tr.artist = by.optString("name");
                        if (!tr.title.isEmpty()) s.tracks.add(tr);
                    }
                } catch (Exception ignored) { }
            }
        }
        if (s.tracks.isEmpty()) throw new Exception("No songs found in that Apple Music playlist (is it shared publicly?)");
        if (s.name.isEmpty()) s.name = "Apple Music playlist";
        return s;
    }

    private static void walkApple(Object n, Source s, java.util.Set<String> seen, int depth) {
        if (n == null || depth > 40) return;
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            String title = o.optString("title", ""), artist = o.optString("artistName", "");
            if (!title.isEmpty() && !artist.isEmpty() && (o.has("duration") || o.has("trackNumber") || o.has("contentDescriptor"))) {
                if (seen.add(title + "|" + artist)) {
                    Track t = new Track();
                    t.title = title;
                    t.artist = artist.split(",|&| feat\\.")[0].trim();
                    t.seconds = (int) (o.optLong("duration") / 1000);
                    s.tracks.add(t);
                }
                return;
            }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) walkApple(o.opt(it.next()), s, seen, depth + 1);
        } else if (n instanceof JSONArray) {
            JSONArray a = (JSONArray) n;
            for (int i = 0; i < a.length(); i++) walkApple(a.opt(i), s, seen, depth + 1);
        }
    }

    private static String unescape(String s) { return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'"); }

    private static String get(String url) throws Exception {
        Request r = new Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9").build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("The playlist page didn't open (HTTP " + res.code() + ")");
            return res.body().string();
        }
    }

    // ------------------------------------------------------------------ finding the songs
    /** Each song's official release on YouTube Music (several at a time). Songs that can't be found are skipped. */
    public static List<MItem> match(Source src, Progress p) {
        ExecutorService ex = Executors.newFixedThreadPool(4);
        List<Future<MItem>> fs = new ArrayList<>();
        for (Track t : src.tracks) fs.add(ex.submit(() -> transfer(t)));
        List<MItem> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < fs.size(); i++) {
            MItem m = null;
            try { m = fs.get(i).get(40, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
            if (m != null && seen.add(m.appleId.isEmpty() ? m.videoId : m.appleId)) out.add(m);
            if (p != null) p.at(i + 1, fs.size(), src.tracks.get(i).title);
        }
        ex.shutdownNow();
        return out;
    }

    /**
     * One song of a transferred playlist, matched the way LT Music plays songs: first the exact song in Apple
     * Music's catalogue (same title, artist, length, album), then that song's own recording on YouTube Music
     * (the same match playing it uses, saved so it always plays the same one). Only if Apple doesn't have it,
     * a direct YouTube Music match.
     */
    static MItem transfer(Track t) {
        try {
            String main = t.artist.split(",|&| feat\\.| ft\\.| x ")[0].trim();
            MItem best = null;
            int bestScore = Integer.MIN_VALUE;
            for (String q : new String[]{t.title + " " + main, plainTitle(t.title) + " " + main}) {
                for (JSONObject o : AppleWeb.itunes(q, "song", 15)) {
                    MItem m = AppleWeb.song(o);
                    m.mvType = "";
                    int sc = score(t, m);
                    if (sc > bestScore) { bestScore = sc; best = m; }
                }
                if (bestScore >= 12) break;
            }
            if (best != null && bestScore >= 10) {
                try { MusicPlayer.resolve(best); } catch (Exception ignored) { }
                if (!best.videoId.isEmpty()) return best;
            }
        } catch (Exception ignored) { }
        return find(t);
    }

    private static final java.util.regex.Pattern VERSION = java.util.regex.Pattern.compile(
            "(?i)\\b(remix|live|acoustic|sped|slowed|instrumental|karaoke|version|edit|demo|mix|cover|remaster(ed)?|mono|session|a cappella|acapella|clean|reprise|extended|radio)\\b");

    /**
     * The same song on YouTube Music: its official release, same title, same artist, same length (and album when we
     * know it). Other versions (remixes, live, sped up, edits) only match when the song itself is one.
     */
    static MItem find(Track t) {
        try {
            MItem best = null;
            int bestScore = Integer.MIN_VALUE;
            String main = t.artist.split(",|&| feat\\.| ft\\.| x ")[0].trim();
            String[] queries = {t.title + " " + main, plainTitle(t.title) + " " + main};
            java.util.Set<String> tried = new java.util.HashSet<>();
            for (String q : queries) {
                if (!tried.add(q)) continue;
                for (Ytm.Shelf s : Ytm.search(q, true).shelves) for (MItem m : s.items) {
                    if (!m.playable() || !m.official()) continue;
                    int score = score(t, m);
                    if (score > bestScore) { bestScore = score; best = m; }
                }
                if (bestScore >= 12) break;
            }
            if (bestScore >= 8) return best;
            // Not among the songs: everything else, still official (never uploads), and only a close match.
            for (Ytm.Shelf s : Ytm.search(t.title + " " + main, false).shelves) for (MItem m : s.items) {
                if (!m.playable() || m.slop() || m.mvType.endsWith("UGC")) continue;
                int score = score(t, m) - 1;
                if (score > bestScore) { bestScore = score; best = m; }
            }
            return bestScore >= 8 ? best : null;
        } catch (Exception e) {
            return null;
        }
    }

    static int score(Track t, MItem m) {
        int score = 0;
        String a = norm(plainTitle(t.title)), b = norm(plainTitle(m.title));
        if (a.equals(b)) score += 6;
        else if (!a.isEmpty() && !b.isEmpty() && (a.contains(b) || b.contains(a))) score += 3;
        else return -100;
        // Artist: any of the song's artists on the YouTube one counts.
        boolean artist = false;
        for (String part : t.artist.split(",|&| feat\\.| ft\\.| x | and ")) {
            String p = norm(part);
            if (p.length() > 1 && (norm(m.artist).contains(p) || p.contains(norm(m.artist)) && norm(m.artist).length() > 1)) artist = true;
        }
        score += artist ? 4 : -5;
        if (t.seconds > 0 && m.seconds > 0) {
            int d = Math.abs(t.seconds - m.seconds);
            score += d <= 2 ? 4 : d <= 5 ? 2 : d <= 12 ? -1 : -5;
        }
        if (!t.album.isEmpty() && !m.album.isEmpty() && norm(plainTitle(t.album)).equals(norm(plainTitle(m.album)))) score += 2;
        // A different version of the song (remix, live, sped up…) is wrong unless the song is that version.
        java.util.regex.Matcher vy = VERSION.matcher(m.title + " " + m.album), va = VERSION.matcher(t.title);
        boolean ytVersion = vy.find(), appleVersion = va.find();
        if (ytVersion && !appleVersion) score -= 5;
        if (m.mvType.endsWith("ATV")) score += 1;
        return score;
    }

    /** "Song (feat. X) [Remastered 2011]" → "Song" for comparing. */
    static String plainTitle(String s) {
        return s == null ? "" : s.replaceAll("(?i)\\s*[(\\[](feat|ft|with|prod)[^)\\]]*[)\\]]", "").replaceAll("(?i)\\s+-\\s+(single|ep)$", "").trim();
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.US).replaceAll("\\(.*?\\)|\\[.*?\\]|- .*remaster.*", "").replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ keeping it
    /** Saves the playlist: to your YouTube account if you're signed in (falls back to this TV). Returns where. */
    public static String save(Context c, Source src, List<MItem> songs) {
        if (Ytm.signedIn()) {
            try {
                List<String> ids = new ArrayList<>();
                for (MItem m : songs) ids.add(m.videoId);
                String id = Ytm.createPlaylist(src.name, "Imported from " + src.service + " by LT Music", ids);
                if (id != null && !id.isEmpty()) return "account";
            } catch (Exception ignored) { }
        }
        try {
            SharedPreferences p = c.getSharedPreferences("music", Context.MODE_PRIVATE);
            JSONArray all = new JSONArray(p.getString("imports", "[]"));
            JSONObject o = new JSONObject().put("id", String.valueOf(System.currentTimeMillis())).put("name", src.name)
                    .put("cover", src.cover).put("service", src.service);
            JSONArray a = new JSONArray();
            for (MItem m : songs) a.put(m.toJson());
            o.put("songs", a);
            all.put(o);
            p.edit().putString("imports", all.toString()).apply();
        } catch (Exception ignored) { }
        return "tv";
    }

    /** Takes an imported playlist off this TV. */
    public static void delete(Context c, String id) {
        try {
            SharedPreferences p = c.getSharedPreferences("music", Context.MODE_PRIVATE);
            JSONArray all = new JSONArray(p.getString("imports", "[]")), keep = new JSONArray();
            for (int i = 0; i < all.length(); i++) if (!id.equals("LOCAL:" + all.optJSONObject(i).optString("id"))) keep.put(all.optJSONObject(i));
            p.edit().putString("imports", keep.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Playlists imported onto this TV, as cards for Your Library. */
    public static List<MItem> local(Context c) {
        List<MItem> out = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(c.getSharedPreferences("music", Context.MODE_PRIVATE).getString("imports", "[]"));
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                MItem m = new MItem();
                m.type = MItem.PLAYLIST;
                m.title = o.optString("name");
                m.subtitle = "Playlist • From " + o.optString("service");
                m.thumb = o.optString("cover");
                m.browseId = "LOCAL:" + o.optString("id");
                out.add(m);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** An imported playlist as a page. */
    public static Ytm.Page page(Context c, String id) throws Exception {
        JSONArray all = new JSONArray(c.getSharedPreferences("music", Context.MODE_PRIVATE).getString("imports", "[]"));
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (!id.equals("LOCAL:" + o.optString("id"))) continue;
            Ytm.Page p = new Ytm.Page();
            p.title = o.optString("name");
            p.thumb = o.optString("cover");
            Ytm.Shelf s = new Ytm.Shelf();
            s.list = true;
            JSONArray a = o.optJSONArray("songs");
            if (a != null) for (int k = 0; k < a.length(); k++) s.items.add(MItem.fromJson(a.optJSONObject(k)));
            p.subtitle = "Playlist • From " + o.optString("service") + " • " + s.items.size() + " songs";
            p.shelves.add(s);
            return p;
        }
        throw new Exception("That playlist isn't on this TV any more");
    }

    private Imports() { }
}
