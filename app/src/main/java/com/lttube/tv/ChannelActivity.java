package com.lttube.tv;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;

import java.util.ArrayList;
import java.util.List;

/** A channel page like YouTube TV: header with Subscribe, then Videos / Shorts / Live / Playlists rows. */
public class ChannelActivity extends FragmentActivity {
    public static final String URL = "url", TITLE = "title";

    private String url, title;
    private ChannelInfo info;
    private TextView name, meta, subBtn;
    private ImageView avatar, banner;
    private RowsController rows;
    private float d;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        url = getIntent().getStringExtra(URL);
        title = getIntent().getStringExtra(TITLE);
        if (title == null) title = "";

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F0F0F);
        banner = new ImageView(this);
        banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.setAlpha(0.35f);
        root.addView(banner, new FrameLayout.LayoutParams(-1, dp(170)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(56), dp(36), 0, 0);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        avatar = new ImageView(this);
        head.addView(avatar, new LinearLayout.LayoutParams(dp(88), dp(88)));
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(20), 0, dp(24), 0);
        name = tv(28, Color.WHITE, true);
        name.setText(title);
        meta = tv(15, 0xFFAAAAAA, false);
        txt.addView(name);
        txt.addView(meta);
        head.addView(txt);
        subBtn = tv(16, Color.WHITE, true);
        subBtn.setFocusable(true);
        subBtn.setPadding(dp(22), dp(10), dp(22), dp(10));
        subBtn.setOnFocusChangeListener((v, has) -> styleSub());
        subBtn.setOnClickListener(v -> toggleSub());
        head.addView(subBtn);
        col.addView(head);

        FrameLayout holder = new FrameLayout(this);
        holder.setId(View.generateViewId());
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, 0, 1f);
        hl.topMargin = dp(10);
        col.addView(holder, hl);
        setContentView(root);

        rows = new RowsController(this, holder.getId(), (it, row) -> Nav.open(this, it, row));
        styleSub();
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

    private void load() {
        Bg.run(() -> ChannelInfo.getInfo(Feeds.yt(), url), (c, err) -> {
            if (c == null) { Toast.makeText(this, "Couldn't load channel: " + Bg.message(err), Toast.LENGTH_LONG).show(); return; }
            info = c;
            title = c.getName();
            name.setText(title);
            List<String> bits = new ArrayList<>();
            if (c.getSubscriberCount() >= 0) bits.add(Item.count(c.getSubscriberCount()) + " subscribers");
            String desc = c.getDescription();
            if (desc != null && !desc.isEmpty()) bits.add(desc.replace('\n', ' ').length() > 90 ? desc.replace('\n', ' ').substring(0, 90) + "…" : desc.replace('\n', ' '));
            meta.setText(android.text.TextUtils.join("  •  ", bits));
            Glide.with(this).load(Item.bestImage(c.getAvatars(), 176)).apply(RequestOptions.circleCropTransform()).into(avatar);
            String b = Item.bestImage(c.getBanners(), 400);
            if (!b.isEmpty()) Glide.with(this).load(b).centerCrop().into(banner);
            addTab(ChannelTabs.VIDEOS, "Videos");
            addTab(ChannelTabs.SHORTS, "Shorts");
            addTab(ChannelTabs.LIVESTREAMS, "Live");
            addTab(ChannelTabs.PLAYLISTS, "Playlists");
        });
    }

    private void addTab(String tab, String label) {
        final ListLinkHandler h = Feeds.channelTab(info, tab);
        if (h == null) return;
        rows.add(() -> {
            ChannelTabInfo t = ChannelTabInfo.getInfo(Feeds.yt(), h);
            Feeds.Result r = new Feeds.Result();
            List<InfoItem> items = new ArrayList<>(t.getRelatedItems());
            Feeds.addAll(r, items, false);
            if (ChannelTabs.SHORTS.equals(tab)) for (Item i : r.items) i.isShort = true;
            for (Item i : r.items) if (i.channel.isEmpty()) i.channel = title;
            r.next = t.getNextPage();
            final ChannelInfo ci = info;
            List<Shelf> l = new ArrayList<>();
            if (!r.items.isEmpty()) l.add(Shelf.of(label, r, page -> {
                Feeds.Result m = Feeds.channelMore(ci, tab, page);
                if (ChannelTabs.SHORTS.equals(tab)) for (Item i : m.items) i.isShort = true;
                return m;
            }));
            return l;
        });
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
        int bg = f ? Color.WHITE : subbed ? 0xFF303030 : 0xFFF1F1F1;
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(22));
        if (f) g.setStroke(dp(3), 0xFFFF0033);
        subBtn.setBackground(g);
        subBtn.setTextColor(subbed && !f ? Color.WHITE : 0xFF0F0F0F);
    }

    private TextView tv(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return t;
    }

    private int dp(int v) { return Math.round(v * d); }
}
