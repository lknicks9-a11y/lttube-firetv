package com.lttube.tv;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.feed.FeedInfo;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Loads lists of videos from YouTube. All methods block: call them through Bg.run. */
public final class Feeds {

    public static StreamingService yt() { return ServiceList.YouTube; }

    /** A page of items plus what's needed to get the next page. */
    public static class Result {
        public final List<Item> items = new ArrayList<>();
        public Page next;
        public String title = "";
        public Object extra; // ChannelInfo for channels
    }

    public interface More { Result load(Page page) throws Exception; }

    static void addAll(Result r, List<? extends InfoItem> in, boolean dropShorts) {
        Set<String> seen = new HashSet<>();
        for (Item i : r.items) seen.add(i.url);
        for (InfoItem ii : in) {
            Item it = Item.from(ii);
            if (Item.ACTION.equals(it.type) || it.url == null || seen.contains(it.url)) continue;
            if (dropShorts && it.isShort) continue;
            seen.add(it.url);
            r.items.add(it);
        }
    }

    // ---------- kiosks (trending music, gaming, live, …) ----------
    public static String kioskUrl(String id) throws Exception {
        return yt().getKioskList().getListLinkHandlerFactoryByType(id).fromId(id).getUrl();
    }

    public static Result kiosk(String id) throws Exception {
        String url = kioskUrl(id);
        KioskInfo k = KioskInfo.getInfo(yt(), url);
        Result r = new Result();
        r.title = k.getName();
        addAll(r, k.getRelatedItems(), false);
        r.next = k.getNextPage();
        return r;
    }

    public static Result kioskMore(String id, Page page) throws Exception {
        ListExtractor.InfoItemsPage<?> p = KioskInfo.getMoreItems(yt(), kioskUrl(id), page);
        Result r = new Result();
        addAll(r, castItems(p.getItems()), false);
        r.next = p.getNextPage();
        return r;
    }

    // ---------- search ----------
    public static Result search(String q) throws Exception {
        SearchInfo s = SearchInfo.getInfo(yt(), yt().getSearchQHFactory().fromQuery(q));
        Result r = new Result();
        addAll(r, s.getRelatedItems(), false);
        r.next = s.getNextPage();
        return r;
    }

    public static Result searchMore(String q, Page page) throws Exception {
        ListExtractor.InfoItemsPage<InfoItem> p =
                SearchInfo.getMoreItems(yt(), yt().getSearchQHFactory().fromQuery(q), page);
        Result r = new Result();
        addAll(r, p.getItems(), false);
        r.next = p.getNextPage();
        return r;
    }

    public static List<String> suggestions(String q) {
        try { return yt().getSuggestionExtractor().suggestionList(q); }
        catch (Exception e) { return Collections.emptyList(); }
    }

    // ---------- channels ----------
    public static ListLinkHandler channelTab(ChannelInfo c, String tab) {
        for (ListLinkHandler h : c.getTabs()) if (h.getContentFilters().contains(tab)) return h;
        return null;
    }

    public static Result channel(String url, String tab) throws Exception {
        ChannelInfo c = ChannelInfo.getInfo(yt(), url);
        Result r = new Result();
        r.title = c.getName();
        r.extra = c;
        ListLinkHandler h = channelTab(c, tab);
        if (h != null) {
            ChannelTabInfo t = ChannelTabInfo.getInfo(yt(), h);
            addAll(r, t.getRelatedItems(), false);
            r.next = t.getNextPage();
        }
        return r;
    }

    public static Result channelMore(ChannelInfo c, String tab, Page page) throws Exception {
        ListLinkHandler h = channelTab(c, tab);
        Result r = new Result();
        if (h == null) return r;
        ListExtractor.InfoItemsPage<InfoItem> p = ChannelTabInfo.getMoreItems(yt(), h, page);
        addAll(r, p.getItems(), false);
        r.next = p.getNextPage();
        return r;
    }

    // ---------- playlists ----------
    public static Result playlist(String url) throws Exception {
        PlaylistInfo p = PlaylistInfo.getInfo(yt(), url);
        Result r = new Result();
        r.title = p.getName();
        addAll(r, p.getRelatedItems(), false);
        r.next = p.getNextPage();
        return r;
    }

    public static Result playlistMore(String url, Page page) throws Exception {
        ListExtractor.InfoItemsPage<?> p = PlaylistInfo.getMoreItems(yt(), url, page);
        Result r = new Result();
        addAll(r, castItems(p.getItems()), false);
        r.next = p.getNextPage();
        return r;
    }

    // ---------- "recommended": videos related to what you watched last ----------
    public static Result recommended() throws Exception {
        Result r = new Result();
        List<Item> hist = Store.history();
        int used = 0;
        for (Item h : hist) {
            if (used >= 2) break;
            try {
                StreamInfo s = StreamInfo.getInfo(yt(), h.url);
                used++;
                List<InfoItem> rel = new ArrayList<>(s.getRelatedItems());
                addAll(r, rel, true);
            } catch (Exception ignored) { }
        }
        // don't recommend things already watched
        Set<String> watched = new HashSet<>();
        for (Item h : hist) watched.add(h.videoId());
        List<Item> keep = new ArrayList<>();
        for (Item i : r.items) if (Item.VIDEO.equals(i.type) && !watched.contains(i.videoId())) keep.add(i);
        r.items.clear();
        r.items.addAll(keep);
        return r;
    }

    // ---------- subscriptions feed ----------
    public interface Progress { void update(int done, int total); }

    /** Newest videos from every subscribed channel, newest first. */
    public static List<Item> subscriptionFeed(Progress progress) throws Exception {
        final List<Item> subs = Store.subs();
        final List<Item> all = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger done = new AtomicInteger();
        final long cutoff = System.currentTimeMillis() - 45L * 24 * 3600 * 1000;
        ExecutorService pool = Executors.newFixedThreadPool(12);
        for (final Item ch : subs) {
            pool.execute(() -> {
                try {
                    FeedInfo f = FeedInfo.getInfo(yt(), ch.url);
                    for (InfoItem ii : f.getRelatedItems()) {
                        Item it = Item.from(ii);
                        if (!Item.VIDEO.equals(it.type)) continue;
                        if (it.uploaded != 0 && it.uploaded < cutoff) continue;
                        if (it.channel.isEmpty()) it.channel = ch.title;
                        all.add(it);
                    }
                } catch (Exception ignored) { }
                int d = done.incrementAndGet();
                if (progress != null && (d % 20 == 0 || d == subs.size())) progress.update(d, subs.size());
            });
        }
        pool.shutdown();
        pool.awaitTermination(4, TimeUnit.MINUTES);
        List<Item> out = new ArrayList<>(all);
        Collections.sort(out, (a, b) -> Long.compare(b.uploaded, a.uploaded));
        Set<String> seen = new HashSet<>();
        List<Item> dedup = new ArrayList<>();
        for (Item i : out) if (seen.add(i.videoId())) dedup.add(i);
        if (dedup.size() > 400) return new ArrayList<>(dedup.subList(0, 400));
        return dedup;
    }

    @SuppressWarnings("unchecked")
    private static List<InfoItem> castItems(List<?> l) { return (List<InfoItem>) l; }

    private Feeds() { }
}
