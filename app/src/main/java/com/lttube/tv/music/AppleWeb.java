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
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();

    static Object[] page(String url) throws Exception {
        synchronized (cache) {
            Object[] hit = cache.get(url);
            if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 20 * 60_000L) return hit;
        }
        Object lock = locks.computeIfAbsent(url, k -> new Object());
        synchronized (lock) {
            synchronized (cache) {
                Object[] hit = cache.get(url);
                if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 20 * 60_000L) return hit;
            }
            return fetchPage(url);
        }
    }

    // ------------------------------------------------------------------ getting ahead
    private static final java.util.concurrent.ExecutorService ahead = java.util.concurrent.Executors.newFixedThreadPool(2);
    private static android.os.Handler later;
    private static Runnable pendingAhead;

    /**
     * You've rested on an album / playlist / artist: start loading its page now, so it's ready (or nearly) when
     * you open it. Only the one you stay on for a moment, not everything you pass over.
     */
    public static void prefetch(MItem m) {
        if (m == null) return;
        if (later == null) later = new android.os.Handler(android.os.Looper.getMainLooper());
        final String id = m.browseId;
        if (pendingAhead != null) later.removeCallbacks(pendingAhead);
        pendingAhead = () -> ahead.submit(() -> {
            try {
                if (id.startsWith("AMA:")) artist(id.substring(4));
                else if (id.startsWith("AMP:")) page(id.substring(4));
                else if (id.startsWith("AM:")) { AppleMusic.album(id); page("https://music.apple.com/us/album/x/" + id.substring(3)); }
            } catch (Throwable ignored) { }
        });
        later.postDelayed(pendingAhead, 350);
    }

    private static Object[] fetchPage(String url) throws Exception {
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
                if (m != null && s.kind.equals("flowcaseLockup")) {
                    JSONObject a = it.optJSONObject("artwork");
                    JSONObject d = a == null ? null : (a.optJSONObject("dictionary") != null ? a.optJSONObject("dictionary") : a);
                    if (d != null) m.thumb = d.optString("url").replace("{w}", "1040").replace("{h}", "594").replace("{c}", "sr").replace("{f}", "jpg");
                }
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
        m.explicit = o.optBoolean("showExplicitBadge");
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
            case "musicVideo":
                m.type = MItem.SONG;
                m.video = true;
                m.appleId = id;
                m.mvType = "";
                JSONObject va = o.optJSONObject("artwork");
                JSONObject vd = va == null ? null : (va.optJSONObject("dictionary") != null ? va.optJSONObject("dictionary") : va);
                if (vd != null) m.thumb = vd.optString("url").replace("{w}", "640").replace("{h}", "360").replace("{c}", "sr").replace("{f}", "jpg");
                if (m.artist.isEmpty()) m.artist = o.optString("subtitle", "");
                m.subtitle = m.artist;
                return id.isEmpty() || m.title.isEmpty() ? null : m;
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
        // Big feature cards carry a wide editorial picture (often the artist) as "artwork" and the real cover as
        // "coverArtwork": the cover is what a cover spot shows.
        JSONObject a = holder instanceof JSONObject ? ((JSONObject) holder).optJSONObject("coverArtwork") : null;
        if (a == null && holder instanceof JSONObject) a = ((JSONObject) holder).optJSONObject("artwork");
        if (a == null) a = Ytm.findObj(holder, "artwork");
        if (a == null) return "";
        JSONObject dict = a.optJSONObject("dictionary");
        JSONObject d = dict != null ? dict : a;
        String u = d.optString("url");
        // Artwork that isn't square, asked to "fit in a square", comes back as a thin strip that then gets
        // blown up (blurry) and cut off. Those get Apple's own smart crop to a full-size square instead.
        int w = d.optInt("width"), h = d.optInt("height");
        if (w > 0 && h > 0 && Math.abs(w - h) > Math.max(w, h) / 50) return u.replace("{w}", String.valueOf(px)).replace("{h}", String.valueOf(px)).replace("{c}", "sr").replace("{f}", "jpg");
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

    /**
     * The category tiles on Apple Music's search page, exactly as Apple has them right now (their pictures,
     * colours and order), each opening that category's page.
     */
    public static List<MItem> categories() throws Exception {
        Object[] pg = page("https://music.apple.com/us/search");
        List<MItem> out = new ArrayList<>();
        List<Object> stack = new ArrayList<>();
        stack.add(pg[1]);
        while (!stack.isEmpty()) {
            Object o = stack.remove(0);
            if (o instanceof JSONObject) {
                JSONObject j = (JSONObject) o;
                if ("brickLockup".equals(j.optString("itemKind")) && j.optJSONArray("items") != null) {
                    JSONArray items = j.optJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.optJSONObject(i);
                        if (it == null) continue;
                        JSONObject cd = it.optJSONObject("contentDescriptor");
                        if (cd == null) cd = Ytm.findObj(it.opt("segue"), "contentDescriptor");
                        String url = cd == null ? "" : cd.optString("url");
                        if (url.isEmpty()) continue;
                        MItem m = new MItem();
                        m.type = MItem.CATEGORY;
                        m.title = it.optString("title");
                        m.browseId = "AMX:" + url;
                        JSONObject art = it.optJSONObject("artwork");
                        JSONObject d = art == null ? null : (art.optJSONObject("dictionary") != null ? art.optJSONObject("dictionary") : art);
                        if (d != null) {
                            m.thumb = d.optString("url").replace("{w}", "640").replace("{h}", "360").replace("{c}", "sr").replace("{f}", "jpg");
                            String bg = d.optString("bgColor", "");
                            if (bg.matches("[0-9a-fA-F]{6}")) m.color = 0xFF000000 | Integer.parseInt(bg, 16);
                        }
                        out.add(m);
                    }
                    return out;
                }
                for (java.util.Iterator<String> k = j.keys(); k.hasNext(); ) stack.add(j.opt(k.next()));
            } else if (o instanceof JSONArray) for (int i = 0; i < ((JSONArray) o).length(); i++) stack.add(((JSONArray) o).opt(i));
        }
        return out;
    }

    /** Any Apple Music page (a category, a curator, a room): its rows, as Apple lays them out. */
    public static Ytm.Page anyPage(String url) throws Exception {
        Object[] pg = page(url);
        Ytm.Page p = new Ytm.Page();
        for (Section s : sections(pg[1])) {
            if (s.items.isEmpty()) continue;
            Ytm.Shelf sh = new Ytm.Shelf();
            sh.title = s.title;
            sh.items.addAll(s.items);
            sh.list = s.kind.equals("trackLockup");
            if (s.kind.equals("flowcaseLockup")) sh.moreId = "WIDE";
            p.shelves.add(sh);
        }
        return p;
    }

    /**
     * The rows under an album on Apple Music: Other Versions (deluxe, clean…), More By the artist, Featured On,
     * You Might Also Like.
     */
    public static List<Ytm.Shelf> albumExtras(String albumId) {
        List<Ytm.Shelf> out = new ArrayList<>();
        try {
            for (Section s : sections(page("https://music.apple.com/us/album/x/" + albumId)[1])) {
                if (!s.kind.equals("squareLockup") || s.items.isEmpty() || s.title.isEmpty()) continue;
                Ytm.Shelf sh = new Ytm.Shelf();
                sh.title = s.title;
                for (MItem m : s.items) if (!m.browseId.equals("AM:" + albumId)) sh.items.add(m);
                if (!sh.items.isEmpty()) out.add(sh);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** The Apple Music album for a name and artist (so albums found elsewhere get Apple's motion artwork too). */
    public static String albumIdFor(String title, String artist) {
        try {
            for (JSONObject o : itunes(title + " " + artist, "album", 5)) {
                String n = o.optString("collectionName").replaceAll(" - (Single|EP)$", "");
                if (norm(n).equals(norm(title)) && (artist.isEmpty() || norm(o.optString("artistName")).contains(norm(artist.split(",|&")[0])))) return String.valueOf(o.optLong("collectionId"));
            }
        } catch (Exception ignored) { }
        return null;
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
            if (t.contains("video")) sh.moreId = "AMALL:" + appleId + ":videos";
            else if (t.contains("appears")) sh.moreId = "AMALL:" + appleId + ":appears";
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
        // Albums, Singles & EPs, Music Videos, Appears On: newest first.
        java.util.Map<String, String> dates = datesFor(appleId);
        for (Ytm.Shelf s : p.shelves) {
            String t = s.title.toLowerCase(java.util.Locale.US);
            if (!s.list && (t.contains("album") || t.contains("single") || t.contains("video") || t.contains("appears") || t.contains("release"))) byYear(s.items, dates);
        }
        // Their music videos show the year under the name (the video is found by the artist's name).
        for (Ytm.Shelf s : p.shelves) for (MItem m : s.items) if (m.video) {
            String year = m.artist.matches("\\d{4}") ? m.artist : m.subtitle.matches("\\d{4}") ? m.subtitle : "";
            m.artist = p.title;
            m.subtitle = year.isEmpty() ? p.title : year;
        }
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
        String section = kind.equals("albums") ? "full-albums" : kind.equals("singles") ? "singles" : kind.equals("appears") ? "appears-on-albums"
                : kind.equals("videos") ? "music-videos" : "top-songs";
        java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            for (Section sec : sections(page("https://music.apple.com/us/artist/x/" + appleId + "/see-all?section=" + section)[1]))
                for (MItem m : sec.items) {
                    if (m.type.equals(MItem.ALBUM)) m.title = m.title.replaceAll(" - (Single|EP)$", "");
                    if (seen.add(norm(m.title))) s.items.add(m);
                }
        } catch (Exception ignored) { }
        if (kind.equals("videos")) {
            // Every music video of theirs: Apple's list, then the rest of the catalogue's (newest first).
            String name = "";
            try { name = artist(appleId).title; } catch (Exception ignored) { }
            for (MItem m : s.items) { if (m.artist.matches("\\d{4}") || m.artist.isEmpty()) { m.subtitle = m.artist.isEmpty() ? name : m.artist; m.artist = name; } }
            try {
                JSONObject o = new JSONObject(get("https://itunes.apple.com/lookup?id=" + appleId + "&entity=musicVideo&limit=200&sort=recent&country=US"));
                JSONArray a = o.optJSONArray("results");
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject t = a.optJSONObject(i);
                    if (t == null || !"track".equals(t.optString("wrapperType"))) continue;
                    MItem m = song(t);
                    m.video = true;
                    m.thumb = t.optString("artworkUrl100").replaceAll("/\\d+x\\d+bb\\.(jpg|png)$", "/640x360bb.jpg");
                    String yr = t.optString("releaseDate").length() >= 4 ? t.optString("releaseDate").substring(0, 4) : "";
                    m.subtitle = yr.isEmpty() ? m.artist : yr;
                    if (seen.add(norm(m.title))) s.items.add(m);
                }
            } catch (Exception ignored) { }
        } else if (kind.equals("songs")) {
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
        // Everything in order of year, newest first.
        java.util.Map<String, String> dates = datesFor(appleId);
        if (kind.equals("songs")) {
            // (Apple's top-songs list carries no dates: take them from the catalogue's songs.)
            for (MItem m : s.items) if (m.date.isEmpty() && !m.appleId.isEmpty()) dates.putIfAbsent("s:" + m.appleId, "");
            try { for (JSONObject o : lookupSongs(appleId)) dates.put("s:" + o.optLong("trackId"), o.optString("releaseDate")); } catch (Exception ignored) { }
            for (MItem m : s.items) if (m.date.isEmpty()) { String d = dates.get("s:" + m.appleId); if (d != null && !d.isEmpty()) m.date = d; }
        }
        byYear(s.items, dates);
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
                if (t.contains("top")) {
                    sh.moreId = "TOP";
                    // Apple writes the kind into the line under the name ("Song · SoFaygo", "Artist"): keep just who.
                    for (MItem m : sh.items) {
                        m.artist = m.artist.replaceAll("^(Song|Album|Single|EP|Playlist|Artist|Music Video|Station)\\s*(\u00B7|-)?\\s*", "").trim();
                        if (MItem.ARTIST.equals(m.type)) m.artist = "";
                    }
                }
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
        m.explicit = "explicit".equals(o.optString("trackExplicitness"));
        m.date = o.optString("releaseDate");
        return m;
    }

    // ------------------------------------------------------------------ in order of year
    private static final java.util.Map<String, Object[]> dateCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Release dates for everything of an artist's, from Apple's catalogue: records by id ("AM:…"), music videos
     * and songs by name. Kept 20 minutes.
     */
    @SuppressWarnings("unchecked")
    static java.util.Map<String, String> datesFor(String appleId) {
        Object[] hit = dateCache.get(appleId);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 20 * 60_000L) return (java.util.Map<String, String>) hit[1];
        java.util.Map<String, String> d = new java.util.HashMap<>();
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<AppleMusic.Discography> fd = ex.submit(() -> AppleMusic.discography(appleId));
            java.util.concurrent.Future<JSONObject> fv = ex.submit(() -> new JSONObject(get("https://itunes.apple.com/lookup?id=" + appleId + "&entity=musicVideo&limit=200&country=US")));
            try {
                AppleMusic.Discography disc = fd.get(10, java.util.concurrent.TimeUnit.SECONDS);
                for (List<MItem> l : java.util.Arrays.asList(disc.albums, disc.singles, disc.appearsOn)) for (MItem m : l) { d.put(m.browseId, m.date); d.putIfAbsent("t:" + norm(m.title), m.date); }
            } catch (Exception ignored) { }
            try {
                JSONArray a = fv.get(10, java.util.concurrent.TimeUnit.SECONDS).optJSONArray("results");
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject t = a.optJSONObject(i);
                    if (t != null && "track".equals(t.optString("wrapperType"))) d.putIfAbsent("v:" + norm(t.optString("trackName")), t.optString("releaseDate"));
                }
            } catch (Exception ignored) { }
        } finally { ex.shutdownNow(); }
        dateCache.put(appleId, new Object[]{System.currentTimeMillis(), d});
        return d;
    }

    /** The year written under an item ("2009", "Album • 2021"), or "". */
    static String shownYear(MItem m) {
        java.util.regex.Matcher y = java.util.regex.Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(m.subtitle);
        String last = "";
        while (y.find()) last = y.group();
        return last;
    }

    /** Fills in dates it knows and puts the list newest first (anything without a date keeps its place at the end). */
    static void byYear(List<MItem> items, java.util.Map<String, String> dates) {
        for (MItem m : items) {
            if (!m.date.isEmpty()) continue;
            String d = dates.get(m.browseId);
            if (d == null) d = dates.get((m.video ? "v:" : "t:") + norm(m.title));
            if (d == null && !m.appleId.isEmpty()) d = dates.get("s:" + m.appleId);
            if (d == null && m.subtitle.matches("\\d{4}")) d = m.subtitle;
            if (d != null) m.date = d;
        }
        List<MItem> dated = new ArrayList<>(), rest = new ArrayList<>();
        for (MItem m : items) (m.date.isEmpty() && shownYear(m).isEmpty() ? rest : dated).add(m);
        // By the year shown under it (its original year: "So Far Gone" says 2009 even though it reached streaming
        // in 2019), then the exact date within a year.
        dated.sort((x, y) -> {
            String kx = (shownYear(x).isEmpty() ? (x.date.length() >= 4 ? x.date.substring(0, 4) : "") : shownYear(x)) + "|" + x.date;
            String ky = (shownYear(y).isEmpty() ? (y.date.length() >= 4 ? y.date.substring(0, 4) : "") : shownYear(y)) + "|" + y.date;
            return ky.compareTo(kx);
        });
        items.clear();
        items.addAll(dated);
        items.addAll(rest);
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
