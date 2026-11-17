package com.lttube.tv;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.ArrayList;
import java.util.List;

/** A channel page like YouTube TV: banner, name, Subscribe, tabs, then videos. */
public class ChannelActivity extends FragmentActivity {
    public static final String URL = "url", TITLE = "title";

    private static final String[][] TABS = {{"home", "Home"}, {ChannelTabs.VIDEOS, "Videos"}, {ChannelTabs.SHORTS, "Shorts"},
            {ChannelTabs.LIVESTREAMS, "Live"}, {ChannelTabs.PLAYLISTS, "Playlists"}};

    private String url, title;
    private ChannelInfo info;
    private FrameLayout header;
    private ImageView banner, avatar;
    private TextView name, meta, subBtn;
    private LinearLayout tabsRow;
    private final List<TextView> tabViews = new ArrayList<>();
    private FrameLayout rowsHolder;
    private VerticalGridView grid;
    private ArrayObjectAdapter cards;
    private RowsController rows;
    private String tab = "home";
    private Page next;
    private boolean loading;
    private int seq;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        url = getIntent().getStringExtra(URL);
        title = getIntent().getStringExtra(TITLE);
        if (title == null) title = "";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // ---- banner + channel info
        header = new FrameLayout(this);
        banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(banner, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{Ui.BG, 0xCC14161E, 0x5514161E}));
        header.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(56), 0, dp(56), dp(14));
        avatar = new ImageView(this);
        info.addView(avatar, new LinearLayout.LayoutParams(dp(84), dp(84)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), 0, dp(20), 0);
        name = Ui.text(this, 30, Color.WHITE, true);
        name.setText(title);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        meta = Ui.text(this, 15, Ui.TEXT2, false);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name);
        col.addView(meta);
        info.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        subBtn = Ui.text(this, 16, Color.WHITE, true);
        subBtn.setFocusable(true);
        subBtn.setPadding(dp(26), dp(11), dp(26), dp(11));
        subBtn.setOnFocusChangeListener((v, has) -> styleSub());
        subBtn.setOnClickListener(v -> toggleSub());
        info.addView(subBtn);
        header.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(190)));

        // ---- tabs
        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(dp(50), dp(6), dp(50), dp(10));
        for (String[] t : TABS) {
            final String key = t[0];
            final TextView tv = Ui.text(this, 16, Color.WHITE, true);
            tv.setText(t[1]);
            tv.setFocusable(true);
            tv.setPadding(dp(20), dp(8), dp(20), dp(8));
            tv.setTag(key);
            tv.setOnFocusChangeListener((v, has) -> styleTabs());
            tv.setOnClickListener(v -> selectTab(key));
            tv.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) { focusContent(); return true; }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            tabsRow.addView(tv, lp);
            tabViews.add(tv);
        }
        root.addView(tabsRow);

        // ---- content: rows (Home) or a grid (other tabs)
        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        rowsHolder = new FrameLayout(this);
        rowsHolder.setId(View.generateViewId());
        content.addView(rowsHolder, new FrameLayout.LayoutParams(-1, -1));
        float sw = getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density;
        float avail = sw - 56 - 40;
        int cols = avail >= 4 * 210 + 48 ? 4 : 3;
        int cardW = (int) ((avail - (cols - 1) * 16) / cols);
        cards = new ArrayObjectAdapter(new CardPresenter(cardW));
        grid = new VerticalGridView(this);
        grid.setNumColumns(cols);
        grid.setHorizontalSpacing(dp(16));
        grid.setVerticalSpacing(dp(18));
        grid.setClipToPadding(false);
        grid.setPadding(dp(56), dp(10), dp(40), dp(40));
        grid.setVisibility(View.GONE);
        ItemBridgeAdapter bridge = new ItemBridgeAdapter(cards);
        bridge.setAdapterListener(new ItemBridgeAdapter.AdapterListener() {
            @Override
            public void onBind(ItemBridgeAdapter.ViewHolder vh) {
                vh.itemView.setOnClickListener(v -> {
                    List<Item> ctx = new ArrayList<>();
                    for (int i = 0; i < cards.size(); i++) ctx.add((Item) cards.get(i));
                    Nav.open(ChannelActivity.this, (Item) vh.getItem(), ctx);
                });
            }
        });
        grid.setAdapter(bridge);
        grid.setOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(RecyclerView parent, RecyclerView.ViewHolder child, int position, int subposition) {
                if (position >= cards.size() - 8) loadMore();
            }
        });
        content.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        rows = new RowsController(this, rowsHolder.getId(), (it, row) -> Nav.open(this, it, row));

        // header slides away while you browse videos, like YouTube
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusWatcher);

        styleSub();
        styleTabs();
        subBtn.requestFocus();
        load();

        if (Account.signedIn()) {
            final String cid = Store.channelId(url);
            Bg.run(() -> Yti.channelSubscribed(cid), (sub, e) -> {
                if (sub == null) return;
                if (sub && !Store.isSubscribed(url)) Store.subscribe(url, title, "");
                if (!sub && Store.isSubscribed(url)) Store.unsubscribe(url);
                styleSub();
            });
        }
    }

    private final ViewTreeObserver.OnGlobalFocusChangeListener focusWatcher = (oldF, newF) -> {
        if (newF == null) return;
        boolean top = newF == subBtn || tabViews.contains(newF);
        header.setVisibility(top ? View.VISIBLE : View.GONE);
    };

    private void focusContent() {
        if ("home".equals(tab)) rows.focus();
        else grid.requestFocus();
    }

    private void load() {
        Bg.run(() -> ChannelInfo.getInfo(Feeds.yt(), url), (c, err) -> {
            if (c == null) { Toast.makeText(this, "Couldn't load channel: " + Bg.message(err), Toast.LENGTH_LONG).show(); return; }
            info = c;
            title = c.getName();
            name.setText(title);
            List<String> bits = new ArrayList<>();
            String handle = c.getUrl() != null && c.getUrl().contains("/@") ? c.getUrl().substring(c.getUrl().indexOf("/@") + 1) : "";
            if (!handle.isEmpty()) bits.add(handle);
            if (c.getSubscriberCount() >= 0) bits.add(Item.count(c.getSubscriberCount()) + " subscribers");
            meta.setText(TextUtils.join("  •  ", bits));
            Glide.with(this).load(Item.bestImage(c.getAvatars(), 176)).apply(RequestOptions.circleCropTransform()).into(avatar);
            String b = Item.bestImage(c.getBanners(), 600);
            if (!b.isEmpty()) Glide.with(this).load(b).centerCrop().into(banner);
            // hide tabs the channel doesn't have
            for (TextView tv : tabViews) {
                String k = (String) tv.getTag();
                if (!"home".equals(k) && Feeds.channelTab(c, k) == null) tv.setVisibility(View.GONE);
            }
            selectTab("home");
        });
    }

    private void selectTab(String key) {
        tab = key;
        styleTabs();
        final int s = ++seq;
        if (info == null) return;
        if ("home".equals(key)) {
            grid.setVisibility(View.GONE);
            rowsHolder.setVisibility(View.VISIBLE);
            rows.clear();
            for (String[] t : TABS) if (!"home".equals(t[0])) addRow(t[0], t[1]);
            return;
        }
        rowsHolder.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        cards.clear();
        next = null;
        loading = true;
        final ListLinkHandler h = Feeds.channelTab(info, key);
        if (h == null) { loading = false; return; }
        Bg.run(() -> ChannelTabInfo.getInfo(Feeds.yt(), h), (t, err) -> {
            if (s != seq) return;
            loading = false;
            if (t == null) { Toast.makeText(this, "Couldn't load: " + Bg.message(err), Toast.LENGTH_SHORT).show(); return; }
            Feeds.Result r = new Feeds.Result();
            Feeds.addAll(r, new ArrayList<InfoItem>(t.getRelatedItems()), false);
            fix(r.items, key);
            cards.addAll(0, r.items);
            next = t.getNextPage();
        });
    }

    private void loadMore() {
        if (loading || next == null || info == null || "home".equals(tab)) return;
        loading = true;
        final int s = seq;
        final String key = tab;
        final Page page = next;
        Bg.run(() -> Feeds.channelMore(info, key, page), (r, err) -> {
            if (s != seq) return;
            loading = false;
            if (r == null) return;
            fix(r.items, key);
            cards.addAll(cards.size(), r.items);
            next = r.next;
        });
    }

    private void addRow(final String key, final String label) {
        final ListLinkHandler h = Feeds.channelTab(info, key);
        if (h == null) return;
        final ChannelInfo ci = info;
        rows.add(() -> {
            ChannelTabInfo t = ChannelTabInfo.getInfo(Feeds.yt(), h);
            Feeds.Result r = new Feeds.Result();
            Feeds.addAll(r, new ArrayList<InfoItem>(t.getRelatedItems()), false);
            fix(r.items, key);
            r.next = t.getNextPage();
            List<Shelf> l = new ArrayList<>();
            if (!r.items.isEmpty()) l.add(Shelf.of(label, r, page -> {
                Feeds.Result m = Feeds.channelMore(ci, key, page);
                fix(m.items, key);
                return m;
            }));
            return l;
        });
    }

    private void fix(List<Item> items, String key) {
        for (Item i : items) {
            if (ChannelTabs.SHORTS.equals(key)) i.isShort = true;
            if (i.channel.isEmpty()) i.channel = title;
            if (i.channelUrl.isEmpty()) i.channelUrl = url;
        }
    }

    private void styleTabs() {
        for (TextView tv : tabViews) {
            boolean f = tv.isFocused(), cur = tab.equals(tv.getTag());
            tv.setBackground(f ? Ui.pill(this, Ui.FOCUS, 22) : cur ? Ui.pill(this, 0xFF333746, 22) : null);
            tv.setTextColor(f ? 0xFF0F0F0F : cur ? Color.WHITE : Ui.TEXT2);
        }
    }

    private void toggleSub() {
        final boolean on = !Store.isSubscribed(url);
        if (on) Store.subscribe(url, title, info != null ? Item.bestImage(info.getAvatars(), 176) : "");
        else Store.unsubscribe(url);
        styleSub();
        if (Account.signedIn()) {
            final String cid = Store.channelId(url);
            Bg.run(() -> { Yti.subscribe(cid, on); return true; }, (r, e) -> {
                if (r == null) Toast.makeText(this, "YouTube didn't save that: " + Bg.message(e), Toast.LENGTH_LONG).show();
                else Toast.makeText(this, on ? "Subscribed" : "Unsubscribed", Toast.LENGTH_SHORT).show();
            });
        } else {
            Toast.makeText(this, on ? "Subscribed (on this TV)" : "Unsubscribed", Toast.LENGTH_SHORT).show();
        }
    }

    private void styleSub() {
        boolean subbed = Store.isSubscribed(url);
        boolean f = subBtn.isFocused();
        subBtn.setText(subbed ? "Subscribed" : "Subscribe");
        subBtn.setBackground(Ui.pill(this, f ? Color.WHITE : subbed ? 0xFF333746 : 0xFFF1F1F1, 24));
        subBtn.setTextColor(subbed && !f ? Color.WHITE : 0xFF0F0F0F);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Back from the videos goes to the tabs first
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && header.getVisibility() == View.GONE) {
            for (TextView tv : tabViews) if (tab.equals(tv.getTag())) { tv.requestFocus(); return true; }
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_DOWN && header.getVisibility() == View.GONE) return true;
        return super.dispatchKeyEvent(e);
    }

    private int dp(float v) { return Ui.dp(this, v); }
}
