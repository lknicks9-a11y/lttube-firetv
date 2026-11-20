package com.lttube.tv.music;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.lttube.tv.Account;
import com.lttube.tv.Bg;
import com.lttube.tv.R;
import com.lttube.tv.WelcomeActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * LT Music's main screen. A bar along the top (what's playing on the left; Home, Search and Your Library in the
 * middle; settings and your picture on the right) over one of three pages:
 * Home — a big picture of whatever you're on with its name, chips, quick picks and rows;
 * Search — a search bar and colour tiles to browse;
 * Your Library — chips and a grid of your playlists, artists and albums.
 */
public class MusicActivity extends Activity implements MusicPlayer.Listener {
    private FrameLayout root;
    private Hero hero;
    private View scrim;
    private ScrollView scroll;
    private LinearLayout content;
    private ProgressBar spinner;
    private FrameLayout bar;
    private LinearLayout nowPill;
    private ImageView nowArt;
    private TextView nowTitle, nowArtist;
    private final List<TextView> tabViews = new ArrayList<>();
    private String page = "home";
    private int seq;
    private int W, H;
    private MusicPlayer mp;

    // home
    private TextView heroTitle, heroSub;
    private LinearLayout homeRows;
    private final List<TextView> homeChips = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mp = MusicPlayer.get(this);
        float d = getResources().getDisplayMetrics().density;
        W = Math.round(getResources().getDisplayMetrics().widthPixels / d);
        H = Math.round(getResources().getDisplayMetrics().heightPixels / d);
        root = new FrameLayout(this);
        root.setBackground(MusicUi.background());
        setContentView(root);

        // ---- the big picture behind Home's header (kept whole, melting into the background)
        hero = new Hero(this);
        root.addView(hero, new FrameLayout.LayoutParams(-1, dp(Math.round(H * 0.72f)), Gravity.TOP));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        scroll.addView(content);
        root.addView(scroll, MusicUi.match());
        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
            float t = Math.min(1f, scroll.getScrollY() / (float) dp(H * 0.35f));
            hero.setAlpha(page.equals("home") ? 1f - t : 0f);
        });

        buildBar();
        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        show("home");
        tabViews.get(0).requestFocus();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    // ================================================================== top bar
    private void buildBar() {
        // A soft dark band behind the bar so rows scrolling up never run into it.
        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{MusicUi.BG, MusicUi.BG & 0xCCFFFFFF, MusicUi.BG & 0x00FFFFFF}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, dp(100), Gravity.TOP));

        bar = new FrameLayout(this);
        bar.setPadding(dp(MusicUi.PAD), dp(MusicUi.TOP), dp(MusicUi.PAD), 0);

        // what's playing
        nowPill = new LinearLayout(this);
        nowPill.setGravity(Gravity.CENTER_VERTICAL);
        nowPill.setFocusable(true);
        nowPill.setPadding(dp(4), dp(4), dp(12), dp(4));
        nowPill.setBackground(MusicUi.round(0x33FFFFFF, dp(6)));
        nowArt = new ImageView(this);
        nowPill.addView(nowArt, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        nt.setPadding(dp(10), 0, 0, 0);
        nowTitle = MusicUi.line(this, 14, Color.WHITE, true);
        nowArtist = MusicUi.line(this, 12, MusicUi.TEXT2, false);
        nt.addView(nowTitle);
        nowArtist.setPadding(0, dp(2), 0, 0);
        nt.addView(nowArtist);
        nowPill.addView(nt, new LinearLayout.LayoutParams(0, -2, 1));
        nowPill.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = MusicUi.round(has ? 0x59FFFFFF : 0x33FFFFFF, dp(6));
            if (has) g.setStroke(dp(2), Color.WHITE);
            v.setBackground(g);
        });
        nowPill.setOnClickListener(v -> startActivity(MusicPlayer.nowPlaying(this)));
        bar.addView(nowPill, new FrameLayout.LayoutParams(dp(Math.round(W * 0.21f)), dp(44), Gravity.START | Gravity.TOP));

        // Home · Search · Your Library
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.addView(tab("home", R.drawable.ic_m_home, "Home"));
        tabs.addView(tab("search", R.drawable.ic_search, "Search"));
        tabs.addView(tab("library", R.drawable.ic_m_library, "Your Library"));
        bar.addView(tabs, new FrameLayout.LayoutParams(-2, dp(44), Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        // settings and you
        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        ImageView gear = MusicUi.iconButton(this, R.drawable.ic_m_gear, 38);
        gear.setOnClickListener(v -> settings());
        right.addView(gear, new LinearLayout.LayoutParams(dp(38), dp(38)));
        String photo = "";
        try { photo = Ytm.signedIn() ? Account.photo() : ""; } catch (Throwable ignored) { }
        if (photo != null && !photo.isEmpty()) {
            ImageView me = new ImageView(this);
            me.setFocusable(true);
            MusicUi.circle(me, photo);
            MusicUi.outline(me, 0, true, 1.1f);
            me.setOnClickListener(v -> settings());
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(dp(38), dp(38));
            ml.leftMargin = dp(14);
            right.addView(me, ml);
        }
        bar.addView(right, new FrameLayout.LayoutParams(-2, dp(44), Gravity.END | Gravity.TOP));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        paintNow();
    }

    private TextView tab(String key, int icon, String label) {
        TextView t = MusicUi.text(this, 16, 0xD9FFFFFF, true);
        t.setText(label);
        t.setTag(key);
        t.setFocusable(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), 0, dp(16), 0);
        Drawable d = getDrawable(icon).mutate();
        d.setBounds(0, 0, dp(20), dp(20));
        t.setCompoundDrawables(d, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
        t.setOnFocusChangeListener((v, has) -> paintTabs());
        t.setOnClickListener(v -> { if (!key.equals(page)) show(key); });
        tabViews.add(t);
        t.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(40)));
        return t;
    }

    private void paintTabs() {
        for (TextView t : tabViews) {
            boolean f = t.hasFocus(), sel = page.equals(t.getTag());
            GradientDrawable g = MusicUi.round(f ? 0x40FFFFFF : 0, dp(20));
            if (f) g.setStroke(dp(2), Color.WHITE);
            t.setBackground(g);
            int c = f || sel ? Color.WHITE : 0xA6FFFFFF;
            t.setTextColor(c);
            t.getCompoundDrawables()[0].setTint(c);
        }
    }

    /** The page takes on a deep shade of the cover you're on (or of what's playing). */
    private void tintFrom(String thumb) {
        if (thumb == null || thumb.isEmpty()) return;
        final String want = thumb;
        tintWant = want;
        MusicUi.colorOf(this, thumb, c -> {
            if (!want.equals(tintWant)) return;
            MusicUi.fadeTo(root, MusicUi.tintOf(c), t -> {
                hero.setTint(t);
                scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{t, t & 0xCCFFFFFF, t & 0x00FFFFFF}));
            });
        });
    }

    private String tintWant;

    /** Worth showing: albums, artists, YouTube Music's own playlists and mixes, official songs — not random uploads. */
    private static boolean curated(MItem m) {
        if (m.playable()) return m.official();
        if (MItem.PLAYLIST.equals(m.type)) {
            String b = m.browseId, s = m.subtitle.toLowerCase(java.util.Locale.US);
            return b.startsWith("VLRDCLAK") || b.startsWith("RDCLAK") || m.playlistId.startsWith("RD") || s.contains("youtube music") || b.startsWith("MIX:");
        }
        return true;
    }

    private void paintNow() {
        MItem cur = mp.current();
        nowPill.setVisibility(cur == null ? View.INVISIBLE : View.VISIBLE);
        nowPill.setFocusable(cur != null);
        if (cur == null) return;
        if (!cur.videoId.equals(nowArt.getTag())) { MusicUi.art(nowArt, cur.thumb, dp(4)); nowArt.setTag(cur.videoId); }
        nowTitle.setText(cur.title);
        nowArtist.setText(cur.artist);
    }

    private void settings() {
        boolean in = Ytm.signedIn();
        String who = "";
        try { who = in ? Account.name() : ""; } catch (Throwable ignored) { }
        MusicUi.choose(this, in ? "Signed in as " + who : "Settings",
                new String[]{in ? "Switch account" : "Sign in with YouTube", "Import a playlist from Spotify or Apple Music", mp.autoplay ? "Autoplay: on" : "Autoplay: off"},
                new Runnable[]{this::signIn, () -> startActivity(new Intent(this, ImportActivity.class)), () -> mp.setAutoplay(!mp.autoplay)});
    }

    private void signIn() {
        Intent i = new Intent(this, WelcomeActivity.class);
        i.putExtra(WelcomeActivity.MODE, WelcomeActivity.SIGN_IN);
        startActivity(i);
    }

    // ================================================================== pages
    private void show(String key) {
        page = key;
        paintTabs();
        seq++;
        content.removeAllViews();
        homeChips.clear();
        heroTitle = null;
        scroll.scrollTo(0, 0);
        hero.setAlpha(key.equals("home") ? 1f : 0f);
        if (!key.equals("home") && mp.current() != null) tintFrom(mp.current().thumb);
        spinner.setVisibility(View.GONE);
        switch (key) {
            case "home": home(); break;
            case "search": search(); break;
            default: library("all"); break;
        }
    }

    private View spacer(int hDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(hDp)));
        return v;
    }

    private TextView note(String s) {
        TextView t = MusicUi.text(this, 17, MusicUi.TEXT2, false);
        t.setText(s);
        t.setPadding(dp(MusicUi.PAD), dp(30), dp(MusicUi.PAD), 0);
        return t;
    }

    // ---------------------------------------------------------------- Home
    private void home() {
        // Header: the name of what you're on, big, over its picture.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.BOTTOM);
        head.setPadding(dp(MusicUi.PAD), dp(84), dp(MusicUi.PAD), dp(6));
        heroTitle = MusicUi.text(this, 34, Color.WHITE, true);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroSub = MusicUi.line(this, 15, 0xE6FFFFFF, true);
        heroSub.setPadding(0, dp(8), 0, 0);
        int tw = Math.round(W * 0.50f);
        head.addView(heroTitle, new LinearLayout.LayoutParams(dp(tw), -2));
        head.addView(heroSub, new LinearLayout.LayoutParams(dp(tw), -2));
        content.addView(head, new LinearLayout.LayoutParams(-1, dp(Math.round(H * 0.46f))));

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(MusicUi.PAD), dp(12), dp(MusicUi.PAD), dp(4));
        chips.setClipChildren(false);
        String[][] cs = {{"all", "All"}, {"new", "New Releases"}, {"charts", "Charts"}, {"moods", "Moods & Genres"}};
        for (String[] c : cs) {
            TextView t = MusicUi.chip(this, c[1], c[0].equals("all"));
            t.setOnClickListener(v -> { for (TextView o : homeChips) MusicUi.setChosen(o, o == v); homeRows(c[0]); });
            homeChips.add(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36));
            lp.rightMargin = dp(12);
            chips.addView(t, lp);
        }
        content.addView(chips);
        homeRows = new LinearLayout(this);
        homeRows.setOrientation(LinearLayout.VERTICAL);
        homeRows.setClipChildren(false);
        homeRows.setPadding(0, 0, 0, dp(50));
        content.addView(homeRows);
        MItem cur = mp.current();
        if (cur != null) hover(cur); else { heroTitle.setText("Good to see you"); heroSub.setText("Music picked for you"); }
        homeRows("all");
    }

    private void hover(MItem m) {
        if (!"home".equals(page) || heroTitle == null) return;
        heroTitle.setText(m.title);
        String sub = MusicUi.kindLine(m);
        String low = sub.toLowerCase(java.util.Locale.US);
        if (MItem.ALBUM.equals(m.type) && !low.startsWith("album") && !low.startsWith("single") && !low.startsWith("ep")) sub = "Album • " + sub;
        if (MItem.PLAYLIST.equals(m.type) && !low.startsWith("playlist")) sub = "Playlist • " + sub;
        if (m.playable()) sub = "Song • " + m.artist + (m.album.isEmpty() ? "" : " • " + m.album);
        heroSub.setText(sub);
        if (m.thumb != null && !m.thumb.isEmpty()) hero.set(MItem.art(m.thumb, 1080), m.thumb.contains("i.ytimg.com"));
        tintFrom(m.thumb);
    }

    private void homeRows(String which) {
        final int my = ++seq;
        homeRows.removeAllViews();
        spinner.setVisibility(View.VISIBLE);
        final boolean in = Ytm.signedIn();
        Bg.run(() -> {
            List<Object> out = new ArrayList<>();
            switch (which) {
                case "new": {
                    // New releases: Apple Music's shelves of new albums.
                    Ytm.Page p = new Ytm.Page();
                    for (Ytm.Shelf sh : AppleWeb.browse()) {
                        int albums = 0;
                        for (MItem m : sh.items) if (MItem.ALBUM.equals(m.type)) albums++;
                        if (albums > sh.items.size() / 2) p.shelves.add(sh);
                    }
                    out.add(p);
                    break;
                }
                case "charts": {
                    Ytm.Page p = new Ytm.Page();
                    p.shelves.addAll(AppleWeb.charts());
                    out.add(p);
                    break;
                }
                case "moods": {
                    Ytm.Page p = new Ytm.Page();
                    Ytm.Shelf g = new Ytm.Shelf();
                    g.title = "Genres";
                    g.items.addAll(AppleWeb.genres());
                    p.shelves.add(g);
                    out.add(p);
                    break;
                }
                default: {
                    List<Ytm.Shelf> browse = new ArrayList<>();
                    try { browse = AppleWeb.browse(); } catch (Exception ignored) { }
                    // Quick picks: Apple Music's playlists.
                    List<MItem> quick = new ArrayList<>();
                    for (Ytm.Shelf sh : browse) for (MItem m : sh.items) if (MItem.PLAYLIST.equals(m.type) && quick.size() < 8 && !quick.contains(m)) quick.add(m);
                    out.add(quick);
                    // Made for you (from what you play here), and more from the artists you play most.
                    Ytm.Page mine = new Ytm.Page();
                    List<MItem> made = Mixes.cards(this);
                    if (!made.isEmpty()) { Ytm.Shelf ms = new Ytm.Shelf(); ms.title = "Made for You"; ms.items.addAll(made); mine.shelves.add(ms); }
                    out.add(mine);
                    Ytm.Page rest = new Ytm.Page();
                    try {
                        for (Ytm.Shelf c : AppleWeb.charts()) {
                            if (c.list) { while (c.items.size() > 20) c.items.remove(c.items.size() - 1); }
                            rest.shelves.add(c);
                        }
                    } catch (Exception ignored) { }
                    rest.shelves.addAll(browse);
                    for (MItem a : Mixes.topArtists(this, 2)) {
                        try {
                            String id = AppleMusic.artistId(a.title);
                            if (id == null) continue;
                            AppleMusic.Discography d = AppleMusic.discography(id);
                            if (d.albums.isEmpty()) continue;
                            Ytm.Shelf sh = new Ytm.Shelf();
                            sh.title = "More from " + a.title;
                            sh.items.addAll(d.albums.subList(0, Math.min(12, d.albums.size())));
                            rest.shelves.add(sh);
                        } catch (Exception ignored) { }
                    }
                    if (rest.shelves.isEmpty() && browse.isEmpty()) throw new Exception("Couldn't reach the music catalogue");
                    out.add(rest);
                }
            }
            return out;
        }, (out, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            if (out == null || out.isEmpty()) { homeRows.addView(note("Couldn't load this" + (e == null ? "" : ": " + Bg.message(e)))); return; }
            MusicUi.Opener open = (m, ctx) -> MusicUi.open(this, m, ctx);
            for (Object o : out) {
                if (o instanceof List) {
                    @SuppressWarnings("unchecked") List<MItem> quick = (List<MItem>) o;
                    if (!quick.isEmpty()) homeRows.addView(quickGrid(quick, open));
                    // Jump back in: the albums, playlists, artists and mixes you were last in.
                    List<MItem> places = Mixes.places(this);
                    if (!places.isEmpty()) MusicUi.addRow(this, homeRows, "Jump back in", places.subList(0, Math.min(14, places.size())), 136, open, this::hover);
                } else MusicUi.addShelves(this, homeRows, ((Ytm.Page) o).shelves, open, this::hover);
            }
        });
    }

    /** Eight quick picks in two rows of four. */
    private View quickGrid(List<MItem> items, MusicUi.Opener open) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(MusicUi.PAD), dp(14), dp(MusicUi.PAD), dp(4));
        grid.setClipChildren(false);
        int gap = 14, w = (W - MusicUi.PAD * 2 - gap * 3) / 4;
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = dp(12);
                grid.addView(row, rl);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(52));
            if (i % 4 != 3) lp.rightMargin = dp(gap);
            row.addView(MusicUi.tile(this, items.get(i), dp(w), dp(52), items, open, this::hover), lp);
        }
        return grid;
    }

    // ---------------------------------------------------------------- Search
    private void search() {
        content.addView(spacer(80));
        TextView box = MusicUi.text(this, 17, 0xB3FFFFFF, false);
        box.setText("What do you want to play?");
        box.setFocusable(true);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        Drawable s = getDrawable(R.drawable.ic_search).mutate();
        s.setBounds(0, 0, dp(20), dp(20));
        s.setTint(Color.WHITE);
        box.setCompoundDrawables(s, null, null, null);
        box.setCompoundDrawablePadding(dp(12));
        GradientDrawable g0 = MusicUi.round(0x26FFFFFF, dp(26));
        g0.setStroke(dp(1), 0x59FFFFFF);
        box.setBackground(g0);
        box.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = MusicUi.round(has ? 0x40FFFFFF : 0x26FFFFFF, dp(26));
            g.setStroke(dp(has ? 3 : 1), has ? Color.WHITE : 0x59FFFFFF);
            v.setBackground(g);
        });
        box.setOnClickListener(v -> startActivity(new Intent(this, MusicSearchActivity.class)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(50));
        bl.setMargins(dp(MusicUi.PAD), dp(4), dp(MusicUi.PAD), dp(6));
        content.addView(box, bl);
        final int my = seq;
        spinner.setVisibility(View.VISIBLE);
        List<MItem> all = AppleWeb.genres();
        spinner.setVisibility(View.GONE);
        MusicUi.addTiles(this, content, "Browse all", all, (m, ctx) -> MusicUi.open(this, m, ctx));
        content.addView(spacer(40));
        tilePictures(all);
    }

    /** Each browse tile gets a picture from inside it, tilted into its corner. */
    private void tilePictures(List<MItem> tiles) {
        final int my = seq;
        List<ImageView> pics = new ArrayList<>();
        collectPics(content, pics);
        for (int i = 0; i < Math.min(Math.min(tiles.size(), pics.size()), 24); i++) {
            final MItem m = tiles.get(i);
            final ImageView pic = pics.get(i);
            Bg.run(() -> AppleWeb.genreCover(m.browseId), (url, e) -> { if (my == seq && url != null && !url.isEmpty()) MusicUi.art(pic, url, dp(4)); });
        }
    }

    private void collectPics(View v, List<ImageView> out) {
        if (v instanceof FrameLayout && v.getTag() instanceof ImageView) { out.add((ImageView) v.getTag()); return; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) collectPics(((ViewGroup) v).getChildAt(i), out);
    }

    // ---------------------------------------------------------------- Your Library
    private void library(String which) {
        content.removeAllViews();
        content.addView(spacer(78));
        if (!Ytm.signedIn()) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(MusicUi.PAD), dp(30), dp(MusicUi.PAD), 0);
            TextView t = MusicUi.text(this, 28, Color.WHITE, true);
            t.setText("Your Library");
            box.addView(t);
            TextView s = MusicUi.text(this, 17, MusicUi.TEXT2, false);
            s.setText("Sign in with your YouTube account to see your liked songs, playlists, artists and albums.");
            s.setPadding(0, dp(12), 0, dp(22));
            box.addView(s, new LinearLayout.LayoutParams(dp(Math.round(W * 0.6f)), -2));
            LinearLayout bb = new LinearLayout(this);
            bb.setClipChildren(false);
            TextView in = MusicUi.pill(this, 0, "Sign in");
            in.setOnClickListener(v -> signIn());
            bb.addView(in, new LinearLayout.LayoutParams(-2, dp(46)));
            TextView imp = MusicUi.pill(this, R.drawable.ic_m_add, "Import a playlist");
            imp.setOnClickListener(v -> startActivity(new Intent(this, ImportActivity.class)));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-2, dp(46));
            il.leftMargin = dp(14);
            bb.addView(imp, il);
            box.addView(bb);
            content.addView(box);
            List<MItem> mine = Imports.local(this);
            if (!mine.isEmpty()) { content.addView(MusicUi.heading(this, "On this TV")); grid(content, mine); }
            return;
        }
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(MusicUi.PAD), dp(8), dp(MusicUi.PAD), dp(6));
        String[][] cs = {{"all", "All"}, {"playlists", "Playlists"}, {"artists", "Artists"}, {"albums", "Albums"}};
        View chosen = null;
        for (String[] c : cs) {
            TextView t = MusicUi.chip(this, c[1], c[0].equals(which));
            t.setOnClickListener(v -> { if (!c[0].equals(which)) library(c[0]); });
            if (c[0].equals(which)) chosen = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36));
            lp.rightMargin = dp(12);
            chips.addView(t, lp);
        }
        TextView imp = MusicUi.pill(this, R.drawable.ic_m_add, "Import playlist");
        imp.setOnClickListener(v -> startActivity(new Intent(this, ImportActivity.class)));
        chips.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        chips.addView(imp, new LinearLayout.LayoutParams(-2, dp(36)));
        chips.setClipChildren(false);
        content.addView(chips);
        final View focusChip = chosen;
        final LinearLayout gridBox = new LinearLayout(this);
        gridBox.setOrientation(LinearLayout.VERTICAL);
        gridBox.setClipChildren(false);
        content.addView(gridBox);
        final int my = ++seq;
        spinner.setVisibility(View.VISIBLE);
        Bg.run(() -> {
            List<MItem> l = new ArrayList<>();
            if (which.equals("all") || which.equals("playlists")) {
                MItem liked = new MItem();
                liked.type = MItem.PLAYLIST;
                liked.title = "Liked Songs";
                liked.subtitle = "Playlist • Your likes";
                liked.browseId = "VLLM";
                l.add(liked);
            }
            if (which.equals("all") || which.equals("playlists")) l.addAll(Imports.local(this));
            String[] feeds = which.equals("playlists") ? new String[]{Ytm.LIB_PLAYLISTS} : which.equals("artists") ? new String[]{Ytm.LIB_ARTISTS}
                    : which.equals("albums") ? new String[]{Ytm.LIB_ALBUMS} : new String[]{Ytm.LIB_PLAYLISTS, Ytm.LIB_ARTISTS, Ytm.LIB_ALBUMS};
            for (String f : feeds) try {
                for (Ytm.Shelf s : Ytm.browse(f).shelves) for (MItem m : s.items) {
                    if (m.title.equalsIgnoreCase("New playlist") || "VLLM".equals(m.browseId) || m.title.equalsIgnoreCase("Liked Music")) continue;
                    if (f.equals(Ytm.LIB_ARTISTS)) m.type = MItem.ARTIST;
                    l.add(m);
                }
            } catch (Exception ignored) { }
            return l;
        }, (l, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            if (l == null || l.isEmpty()) { gridBox.addView(note("Nothing here yet.")); return; }
            grid(gridBox, l);
        });
        if (focusChip != null && !inBar()) focusChip.post(focusChip::requestFocus);
    }

    /** Library grid: six across, wrapping. Liked Songs is a gradient square with a heart. */
    private void grid(LinearLayout into, List<MItem> items) {
        int cols = 6, gap = 18, size = (W - MusicUi.PAD * 2 - gap * (cols - 1)) / cols;
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(dp(MusicUi.PAD), dp(14), dp(MusicUi.PAD), dp(50));
        g.setClipChildren(false);
        LinearLayout row = null;
        MusicUi.Opener open = (m, ctx) -> MusicUi.open(this, m, ctx);
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = dp(18);
                g.addView(row, rl);
            }
            MItem m = items.get(i);
            View card = MusicUi.card(this, m, dp(size), items, open, null);
            if ("VLLM".equals(m.browseId)) {
                ImageView iv = (ImageView) ((LinearLayout) card).getChildAt(0);
                GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4A2BE0, 0xFF8B7CF6, 0xFFC9E8F2});
                gd.setCornerRadius(dp(6));
                iv.setBackground(gd);
                iv.setImageResource(R.drawable.ic_m_heart);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int p = dp(size * 0.3f);
                iv.setPadding(p, p, p, p);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), -2);
            if (i % cols != cols - 1) lp.rightMargin = dp(gap);
            row.addView(card, lp);
        }
        into.addView(g);
    }

    // ================================================================== remote
    private boolean inBar() {
        View f = getCurrentFocus();
        for (Object p = f; p instanceof View; p = ((View) p).getParent()) if (p == bar) return true;
        return false;
    }

    private void focusTab() { for (TextView t : tabViews) if (page.equals(t.getTag())) t.requestFocus(); }

    private static View firstFocusable(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v.isFocusable()) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View r = firstFocusable(((ViewGroup) v).getChildAt(i)); if (r != null) return r; }
        return null;
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
                // Back from the page goes up to the bar; from the bar, LT Music goes away (and the music stops).
                if (!inBar()) { scroll.smoothScrollTo(0, 0); focusTab(); } else moveTaskToBack(true);
            }
            return true;
        }
        if (down && k == KeyEvent.KEYCODE_DPAD_DOWN && inBar()) {
            View first = firstFocusable(content);
            if (first != null) first.requestFocus();
            return true;
        }
        if (down && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) && !inBar()) {
            // One row at a time, landing on the item nearest across; past the top row, up to the bar.
            boolean up = k == KeyEvent.KEYCODE_DPAD_UP;
            if (MusicUi.moveVertical(content, getCurrentFocus(), up)) return true;
            if (up) { scroll.smoothScrollTo(0, 0); focusTab(); }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() { super.onResume(); mp.addListener(this); paintNow(); }
    @Override protected void onPause() { mp.removeListener(this); super.onPause(); }
    @Override public void changed() { paintNow(); }
}
