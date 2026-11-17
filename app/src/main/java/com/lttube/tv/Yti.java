package com.lttube.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Your YouTube account: feeds and actions, using the TV sign-in. */
public final class Yti {
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String TV_VERSION = "7.20251113.10.00";
    private static final String WEB_FALLBACK_VERSION = "2.20251113.01.00";
    private static final String TV_UA =
            "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)";

    private static SharedPreferences p;
    private static String webVersion;

    public static void init(Context c) {
        p = c.getSharedPreferences("yti", Context.MODE_PRIVATE);
    }

    // Your feeds. Each has a browse id for the web and the TV flavour of YouTube's API.
    public static final String HOME = "home", SUBS = "subs", HISTORY = "history", WATCH_LATER = "wl",
            LIKED = "liked", PLAYLISTS = "playlists", CHANNELS = "channels";

    private static String browseId(String feed, boolean tv) {
        switch (feed) {
            case HOME: return tv ? "FEtopics" : "FEwhat_to_watch";
            case SUBS: return "FEsubscriptions";
            case HISTORY: return "FEhistory";
            case WATCH_LATER: return "VLWL";
            case LIKED: return "VLLL";
            case PLAYLISTS: return tv ? "FEmy_youtube" : "FEplaylist_aggregation";
            case CHANNELS: return tv ? "FEsubscriptions" : "FEchannels";
            default: return feed;
        }
    }

    public static class Page {
        public final List<Item> items = new ArrayList<>();
        public String continuation;
        boolean tv;
    }

    // ------------------------------------------------------------------ feeds
    /** One of your feeds. Tries whichever flavour of YouTube's API worked last time first. */
    public static Page feed(String feed) throws Exception {
        boolean tvFirst = p.getBoolean("tv", true);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean tv = attempt == 0 ? tvFirst : !tvFirst;
            try {
                JSONObject r = call("browse", new JSONObject().put("browseId", browseId(feed, tv)), tv);
                Page pg = parse(r, feed.equals(CHANNELS) ? Item.CHANNEL : null);
                pg.tv = tv;
                if (!pg.items.isEmpty() || attempt == 1) {
                    if (!feed.equals(CHANNELS) && !pg.items.isEmpty()) p.edit().putBoolean("tv", tv).apply();
                    return pg;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return new Page();
    }

    public static Page more(String continuation, boolean tv, String only) throws Exception {
        JSONObject r = call("browse", new JSONObject().put("continuation", continuation), tv);
        Page pg = parse(r, only);
        pg.tv = tv;
        return pg;
    }

    public static boolean tvFlavour() { return p.getBoolean("tv", true); }

    // ------------------------------------------------------------------ actions
    public static void like(String videoId, String kind) throws Exception {
        // kind: "like", "dislike" or "removelike"
        call("like/" + kind, new JSONObject().put("target", new JSONObject().put("videoId", videoId)), tvFlavour());
    }

    public static void subscribe(String channelId, boolean on) throws Exception {
        call("subscription/" + (on ? "subscribe" : "unsubscribe"),
                new JSONObject().put("channelIds", new JSONArray().put(channelId)), tvFlavour());
    }

    public static void watchLater(String videoId, boolean add) throws Exception {
        JSONObject action = new JSONObject().put("action", add ? "ACTION_ADD_VIDEO" : "ACTION_REMOVE_VIDEO_BY_VIDEO_ID");
        action.put(add ? "addedVideoId" : "removedVideoId", videoId);
        call("browse/edit_playlist", new JSONObject().put("playlistId", "WL").put("actions", new JSONArray().put(action)), tvFlavour());
    }

    /** Like/subscribe state for a video, from YouTube. [likeStatus, subscribed] */
    public static String[] videoState(String videoId) throws Exception {
        JSONObject r = call("next", new JSONObject().put("videoId", videoId), tvFlavour());
        String like = findString(r, "likeStatus");
        Boolean sub = findBool(r, "subscribed");
        return new String[]{like == null ? "INDIFFERENT" : like, sub == null ? "" : String.valueOf(sub)};
    }

    public static Boolean channelSubscribed(String channelId) {
        try {
            JSONObject r = call("browse", new JSONObject().put("browseId", channelId), tvFlavour());
            return findBool(r, "subscribed");
        } catch (Exception e) {
            return null;
        }
    }

    /** Adds the video to your YouTube watch history. */
    public static void markWatched(String videoId) {
        try {
            boolean tv = tvFlavour();
            JSONObject r = call("player", new JSONObject().put("videoId", videoId)
                    .put("racyCheckOk", true).put("contentCheckOk", true), tv);
            JSONObject track = r.optJSONObject("playbackTracking");
            if (track == null) return;
            JSONObject u = track.optJSONObject("videostatsPlaybackUrl");
            if (u == null) return;
            String url = u.optString("baseUrl").replace("https://s.youtube.com", "https://www.youtube.com");
            String cpn = randomCpn();
            url += (url.contains("?") ? "&" : "?") + "ver=2&c=" + (tv ? "TVHTML5" : "WEB") + "&cver="
                    + (tv ? TV_VERSION : web()) + "&cpn=" + cpn + "&fmt=251&rtn=0&rt=0";
            Request.Builder b = new Request.Builder().url(url).header("User-Agent", tv ? TV_UA : NpDownloader.USER_AGENT);
            String t = Account.token();
            if (t != null) b.header("Authorization", "Bearer " + t);
            try (Response ignored = NpDownloader.get().client.newCall(b.build()).execute()) { /* done */ }
        } catch (Exception ignored) { }
    }

    /** Your name and picture. */
    public static void loadProfile() {
        for (int i = 0; i < 2; i++) {
            boolean tv = i == 0;
            try {
                JSONObject r = call(tv ? "account/accounts_list" : "account/account_menu", new JSONObject(), tv);
                JSONObject name = findObject(r, "accountName");
                JSONObject photo = findObject(r, "accountPhoto");
                String n = name == null ? null : text(name);
                String ph = photo == null ? "" : bestThumb(photo);
                if (n != null && !n.isEmpty()) { Account.saveProfile(n, ph); return; }
            } catch (Exception ignored) { }
        }
    }

    // ------------------------------------------------------------------ the API call
    static JSONObject call(String endpoint, JSONObject body, boolean tv) throws Exception {
        String token = Account.token();
        if (token == null) throw new Exception("Not signed in");
        Locale l = Locale.getDefault();
        JSONObject client = new JSONObject()
                .put("clientName", tv ? "TVHTML5" : "WEB")
                .put("clientVersion", tv ? TV_VERSION : web())
                .put("hl", l.getLanguage())
                .put("gl", l.getCountry().isEmpty() ? "US" : l.getCountry());
        if (tv) client.put("platform", "TV").put("clientFormFactor", "UNKNOWN_FORM_FACTOR");
        body.put("context", new JSONObject().put("client", client).put("user", new JSONObject()));

        Request r = new Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/" + endpoint + "?prettyPrint=false")
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", tv ? TV_UA : NpDownloader.USER_AGENT)
                .header("X-Youtube-Client-Name", tv ? "7" : "1")
                .header("X-Youtube-Client-Version", tv ? TV_VERSION : web())
                .header("Origin", "https://www.youtube.com")
                .header("Referer", tv ? "https://www.youtube.com/tv" : "https://www.youtube.com/")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            String text = res.body() == null ? "" : res.body().string();
            if (!res.isSuccessful()) {
                String msg = "HTTP " + res.code();
                try { msg = new JSONObject(text).getJSONObject("error").optString("message", msg); } catch (Exception ignored) { }
                throw new Exception(msg);
            }
            return new JSONObject(text);
        }
    }

    private static String web() {
        if (webVersion == null) {
            try { webVersion = YoutubeParsingHelper.getClientVersion(); }
            catch (Exception e) { webVersion = WEB_FALLBACK_VERSION; }
        }
        return webVersion;
    }

    private static String randomCpn() {
        String abc = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_";
        SecureRandom r = new SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 16; i++) b.append(abc.charAt(r.nextInt(abc.length())));
        return b.toString();
    }

    // ------------------------------------------------------------------ reading YouTube's responses
    /** Finds every video / channel / playlist anywhere in a response (web and TV layouts). */
    static Page parse(JSONObject root, String only) {
        Page pg = new Page();
        Set<String> seen = new HashSet<>();
        walk(root, pg, seen, only);
        return pg;
    }

    private static void walk(Object node, Page pg, Set<String> seen, String only) {
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) walk(a.opt(i), pg, seen, only);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = o.opt(k);
            if (!(v instanceof JSONObject)) { walk(v, pg, seen, only); continue; }
            JSONObject r = (JSONObject) v;
            Item it = null;
            switch (k) {
                case "videoRenderer": case "gridVideoRenderer": case "compactVideoRenderer":
                case "playlistVideoRenderer": case "playlistPanelVideoRenderer": case "videoWithContextRenderer":
                    it = video(r); break;
                case "reelItemRenderer": case "shortsLockupViewModel":
                    it = shortVideo(r); break;
                case "lockupViewModel":
                    it = lockup(r); break;
                case "tileRenderer":
                    it = tile(r); break;
                case "channelRenderer": case "gridChannelRenderer":
                    it = channel(r); break;
                case "gridPlaylistRenderer": case "playlistRenderer":
                    it = playlist(r); break;
                case "continuationItemRenderer": {
                    String t = findString(r, "token");
                    if (t != null && pg.continuation == null) pg.continuation = t;
                    continue;
                }
                case "nextContinuationData": {
                    String t = r.optString("continuation", null);
                    if (t != null && pg.continuation == null) pg.continuation = t;
                    continue;
                }
                default:
                    walk(r, pg, seen, only);
                    continue;
            }
            if (it == null || it.url.isEmpty()) continue;
            if (only != null && !only.equals(it.type)) continue;
            if (seen.add(it.url)) pg.items.add(it);
        }
    }

    private static Item video(JSONObject r) {
        String id = r.optString("videoId", "");
        if (id.isEmpty()) return null;
        Item it = new Item();
        it.type = Item.VIDEO;
        it.url = "https://www.youtube.com/watch?v=" + id;
        it.title = text(r.optJSONObject("title"));
        if (it.title.isEmpty()) it.title = text(r.optJSONObject("headline"));
        it.thumb = bestThumb(r.optJSONObject("thumbnail"));
        if (it.thumb.isEmpty()) it.thumb = Item.videoThumb(it.url);
        JSONObject by = r.optJSONObject("shortBylineText");
        if (by == null) by = r.optJSONObject("longBylineText");
        if (by == null) by = r.optJSONObject("ownerText");
        it.channel = text(by);
        String cid = findString(by, "browseId");
        if (cid != null) it.channelUrl = "https://www.youtube.com/channel/" + cid;
        String len = text(r.optJSONObject("lengthText"));
        if (len.isEmpty()) len = overlayTime(r);
        it.duration = seconds(len);
        it.live = len.isEmpty() && String.valueOf(r.optJSONArray("badges")).contains("LIVE");
        String views = text(r.optJSONObject("shortViewCountText"));
        if (views.isEmpty()) views = text(r.optJSONObject("viewCountText"));
        String when = text(r.optJSONObject("publishedTimeText"));
        it.sub = join(it.channel, views, when);
        JSONObject resume = findObject(r, "thumbnailOverlayResumePlaybackRenderer");
        if (resume != null && it.duration > 0) it.position = it.duration * 1000 * resume.optInt("percentDurationWatched") / 100;
        return it;
    }

    private static Item shortVideo(JSONObject r) {
        String id = r.optString("videoId", "");
        if (id.isEmpty()) {
            JSONObject e = findObject(r, "reelWatchEndpoint");
            if (e != null) id = e.optString("videoId", "");
        }
        if (id.isEmpty()) return null;
        Item it = new Item();
        it.type = Item.VIDEO;
        it.isShort = true;
        it.url = "https://www.youtube.com/watch?v=" + id;
        it.title = text(r.optJSONObject("headline"));
        if (it.title.isEmpty()) {
            JSONObject om = r.optJSONObject("overlayMetadata");
            if (om != null) it.title = text(om.optJSONObject("primaryText"));
        }
        it.thumb = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        it.sub = "Short";
        return it;
    }

    private static Item lockup(JSONObject r) {
        String id = r.optString("contentId", "");
        String type = r.optString("contentType", "");
        if (id.isEmpty()) return null;
        Item it = new Item();
        JSONObject meta = findObject(r, "lockupMetadataViewModel");
        it.title = meta == null ? "" : text(meta.optJSONObject("title"));
        List<String> parts = new ArrayList<>();
        JSONObject rows = meta == null ? null : findObject(meta, "contentMetadataViewModel");
        if (rows != null) {
            JSONArray mr = rows.optJSONArray("metadataRows");
            for (int i = 0; mr != null && i < mr.length(); i++) {
                JSONArray ps = mr.optJSONObject(i) == null ? null : mr.optJSONObject(i).optJSONArray("metadataParts");
                for (int j = 0; ps != null && j < ps.length(); j++) {
                    String t = text(ps.optJSONObject(j) == null ? null : ps.optJSONObject(j).optJSONObject("text"));
                    if (!t.isEmpty()) parts.add(t);
                }
            }
        }
        JSONObject img = findObject(r, "contentImage");
        String thumb = img == null ? "" : bestSource(img);
        if (type.contains("PLAYLIST")) {
            it.type = Item.PLAYLIST;
            it.url = "https://www.youtube.com/playlist?list=" + id;
            it.thumb = thumb;
            String badge = findBadge(r);
            it.sub = join("Playlist", badge, parts.isEmpty() ? "" : parts.get(0));
            return it;
        }
        if (type.contains("CHANNEL")) {
            it.type = Item.CHANNEL;
            it.url = "https://www.youtube.com/channel/" + id;
            it.thumb = thumb;
            it.sub = parts.isEmpty() ? "Channel" : parts.get(0);
            return it;
        }
        it.type = Item.VIDEO;
        it.url = "https://www.youtube.com/watch?v=" + id;
        it.thumb = thumb.isEmpty() ? Item.videoThumb(it.url) : thumb;
        if (!parts.isEmpty()) it.channel = parts.get(0);
        String cid = meta == null ? null : findChannelId(meta);
        if (cid != null) it.channelUrl = "https://www.youtube.com/channel/" + cid;
        String badge = findBadge(r);
        it.live = "LIVE".equalsIgnoreCase(badge);
        it.duration = seconds(badge);
        StringBuilder sub = new StringBuilder();
        for (String s : parts) { if (sub.length() > 0) sub.append(" • "); sub.append(s); }
        it.sub = sub.toString();
        return it;
    }

    private static Item tile(JSONObject r) {
        String type = r.optString("contentType", "");
        String id = r.optString("contentId", "");
        JSONObject meta = findObject(r, "tileMetadataRenderer");
        Item it = new Item();
        it.title = meta == null ? "" : text(meta.optJSONObject("title"));
        List<String> parts = new ArrayList<>();
        JSONArray lines = meta == null ? null : meta.optJSONArray("lines");
        for (int i = 0; lines != null && i < lines.length(); i++) {
            JSONObject lr = lines.optJSONObject(i) == null ? null : lines.optJSONObject(i).optJSONObject("lineRenderer");
            JSONArray items = lr == null ? null : lr.optJSONArray("items");
            for (int j = 0; items != null && j < items.length(); j++) {
                JSONObject li = items.optJSONObject(j) == null ? null : items.optJSONObject(j).optJSONObject("lineItemRenderer");
                String t = li == null ? "" : text(li.optJSONObject("text"));
                if (!t.isEmpty() && !t.equals("•")) parts.add(t);
            }
        }
        JSONObject header = findObject(r, "tileHeaderRenderer");
        String thumb = header == null ? "" : bestThumb(header.optJSONObject("thumbnail"));
        JSONObject sel = r.optJSONObject("onSelectCommand");
        if (type.contains("CHANNEL")) {
            String bid = sel == null ? null : findString(sel, "browseId");
            if (bid == null) bid = id;
            if (bid == null || bid.isEmpty()) return null;
            it.type = Item.CHANNEL;
            it.url = "https://www.youtube.com/channel/" + bid;
            it.thumb = thumb;
            it.sub = parts.isEmpty() ? "Channel" : parts.get(0);
            return it;
        }
        if (type.contains("PLAYLIST")) {
            String pid = id;
            if (sel != null && findString(sel, "playlistId") != null) pid = findString(sel, "playlistId");
            if (pid == null || pid.isEmpty()) return null;
            if (pid.startsWith("VL")) pid = pid.substring(2);
            it.type = Item.PLAYLIST;
            it.url = "https://www.youtube.com/playlist?list=" + pid;
            it.thumb = thumb;
            it.sub = join("Playlist", parts.isEmpty() ? "" : parts.get(0));
            return it;
        }
        String vid = id;
        if (sel != null && findString(sel, "videoId") != null) vid = findString(sel, "videoId");
        if (vid == null || vid.isEmpty()) return null;
        it.type = Item.VIDEO;
        it.url = "https://www.youtube.com/watch?v=" + vid;
        it.thumb = thumb.isEmpty() ? Item.videoThumb(it.url) : thumb;
        if (!parts.isEmpty()) it.channel = parts.get(0);
        String time = header == null ? "" : overlayTime(header);
        it.duration = seconds(time);
        it.live = "LIVE".equalsIgnoreCase(time);
        it.isShort = type.contains("SHORT");
        StringBuilder sub = new StringBuilder();
        for (String s : parts) { if (sub.length() > 0) sub.append(" • "); sub.append(s); }
        it.sub = sub.toString();
        return it;
    }

    private static Item channel(JSONObject r) {
        String id = r.optString("channelId", "");
        if (id.isEmpty()) return null;
        Item it = new Item();
        it.type = Item.CHANNEL;
        it.url = "https://www.youtube.com/channel/" + id;
        it.title = text(r.optJSONObject("title"));
        it.channel = it.title;
        it.channelUrl = it.url;
        it.thumb = bestThumb(r.optJSONObject("thumbnail"));
        String subs = text(r.optJSONObject("subscriberCountText"));
        if (subs.isEmpty()) subs = text(r.optJSONObject("videoCountText"));
        it.sub = subs.isEmpty() ? "Channel" : subs;
        return it;
    }

    private static Item playlist(JSONObject r) {
        String id = r.optString("playlistId", "");
        if (id.isEmpty()) return null;
        Item it = new Item();
        it.type = Item.PLAYLIST;
        it.url = "https://www.youtube.com/playlist?list=" + id;
        it.title = text(r.optJSONObject("title"));
        JSONObject th = r.optJSONObject("thumbnail");
        if (th == null) {
            JSONArray ths = r.optJSONArray("thumbnails");
            th = ths == null ? null : ths.optJSONObject(0);
        }
        it.thumb = bestThumb(th);
        String count = text(r.optJSONObject("videoCountShortText"));
        if (count.isEmpty()) count = text(r.optJSONObject("videoCountText"));
        it.sub = join("Playlist", count);
        return it;
    }

    // ------------------------------------------------------------------ small JSON helpers
    static String text(JSONObject t) {
        if (t == null) return "";
        if (t.has("simpleText")) return t.optString("simpleText");
        if (t.has("content")) return t.optString("content");
        JSONArray runs = t.optJSONArray("runs");
        if (runs == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run != null) b.append(run.optString("text"));
        }
        return b.toString();
    }

    static String bestThumb(JSONObject t) {
        if (t == null) return "";
        JSONArray a = t.optJSONArray("thumbnails");
        if (a == null) a = findArray(t, "thumbnails");
        return a == null ? "" : pick(a, "width");
    }

    private static String bestSource(JSONObject o) {
        JSONArray a = findArray(o, "sources");
        return a == null ? "" : pick(a, "width");
    }

    private static String pick(JSONArray a, String widthKey) {
        String best = "";
        int bestW = -1;
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            int w = x.optInt(widthKey, 0);
            boolean better = best.isEmpty() || (w <= 640 && w > bestW) || (bestW > 640 && w < bestW);
            if (better) { best = x.optString("url"); bestW = w; }
        }
        if (best.startsWith("//")) best = "https:" + best;
        return best;
    }

    private static String overlayTime(JSONObject r) {
        JSONObject ts = findObject(r, "thumbnailOverlayTimeStatusRenderer");
        if (ts == null) return "";
        String t = text(ts.optJSONObject("text"));
        if (t.isEmpty() && "LIVE".equals(ts.optString("style"))) return "LIVE";
        return t;
    }

    private static String findBadge(JSONObject r) {
        JSONObject b = findObject(r, "thumbnailBadgeViewModel");
        if (b == null) return "";
        return b.optString("text", "");
    }

    private static String findChannelId(JSONObject o) {
        String s = findString(o, "browseId");
        return s != null && s.startsWith("UC") ? s : null;
    }

    static long seconds(String t) {
        if (t == null || !t.matches("\\d+(:\\d{1,2}){1,2}")) return 0;
        long s = 0;
        for (String part : t.split(":")) s = s * 60 + Long.parseLong(part);
        return s;
    }

    private static String join(String... bits) {
        StringBuilder b = new StringBuilder();
        for (String s : bits) {
            if (s == null || s.isEmpty()) continue;
            if (b.length() > 0) b.append(" • ");
            b.append(s);
        }
        return b.toString();
    }

    static JSONObject findObject(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            if (o.optJSONObject(key) != null) return o.optJSONObject(key);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                JSONObject f = findObject(o.opt(it.next()), key);
                if (f != null) return f;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                JSONObject f = findObject(a.opt(i), key);
                if (f != null) return f;
            }
        }
        return null;
    }

    static JSONArray findArray(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            if (o.optJSONArray(key) != null) return o.optJSONArray(key);
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                JSONArray f = findArray(o.opt(it.next()), key);
                if (f != null) return f;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                JSONArray f = findArray(a.opt(i), key);
                if (f != null) return f;
            }
        }
        return null;
    }

    static String findString(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            Object v = o.opt(key);
            if (v instanceof String) return (String) v;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String f = findString(o.opt(it.next()), key);
                if (f != null) return f;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                String f = findString(a.opt(i), key);
                if (f != null) return f;
            }
        }
        return null;
    }

    static Boolean findBool(Object node, String key) {
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            Object v = o.opt(key);
            if (v instanceof Boolean) return (Boolean) v;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                Boolean f = findBool(o.opt(it.next()), key);
                if (f != null) return f;
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) {
                Boolean f = findBool(a.opt(i), key);
                if (f != null) return f;
            }
        }
        return null;
    }

    private Yti() { }
}
