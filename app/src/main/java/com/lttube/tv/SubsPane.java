package com.lttube.tv;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.VerticalGridView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Subscriptions page: channel list on the left (All, recent, A–Z), videos on the right. */
public class SubsPane {
    final LinearLayout view;
    private final MainActivity a;
    private final LinearLayout list;
    private final TextView gridTitle, status;
    private final VerticalGridView grid;
    private final ArrayObjectAdapter cards;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, List<Item>> cache = new HashMap<>();
    private List<Item> feed = new ArrayList<>();
    private View firstRow;
    private boolean wantFocus;
    private int seq;
    private String showing = "";
    private final int cols, cardW;

    SubsPane(MainActivity act) {
        a = act;
        // fit whole cards in the space right of the channel list
        float sw = a.getResources().getDisplayMetrics().widthPixels / a.getResources().getDisplayMetrics().density;
        // Cards are sized for the collapsed channel list (avatars only), like YouTube: moving onto the videos
        // folds the list to give them the room; moving back onto it opens it again and slides the videos right.
        float avail = sw - 88 - 24 - LIST_SMALL - 29 - 24;
        cols = 3; // three videos across, like YouTube
        cardW = (int) ((avail - (cols - 1) * 18) / cols);
        cards = new ArrayObjectAdapter(new CardPresenter(cardW));
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(24), dp(22), 0, 0);

        TextView title = Ui.text(a, 30, Color.WHITE, true);
        title.setText("Subscriptions");
        title.setPadding(dp(20), 0, 0, dp(14));
        view.addView(title);

        android.widget.FrameLayout body = new android.widget.FrameLayout(a);
        view.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, dp(10), dp(30));
        sv.addView(list);

        line = new View(a);
        line.setBackgroundColor(0x33FFFFFF);
        android.widget.FrameLayout.LayoutParams ll = new android.widget.FrameLayout.LayoutParams(dp(1), -1);
        ll.leftMargin = dp(LIST_BIG + 8);
        body.addView(line, ll);

        right = new LinearLayout(a);
        right.setOrientation(LinearLayout.VERTICAL);
        android.widget.FrameLayout.LayoutParams rl = new android.widget.FrameLayout.LayoutParams(-1, -1);
        rl.leftMargin = dp(LIST_SMALL + 29);
        body.addView(right, rl);
        right.setTranslationX(dp(LIST_BIG - LIST_SMALL));
        body.addView(sv, new android.widget.FrameLayout.LayoutParams(dp(LIST_BIG), -1));
        gridTitle = Ui.text(a, 18, Color.WHITE, true);
        gridTitle.setText("Most relevant");
        gridTitle.setPadding(0, 0, 0, dp(10));
        right.addView(gridTitle);
        status = Ui.text(a, 16, Ui.TEXT2, false);
        status.setText("Loading your subscriptions…");
        right.addView(status);

        grid = new VerticalGridView(a);
        grid.setNumColumns(cols);
        grid.setHorizontalSpacing(dp(18));
        grid.setVerticalSpacing(dp(20));
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(4), 0, dp(40));
        ItemBridgeAdapter bridge = new ItemBridgeAdapter(cards);
        bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
            @Override
            public void onBind(ItemBridgeAdapter.ViewHolder vh) {
                vh.itemView.setOnClickListener(v -> {
                    Item it = (Item) vh.getItem();
                    List<Item> ctx = new ArrayList<>();
                    for (int i = 0; i < cards.size(); i++) ctx.add((Item) cards.get(i));
                    Nav.open(a, it, ctx);
                });
            }
        });
        grid.setAdapter(bridge);
        right.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        view.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (now == null) return;
            if (inside(now, grid)) setListOpen(false);
            else if (inside(now, list)) setListOpen(true);
        });

        load();
    }

    private static final int LIST_BIG = 280, LIST_SMALL = 76;
    private ScrollView sv;
    private View line;
    private LinearLayout right;
    private boolean listOpen = true;
    private final List<TextView> names = new ArrayList<>();

    private static boolean inside(View v, View parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) if (p == parent) return true;
        return false;
    }

    /** Open: avatars and names. Folded: avatars only, and the videos slide left into the room. */
    private void setListOpen(boolean open) {
        if (open == listOpen) return;
        listOpen = open;
        android.view.ViewGroup.LayoutParams lp = sv.getLayoutParams();
        lp.width = dp(open ? LIST_BIG : LIST_SMALL);
        sv.setLayoutParams(lp);
        for (TextView t : names) t.setVisibility(open ? View.VISIBLE : View.GONE);
        android.widget.FrameLayout.LayoutParams ll = (android.widget.FrameLayout.LayoutParams) line.getLayoutParams();
        ll.leftMargin = dp((open ? LIST_BIG : LIST_SMALL) + 8);
        line.setLayoutParams(ll);
        right.animate().translationX(open ? dp(LIST_BIG - LIST_SMALL) : 0).setDuration(180).start();
    }

    void focus() {
        if (firstRow != null) firstRow.requestFocus(); else wantFocus = true;
    }

    void focusWhenReady() { focus(); }

    private void load() {
        final boolean in = Account.signedIn();
        Bg.run(() -> {
            // Each part on its own: one that fails (YouTube changed something, signed out…) no longer
            // empties the whole page.
            List<Item> channels = new ArrayList<>();
            List<Item> latest = new ArrayList<>();
            String problem = null;
            if (in) {
                // Every channel you follow, from all the places YouTube lists them (TV, web, side menu).
                try { channels.addAll(Yti.subscribedChannels()); } catch (Exception e) { problem = Bg.message(e); }
                if (channels.isEmpty()) {
                    try { for (Shelf s : Sources.accountRow(Yti.CHANNELS, "Channels")) channels.addAll(s.items); }
                    catch (Exception e) { if (problem == null) problem = Bg.message(e); }
                }
                // Your subscriptions feed, a few pages deep.
                try {
                    for (Shelf s : Yti.sections(Yti.SUBS, "Latest")) {
                        for (Item i : s.items) if (Item.VIDEO.equals(i.type)) latest.add(i);
                        Shelf page = s;
                        for (int n = 0; n < 3 && page.canLoadMore() && latest.size() < 150; n++) {
                            page = page.loadMore();
                            for (Item i : page.items) if (Item.VIDEO.equals(i.type)) latest.add(i);
                        }
                    }
                } catch (Exception e) { if (problem == null) problem = Bg.message(e); }
            }
            // Signed in: only the channels YouTube says you're subscribed to (never ones you only opened or watched).
            if (!in && channels.isEmpty()) channels.addAll(Store.subs());
            if (latest.isEmpty()) {
                // Not signed in, or YouTube's feed came back empty: your channels' newest uploads.
                try { for (Shelf s : Sources.localSubsFeed()) latest.addAll(s.items); }
                catch (Exception e) { if (problem == null) problem = Bg.message(e); }
            }
            // Channels from the feed itself, only if YouTube didn't list any.
            if (channels.isEmpty() && !in) {
                java.util.Set<String> names = new java.util.HashSet<>();
                for (Item v : latest) {
                    if (v.channelUrl == null || v.channelUrl.isEmpty() || v.channel == null || v.channel.isEmpty() || !names.add(v.channel)) continue;
                    Item c = new Item();
                    c.type = Item.CHANNEL;
                    c.title = v.channel;
                    c.url = v.channelUrl;
                    channels.add(c);
                }
            }
            if (channels.isEmpty() && latest.isEmpty() && problem != null) throw new Exception(problem);
            // drop anything without a usable channel link or name
            List<Item> clean = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Item c : channels) if (c.url != null && !c.url.isEmpty() && c.title != null && !c.title.isEmpty() && seen.add(Store.channelId(c.url))) clean.add(c);
            return new Object[]{clean, latest};
        }, (res, err) -> {
            if (res == null) { status.setText("Couldn't load subscriptions: " + Bg.message(err)); return; }
            @SuppressWarnings("unchecked") List<Item> channels = (List<Item>) res[0];
            @SuppressWarnings("unchecked") List<Item> latest = (List<Item>) res[1];
            try {
                feed = latest;
                buildList(channels);
                showing = "";
                show("all", "All");
                if (wantFocus && firstRow != null) firstRow.requestFocus();
            } catch (Exception e) {
                status.setVisibility(View.VISIBLE);
                status.setText("Couldn't show subscriptions: " + Bg.message(e));
            }
        });
    }

    private void buildList(List<Item> channels) {
        list.removeAllViews();
        names.clear();
        firstRow = row("all", "All", null, R.drawable.ic_subs);
        list.addView(firstRow);
        // channels that uploaded most recently first, like YouTube
        Map<String, Item> byName = new LinkedHashMap<>();
        for (Item c : channels) byName.put(c.title, c);
        List<Item> recent = new ArrayList<>();
        for (Item v : feed) {
            if (v.channel == null) continue;
            Item c = byName.get(v.channel);
            if (c != null && !recent.contains(c)) recent.add(c);
            if (recent.size() >= 7) break;
        }
        for (Item c : recent) list.addView(row(c.url, c.title, c.thumb, 0));
        if (!channels.isEmpty()) {
            TextView az = Ui.text(a, 14, Ui.TEXT2, true);
            az.setText("A–Z");
            az.setPadding(dp(14), dp(14), 0, dp(6));
            list.addView(az);
            names.add(az);
            if (!listOpen) az.setVisibility(View.GONE);
            List<Item> sorted = new ArrayList<>(channels);
            Collections.sort(sorted, (x, y) -> x.title.compareToIgnoreCase(y.title));
            for (Item c : sorted) list.addView(row(c.url, c.title, c.thumb, 0));
        }
        if (channels.isEmpty() && feed.isEmpty()) {
            status.setText(Account.signedIn() ? "No subscriptions found." : "Sign in (Settings) or import your subscriptions to see them here.");
        }
    }

    private View row(final String key, final String name, String thumb, int icon) {
        final LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(16), dp(9), dp(16), dp(9));
        ImageView img = new ImageView(a);
        if (icon != 0) { img.setImageResource(icon); img.setColorFilter(Color.WHITE); img.setPadding(dp(4), dp(4), dp(4), dp(4)); }
        else Glide.with(a).load(thumb).apply(RequestOptions.circleCropTransform()).into(img);
        r.addView(img, new LinearLayout.LayoutParams(dp(34), dp(34)));
        final TextView t = Ui.text(a, 17, Color.WHITE, true);
        t.setText(name);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(dp(14), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        names.add(t);
        if (!listOpen) t.setVisibility(View.GONE);
        r.setOnFocusChangeListener((v, has) -> {
            r.setBackground(has ? Ui.pill(a, Ui.FOCUS, 24) : key.equals(showing) ? Ui.pill(a, Ui.SELECTED, 24) : null);
            t.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
            if (icon != 0) img.setColorFilter(has ? 0xFF0F0F0F : Color.WHITE);
            if (has) {
                h.removeCallbacksAndMessages(null);
                h.postDelayed(() -> show(key, name), 350);
            }
        });
        r.setOnClickListener(v -> {
            if (!"all".equals(key)) Nav.channel(a, key, name);
        });
        r.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT && cards.size() > 0) {
                setListOpen(false);
                grid.requestFocus();
                return true;
            }
            return false;
        });
        return r;
    }

    private void show(final String key, final String name) {
        if (key.equals(showing)) return;
        showing = key;
        final int s = ++seq;
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (c.isFocusable() && !c.isFocused()) c.setBackground(null);
        }
        if ("all".equals(key)) {
            gridTitle.setText("Most relevant");
            setCards(feed);
            status.setText(feed.isEmpty() ? "No recent videos." : "");
            status.setVisibility(feed.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }
        gridTitle.setText(name);
        List<Item> cached = cache.get(key);
        if (cached != null) { setCards(cached); return; }
        status.setText("Loading…");
        status.setVisibility(View.VISIBLE);
        setCards(new ArrayList<>());
        Bg.run(() -> {
            List<Item> l = Feeds.channel(key, org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS).items;
            if (l.isEmpty() && key.contains("/channel/UC")) l = Yti.sections(Store.channelId(key), name).isEmpty() ? l : Yti.sections(Store.channelId(key), name).get(0).items;
            return l;
        }, (items, err) -> {
            if (s != seq) return;
            if (items == null) { status.setText("Couldn't load: " + Bg.message(err)); return; }
            for (Item i : items) if (i.channel.isEmpty()) i.channel = name;
            cache.put(key, items);
            status.setVisibility(View.GONE);
            setCards(items);
        });
    }

    private void setCards(List<Item> items) {
        cards.clear();
        cards.addAll(0, items);
        if (!items.isEmpty()) status.setVisibility(View.GONE);
    }

    private int dp(float v) { return Ui.dp(a, v); }
}
