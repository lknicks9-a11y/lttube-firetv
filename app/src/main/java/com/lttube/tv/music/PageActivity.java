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
    private MItem place;
    /** Apple gave the artwork's own colour: use it rather than one guessed from the picture. */
    private boolean exactColor;

    private void exact(int c) {
        if (c == 0) return;
        exactColor = true;
        MusicUi.fadeTo(root, MusicUi.pageColor(c), t -> hero.setTint(t));
    }
    private Motion motion;

    @Override protected void onPause() { super.onPause(); hero.pauseMotion(); if (motion != null) motion.pause(); }
    @Override protected void onResume() { super.onResume(); hero.resumeMotion(); if (motion != null) motion.resume(); }

    /** You played something from this album / playlist: it goes in Recently Played. */
    private void played() {
        if (place == null || MItem.ARTIST.equals(item.type) || item.browseId.startsWith("AMA:") || item.browseId.startsWith("UC")) return;
        Mixes.visited(this, place);
    }

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
        // The page takes on a deep shade of the album / artist picture.
        MusicUi.colorOf(this, item.thumb, c -> { if (!exactColor) MusicUi.fadeTo(root, MusicUi.pageColor(c), t -> hero.setTint(t)); });

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
        Bg.run(() -> item.browseId.startsWith("LOCAL:") ? Imports.page(this, item.browseId)
                : item.browseId.startsWith("MIX:") ? Mixes.page(this, item.browseId)
                : item.browseId.startsWith("AM:") ? AppleMusic.album(item.browseId)
                : item.browseId.startsWith("AMA:") ? AppleWeb.artist(item.browseId.substring(4))
                : item.browseId.startsWith("AMP:") ? AppleWeb.playlist(item.browseId.substring(4))
                : item.browseId.startsWith("AMG:") ? AppleWeb.genre(item.browseId)
                : item.browseId.startsWith("AMN:") || item.browseId.startsWith("UC") ? appleArtistByName(item)
                : item.browseId.startsWith("AMALL:") ? appleAll(item.browseId) : Ytm.browse(item.browseId, item.params), (pg, e) -> {
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

    /** An artist reached from a song (by name): their Apple Music page. */
    private Ytm.Page appleArtistByName(MItem a) throws Exception {
        String name = a.browseId.startsWith("AMN:") ? a.browseId.substring(4) : a.title;
        String id = name.isEmpty() ? null : AppleMusic.artistId(name);
        if (id == null) return Ytm.browse(a.browseId.startsWith("UC") ? a.browseId : "", null);
        item.browseId = "AMA:" + id;
        item.type = MItem.ARTIST;
        return AppleWeb.artist(id);
    }

    /** "See all" of an artist's albums, singles & EPs, appears on, or songs. */
    private static Ytm.Page appleAll(String id) throws Exception {
        String[] p = id.split(":");
        return AppleWeb.seeAll(p[1], p.length > 2 ? p[2] : "albums");
    }

    private void show(Ytm.Page pg) {
        if (MItem.MORE.equals(item.type)) { all(pg); return; }
        // Remembered for Home's "Jump back in" (with its picture and name as the page has them).
        MItem seen = MItem.fromJson(item.toJson());
        if (seen.thumb.isEmpty()) seen.thumb = pg.thumb;
        if (seen.title.isEmpty()) seen.title = pg.title;
        if (seen.subtitle.isEmpty()) seen.subtitle = pg.subtitle;
        // It goes in Recently Played only once you play something from it (opening it isn't enough).
        place = seen;
        boolean artist = MItem.ARTIST.equals(item.type) || item.browseId.startsWith("UC");
        // An artist with motion artwork: it plays across the whole top (their photo shows until it starts).
        // Without it, their photo sits in a circle instead (below), on the page's colour.
        boolean moving = artist && !pg.motion.isEmpty();
        if (artist && !moving) hero.setVisibility(View.GONE);
        else if (!pg.banner.isEmpty()) hero.set(pg.banner, true);
        else if (!pg.thumb.isEmpty()) hero.set(MItem.art(pg.thumb, 1080), false);
        if (moving) hero.setMotion(pg.motion);
        exact(pg.bgColor);
        // Only official releases (the songs on Spotify / Apple Music) — no uploads or clips.
        final List<MItem> songs = new ArrayList<>();
        for (MItem m : pg.songs()) if (m.official()) songs.add(m);
        final String pl = !pg.playlistId.isEmpty() ? pg.playlistId : item.playlistId;

        // ---- header
        final String title = pg.title.isEmpty() ? item.title : pg.title;
        View play;
        if (artist) {
            // An artist: their name and Play at the bottom left, out of the way of the motion artwork (or, with
            // only a photo, under their photo in a circle on the page's colour).
            FrameLayout headBox = new FrameLayout(this);
            if (!moving) {
                String ph = !pg.thumb.isEmpty() ? pg.thumb : item.thumb;
                int cs = Math.round(H * 0.36f);
                View glow = new View(this);
                GradientDrawable gg = new GradientDrawable();
                gg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
                gg.setGradientRadius(dp(cs * 0.95f));
                gg.setColors(new int[]{0x2EFFFFFF, 0x00FFFFFF});
                glow.setBackground(gg);
                FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(dp(cs * 2), dp(cs * 2), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                gl.topMargin = dp(MusicUi.TOP + 26 - cs / 2f);
                headBox.addView(glow, gl);
                ImageView circle = new ImageView(this);
                circle.setElevation(dp(14));
                circle.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
                GradientDrawable ring = new GradientDrawable();
                ring.setShape(GradientDrawable.OVAL);
                ring.setColor(0x33000000);
                circle.setBackground(ring);
                MusicUi.circle(circle, MItem.art(ph, 800).replace("bb.jpg", "cc.jpg"));
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(dp(cs), dp(cs), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                cl.topMargin = dp(MusicUi.TOP + 26);
                headBox.addView(circle, cl);
            }
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.VERTICAL);
            head.setGravity(Gravity.BOTTOM | Gravity.START);
            head.setPadding(dp(MusicUi.PAD), 0, dp(MusicUi.PAD), dp(14));
            TextView t = MusicUi.text(this, 40, Color.WHITE, true);
            t.setText(title);
            t.setShadowLayer(dp(10), 0, dp(1), 0x66000000);
            t.setGravity(Gravity.START);
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            head.addView(t, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout btns = new LinearLayout(this);
            btns.setGravity(Gravity.CENTER_VERTICAL);
            btns.setPadding(0, dp(14), 0, 0);
            btns.setClipChildren(false);
            ImageView shuf = MusicUi.iconButton(this, R.drawable.ic_m_shuffle, 40);
            shuf.setOnClickListener(v -> { if (!songs.isEmpty()) { played(); MusicPlayer.get(this).shuffleList(songs, pl); startActivity(MusicPlayer.nowPlaying(this)); } });
            btns.addView(shuf, new LinearLayout.LayoutParams(dp(40), dp(40)));
            ImageView big = new ImageView(this);
            big.setImageResource(R.drawable.ic_m_play);
            big.setColorFilter(0xFF000000);
            big.setFocusable(true);
            int bp = dp(16);
            big.setPadding(bp + dp(2), bp, bp - dp(2), bp);
            paintBig(big, false);
            big.setOnFocusChangeListener((v, has) -> paintBig((ImageView) v, has));
            big.setOnClickListener(v -> startPlay(songs, pl));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(58), dp(58));
            bl.setMargins(dp(16), 0, dp(16), 0);
            btns.addView(big, bl);
            play = big;
            if (!item.browseId.startsWith("AMA:")) {
                final TextView fol = MusicUi.pill(this, 0, "Follow");
                final String cid = pg.channelId.isEmpty() ? item.browseId : pg.channelId;
                fol.setOnClickListener(v -> {
                    if (!Ytm.signedIn()) { Toast.makeText(this, "Sign in (Your Library) to follow artists", Toast.LENGTH_SHORT).show(); return; }
                    followingNow = !followingNow;
                    fol.setText(followingNow ? "Following" : "Follow");
                    final boolean on = followingNow;
                    Bg.run(() -> { Ytm.follow(cid, on); return true; }, (ok, e) -> { if (e != null) { followingNow = !on; fol.setText(!on ? "Following" : "Follow"); } });
                });
                btns.addView(fol, new LinearLayout.LayoutParams(-2, dp(40)));
            } else {
                View sp = new View(this);
                btns.addView(sp, new LinearLayout.LayoutParams(dp(40), dp(40)));
            }
            head.addView(btns, new LinearLayout.LayoutParams(-2, -2));
            headBox.addView(head, new FrameLayout.LayoutParams(-1, -1));
            content.addView(headBox, new LinearLayout.LayoutParams(-1, dp(Math.round(H * (moving ? 0.66f : 0.62f)))));
        } else {
            // An album or playlist: the cover on the left; beside it the name, who it's by, a line about it, and
            // Shuffle / Play under them. No big picture behind: the page takes on the cover's colour.
            hero.setVisibility(View.GONE);
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.BOTTOM);
            head.setPadding(dp(MusicUi.PAD), dp(MusicUi.TOP + 18), dp(MusicUi.PAD), dp(18));
            int cs = 200;
            ImageView cover = new ImageView(this);
            String th = !pg.thumb.isEmpty() ? pg.thumb : item.thumb;
            MusicUi.art(cover, MItem.art(th, 600), dp(8));
            // The cover, with its motion artwork playing over it if it has one.
            FrameLayout coverBox = new FrameLayout(this);
            coverBox.setElevation(dp(6));
            coverBox.setClipToOutline(true);
            coverBox.setBackground(MusicUi.round(MusicUi.EMPTY, dp(8)));
            coverBox.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            final Motion mo = new Motion(this);
            coverBox.addView(mo, new FrameLayout.LayoutParams(-1, -1));
            motion = mo;
            // The motion starts a moment after the page is up, small (it's only a cover), so it never holds anything up.
            if (!pg.motion.isEmpty()) mo.postDelayed(() -> { if (!isFinishing()) mo.play(pg.motion, 540, 540); }, 900);
            else if (item.browseId.startsWith("AM:")) {
                final String aid = item.browseId.substring(3);
                Bg.run(() -> AppleWeb.albumLook(aid), (u, e) -> {
                    if (u == null || isFinishing()) return;
                    if (!((String) u[0]).isEmpty()) mo.postDelayed(() -> { if (!isFinishing()) mo.play((String) u[0], 540, 540); }, 600);
                    exact((Integer) u[1]);
                });
            }
            head.addView(coverBox, new LinearLayout.LayoutParams(dp(cs), dp(cs)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.BOTTOM);
            info.setPadding(dp(26), 0, 0, 0);
            info.setClipChildren(false);
            TextView t = MusicUi.text(this, 28, MusicUi.TEXT, true);
            t.setText(title);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(t);
            String by = !item.artist.isEmpty() ? item.artist : pg.subtitle;
            if (!by.isEmpty()) {
                TextView s = MusicUi.line(this, 24, MusicUi.TEXT, false);
                s.setText(by);
                s.setPadding(0, dp(2), 0, 0);
                info.addView(s);
            }
            String meta = by.equals(pg.subtitle) ? "" : pg.subtitle;
            if (!meta.isEmpty()) {
                TextView m = MusicUi.medium(this, 13, MusicUi.TEXT2);
                m.setText(meta.toUpperCase(java.util.Locale.US));
                m.setMaxLines(1);
                m.setEllipsize(TextUtils.TruncateAt.END);
                m.setPadding(0, dp(6), 0, 0);
                info.addView(m);
            }
            if (!pg.description.isEmpty()) {
                TextView ds = MusicUi.text(this, 14, MusicUi.TEXT2, false);
                ds.setText(pg.description);
                ds.setMaxLines(2);
                ds.setEllipsize(TextUtils.TruncateAt.END);
                ds.setLineSpacing(0, 1.15f);
                ds.setPadding(0, dp(10), 0, 0);
                info.addView(ds, new LinearLayout.LayoutParams(dp(Math.round(W * 0.42f)), -2));
            }
            LinearLayout btns = new LinearLayout(this);
            btns.setGravity(Gravity.CENTER_VERTICAL);
            btns.setPadding(0, dp(16), 0, 0);
            btns.setClipChildren(false);
            ImageView shuf = MusicUi.iconButton(this, R.drawable.ic_m_shuffle, 40);
            shuf.setOnClickListener(v -> { if (!songs.isEmpty()) { played(); MusicPlayer.get(this).shuffleList(songs, pl); startActivity(MusicPlayer.nowPlaying(this)); } });
            btns.addView(shuf, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView pp = MusicUi.playPill(this, "Play");
            pp.setOnClickListener(v -> startPlay(songs, pl));
            LinearLayout.LayoutParams pl2 = new LinearLayout.LayoutParams(-2, dp(40));
            pl2.leftMargin = dp(12);
            btns.addView(pp, pl2);
            play = pp;
            if (MusicUi.canDelete(item)) {
                // Your own playlist: a Delete button (asks first).
                ImageView del = MusicUi.iconButton(this, R.drawable.ic_more, 40);
                del.setOnClickListener(v -> MusicUi.choose(this, title, new String[]{"Delete playlist"},
                        new Runnable[]{() -> MusicUi.confirmDelete(this, item, this::finish)}));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(40), dp(40));
                dl.leftMargin = dp(12);
                btns.addView(del, dl);
            }
            info.addView(btns);
            head.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            content.addView(head);
        }

        // ---- songs (album / playlist): a full-width list
        boolean list = !artist && !MItem.CATEGORY.equals(item.type) && !songs.isEmpty();
        MusicUi.Opener open = (m, ctx) -> MusicUi.open(this, m, ctx);
        if (list) {
            boolean numbered = MItem.ALBUM.equals(item.type) || item.browseId.startsWith("MPRE") || item.browseId.startsWith("AM:");
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(dp(MusicUi.PAD - 12), dp(4), dp(MusicUi.PAD - 12), dp(20));
            l.addView(MusicUi.songHeader(this, numbered), new LinearLayout.LayoutParams(-1, dp(32)));
            View rule = new View(this);
            rule.setBackgroundColor(0x1AFFFFFF);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, 1);
            rl.setMargins(dp(12), 0, dp(16), dp(2));
            l.addView(rule, rl);
            for (int i = 0; i < songs.size(); i++) {
                final int at = i;
                final MItem m = songs.get(i);
                View r = MusicUi.songRow(this, m, numbered ? i + 1 : -1, true, !numbered);
                r.setOnClickListener(v -> { played(); MusicPlayer.get(this).playList(songs, at, pl); startActivity(MusicPlayer.nowPlaying(this)); });
                r.setOnKeyListener((v, k, e) -> {
                    if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { MusicUi.options(this, m); return true; }
                    return false;
                });
                l.addView(r, new LinearLayout.LayoutParams(-1, dp(numbered ? 54 : 58)));
            }
            if (numbered || songs.size() > 1) {
                int secs = 0;
                for (MItem m : songs) secs += m.seconds;
                TextView foot = MusicUi.text(this, 14, MusicUi.TEXT2, false);
                foot.setText(songs.size() + (songs.size() == 1 ? " song" : " songs") + (secs > 0 ? ", " + Math.max(1, secs / 60) + " minutes" : ""));
                foot.setPadding(dp(12), dp(16), 0, 0);
                l.addView(foot);
            }
            content.addView(l);
            // Everything under the list (More by…, You might also like) sits on the plain charcoal.
            List<Ytm.Shelf> rest = new ArrayList<>();
            for (Ytm.Shelf s : pg.shelves) if (!s.list) rest.add(s);
            if (!rest.isEmpty()) {
                LinearLayout lower = new LinearLayout(this);
                lower.setOrientation(LinearLayout.VERTICAL);
                lower.setClipChildren(false);
                lower.setBackgroundColor(MusicUi.BG);
                lower.setPadding(0, dp(8), 0, dp(60));
                lower.setMinimumHeight(dp(H));
                MusicUi.addShelves(this, lower, rest, open, null);
                content.addView(lower);
                content.setPadding(0, 0, 0, 0);
            }
        } else if (artist) {
            // Popular: the artist's top songs, numbered, one per row.
            List<Ytm.Shelf> rest = new ArrayList<>(), restAll = new ArrayList<>();
            boolean done = false;
            for (Ytm.Shelf s : pg.shelves) {
                String st = s.title.toLowerCase(java.util.Locale.US);
                if (!done && s.list && !s.items.isEmpty()) {
                    done = true;
                    List<MItem> off = new ArrayList<>();
                    for (MItem m : s.items) if (m.official()) off.add(m);
                    s.items.clear();
                    s.items.addAll(off);
                    // Top Songs: three to a column, columns side by side (a cover, the song, its album).
                    final List<MItem> top = new ArrayList<>(s.items.subList(0, Math.min(s.items.size(), 18)));
                    for (MItem m : top) if (!m.album.isEmpty()) m.subtitle = m.album;
                    MusicUi.addSongs(this, content, "Top Songs", top, open, null, 3);
                    if (!s.moreId.isEmpty()) {
                        // Every song of theirs (official releases only), not just the top few.
                        final MItem allSongs = new MItem();
                        allSongs.type = s.moreId.startsWith("AMALL:") ? MItem.MORE : MItem.PLAYLIST;
                        allSongs.title = "All songs";
                        allSongs.browseId = s.moreId;
                        allSongs.params = s.moreParams;
                        allSongs.thumb = item.thumb;
                        TextView more = MusicUi.pill(this, R.drawable.ic_m_queue, "All songs");
                        more.setOnClickListener(v -> MusicUi.open(this, allSongs, null));
                        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, dp(42));
                        ml.setMargins(dp(MusicUi.PAD), dp(10), 0, dp(4));
                        content.addView(more, ml);
                    }
                } else {
                    if (st.matches(".*\\b(albums?|singles?|eps?)\\b.*")) rest.add(s);
                    if (!s.list && !st.contains("video")) restAll.add(s);
                }
            }
            // Albums and Singles & EPs: Apple Music's complete discography (YouTube Music's rows if Apple has none).
            final LinearLayout disco = new LinearLayout(this);
            disco.setOrientation(LinearLayout.VERTICAL);
            disco.setClipChildren(false);
            content.addView(disco);
            final String name = pg.title.isEmpty() ? item.title : pg.title;
            if (item.browseId.startsWith("AMA:")) {
                // Already the artist's Apple Music page: Albums, Singles & EPs, Appears On, Similar Artists.
                MusicUi.addShelves(this, disco, restAll, open, null);
                if (!songs.isEmpty() || !pg.shelves.isEmpty()) { View f = firstFocusable(content); if (f != null) f.requestFocus(); }
                return;
            }
            Bg.run(() -> {
                String id = AppleMusic.artistId(name);
                if (id == null) return null;
                AppleMusic.Discography d = AppleMusic.discography(id);
                return d.albums.isEmpty() && d.singles.isEmpty() ? null : new Object[]{id, d};
            }, (r, e) -> {
                if (r == null) { MusicUi.addShelves(this, disco, rest, open, null); return; }
                String id = (String) r[0];
                AppleMusic.Discography d = (AppleMusic.Discography) r[1];
                discoRow(disco, "Albums", d.albums, "AMALL:" + id + ":albums", open);
                discoRow(disco, "Singles & EPs", d.singles, "AMALL:" + id + ":singles", open);
            });
        } else {
            MusicUi.addShelves(this, content, pg.shelves, open, null);
        }
        play.requestFocus();
    }

    private void startPlay(List<MItem> songs, String pl) {
        played();
        MusicPlayer mp = MusicPlayer.get(this);
        mp.setShuffle(false);
        if (!songs.isEmpty()) mp.playList(songs, 0, pl);
        else if (!pl.isEmpty()) mp.playId(null, pl);
        else return;
        startActivity(MusicPlayer.nowPlaying(this));
    }

    /** The artist page's big round Play: white, a ring when you're on it. */
    private void paintBig(ImageView v, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? Color.WHITE : 0xF2FFFFFF);
        if (on) g.setStroke(dp(3), 0xFF9A9A9A);
        v.setBackground(g);
    }

    private void discoRow(LinearLayout into, String title, List<MItem> items, String allId, MusicUi.Opener open) {
        if (items.isEmpty()) return;
        MItem more = null;
        if (items.size() > 8) {
            more = new MItem();
            more.type = MItem.MORE;
            more.title = title;
            more.browseId = allId;
        }
        MusicUi.addRow(this, into, title, items.subList(0, Math.min(12, items.size())), 138, open, null, more);
    }

    /** "See all": every release in the shelf (official only), as a grid. */
    private void all(Ytm.Page pg) {
        hero.setVisibility(View.GONE);
        TextView t = MusicUi.text(this, 30, Color.WHITE, true);
        t.setText(item.title.isEmpty() ? pg.title : item.title);
        t.setPadding(dp(MusicUi.PAD), dp(36), dp(MusicUi.PAD), dp(4));
        content.addView(t);
        List<MItem> items = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Ytm.Shelf s : pg.shelves) for (MItem m : s.items) if (m.official() && seen.add(m.browseId + m.videoId + m.appleId)) items.add(m);
        if (items.isEmpty()) { TextView n = MusicUi.text(this, 17, MusicUi.TEXT2, false); n.setText("Nothing here."); n.setPadding(dp(MusicUi.PAD), dp(20), 0, 0); content.addView(n); return; }
        if (items.get(0).playable()) {
            // All of an artist's songs: one list, numbered, playable from any song.
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setPadding(dp(MusicUi.PAD - 12), dp(10), dp(MusicUi.PAD - 12), dp(40));
            for (int i = 0; i < items.size(); i++) {
                final int at = i;
                View r = MusicUi.songRow(this, items.get(i), i + 1, true);
                r.setOnClickListener(v -> { played(); MusicPlayer.get(this).playList(items, at, null); startActivity(MusicPlayer.nowPlaying(this)); });
                l.addView(r, new LinearLayout.LayoutParams(-1, dp(62)));
            }
            content.addView(l);
        } else MusicUi.addGrid(this, content, items, 6, (m, ctx) -> MusicUi.open(this, m, ctx));
        content.post(() -> { View f = firstFocusable(content); if (f != null) f.requestFocus(); });
        if (!items.get(0).thumb.isEmpty()) MusicUi.colorOf(this, items.get(0).thumb, c -> MusicUi.fadeTo(root, MusicUi.tintOf(c)));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (MusicUi.moveVertical(content, getCurrentFocus(), k == KeyEvent.KEYCODE_DPAD_UP)) return true;
            if (k == KeyEvent.KEYCODE_DPAD_UP) scroll.smoothScrollTo(0, 0);
            return true;
        }
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
