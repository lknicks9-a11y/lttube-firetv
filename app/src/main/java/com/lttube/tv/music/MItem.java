package com.lttube.tv.music;

import org.json.JSONObject;

/** A song, album, playlist, artist or category on YouTube Music. */
public class MItem {
    public static final String SONG = "song", ALBUM = "album", PLAYLIST = "playlist", ARTIST = "artist", CATEGORY = "category",
            /** "See all" of a shelf: opens everything in it as a grid. */
            MORE = "more";

    /** Not real songs: sped-up / slowed edits, AI covers, karaoke, "type beats" and the like. */
    private static final java.util.regex.Pattern SLOP = java.util.regex.Pattern.compile(
            "(?i)\\b(sped ?up|slowed|reverb|nightcore|8d audio|type beat|ai cover|ai version|ai generated|karaoke|instrumental version|lofi version|tribute to|in the style of|made famous by|cover version)\\b");

    public boolean slop() { return SLOP.matcher(title).find() || SLOP.matcher(artist).find(); }

    public String type = SONG;
    public String title = "";
    /** "Artist • Album" or "Album • 2019" — what YouTube Music shows under the title. */
    public String subtitle = "";
    public String artist = "";
    public String album = "";
    /** The artist's channel id (UC…), for their picture and "About the artist". */
    public String artistId = "";
    public String thumb = "";
    public String videoId = "";
    /** The song's Apple Music id: its details and cover come from Apple; its audio is found on YouTube Music when it plays. */
    public String appleId = "";
    /** Explicit (Apple / YouTube Music's "E"); clean and non-explicit versions are false. */
    public boolean explicit;
    /** For playing an album / playlist / mix. */
    public String playlistId = "";
    /** For opening an album / playlist / artist page. */
    public String browseId = "";
    public String params = "";
    public int seconds;
    /** YouTube Music's kind of track: MUSIC_VIDEO_TYPE_ATV (official audio — the release on every music service),
     *  _OMV (official video), _UGC (someone's upload), _PODCAST_EPISODE. */
    public String mvType = "";

    /** An official release — the song as it is on Spotify / Apple Music, not an upload or a clip. */
    public boolean official() {
        if (!playable()) return true;
        if (slop()) return false;
        if (!appleId.isEmpty()) return true;
        if (mvType.endsWith("ATV")) return true;
        if (mvType.endsWith("UGC") || mvType.contains("PODCAST")) return false;
        return !album.isEmpty();
    }

    /** A music video: plays as video, in the video player. */
    public boolean video;
    /** Mood / genre tiles carry their colour. */
    public int color;

    public boolean playable() { return SONG.equals(type) && (!videoId.isEmpty() || !appleId.isEmpty()); }

    /** One key per song, whether or not its audio has been found yet. */
    public String key() { return !videoId.isEmpty() ? videoId : "am" + appleId; }

    /** Square art at a size that looks sharp on a TV. */
    public static String art(String url, int px) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) url = "https:" + url;
        if (url.contains("mzstatic.com")) {
            // Only the size changes; Apple's own crop in the address stays (it knows how the picture is meant to
            // fill a square — switching it to "fit inside" turned wide pictures into thin, blurry strips).
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/(\\d+)x(\\d+)([^/]*)\\.(jpg|png|webp)(\\?.*)?$").matcher(url);
            if (!m.find()) return url;
            int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
            if (Math.max(w, h) >= px) return url;
            float k = px / (float) Math.max(w, h);
            return url.substring(0, m.start()) + "/" + Math.round(w * k) + "x" + Math.round(h * k) + m.group(3) + ".jpg" + (m.group(5) == null ? "" : m.group(5));
        }
        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            return url.replaceAll("=w\\d+-h\\d+[^&]*$", "=w" + px + "-h" + px + "-l90-rj").replaceAll("=s\\d+[^&]*$", "=s" + px);
        }
        return url;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type).put("title", title).put("subtitle", subtitle).put("artist", artist).put("album", album)
                    .put("thumb", thumb).put("videoId", videoId).put("playlistId", playlistId).put("browseId", browseId)
                    .put("params", params).put("seconds", seconds).put("color", color).put("artistId", artistId).put("video", video).put("mvType", mvType).put("appleId", appleId).put("explicit", explicit);
        } catch (Exception ignored) { }
        return o;
    }

    public static MItem fromJson(JSONObject o) {
        MItem m = new MItem();
        if (o == null) return m;
        m.type = o.optString("type", SONG);
        m.title = o.optString("title");
        m.subtitle = o.optString("subtitle");
        m.artist = o.optString("artist");
        m.album = o.optString("album");
        m.thumb = o.optString("thumb");
        m.videoId = o.optString("videoId");
        m.playlistId = o.optString("playlistId");
        m.browseId = o.optString("browseId");
        m.params = o.optString("params");
        m.seconds = o.optInt("seconds");
        m.color = o.optInt("color");
        m.artistId = o.optString("artistId");
        m.video = o.optBoolean("video");
        m.mvType = o.optString("mvType");
        m.appleId = o.optString("appleId");
        m.explicit = o.optBoolean("explicit");
        return m;
    }

    public static String time(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) : String.format(java.util.Locale.US, "%d:%02d", m, s);
    }
}
