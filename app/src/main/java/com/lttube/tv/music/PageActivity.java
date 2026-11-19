package com.lttube.tv.music;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.lttube.tv.Bg;
import com.lttube.tv.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * An album, playlist, artist or mood: a big header (name, "Album • Artist • 2016", a line about it) over its
 * picture, Play / Shuffle (and Follow for artists), then the songs as a list and anything else as rows.
 */
public class PageActivity extends Activity {
    public static final String ITEM = "item";

    private MItem item;
    private FrameLayout root;
    private Hero hero;
    private ScrollView scroll;
    private LinearLayout content;
    private ProgressBar spinner;
    private int W, H;
    private boolean followingNow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { item = MItem.fromJson(new JSONObject(getIntent().getStringExtra(ITEM))); } catch (Exception e) { finish(); return; }
        float d = getResources().getDisplayMetrics().density;
        W = Math.round(getResources().getDisplayMetrics().widthPixels / d);
        H = Math.round(getResources().getDisplayMetrics().heightPixels / d);
        root = new FrameLayout(this);
        root.setBackground(MusicUi.background());
        setContentView(root);

        hero = new Hero(this);
        root.addView(hero, new FrameLayout.LayoutParams(-1, dp(Math.round(H * 0.74f)), Gravity.TOP));
        if (!item.thumb.isEmpty()) hero.set(MItem.art(item.thumb, 1080), false);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(0, 0, 0, dp(50));
        scroll.addView(content);
        root.addView(scroll, MusicUi.match());
        scroll.getViewTreeObserver().addOnScrollChangedListener(() ->
                hero.setAlpha(1f - Math.min(1f, scroll.getScrollY() / (float) dp(H * 0.4f))));

        spinner = new ProgressBar(this);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        Bg.run(() -> Ytm.browse(item.browseId, item.params), (pg, e) -> {
            spinner.setVisibility(View.GONE);
            if (pg == null) {
                TextView t = MusicUi.text(this, 18, MusicUi.TEXT2, false);
                t.setText("Couldn't open this: " + Bg.message(e));
                t.setPadding(dp(MusicUi.PAD), dp(H * 0.4f), dp(MusicUi.PAD), 0);
                content.addView(t);
                return;
            }
            show(pg);
        });
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    private void show(Ytm.Page pg) {
        boolean artist = MItem.ARTIST.equals(item.type) || item.browseId.startsWith("UC");
        if (!pg.banner.isEmpty()) hero.set(pg.banner, true);
        else if (!pg.thumb.isEmpty()) hero.set(MItem.art(pg.thumb, 1080), false);
        final List<MItem> songs = pg.songs();
        final String pl = !pg.playlistId.isEmpty() ? pg.playlistId : item.playlistId;

        // ---- header
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(dp(MusicUi.PAD), dp(40), dp(MusicUi.PAD), dp(10));
        int tw = Math.round(W * 0.55f);
        TextView t = MusicUi.text(this, 36, Color.WHITE, true);
        t.setText(pg.title.isEmpty() ? item.title : pg.title);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(t, new LinearLayout.LayoutParams(dp(tw), -2));
        String sub = pg.subtitle;
        if (artist) sub = (pg.subscribers + (pg.monthly.isEmpty() ? "" : (pg.subscribers.isEmpty() ? "" : "  ·  ") + pg.monthly)).trim();
        if (!sub.isEmpty()) {
            TextView s = MusicUi.line(this, 15, 0xE6FFFFFF, true);
            s.setText(sub);
            s.setPadding(0, dp(8), 0, 0);
            head.addView(s, new LinearLayout.LayoutParams(dp(tw), -2));
        }
        if (!pg.description.isEmpty()) {
            TextView ds = MusicUi.text(this, 14, 0xCCFFFFFF, false);
            ds.setText(pg.description);
            ds.setMaxLines(2);
            ds.setEllipsize(TextUtils.TruncateAt.END);
            ds.setPadding(0, dp(8), 0, 0);
            head.addView(ds, new LinearLayout.LayoutParams(dp(tw), -2));
        }
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(16), 0, 0);
        btns.setClipChildren(false);
        TextView play = MusicUi.pill(this, R.drawable.ic_m_play, "Play");
        play.setOnClickListener(v -> {
            MusicPlayer mp = MusicPlayer.get(this);
            mp.setShuffle(false);
            if (!songs.isEmpty()) mp.playList(songs, 0, pl);
            else if (!pl.isEmpty()) mp.playId(null, pl);
            else return;
            startActivity(MusicPlayer.nowPlaying(this));
        });
        btns.addView(play, new LinearLayout.LayoutParams(-2, dp(44)));
        if (!songs.isEmpty()) {
            TextView shuf = MusicUi.pill(this, R.drawable.ic_m_shuffle, artist ? "Shuffle" : "Shuffle");
            shuf.setOnClickListener(v -> { MusicPlayer.get(this).shuffleList(songs, pl); startActivity(MusicPlayer.nowPlaying(this)); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
            lp.leftMargin = dp(14);
            btns.addView(shuf, lp);
        }
        if (artist) {
            final TextView fol = MusicUi.pill(this, 0, "Follow");
            final String cid = pg.channelId.isEmpty() ? item.browseId : pg.channelId;
            fol.setOnClickListener(v -> {
                if (!Ytm.signedIn()) { Toast.makeText(this, "Sign in (Your Library) to follow artists", Toast.LENGTH_SHORT).show(); return; }
                followingNow = !followingNow;
                fol.setText(followingNow ? "Following" : "Follow");
                final boolean on = followingNow;
                Bg.run(() -> { Ytm.follow(cid, on); return true; }, (ok, e) -> { if (e != null) { followingNow = !on; fol.setText(!on ? "Following" : "Follow"); } });
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
            lp.leftMargin = dp(14);
            btns.addView(fol, lp);
        }
        head.addView(btns);
        content.addView(head, new LinearLayout.LayoutParams(-1, dp(Math.round(H * 0.70f))));

        // ---- songs (album / playlist): a full-width list
        boolean list = !artist && !MItem.CATEGORY.equals(item.type) && !songs.isEmpty();
        MusicUi.Opener open = (m, ctx) -> MusicUi.open(this, m, ctx);
        if (list) {
            boolean numbered = MItem.ALBUM.equals(item.type) || item.browseId.startsWith("MPRE");
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(dp(MusicUi.PAD - 12), dp(6), dp(MusicUi.PAD - 12), 0);
            for (int i = 0; i < songs.size(); i++) {
                final int at = i;
                final MItem m = songs.get(i);
                View r = MusicUi.songRow(this, m, numbered ? i + 1 : -1, true);
                r.setOnClickListener(v -> { MusicPlayer.get(this).playList(songs, at, pl); startActivity(MusicPlayer.nowPlaying(this)); });
                r.setOnKeyListener((v, k, e) -> {
                    if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { MusicUi.options(this, m); return true; }
                    return false;
                });
                l.addView(r, new LinearLayout.LayoutParams(-1, dp(62)));
            }
            content.addView(l);
            List<Ytm.Shelf> rest = new ArrayList<>();
            for (Ytm.Shelf s : pg.shelves) if (!s.list) rest.add(s);
            MusicUi.addShelves(this, content, rest, open, null);
        } else if (artist) {
            // Popular: the artist's top songs, numbered, one per row.
            List<Ytm.Shelf> rest = new ArrayList<>();
            boolean done = false;
            for (Ytm.Shelf s : pg.shelves) {
                if (!done && s.list && !s.items.isEmpty()) {
                    done = true;
                    content.addView(MusicUi.heading(this, "Popular"));
                    LinearLayout l = new LinearLayout(this);
                    l.setOrientation(LinearLayout.VERTICAL);
                    l.setPadding(dp(MusicUi.PAD - 12), dp(6), dp(MusicUi.PAD - 12), 0);
                    final List<MItem> top = s.items;
                    for (int i = 0; i < Math.min(top.size(), 10); i++) {
                        final int at = i;
                        final MItem m = top.get(i);
                        View r = MusicUi.songRow(this, m, i + 1, true);
                        r.setOnClickListener(v -> { MusicPlayer.get(this).playList(top, at, null); startActivity(MusicPlayer.nowPlaying(this)); });
                        r.setOnKeyListener((v, k, e) -> {
                            if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { MusicUi.options(this, m); return true; }
                            return false;
                        });
                        l.addView(r, new LinearLayout.LayoutParams(-1, dp(62)));
                    }
                    content.addView(l);
                } else rest.add(s);
            }
            MusicUi.addShelves(this, content, rest, open, null);
        } else {
            MusicUi.addShelves(this, content, pg.shelves, open, null);
        }
        play.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (e.getAction() == KeyEvent.ACTION_DOWN) MusicPlayer.get(this).toggle(); return true; }
        if (k == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && scroll.getScrollY() > 10) {
            scroll.smoothScrollTo(0, 0);
            View first = content.getChildCount() > 0 ? content.getChildAt(0).findFocus() : null;
            View play = content.getChildCount() > 0 ? firstFocusable(content.getChildAt(0)) : null;
            if (play != null) play.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private static View firstFocusable(View v) {
        if (v.isFocusable()) return v;
        if (v instanceof android.view.ViewGroup) for (int i = 0; i < ((android.view.ViewGroup) v).getChildCount(); i++) { View r = firstFocusable(((android.view.ViewGroup) v).getChildAt(i)); if (r != null) return r; }
        return null;
    }
}
