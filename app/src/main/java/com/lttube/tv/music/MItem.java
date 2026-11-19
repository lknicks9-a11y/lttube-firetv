package com.lttube.tv.music;

import org.json.JSONObject;

/** A song, album, playlist, artist or category on YouTube Music. */
public class MItem {
    public static final String SONG = "song", ALBUM = "album", PLAYLIST = "playlist", ARTIST = "artist", CATEGORY = "category";

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
        if (mvType.endsWith("ATV")) return true;
        if (mvType.endsWith("UGC") || mvType.contains("PODCAST")) return false;
        return !album.isEmpty();
    }

    /** A music video: plays as video, in the video player. */
    public boolean video;
    /** Mood / genre tiles carry their colour. */
    public int color;

    public boolean playable() { return SONG.equals(type) && !videoId.isEmpty(); }

    /** Square art at a size that looks sharp on a TV. */
    public static String art(String url, int px) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) url = "https:" + url;
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
                    .put("params", params).put("seconds", seconds).put("color", color).put("artistId", artistId).put("video", video).put("mvType", mvType);
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
        return m;
    }

    public static String time(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s) : String.format(java.util.Locale.US, "%d:%02d", m, s);
    }
}
