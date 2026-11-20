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
        /** When the line ends (0 = until the next one). */
        public long endMs;
        public final String text;
        Line(long ms, String text) { this.ms = ms; this.text = text; }
    }

    /** How early lines light up: none for YouTube Music's own timing (it's exact), a touch for LRCLIB's. */
    private long lead = 60;

    public final List<Line> lines = new ArrayList<>();
    public boolean timed;
    public String source = "";

    /** The line being sung at this moment (-1 before the first). */
    public int at(long ms) {
        if (!timed) return -1;
        int i = -1;
        for (int k = 0; k < lines.size(); k++) { if (lines.get(k).ms <= ms + lead) i = k; else break; }
        return i;
    }

    /** How far through line i the singing is (0..1), for the line's light sweeping across. */
    public float progress(int i, long ms) {
        if (!timed || i < 0 || i >= lines.size()) return 0f;
        Line ln = lines.get(i);
        long start = ln.ms - lead;
        long end;
        if (ln.endMs > ln.ms) end = ln.endMs - lead;
        else {
            end = i + 1 < lines.size() ? lines.get(i + 1).ms - lead : start + 4000;
            // A long gap after a line (a solo) shouldn't stretch the sweep: lines take at most ~6 s to sing.
            end = Math.min(end - 150, start + Math.max(1200, Math.min(6000, ln.text.length() * 160L)));
        }
        if (end <= start) return 1f;
        return Math.max(0f, Math.min(1f, (ms - start) / (float) (end - start)));
    }



    private static final Pattern TAG = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?\\]");

    /** seconds: the real length of what's playing (lyrics are timed to one recording; another is off). */
    public static Lyrics find(MItem song, String ytLyricsId, int seconds) {
        if (ytLyricsId == null) try { ytLyricsId = Ytm.queue(song.videoId, null, false).lyricsId; } catch (Exception ignored) { }
        // Best: YouTube Music's own timing for this very recording — every line starts the moment it's sung.
        Lyrics yt = ytTimed(ytLyricsId);
        if (yt != null) return yt;
        Lyrics l = lrclib(song, seconds > 0 ? seconds : song.seconds);
        if (l != null) return l;
        String plain = Ytm.lyrics(ytLyricsId);
        if (plain == null || plain.trim().isEmpty()) return null;
        l = new Lyrics();
        for (String s : plain.split("\\r?\\n")) l.lines.add(new Line(0, s.trim()));
        l.source = "Lyrics from YouTube Music";
        return l;
    }

    private static Lyrics ytTimed(String id) {
        org.json.JSONArray a = Ytm.timedLyrics(id);
        if (a == null) return null;
        Lyrics l = new Lyrics();
        l.lead = 0;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            JSONObject cue = o.optJSONObject("cueRange");
            String text = o.optString("lyricLine", "").trim();
            if (cue == null) continue;
            Line ln = new Line(cue.optLong("startTimeMilliseconds"), text);
            ln.endMs = cue.optLong("endTimeMilliseconds");
            l.lines.add(ln);
        }
        if (l.lines.isEmpty()) return null;
        l.lines.sort((x, y) -> Long.compare(x.ms, y.ms));
        l.timed = true;
        l.source = "Lyrics from YouTube Music";
        return l;
    }

    private static Lyrics lrclib(MItem song, int secs) {
        String artist = clean(song.artist), title = clean(song.title);
        if (title.isEmpty()) return null;
        try {
            HttpUrl.Builder u = HttpUrl.parse("https://lrclib.net/api/get").newBuilder()
                    .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist);
            if (!song.album.isEmpty()) u.addQueryParameter("album_name", song.album);
            if (secs > 0) u.addQueryParameter("duration", String.valueOf(secs));
            JSONObject o = getObj(u.build());
            Lyrics l = o == null ? null : of(o);
            if (l != null && (l.timed || secs <= 0)) return l;
            // Otherwise the closest recording by length: timed lyrics only count when they're within 3 seconds
            // of this one, or they'd run ahead or behind the singing.
            JSONArray a = getArr(HttpUrl.parse("https://lrclib.net/api/search").newBuilder()
                    .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist).build());
            Lyrics best = null, plainOnly = l;
            double bestDiff = 1e9;
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.optJSONObject(i);
                if (c == null) continue;
                double diff = secs > 0 ? Math.abs(c.optDouble("duration", secs) - secs) : 0;
                Lyrics x = of(c);
                if (x == null) continue;
                if (x.timed && diff <= 3 && diff < bestDiff) { best = x; bestDiff = diff; }
                else if (plainOnly == null && diff <= 20) { plainOnly = x; }
            }
            if (best != null) return best;
            // Lyrics timed to a different recording are still worth reading: shown untimed.
            if (plainOnly != null && plainOnly.timed) { plainOnly.timed = false; }
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

    /** Swear words shown with stars after the first letter ("f***", "s***"), the rest of the word kept. */
    public static String clean(String line) {
        if (line == null || line.isEmpty()) return line;
        String out = line;
        // Anywhere in a word (motherf***er, bulls***).
        out = star(out, "(?i)(f)(uck)", false);
        out = star(out, "(?i)(s)(hit)", false);
        // Whole words only (so "Dickens" or "class" stay as they are).
        out = star(out, "(?i)(?<![a-z])(n)(igga|igger)(?=s?|z?)", false);
        out = star(out, "(?i)(?<![a-z])(b)(itch)", false);
        out = star(out, "(?i)(?<![a-z])(p)(ussy)(?![a-z])", false);
        out = star(out, "(?i)(?<![a-z])(c)(unt)", false);
        out = star(out, "(?i)(?<![a-z])(d)(ick)(?![a-z])", false);
        out = star(out, "(?i)(?<![a-z])(a)(sshole)", false);
        return out;
    }

    private static String star(String s, String regex, boolean unused) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < m.group(2).length(); i++) stars.append('*');
            m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(m.group(1) + stars));
        }
        m.appendTail(b);
        return b.toString();
    }
}
