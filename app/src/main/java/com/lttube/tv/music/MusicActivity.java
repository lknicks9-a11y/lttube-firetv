package com.lttube.tv.music;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lttube.tv.Bg;
import com.lttube.tv.R;
import com.lttube.tv.WelcomeActivity;

import java.util.ArrayList;
import java.util.List;

/** Home of the music app: tabs along the top (Home, New, Charts, Moods, Library, Now Playing, Search) over rows. */
public class MusicActivity extends Activity implements MusicPlayer.Listener {
    private static final String[][] TABS = {{"home", "Home"}, {"new", "New"}, {"videos", "Videos"}, {"radio", "Radio"},
            {"library", "Library"}, {"now", "Now Playing"}, {"search", ""}};

    private FrameLayout root;
    private LinearLayout tabBar, content;
    private ScrollView scroll;
    private ProgressBar spinner;
    private TextView status;
    private final List<TextView> tabViews = new ArrayList<>();
    private ImageView searchTab;
    private String tab = "home";
    private int seq;
    private String moreToken, moreId;
    private boolean loadingMore;
    private MusicPlayer mp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mp = MusicPlayer.get(this);
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF26292A, 0xFF1B1D1E, 0xFF141516}));
        setContentView(root);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(0, dp(84), 0, dp(60));
        scroll.addView(content);
        root.addView(scroll, MusicUi.match());
        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int y = scroll.getScrollY();
            tabBar.setAlpha(Math.max(0f, 1f - y / (float) dp(120)));
            if (!loadingMore && moreToken != null && y + scroll.getHeight() > content.getHeight() - dp(700)) loadMore();
        });

        // ---- pill tab bar, centred at the top
        tabBar = new LinearLayout(this);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(6);
        tabBar.setPadding(p, p, p, p);
        tabBar.setBackground(MusicUi.round(0x26FFFFFF, dp(30)));
        tabBar.setElevation(dp(6));
        for (String[] t : TABS) {
            if (t[0].equals("search")) {
                searchTab = new ImageView(this);
                searchTab.setImageResource(R.drawable.ic_search);
                searchTab.setColorFilter(0xDDFFFFFF);
                searchTab.setFocusable(true);
                searchTab.setPadding(dp(14), dp(10), dp(14), dp(10));
                final GradientDrawable bg = MusicUi.round(0, dp(24));
                searchTab.setBackground(bg);
                searchTab.setOnFocusChangeListener((v, has) -> { bg.setColor(has ? Color.WHITE : 0); searchTab.setColorFilter(has ? 0xFF111111 : 0xDDFFFFFF); });
                searchTab.setOnClickListener(v -> startActivity(new Intent(this, MusicSearchActivity.class)));
                tabBar.addView(searchTab, new LinearLayout.LayoutParams(dp(50), dp(38)));
                continue;
            }
            final String key = t[0];
            TextView tv = MusicUi.text(this, 16, 0xDDFFFFFF, true);
            tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            tv.setText(t[1]);
            tv.setFocusable(true);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(18), 0, dp(18), 0);
            tv.setTag(key);
            tv.setOnFocusChangeListener((v, has) -> paintTabs());
            tv.setOnClickListener(v -> {
                if (key.equals("now")) { startActivity(MusicPlayer.nowPlaying(this)); return; }
                show(key, true);
            });
            tabViews.add(tv);
            tabBar.addView(tv, new LinearLayout.LayoutParams(-2, dp(38)));
        }
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tl.topMargin = dp(22);
        root.addView(tabBar, tl);

        spinner = new ProgressBar(this);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        status = MusicUi.text(this, 18, MusicUi.TEXT2, false);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        show("home", false);
        tabViews.get(0).requestFocus();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    private void paintTabs() {
        for (TextView tv : tabViews) {
            boolean f = tv.hasFocus(), sel = tab.equals(tv.getTag());
            tv.setBackground(MusicUi.round(f ? Color.WHITE : sel ? 0x40FFFFFF : 0, dp(19)));
            tv.setTextColor(f ? 0xFF1C1C1E : sel ? Color.WHITE : 0xB3FFFFFF);
            tv.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(140).start();
            String label = tv.getText().toString().replace("  ▶", "");
            if ("now".equals(tv.getTag()) && mp.current() != null && mp.playing()) label += "  ▶";
            tv.setText(label);
        }
    }

    private void show(String key, boolean focusContent) {
        tab = key;
        paintTabs();
        final int my = ++seq;
        content.removeAllViews();
        scroll.scrollTo(0, 0);
        spinner.setVisibility(View.VISIBLE);
        status.setVisibility(View.GONE);
        moreToken = null;
        if (key.equals("library") && !Ytm.signedIn()) { libraryGuest(); return; }
        Bg.run(() -> load(key), (pages, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            List<Ytm.Shelf> all = new ArrayList<>();
            if (pages != null) for (Ytm.Page pg : pages) all.addAll(pg.shelves);
            if (all.isEmpty()) {
                status.setText(e != null ? "Couldn't load this: " + Bg.message(e) : "Nothing here yet.");
                status.setVisibility(View.VISIBLE);
                return;
            }
            if (key.equals("home") && pages.get(0).continuation != null) { moreToken = pages.get(0).continuation; moreId = Ytm.HOME; }
            if (key.equals("home") && mp.current() != null) addResume();
            if (key.equals("home") && !all.isEmpty() && all.get(0).title.isEmpty()) all.get(0).title = "Top Picks for You";
            MusicUi.addShelves(this, content, all, key.equals("home") || key.equals("new"), (m, ctx) -> MusicUi.open(this, m, ctx));
            if (focusContent) content.post(this::focusFirst);
        });
    }

    private List<Ytm.Page> load(String key) throws Exception {
        List<Ytm.Page> out = new ArrayList<>();
        switch (key) {
            case "home": {
                Ytm.Page home = Ytm.browse(Ytm.HOME);
                out.add(home);
                // Signed out, YouTube Music's Home is short: new releases and the charts fill it out.
                int n = home.shelves.size();
                if (n < 6) {
                    java.util.Set<String> have = new java.util.HashSet<>();
                    for (Ytm.Shelf sh : home.shelves) have.add(sh.title);
                    for (String id : new String[]{Ytm.NEW, Ytm.CHARTS}) {
                        try {
                            Ytm.Page more = Ytm.browse(id);
                            Ytm.Page keep = new Ytm.Page();
                            for (Ytm.Shelf sh : more.shelves) if (!sh.title.isEmpty() && have.add(sh.title) && !sh.title.toLowerCase(java.util.Locale.US).contains("podcast")) keep.shelves.add(sh);
                            out.add(keep);
                        } catch (Exception ignored) { }
                    }
                }
                break;
            }
            case "new":
            case "videos":
                // New: new releases and the charts. Videos: the music videos from both.
                for (String id : new String[]{Ytm.NEW, Ytm.CHARTS}) {
                    try {
                        Ytm.Page pg = Ytm.browse(id), keep = new Ytm.Page();
                        for (Ytm.Shelf sh : pg.shelves) {
                            boolean vid = sh.title.toLowerCase(java.util.Locale.US).contains("video");
                            if (sh.title.toLowerCase(java.util.Locale.US).contains("podcast")) continue;
                            if (vid == key.equals("videos")) {
                                if (sh.title.isEmpty() && key.equals("new")) sh.title = "New Music";
                                keep.shelves.add(sh);
                            }
                        }
                        out.add(keep);
                    } catch (Exception e) { if (out.isEmpty() && id.equals(Ytm.CHARTS)) throw e; }
                }
                break;
            case "radio": {
                // Stations: YouTube Music's mixes for you, then moods and genres.
                Ytm.Page moods = Ytm.browse(Ytm.MOODS);
                for (Ytm.Shelf sh : moods.shelves) if (sh.title.toLowerCase(java.util.Locale.US).startsWith("moods")) sh.title = "Moods & Activities";
                out.add(moods);
                break;
            }
            case "library":
                for (String[] f : new String[][]{{Ytm.LIKED_SONGS, "Liked songs"}, {Ytm.LIB_PLAYLISTS, "Playlists"}, {Ytm.LIB_ALBUMS, "Albums"},
                        {Ytm.LIB_ARTISTS, "Artists"}, {Ytm.HISTORY, "Recently played"}}) {
                    try {
                        Ytm.Page pg = Ytm.browse(f[0]);
                        // One row per part of your library, named for it.
                        Ytm.Page one = new Ytm.Page();
                        Ytm.Shelf sh = new Ytm.Shelf();
                        sh.title = f[1];
                        for (Ytm.Shelf s : pg.shelves) for (MItem m : s.items) if (sh.items.size() < 60) sh.items.add(m);
                        sh.list = f[0].equals(Ytm.LIKED_SONGS) || f[0].equals(Ytm.HISTORY);
                        if (!sh.items.isEmpty()) one.shelves.add(sh);
                        out.add(one);
                    } catch (Exception ignored) { }
                }
                break;
        }
        return out;
    }

    private void loadMore() {
        loadingMore = true;
        final String t = moreToken, id = moreId;
        final int my = seq;
        moreToken = null;
        Bg.run(() -> Ytm.more(id, t), (pg, e) -> {
            loadingMore = false;
            if (my != seq || pg == null) return;
            MusicUi.addShelves(this, content, pg.shelves, false, (m, ctx) -> MusicUi.open(this, m, ctx));
            moreToken = pg.continuation;
        });
    }

    /** What you were listening to, first on Home. */
    private void addResume() {
        MItem cur = mp.current();
        Ytm.Shelf s = new Ytm.Shelf();
        s.title = "Continue listening";
        for (int i = Math.max(0, mp.index); i < Math.min(mp.queue.size(), mp.index + 12); i++) s.items.add(mp.queue.get(i));
        if (s.items.isEmpty()) return;
        List<Ytm.Shelf> l = new ArrayList<>();
        s.list = true;
        l.add(s);
        MusicUi.addShelves(this, content, l, false, (m, ctx) -> {
            int i = mp.queue.indexOf(m);
            if (i >= 0) mp.jump(i); else MusicUi.open(this, m, ctx);
            startActivity(MusicPlayer.nowPlaying(this));
        });
    }

    private void libraryGuest() {
        spinner.setVisibility(View.GONE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(120), 0, 0);
        TextView t = MusicUi.text(this, 28, Color.WHITE, true);
        t.setText("Your library");
        box.addView(t);
        TextView s = MusicUi.text(this, 18, MusicUi.TEXT2, false);
        s.setText("Sign in with your YouTube account to see your liked songs, playlists and albums.");
        s.setPadding(0, dp(14), 0, dp(26));
        box.addView(s);
        TextView btn = MusicUi.text(this, 18, 0xFF111111, true);
        btn.setText("Sign in");
        btn.setFocusable(true);
        btn.setPadding(dp(34), dp(14), dp(34), dp(14));
        btn.setBackground(MusicUi.round(Color.WHITE, dp(26)));
        MusicUi.lift(btn, 1.06f);
        btn.setOnClickListener(v -> {
            Intent i = new Intent(this, WelcomeActivity.class);
            i.putExtra(WelcomeActivity.MODE, WelcomeActivity.SIGN_IN);
            startActivity(i);
        });
        box.addView(btn);
        content.addView(box, new LinearLayout.LayoutParams(-1, -2));
    }

    private void focusFirst() {
        for (int i = 0; i < content.getChildCount(); i++) if (content.getChildAt(i).requestFocus()) return;
    }

    private boolean inTabs() {
        View f = getCurrentFocus();
        return f != null && f.getParent() == tabBar;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (down && e.getRepeatCount() == 0) mp.toggle();
            return true;
        }
        if (k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) { if (down) mp.next(); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PREVIOUS || k == KeyEvent.KEYCODE_MEDIA_REWIND) { if (down) mp.prev(); return true; }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                // Back from the rows goes to the tabs; from the tabs, leaves (the music keeps playing).
                if (!inTabs()) { scroll.smoothScrollTo(0, 0); for (TextView tv : tabViews) if (tab.equals(tv.getTag())) tv.requestFocus(); }
                else moveTaskToBack(true);
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_DOWN && down && inTabs()) {
            focusFirst();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP && down && !inTabs()) {
            View f = getCurrentFocus();
            View next = f == null ? null : f.focusSearch(View.FOCUS_UP);
            if (next == null || next.getParent() == tabBar || !isDescendant(content, next)) {
                scroll.smoothScrollTo(0, 0);
                for (TextView tv : tabViews) if (tab.equals(tv.getTag())) { tv.requestFocus(); return true; }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private static boolean isDescendant(View parent, View v) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == parent) return true;
        return false;
    }

    @Override protected void onResume() { super.onResume(); mp.addListener(this); paintTabs(); }
    @Override protected void onPause() { mp.removeListener(this); super.onPause(); }
    @Override public void changed() { paintTabs(); }
}
