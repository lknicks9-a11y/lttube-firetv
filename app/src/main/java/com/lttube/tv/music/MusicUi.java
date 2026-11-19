package com.lttube.tv.music;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.lttube.tv.LtDialog;
import com.lttube.tv.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The look shared by every music screen: dark glassy background, big rounded art, cards that lift on focus. */
public final class MusicUi {
    public static final int BG_TOP = 0xFF26292A, BG_BOTTOM = 0xFF141516;
    public static final int TEXT2 = 0xB3FFFFFF, TEXT3 = 0x80FFFFFF;
    public static final int PINK = 0xFFFF4F7B;
    /** Left edge of rows and titles. */
    static final int PAD = 52;

    public static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    public static TextView text(Context c, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView line(Context c, float sp, int color, boolean bold) {
        TextView t = text(c, sp, color, bold);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable background(int tint) {
        int top = tint == 0 ? BG_TOP : blend(tint, 0xFF000000, 0.55f);
        int bottom = tint == 0 ? BG_BOTTOM : blend(tint, 0xFF000000, 0.86f);
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, blend(top, bottom, 0.5f), bottom});
    }

    public static int blend(int a, int b, float t) {
        int r = Math.round(Color.red(a) * (1 - t) + Color.red(b) * t);
        int g = Math.round(Color.green(a) * (1 - t) + Color.green(b) * t);
        int bl = Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    /** Lift on focus, like the Apple TV-style cards in the pictures. */
    public static void lift(View v, float scale) {
        v.setOnFocusChangeListener((x, has) -> {
            x.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).translationZ(has ? dp(x.getContext(), 12) : 0).setDuration(160).start();
        });
    }

    public static void art(ImageView iv, String url, int radiusPx) {
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (url == null || url.isEmpty()) { iv.setImageDrawable(round(0xFF2A2D35, radiusPx)); return; }
        Glide.with(iv).load(url).transform(new CenterCrop(), new RoundedCorners(Math.max(1, radiusPx))).placeholder(round(0xFF22252C, radiusPx)).into(iv);
    }

    public static void circle(ImageView iv, String url) {
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(iv).load(url).transform(new CenterCrop(), new CircleCrop()).placeholder(new ColorCircle(0xFF2A2D35)).into(iv);
    }

    static class ColorCircle extends GradientDrawable { ColorCircle(int c) { setShape(OVAL); setColor(c); } }

    // ------------------------------------------------------------------ colour from art
    private static final Map<String, Integer> colors = new HashMap<>();

    public interface ColorDone { void color(int c); }

    /** The art's main colour (cached), for card bands and backgrounds. */
    public static void colorOf(Context c, String url, ColorDone done) {
        if (url == null || url.isEmpty()) { done.color(0xFF3A3D46); return; }
        Integer hit = colors.get(url);
        if (hit != null) { done.color(hit); return; }
        try {
            Glide.with(c.getApplicationContext()).asBitmap().load(url).override(24, 24).disallowHardwareConfig().into(new CustomTarget<Bitmap>() {
                @Override public void onResourceReady(Bitmap b, Transition<? super Bitmap> t) {
                    int col;
                    try { col = vivid(b); } catch (Throwable e) { col = 0xFF3A3D46; }
                    colors.put(url, col);
                    done.color(col);
                }
                @Override public void onLoadCleared(Drawable d) { }
            });
        } catch (Throwable e) { done.color(0xFF3A3D46); }
    }

    /** A colour that stands for the picture: the average of its more colourful pixels. */
    static int vivid(Bitmap b) {
        long r = 0, g = 0, bl = 0, n = 0, r2 = 0, g2 = 0, b2 = 0, n2 = 0;
        for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) {
            int p = b.getPixel(x, y);
            int pr = Color.red(p), pg = Color.green(p), pb = Color.blue(p);
            int mx = Math.max(pr, Math.max(pg, pb)), mn = Math.min(pr, Math.min(pg, pb));
            r2 += pr; g2 += pg; b2 += pb; n2++;
            if (mx - mn > 40 && mx > 50) { r += pr; g += pg; bl += pb; n++; }
        }
        int col = n > n2 / 8 ? Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n)) : Color.rgb((int) (r2 / n2), (int) (g2 / n2), (int) (b2 / n2));
        float[] hsv = new float[3];
        Color.colorToHSV(col, hsv);
        hsv[2] = Math.min(hsv[2], 0.62f);
        hsv[1] = Math.min(1f, hsv[1] * 1.15f);
        return Color.HSVToColor(hsv);
    }

    // ------------------------------------------------------------------ shelves
    public interface Opener { void open(MItem m, List<MItem> context); }

    /** Adds a page's shelves as rows: big feature cards for the first one, art cards, song lists and mood tiles. */
    public static void addShelves(Activity a, LinearLayout into, List<Ytm.Shelf> shelves, boolean feature, Opener open) {
        boolean first = feature;
        for (Ytm.Shelf s : shelves) {
            if (s.items.isEmpty()) continue;
            boolean cats = MItem.CATEGORY.equals(s.items.get(0).type);
            boolean songs = s.list;
            boolean videos = s.title.toLowerCase(java.util.Locale.US).contains("video");
            if (videos) for (MItem m : s.items) if (m.playable()) m.video = true;
            if (!s.title.isEmpty() || !s.strap.isEmpty()) {
                // Section titles like Apple TV's: bold, a soft white, aligned with the cards.
                TextView h = text(a, 22, 0xD9FFFFFF, true);
                h.setText(s.title.isEmpty() ? s.strap : s.title);
                h.setPadding(dp(a, PAD), dp(a, 22), 0, dp(a, 2));
                into.addView(h);
            }
            if (cats) into.addView(tileGrid(a, s.items, open));
            else if (songs) into.addView(songGrid(a, s.items, open));
            else into.addView(cardRow(a, s, first, open));
            if (!cats) first = false;
        }
    }

    private static HorizontalScrollView hscroll(Context c, LinearLayout row) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.setClipToPadding(false);
        h.setClipChildren(false);
        h.setFillViewport(false);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(c, PAD), dp(c, 16), dp(c, PAD), dp(c, 22));
        h.addView(row);
        return h;
    }

    /** A row of cards. Feature cards: art with a coloured band holding the title, a small label above. */
    static View cardRow(Activity a, Ytm.Shelf s, boolean feature, Opener open) {
        LinearLayout row = new LinearLayout(a);
        boolean wide = !feature && s.title.toLowerCase(java.util.Locale.US).contains("video");
        int size = feature ? dp(a, 206) : wide ? dp(a, 262) : dp(a, 164);
        int gap = dp(a, feature ? 24 : 22);
        for (MItem m : s.items) {
            View card = feature ? featureCard(a, m, size) : artCard(a, m, size);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, -2);
            lp.rightMargin = gap;
            row.addView(card, lp);
            card.setOnClickListener(v -> open.open(m, s.items));
            card.setOnKeyListener((v, k, e) -> {
                if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(a, m); return true; }
                return false;
            });
        }
        return hscroll(a, row);
    }

    static View featureCard(Activity a, MItem m, int size) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView strap = line(a, 15, 0x99FFFFFF, false);
        strap.setText(kindLabel(m));
        strap.setPadding(dp(a, 2), 0, 0, dp(a, 10));
        col.addView(strap);
        final LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClipToOutline(true);
        final GradientDrawable bg = round(0xFF30333C, dp(a, 14));
        card.setBackground(bg);
        ImageView iv = new ImageView(a);
        art(iv, m.thumb, 0);
        card.addView(iv, new LinearLayout.LayoutParams(size, size));
        LinearLayout band = new LinearLayout(a);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setGravity(Gravity.CENTER);
        band.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 12));
        TextView t = line(a, 16, Color.WHITE, true);
        t.setText(m.title);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        band.addView(t);
        TextView sub = line(a, 15, 0xE6FFFFFF, false);
        sub.setText(shortSub(m));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(a, 3), 0, 0);
        band.addView(sub);
        card.addView(band, new LinearLayout.LayoutParams(size, Math.round(size * 0.40f)));
        colorOf(a, m.thumb, c -> bg.setColor(c));
        lift(card, 1.10f);
        col.addView(card);
        return new FocusProxy(col, card);
    }

    /** A column whose click listener belongs to its focusable card. */
    static class FocusProxy extends FrameLayout {
        final View card;
        FocusProxy(LinearLayout col, View card) {
            super(col.getContext());
            this.card = card;
            setClipChildren(false);
            addView(col);
        }
        @Override public void setOnClickListener(OnClickListener l) { card.setOnClickListener(l); }
        @Override public void setOnKeyListener(OnKeyListener l) { card.setOnKeyListener(l); }
    }

    static View artCard(Activity a, MItem m, int size) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        boolean round = MItem.ARTIST.equals(m.type);
        ImageView iv = new ImageView(a);
        iv.setFocusable(true);
        if (round) circle(iv, m.thumb); else art(iv, m.thumb, dp(a, 12));
        // Music videos come wide: shown wide.
        boolean wide = m.thumb.contains("i.ytimg.com") || m.video;
        int h = wide ? size * 9 / 16 : size;
        col.addView(iv, new LinearLayout.LayoutParams(size, h));
        final TextView t = line(a, 15, 0xF2FFFFFF, true);
        final TextView sub = line(a, 14, 0x99FFFFFF, false);
        // tvOS: the picture grows and the title under it slides down out of its way.
        iv.setOnFocusChangeListener((x, has) -> {
            x.animate().scaleX(has ? 1.10f : 1f).scaleY(has ? 1.10f : 1f).translationZ(has ? dp(a, 14) : 0).setDuration(170).start();
            float dy = has ? h * 0.05f + dp(a, 4) : 0;
            t.animate().translationY(dy).setDuration(170).start();
            sub.animate().translationY(dy).setDuration(170).start();
        });
        t.setText(m.title);
        t.setGravity(round ? Gravity.CENTER : Gravity.START);
        t.setPadding(dp(a, 1), dp(a, 10), 0, 0);
        col.addView(t, new LinearLayout.LayoutParams(size, -2));
        sub.setText(shortSub(m));
        sub.setGravity(round ? Gravity.CENTER : Gravity.START);
        sub.setPadding(dp(a, 1), dp(a, 3), 0, 0);
        col.addView(sub, new LinearLayout.LayoutParams(size, -2));
        return new FocusProxy(col, iv);
    }

    /** Songs in columns of four, scrolling sideways (like a Top Songs shelf). */
    static View songGrid(Activity a, List<MItem> songs, Opener open) {
        LinearLayout row = new LinearLayout(a);
        int w = dp(a, 380);
        LinearLayout col = null;
        for (int i = 0; i < songs.size(); i++) {
            if (i % 4 == 0) {
                col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, -2);
                lp.rightMargin = dp(a, 18);
                row.addView(col, lp);
            }
            final MItem m = songs.get(i);
            View r = songRow(a, m, -1, false);
            r.setOnClickListener(v -> open.open(m, songs));
            r.setOnKeyListener((v, k, e) -> {
                if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(a, m); return true; }
                return false;
            });
            col.addView(r, new LinearLayout.LayoutParams(-1, dp(a, 72)));
        }
        return hscroll(a, row);
    }

    /** One song: art (or its number), title, artist, length. */
    public static View songRow(Context a, MItem m, int number, boolean withTime) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(a, 10), 0, dp(a, 14), 0);
        final GradientDrawable bg = round(0x00FFFFFF, dp(a, 10));
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> bg.setColor(has ? 0x33FFFFFF : 0x00FFFFFF));
        if (number > 0) {
            TextView n = text(a, 16, TEXT3, false);
            n.setText(String.valueOf(number));
            n.setGravity(Gravity.CENTER);
            r.addView(n, new LinearLayout.LayoutParams(dp(a, 44), -2));
        } else {
            ImageView iv = new ImageView(a);
            art(iv, m.thumb, dp(a, 6));
            r.addView(iv, new LinearLayout.LayoutParams(dp(a, 54), dp(a, 54)));
        }
        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(a, 14), 0, dp(a, 8), 0);
        TextView t = line(a, 17, Color.WHITE, false);
        t.setText(m.title);
        txt.addView(t);
        TextView s = line(a, 14, TEXT2, false);
        s.setText(m.artist.isEmpty() ? m.subtitle : m.artist);
        s.setPadding(0, dp(a, 4), 0, 0);
        txt.addView(s);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
        if (withTime && m.seconds > 0) {
            TextView d = text(a, 15, TEXT3, false);
            d.setText(MItem.time(m.seconds));
            r.addView(d);
        }
        return r;
    }

    /** Mood and genre tiles: rounded colour tiles in two rows. */
    static View tileGrid(Activity a, List<MItem> tiles, Opener open) {
        LinearLayout row = new LinearLayout(a);
        LinearLayout col = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 2 == 0) {
                col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 250), -2);
                lp.rightMargin = dp(a, 20);
                row.addView(col, lp);
            }
            final MItem m = tiles.get(i);
            TextView t = text(a, 19, Color.WHITE, true);
            t.setText(m.title);
            t.setGravity(Gravity.BOTTOM | Gravity.START);
            t.setPadding(dp(a, 18), 0, dp(a, 12), dp(a, 16));
            t.setFocusable(true);
            int c = m.color == 0 ? 0xFF555A66 : m.color;
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{blend(c, 0xFF000000, 0.15f), blend(c, 0xFF000000, 0.55f)});
            g.setCornerRadius(dp(a, 14));
            t.setBackground(g);
            lift(t, 1.07f);
            t.setOnClickListener(v -> open.open(m, tiles));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(a, 104));
            lp.bottomMargin = dp(a, 18);
            col.addView(t, lp);
        }
        return hscroll(a, row);
    }

    static String kindLabel(MItem m) {
        if (MItem.ALBUM.equals(m.type)) return "Album";
        if (MItem.ARTIST.equals(m.type)) return "Artist";
        if (m.title.toLowerCase(java.util.Locale.US).contains("mix") || m.playlistId.startsWith("RDTMAK") || m.playlistId.startsWith("RDAMPL")) return "Made for you";
        if (MItem.PLAYLIST.equals(m.type)) return "Playlist";
        return "Song";
    }

    static String shortSub(MItem m) {
        String s = m.subtitle;
        for (String drop : new String[]{"Playlist • ", "Album • ", "Single • ", "EP • ", "Song • ", "Artist • "}) if (s.startsWith(drop)) s = s.substring(drop.length());
        return s;
    }

    // ------------------------------------------------------------------ opening things
    public static void open(Activity a, MItem m, List<MItem> context) {
        MusicPlayer mp = MusicPlayer.get(a);
        if (m.video && m.playable()) {
            // Music videos play as video (LTTube's player); the music pauses meanwhile.
            mp.pause();
            com.lttube.tv.Item v = new com.lttube.tv.Item();
            v.url = "https://www.youtube.com/watch?v=" + m.videoId;
            v.title = m.title;
            v.channel = m.artist;
            v.thumb = m.thumb;
            v.duration = m.seconds;
            com.lttube.tv.Nav.queue = new java.util.ArrayList<>();
            com.lttube.tv.Nav.play(a, v);
            return;
        }
        if (m.playable()) {
            // A song in a list of songs plays the list from there; on its own, its radio.
            int songs = 0;
            if (context != null) for (MItem x : context) if (x.playable()) songs++;
            if (songs >= 3) mp.playList(context, Math.max(0, context.indexOf(m)), null);
            else mp.playRadio(m);
            a.startActivity(MusicPlayer.nowPlaying(a));
            return;
        }
        // Mixes (made for you, radio) just play — they have no page of their own.
        boolean mix = m.playlistId.startsWith("RD") && !m.playlistId.startsWith("RDCLAK");
        if (MItem.PLAYLIST.equals(m.type) && !m.playlistId.isEmpty() && (m.browseId.isEmpty() || mix)) {
            mp.playId(null, m.playlistId);
            a.startActivity(MusicPlayer.nowPlaying(a));
            return;
        }
        Intent i = new Intent(a, PageActivity.class);
        i.putExtra(PageActivity.ITEM, m.toJson().toString());
        a.startActivity(i);
    }

    /** Menu button on a song: play next, add to queue, its radio. */
    public static void options(Activity a, MItem m) {
        if (!m.playable()) return;
        MusicPlayer mp = MusicPlayer.get(a);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final LtDialog[] d = new LtDialog[1];
        String[] labels = {"Play next", "Add to queue", "Start radio"};
        Runnable[] acts = {() -> mp.playNext(m), () -> mp.addToQueue(m), () -> { mp.playRadio(m); a.startActivity(MusicPlayer.nowPlaying(a)); }};
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView t = text(a, 18, Color.WHITE, false);
            t.setText(labels[i]);
            t.setFocusable(true);
            t.setPadding(dp(a, 18), dp(a, 14), dp(a, 18), dp(a, 14));
            final GradientDrawable bg = round(0, dp(a, 10));
            t.setBackground(bg);
            t.setOnFocusChangeListener((v, has) -> bg.setColor(has ? 0x33FFFFFF : 0));
            t.setOnClickListener(v -> { acts[k].run(); if (d[0] != null) d[0].dismiss(); android.widget.Toast.makeText(a, labels[k] + ": " + m.title, android.widget.Toast.LENGTH_SHORT).show(); });
            box.addView(t);
        }
        d[0] = new LtDialog.Builder(a).setTitle(m.title).setView(box).show();
    }

    /** A round icon button (shuffle, repeat, lyrics…). */
    public static ImageView iconButton(Context c, int icon, int sizeDp) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setFocusable(true);
        int p = dp(c, sizeDp * 0.24f);
        b.setPadding(p, p, p, p);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> {
            paintIcon((ImageView) v);
            v.animate().scaleX(has ? 1.18f : 1f).scaleY(has ? 1.18f : 1f).translationZ(has ? dp(c, 8) : 0).setDuration(150).start();
        });
        paintIcon(b);
        return b;
    }

    /** Round buttons like Apple TV's: frosted when off, solid white with a dark glyph when on or focused. */
    private static void paintIcon(ImageView b) {
        boolean lit = b.isActivated() || b.hasFocus();
        ((GradientDrawable) b.getBackground()).setColor(b.hasFocus() ? Color.WHITE : b.isActivated() ? 0xF2FFFFFF : 0x40FFFFFF);
        b.setColorFilter(lit ? 0xFF1C1C1E : 0xF2FFFFFF);
    }

    /** Marks a toggle button on or off. */
    public static void setOn(ImageView b, boolean on) {
        b.setActivated(on);
        paintIcon(b);
    }

    /** The app's name in the corner: a note in a pink square and "LT Music". */
    public static View wordmark(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        ImageView n = new ImageView(c);
        n.setImageResource(R.drawable.ic_m_note);
        int p = dp(c, 5);
        n.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFFFF6A88, 0xFFB43CFF});
        g.setCornerRadius(dp(c, 8));
        n.setBackground(g);
        l.addView(n, new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)));
        TextView t = text(c, 22, Color.WHITE, true);
        t.setText("LT Music");
        t.setPadding(dp(c, 10), 0, 0, 0);
        l.addView(t);
        return l;
    }

    public static ViewGroup.LayoutParams match() { return new ViewGroup.LayoutParams(-1, -1); }

    private MusicUi() { }
}
