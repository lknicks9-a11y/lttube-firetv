package com.lttube.tv.music;

import com.lttube.tv.Account;
import com.lttube.tv.NpDownloader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * YouTube Music's own API (the one music.youtube.com uses), as you or as a guest. Pages come back as shelves of
 * songs, albums, playlists and artists; playing comes from {@link #queue}, which is YouTube Music's own radio.
 */
public final class Ytm {
    private static final String VERSION = "1.20251110.01.00";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    public static final String HOME = "FEmusic_home", NEW = "FEmusic_new_releases", CHARTS = "FEmusic_charts",
            MOODS = "FEmusic_moods_and_genres", EXPLORE = "FEmusic_explore",
            LIKED_SONGS = "FEmusic_liked_videos", LIB_PLAYLISTS = "FEmusic_liked_playlists", LIB_ALBUMS = "FEmusic_liked_albums",
            LIB_ARTISTS = "FEmusic_library_corpus_track_artists", HISTORY = "FEmusic_history";

    /** Search only songs. */
    public static final String SONGS_ONLY = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D";

    public static class Shelf {
        public String title = "";
        /** The small line above the title ("Top picks for you" over "Made for you"). */
        public String strap = "";
        public final List<MItem> items = new ArrayList<>();
        /** Song lists show as rows of tracks; carousels as cards. */
        public boolean list;
        /** "See all": the page with everything in this shelf (an artist's full albums / singles). */
        public String moreId = "", moreParams = "";
    }

    public static class Page {
        public String title = "", subtitle = "", thumb = "", description = "";
        /** An artist's wide header picture (full size), "1.2M subscribers", "2.3M monthly audience". */
        public String banner = "", subscribers = "", monthly = "";
        public String channelId = "";
        public String playlistId = "";
        public final List<Shelf> shelves = new ArrayList<>();
        public String continuation;
        /** Results that come one per section (search), grouped into shelves afterwards. */
        final List<MItem> loose = new ArrayList<>();
        /** For a song list page: every song on it. */
        public List<MItem> songs() {
            List<MItem> l = new ArrayList<>();
            for (Shelf s : shelves) for (MItem m : s.items) if (m.playable()) l.add(m);
            return l;
        }
    }

    public static class Queue {
        public final List<MItem> items = new ArrayList<>();
        public String continuation;
        public String lyricsId;
        public String relatedId;
    }

    // ------------------------------------------------------------------ calls
    public static boolean signedIn() {
        try { return Account.signedInSafe(); } catch (Throwable e) { return false; }
    }

    static JSONObject call(String endpoint, JSONObject body, String extra) throws Exception {
        String token = null;
        if (signedIn()) try { token = Account.tokenFor(Account.activeKey()); } catch (Exception ignored) { }
        try {
            return post(endpoint, new JSONObject(body.toString()), extra, token);
        } catch (Exception e) {
            // A token YouTube Music won't take still lets you listen as a guest.
            if (token == null) throw e;
            return post(endpoint, new JSONObject(body.toString()), extra, null);
        }
    }

    private static JSONObject post(String endpoint, JSONObject body, String extra, String token) throws Exception {
        Locale l = Locale.getDefault();
        JSONObject client = new JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", VERSION)
                .put("hl", l.getLanguage().isEmpty() ? "en" : l.getLanguage())
                .put("gl", l.getCountry().isEmpty() ? "US" : l.getCountry());
        JSONObject user = new JSONObject();
        if (token != null) {
            try { String pg = Account.pageId(); if (pg != null && !pg.isEmpty()) user.put("onBehalfOfUser", pg); } catch (Throwable ignored) { }
        }
        body.put("context", new JSONObject().put("client", client).put("user", user));
        Request.Builder r = new Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/" + endpoint + "?prettyPrint=false" + (extra == null ? "" : extra))
                .header("User-Agent", UA)
                .header("X-Youtube-Client-Name", "67")
                .header("X-Youtube-Client-Version", VERSION)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .post(RequestBody.create(body.toString(), JSON));
        if (token != null) r.header("Authorization", "Bearer " + token);
        try (Response res = NpDownloader.get().client.newCall(r.build()).execute()) {
            String text = res.body() == null ? "" : res.body().string();
            if (!res.isSuccessful()) {
                String msg = "HTTP " + res.code();
                try { msg = new JSONObject(text).getJSONObject("error").optString("message", msg); } catch (Exception ignored) { }
                throw new Exception(msg);
            }
            return new JSONObject(text);
        }
    }

    // ------------------------------------------------------------------ pages
    public static Page browse(String browseId) throws Exception { return browse(browseId, null); }

    public static Page browse(String browseId, String params) throws Exception {
        JSONObject b = new JSONObject().put("browseId", browseId);
        if (params != null && !params.isEmpty()) b.put("params", params);
        return page(call("browse", b, null));
    }

    /** More shelves of a long page (Home keeps going as you scroll). */
    public static Page more(String browseId, String continuation) throws Exception {
        String c = java.net.URLEncoder.encode(continuation, "UTF-8");
        JSONObject body = new JSONObject();
        if (browseId != null) body.put("browseId", browseId);
        Page p = page(call("browse", body, "&ctoken=" + c + "&continuation=" + c + "&type=next"));
        if (p.shelves.isEmpty()) p = page(call("browse", new JSONObject().put("continuation", continuation), null));
        return p;
    }

    public static Page search(String q, boolean songsOnly) throws Exception {
        JSONObject b = new JSONObject().put("query", q);
        if (songsOnly) b.put("params", java.net.URLDecoder.decode(SONGS_ONLY, "UTF-8"));
        return page(call("search", b, null));
    }

    public static List<String> suggestions(String q) {
        List<String> out = new ArrayList<>();
        try {
            JSONObject r = call("music/get_search_suggestions", new JSONObject().put("input", q), null);
            List<JSONObject> l = new ArrayList<>();
            collect(r, "searchSuggestionRenderer", l);
            for (JSONObject s : l) { String t = runs(s.optJSONObject("suggestion")); if (!t.isEmpty() && out.size() < 8) out.add(t); }
        } catch (Exception ignored) { }
        return out;
    }

    /** What plays: YouTube Music's queue for a song (its radio) or for an album / playlist. */
    public static Queue queue(String videoId, String playlistId, boolean radio) throws Exception {
        JSONObject b = new JSONObject().put("isAudioOnly", true).put("enablePersistentPlaylistPanel", true).put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL");
        if (videoId != null && !videoId.isEmpty()) b.put("videoId", videoId);
        if (playlistId != null && !playlistId.isEmpty()) b.put("playlistId", playlistId.startsWith("VL") ? playlistId.substring(2) : playlistId);
        else if (radio && videoId != null) { b.put("playlistId", "RDAMVM" + videoId); b.put("params", "wAEB"); }
        return queueOf(call("next", b, null));
    }

    public static Queue queueMore(String continuation, String videoId, String playlistId) throws Exception {
        String c = java.net.URLEncoder.encode(continuation, "UTF-8");
        JSONObject b = new JSONObject().put("isAudioOnly", true).put("enablePersistentPlaylistPanel", true);
        if (videoId != null && !videoId.isEmpty()) b.put("videoId", videoId);
        if (playlistId != null && !playlistId.isEmpty()) b.put("playlistId", playlistId);
        return queueOf(call("next", b, "&ctoken=" + c + "&continuation=" + c + "&type=next"));
    }

    /** YouTube Music's lyrics for a song (plain text), or null. */
    public static String lyrics(String lyricsId) {
        if (lyricsId == null || lyricsId.isEmpty()) return null;
        try {
            JSONObject r = call("browse", new JSONObject().put("browseId", lyricsId), null);
            List<JSONObject> l = new ArrayList<>();
            collect(r, "musicDescriptionShelfRenderer", l);
            for (JSONObject d : l) { String t = runs(d.optJSONObject("description")); if (!t.isEmpty()) return t; }
        } catch (Exception ignored) { }
        return null;
    }

    /** Follow / unfollow an artist on your account. */
    public static void follow(String channelId, boolean on) throws Exception {
        call(on ? "subscription/subscribe" : "subscription/unsubscribe", new JSONObject().put("channelIds", new JSONArray().put(channelId)), null);
    }

    /**
     * YouTube Music's own timed lyrics (the ones its phone app shows), synced to this exact recording: each line with
     * the moment it starts and ends. Null when YouTube Music only has plain lyrics for the song.
     */
    public static JSONArray timedLyrics(String lyricsId) {
        if (lyricsId == null || lyricsId.isEmpty()) return null;
        String[][] clients = {{"ANDROID_MUSIC", "7.27.52", "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip", "21"},
                {"IOS_MUSIC", "7.27.0", "com.google.ios.youtubemusic/7.27.0 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)", "26"}};
        for (String[] c : clients) {
            try {
                Locale l = Locale.getDefault();
                JSONObject client = new JSONObject().put("clientName", c[0]).put("clientVersion", c[1])
                        .put("hl", l.getLanguage().isEmpty() ? "en" : l.getLanguage()).put("gl", l.getCountry().isEmpty() ? "US" : l.getCountry());
                if (c[0].startsWith("ANDROID")) client.put("androidSdkVersion", 30).put("osName", "Android").put("osVersion", "11");
                JSONObject body = new JSONObject().put("browseId", lyricsId).put("context", new JSONObject().put("client", client));
                Request r = new Request.Builder().url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
                        .header("User-Agent", c[2]).header("X-Youtube-Client-Name", c[3]).header("X-Youtube-Client-Version", c[1])
                        .post(RequestBody.create(body.toString(), JSON)).build();
                try (Response res = NpDownloader.get().client.newCall(r).execute()) {
                    if (!res.isSuccessful() || res.body() == null) continue;
                    JSONObject o = new JSONObject(res.body().string());
                    JSONArray a = findArr(o, "timedLyricsData", 0);
                    if (a != null && a.length() > 0) return a;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static JSONArray findArr(Object node, String key, int depth) {
        if (node == null || depth > 30) return null;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONArray v = o.optJSONArray(key);
            if (v != null) return v;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) { JSONArray f = findArr(o.opt(it.next()), key, depth + 1); if (f != null) return f; }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) { JSONArray f = findArr(a.opt(i), key, depth + 1); if (f != null) return f; }
        }
        return null;
    }

    /** Like / unlike a song on your account. */
    public static void like(String videoId, boolean on) throws Exception {
        call(on ? "like/like" : "like/removelike", new JSONObject().put("target", new JSONObject().put("videoId", videoId)), null);
    }

    // ------------------------------------------------------------------ parsing
    private static Queue queueOf(JSONObject r) {
        Queue q = new Queue();
        List<JSONObject> panels = new ArrayList<>();
        collect(r, "playlistPanelRenderer", panels);
        JSONObject cont = r.optJSONObject("continuationContents");
        if (cont != null && cont.optJSONObject("playlistPanelContinuation") != null) panels.add(0, cont.optJSONObject("playlistPanelContinuation"));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (JSONObject p : panels) {
            JSONArray c = p.optJSONArray("contents");
            if (c != null) for (int i = 0; i < c.length(); i++) {
                JSONObject o = c.optJSONObject(i);
                if (o == null) continue;
                JSONObject v = o.optJSONObject("playlistPanelVideoRenderer");
                if (v == null) {
                    JSONObject w = o.optJSONObject("playlistPanelVideoWrapperRenderer");
                    if (w != null) v = findObj(w, "playlistPanelVideoRenderer");
                }
                if (v == null) continue;
                MItem m = panelSong(v);
                if (!m.videoId.isEmpty() && seen.add(m.videoId)) q.items.add(m);
            }
            if (q.continuation == null) q.continuation = contToken(p);
            if (!q.items.isEmpty()) break;
        }
        // The tabs beside the queue: Lyrics and Related.
        List<JSONObject> tabs = new ArrayList<>();
        collect(r, "tabRenderer", tabs);
        for (JSONObject t : tabs) {
            JSONObject be = findObj(t.optJSONObject("endpoint"), "browseEndpoint");
            String id = be == null ? "" : be.optString("browseId");
            if (id.startsWith("MPLY") && !t.optBoolean("unselectable")) q.lyricsId = id;
            if (id.startsWith("MPTRt")) q.relatedId = id;
        }
        return q;
    }

    private static MItem panelSong(JSONObject v) {
        MItem m = new MItem();
        m.type = MItem.SONG;
        m.videoId = v.optString("videoId");
        m.title = runs(v.optJSONObject("title"));
        JSONArray by = runArray(v.optJSONObject("longBylineText"));
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        if (by != null) for (int i = 0; i < by.length(); i++) {
            String t = by.optJSONObject(i).optString("text");
            if (t.trim().equals("•")) { parts.add(cur.toString().trim()); cur.setLength(0); } else cur.append(t);
        }
        if (cur.length() > 0) parts.add(cur.toString().trim());
        if (!parts.isEmpty()) m.artist = parts.get(0);
        if (by != null) for (int i = 0; i < by.length(); i++) {
            JSONObject be = findObj(by.optJSONObject(i).optJSONObject("navigationEndpoint"), "browseEndpoint");
            if (be != null && be.optString("browseId").startsWith("UC")) { m.artistId = be.optString("browseId"); break; }
        }
        if (parts.size() > 1 && !parts.get(1).matches(".*\\d.*(views|plays).*")) m.album = parts.get(1);
        m.subtitle = m.artist + (m.album.isEmpty() ? "" : " • " + m.album);
        m.seconds = secs(runs(v.optJSONObject("lengthText")));
        m.thumb = bestThumb(v.optJSONObject("thumbnail"));
        JSONObject we = findObj(v.optJSONObject("navigationEndpoint"), "watchEndpoint");
        if (we != null) m.playlistId = we.optString("playlistId");
        m.mvType = mvType(v.optJSONObject("navigationEndpoint"));
        return m;
    }

    static final String[] SHELVES = {"musicCarouselShelfRenderer", "musicShelfRenderer", "musicPlaylistShelfRenderer",
            "gridRenderer", "musicImmersiveCarouselShelfRenderer", "musicCardShelfRenderer"};

    static Page page(JSONObject r) {
        Page p = new Page();
        header(r, p);
        walk(r, p, 0);
        group(p);
        // Continuation for more shelves.
        List<JSONObject> lists = new ArrayList<>();
        collect(r, "sectionListRenderer", lists);
        JSONObject cc = r.optJSONObject("continuationContents");
        if (cc != null && cc.optJSONObject("sectionListContinuation") != null) lists.add(0, cc.optJSONObject("sectionListContinuation"));
        for (JSONObject l : lists) { String t = contToken(l); if (t != null) { p.continuation = t; break; } }
        // An album's songs have no art of their own: they use the album's.
        for (Shelf s : p.shelves) for (MItem m : s.items) {
            if (m.thumb.isEmpty()) m.thumb = p.thumb;
            if (m.album.isEmpty() && s.list && !p.title.isEmpty() && p.subtitle.toLowerCase(Locale.US).contains("album")) m.album = p.title;
        }
        return p;
    }

    /** Search results by kind, in YouTube Music's order of usefulness. */
    private static void group(Page p) {
        if (p.loose.isEmpty()) return;
        String[][] kinds = {{"song", "Songs"}, {MItem.ALBUM, "Albums"}, {MItem.ARTIST, "Artists"}, {MItem.PLAYLIST, "Playlists"}, {"video", "Videos"}};
        int at = 0;
        for (Shelf s : p.shelves) if ("Top result".equals(s.title)) at = p.shelves.indexOf(s) + 1;
        for (String[] k : kinds) {
            Shelf sh = new Shelf();
            sh.title = k[1];
            for (MItem m : p.loose) {
                boolean video = m.playable() && m.thumb.contains("i.ytimg.com");
                String kind = m.playable() ? (video ? "video" : "song") : m.type;
                if (kind.equals(k[0])) sh.items.add(m);
            }
            sh.list = k[0].equals("song");
            if (!sh.items.isEmpty()) p.shelves.add(Math.min(at++, p.shelves.size()), sh);
        }
        p.loose.clear();
    }

    private static void header(JSONObject r, Page p) {
        for (String k : new String[]{"musicResponsiveHeaderRenderer", "musicImmersiveHeaderRenderer", "musicDetailHeaderRenderer",
                "musicVisualHeaderRenderer", "musicEditablePlaylistDetailHeaderRenderer", "musicHeaderRenderer"}) {
            JSONObject h = findObj(r, k);
            if (h == null) continue;
            if (k.equals("musicEditablePlaylistDetailHeaderRenderer")) {
                JSONObject inner = findObj(h, "musicResponsiveHeaderRenderer");
                if (inner == null) inner = findObj(h, "musicDetailHeaderRenderer");
                if (inner != null) h = inner;
            }
            p.title = runs(h.optJSONObject("title"));
            String strap = runs(h.optJSONObject("straplineTextOne"));
            String sub = runs(h.optJSONObject("subtitle"));
            p.subtitle = strap.isEmpty() ? sub : strap + (sub.isEmpty() ? "" : "  •  " + sub);
            String sec = runs(h.optJSONObject("secondSubtitle"));
            if (!sec.isEmpty()) p.subtitle += (p.subtitle.isEmpty() ? "" : "  •  ") + sec;
            JSONObject d = h.optJSONObject("description");
            if (d != null) {
                String t = runs(d);
                if (t.isEmpty()) { JSONObject ds = findObj(d, "musicDescriptionShelfRenderer"); if (ds != null) t = runs(ds.optJSONObject("description")); }
                p.description = t;
            }
            p.thumb = bestThumb(h.optJSONObject("thumbnail"));
            if (k.equals("musicImmersiveHeaderRenderer") || k.equals("musicVisualHeaderRenderer")) p.banner = widest(h.optJSONObject("thumbnail"));
            JSONObject subs = findObj(h, "subscriberCountText");
            if (subs != null) p.subscribers = runs(subs);
            JSONObject sb = findObj(h, "subscribeButtonRenderer");
            if (sb != null) { p.channelId = sb.optString("channelId"); if (p.subscribers.isEmpty()) p.subscribers = runs(sb.optJSONObject("subscriberCountText")); }
            if (p.subscribers.matches("[\\d.,]+\\s*[KMB]?")) p.subscribers = p.subscribers + " subscribers";
            JSONObject mon = h.optJSONObject("monthlyListenerCount");
            if (mon != null) p.monthly = runs(mon);
            if (p.thumb.isEmpty()) p.thumb = bestThumb(h.optJSONObject("foregroundThumbnail"));
            JSONObject wp = findObj(h, "watchPlaylistEndpoint");
            if (wp != null) p.playlistId = wp.optString("playlistId");
            if (p.playlistId.isEmpty()) { JSONObject we = findObj(h, "watchEndpoint"); if (we != null) p.playlistId = we.optString("playlistId"); }
            if (!p.title.isEmpty()) return;
        }
    }

    private static void walk(Object node, Page p, int depth) {
        if (depth > 40 || node == null) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) walk(a.opt(i), p, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        for (String k : SHELVES) {
            JSONObject s = o.optJSONObject(k);
            if (s == null) continue;
            Shelf sh = shelf(k, s);
            if (!sh.items.isEmpty()) p.shelves.add(sh);
            return;
        }
        // Search: one result per section, no headings.
        JSONObject is = o.optJSONObject("itemSectionRenderer");
        if (is != null && is.optJSONArray("contents") != null) {
            JSONArray c = is.optJSONArray("contents");
            boolean any = false;
            for (int i = 0; i < c.length(); i++) {
                JSONObject x = c.optJSONObject(i);
                if (x == null || !x.has("musicResponsiveListItemRenderer")) continue;
                MItem m = listItem(x.optJSONObject("musicResponsiveListItemRenderer"));
                if (m != null && !m.title.isEmpty()) { p.loose.add(m); any = true; }
            }
            if (any) return;
        }
        // Mood / genre buttons sit in grids of their own.
        JSONObject nb = o.optJSONObject("musicNavigationButtonRenderer");
        if (nb != null) {
            MItem m = navButton(nb);
            if (m != null) {
                Shelf last = p.shelves.isEmpty() ? null : p.shelves.get(p.shelves.size() - 1);
                if (last == null || !last.items.isEmpty() && !MItem.CATEGORY.equals(last.items.get(0).type)) { last = new Shelf(); p.shelves.add(last); }
                last.items.add(m);
            }
            return;
        }
        java.util.Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.endsWith("HeaderRenderer") || k.equals("frameworkUpdates") || k.equals("responseContext")) continue;
            walk(o.opt(k), p, depth + 1);
        }
    }

    private static Shelf shelf(String kind, JSONObject s) {
        Shelf sh = new Shelf();
        JSONObject head = s.optJSONObject("header");
        if (head != null) {
            JSONObject h = findObj(head, "musicCarouselShelfBasicHeaderRenderer");
            if (h == null) h = findObj(head, "gridHeaderRenderer");
            if (h == null) h = head;
            sh.title = runs(h.optJSONObject("title"));
            sh.strap = runs(h.optJSONObject("strapline"));
            JSONObject more = findObj(h.optJSONObject("moreContentButton"), "browseEndpoint");
            if (more == null) more = findObj(h.optJSONObject("title"), "browseEndpoint");
            if (more != null) { sh.moreId = more.optString("browseId"); sh.moreParams = more.optString("params"); }
        }
        if (sh.moreId.isEmpty()) {
            JSONObject more = findObj(s.optJSONObject("bottomEndpoint"), "browseEndpoint");
            if (more != null) { sh.moreId = more.optString("browseId"); sh.moreParams = more.optString("params"); }
        }
        if (sh.title.isEmpty()) sh.title = runs(s.optJSONObject("title"));
        sh.list = kind.equals("musicShelfRenderer") || kind.equals("musicPlaylistShelfRenderer");
        if (kind.equals("musicCardShelfRenderer")) {
            // Search's top result.
            MItem top = twoRow(s);
            if (top != null) { sh.title = "Top result"; sh.items.add(top); }
        }
        JSONArray c = s.optJSONArray("contents");
        if (c == null) c = s.optJSONArray("items");
        if (c != null) for (int i = 0; i < c.length(); i++) {
            JSONObject o = c.optJSONObject(i);
            if (o == null) continue;
            MItem m = null;
            if (o.has("musicTwoRowItemRenderer")) m = twoRow(o.optJSONObject("musicTwoRowItemRenderer"));
            else if (o.has("musicResponsiveListItemRenderer")) m = listItem(o.optJSONObject("musicResponsiveListItemRenderer"));
            else if (o.has("musicMultiRowListItemRenderer")) m = twoRow(o.optJSONObject("musicMultiRowListItemRenderer"));
            else if (o.has("musicNavigationButtonRenderer")) m = navButton(o.optJSONObject("musicNavigationButtonRenderer"));
            // Podcast shows and episodes aren't music: left out.
            if (m != null && (m.browseId.startsWith("MPSP") || m.browseId.startsWith("MPED"))) continue;
            if (m != null && !m.title.isEmpty()) sh.items.add(m);
        }
        return sh;
    }

    private static MItem navButton(JSONObject nb) {
        MItem m = new MItem();
        m.type = MItem.CATEGORY;
        m.title = runs(nb.optJSONObject("buttonText"));
        JSONObject be = findObj(nb.optJSONObject("clickCommand"), "browseEndpoint");
        if (be == null) return null;
        m.browseId = be.optString("browseId");
        m.params = be.optString("params");
        JSONObject solid = nb.optJSONObject("solid");
        if (solid != null) m.color = (int) (solid.optLong("leftStripeColor", 0xFF444444L) | 0xFF000000L);
        return m;
    }

    /** A card: album, playlist, artist, mix or a song. */
    private static MItem twoRow(JSONObject r) {
        if (r == null) return null;
        MItem m = new MItem();
        m.title = runs(r.optJSONObject("title"));
        m.subtitle = runs(r.optJSONObject("subtitle"));
        m.thumb = bestThumb(r.optJSONObject("thumbnailRenderer"));
        if (m.thumb.isEmpty()) m.thumb = bestThumb(r.optJSONObject("thumbnail"));
        JSONObject nav = r.optJSONObject("navigationEndpoint");
        if (nav == null) { JSONObject t = r.optJSONObject("title"); JSONArray ra = runArray(t); if (ra != null && ra.length() > 0) nav = ra.optJSONObject(0).optJSONObject("navigationEndpoint"); }
        fill(m, nav);
        if (m.playlistId.isEmpty()) {
            JSONObject play = findObj(r.optJSONObject("thumbnailOverlay"), "watchPlaylistEndpoint");
            if (play != null) m.playlistId = play.optString("playlistId");
        }
        JSONArray sr = runArray(r.optJSONObject("subtitle"));
        if (sr != null) for (int i = 0; i < sr.length(); i++) {
            JSONObject be = findObj(sr.optJSONObject(i).optJSONObject("navigationEndpoint"), "browseEndpoint");
            if (be != null && be.optString("browseId").startsWith("UC")) { m.artistId = be.optString("browseId"); if (m.artist.isEmpty()) m.artist = sr.optJSONObject(i).optString("text"); break; }
        }
        if (MItem.SONG.equals(m.type)) m.mvType = mvType(nav);
        String[] sub = m.subtitle.split(" • ");
        if (MItem.SONG.equals(m.type) && sub.length > 0 && m.artist.isEmpty()) m.artist = sub[sub.length > 1 && sub[0].equalsIgnoreCase("Song") ? 1 : 0];
        return m.title.isEmpty() ? null : m;
    }

    /** A row: a song in a list (title, artist, album, length), or an artist / album in search. */
    private static MItem listItem(JSONObject r) {
        if (r == null) return null;
        MItem m = new MItem();
        JSONArray cols = r.optJSONArray("flexColumns");
        List<String> texts = new ArrayList<>();
        List<JSONArray> runsList = new ArrayList<>();
        if (cols != null) for (int i = 0; i < cols.length(); i++) {
            JSONObject c = findObj(cols.optJSONObject(i), "musicResponsiveListItemFlexColumnRenderer");
            JSONObject t = c == null ? null : c.optJSONObject("text");
            texts.add(runs(t));
            runsList.add(runArray(t));
        }
        if (texts.isEmpty()) return null;
        m.title = texts.get(0);
        // Second column: "Song • Artist • Album • 3:12" in search, "Artist" / "Album" in lists.
        List<String> rest = new ArrayList<>();
        for (int i = 1; i < texts.size(); i++) for (String part : texts.get(i).split(" • ")) if (!part.trim().isEmpty()) rest.add(part.trim());
        JSONObject fixed = null;
        JSONArray fc = r.optJSONArray("fixedColumns");
        if (fc != null && fc.length() > 0) fixed = findObj(fc.optJSONObject(0), "musicResponsiveListItemFixedColumnRenderer");
        String len = fixed == null ? "" : runs(fixed.optJSONObject("text"));
        m.thumb = bestThumb(r.optJSONObject("thumbnail"));
        JSONObject nav = r.optJSONObject("navigationEndpoint");
        JSONObject watch = null;
        if (runsList.get(0) != null && runsList.get(0).length() > 0) {
            JSONObject ne = runsList.get(0).optJSONObject(0).optJSONObject("navigationEndpoint");
            if (ne != null) { watch = findObj(ne, "watchEndpoint"); if (nav == null) nav = ne; }
        }
        JSONObject pid = r.optJSONObject("playlistItemData");
        if (pid != null) m.videoId = pid.optString("videoId");
        if (m.videoId.isEmpty()) {
            JSONObject we = watch != null ? watch : findObj(r.optJSONObject("overlay"), "watchEndpoint");
            if (we != null) m.videoId = we.optString("videoId");
        }
        if (!m.videoId.isEmpty()) {
            m.type = MItem.SONG;
            m.mvType = mvType(watch != null ? watch : r.optJSONObject("overlay"));
            if (m.mvType.isEmpty()) m.mvType = mvType(r.optJSONObject("overlay"));
        } else {
            fill(m, nav);
        }
        // Artist and album: the runs that open an artist / album page.
        for (int i = 1; i < runsList.size(); i++) {
            JSONArray ra = runsList.get(i);
            if (ra == null) continue;
            for (int j = 0; j < ra.length(); j++) {
                JSONObject run = ra.optJSONObject(j);
                JSONObject be = findObj(run.optJSONObject("navigationEndpoint"), "browseEndpoint");
                if (be == null) continue;
                String pt = pageType(be);
                if (pt.endsWith("ARTIST") && m.artist.isEmpty()) { m.artist = run.optString("text"); m.artistId = be.optString("browseId"); }
                if (pt.endsWith("ALBUM") && m.album.isEmpty()) m.album = run.optString("text");
            }
        }
        List<String> shown = new ArrayList<>();
        for (String s : rest) {
            if (s.equalsIgnoreCase("Song") || s.equalsIgnoreCase("Video")) continue;
            if (s.matches("\\d+:\\d+(:\\d+)?")) { if (len.isEmpty()) len = s; continue; }
            if (s.matches("(?i).*\\b(plays|views)\\b.*")) continue;
            shown.add(s);
        }
        if (m.artist.isEmpty() && !shown.isEmpty() && MItem.SONG.equals(m.type)) m.artist = shown.get(0);
        m.subtitle = android_join(shown);
        m.seconds = secs(len);
        return m.title.isEmpty() ? null : m;
    }

    private static String android_join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(" • "); b.append(s); }
        return b.toString();
    }

    private static void fill(MItem m, JSONObject nav) {
        if (nav == null) return;
        JSONObject we = findObj(nav, "watchEndpoint");
        JSONObject be = findObj(nav, "browseEndpoint");
        if (be != null) {
            m.browseId = be.optString("browseId");
            m.params = be.optString("params");
            String pt = pageType(be);
            if (pt.endsWith("ARTIST") || pt.endsWith("USER_CHANNEL") || m.browseId.startsWith("UC")) m.type = MItem.ARTIST;
            else if (pt.endsWith("ALBUM") || m.browseId.startsWith("MPRE")) m.type = MItem.ALBUM;
            else m.type = MItem.PLAYLIST;
            if (m.browseId.startsWith("VL")) m.playlistId = m.browseId.substring(2);
        } else if (we != null) {
            m.videoId = we.optString("videoId");
            m.playlistId = we.optString("playlistId");
            m.type = m.videoId.isEmpty() ? MItem.PLAYLIST : MItem.SONG;
        }
    }

    private static String mvType(Object holder) {
        JSONObject c = findObj(holder, "watchEndpointMusicConfig");
        return c == null ? "" : c.optString("musicVideoType");
    }

    /** Makes a playlist on your YouTube account from these songs; returns its id. */
    public static String createPlaylist(String title, String description, List<String> videoIds) throws Exception {
        JSONArray ids = new JSONArray();
        for (String v : videoIds) ids.put(v);
        JSONObject r = call("playlist/create", new JSONObject().put("title", title).put("description", description)
                .put("privacyStatus", "PRIVATE").put("videoIds", ids), null);
        return r.optString("playlistId");
    }

    private static String pageType(JSONObject be) {
        JSONObject c = findObj(be, "browseEndpointContextMusicConfig");
        return c == null ? "" : c.optString("pageType");
    }

    // ------------------------------------------------------------------ JSON helpers
    static String runs(JSONObject t) {
        if (t == null) return "";
        if (t.has("simpleText")) return t.optString("simpleText");
        JSONArray a = t.optJSONArray("runs");
        if (a == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < a.length(); i++) { JSONObject r = a.optJSONObject(i); if (r != null) b.append(r.optString("text")); }
        return b.toString().trim();
    }

    private static JSONArray runArray(JSONObject t) { return t == null ? null : t.optJSONArray("runs"); }

    /** The biggest picture as YouTube gives it (wide pictures stay wide). */
    static String widest(JSONObject o) {
        if (o == null) return "";
        List<JSONObject> l = new ArrayList<>();
        holders(o, "thumbnails", l);
        String best = "";
        int bw = -1;
        for (JSONObject holder : l) {
            JSONArray a = holder.optJSONArray("thumbnails");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                JSONObject t = a.optJSONObject(i);
                if (t != null && t.optInt("width") > bw) { bw = t.optInt("width"); best = t.optString("url"); }
            }
        }
        if (best.startsWith("//")) best = "https:" + best;
        return best;
    }

    static String bestThumb(JSONObject o) {
        if (o == null) return "";
        List<JSONObject> l = new ArrayList<>();
        holders(o, "thumbnails", l);
        String best = "";
        int bw = -1;
        for (JSONObject holder : l) {
            JSONArray a = holder.optJSONArray("thumbnails");
            if (a == null) continue;
            for (int i = 0; i < a.length(); i++) {
                JSONObject t = a.optJSONObject(i);
                if (t == null) continue;
                int w = t.optInt("width");
                if (w > bw) { bw = w; best = t.optString("url"); }
            }
        }
        return MItem.art(best, 544);
    }

    private static int secs(String t) {
        if (t == null || !t.matches("\\d+(:\\d+){1,2}")) return 0;
        int s = 0;
        for (String p : t.split(":")) s = s * 60 + Integer.parseInt(p);
        return s;
    }

    private static String contToken(JSONObject o) {
        JSONArray c = o == null ? null : o.optJSONArray("continuations");
        if (c == null || c.length() == 0) return null;
        JSONObject f = c.optJSONObject(0);
        for (String k : new String[]{"nextContinuationData", "nextRadioContinuationData", "reloadContinuationData"}) {
            JSONObject d = f.optJSONObject(k);
            if (d != null && !d.optString("continuation").isEmpty()) return d.optString("continuation");
        }
        return null;
    }

    static JSONObject findObj(Object node, String key) {
        return findObj(node, key, 0);
    }

    private static JSONObject findObj(Object node, String key, int depth) {
        if (node == null || depth > 30) return null;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONObject v = o.optJSONObject(key);
            if (v != null) return v;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) { JSONObject f = findObj(o.opt(it.next()), key, depth + 1); if (f != null) return f; }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) { JSONObject f = findObj(a.opt(i), key, depth + 1); if (f != null) return f; }
        }
        return null;
    }

    /** Every object under node keyed key. */
    static void collect(Object node, String key, List<JSONObject> out) { collectRec(node, key, out, null, 0); }

    /** Every object under node that has key. */
    private static void holders(Object node, String key, List<JSONObject> out) { collectRec(node, key, null, out, 0); }

    private static void collectRec(Object node, String key, List<JSONObject> out, List<JSONObject> holders, int depth) {
        if (node == null || depth > 40) return;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            if (o.has(key)) {
                if (holders != null) holders.add(o);
                if (out != null && o.optJSONObject(key) != null) out.add(o.optJSONObject(key));
            }
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) collectRec(o.opt(it.next()), key, out, holders, depth + 1);
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectRec(a.opt(i), key, out, holders, depth + 1);
        }
    }

    private Ytm() { }
}
