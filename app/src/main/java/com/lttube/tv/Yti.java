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

    private static final Set<String> GUEST_OK = new HashSet<>(java.util.Arrays.asList("next", "search", "browse"));

    /**
     * YouTube's own "Up next" for a video: the same list and order the YouTube app shows
     * (personal when signed in). The first one is what autoplay plays.
     */
    public static List<Item> upNext(String videoId) throws Exception {
        Exception last = null;
        boolean tvFirst = !Account.signedIn() || tvFlavour();
        for (int i = 0; i < 2; i++) {
            boolean tv = i == 0 ? tvFirst : !tvFirst;
            try {
                JSONObject r = call("next", new JSONObject().put("videoId", videoId), tv);
                Page pg = parse(r, null);
                List<Item> out = new ArrayList<>();
                for (Item it : pg.items) {
                    if (!Item.VIDEO.equals(it.type) || it.videoId().equals(videoId)) continue;
                    out.add(it);
                }
                if (!out.isEmpty()) return out;
            } catch (Exception e) { last = e; }
        }
        if (last != null) throw last;
        return new ArrayList<>();
    }

    /**
     * A channel's most viewed videos: the Videos tab with YouTube's "Popular" sort chip.
     * Uses the web flavour (that's where the sort chips are).
     */
    public static List<Item> channelPopular(String channelId) throws Exception {
        String acct = Account.signedInSafe() ? Account.activeKey() : "";
        JSONObject r = callAs(acct, "", "browse", new JSONObject().put("browseId", channelId).put("params", "EgZ2aWRlb3PyBgQKAjoA"), false);
        String token = null;
        List<JSONObject> chips = new ArrayList<>();
        collectWith(r, "chipViewModel", chips);
        for (JSONObject c : chips) {
            JSONObject cv = c.optJSONObject("chipViewModel");
            if (cv != null && "Popular".equalsIgnoreCase(cv.optString("text"))) token = findString(cv, "token");
        }
        if (token == null) {
            List<JSONObject> old = new ArrayList<>();
            collectWith(r, "chipCloudChipRenderer", old);
            for (JSONObject c : old) {
                JSONObject cr = c.optJSONObject("chipCloudChipRenderer");
                if (cr != null && "Popular".equalsIgnoreCase(text(cr.optJSONObject("text")))) token = findString(cr, "token");
            }
        }
        if (token == null) return new ArrayList<>();
        JSONObject more = callAs(acct, "", "browse", new JSONObject().put("continuation", token), false);
        List<Item> out = new ArrayList<>();
        for (Item it : parse(more, Item.VIDEO).items) out.add(it);
        return out;
    }

    /** The next screenful of shelves (YouTube's home keeps going as you scroll down). */
    public static List<Shelf> moreShelves(String continuation, boolean tv) throws Exception {
        JSONObject r = call("browse", new JSONObject().put("continuation", continuation), tv);
        return shelves(r, "More for you", tv, null);
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

    /** The main account and each of its channels, for "Who's watching?". */
    public static volatile String lastAccountsReport = "";

    public static List<Account.Profile> accountsList(String acct) throws Exception {
        // Every channel on this Google account: YouTube lists them differently in the TV app, the web's account
        // menu and the web's channel switcher, so all three are asked and put together (no duplicates).
        List<Account.Profile> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Exception last = null;
        Object[][] asks = {
                {"account/accounts_list", true, new JSONObject().put("accountReadMask", new JSONObject().put("returnOwner", true)
                        .put("returnBrandAccounts", true).put("returnPersonalAccount", true))},
                {"account/accounts_list", false, new JSONObject().put("requestType", "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER")
                        .put("callCircumstance", "SWITCHING_USERS_FULL")},
                {"account/accounts_list", true, new JSONObject().put("requestType", "ACCOUNTS_LIST_REQUEST_TYPE_CHANNEL_SWITCHER")},
                {"account/account_menu", false, new JSONObject()},
        };
        StringBuilder rep = new StringBuilder();
        int n0 = 0;
        for (Object[] ask : asks) {
            if (rep.length() > 0) rep.append(" · ");
            try {
                JSONObject r = callAs(acct, "", (String) ask[0], (JSONObject) ask[2], (Boolean) ask[1]);
                n0 = out.size();
                List<JSONObject> items = new ArrayList<>();
                collectWith(r, "accountName", items);
                for (JSONObject it : items) {
                    Account.Profile pr = new Account.Profile();
                    pr.acct = acct;
                    pr.name = text(it.optJSONObject("accountName"));
                    pr.photo = bestThumb(it.optJSONObject("accountPhoto"));
                    pr.handle = text(it.optJSONObject("channelHandle"));
                    if (pr.handle.isEmpty()) pr.handle = text(it.optJSONObject("accountByline"));
                    String pid = findString(it, "pageId");
                    if (pid == null) pid = findString(it, "obfuscatedPageId");
                    if (pid == null) {
                        // Some lists give "datasyncId" = "<pageId>||<accountId>" instead.
                        String ds = findString(it, "datasyncId");
                        if (ds != null && ds.contains("||") && !ds.startsWith("||")) pid = ds.substring(0, ds.indexOf("||"));
                    }
                    pr.pageId = pid == null ? "" : pid;
                    // Main account = no page id. Channels without one are told apart by name (not all merged into one).
                    boolean isMain = pr.pageId.isEmpty() && (it.optBoolean("isSelected", false) || out.isEmpty());
                    String key = !pr.pageId.isEmpty() ? pr.pageId : isMain ? "main" : "name:" + pr.name;
                    if (pr.name.isEmpty() || !seen.add(key) || !seen.add("n:" + pr.name + "|" + pr.pageId)) continue;
                    if (pr.pageId.isEmpty()) out.add(0, pr); else out.add(pr);
                }
                rep.append("+").append(out.size() - n0);
            } catch (Exception e) { last = e; rep.append("✗"); }
        }
        lastAccountsReport = out.size() + " channels found (" + rep + ")";
        if (out.isEmpty() && last != null) throw last;
        return out;
    }

    /**
     * Posts a comment on a video as your signed-in channel. YouTube hands out a "create comment" ticket inside the
     * video's comments section; this finds it (TV app first, then web) and posts the text with it.
     */
    public static void postComment(String videoId, String text) throws Exception {
        if (!Account.signedIn()) throw new Exception("Sign in to comment");
        Exception last = null;
        for (boolean tv : new boolean[]{false, true}) {
            try {
                JSONObject r = call("next", new JSONObject().put("videoId", videoId), tv);
                String params = findString(r, "createCommentParams");
                if (params == null) {
                    // The comments section comes in a second request.
                    String token = null;
                    List<JSONObject> secs = new ArrayList<>();
                    collectWith(r, "sectionIdentifier", secs);
                    for (JSONObject o : secs) if ("comment-item-section".equals(o.optString("sectionIdentifier"))) token = findString(o, "token");
                    if (token == null) {
                        List<JSONObject> panels = new ArrayList<>();
                        collectWith(r, "targetId", panels);
                        for (JSONObject o : panels) if (o.optString("targetId").contains("comments")) { token = findString(o, "token"); if (token != null) break; }
                    }
                    if (token != null) params = findString(call("next", new JSONObject().put("continuation", token), tv), "createCommentParams");
                }
                if (params == null) throw new Exception("Comments are off for this video");
                JSONObject res = call("comment/create_comment", new JSONObject().put("commentText", text).put("createCommentParams", params), tv);
                if (res.toString().contains("\"status\":\"STATUS_FAILED\"")) throw new Exception("YouTube didn't accept the comment");
                return;
            } catch (Exception e) { last = e; }
        }
        throw last == null ? new Exception("Couldn't post") : last;
    }

    /** Every object that has the given key, anywhere in a response. */
    private static void collectWith(Object node, String key, List<JSONObject> out) {
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectWith(a.opt(i), key, out);
        } else if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            if (o.has(key)) { out.add(o); return; }
            Iterator<String> it = o.keys();
            while (it.hasNext()) collectWith(o.opt(it.next()), key, out);
        }
    }

    /** Nothing cached per-account right now; kept for profile switches. */
    public static void resetCaches() { }

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
        return callAs(Account.activeKey(), Account.pageId(), endpoint, body, tv);
    }

    /** Calls YouTube as a Google account, optionally as one of its channels (pageId). */
    static JSONObject callAs(String acct, String pageId, String endpoint, JSONObject body, boolean tv) throws Exception {
        String token = acct == null || acct.isEmpty() ? null : Account.tokenFor(acct);
        if (token == null && !GUEST_OK.contains(endpoint)) throw new Exception("Not signed in");
        Locale l = Locale.getDefault();
        JSONObject client = new JSONObject()
                .put("clientName", tv ? "TVHTML5" : "WEB")
                .put("clientVersion", tv ? TV_VERSION : web())
                .put("hl", l.getLanguage())
                .put("gl", l.getCountry().isEmpty() ? "US" : l.getCountry());
        if (tv) client.put("platform", "TV").put("clientFormFactor", "UNKNOWN_FORM_FACTOR");
        JSONObject user = new JSONObject();
        if (pageId != null && !pageId.isEmpty()) user.put("onBehalfOfUser", pageId);
        body.put("context", new JSONObject().put("client", client).put("user", user));

        Request r = new Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/" + endpoint + "?prettyPrint=false")
                .header("User-Agent", tv ? TV_UA : NpDownloader.USER_AGENT)
                .header("X-Youtube-Client-Name", tv ? "7" : "1")
                .header("X-Youtube-Client-Version", tv ? TV_VERSION : web())
                .header("Origin", "https://www.youtube.com")
                .header("Referer", tv ? "https://www.youtube.com/tv" : "https://www.youtube.com/")
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        if (token != null) r = r.newBuilder().header("Authorization", "Bearer " + token).build();
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

    /** A feed split into YouTube's own shelves ("Recommended music videos", …). */
    public static List<Shelf> sections(String feed, String looseTitle) throws Exception {
        boolean tvFirst = p.getBoolean("tv", true);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean tv = attempt == 0 ? tvFirst : !tvFirst;
            try {
                JSONObject r = call("browse", new JSONObject().put("browseId", browseId(feed, tv)), tv);
                List<Shelf> out = shelves(r, looseTitle, tv, feed.equals(CHANNELS) ? Item.CHANNEL : null);
                int n = 0;
                for (Shelf sh : out) n += sh.items.size();
                if (n > 0 || attempt == 1) {
                    if (!feed.equals(CHANNELS) && n > 0) p.edit().putBoolean("tv", tv).apply();
                    return out;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return new ArrayList<>();
    }

    /** Ads and promotions YouTube mixes into feeds; never shown. */
    static final Set<String> AD_KEYS = new HashSet<>(java.util.Arrays.asList(
            "adSlotRenderer", "inFeedAdLayoutRenderer", "promotedVideoRenderer", "compactPromotedVideoRenderer",
            "promotedSparklesWebRenderer", "brandVideoShelfRenderer", "statementBannerRenderer", "adSlotMetadata",
            "displayAdRenderer", "searchPyvRenderer", "bannerPromoRenderer", "mealbarPromoRenderer",
            "primetimePromoRenderer", "movingThumbnailRenderer", "feedNudgeRenderer"));

    private static final Set<String> SHELF_KEYS = new HashSet<>(java.util.Arrays.asList(
            "shelfRenderer", "richShelfRenderer", "reelShelfRenderer", "gridShelfViewModel"));

    static List<Shelf> shelves(JSONObject root, String looseTitle, boolean tv, String only) {
        List<Shelf> out = new ArrayList<>();
        Page loose = new Page();
        Set<String> seen = new HashSet<>();
        collectShelves(root, out, loose, seen, tv, only);
        if (!loose.items.isEmpty()) {
            Shelf sh = new Shelf(looseTitle, loose.items);
            sh.cont = loose.continuation;
            sh.contTv = tv;
            sh.only = only;
            out.add(0, sh);
        } else if (loose.continuation != null && !out.isEmpty()) {
            out.get(out.size() - 1).pageCont = loose.continuation;
            out.get(out.size() - 1).pageTv = tv;
        }
        List<Shelf> keep = new ArrayList<>();
        for (Shelf sh : out) if (!sh.items.isEmpty()) keep.add(sh);
        return keep;
    }

    private static void collectShelves(Object node, List<Shelf> out, Page loose, Set<String> seen, boolean tv, String only) {
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectShelves(a.opt(i), out, loose, seen, tv, only);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = o.opt(k);
            if (AD_KEYS.contains(k)) continue;
            if (v instanceof JSONObject && SHELF_KEYS.contains(k)) {
                JSONObject r = (JSONObject) v;
                String title = shelfTitle(r);
                Page pg = new Page();
                walk(r, pg, seen, only);
                if ("reelShelfRenderer".equals(k)) for (Item it : pg.items) it.isShort = it.isShort || Item.VIDEO.equals(it.type);
                Shelf sh = new Shelf(title.isEmpty() ? "More" : title, pg.items);
                JSONObject sub = r.optJSONObject("subtitle");
                if (sub == null) {
                    JSONObject hr2 = r.optJSONObject("headerRenderer");
                    if (hr2 != null) sub = findObject(hr2, "subtitle");
                }
                sh.subtitle = text(sub);
                sh.cont = pg.continuation;
                sh.contTv = tv;
                sh.only = only;
                out.add(sh);
                continue;
            }
            if (v instanceof JSONObject && isItemKey(k)) {
                JSONObject wrap = new JSONObject();
                try { wrap.put(k, v); } catch (Exception ignored) { }
                walk(wrap, loose, seen, only);
                continue;
            }
            if (v instanceof JSONObject && ("continuationItemRenderer".equals(k) || "nextContinuationData".equals(k))) {
                JSONObject wrap = new JSONObject();
                try { wrap.put(k, v); } catch (Exception ignored) { }
                walk(wrap, loose, seen, only);
                continue;
            }
            collectShelves(v, out, loose, seen, tv, only);
        }
    }

    /** A shelf's title from its header (TV: shelfHeaderRenderer / avatarLockupRenderer, web: title). */
    static String shelfTitle(JSONObject r) {
        String t = text(r.optJSONObject("title"));
        JSONObject hr = r.optJSONObject("headerRenderer");
        if (hr == null) hr = r.optJSONObject("header");
        if (t.isEmpty() && hr != null) {
            JSONObject sh = findObject(hr, "shelfHeaderRenderer");
            JSONObject al = findObject(hr, "avatarLockupRenderer");
            JSONObject src = sh != null ? sh : al != null ? al : hr;
            t = text(src.optJSONObject("title"));
            if (t.isEmpty()) t = text(findObject(src, "title"));
            if (t.isEmpty()) {
                JSONObject tvm = findObject(hr, "dynamicTextViewModel");
                if (tvm != null) t = text(tvm.optJSONObject("text"));
            }
        }
        t = t.trim();
        // ignore counts / times / accessibility numbers that aren't real titles
        if (t.matches("[\\d\\s.,:+KMB%•-]*") || t.matches("(?i)\\d+\\s*(videos?|views?|items?)")) t = "";
        return t;
    }

    private static boolean isItemKey(String k) {
        switch (k) {
            case "videoRenderer": case "gridVideoRenderer": case "compactVideoRenderer": case "playlistVideoRenderer":
            case "playlistPanelVideoRenderer": case "videoWithContextRenderer": case "reelItemRenderer":
            case "shortsLockupViewModel": case "lockupViewModel": case "tileRenderer": case "channelRenderer":
            case "gridChannelRenderer": case "gridPlaylistRenderer": case "playlistRenderer":
                return true;
            default:
                return false;
        }
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
            if (AD_KEYS.contains(k)) continue;
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
        // TV-channel listings ("Pistons vs. Spurs, 4:00 PM") and other non-videos aren't playable
        if (id.length() != 11 || (findObject(r, "watchEndpoint") == null && findObject(r, "reelWatchEndpoint") == null)) return null;
        it.type = Item.VIDEO;
        it.url = "https://www.youtube.com/watch?v=" + id;
        it.thumb = thumb.isEmpty() ? Item.videoThumb(it.url) : thumb;
        if (!parts.isEmpty()) it.channel = parts.get(0);
        if (findObject(r, "reelWatchEndpoint") != null) it.isShort = true;
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
        // The channel's link: TV tiles carry it in their menu ("Go to channel").
        JSONObject menu = r.optJSONObject("menu");
        String tcid = menu == null ? null : findChannelId(menu);
        if (tcid == null) { JSONObject lp = r.optJSONObject("onLongPressCommand"); if (lp != null) tcid = findChannelId(lp); }
        if (tcid != null) it.channelUrl = "https://www.youtube.com/channel/" + tcid;
        String time = header == null ? "" : overlayTime(header);
        it.duration = seconds(time);
        it.live = "LIVE".equalsIgnoreCase(time);
        it.isShort = type.contains("SHORT");
        StringBuilder sub = new StringBuilder();
        for (String s : parts) { if (sub.length() > 0) sub.append(" • "); sub.append(s); }
        it.sub = sub.toString();
        return it;
    }

    /**
     * Every channel you're subscribed to. YouTube lists them in different places depending on the app: the TV app's
     * Subscriptions page (the channel list down the left), the web's "All subscriptions" page (in pages), and the
     * web side menu. All of them are read and put together, so none are missing.
     */
    public static List<Item> subscribedChannels() {
        java.util.LinkedHashMap<String, Item> out = new java.util.LinkedHashMap<>();
        StringBuilder report = new StringBuilder();
        // 1) YouTube's account API: your subscriptions, every page (needs the YouTube permission you signed in with).
        try {
            int before = out.size();
            dataApiSubscriptions(out);
            report.append("api ").append(out.size() - before);
        } catch (Exception e) { report.append("api ✗ ").append(shortErr(e)); }
        // 2) The web's All subscriptions page, every page.
        try {
            int before = out.size();
            JSONObject r = call("browse", new JSONObject().put("browseId", "FEchannels"), false);
            String last = "";
            for (int n = 0; n < 40 && r != null; n++) {
                collectChannels(r, out);
                String cont = findString(r, "token");
                if (cont == null) cont = findString(r, "continuation");
                if (cont == null || cont.equals(last)) break;
                last = cont;
                r = call("browse", new JSONObject().put("continuation", cont), false);
            }
            report.append(" · web ").append(out.size() - before);
        } catch (Exception e) { report.append(" · web ✗ ").append(shortErr(e)); }
        // 3) The TV app's Subscriptions page: its channel list down the left (A–Z), read broadly — any entry that
        //    opens a channel and has a name — and every channel whose videos are in it.
        try {
            int before = out.size();
            JSONObject r = call("browse", new JSONObject().put("browseId", "FEsubscriptions"), true);
            collectChannels(r, out);
            collectAnyChannels(r, out, 0);
            // The list can come in pages too.
            String last = "";
            for (int n = 0; n < 20; n++) {
                String cont = navContinuation(r);
                if (cont == null || cont.equals(last)) break;
                last = cont;
                r = call("browse", new JSONObject().put("continuation", cont), true);
                collectChannels(r, out);
                collectAnyChannels(r, out, 0);
            }
            for (String extra : new String[]{"FEchannels", "FEguide"}) {
                try {
                    JSONObject x = call("browse", new JSONObject().put("browseId", extra), true);
                    collectChannels(x, out);
                    collectAnyChannels(x, out, 0);
                } catch (Exception ignored) { }
            }
            try { JSONObject g = call("guide", new JSONObject(), true); collectChannels(g, out); collectAnyChannels(g, out, 0); } catch (Exception ignored) { }
            int got = out.size() - before;
            report.append(" · tv ").append(got);
            if (got < 5) report.append(" [").append(rendererKeys(r)).append("]");
        } catch (Exception e) { report.append(" · tv ✗ ").append(shortErr(e)); }
        lastSubsReport = report.toString();
        return new ArrayList<>(out.values());
    }

    private static String shortErr(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 40 ? m.substring(0, 40) + "…" : m;
    }

    /** How the last channel lookup went (shown small on the Subscriptions page). */
    public static volatile String lastSubsReport = "";

    private static final Set<String> NOT_NAMES = new HashSet<>(java.util.Arrays.asList(
            "go to channel", "visit channel", "view channel", "subscribed", "subscribe", "all", "channel", "more", "see all"));

    private static boolean genericName(String n) {
        String l = n.trim().toLowerCase();
        return NOT_NAMES.contains(l) || l.startsWith("go to") || l.startsWith("visit ") || l.startsWith("view ") || l.startsWith("{") || l.startsWith("[");
    }

    /** Every entry that opens a channel by itself and carries a name (TV lists, owner names on videos, menus). */
    private static void collectAnyChannels(Object node, java.util.Map<String, Item> out, int depth) {
        if (depth > 60) return;
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectAnyChannels(a.opt(i), out, depth + 1);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        String id = null;
        for (String k : new String[]{"endpoint", "navigationEndpoint", "onSelectCommand", "command", "onTap", "onSelect", "onClickCommand"}) {
            Object e = o.opt(k);
            if (e != null) { id = ucIn(e, 0); if (id != null) break; }
        }
        if (id != null) {
            String name = text(o.optJSONObject("title"));
            if (name.isEmpty() && o.opt("title") instanceof String) name = o.optString("title", "");
            if (name.isEmpty() && o.opt("text") instanceof String) name = o.optString("text", "");
            if (name.isEmpty()) name = text(o.optJSONObject("text"));
            if (name.isEmpty()) name = text(o.optJSONObject("formattedTitle"));
            if (name.isEmpty()) { JSONObject h = o.optJSONObject("header"); if (h != null) { String t = findString(h, "simpleText"); if (t != null) name = t; } }
            name = name.trim();
            String url = "https://www.youtube.com/channel/" + id;
            if (!name.isEmpty() && name.length() < 80 && !genericName(name)) {
                Item have = out.get(url);
                String thumb = bestThumb(o.optJSONObject("thumbnail"));
                if (thumb == null || thumb.isEmpty()) { JSONObject av = o.optJSONObject("avatar"); if (av != null) thumb = bestThumb(av.optJSONObject("thumbnail") != null ? av.optJSONObject("thumbnail") : av); }
                if (thumb != null && thumb.startsWith("//")) thumb = "https:" + thumb;
                if (have == null) {
                    Item it = new Item();
                    it.type = Item.CHANNEL;
                    it.url = url;
                    it.title = name;
                    it.channel = name;
                    it.channelUrl = url;
                    it.thumb = thumb == null ? "" : thumb;
                    it.sub = "Channel";
                    out.put(url, it);
                } else if ((have.thumb == null || have.thumb.isEmpty()) && thumb != null) have.thumb = thumb;
            }
        }
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) collectAnyChannels(o.opt(keys.next()), out, depth + 1);
    }

    /** A UC… channel id that this endpoint / command opens (looked for a few levels in, not in page content). */
    private static String ucIn(Object e, int depth) {
        if (depth > 4) return null;
        if (e instanceof JSONObject) {
            JSONObject o = (JSONObject) e;
            JSONObject be = o.optJSONObject("browseEndpoint");
            if (be != null && be.optString("browseId", "").startsWith("UC")) return be.optString("browseId");
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.equals("content") || k.equals("contents") || k.equals("items")) continue;
                String f = ucIn(o.opt(k), depth + 1);
                if (f != null) return f;
            }
        } else if (e instanceof JSONArray) {
            JSONArray a = (JSONArray) e;
            for (int i = 0; i < a.length() && i < 6; i++) { String f = ucIn(a.opt(i), depth + 1); if (f != null) return f; }
        }
        return null;
    }

    /** A continuation for the TV channel list (not the video feed's). */
    private static String navContinuation(JSONObject r) {
        JSONObject nav = findObject(r, "tvSecondaryNavRenderer");
        if (nav == null) return null;
        String t = findString(nav, "continuation");
        return t == null ? findString(nav, "token") : t;
    }

    /** The kinds of entries YouTube sent (for the report when no channels were found). */
    private static String rendererKeys(Object node) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        gatherKeys(node, keys, 0);
        StringBuilder b = new StringBuilder();
        for (String k : keys) { if (b.length() > 0) b.append(","); b.append(k.replace("Renderer", "R").replace("ViewModel", "VM")); if (b.length() > 160) break; }
        return b.toString();
    }

    private static void gatherKeys(Object node, java.util.Set<String> keys, int depth) {
        if (depth > 40 || keys.size() > 40) return;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.endsWith("Renderer") || k.endsWith("ViewModel")) keys.add(k);
                gatherKeys(o.opt(k), keys, depth + 1);
            }
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) gatherKeys(a.opt(i), keys, depth + 1);
        }
    }

    /** YouTube Data API: subscriptions?mine=true, all pages (50 a page). */
    private static void dataApiSubscriptions(java.util.Map<String, Item> out) throws Exception {
        String token = Account.tokenFor(Account.activeKey());
        if (token == null) throw new Exception("Not signed in");
        String page = "";
        for (int n = 0; n < 40; n++) {
            String url = "https://www.googleapis.com/youtube/v3/subscriptions?part=snippet&mine=true&maxResults=50&order=alphabetical"
                    + (page.isEmpty() ? "" : "&pageToken=" + java.net.URLEncoder.encode(page, "UTF-8"));
            Request r = new Request.Builder().url(url).header("Authorization", "Bearer " + token).build();
            JSONObject j;
            try (Response res = NpDownloader.get().client.newCall(r).execute()) {
                String text = res.body() == null ? "" : res.body().string();
                if (!res.isSuccessful()) throw new Exception("HTTP " + res.code());
                j = new JSONObject(text);
            }
            JSONArray items = j.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) {
                JSONObject sn = items.getJSONObject(i).optJSONObject("snippet");
                if (sn == null) continue;
                JSONObject rid = sn.optJSONObject("resourceId");
                String id = rid == null ? "" : rid.optString("channelId", "");
                if (!id.startsWith("UC")) continue;
                Item it = new Item();
                it.type = Item.CHANNEL;
                it.url = "https://www.youtube.com/channel/" + id;
                it.title = sn.optString("title", "");
                it.channel = it.title;
                it.channelUrl = it.url;
                JSONObject th = sn.optJSONObject("thumbnails");
                JSONObject best = th == null ? null : (th.has("high") ? th.optJSONObject("high") : th.has("medium") ? th.optJSONObject("medium") : th.optJSONObject("default"));
                it.thumb = best == null ? "" : best.optString("url", "");
                it.sub = "Channel";
                if (!it.title.isEmpty() && !out.containsKey(it.url)) out.put(it.url, it);
            }
            page = j.optString("nextPageToken", "");
            if (page.isEmpty()) break;
        }
    }

    private static final Set<String> CHANNEL_HOLDERS = new HashSet<>(java.util.Arrays.asList(
            "channelRenderer", "gridChannelRenderer", "tabRenderer", "guideEntryRenderer",
            "compactChannelRenderer", "channelListItemRenderer"));

    private static void collectChannels(Object node, java.util.Map<String, Item> out) {
        if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int i = 0; i < a.length(); i++) collectChannels(a.opt(i), out);
            return;
        }
        if (!(node instanceof JSONObject)) return;
        JSONObject o = (JSONObject) node;
        java.util.Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = o.opt(k);
            if (v instanceof JSONObject) {
                JSONObject r = (JSONObject) v;
                Item it = CHANNEL_HOLDERS.contains(k) ? channelFrom(r) : null;
                // Any entry that itself links straight to a channel (TV channel lists, side menu entries).
                if (it == null && ownChannelLink(r) != null) it = channelFrom(r);
                if (it != null && !out.containsKey(it.url)) out.put(it.url, it);
            }
            collectChannels(v, out);
        }
    }

    /** The channel an entry itself opens (its own endpoint / command), not one buried inside it. */
    private static String ownChannelLink(JSONObject r) {
        for (String k : new String[]{"endpoint", "navigationEndpoint", "onSelectCommand", "command", "onTap"}) {
            JSONObject e = r.optJSONObject(k);
            if (e == null) continue;
            JSONObject be = e.optJSONObject("browseEndpoint");
            if (be == null) { JSONObject inner = e.optJSONObject("innertubeCommand"); if (inner != null) be = inner.optJSONObject("browseEndpoint"); }
            if (be != null && be.optString("browseId", "").startsWith("UC")) return be.optString("browseId");
        }
        return null;
    }

    /** A channel from any of YouTube's channel entries (needs a UC… id that it links to, and a name). */
    private static Item channelFrom(JSONObject r) {
        String id = r.optString("channelId", "");
        if (!id.startsWith("UC")) { String own = ownChannelLink(r); if (own != null) id = own; }
        if (!id.startsWith("UC")) {
            String b = findString(r, "browseId");
            id = b != null && b.startsWith("UC") ? b : "";
        }
        if (id.isEmpty()) return null;
        String name = text(r.optJSONObject("title"));
        if (name.isEmpty()) name = text(r.optJSONObject("formattedTitle"));
        if (name.isEmpty() && r.opt("title") instanceof String) name = r.optString("title", "");
        if (name.isEmpty()) {
            JSONObject md = r.optJSONObject("metadata");
            if (md != null) name = findString(md, "simpleText") == null ? "" : findString(md, "simpleText");
        }
        if (name.isEmpty() || genericName(name)) return null;
        Item it = new Item();
        it.type = Item.CHANNEL;
        it.url = "https://www.youtube.com/channel/" + id;
        it.title = name;
        it.channel = name;
        it.channelUrl = it.url;
        JSONObject th = r.optJSONObject("thumbnail");
        if (th == null) { JSONObject h = r.optJSONObject("header"); if (h != null) th = h.optJSONObject("thumbnail"); }
        String t = bestThumb(th);
        if ((t == null || t.isEmpty())) { String u = findString(r, "url"); if (u != null && (u.contains("ggpht") || u.contains("ytimg"))) t = u; }
        if (t != null && t.startsWith("//")) t = "https:" + t;
        it.thumb = t == null ? "" : t;
        it.sub = "Channel";
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
