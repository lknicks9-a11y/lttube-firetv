package com.lttube.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Where each row on the home screen comes from. All methods block. */
public final class Sources {

    private static List<Shelf> one(Shelf s) {
        List<Shelf> l = new ArrayList<>();
        if (s != null && !s.items.isEmpty()) l.add(s);
        return l;
    }

    public static List<Shelf> continueWatching() {
        List<Item> l = new ArrayList<>();
        for (Item i : Store.history()) if (i.position > 0 && l.size() < 30) l.add(i);
        return one(new Shelf("Continue watching", l));
    }

    public static List<Shelf> localHistory() {
        List<Item> h = Store.history();
        return one(new Shelf("History", h.size() > 80 ? h.subList(0, 80) : h));
    }

    public static List<Shelf> recommended() throws Exception {
        if (Store.history().isEmpty()) return Collections.emptyList();
        Feeds.Result r = Feeds.recommended();
        return one(new Shelf("Recommended", r.items));
    }

    public static List<Shelf> kiosk(String id, String title) throws Exception {
        Feeds.Result r = Feeds.kiosk(id);
        return one(Shelf.of(title, r, page -> Feeds.kioskMore(id, page)));
    }

    public static List<Shelf> search(String q, String title) throws Exception {
        Feeds.Result r = Feeds.search(q);
        List<Item> v = new ArrayList<>();
        for (Item i : r.items) if (Item.VIDEO.equals(i.type) && !i.isShort) v.add(i);
        Shelf s = new Shelf(title, v);
        return one(s);
    }

    public static List<Shelf> searchShorts(String q, String title) throws Exception {
        Feeds.Result r = Feeds.search(q);
        List<Item> v = new ArrayList<>();
        for (Item i : r.items) {
            if (!Item.VIDEO.equals(i.type)) continue;
            if (i.isShort || (i.duration > 0 && i.duration <= 75)) { i.isShort = true; v.add(i); }
        }
        return one(new Shelf(title, v));
    }

    /** Shorts that appear in your YouTube home and subscriptions. */
    public static List<Shelf> accountShorts() throws Exception {
        List<Shelf> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String[] f : new String[][]{{Yti.SUBS, "Shorts from your subscriptions"}, {Yti.HOME, "Shorts for you"}}) {
            List<Item> l = new ArrayList<>();
            try {
                for (Shelf sh : Yti.sections(f[0], "")) for (Item i : sh.items) if (i.isShort && seen.add(i.url)) l.add(i);
            } catch (Exception ignored) { }
            if (!l.isEmpty()) out.add(new Shelf(f[1], l));
        }
        return out;
    }

    /**
     * Shorts picked for you, like YouTube's Shorts tab: YouTube's own Shorts feed and the Shorts it recommends on
     * your Home first, mixed with what's popular across topics — with only a few from channels you follow.
     */
    public static List<Item> shortsForYou(boolean signedIn) { return shortsForYou(signedIn, false); }

    /** quick = just enough to start playing now (the rest is added while you watch). */
    public static List<Item> shortsForYou(boolean signedIn, boolean quick) {
        List<Item> mine = new ArrayList<>(), subs = new ArrayList<>(), wide = new ArrayList<>();
        Set<String> seen = java.util.Collections.synchronizedSet(new HashSet<>());
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Future<List<Item>>> acct = new ArrayList<>();
            if (signedIn) {
                for (String feed : new String[]{"FEshorts", Yti.HOME, Yti.SUBS}) acct.add(ex.submit(() -> {
                    List<Item> l = new ArrayList<>();
                    try {
                        for (Shelf sh : Yti.sections(feed, "")) for (Item i : sh.items) {
                            if (!Item.VIDEO.equals(i.type) || i.url == null) continue;
                            boolean sh2 = i.isShort || (i.duration > 0 && i.duration <= 75) || i.url.contains("/shorts/");
                            if (sh2 || ("FEshorts".equals(feed) && i.duration <= 0)) { i.isShort = true; l.add(i); }
                        }
                    } catch (Exception ignored) { }
                    return l;
                }));
            }
            // Plain words, not "#shorts": a lone hashtag search gives YouTube's hashtag page, which comes back empty.
            String[] topics = quick
                    ? new String[]{"shorts trending", "funny shorts", "viral shorts"}
                    : new String[]{"shorts trending", "funny shorts", "viral shorts", "sports shorts", "gaming shorts",
                                   "music shorts", "food shorts", "satisfying shorts", "animals shorts", "comedy shorts"};
            List<java.util.concurrent.Future<List<Item>>> pub = new ArrayList<>();
            for (String q : topics) pub.add(ex.submit(() -> {
                List<Item> l = new ArrayList<>();
                try { for (Shelf sh : searchShorts(q, "Shorts")) l.addAll(sh.items); } catch (Exception ignored) { }
                return l;
            }));
            for (int k = 0; k < acct.size(); k++) {
                List<Item> l = get(acct.get(k));
                for (Item i : l) if (seen.add(i.url)) (k == 2 ? subs : mine).add(i);
            }
            for (java.util.concurrent.Future<List<Item>> f : pub) for (Item i : get(f)) if (seen.add(i.url)) wide.add(i);
        } finally { ex.shutdownNow(); }
        java.util.Collections.shuffle(wide);
        java.util.Collections.shuffle(subs);
        // For you first, then popular ones with one from your subscriptions every few Shorts.
        List<Item> out = new ArrayList<>(mine);
        int si = 0;
        for (int i = 0; i < wide.size(); i++) {
            out.add(wide.get(i));
            if (i % 5 == 4 && si < subs.size()) out.add(subs.get(si++));
        }
        while (si < subs.size() && out.size() < 200) out.add(subs.get(si++));
        return out;
    }

    private static List<Item> get(java.util.concurrent.Future<List<Item>> f) {
        try { return f.get(25, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { return new ArrayList<>(); }
    }

    /** Your playlists (only playlists — not the recently watched videos YouTube puts on the same page). */
    public static List<Shelf> accountPlaylists() throws Exception {
        List<Item> l = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Exception last = null;
        for (String feed : new String[]{Yti.PLAYLISTS, "FEplaylist_aggregation", "FElibrary"}) {
            try {
                for (Shelf sh : Yti.sections(feed, "Playlists")) for (Item i : sh.items)
                    if (Item.PLAYLIST.equals(i.type) && seen.add(i.url)) l.add(i);
            } catch (Exception e) { last = e; }
            if (l.size() >= 4) break;
        }
        if (l.isEmpty() && last != null) throw last;
        return one(new Shelf("Playlists", l));
    }

    /** One of your account lists as a single row (History, Watch later, …). */
    public static List<Shelf> accountRow(String feed, String title) throws Exception {
        List<Shelf> parts = Yti.sections(feed, title);
        if (parts.isEmpty()) return parts;
        Shelf all = new Shelf(title, null);
        Set<String> seen = new HashSet<>();
        for (Shelf p : parts) {
            for (Item i : p.items) if (seen.add(i.url)) all.items.add(i);
            if (all.cont == null && p.cont != null) { all.cont = p.cont; all.contTv = p.contTv; all.only = p.only; }
        }
        if (Yti.CHANNELS.equals(feed)) {
            List<Item> ch = new ArrayList<>();
            for (Item i : all.items) if (Item.CHANNEL.equals(i.type)) ch.add(i);
            all.items.clear();
            all.items.addAll(ch);
            if (!ch.isEmpty()) Store.addSubs(ch);
        }
        return one(all);
    }

    public static List<Shelf> localChannels() {
        return one(new Shelf("Your channels", Store.subs()));
    }

    public static List<Shelf> localSubsFeed() throws Exception {
        if (Store.subs().isEmpty()) return Collections.emptyList();
        List<Item> cached = Store.feed();
        if (cached.isEmpty() || Store.feedAge() > 20 * 60 * 1000) {
            List<Item> fresh = Feeds.subscriptionFeed(null);
            if (!fresh.isEmpty()) { Store.saveFeed(fresh); cached = fresh; }
        }
        List<Item> videos = new ArrayList<>(), shorts = new ArrayList<>();
        for (Item i : cached) (i.isShort ? shorts : videos).add(i);
        List<Shelf> out = new ArrayList<>();
        if (!videos.isEmpty()) out.add(new Shelf("Latest", videos));
        if (!shorts.isEmpty()) out.add(new Shelf("Shorts", shorts));
        return out;
    }

    private Sources() { }
}
