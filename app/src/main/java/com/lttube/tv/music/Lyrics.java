package com.lttube.tv.music;

import com.lttube.tv.NpDownloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * A song's lyrics: timed line by line when LRCLIB (a free, open lyrics library) has them, so the line being sung
 * lights up; otherwise YouTube Music's own plain lyrics.
 */
public final class Lyrics {
    public static class Line {
        public final long ms;
        public final String text;
        Line(long ms, String text) { this.ms = ms; this.text = text; }
    }

    public final List<Line> lines = new ArrayList<>();
    public boolean timed;
    public String source = "";

    /** The line being sung at this moment (-1 before the first). */
    public int at(long ms) {
        if (!timed) return -1;
        int i = -1;
        for (int k = 0; k < lines.size(); k++) { if (lines.get(k).ms <= ms + 250) i = k; else break; }
        return i;
    }

    private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]");

    public static Lyrics find(MItem song, String ytLyricsId) {
        Lyrics l = lrclib(song);
        if (l != null) return l;
        if (ytLyricsId == null) try { ytLyricsId = Ytm.queue(song.videoId, null, false).lyricsId; } catch (Exception ignored) { }
        String plain = Ytm.lyrics(ytLyricsId);
        if (plain == null || plain.trim().isEmpty()) return null;
        l = new Lyrics();
        for (String s : plain.split("\\r?\\n")) l.lines.add(new Line(0, s.trim()));
        l.source = "Lyrics from YouTube Music";
        return l;
    }

    private static Lyrics lrclib(MItem song) {
        String artist = clean(song.artist), title = clean(song.title);
        if (title.isEmpty()) return null;
        try {
            HttpUrl.Builder u = HttpUrl.parse("https://lrclib.net/api/get").newBuilder()
                    .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist);
            if (!song.album.isEmpty()) u.addQueryParameter("album_name", song.album);
            if (song.seconds > 0) u.addQueryParameter("duration", String.valueOf(song.seconds));
            JSONObject o = getObj(u.build());
            Lyrics l = o == null ? null : of(o);
            if (l != null) return l;
            // Close matches: same song, slightly different title or length.
            JSONArray a = getArr(HttpUrl.parse("https://lrclib.net/api/search").newBuilder()
                    .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist).build());
            Lyrics plainOnly = null;
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null) continue;
                if (song.seconds > 0 && Math.abs(c.optDouble("duration", song.seconds) - song.seconds) > 8) continue;
                Lyrics x = of(c);
                if (x != null && x.timed) return x;
                if (x != null && plainOnly == null) plainOnly = x;
            }
            return plainOnly;
        } catch (Exception e) {
            return null;
        }
    }

    private static Lyrics of(JSONObject o) {
        if (o.optBoolean("instrumental")) return null;
        String synced = o.optString("syncedLyrics", "");
        Lyrics l = new Lyrics();
        l.source = "Lyrics from LRCLIB";
        if (!synced.isEmpty() && !"null".equals(synced)) {
            for (String row : synced.split("\\r?\\n")) {
                Matcher m = TAG.matcher(row);
                List<Long> times = new ArrayList<>();
                int end = 0;
                while (m.find() && m.start() == end) {
                    long ms = Long.parseLong(m.group(1)) * 60_000 + Long.parseLong(m.group(2)) * 1000;
                    if (m.group(3) != null) { String f = (m.group(3) + "00").substring(0, 3); ms += Long.parseLong(f); }
                    times.add(ms);
                    end = m.end();
                }
                String text = row.substring(end).trim();
                for (long t : times) l.lines.add(new Line(t, text));
            }
            l.lines.sort((a, b) -> Long.compare(a.ms, b.ms));
            l.timed = !l.lines.isEmpty();
            if (l.timed) return l;
        }
        String plain = o.optString("plainLyrics", "");
        if (plain.isEmpty() || "null".equals(plain)) return null;
        for (String s : plain.split("\\r?\\n")) l.lines.add(new Line(0, s.trim()));
        return l;
    }

    /** "Song (Official Video)" → "Song"; "Artist - Topic" → "Artist". */
    static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\s*[(\\[](official|lyric|audio|video|visualizer|music video|hd|4k)[^)\\]]*[)\\]]", "")
                .replaceAll("(?i)\\s*-\\s*topic$", "").trim();
    }

    private static String body(HttpUrl u) throws Exception {
        Request r = new Request.Builder().url(u).header("User-Agent", "LTMusic for Fire TV (https://github.com/lknicks9-a11y/lttube-firetv)").build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            if (!res.isSuccessful() || res.body() == null) return null;
            return res.body().string();
        }
    }

    private static JSONObject getObj(HttpUrl u) throws Exception { String b = body(u); return b == null ? null : new JSONObject(b); }
    private static JSONArray getArr(HttpUrl u) throws Exception { String b = body(u); return b == null ? null : new JSONArray(b); }

    private Lyrics() { }
}
