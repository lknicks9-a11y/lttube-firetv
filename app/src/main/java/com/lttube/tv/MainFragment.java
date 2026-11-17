package com.lttube.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Row;

import org.schabi.newpipe.extractor.Page;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The home screen: rows of videos with a menu of sections on the left. */
public class MainFragment extends BrowseSupportFragment {

    private static final long FEED_MAX_AGE = 20 * 60 * 1000;

    private ArrayObjectAdapter rows;
    private final CardPresenter cards = new CardPresenter();
    private final Map<String, RowState> state = new HashMap<>();
    private final String[] order = {"continue", "subs", "recommended", "music", "gaming", "live",
            "podcasts", "movies", "channels", "history", "settings"};
    private boolean feedLoading;

    /** One row on the home screen. */
    private class RowState {
        final String key;
        String title;
        final ArrayObjectAdapter items = new ArrayObjectAdapter(cards);
        ListRow row;
        Page next;
        Feeds.More more;
        boolean loading;

        RowState(String key, String title) {
            this.key = key;
            this.title = title;
            row = new ListRow(new HeaderItem(title), items);
        }

        void setTitle(String t) {
            title = t;
            int idx = rows.indexOf(row);
            row = new ListRow(new HeaderItem(t), items);
            if (idx >= 0) rows.replace(idx, row);
        }

        void set(List<Item> list) {
            items.clear();
            items.addAll(0, list);
            show(!list.isEmpty());
        }

        void show(boolean visible) {
            int idx = rows.indexOf(row);
            if (visible && idx < 0) {
                // insert at its place in the fixed order
                int pos = 0;
                for (String k : order) {
                    if (k.equals(key)) break;
                    RowState other = state.get(k);
                    if (other != null && rows.indexOf(other.row) >= 0) pos++;
                }
                rows.add(Math.min(pos, rows.size()), row);
            } else if (!visible && idx >= 0) {
                rows.removeItems(idx, 1);
            }
        }

        List<Item> list() {
            List<Item> l = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) l.add((Item) items.get(i));
            return l;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("LTTube");
        Drawable badge = ContextCompat.getDrawable(requireContext(), R.drawable.banner);
        if (badge != null) setBadgeDrawable(badge);
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setBrandColor(0xFF0F0F0F);
        setSearchAffordanceColor(0xFFFF0033);

        rows = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(rows);

        add("continue", "Continue watching");
        add("subs", "Subscriptions");
        add("recommended", "Recommended");
        add("music", "Music");
        add("gaming", "Gaming");
        add("live", "Live");
        add("podcasts", "Podcasts");
        add("movies", "Movies & trailers");
        add("channels", "Your channels");
        add("history", "History");
        add("settings", "Settings");

        setOnSearchClickedListener(v -> startActivity(new Intent(requireContext(), SearchActivity.class)));

        setOnItemViewClickedListener((vh, o, rvh, row) -> {
            Item it = (Item) o;
            if (Item.ACTION.equals(it.type)) { onAction(it.action); return; }
            RowState rs = stateFor((Row) row);
            Nav.open(requireContext(), it, rs == null ? null : rs.list());
        });

        setOnItemViewSelectedListener((vh, o, rvh, row) -> {
            if (!(row instanceof ListRow) || o == null) return;
            RowState rs = stateFor((Row) row);
            if (rs == null || rs.more == null || rs.next == null || rs.loading) return;
            int idx = rs.items.indexOf(o);
            if (idx >= rs.items.size() - 6) loadMore(rs);
        });

        loadKiosk("music", "trending_music");
        loadKiosk("gaming", "trending_gaming");
        loadKiosk("live", "live");
        loadKiosk("podcasts", "trending_podcasts_episodes");
        loadKiosk("movies", "trending_movies_and_shows");
        loadRecommended();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshLocalRows();
        loadSubsFeed(false);
    }

    private void add(String key, String title) {
        RowState rs = new RowState(key, title);
        state.put(key, rs);
    }

    private RowState stateFor(Row row) {
        for (RowState rs : state.values()) if (rs.row == row) return rs;
        return null;
    }

    // ---------------------------------------------------------------- rows
    private void refreshLocalRows() {
        List<Item> cont = new ArrayList<>();
        List<Item> hist = Store.history();
        for (Item i : hist) if (i.position > 0 && cont.size() < 30) cont.add(i);
        state.get("continue").set(cont);
        state.get("history").set(hist.size() > 60 ? new ArrayList<>(hist.subList(0, 60)) : hist);

        List<Item> subs = Store.subs();
        state.get("channels").set(subs);

        List<Item> s = new ArrayList<>();
        s.add(Item.action("search", "Search", "Find videos and channels", 0xFF3EA6FF));
        s.add(Item.action("import", "Import subscriptions", "From LTTube on your PC", 0xFFFF0033));
        s.add(Item.action("refresh", "Refresh subscriptions", subs.size() + " channels", 0xFF7E57C2));
        s.add(Item.action("quality", "Video quality", qualityName(Store.maxHeight()), 0xFF26A69A));
        s.add(Item.action("autoplay", "Autoplay", Store.autoplay() ? "On" : "Off", 0xFF5C6BC0));
        s.add(Item.action("sponsor", "Skip sponsors", Store.sponsorBlock() ? "On (SponsorBlock)" : "Off", 0xFFFFA000));
        s.add(Item.action("captions", "Subtitles", Store.captions() ? "On when available" : "Off", 0xFF8D6E63));
        s.add(Item.action("clear", "Clear watch history", hist.size() + " videos", 0xFF546E7A));
        state.get("settings").set(s);

        if (subs.isEmpty()) {
            List<Item> e = new ArrayList<>();
            e.add(Item.action("import", "Import your subscriptions", "From LTTube on your PC", 0xFFFF0033));
            e.add(Item.action("search", "Find channels", "Search, open a channel, press Subscribe", 0xFF3EA6FF));
            state.get("subs").set(e);
        }
    }

    private void loadKiosk(String key, String kioskId) {
        RowState rs = state.get(key);
        rs.more = page -> Feeds.kioskMore(kioskId, page);
        Bg.run(() -> Feeds.kiosk(kioskId), (r, err) -> {
            if (!isAdded() || r == null) return;
            rs.set(r.items);
            rs.next = r.next;
        });
    }

    private void loadRecommended() {
        if (Store.history().isEmpty()) return;
        Bg.run(Feeds::recommended, (r, err) -> {
            if (!isAdded() || r == null) return;
            state.get("recommended").set(r.items);
        });
    }

    private void loadMore(RowState rs) {
        rs.loading = true;
        final Page page = rs.next;
        Bg.run(() -> rs.more.load(page), (r, err) -> {
            rs.loading = false;
            if (!isAdded() || r == null) return;
            rs.next = r.next;
            List<Item> have = rs.list();
            for (Item i : r.items) {
                boolean dup = false;
                for (Item h : have) if (h.url.equals(i.url)) { dup = true; break; }
                if (!dup) rs.items.add(i);
            }
        });
    }

    private void loadSubsFeed(boolean force) {
        RowState rs = state.get("subs");
        if (Store.subs().isEmpty()) return;
        List<Item> cached = Store.feed();
        if (!cached.isEmpty() && rs.items.size() == 0) rs.set(cached);
        if (feedLoading || (!force && !cached.isEmpty() && Store.feedAge() < FEED_MAX_AGE)) return;
        feedLoading = true;
        if (cached.isEmpty()) rs.setTitle("Subscriptions (loading…)");
        Bg.run(() -> Feeds.subscriptionFeed((done, total) -> Bg.UI.post(() -> {
            if (isAdded() && Store.feed().isEmpty()) rs.setTitle("Subscriptions (loading " + done + "/" + total + ")");
        })), (list, err) -> {
            feedLoading = false;
            if (!isAdded()) return;
            rs.setTitle("Subscriptions");
            if (list != null && !list.isEmpty()) {
                Store.saveFeed(list);
                rs.set(list);
            }
            if (force) Toast.makeText(requireContext(), "Subscriptions updated", Toast.LENGTH_SHORT).show();
        });
    }

    // ---------------------------------------------------------------- settings
    private void onAction(String a) {
        switch (a) {
            case "search":
                startActivity(new Intent(requireContext(), SearchActivity.class));
                break;
            case "import":
                new Importer(requireActivity(), added -> {
                    refreshLocalRows();
                    if (added > 0) loadSubsFeed(true);
                }).start();
                break;
            case "refresh":
                Toast.makeText(requireContext(), "Updating subscriptions…", Toast.LENGTH_SHORT).show();
                loadSubsFeed(true);
                break;
            case "quality": {
                final int[] hs = {2160, 1440, 1080, 720, 480, 360};
                String[] names = new String[hs.length];
                int cur = 2;
                for (int i = 0; i < hs.length; i++) {
                    names[i] = qualityName(hs[i]);
                    if (hs[i] == Store.maxHeight()) cur = i;
                }
                new AlertDialog.Builder(requireContext())
                        .setTitle("Highest video quality")
                        .setSingleChoiceItems(names, cur, (d, w) -> {
                            Store.setMaxHeight(hs[w]);
                            d.dismiss();
                            refreshLocalRows();
                        }).show();
                break;
            }
            case "autoplay":
                Store.setAutoplay(!Store.autoplay());
                refreshLocalRows();
                break;
            case "sponsor":
                Store.setSponsorBlock(!Store.sponsorBlock());
                refreshLocalRows();
                break;
            case "captions":
                Store.setCaptions(!Store.captions());
                refreshLocalRows();
                break;
            case "clear":
                new AlertDialog.Builder(requireContext())
                        .setTitle("Clear watch history?")
                        .setMessage("This also clears Continue watching and Recommended.")
                        .setPositiveButton("Clear", (d, w) -> { Store.clearHistory(); refreshLocalRows(); state.get("recommended").set(new ArrayList<>()); })
                        .setNegativeButton("Cancel", null)
                        .show();
                break;
            default:
                break;
        }
    }

    private static String qualityName(int h) {
        switch (h) {
            case 2160: return "4K (2160p)";
            case 1440: return "1440p";
            default: return h + "p";
        }
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(0xFF0F0F0F);
    }
}
