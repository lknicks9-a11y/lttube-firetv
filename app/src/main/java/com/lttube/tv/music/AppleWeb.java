package com.lttube.tv.music;

import com.lttube.tv.NpDownloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Apple Music's public catalogue: its Browse page, charts, genre charts, artist pages, editorial playlists and
 * search, read from music.apple.com and Apple's public feeds (no account). Songs come back unplayed — each is
 * matched to its official release on YouTube Music when it plays (see MusicPlayer).
 */
public final class AppleWeb {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Map<String, Object[]> cache = new HashMap<>();

    static String get(String url) throws Exception {
        Request r = new Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9").build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("HTTP " + res.code());
            return res.body().string();
        }
    }

    /** A music.apple.com page's data, cached for 20 minutes. */
    static Object[] page(String url) throws Exception {
        synchronized (cache) {
            Object[] hit = cache.get(url);
            if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 20 * 60_000L) return hit;
        }
        String html = get(url);
        Matcher d = Pattern.compile("<script[^>]*id=\"serialized-server-data\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
        if (!d.find()) throw new Exception("Couldn't read that page");
        Object root = new org.json.JSONTokener(d.group(1)).nextValue();
        Matcher og = Pattern.compile("<meta property=\"og:image\" content=\"([^\"]*)\"").matcher(html);
        Object[] v = {System.currentTimeMillis(), root, og.find() ? og.group(1) : ""};
        synchronized (cache) { cache.put(url, v); }
        return v;
    }

    // ------------------------------------------------------------------ sections and items
    static class Section {
        String id = "", kind = "", title = "";
        final List<MItem> items = new ArrayList<>();
        JSONObject raw;
    }

    static List<Section> sections(Object root) {
        List<JSONObject> raw = new ArrayList<>();
        collectSections(root, raw, 0);
        List<Section> out = new ArrayList<>();
        for (JSONObject o : raw) {
            Section s = new Section();
            s.raw = o;
            s.id = o.optString("id");
            s.kind = o.optString("itemKind");
            s.title = titleOf(o.opt("header"));
            JSONArray items = o.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.optJSONObject(i);
                if (it == null) continue;
                // "Top Songs" on artist pages sits inside a featured block: its songs are one level down.
                if (s.kind.equals("artistFeaturedContentAndTracks")) {
                    List<JSONObject> tracks = new ArrayList<>();
                    collectTracks(it, tracks, 0);
                    for (JSONObject t : tracks) { MItem m = item(t); if (m != null) s.items.add(m); }
                    if (s.title.isEmpty()) s.title = titleOf(it.opt("tracksHeader"));
                    continue;
                }
                MItem m = item(it);
                if (m != null) s.items.add(m);
            }
            if (s.title.isEmpty()) s.title = guessTitle(s.id);
            out.add(s);
        }
        return out;
    }

    private static void collectSections(Object n, List<JSONObject> out, int d) {
        if (n == null || d > 30) return;
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            if (o.has("itemKind") && o.has("items")) { out.add(o); return; }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) collectSections(o.opt(it.next()), out, d + 1);
        } else if (n instanceof JSONArray) {
            JSONArray a = (JSONArray) n;
            for (int i = 0; i < a.length(); i++) collectSections(a.opt(i), out, d + 1);
        }
    }

    private static void collectTracks(Object n, List<JSONObject> out, int d) {
        if (n == null || d > 20) return;
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            if (o.has("duration") && o.has("title")) { out.add(o); return; }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) collectTracks(o.opt(it.next()), out, d + 1);
        } else if (n instanceof JSONArray) {
            JSONArray a = (JSONArray) n;
            for (int i = 0; i < a.length(); i++) collectTracks(a.opt(i), out, d + 1);
        }
    }

    private static String titleOf(Object header) {
        if (header == null) return "";
        JSONObject tl = Ytm.findObj(header, "titleLink");
        if (tl != null && !tl.optString("title").isEmpty()) return tl.optString("title");
        return firstString(header, "title", 0);
    }

    private static String firstString(Object n, String key, int d) {
        if (n == null || d > 8) return "";
        if (n instanceof JSONObject) {
            JSONObject o = (JSONObject) n;
            Object v = o.opt(key);
            if (v instanceof String && !((String) v).isEmpty()) return (String) v;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) { String s = firstString(o.opt(it.next()), key, d + 1); if (!s.isEmpty()) return s; }
        } else if (n instanceof JSONArray) {
            JSONArray a = (JSONArray) n;
            for (int i = 0; i < a.length(); i++) { String s = firstString(a.opt(i), key, d + 1); if (!s.isEmpty()) return s; }
        }
        return "";
    }

    private static String guessTitle(String id) {
        String s = id.toLowerCase(java.util.Locale.US);
        if (s.contains("topsongs")) return "Top Songs";
        if (s.contains("artistsingles")) return "Singles & EPs";
        if (s.contains("albums")) return "Albums";
        if (s.contains("appearson")) return "Appears On";
        if (s.contains("similarartists")) return "Similar Artists";
        if (s.contains("featured-artists")) return "Featured Artists";
        return "";
    }

    /** A shelf item as one of ours: song, album, playlist or artist (stations, videos and the like are left out). */
    static MItem item(JSONObject o) {
        JSONObject cd = Ytm.findObj(o.optJSONObject("segue"), "contentDescriptor");
        String kind = cd == null ? "" : cd.optString("kind");
        String id = cd == null ? "" : Ytm.findObj(cd, "identifiers") == null ? "" : cd.optJSONObject("identifiers").optString("storeAdamID");
        String url = cd == null ? "" : cd.optString("url");
        MItem m = new MItem();
        m.title = o.optString("title", o.optString("name", ""));
        if (m.title.isEmpty()) {
            JSONArray tl = o.optJSONArray("titleLinks");
            if (tl != null && tl.length() > 0) m.title = tl.optJSONObject(0).optString("title");
        }
        m.thumb = art(o, 600);
        JSONArray sl = o.optJSONArray("subtitleLinks");
        if (sl != null && sl.length() > 0) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < sl.length(); i++) { String t = sl.optJSONObject(i).optString("title"); if (!t.isEmpty()) { if (b.length() > 0) b.append(", "); b.append(t); } }
            m.artist = b.toString();
        } else m.artist = o.optString("subtitle", o.optString("artistName", ""));
        if (o.has("duration") && (kind.equals("album") || kind.equals("song") || kind.isEmpty())) {
            // A song (in a track list it points to its album; the song's own id is in its play action).
            m.type = MItem.SONG;
            m.appleId = firstString(o.opt("playAction"), "targetId", 0);
            if (m.appleId.isEmpty()) m.appleId = id;
            m.seconds = (int) (o.optLong("duration") / 1000);
            m.subtitle = m.artist;
            m.mvType = "";
            return m.title.isEmpty() ? null : m;
        }
        switch (kind) {
            case "album":
                m.type = MItem.ALBUM;
                m.title = m.title.replaceAll(" - (Single|EP)$", "");
                m.browseId = "AM:" + id;
                m.subtitle = m.artist;
                break;
            case "playlist":
                m.type = MItem.PLAYLIST;
                m.browseId = "AMP:" + url;
                m.subtitle = m.artist.isEmpty() ? "Playlist" : m.artist;
                break;
            case "song":
                m.type = MItem.SONG;
                m.appleId = id;
                m.subtitle = m.artist;
                m.mvType = "";
                return id.isEmpty() || m.title.isEmpty() ? null : m;
            case "artist":
                m.type = MItem.ARTIST;
                m.browseId = "AMA:" + id;
                m.subtitle = "Artist";
                break;
            default:
                return null;
        }
        if (id.isEmpty() || m.title.isEmpty()) return null;
        return m;
    }

    /** An Apple artwork address at a size (their links are templates with {w}x{h}). */
    static String art(Object holder, int px) {
        JSONObject a = holder instanceof JSONObject ? ((JSONObject) holder).optJSONObject("artwork") : null;
        if (a == null) a = Ytm.findObj(holder, "artwork");
        if (a == null) return "";
        JSONObject dict = a.optJSONObject("dictionary");
        String u = (dict != null ? dict : a).optString("url");
        return tpl(u, px);
    }

    static String tpl(String u, int px) {
        if (u == null || u.isEmpty()) return "";
        return u.replace("{w}", String.valueOf(px)).replace("{h}", String.valueOf(px)).replace("{c}", "bb").replace("{f}", "jpg");
    }

    /** The looping video inside a motion-artwork block (Apple's "…VideoArtwork"), or "". */
    static String motion(Object holder) {
        if (holder == null) return "";
        List<Object> stack = new ArrayList<>();
        stack.add(holder);
        while (!stack.isEmpty()) {
            Object o = stack.remove(stack.size() - 1);
            if (o instanceof JSONObject) {
                JSONObject j = (JSONObject) o;
                String v = j.optString("video", "");
                if (v.startsWith("https://") && v.contains(".m3u8")) return v;
                for (java.util.Iterator<String> it = j.keys(); it.hasNext(); ) stack.add(j.opt(it.next()));
            } else if (o instanceof JSONArray) for (int i = 0; i < ((JSONArray) o).length(); i++) stack.add(((JSONArray) o).opt(i));
        }
        return "";
    }

    /** The first "bgColor" Apple gives inside an artwork block, as a colour (0 if none). */
    static int bgOf(Object holder) {
        if (holder == null) return 0;
        List<Object> stack = new ArrayList<>();
        stack.add(holder);
        while (!stack.isEmpty()) {
            Object o = stack.remove(0);
            if (o instanceof JSONObject) {
                JSONObject j = (JSONObject) o;
                String c = j.optString("bgColor", "");
                if (c.matches("[0-9a-fA-F]{6}")) return 0xFF000000 | Integer.parseInt(c, 16);
                for (java.util.Iterator<String> it = j.keys(); it.hasNext(); ) stack.add(j.opt(it.next()));
            } else if (o instanceof JSONArray) for (int i = 0; i < ((JSONArray) o).length(); i++) stack.add(((JSONArray) o).opt(i));
        }
        return 0;
    }

    /** An album's motion artwork and its colour, as on its Apple Music page: {motion, colour}. */
    public static Object[] albumLook(String albumId) {
        try {
            Object[] pg = page("https://music.apple.com/us/album/x/" + albumId);
            for (Section s : sections(pg[1])) if (s.kind.equals("containerDetailHeaderLockup")) {
                JSONArray it = s.raw.optJSONArray("items");
                JSONObject h = it == null ? null : it.optJSONObject(0);
                if (h != null) return new Object[]{motion(h.opt("videoArtwork")), bgOf(h.opt("artwork"))};
            }
        } catch (Exception ignored) { }
        return new Object[]{"", 0};
    }

    // ------------------------------------------------------------------ pages
    /** Apple Music's Browse page: its shelves of new albums, songs and playlists. */
    public static List<Ytm.Shelf> browse() throws Exception {
        List<Ytm.Shelf> out = new ArrayList<>();
        for (Section s : sections(page("https://music.apple.com/us/browse")[1])) {
            if (s.items.size() < 3) continue;
            Ytm.Shelf sh = new Ytm.Shelf();
            sh.title = s.title;
            sh.items.addAll(s.items);
            sh.list = s.kind.equals("trackLockup");
            if (sh.title.isEmpty()) sh.title = sh.list ? "Songs to Know" : kindTitle(s.items);
            out.add(sh);
        }
        return out;
    }

    private static String kindTitle(List<MItem> items) {
        int pl = 0;
        for (MItem m : items) if (MItem.PLAYLIST.equals(m.type)) pl++;
        return pl > items.size() / 2 ? "Playlists for You" : "New Releases";
    }

    /** An artist: photo, Top Songs, Albums, Singles & EPs, Appears On, Similar Artists. */
    public static Ytm.Page artist(String appleId) throws Exception {
        Object[] pg = page("https://music.apple.com/us/artist/x/" + appleId);
        Ytm.Page p = new Ytm.Page();
        // (Not the page's share picture: that's their photo cut into a circle on a coloured card.)
        String og = (String) pg[2];
        p.banner = og.replaceAll("/\\d+x\\d+\\w*\\.(png|jpg)$", "/1920x1080sr.jpg");
        for (Section s : sections(pg[1])) {
            if (s.kind.equals("artistDetailHeader")) {
                JSONArray it = s.raw.optJSONArray("items");
                JSONObject h = it == null ? null : it.optJSONObject(0);
                if (h != null) {
                    // Their real photo, cropped wide the way Apple crops it; and their motion video, if any.
                    JSONObject art = h.optJSONObject("artwork");
                    String u = art == null ? "" : (art.optJSONObject("dictionary") != null ? art.optJSONObject("dictionary") : art).optString("url");
                    if (!u.isEmpty()) {
                        p.banner = u.replace("{w}x{h}{c}.{f}", "1920x1080sr.jpg");
                        p.thumb = u.replace("{w}x{h}{c}.{f}", "600x600cc.jpg");
                    }
                    p.motion = motion(h.opt("wideVideoArtwork"));
                    // The page's colour: the motion video's (if it plays) or the photo's, as Apple gives it.
                    p.bgColor = bgOf(p.motion.isEmpty() ? h.opt("artwork") : h.opt("wideVideoArtwork"));
                    p.title = h.optString("title", h.optString("name", ""));
                    p.description = h.optString("bio", "").replaceAll("<[^>]+>", "");
                    if (p.description.isEmpty()) p.description = firstString(h.opt("bioAction"), "paragraphText", 0).replaceAll("<[^>]+>", "");
                }
                continue;
            }
            if (s.items.isEmpty()) continue;
            Ytm.Shelf sh = new Ytm.Shelf();
            sh.title = s.title;
            sh.items.addAll(s.items);
            sh.list = s.kind.equals("artistFeaturedContentAndTracks") || s.kind.equals("trackLockup");
            String t = sh.title.toLowerCase(java.util.Locale.US);
            if (t.contains("appears")) sh.moreId = "AMALL:" + appleId + ":appears";
            else if (t.contains("single")) sh.moreId = "AMALL:" + appleId + ":singles";
            else if (t.contains("album")) sh.moreId = "AMALL:" + appleId + ":albums";
            else if (sh.list) sh.moreId = "AMALL:" + appleId + ":songs";
            p.shelves.add(sh);
        }
        if (p.title.isEmpty()) {
            Matcher t = Pattern.compile("([^/]+)/" + appleId).matcher("");
            p.title = "";
        }
        if (p.thumb.isEmpty()) p.thumb = p.banner.replace("/1920x1080sr.jpg", "/600x600cc.jpg");
        // An artist's top songs list the album ("Angelic 7 · 2019") where the artist would go: it's their song.
        for (Ytm.Shelf s : p.shelves) for (MItem m : s.items) if (m.playable() && s.list) {
            String sub = m.artist;
            if (sub.contains(" · ")) m.album = sub.substring(0, sub.indexOf(" · "));
            m.artist = p.title;
            m.subtitle = p.title;
        }
        return p;
    }

    /** An Apple playlist: its cover, name, description and songs. */
    public static Ytm.Page playlist(String url) throws Exception {
        Object[] pg = page(url);
        Ytm.Page p = new Ytm.Page();
        Ytm.Shelf songs = new Ytm.Shelf();
        songs.list = true;
        for (Section s : sections(pg[1])) {
            if (s.kind.equals("containerDetailHeaderLockup")) {
                JSONArray it = s.raw.optJSONArray("items");
                JSONObject h = it == null ? null : it.optJSONObject(0);
                if (h != null) {
                    p.title = h.optString("title");
                    p.thumb = art(h, 1000);
                    p.motion = motion(h.opt("videoArtwork"));
                    p.bgColor = bgOf(h.opt("artwork"));
                    JSONObject desc = h.optJSONObject("modalPresentationDescriptor");
                    if (desc != null) p.description = desc.optString("paragraphText").replaceAll("<[^>]+>", "");
                    JSONArray sl = h.optJSONArray("subtitleLinks");
                    if (sl != null && sl.length() > 0) p.subtitle = "Playlist  •  " + sl.optJSONObject(0).optString("title");
                }
            } else if (s.kind.equals("trackLockup")) songs.items.addAll(s.items);
        }
        if (p.thumb.isEmpty()) p.thumb = (String) pg[2];
        if (p.subtitle.isEmpty()) p.subtitle = "Playlist";
        p.subtitle += "  •  " + songs.items.size() + " songs";
        p.shelves.add(songs);
        return p;
    }

    /**
     * Everything in one of an artist's sections: albums, singles & EPs, appears on, or songs. Apple's own
     * "see all" page (first 20) completed from the whole catalogue, kept apart the way Apple keeps them.
     */
    public static Ytm.Page seeAll(String appleId, String kind) throws Exception {
        Ytm.Page p = new Ytm.Page();
        Ytm.Shelf s = new Ytm.Shelf();
        String section = kind.equals("albums") ? "full-albums" : kind.equals("singles") ? "singles" : kind.equals("appears") ? "appears-on-albums" : "top-songs";
        java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            for (Section sec : sections(page("https://music.apple.com/us/artist/x/" + appleId + "/see-all?section=" + section)[1]))
                for (MItem m : sec.items) {
                    if (m.type.equals(MItem.ALBUM)) m.title = m.title.replaceAll(" - (Single|EP)$", "");
                    if (seen.add(norm(m.title))) s.items.add(m);
                }
        } catch (Exception ignored) { }
        if (kind.equals("songs")) {
            s.list = true;
            String name = "";
            try { name = artist(appleId).title; } catch (Exception ignored) { }
            for (MItem m : s.items) { if (m.artist.contains(" · ")) m.album = m.artist.substring(0, m.artist.indexOf(" · ")); m.artist = name; m.subtitle = name; }
            for (JSONObject o : lookupSongs(appleId)) {
                MItem m = song(o);
                if (seen.add(norm(m.title))) s.items.add(m);
            }
        } else {
            AppleMusic.Discography d = AppleMusic.discography(appleId);
            // Keep the lists apart: nothing of theirs in Appears On, nothing in Appears On among theirs.
            java.util.Set<String> own = new java.util.HashSet<>(), feat = new java.util.HashSet<>();
            for (MItem m : d.albums) own.add(norm(m.title));
            for (MItem m : d.singles) own.add(norm(m.title));
            for (MItem m : d.appearsOn) feat.add(norm(m.title));
            if (kind.equals("appears")) {
                List<MItem> keep = new ArrayList<>();
                for (MItem m : s.items) if (!own.contains(norm(m.title))) keep.add(m);
                s.items.clear();
                s.items.addAll(keep);
            }
            for (MItem m : kind.equals("albums") ? d.albums : kind.equals("singles") ? d.singles : d.appearsOn)
                if (seen.add(norm(m.title))) s.items.add(m);
        }
        p.shelves.add(s);
        return p;
    }

    /** One song's details (length, album) by its Apple id. */
    public static JSONObject lookupTrack(String appleId) {
        try {
            JSONArray a = new JSONObject(get("https://itunes.apple.com/lookup?id=" + appleId + "&country=US")).optJSONArray("results");
            return a == null || a.length() == 0 ? null : a.optJSONObject(0);
        } catch (Exception e) { return null; }
    }

    private static List<JSONObject> lookupSongs(String appleId) throws Exception {
        JSONObject o = new JSONObject(get("https://itunes.apple.com/lookup?id=" + appleId + "&entity=song&limit=200&country=US"));
        JSONArray a = o.optJSONArray("results");
        List<JSONObject> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject t = a.optJSONObject(i);
            if (t != null && "track".equals(t.optString("wrapperType")) && String.valueOf(t.optLong("artistId")).equals(appleId)) l.add(t);
        }
        return l;
    }

    // ------------------------------------------------------------------ charts and genres
    public static final String[][] GENRES = {
            {"18", "Hip-Hop/Rap", "#BA5D07"}, {"14", "Pop", "#8D67AB"}, {"15", "R&B/Soul", "#DC148C"}, {"20", "Alternative", "#E8115B"},
            {"6", "Country", "#D84000"}, {"21", "Rock", "#E91429"}, {"17", "Dance", "#0D73EC"}, {"12", "Latin", "#E1118C"},
            {"7", "Electronic", "#7358FF"}, {"24", "Reggae", "#148A08"}, {"11", "Jazz", "#477D95"}, {"5", "Classical", "#7D4B32"},
            {"22", "Christian & Gospel", "#1E3264"}, {"16", "Soundtrack", "#503750"}, {"10", "Singer/Songwriter", "#8C1932"}, {"2", "Blues", "#1E3264"},
            {"51", "K-Pop", "#AF2896"}, {"1122", "Afrobeats", "#27856A"}, {"19", "Worldwide", "#509BF5"}, {"50000064", "Metal", "#535353"}};

    /** Browse tiles: one per genre, each with its colour. */
    public static List<MItem> genres() {
        List<MItem> out = new ArrayList<>();
        for (String[] g : GENRES) {
            MItem m = new MItem();
            m.type = MItem.CATEGORY;
            m.title = g[1];
            m.browseId = "AMG:" + g[0];
            m.color = android_color(g[2]);
            out.add(m);
        }
        return out;
    }

    private static int android_color(String hex) { return (int) (0xFF000000L | Long.parseLong(hex.substring(1), 16)); }

    /** The charts: today's top songs and top albums. */
    public static List<Ytm.Shelf> charts() throws Exception {
        List<Ytm.Shelf> out = new ArrayList<>();
        Ytm.Shelf songs = new Ytm.Shelf();
        songs.title = "Top Songs";
        songs.list = true;
        for (JSONObject o : rss("songs", 100)) {
            MItem m = new MItem();
            m.type = MItem.SONG;
            m.appleId = o.optString("id");
            m.title = o.optString("name");
            m.artist = o.optString("artistName");
            m.subtitle = m.artist;
            m.thumb = o.optString("artworkUrl100").replace("100x100bb", "600x600bb");
            songs.items.add(m);
        }
        Ytm.Shelf albums = new Ytm.Shelf();
        albums.title = "Top Albums";
        for (JSONObject o : rss("albums", 50)) {
            MItem m = new MItem();
            m.type = MItem.ALBUM;
            m.browseId = "AM:" + o.optString("id");
            m.title = o.optString("name");
            m.artist = o.optString("artistName");
            m.subtitle = m.artist;
            m.thumb = o.optString("artworkUrl100").replace("100x100bb", "600x600bb");
            albums.items.add(m);
        }
        if (!songs.items.isEmpty()) out.add(songs);
        if (!albums.items.isEmpty()) out.add(albums);
        return out;
    }

    private static List<JSONObject> rss(String kind, int n) throws Exception {
        JSONObject o = new JSONObject(get("https://rss.applemarketingtools.com/api/v2/us/music/most-played/" + n + "/" + kind + ".json"));
        JSONArray a = o.getJSONObject("feed").optJSONArray("results");
        List<JSONObject> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optJSONObject(i));
        return l;
    }

    /** A genre: its top songs and top albums right now. */
    public static Ytm.Page genre(String id) throws Exception {
        String gid = id.startsWith("AMG:") ? id.substring(4) : id;
        Ytm.Page p = new Ytm.Page();
        for (String[] g : GENRES) if (g[0].equals(gid)) p.title = g[1];
        Ytm.Shelf songs = new Ytm.Shelf();
        songs.title = "Top Songs";
        songs.list = true;
        for (JSONObject e : oldRss("topsongs", gid, 50)) {
            MItem m = new MItem();
            m.type = MItem.SONG;
            m.appleId = e.optJSONObject("id") == null ? "" : e.optJSONObject("id").optJSONObject("attributes").optString("im:id");
            m.title = label(e, "im:name");
            m.artist = label(e, "im:artist");
            m.subtitle = m.artist;
            JSONObject col = e.optJSONObject("im:collection");
            if (col != null) m.album = label(col, "im:name");
            m.thumb = image(e);
            if (!m.title.isEmpty()) songs.items.add(m);
        }
        Ytm.Shelf albums = new Ytm.Shelf();
        albums.title = "Top Albums";
        for (JSONObject e : oldRss("topalbums", gid, 30)) {
            MItem m = new MItem();
            m.type = MItem.ALBUM;
            String aid = e.optJSONObject("id") == null ? "" : e.optJSONObject("id").optJSONObject("attributes").optString("im:id");
            m.browseId = "AM:" + aid;
            m.title = label(e, "im:name");
            m.artist = label(e, "im:artist");
            m.subtitle = m.artist;
            m.thumb = image(e);
            if (!aid.isEmpty()) albums.items.add(m);
        }
        p.shelves.add(songs);
        p.shelves.add(albums);
        if (!albums.items.isEmpty()) p.thumb = albums.items.get(0).thumb;
        p.subtitle = "Top songs and albums right now";
        return p;
    }

    /** The first album cover of a genre (the picture on its tile). */
    public static String genreCover(String id) {
        try {
            List<JSONObject> l = oldRss("topalbums", id.replace("AMG:", ""), 1);
            return l.isEmpty() ? "" : image(l.get(0));
        } catch (Exception e) { return ""; }
    }

    private static List<JSONObject> oldRss(String what, String genre, int n) throws Exception {
        JSONObject o = new JSONObject(get("https://itunes.apple.com/us/rss/" + what + "/limit=" + n + "/genre=" + genre + "/json"));
        Object e = o.getJSONObject("feed").opt("entry");
        List<JSONObject> l = new ArrayList<>();
        if (e instanceof JSONArray) for (int i = 0; i < ((JSONArray) e).length(); i++) l.add(((JSONArray) e).optJSONObject(i));
        else if (e instanceof JSONObject) l.add((JSONObject) e);
        return l;
    }

    private static String label(JSONObject e, String key) {
        JSONObject v = e.optJSONObject(key);
        return v == null ? "" : v.optString("label");
    }

    private static String image(JSONObject e) {
        JSONArray a = e.optJSONArray("im:image");
        if (a == null || a.length() == 0) return "";
        return a.optJSONObject(a.length() - 1).optString("label").replaceAll("/\\d+x\\d+bb", "/600x600bb");
    }

    // ------------------------------------------------------------------ search
    /**
     * Search exactly as Apple Music's own search page has it: Top Results (artists, songs and albums mixed, best
     * first), Artists, Albums, Songs, Playlists. Falls back to the catalogue search if the page can't be read.
     */
    public static Ytm.Page searchAll(String q) throws Exception {
        try {
            Object[] pg = page("https://music.apple.com/us/search?term=" + java.net.URLEncoder.encode(q, "UTF-8"));
            Ytm.Page p = new Ytm.Page();
            for (Section s : sections(pg[1])) {
                String t = s.title.toLowerCase(java.util.Locale.US);
                if (s.items.isEmpty() || !(t.contains("top") || t.equals("artists") || t.equals("albums") || t.equals("songs") || t.equals("playlists"))) continue;
                Ytm.Shelf sh = new Ytm.Shelf();
                sh.title = s.title;
                sh.items.addAll(s.items);
                sh.list = t.equals("songs");
                if (t.contains("top")) sh.moreId = "TOP";
                p.shelves.add(sh);
            }
            if (!p.shelves.isEmpty()) return p;
        } catch (Exception ignored) { }
        return search(q);
    }

    /** Search: songs, albums and artists from Apple Music's catalogue. */
    public static Ytm.Page search(String q) throws Exception {
        Ytm.Page p = new Ytm.Page();
        Ytm.Shelf songs = new Ytm.Shelf(), albums = new Ytm.Shelf(), artists = new Ytm.Shelf();
        songs.title = "Songs";
        songs.list = true;
        albums.title = "Albums";
        artists.title = "Artists";
        for (JSONObject o : itunes(q, "song", 20)) songs.items.add(song(o));
        for (JSONObject o : itunes(q, "album", 20)) {
            MItem m = new MItem();
            m.type = MItem.ALBUM;
            m.browseId = "AM:" + o.optLong("collectionId");
            String name = o.optString("collectionName");
            m.title = name.replaceAll(" - (Single|EP)$", "");
            m.artist = o.optString("artistName");
            m.subtitle = (name.endsWith(" - Single") ? "Single" : name.endsWith(" - EP") ? "EP" : "Album") + " • " + m.artist;
            m.thumb = o.optString("artworkUrl100").replace("100x100bb", "600x600bb");
            albums.items.add(m);
        }
        for (JSONObject o : itunes(q, "musicArtist", 8)) {
            MItem m = new MItem();
            m.type = MItem.ARTIST;
            m.browseId = "AMA:" + o.optLong("artistId");
            m.title = o.optString("artistName");
            m.subtitle = "Artist";
            artists.items.add(m);
        }
        // Artist pictures (from their pages), for the first few.
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
        for (MItem a : artists.items.subList(0, Math.min(4, artists.items.size())))
            fs.add(ex.submit(() -> { try { a.thumb = artistPhoto(a.browseId.substring(4), 600); } catch (Exception ignored) { } }));
        for (java.util.concurrent.Future<?> f : fs) try { f.get(8, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
        ex.shutdownNow();
        if (!artists.items.isEmpty() && !songs.items.isEmpty() && norm(artists.items.get(0).title).equals(norm(q))) {
            p.shelves.add(artists);
            p.shelves.add(songs);
        } else {
            if (!songs.items.isEmpty()) p.shelves.add(songs);
            if (!artists.items.isEmpty()) p.shelves.add(artists);
        }
        if (!albums.items.isEmpty()) p.shelves.add(albums);
        return p;
    }

    static MItem song(JSONObject o) {
        MItem m = new MItem();
        m.type = MItem.SONG;
        m.appleId = String.valueOf(o.optLong("trackId"));
        m.title = o.optString("trackName");
        m.artist = o.optString("artistName");
        m.album = o.optString("collectionName").replaceAll(" - (Single|EP)$", "");
        m.subtitle = m.artist;
        m.seconds = (int) (o.optLong("trackTimeMillis") / 1000);
        m.thumb = o.optString("artworkUrl100").replace("100x100bb", "600x600bb");
        return m;
    }

    static List<JSONObject> itunes(String q, String entity, int n) throws Exception {
        String body = get(HttpUrl.parse("https://itunes.apple.com/search").newBuilder().addQueryParameter("term", q).addQueryParameter("entity", entity)
                .addQueryParameter("limit", String.valueOf(n)).addQueryParameter("country", "US").build().toString());
        JSONArray a = new JSONObject(body).optJSONArray("results");
        List<JSONObject> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) l.add(a.optJSONObject(i));
        return l;
    }

    private static final Map<String, String> photos = new HashMap<>();

    /** An artist's photo (square). */
    public static String artistPhoto(String appleId, int px) throws Exception {
        synchronized (photos) { if (photos.containsKey(appleId)) return photos.get(appleId); }
        String og = (String) page("https://music.apple.com/us/artist/x/" + appleId)[2];
        String u = og.replaceAll("/\\d+x\\d+\\w*\\.(png|jpg)$", "/" + px + "x" + px + "cc.jpg");
        synchronized (photos) { photos.put(appleId, u); }
        return u;
    }

    private static final Map<String, String> covers = new HashMap<>();

    /** Apple's cover for a song (for songs that came from elsewhere, like the radio). Null if it isn't found. */
    public static String cover(MItem m) {
        String key = norm(m.title) + "|" + norm(m.artist);
        synchronized (covers) { if (covers.containsKey(key)) return covers.get(key); }
        String found = null;
        try {
            for (JSONObject o : itunes(m.title + " " + m.artist, "song", 5)) {
                if (norm(o.optString("trackName")).contains(norm(m.title).length() > 3 ? norm(m.title).substring(0, Math.min(12, norm(m.title).length())) : norm(m.title))
                        && (norm(o.optString("artistName")).contains(norm(m.artist)) || norm(m.artist).contains(norm(o.optString("artistName"))))) {
                    found = o.optString("artworkUrl100").replace("100x100bb", "600x600bb");
                    if (m.album.isEmpty()) m.album = o.optString("collectionName").replaceAll(" - (Single|EP)$", "");
                    break;
                }
            }
        } catch (Exception ignored) { }
        synchronized (covers) { covers.put(key, found); }
        return found;
    }

    /** Apple's id for an artist by name (for artists reached from a song). */
    public static String artistIdByName(String name) { return AppleMusic.artistId(name); }

    static String norm(String s) { return s == null ? "" : s.toLowerCase(java.util.Locale.US).replaceAll("\\(.*?\\)|\\[.*?\\]", "").replaceAll("[^a-z0-9]", ""); }

    private AppleWeb() { }
}
