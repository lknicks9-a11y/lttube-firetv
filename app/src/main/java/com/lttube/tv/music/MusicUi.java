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

/**
 * LT Music's look, after a big-screen music app: a soft charcoal background, square covers with a white outline
 * when you're on them, round artist pictures, pill buttons and chips, colour tiles to browse by.
 * Everything is laid out in rows and columns that size themselves, so text never runs into other text.
 */
public final class MusicUi {
    public static final int BG_TOP = 0xFF34363A, BG_BOTTOM = 0xFF17181A;
    public static final int TEXT2 = 0xB3FFFFFF, TEXT3 = 0x80FFFFFF;
    /** LT Music's accent (its own pink, for things that are on). */
    public static final int PINK = 0xFFFF4F7B;
    /** Safe margins: TVs crop the edges, so nothing sits closer than this. */
    public static final int PAD = 44, TOP = 22;

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

    /** The page colour: one even charcoal, so pictures can fade into it without seams. */
    public static final int BG = 0xFF2B2D30;

    public static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG, BG});
    }

    public static GradientDrawable background(int ignored) { return background(); }

    /** A deep shade of a cover's colour, for the page behind it (like the colour wash on big-screen music apps). */
    public static int tintOf(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(0.55f, hsv[1] * 0.85f);
        hsv[2] = 0.30f;
        return blend(Color.HSVToColor(hsv), BG, 0.25f);
    }

    /** Fades a page's background to a new colour. */
    public static void fadeTo(View v, int to) {
        android.graphics.drawable.Drawable d = v.getBackground();
        if (!(d instanceof GradientDrawable)) { v.setBackground(round(to, 0)); return; }
        GradientDrawable g = (GradientDrawable) d;
        Object tag = v.getTag(R.id.tag_color);
        int from = tag instanceof Integer ? (Integer) tag : BG;
        if (from == to) return;
        v.setTag(R.id.tag_color, to);
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofArgb(from, to);
        a.setDuration(500);
        a.addUpdateListener(an -> g.setColor((int) an.getAnimatedValue()));
        a.start();
    }

    public static int blend(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) * (1 - t) + Color.red(b) * t), Math.round(Color.green(a) * (1 - t) + Color.green(b) * t),
                Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    public static ViewGroup.LayoutParams match() { return new ViewGroup.LayoutParams(-1, -1); }

    // ------------------------------------------------------------------ focus
    /** Selected = a white outline around it and a small lift. */
    public static void outline(View v, float radiusPx, boolean circle, float scale) {
        v.setOnFocusChangeListener((x, has) -> markFocus(x, has, radiusPx, circle, scale));
    }

    static void markFocus(View x, boolean has, float radiusPx, boolean circle, float scale) {
        if (has) {
            GradientDrawable ring = new GradientDrawable();
            if (circle) ring.setShape(GradientDrawable.OVAL); else ring.setCornerRadius(radiusPx);
            ring.setStroke(dp(x.getContext(), 3), Color.WHITE);
            ring.setColor(Color.TRANSPARENT);
            x.setForeground(ring);
        } else x.setForeground(null);
        x.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).translationZ(has ? dp(x.getContext(), 10) : 0).setDuration(150).start();
    }

    // ------------------------------------------------------------------ pictures
    public static void art(ImageView iv, String url, int radiusPx) {
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (url == null || url.isEmpty()) { iv.setImageDrawable(round(0xFF3A3C40, radiusPx)); return; }
        if (radiusPx <= 0) Glide.with(iv).load(url).centerCrop().placeholder(round(0xFF2E3033, 0)).into(iv);
        else Glide.with(iv).load(url).transform(new CenterCrop(), new RoundedCorners(radiusPx)).placeholder(round(0xFF2E3033, radiusPx)).into(iv);
    }

    public static void circle(ImageView iv, String url) {
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable ph = new GradientDrawable();
        ph.setShape(GradientDrawable.OVAL);
        ph.setColor(0xFF3A3C40);
        Glide.with(iv).load(url).transform(new CenterCrop(), new CircleCrop()).placeholder(ph).into(iv);
    }

    private static final Map<String, Integer> colors = new HashMap<>();

    public interface ColorDone { void color(int c); }

    /** A cover's main colour (cached). */
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
        return Color.HSVToColor(hsv);
    }

    // ------------------------------------------------------------------ pieces
    public interface Opener { void open(MItem m, List<MItem> context); }
    /** Told which item you're on (Home's big header follows you). */
    public interface Hover { void on(MItem m); }

    /** A cover with its title and "Album • Artist" under it; artists are round. */
    public static View card(Activity a, MItem m, int size, List<MItem> ctx, Opener open, Hover hover) {
        boolean round = MItem.ARTIST.equals(m.type);
        boolean wide = m.video;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        ImageView iv = new ImageView(a);
        iv.setFocusable(true);
        int w = wide ? Math.round(size * 1.6f) : size, h = size;
        if (round) circle(iv, m.thumb); else art(iv, m.thumb, dp(a, 6));
        iv.setOnFocusChangeListener((x, has) -> { markFocus(x, has, dp(a, 6), round, 1.05f); if (has && hover != null) hover.on(m); });
        iv.setOnClickListener(v -> open.open(m, ctx));
        iv.setOnKeyListener((v, k, e) -> {
            if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(a, m); return true; }
            return false;
        });
        col.addView(iv, new LinearLayout.LayoutParams(w, h));
        TextView t = line(a, 15, Color.WHITE, true);
        t.setText(m.title);
        t.setGravity(round ? Gravity.CENTER : Gravity.START);
        t.setPadding(0, dp(a, 9), 0, 0);
        col.addView(t, new LinearLayout.LayoutParams(w, -2));
        TextView s = line(a, 13, TEXT2, false);
        s.setText(kindLine(m));
        s.setGravity(round ? Gravity.CENTER : Gravity.START);
        s.setPadding(0, dp(a, 3), 0, 0);
        col.addView(s, new LinearLayout.LayoutParams(w, -2));
        return col;
    }

    /** A compact tile: small cover on the left, the name beside it (the quick picks on Home). */
    public static View tile(Activity a, MItem m, int w, int h, List<MItem> ctx, Opener open, Hover hover) {
        LinearLayout t = new LinearLayout(a);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setFocusable(true);
        t.setClipToOutline(true);
        t.setBackground(round(0x2EFFFFFF, dp(a, 6)));
        ImageView iv = new ImageView(a);
        if (MItem.ARTIST.equals(m.type)) circle(iv, m.thumb); else art(iv, m.thumb, 0);
        t.addView(iv, new LinearLayout.LayoutParams(h, h));
        TextView n = text(a, 15, Color.WHITE, true);
        n.setText(m.title);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        n.setPadding(dp(a, 12), 0, dp(a, 10), 0);
        t.addView(n, new LinearLayout.LayoutParams(0, -2, 1));
        t.setOnFocusChangeListener((x, has) -> {
            markFocus(x, has, dp(a, 6), false, 1.03f);
            x.setBackground(round(has ? 0x4DFFFFFF : 0x2EFFFFFF, dp(a, 6)));
            if (has && hover != null) hover.on(m);
        });
        t.setOnClickListener(v -> open.open(m, ctx));
        return t;
    }

    /** A filter chip; the chosen one is white. */
    public static TextView chip(Context c, String label, boolean chosen) {
        TextView t = text(c, 15, chosen ? 0xFF121212 : Color.WHITE, true);
        t.setText(label);
        t.setFocusable(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        t.setTag(chosen);
        paintChip(t, chosen, false);
        t.setOnFocusChangeListener((v, has) -> paintChip((TextView) v, Boolean.TRUE.equals(v.getTag()), has));
        return t;
    }

    public static void setChosen(TextView t, boolean chosen) { t.setTag(chosen); paintChip(t, chosen, t.hasFocus()); }

    private static void paintChip(TextView t, boolean chosen, boolean focus) {
        Context c = t.getContext();
        GradientDrawable g = round(chosen ? 0xF2FFFFFF : focus ? 0x59FFFFFF : 0x33FFFFFF, dp(c, 18));
        if (focus) g.setStroke(dp(c, 2), Color.WHITE);
        t.setBackground(g);
        t.setTextColor(chosen ? 0xFF121212 : Color.WHITE);
    }

    /** A pill button with an icon ("Lyrics", "Queue", "Save"). */
    public static TextView pill(Context c, int icon, String label) {
        TextView t = text(c, 16, Color.WHITE, true);
        t.setText(label);
        t.setFocusable(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), 0, dp(c, 20), 0);
        if (icon != 0) {
            Drawable d = c.getDrawable(icon).mutate();
            d.setBounds(0, 0, dp(c, 20), dp(c, 20));
            d.setTint(Color.WHITE);
            t.setCompoundDrawables(d, null, null, null);
            t.setCompoundDrawablePadding(dp(c, 8));
        }
        paintPill(t, false);
        t.setOnFocusChangeListener((v, has) -> paintPill((TextView) v, has));
        return t;
    }

    private static void paintPill(TextView t, boolean focus) {
        Context c = t.getContext();
        GradientDrawable g = round(focus ? 0x59FFFFFF : 0x40FFFFFF, dp(c, 24));
        if (focus) g.setStroke(dp(c, 3), Color.WHITE);
        t.setBackground(g);
    }

    /** A round icon button (next, shuffle, repeat…). When on it shows in the accent colour. */
    public static ImageView iconButton(Context c, int icon, int sizeDp) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        b.setFocusable(true);
        int p = dp(c, sizeDp * 0.26f);
        b.setPadding(p, p, p, p);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setOnFocusChangeListener((v, has) -> paintIcon((ImageView) v));
        paintIcon(b);
        return b;
    }

    static void paintIcon(ImageView b) {
        Context c = b.getContext();
        boolean f = b.hasFocus();
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(f ? 0x66FFFFFF : 0x40FFFFFF);
        if (f) g.setStroke(dp(c, 3), Color.WHITE);
        b.setBackground(g);
        b.setColorFilter(b.isActivated() ? PINK : Color.WHITE);
    }

    public static void setOn(ImageView b, boolean on) { b.setActivated(on); paintIcon(b); }

    /** Browse tiles: a colour block with its name, and a picture tilted into the corner. */
    public static View categoryTile(Activity a, MItem m, int w, int h, Opener open) {
        FrameLayout f = new FrameLayout(a);
        f.setFocusable(true);
        f.setClipToOutline(true);
        int col = m.color == 0 ? 0xFF6A5ACD : m.color;
        f.setBackground(round(blend(col, 0xFF000000, 0.08f), dp(a, 8)));
        TextView t = text(a, 18, Color.WHITE, true);
        t.setText(m.title);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(Math.round(w * 0.62f), -2, Gravity.TOP | Gravity.START);
        tl.setMargins(dp(a, 14), dp(a, 12), 0, 0);
        f.addView(t, tl);
        ImageView pic = new ImageView(a);
        pic.setRotation(22f);
        pic.setElevation(dp(a, 4));
        int ps = Math.round(h * 0.68f);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(ps, ps, Gravity.BOTTOM | Gravity.END);
        pl.setMargins(0, 0, -Math.round(ps * 0.14f), -Math.round(ps * 0.10f));
        f.addView(pic, pl);
        if (!m.thumb.isEmpty()) art(pic, m.thumb, dp(a, 4));
        f.setTag(pic);
        outline(f, dp(a, 8), false, 1.05f);
        f.setOnClickListener(v -> open.open(m, null));
        return f;
    }

    /** One song in a list: number or cover, title and artist, album, length. */
    public static View songRow(Context a, MItem m, int number, boolean withTime) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(a, 12), 0, dp(a, 16), 0);
        r.setBackground(round(0, dp(a, 6)));
        r.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = round(has ? 0x40FFFFFF : 0, dp(a, 6));
            if (has) g.setStroke(dp(a, 2), Color.WHITE);
            v.setBackground(g);
        });
        if (number > 0) {
            TextView n = text(a, 15, TEXT2, false);
            n.setText(String.valueOf(number));
            n.setGravity(Gravity.CENTER);
            r.addView(n, new LinearLayout.LayoutParams(dp(a, 34), -2));
        }
        ImageView iv = new ImageView(a);
        art(iv, m.thumb, dp(a, 4));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(a, 46), dp(a, 46));
        il.leftMargin = number > 0 ? dp(a, 6) : 0;
        r.addView(iv, il);
        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(a, 14), 0, dp(a, 12), 0);
        TextView t = line(a, 16, Color.WHITE, true);
        t.setText(m.title);
        txt.addView(t);
        TextView s = line(a, 13, TEXT2, false);
        s.setText(m.artist.isEmpty() ? m.subtitle : m.artist);
        s.setPadding(0, dp(a, 3), 0, 0);
        txt.addView(s);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1.4f));
        if (withTime) {
            TextView al = line(a, 14, TEXT2, false);
            al.setText(m.album);
            r.addView(al, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView d = text(a, 14, TEXT2, false);
            d.setText(m.seconds > 0 ? MItem.time(m.seconds) : "");
            d.setGravity(Gravity.END);
            r.addView(d, new LinearLayout.LayoutParams(dp(a, 56), -2));
        }
        return r;
    }

    // ------------------------------------------------------------------ rows
    /** A titled row of covers that scrolls sideways. */
    public static void addRow(Activity a, LinearLayout into, String title, List<MItem> items, int size, Opener open, Hover hover) {
        if (items.isEmpty()) return;
        if (title != null && !title.isEmpty()) into.addView(heading(a, title));
        LinearLayout row = new LinearLayout(a);
        row.setClipChildren(false);
        for (MItem m : items) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(a, 18);
            row.addView(card(a, m, dp(a, size), items, open, hover), lp);
        }
        into.addView(hscroll(a, row));
    }

    public static TextView heading(Context a, String title) {
        TextView h = text(a, 20, Color.WHITE, true);
        h.setText(title);
        h.setPadding(dp(a, PAD), dp(a, 18), dp(a, PAD), dp(a, 2));
        h.setMaxLines(1);
        h.setEllipsize(TextUtils.TruncateAt.END);
        return h;
    }

    static HorizontalScrollView hscroll(Context c, LinearLayout row) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.setClipToPadding(false);
        h.setClipChildren(false);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        // Room above and below so a selected cover's outline and lift never get cut.
        row.setPadding(dp(c, PAD), dp(c, 12), dp(c, PAD), dp(c, 14));
        h.addView(row);
        return h;
    }

    /** Song lists as rows of songs in columns of four. */
    public static void addSongs(Activity a, LinearLayout into, String title, List<MItem> songs, Opener open, Hover hover) {
        if (songs.isEmpty()) return;
        if (title != null && !title.isEmpty()) into.addView(heading(a, title));
        LinearLayout row = new LinearLayout(a);
        LinearLayout col = null;
        for (int i = 0; i < songs.size(); i++) {
            if (i % 4 == 0) {
                col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 360), -2);
                lp.rightMargin = dp(a, 16);
                row.addView(col, lp);
            }
            final MItem m = songs.get(i);
            View r = songRow(a, m, -1, false);
            r.setOnClickListener(v -> open.open(m, songs));
            final View.OnFocusChangeListener base = r.getOnFocusChangeListener();
            r.setOnFocusChangeListener((v, has) -> { base.onFocusChange(v, has); if (has && hover != null) hover.on(m); });
            r.setOnKeyListener((v, k, e) -> {
                if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(a, m); return true; }
                return false;
            });
            col.addView(r, new LinearLayout.LayoutParams(-1, dp(a, 60)));
        }
        into.addView(hscroll(a, row));
    }

    /** A page's shelves as rows: songs as lists, mood buttons as colour tiles, everything else as covers. */
    public static void addShelves(Activity a, LinearLayout into, List<Ytm.Shelf> shelves, Opener open, Hover hover) {
        for (Ytm.Shelf s : shelves) {
            if (s.items.isEmpty()) continue;
            boolean videos = s.title.toLowerCase(java.util.Locale.US).contains("video");
            if (videos) for (MItem m : s.items) if (m.playable()) m.video = true;
            String title = s.title.isEmpty() ? s.strap : s.title;
            if (MItem.CATEGORY.equals(s.items.get(0).type)) addTiles(a, into, title, s.items, open);
            else if (s.list) addSongs(a, into, title, s.items, open, hover);
            else addRow(a, into, title, s.items, videos ? 118 : 136, open, hover);
        }
    }

    /** Colour tiles in rows of four. */
    public static void addTiles(Activity a, LinearLayout into, String title, List<MItem> tiles, Opener open) {
        if (title != null && !title.isEmpty()) into.addView(heading(a, title));
        int W = Math.round(a.getResources().getDisplayMetrics().widthPixels / a.getResources().getDisplayMetrics().density);
        int gap = 16, w = (W - PAD * 2 - gap * 3) / 4, h = Math.round(w * 0.52f);
        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(a, PAD), dp(a, 12), dp(a, PAD), dp(a, 6));
        grid.setClipChildren(false);
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(a);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = dp(a, gap);
                grid.addView(row, rl);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, w), dp(a, h));
            if (i % 4 != 3) lp.rightMargin = dp(a, gap);
            row.addView(categoryTile(a, tiles.get(i), dp(a, w), dp(a, h), open), lp);
        }
        into.addView(grid);
    }

    static String kindLine(MItem m) {
        String s = m.subtitle;
        if (s.isEmpty()) {
            if (MItem.ARTIST.equals(m.type)) return "Artist";
            return m.artist;
        }
        if (MItem.ARTIST.equals(m.type) && !s.toLowerCase(java.util.Locale.US).startsWith("artist")) return "Artist • " + s;
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
            int songs = 0;
            if (context != null) for (MItem x : context) if (x.playable()) songs++;
            if (songs >= 3) mp.playList(context, Math.max(0, context.indexOf(m)), null);
            else mp.playRadio(m);
            a.startActivity(MusicPlayer.nowPlaying(a));
            return;
        }
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

    /** A list of choices in LT Music's own dialog. */
    public static void choose(Activity a, String title, String[] labels, Runnable[] acts) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final LtDialog[] d = new LtDialog[1];
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView t = text(a, 18, Color.WHITE, false);
            t.setText(labels[i]);
            t.setFocusable(true);
            t.setPadding(dp(a, 18), dp(a, 13), dp(a, 18), dp(a, 13));
            t.setBackground(round(0, dp(a, 8)));
            t.setOnFocusChangeListener((v, has) -> { v.setBackground(round(has ? Color.WHITE : 0, dp(a, 8))); ((TextView) v).setTextColor(has ? 0xFF121212 : Color.WHITE); });
            t.setOnClickListener(v -> { if (d[0] != null) d[0].dismiss(); acts[k].run(); });
            box.addView(t);
        }
        d[0] = new LtDialog.Builder(a).setTitle(title).setView(box).show();
    }

    /** Menu button on a song. */
    public static void options(Activity a, MItem m) {
        if (!m.playable()) return;
        MusicPlayer mp = MusicPlayer.get(a);
        choose(a, m.title, new String[]{"Play next", "Add to queue", "Go to song radio"}, new Runnable[]{
                () -> { mp.playNext(m); android.widget.Toast.makeText(a, "Plays next", android.widget.Toast.LENGTH_SHORT).show(); },
                () -> { mp.addToQueue(m); android.widget.Toast.makeText(a, "Added to queue", android.widget.Toast.LENGTH_SHORT).show(); },
                () -> { mp.playRadio(m); a.startActivity(MusicPlayer.nowPlaying(a)); }});
    }

    /** The app's name with its note badge. */
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
        l.addView(n, new LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)));
        TextView t = text(c, 20, Color.WHITE, true);
        t.setText("LT Music");
        t.setPadding(dp(c, 10), 0, 0, 0);
        l.addView(t);
        return l;
    }

    private MusicUi() { }
}
