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
 * LT Music's main screen, laid out like Music on Apple TV: your picture on the left, a pill of tabs in the middle
 * (Listen Now, Browse, Videos, Radio, Library, Now Playing, search). Listen Now opens with Top Picks — big cards,
 * each with a small line above it saying why it's there — then Recently Played and more rows. The background takes
 * on the colour of whatever you're on.
 */
public class MusicActivity extends Activity implements MusicPlayer.Listener {
    private static final String[][] TABS = {{"listen", "Listen Now"}, {"browse", "Browse"}, {"videos", "Videos"}, {"radio", "Radio"},
            {"library", "Library"}, {"now", "Now Playing"}};

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private ProgressBar spinner;
    private FrameLayout bar;
    private LinearLayout tabBar;
    private View scrim;
    private final List<TextView> tabViews = new ArrayList<>();
    private ImageView searchTab;
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
        W = Math.round(getResources().getDisplayMetrics().widthPixels / d);
        H = Math.round(getResources().getDisplayMetrics().heightPixels / d);
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
        root.addView(scroll, MusicUi.match());

        buildBar();
        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        show("listen");
        tabViews.get(0).requestFocus();
        // Update Center: looks for a newer LT Music in the background (asks before installing anything).
        com.lttube.tv.Updater.check(this, false, null);
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    // ================================================================== top bar
    private void buildBar() {
        // A faint shade at the very top, so the glass reads against bright covers.
        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x59000000, 0x00000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, dp(70), Gravity.TOP));
        bar = new FrameLayout(this);
        // Even on both sides, so the tab pill sits exactly in the middle of the screen.
        bar.setPadding(dp(MusicUi.PAD - 16), dp(MusicUi.TOP), dp(MusicUi.PAD - 16), 0);

        // You (your picture; settings and accounts behind it).
        ImageView me = new ImageView(this);
        me.setFocusable(true);
        String photo = "";
        try { photo = Ytm.signedIn() ? Account.photo() : ""; } catch (Throwable ignored) { }
        if (photo != null && !photo.isEmpty()) MusicUi.circle(me, photo);
        else {
            me.setImageResource(R.drawable.ic_m_note);
            int p = dp(9);
            me.setPadding(p, p, p, p);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF6A88, 0xFFB43CFF});
            g.setShape(GradientDrawable.OVAL);
            me.setBackground(g);
        }
        MusicUi.outline(me, 0, true, 1f);
        me.setOnClickListener(v -> settings());
        bar.addView(me, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.START | Gravity.TOP));

        // The tabs, in a pill of liquid glass (the page slides by, blurred, underneath it).
        tabBar = new LinearLayout(this);
        tabBar.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(5);
        tabBar.setPadding(p, p, p, p);
        Glass glass = new Glass(this, scroll, root, dp(24));
        glass.addView(tabBar, new FrameLayout.LayoutParams(-2, -2));
        for (String[] t : TABS) {
            final String key = t[0];
            TextView tv = MusicUi.text(this, 16, 0xE6FFFFFF, false);
            tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            tv.setText(t[1]);
            tv.setTag(key);
            tv.setFocusable(true);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(18), 0, dp(18), 0);
            tv.setOnFocusChangeListener((v, has) -> paintTabs());
            tv.setOnClickListener(v -> {
                if (key.equals("now")) { if (mp.current() != null) startActivity(MusicPlayer.nowPlaying(this)); return; }
                if (!key.equals(page)) show(key);
            });
            tabViews.add(tv);
            tabBar.addView(tv, new LinearLayout.LayoutParams(-2, dp(38)));
        }
        searchTab = new ImageView(this);
        searchTab.setImageResource(R.drawable.ic_search);
        searchTab.setFocusable(true);
        searchTab.setPadding(dp(14), dp(9), dp(14), dp(9));
        searchTab.setColorFilter(0xE6FFFFFF);
        searchTab.setOnFocusChangeListener((v, has) -> paintTabs());
        searchTab.setOnClickListener(v -> startActivity(new Intent(this, MusicSearchActivity.class)));
        tabBar.addView(searchTab, new LinearLayout.LayoutParams(dp(52), dp(38)));
        bar.addView(glass, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        paintTabs();
    }

    private void paintTabs() {
        for (TextView t : tabViews) {
            boolean f = t.hasFocus(), sel = page.equals(t.getTag());
            // Selected: a soft grey pill; on it: white with dark text.
            // On it: white. Selected: a brighter bubble of glass with a soft rim.
            GradientDrawable g = MusicUi.round(f ? Color.WHITE : sel ? 0x33FFFFFF : 0, dp(19));
            if (sel && !f) g.setStroke(Math.max(1, dp(0.75f)), 0x40FFFFFF);
            t.setBackground(g);
            t.setTextColor(f ? 0xFF1C1C1E : Color.WHITE);
        }
        boolean sf = searchTab.hasFocus();
        searchTab.setBackground(MusicUi.round(sf ? Color.WHITE : 0, dp(19)));
        searchTab.setColorFilter(sf ? 0xFF1C1C1E : 0xE6FFFFFF);
    }

    private void paintNow() { }

    /** The page takes on a deep shade of the cover you're on. */
    private void tintFrom(String thumb) {
        if (thumb == null || thumb.isEmpty()) return;
        final String want = thumb;
        tintWant = want;
        MusicUi.colorOf(this, thumb, c -> {
            if (!want.equals(tintWant)) return;
            MusicUi.fadeTo(root, MusicUi.tintOf(c));
        });
    }

    private void hover(MItem m) { if (m != null) tintFrom(m.thumb); }

    private void settings() {
        boolean in = Ytm.signedIn();
        String who = "";
        try { who = in ? Account.name() : ""; } catch (Throwable ignored) { }
        MusicUi.choose(this, in ? "Signed in as " + who : "Settings",
                new String[]{in ? "Switch account" : "Sign in with YouTube", "Import a playlist from Spotify or Apple Music", mp.autoplay ? "Autoplay: on" : "Autoplay: off",
                        "Update Center \u2014 " + (com.lttube.tv.Updater.updateReady(this) ? "install build " + com.lttube.tv.Updater.latest.build : "you have build " + com.lttube.tv.Updater.currentBuild(this))},
                new Runnable[]{this::signIn, () -> startActivity(new Intent(this, ImportActivity.class)), () -> mp.setAutoplay(!mp.autoplay),
                        () -> { if (com.lttube.tv.Updater.updateReady(this)) com.lttube.tv.Updater.install(this, com.lttube.tv.Updater.latest); else com.lttube.tv.Updater.check(this, true, null); }});
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
        scroll.scrollTo(0, 0);
        spinner.setVisibility(View.GONE);
        if (mp.current() != null) tintFrom(mp.current().thumb);
        switch (key) {
            case "browse": browse(); break;
            case "videos": videos(); break;
            case "radio": radio(); break;
            case "library": library("all"); break;
            default: listenNow(); break;
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

    private MusicUi.Opener opener() { return (m, ctx) -> MusicUi.open(this, m, ctx); }

    // ---------------------------------------------------------------- Listen Now
    private void listenNow() {
        content.addView(spacer(76));
        final int my = seq;
        // Home shows at once from last time, while a fresh one loads for next time; the first time it loads here.
        Object[] cached = homeFromCache();
        if (cached != null) {
            renderHome(cached);
            Bg.run(this::loadHome, (r, e) -> { if (r != null) saveHome(r); });
            return;
        }
        spinner.setVisibility(View.VISIBLE);
        Bg.run(this::loadHome, (r, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            if (r == null) { content.addView(note("Couldn't load this" + (e == null ? "" : ": " + Bg.message(e)))); return; }
            saveHome(r);
            renderHome(r);
        });
    }

    /** Everything Home needs, fetched side by side (not one after another). {picks, browse, charts, newFor, fansLike, fansOf} */
    private Object[] loadHome() throws Exception {
        java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            java.util.concurrent.Future<List<Ytm.Shelf>> fBrowse = ex.submit(AppleWeb::browse);
            java.util.concurrent.Future<List<Ytm.Shelf>> fCharts = ex.submit(AppleWeb::charts);
            List<MItem> top = Mixes.topArtists(this, 5);
            List<java.util.concurrent.Future<Object[]>> fArtists = new ArrayList<>();
            for (MItem a : top) fArtists.add(ex.submit(() -> {
                String id = AppleMusic.artistId(a.title);
                return id == null ? null : new Object[]{id, AppleWeb.artist(id), a.title};
            }));
            List<Object[]> picks = new ArrayList<>();   // {label, item}
            List<MItem> made = Mixes.cards(this);
            for (MItem m : made) if (m.browseId.equals(Mixes.HEAVY)) picks.add(new Object[]{"Heavy Rotation", m});
            List<MItem> newFor = new ArrayList<>(), fansLike = new ArrayList<>();
            String fansOf = "";
            int n = 0;
            for (java.util.concurrent.Future<Object[]> f : fArtists) {
                Object[] r;
                try { r = f.get(12, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { continue; }
                if (r == null) continue;
                String id = (String) r[0];
                Ytm.Page ap = (Ytm.Page) r[1];
                String name = ap.title.isEmpty() ? (String) r[2] : ap.title;
                MItem latest = null;
                for (Ytm.Shelf s : ap.shelves) {
                    String t = s.title.toLowerCase(java.util.Locale.US);
                    if (!s.list && (t.contains("latest") || t.contains("album") || t.contains("single")) && !s.items.isEmpty() && latest == null) latest = s.items.get(0);
                    if (fansLike.isEmpty() && t.contains("similar")) { fansLike.addAll(s.items); fansOf = name; }
                }
                if (latest != null) { if (latest.artist.isEmpty()) latest.artist = name; newFor.add(latest); }
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
            }
            for (MItem m : made) if (!m.browseId.equals(Mixes.HEAVY)) picks.add(new Object[]{"Made For You", m});
            List<Ytm.Shelf> browse = new ArrayList<>(), charts = new ArrayList<>();
            try { browse = fBrowse.get(15, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
            try { charts = fCharts.get(10, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) { }
            for (Ytm.Shelf s : browse) {
                if (picks.size() >= 10) break;
                String label = s.title.toLowerCase(java.util.Locale.US).contains("playlist") ? "Updated Playlist" : "New Release";
                for (MItem m : s.items) if (!m.playable()) { picks.add(new Object[]{label, m}); break; }
            }
            if (browse.isEmpty() && charts.isEmpty() && picks.isEmpty()) throw new Exception("No connection");
            return new Object[]{picks, browse, charts, newFor, fansLike, fansOf};
        } finally { ex.shutdownNow(); }
    }

    // ---- Home kept on the TV between visits
    private static org.json.JSONArray itemsJson(List<MItem> l) { org.json.JSONArray a = new org.json.JSONArray(); for (MItem m : l) a.put(m.toJson()); return a; }

    private static List<MItem> itemsOf(org.json.JSONArray a) { List<MItem> l = new ArrayList<>(); if (a != null) for (int i = 0; i < a.length(); i++) l.add(MItem.fromJson(a.optJSONObject(i))); return l; }

    private static org.json.JSONArray shelvesJson(List<Ytm.Shelf> l) throws org.json.JSONException {
        org.json.JSONArray a = new org.json.JSONArray();
        for (Ytm.Shelf s : l) a.put(new org.json.JSONObject().put("t", s.title).put("l", s.list).put("m", s.moreId).put("i", itemsJson(s.items)));
        return a;
    }

    private static List<Ytm.Shelf> shelvesOf(org.json.JSONArray a) {
        List<Ytm.Shelf> l = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject o = a.optJSONObject(i);
            Ytm.Shelf s = new Ytm.Shelf();
            s.title = o.optString("t");
            s.list = o.optBoolean("l");
            s.moreId = o.optString("m");
            s.items.addAll(itemsOf(o.optJSONArray("i")));
            l.add(s);
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    private void saveHome(Object[] r) {
        try {
            org.json.JSONArray picks = new org.json.JSONArray();
            for (Object[] p : (List<Object[]>) r[0]) picks.put(new org.json.JSONObject().put("l", p[0]).put("i", ((MItem) p[1]).toJson()));
            org.json.JSONObject o = new org.json.JSONObject().put("at", System.currentTimeMillis()).put("picks", picks)
                    .put("browse", shelvesJson((List<Ytm.Shelf>) r[1])).put("charts", shelvesJson((List<Ytm.Shelf>) r[2]))
                    .put("newFor", itemsJson((List<MItem>) r[3])).put("fans", itemsJson((List<MItem>) r[4])).put("fansOf", r[5]);
            getSharedPreferences("music", MODE_PRIVATE).edit().putString("home", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    private Object[] homeFromCache() {
        try {
            org.json.JSONObject o = new org.json.JSONObject(getSharedPreferences("music", MODE_PRIVATE).getString("home", "{}"));
            if (System.currentTimeMillis() - o.optLong("at") > 2 * 24 * 3600_000L) return null;
            List<Object[]> picks = new ArrayList<>();
            org.json.JSONArray pa = o.optJSONArray("picks");
            if (pa != null) for (int i = 0; i < pa.length(); i++) picks.add(new Object[]{pa.optJSONObject(i).optString("l"), MItem.fromJson(pa.optJSONObject(i).optJSONObject("i"))});
            // Your own mixes always as they are now.
            List<Object[]> fresh = new ArrayList<>();
            for (Object[] p : picks) if (!((MItem) p[1]).browseId.startsWith("MIX:")) fresh.add(p);
            List<MItem> made = Mixes.cards(this);
            for (MItem m : made) if (m.browseId.equals(Mixes.HEAVY)) fresh.add(0, new Object[]{"Heavy Rotation", m});
            for (MItem m : made) if (!m.browseId.equals(Mixes.HEAVY)) fresh.add(Math.min(fresh.size(), 5), new Object[]{"Made For You", m});
            return new Object[]{fresh, shelvesOf(o.optJSONArray("browse")), shelvesOf(o.optJSONArray("charts")),
                    itemsOf(o.optJSONArray("newFor")), itemsOf(o.optJSONArray("fans")), o.optString("fansOf")};
        } catch (Exception e) { return null; }
    }

    private void renderHome(Object[] r) {
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
        content.addView(spacer(80));
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
        content.addView(spacer(80));
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
        content.addView(spacer(80));
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
        if (v.isFocusable() && !(v instanceof android.widget.HorizontalScrollView)) return v;
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
            boolean up = k == KeyEvent.KEYCODE_DPAD_UP;
            if (MusicUi.moveVertical(content, getCurrentFocus(), up)) return true;
            if (up) { scroll.smoothScrollTo(0, 0); focusTab(); }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() { super.onResume(); com.lttube.tv.Updater.resumePending(this); mp.addListener(this); }
    @Override protected void onPause() { mp.removeListener(this); super.onPause(); }
    @Override public void changed() { }
}
