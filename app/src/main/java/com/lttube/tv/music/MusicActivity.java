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

import com.lttube.tv.Account;
import com.lttube.tv.Bg;
import com.lttube.tv.R;
import com.lttube.tv.WelcomeActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * LT Music's main screen, laid out like the Apple Music web player: a sidebar on the left (Search, Home, New,
 * Radio, then your Library and Playlists, you at the bottom), each page under a big title, and a floating
 * Now Playing bar at the bottom. The page takes on the colour of whatever you're on.
 */
public class MusicActivity extends Activity implements MusicPlayer.Listener {
    /** The sidebar's width, and how far the page sits in from it. */
    private static final int SIDE = 196, INSET = SIDE - 18;

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private ProgressBar spinner;
    private LinearLayout side;
    private LinearLayout sideList;
    private LinearLayout playlistsBox;
    private final List<TextView> navs = new ArrayList<>();
    private FrameLayout nowBar;
    private ImageView nowArt, nowPlay;
    private TextView nowTitle, nowSub;
    private String page = "listen";
    private int seq;
    private int W, H;
    private MusicPlayer mp;
    private String tintWant;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mp = MusicPlayer.get(this);
        float d = getResources().getDisplayMetrics().density;
        W = Math.round(getResources().getDisplayMetrics().widthPixels / d) - INSET;
        H = Math.round(getResources().getDisplayMetrics().heightPixels / d);
        MusicUi.inset = INSET;
        root = new FrameLayout(this);
        root.setBackgroundColor(MusicUi.BG);
        setContentView(root);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setSmoothScrollingEnabled(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        scroll.addView(content);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -1);
        sl.leftMargin = dp(INSET);
        root.addView(scroll, sl);

        buildSide();
        buildNowBar();
        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);
        FrameLayout.LayoutParams spl = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        spl.leftMargin = dp(INSET / 2f);
        root.addView(spinner, spl);
        show("listen");
        focusNav();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    // ================================================================== sidebar
    private void buildSide() {
        side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(0xFF242426);
        ScrollView ss = new ScrollView(this);
        ss.setVerticalScrollBarEnabled(false);
        sideList = new LinearLayout(this);
        sideList.setOrientation(LinearLayout.VERTICAL);
        sideList.setPadding(dp(12), dp(MusicUi.TOP + 4), dp(12), dp(10));
        ss.addView(sideList);

        View mark = MusicUi.wordmark(this);
        mark.setPadding(dp(8), 0, 0, dp(16));
        sideList.addView(mark);
        nav("search", "Search", R.drawable.ic_search);
        nav("listen", "Home", R.drawable.ic_m_home);
        nav("browse", "New", R.drawable.ic_m_grid);
        nav("radio", "Radio", R.drawable.ic_m_radio);
        section("Library");
        nav("lib:all", "Recently Added", R.drawable.ic_history);
        nav("lib:artists", "Artists", R.drawable.ic_person);
        nav("lib:albums", "Albums", R.drawable.ic_m_album);
        nav("videos", "Music Videos", R.drawable.ic_movies);
        nav("made", "Made for You", R.drawable.ic_m_heart);
        section("Playlists");
        nav("lib:playlists", "All Playlists", R.drawable.ic_m_grid);
        playlistsBox = new LinearLayout(this);
        playlistsBox.setOrientation(LinearLayout.VERTICAL);
        sideList.addView(playlistsBox);
        loadSidePlaylists();
        side.addView(ss, new LinearLayout.LayoutParams(-1, 0, 1));

        // You, at the bottom (settings and accounts).
        LinearLayout me = navRow("Settings", 0);
        String photo = "", name = "";
        try { if (Ytm.signedIn()) { photo = Account.photo(); name = Account.name(); } } catch (Throwable ignored) { }
        ImageView pic = (ImageView) me.getChildAt(0);
        if (photo != null && !photo.isEmpty()) { pic.clearColorFilter(); MusicUi.circle(pic, photo); }
        else pic.setImageResource(R.drawable.ic_m_gear);
        if (name != null && !name.isEmpty()) ((TextView) me.getChildAt(1)).setText(name);
        me.setOnClickListener(v -> settings());
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, dp(40));
        ml.setMargins(dp(12), dp(6), dp(12), dp(16));
        side.addView(me, ml);
        View rule = new View(this);
        rule.setBackgroundColor(0x14FFFFFF);
        FrameLayout box = new FrameLayout(this);
        box.addView(side, new FrameLayout.LayoutParams(-1, -1));
        box.addView(rule, new FrameLayout.LayoutParams(1, -1, Gravity.END));
        root.addView(box, new FrameLayout.LayoutParams(dp(SIDE), -1, Gravity.START));
    }

    private void section(String title) {
        TextView t = MusicUi.medium(this, 12, MusicUi.TEXT3);
        t.setText(title);
        t.setPadding(dp(10), dp(16), 0, dp(6));
        sideList.addView(t);
    }

    private LinearLayout navRow(String label, int icon) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(10), 0, dp(10), 0);
        ImageView iv = new ImageView(this);
        if (icon != 0) iv.setImageResource(icon);
        iv.setColorFilter(MusicUi.PINK);
        r.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView t = MusicUi.line(this, 15, MusicUi.TEXT, false);
        t.setText(label);
        t.setPadding(dp(10), 0, 0, 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        r.setOnFocusChangeListener((v, has) -> paintNav((LinearLayout) v));
        paintNav(r);
        return r;
    }

    private void nav(String key, String label, int icon) {
        LinearLayout r = navRow(label, icon);
        TextView t = (TextView) r.getChildAt(1);
        t.setTag(key);
        navs.add(t);
        r.setTag(key);
        r.setOnClickListener(v -> {
            if (key.equals("search")) { startActivity(new Intent(this, MusicSearchActivity.class)); return; }
            if (!key.equals(page)) show(key);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(38));
        lp.bottomMargin = dp(2);
        sideList.addView(r, lp);
    }

    /** Selected: a soft grey behind it. On it: white, with dark text. */
    private void paintNav(LinearLayout r) {
        boolean f = r.hasFocus() || r.isFocused(), sel = page.equals(r.getTag());
        r.setBackground(MusicUi.round(f ? 0xF2FFFFFF : sel ? 0x1AFFFFFF : 0, dp(8)));
        ((TextView) r.getChildAt(1)).setTextColor(f ? 0xFF1C1C1E : MusicUi.TEXT);
        ImageView iv = (ImageView) r.getChildAt(0);
        if (iv.getColorFilter() != null) iv.setColorFilter(f ? 0xFFE0245E : MusicUi.PINK);
    }

    private void paintNavs() {
        for (int i = 0; i < sideList.getChildCount(); i++) if (sideList.getChildAt(i) instanceof LinearLayout && sideList.getChildAt(i).isFocusable()) paintNav((LinearLayout) sideList.getChildAt(i));
        for (int i = 0; i < playlistsBox.getChildCount(); i++) paintNav((LinearLayout) playlistsBox.getChildAt(i));
    }

    /** Your playlists listed under "Playlists": Liked Songs, the ones you imported, your YouTube ones. */
    private void loadSidePlaylists() {
        Bg.run(() -> {
            List<MItem> l = new ArrayList<>();
            if (Ytm.signedIn()) {
                MItem liked = new MItem();
                liked.type = MItem.PLAYLIST;
                liked.title = "Liked Songs";
                liked.browseId = "VLLM";
                l.add(liked);
            }
            l.addAll(Imports.local(this));
            if (Ytm.signedIn()) try {
                for (Ytm.Shelf s : Ytm.browse(Ytm.LIB_PLAYLISTS).shelves) for (MItem m : s.items) {
                    if (m.title.equalsIgnoreCase("New playlist") || "VLLM".equals(m.browseId) || m.title.equalsIgnoreCase("Liked Music")) continue;
                    l.add(m);
                }
            } catch (Exception ignored) { }
            Mixes.cards(this).stream().filter(m -> m.browseId.equals(Mixes.HEAVY)).findFirst().ifPresent(l::add);
            return l;
        }, (l, e) -> {
            if (l == null) return;
            for (int i = 0; i < Math.min(8, l.size()); i++) {
                final MItem m = l.get(i);
                LinearLayout r = navRow(m.title, "VLLM".equals(m.browseId) ? R.drawable.ic_m_heart : R.drawable.ic_m_queue);
                r.setOnClickListener(v -> MusicUi.open(this, m, null));
                r.setOnKeyListener((v, k, ev) -> {
                    if (k == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_UP) { MusicUi.options(this, m); return true; }
                    return false;
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(38));
                lp.bottomMargin = dp(2);
                playlistsBox.addView(r, lp);
            }
        });
    }

    private void focusNav() {
        for (int i = 0; i < sideList.getChildCount(); i++) {
            View v = sideList.getChildAt(i);
            if (page.equals(v.getTag())) { v.requestFocus(); return; }
        }
    }

    // ================================================================== now playing bar
    /** A floating rounded bar at the bottom: what's playing, like the web player's. */
    private void buildNowBar() {
        nowBar = new FrameLayout(this);
        nowBar.setBackground(MusicUi.round(0xE62C2C2E, dp(30)));
        nowBar.setElevation(dp(10));
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.CENTER_VERTICAL);
        in.setPadding(dp(8), 0, dp(18), 0);
        nowArt = new ImageView(this);
        in.addView(nowArt, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(12), 0, dp(12), 0);
        nowTitle = MusicUi.line(this, 14, MusicUi.TEXT, false);
        nowSub = MusicUi.line(this, 12, MusicUi.TEXT2, false);
        tx.addView(nowTitle);
        tx.addView(nowSub);
        in.addView(tx, new LinearLayout.LayoutParams(0, -2, 1));
        nowPlay = new ImageView(this);
        nowPlay.setColorFilter(MusicUi.TEXT);
        in.addView(nowPlay, new LinearLayout.LayoutParams(dp(22), dp(22)));
        nowBar.addView(in, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(Math.min(380, W - 60)), dp(52), Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = dp(INSET + (W - Math.min(380, W - 60)) / 2f);
        lp.bottomMargin = dp(18);
        root.addView(nowBar, lp);
        paintNow();
    }

    private void paintNow() {
        if (nowBar == null) return;
        MItem c = mp.current();
        nowBar.setVisibility(c == null ? View.GONE : View.VISIBLE);
        if (c == null) return;
        MusicUi.art(nowArt, c.thumb, dp(5));
        nowTitle.setText(c.title);
        nowSub.setText(c.artist.isEmpty() ? c.subtitle : c.artist);
        nowPlay.setImageResource(mp.playing() ? R.drawable.ic_m_pause : R.drawable.ic_m_play);
    }

    /** The page takes on a deep shade of the cover you're on. */
    private void tintFrom(String thumb) {
        if (thumb == null || thumb.isEmpty()) return;
        final String want = thumb;
        tintWant = want;
        MusicUi.colorOf(this, thumb, c -> {
            if (!want.equals(tintWant)) return;
            MusicUi.fadeTo(root, MusicUi.blend(MusicUi.tintOf(c), MusicUi.BG, 0.45f));
        });
    }

    private void hover(MItem m) { if (m != null) tintFrom(m.thumb); }

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
        paintNavs();
        seq++;
        content.removeAllViews();
        scroll.scrollTo(0, 0);
        spinner.setVisibility(View.GONE);
        if (mp.current() != null) tintFrom(mp.current().thumb);
        if (key.startsWith("lib:")) { library(key.substring(4)); return; }
        switch (key) {
            case "browse": browse(); break;
            case "videos": videos(); break;
            case "radio": radio(); break;
            case "made": made(); break;
            default: listenNow(); break;
        }
    }

    /** A page's big title (34 on the web player). */
    private View title(String t) {
        TextView v = MusicUi.text(this, 32, MusicUi.TEXT, true);
        v.setText(t);
        v.setPadding(dp(MusicUi.PAD), dp(MusicUi.TOP + 14), dp(MusicUi.PAD), dp(2));
        return v;
    }

    /** Made for You: your mixes as tall cards, then stations. */
    private void made() {
        content.addView(title("Made for You"));
        List<MItem> made = Mixes.cards(this);
        if (made.isEmpty()) { content.addView(note("Play some music and your mixes will show up here.")); return; }
        List<Object[]> mp2 = new ArrayList<>();
        for (MItem m : made) mp2.add(new Object[]{m.browseId.equals(Mixes.HEAVY) ? "Heavy Rotation" : "Made For You", m});
        content.addView(MusicUi.heading(this, "Playlists Made for You"));
        content.addView(MusicUi.pickRow(this, mp2, opener(), this::hover));
        List<MItem> st = stations();
        if (!st.isEmpty()) MusicUi.addRow(this, content, "Stations for You", st, 138, opener(), this::hover);
        content.addView(spacer(80));
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

    private MusicUi.Opener opener() { return (m, ctx) -> MusicUi.open(this, m, ctx); }

    // ---------------------------------------------------------------- Listen Now
    private void listenNow() {
        content.addView(title("Home"));
        final int my = seq;
        spinner.setVisibility(View.VISIBLE);
        Bg.run(() -> {
            List<Object[]> picks = new ArrayList<>();   // {label, item}
            List<Ytm.Shelf> browse = new ArrayList<>();
            try { browse = AppleWeb.browse(); } catch (Exception ignored) { }
            // Top Picks: from what you play (Heavy Rotation, Made For You, an artist you love, their newest
            // release), then the catalogue's newest.
            List<MItem> made = Mixes.cards(this);
            for (MItem m : made) if (m.browseId.equals(Mixes.HEAVY)) picks.add(new Object[]{"Heavy Rotation", m});
            List<MItem> newFor = new ArrayList<>(), fansLike = new ArrayList<>();
            String fansOf = "";
            List<MItem> top = Mixes.topArtists(this, 6);
            int n = 0;
            for (MItem a : top) {
                try {
                    String id = AppleMusic.artistId(a.title);
                    if (id == null) continue;
                    Ytm.Page ap = AppleWeb.artist(id);
                    String name = ap.title.isEmpty() ? a.title : ap.title;
                    MItem latest = null;
                    for (Ytm.Shelf s : ap.shelves) {
                        String t = s.title.toLowerCase(java.util.Locale.US);
                        if (!s.list && (t.contains("latest") || t.contains("album") || t.contains("single")) && !s.items.isEmpty() && latest == null) latest = s.items.get(0);
                        if (fansLike.isEmpty() && t.contains("similar")) { fansLike.addAll(s.items); fansOf = name; }
                    }
                    if (latest != null) {
                        if (latest.artist.isEmpty()) latest.artist = name;
                        newFor.add(latest);
                    }
                    if (n < 2) {
                        if (latest != null) picks.add(new Object[]{"New Release", latest});
                        MItem art = new MItem();
                        art.type = MItem.ARTIST;
                        art.browseId = "AMA:" + id;
                        art.title = name + " & Similar Artists";
                        art.subtitle = "Artist";
                        art.thumb = ap.thumb;
                        picks.add(new Object[]{"Featuring " + name, art});
                    }
                    n++;
                } catch (Exception ignored) { }
            }
            for (MItem m : made) if (!m.browseId.equals(Mixes.HEAVY)) picks.add(new Object[]{"Made For You", m});
            for (Ytm.Shelf s : browse) {
                if (picks.size() >= 10) break;
                String label = s.title.toLowerCase(java.util.Locale.US).contains("playlist") ? "Updated Playlist" : "New Release";
                for (MItem m : s.items) if (!m.playable()) { picks.add(new Object[]{label, m}); break; }
            }
            List<Ytm.Shelf> charts = new ArrayList<>();
            try { charts = AppleWeb.charts(); } catch (Exception ignored) { }
            return new Object[]{picks, browse, charts, newFor, fansLike, fansOf};
        }, (r, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            if (r == null) { content.addView(note("Couldn't load this" + (e == null ? "" : ": " + Bg.message(e)))); return; }
            @SuppressWarnings("unchecked") List<Object[]> picks = (List<Object[]>) r[0];
            @SuppressWarnings("unchecked") List<Ytm.Shelf> browse = (List<Ytm.Shelf>) r[1];
            @SuppressWarnings("unchecked") List<Ytm.Shelf> charts = (List<Ytm.Shelf>) r[2];
            @SuppressWarnings("unchecked") List<MItem> newFor = (List<MItem>) r[3];
            @SuppressWarnings("unchecked") List<MItem> fansLike = (List<MItem>) r[4];
            String fansOf = (String) r[5];
            if (!picks.isEmpty()) {
                content.addView(MusicUi.heading(this, "Top Picks for You"));
                content.addView(MusicUi.pickRow(this, picks, opener(), this::hover));
            }
            List<MItem> places = Mixes.places(this);
            if (!places.isEmpty()) MusicUi.addRow(this, content, "Recently Played", places.subList(0, Math.min(14, places.size())), 138, opener(), this::hover);
            if (!newFor.isEmpty()) MusicUi.addRow(this, content, "New Releases for You", newFor, 138, opener(), this::hover);
            List<MItem> made = Mixes.cards(this);
            if (!made.isEmpty()) {
                List<Object[]> mp = new ArrayList<>();
                for (MItem m : made) mp.add(new Object[]{"Made For You", m});
                content.addView(MusicUi.heading(this, "Playlists Made for You"));
                content.addView(MusicUi.pickRow(this, mp, opener(), this::hover));
            }
            List<MItem> stations = stations();
            if (!stations.isEmpty()) MusicUi.addRow(this, content, "Stations for You", stations, 138, opener(), this::hover);
            if (!fansLike.isEmpty()) MusicUi.addRow(this, content, fansOf + " Fans Like", fansLike, 112, opener(), this::hover);
            for (Ytm.Shelf c : charts) if (c.list) { while (c.items.size() > 20) c.items.remove(c.items.size() - 1); }
            MusicUi.addShelves(this, content, charts, opener(), this::hover);
            MusicUi.addShelves(this, content, browse, opener(), this::hover);
            content.addView(spacer(40));
        });
    }

    /** A station for each artist you play most (their songs and songs like them). */
    private List<MItem> stations() {
        List<MItem> l = new ArrayList<>();
        for (Mixes.Entry e : topSongsByArtist()) {
            MItem st = new MItem();
            st.type = MItem.PLAYLIST;
            st.title = e.item.artist + " Station";
            st.subtitle = "Radio";
            st.playlistId = "RDAMVM" + e.item.videoId;
            st.thumb = e.item.thumb;
            l.add(st);
        }
        return l;
    }

    // ---------------------------------------------------------------- Browse
    private void browse() {
        content.addView(title("New"));
        final int my = seq;
        spinner.setVisibility(View.VISIBLE);
        Bg.run(() -> {
            List<Ytm.Shelf> l = new ArrayList<>(AppleWeb.browse());
            try { l.addAll(AppleWeb.charts()); } catch (Exception ignored) { }
            return l;
        }, (l, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            if (l == null) { content.addView(note("Couldn't load this: " + Bg.message(e))); return; }
            MusicUi.addShelves(this, content, l, opener(), this::hover);
            Ytm.Shelf g = new Ytm.Shelf();
            g.title = "Genres";
            g.items.addAll(AppleWeb.genres());
            List<Ytm.Shelf> gl = new ArrayList<>();
            gl.add(g);
            MusicUi.addShelves(this, content, gl, opener(), this::hover);
            tilePictures(g.items);
            content.addView(spacer(40));
        });
    }

    // ---------------------------------------------------------------- Videos
    private void videos() {
        content.addView(title("Music Videos"));
        final int my = seq;
        spinner.setVisibility(View.VISIBLE);
        Bg.run(() -> {
            List<Ytm.Shelf> l = new ArrayList<>();
            for (String id : new String[]{Ytm.NEW, Ytm.CHARTS}) {
                try { for (Ytm.Shelf s : Ytm.browse(id).shelves) if (s.title.toLowerCase(java.util.Locale.US).contains("video")) l.add(s); } catch (Exception ignored) { }
            }
            return l;
        }, (l, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            if (l == null || l.isEmpty()) { content.addView(note("No music videos right now.")); return; }
            MusicUi.addShelves(this, content, l, opener(), this::hover);
            content.addView(spacer(40));
        });
    }

    // ---------------------------------------------------------------- Radio
    private void radio() {
        content.addView(title("Radio"));
        // Stations: one for each artist you play most (their songs and songs like them), then by genre.
        List<MItem> stations = stations();
        if (!stations.isEmpty()) MusicUi.addRow(this, content, "Stations for You", stations, 138, opener(), this::hover);
        List<MItem> g = AppleWeb.genres();
        MusicUi.addTiles(this, content, "Stations by Genre", g, opener());
        tilePictures(g);
        content.addView(spacer(40));
    }

    private List<Mixes.Entry> topSongsByArtist() {
        java.util.Map<String, Mixes.Entry> best = new java.util.LinkedHashMap<>();
        List<Mixes.Entry> h = Mixes.history(this);
        h.sort((a, b) -> b.n - a.n);
        for (Mixes.Entry e : h) if (!e.item.videoId.isEmpty() && !e.item.artist.isEmpty() && !best.containsKey(e.item.artist) && best.size() < 10) best.put(e.item.artist, e);
        return new ArrayList<>(best.values());
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
        content.addView(title(which.equals("artists") ? "Artists" : which.equals("albums") ? "Albums" : which.equals("playlists") ? "Playlists" : "Recently Added"));
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
        View chosen = null;
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

    }

    /** Library grid: six across, wrapping. Liked Songs is a gradient square with a heart. */
    private void grid(LinearLayout into, List<MItem> items) {
        int cols = 5, gap = 16, size = (W - MusicUi.PAD * 2 - gap * (cols - 1)) / cols;
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
    private boolean inSide() {
        View f = getCurrentFocus();
        for (Object p = f; p instanceof View; p = ((View) p).getParent()) if (p == side) return true;
        return false;
    }

    private static View firstFocusable(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        if (v.isFocusable() && !(v instanceof android.widget.HorizontalScrollView)) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) { View r = firstFocusable(((ViewGroup) v).getChildAt(i)); if (r != null) return r; }
        return null;
    }

    /** True when the item you're on is the first in its row (nothing more to the left). */
    private boolean atLeftEdge() {
        View f = getCurrentFocus();
        if (f == null) return true;
        View next = f.focusSearch(View.FOCUS_LEFT);
        if (next == null) return true;
        for (Object p = next; p instanceof View; p = ((View) p).getParent()) if (p == side) return true;
        int[] a = new int[2], b = new int[2];
        f.getLocationOnScreen(a);
        next.getLocationOnScreen(b);
        return Math.abs((a[1] + f.getHeight() / 2) - (b[1] + next.getHeight() / 2)) > f.getHeight() / 2;
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
                if (!inSide()) { scroll.smoothScrollTo(0, 0); focusNav(); } else moveTaskToBack(true);
            }
            return true;
        }
        if (inSide()) {
            if (down && k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                View first = firstFocusable(content);
                if (first != null) first.requestFocus();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (down && k == KeyEvent.KEYCODE_DPAD_LEFT && atLeftEdge()) { focusNav(); return true; }
        if (down && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
            boolean up = k == KeyEvent.KEYCODE_DPAD_UP;
            if (MusicUi.moveVertical(content, getCurrentFocus(), up)) return true;
            if (up) scroll.smoothScrollTo(0, 0);
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() { super.onResume(); MusicUi.inset = INSET; mp.addListener(this); paintNow(); }
    @Override protected void onPause() { mp.removeListener(this); MusicUi.inset = 0; super.onPause(); }
    @Override public void changed() { paintNow(); }
}
