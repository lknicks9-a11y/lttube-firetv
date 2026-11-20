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
    /** Text: main (92% white), secondary (64%), tertiary (40%), as on the big music apps. */
    public static final int TEXT = 0xEBFFFFFF, TEXT2 = 0xA3FFFFFF, TEXT3 = 0x66FFFFFF;
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

    /** Semi-bold (titles on cards, labels, buttons). */
    public static TextView medium(Context c, float sp, int color) {
        TextView t = text(c, sp, color, false);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** How far the main screen's sidebar pushes the page in (0 elsewhere). */
    public static int inset;

    /** The width the page has to lay itself out in. */
    public static int screenDp(Context a) {
        return Math.round(a.getResources().getDisplayMetrics().widthPixels / a.getResources().getDisplayMetrics().density) - inset;
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
    public static final int BG = 0xFF1F1F1F;
    /** An empty cover's grey. */
    public static final int EMPTY = 0xFF323232;

    public static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG, BG});
    }

    public static GradientDrawable background(int ignored) { return background(); }

    /** A deep shade of a cover's colour, for the page behind it (like the colour wash on big-screen music apps). */
    public static int tintOf(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        // The cover's own colour, deep enough for white text to read on.
        hsv[1] = Math.min(0.75f, Math.max(0.25f, hsv[1]));
        hsv[2] = 0.36f;
        return Color.HSVToColor(hsv);
    }

    /** Fades a page's background to a new colour. */
    public static void fadeTo(View v, int to) { fadeTo(v, to, null); }

    /** Fades a page to a colour; each step also goes to `also` (the big picture's fades), so they never differ. */
    public static void fadeTo(View v, int to, ColorDone also) {
        Object tag = v.getTag(R.id.tag_color);
        int from = tag instanceof Integer ? (Integer) tag : BG;
        Object run = v.getTag(R.id.tag_tint);
        if (run instanceof android.animation.ValueAnimator) ((android.animation.ValueAnimator) run).cancel();
        if (from == to) { v.setBackgroundColor(to); if (also != null) also.color(to); return; }
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofArgb(from, to);
        a.setDuration(450);
        a.addUpdateListener(an -> {
            int col = (int) an.getAnimatedValue();
            v.setTag(R.id.tag_color, col);
            v.setBackgroundColor(col);
            if (also != null) also.color(col);
        });
        v.setTag(R.id.tag_tint, a);
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
        // No growing: the outline stays inside the item, so nothing around it ever cuts it off.
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
        // Videos are 16:9 and a little wider than a cover; covers have just-rounded corners.
        int w = wide ? Math.round(size * 1.54f) : size, h = wide ? Math.round(size * 1.54f * 9f / 16f) : size;
        if (round) circle(iv, m.thumb); else art(iv, m.thumb, dp(a, 5));
        if (round && !m.thumb.contains("mzstatic.com")) artistPhoto(iv, m);
        iv.setOnFocusChangeListener((x, has) -> { markFocus(x, has, dp(a, 5), round, 1.05f); if (has && hover != null) hover.on(m); });
        iv.setOnClickListener(v -> open.open(m, ctx));
        iv.setOnKeyListener((v, k, e) -> {
            if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { options(a, m); return true; }
            return false;
        });
        col.addView(iv, new LinearLayout.LayoutParams(w, h));
        TextView t = line(a, 14, TEXT, false);
        t.setText(m.title);
        t.setGravity(round ? Gravity.CENTER : Gravity.START);
        t.setPadding(0, dp(a, 8), 0, 0);
        col.addView(t, new LinearLayout.LayoutParams(w, -2));
        TextView s = line(a, 13, TEXT2, false);
        s.setText(kindLine(m));
        s.setGravity(round ? Gravity.CENTER : Gravity.START);
        s.setPadding(0, dp(a, 2), 0, 0);
        col.addView(s, new LinearLayout.LayoutParams(w, -2));
        return col;
    }

    /** Every artist shows their Apple Music photo (found by their Apple id, or by name). */
    static void artistPhoto(ImageView iv, MItem m) {
        final String key = m.browseId + "|" + m.title;
        iv.setTag(R.id.tag_color, key);
        com.lttube.tv.Bg.run(() -> {
            String id = m.browseId.startsWith("AMA:") ? m.browseId.substring(4) : AppleMusic.artistId(m.title);
            return id == null ? null : AppleWeb.artistPhoto(id, 600);
        }, (u, e) -> {
            if (u == null || u.isEmpty() || !key.equals(iv.getTag(R.id.tag_color))) return;
            m.thumb = u;
            circle(iv, u);
        });
    }

    /**
     * Top Picks: tall 3:4 cards. The cover fills the top, and the bottom takes on the cover's colour, with a small
     * label saying why it's there ("Heavy Rotation", "Made For You", "Featuring …", "New Release") over the name.
     */
    public static View pickRow(Activity a, List<Object[]> picks, Opener open, Hover hover) {
        LinearLayout row = new LinearLayout(a);
        row.setClipChildren(false);
        int gap = 14, w = (screenDp(a) - PAD * 2 - gap * 4) / 5, h = Math.round(w * 4f / 3f);
        List<MItem> ctx = new java.util.ArrayList<>();
        for (Object[] p : picks) ctx.add((MItem) p[1]);
        for (Object[] p : picks) {
            final MItem m = (MItem) p[1];
            FrameLayout card = new FrameLayout(a);
            card.setFocusable(true);
            card.setClipToOutline(true);
            final GradientDrawable bg = round(EMPTY, dp(a, 8));
            card.setBackground(bg);
            ImageView iv = new ImageView(a);
            if (MItem.ARTIST.equals(m.type)) {
                int cs = Math.round(w * 0.62f);
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(dp(a, cs), dp(a, cs), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
                cl.topMargin = dp(a, (w - cs) / 2f);
                card.addView(iv, cl);
                circle(iv, m.thumb);
                if (!m.thumb.contains("mzstatic.com")) artistPhoto(iv, m);
            } else {
                art(iv, m.thumb, 0);
                card.addView(iv, new FrameLayout.LayoutParams(dp(a, w), dp(a, w), Gravity.TOP));
            }
            // The cover melts into the colour below it.
            final View melt = new View(a);
            FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(-1, dp(a, w * 0.34f), Gravity.TOP);
            ml.topMargin = dp(a, w * 0.66f + 1);
            card.addView(melt, ml);
            LinearLayout txt = new LinearLayout(a);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setGravity(Gravity.BOTTOM);
            txt.setPadding(dp(a, 10), 0, dp(a, 10), dp(a, 10));
            TextView eye = line(a, 11, Color.WHITE, false);
            eye.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            eye.setText((String) p[0]);
            txt.addView(eye);
            TextView t = line(a, 14, Color.WHITE, false);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setText(m.title);
            t.setPadding(0, dp(a, 2), 0, 0);
            txt.addView(t);
            TextView sub = text(a, 12, 0xEBFFFFFF, false);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setText(MItem.ARTIST.equals(m.type) ? "Artist" : !m.artist.isEmpty() ? m.artist : !m.subtitle.isEmpty() ? m.subtitle : kindLine(m));
            sub.setPadding(0, dp(a, 2), 0, 0);
            txt.addView(sub);
            card.addView(txt, new FrameLayout.LayoutParams(-1, dp(a, h - w * 0.86f), Gravity.BOTTOM));
            colorOf(a, m.thumb, c -> {
                int chin = blend(c, 0xFF000000, 0.18f);
                bg.setColor(chin);
                melt.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{chin & 0x00FFFFFF, chin}));
            });
            card.setOnFocusChangeListener((x, has) -> { markFocus(x, has, dp(a, 8), false, 1f); if (has && hover != null) hover.on(m); });
            card.setOnClickListener(v -> open.open(m, ctx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, w), dp(a, h));
            lp.rightMargin = dp(a, gap);
            row.addView(card, lp);
        }
        return hscroll(a, row);
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

    /** The main button: white, with dark text ("Play"). */
    public static TextView playPill(Context c, String label) {
        TextView t = medium(c, 16, 0xFF000000);
        t.setText(label);
        t.setFocusable(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 20), 0, dp(c, 24), 0);
        Drawable d = c.getDrawable(R.drawable.ic_m_play).mutate();
        d.setBounds(0, 0, dp(c, 16), dp(c, 16));
        d.setTint(0xFF000000);
        t.setCompoundDrawables(d, null, null, null);
        t.setCompoundDrawablePadding(dp(c, 6));
        t.setMinWidth(dp(c, 124));
        t.setTag(R.id.tag_tint, "white");
        paintPill(t, false);
        t.setOnFocusChangeListener((v, has) -> paintPill((TextView) v, has));
        return t;
    }

    private static void paintPill(TextView t, boolean focus) {
        Context c = t.getContext();
        if ("white".equals(t.getTag(R.id.tag_tint))) {
            GradientDrawable g = round(focus ? Color.WHITE : 0xF2FFFFFF, dp(c, 24));
            if (focus) g.setStroke(dp(c, 3), 0xFF9A9A9A);
            t.setBackground(g);
            return;
        }
        GradientDrawable g = round(focus ? 0x59FFFFFF : 0x26FFFFFF, dp(c, 24));
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
        g.setColor(f ? 0x66FFFFFF : 0x26FFFFFF);
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
    public static View songRow(Context a, MItem m, int number, boolean withTime) { return songRow(a, m, number, withTime, true); }

    /**
     * One song in a list. withTime: the full-width page list (song | artist | album | time, or on an album
     * number | song | time); otherwise the compact kind (cover, title over artist) used in rows of songs.
     */
    public static View songRow(Context a, MItem m, int number, boolean withTime, boolean withArt) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(a, 12), 0, dp(a, 16), 0);
        r.setBackground(round(0, dp(a, 6)));
        r.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = round(has ? 0x33FFFFFF : 0, dp(a, 6));
            if (has) g.setStroke(dp(a, 2), Color.WHITE);
            v.setBackground(g);
        });
        if (number > 0) {
            TextView n = text(a, 15, TEXT2, false);
            n.setText(String.valueOf(number));
            n.setGravity(Gravity.CENTER);
            r.addView(n, new LinearLayout.LayoutParams(dp(a, 34), -2));
        }
        if (withArt) {
            ImageView iv = new ImageView(a);
            art(iv, m.thumb, dp(a, 4));
            int s = withTime ? 42 : 46;
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(a, s), dp(a, s));
            il.leftMargin = number > 0 ? dp(a, 6) : 0;
            r.addView(iv, il);
        }
        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(a, withArt || number > 0 ? 14 : 4), 0, dp(a, 12), 0);
        TextView t = line(a, 16, TEXT, false);
        t.setText(m.title);
        txt.addView(t);
        String who = m.artist.isEmpty() ? m.subtitle : m.artist;
        boolean columns = withTime && withArt;
        if (!columns && !who.isEmpty()) {
            TextView s = line(a, 13, TEXT2, false);
            s.setText(who);
            s.setPadding(0, dp(a, 3), 0, 0);
            txt.addView(s);
        }
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1.4f));
        if (columns) {
            TextView ar = line(a, 15, TEXT2, false);
            ar.setText(who);
            ar.setPadding(0, 0, dp(a, 12), 0);
            r.addView(ar, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView al = line(a, 15, TEXT2, false);
            al.setText(m.album);
            al.setPadding(0, 0, dp(a, 12), 0);
            r.addView(al, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (withTime) {
            TextView d = text(a, 15, TEXT2, false);
            d.setText(m.seconds > 0 ? MItem.time(m.seconds) : "");
            d.setGravity(Gravity.END);
            r.addView(d, new LinearLayout.LayoutParams(dp(a, 56), -2));
        }
        return r;
    }

    /** The column names over a playlist's songs. */
    public static View songHeader(Context a, boolean numbered) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(a, 12), 0, dp(a, 16), 0);
        String[] names = numbered ? new String[]{"Song", "Time"} : new String[]{"Song", "Artist", "Album", "Time"};
        for (int i = 0; i < names.length; i++) {
            TextView t = medium(a, 13, TEXT2);
            t.setText(names[i]);
            boolean last = i == names.length - 1;
            if (last) t.setGravity(Gravity.END);
            LinearLayout.LayoutParams lp = last ? new LinearLayout.LayoutParams(dp(a, 56), -2)
                    : new LinearLayout.LayoutParams(0, -2, i == 0 ? 1.4f : 1f);
            if (i == 0) t.setPadding(numbered ? dp(a, 38) : 0, 0, 0, 0);
            r.addView(t, lp);
        }
        return r;
    }

    // ------------------------------------------------------------------ rows
    /** A titled row of covers that scrolls sideways. */
    public static void addRow(Activity a, LinearLayout into, String title, List<MItem> items, int size, Opener open, Hover hover) {
        addRow(a, into, title, items, size, open, hover, null);
    }

    /** more: a "See all" card at the end of the row, opening everything in it. */
    public static void addRow(Activity a, LinearLayout into, String title, List<MItem> items, int size, Opener open, Hover hover, MItem more) {
        if (items.isEmpty()) return;
        if (title != null && !title.isEmpty()) into.addView(heading(a, title));
        LinearLayout row = new LinearLayout(a);
        row.setClipChildren(false);
        for (MItem m : items) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(a, 14);
            row.addView(card(a, m, dp(a, size), items, open, hover), lp);
        }
        if (more != null) row.addView(seeAll(a, more, dp(a, size), open));
        into.addView(hscroll(a, row));
    }

    private static View seeAll(Activity a, MItem more, int size, Opener open) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(a, 17, Color.WHITE, true);
        t.setText("See all");
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setBackground(round(0x33FFFFFF, dp(a, 6)));
        t.setOnFocusChangeListener((x, has) -> { x.setBackground(round(has ? 0x59FFFFFF : 0x33FFFFFF, dp(a, 6))); markFocus(x, has, dp(a, 6), false, 1.0f); });
        t.setOnClickListener(v -> open.open(more, null));
        col.addView(t, new LinearLayout.LayoutParams(size, size));
        return col;
    }

    /** Everything as a wrapping grid (a "See all" page, Your Library). */
    public static void addGrid(Activity a, LinearLayout into, List<MItem> items, int cols, Opener open) {
        int W = screenDp(a);
        int gap = 18, size = (W - PAD * 2 - gap * (cols - 1)) / cols;
        LinearLayout g = new LinearLayout(a);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(dp(a, PAD), dp(a, 14), dp(a, PAD), dp(a, 40));
        g.setClipChildren(false);
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(a);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = dp(a, 18);
                g.addView(row, rl);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, size), -2);
            if (i % cols != cols - 1) lp.rightMargin = dp(a, gap);
            row.addView(card(a, items.get(i), dp(a, size), items, open, null), lp);
        }
        into.addView(g);
    }

    /**
     * Up / down one row at a time: to the nearest row above / below, onto the item closest to where you are
     * across. (The TV's own guess sometimes jumps a row, or lands off to the side.) False when there's no row
     * that way inside `area`.
     */
    public static boolean moveVertical(View area, View from, boolean up) {
        if (from == null) return false;
        List<View> all = new java.util.ArrayList<>();
        focusables(area, all);
        int[] p = new int[2];
        from.getLocationOnScreen(p);
        float fy = p[1] + from.getHeight() / 2f, fx = p[0] + from.getWidth() / 2f;
        int screenW = area.getResources().getDisplayMetrics().widthPixels;
        float bestRow = up ? -Float.MAX_VALUE : Float.MAX_VALUE;
        for (View v : all) {
            if (v == from) continue;
            v.getLocationOnScreen(p);
            float cy = p[1] + v.getHeight() / 2f;
            float gap = Math.max(8, from.getHeight() * 0.4f);
            if (up ? cy < fy - gap && cy > bestRow : cy > fy + gap && cy < bestRow) bestRow = cy;
        }
        if (bestRow == Float.MAX_VALUE || bestRow == -Float.MAX_VALUE) return false;
        View best = null;
        float bestDx = Float.MAX_VALUE;
        for (View v : all) {
            v.getLocationOnScreen(p);
            float cy = p[1] + v.getHeight() / 2f;
            if (Math.abs(cy - bestRow) > Math.max(12, v.getHeight() * 0.35f)) continue;
            float cx = p[0] + v.getWidth() / 2f;
            float dx = Math.abs(cx - fx) + (cx < 0 || cx > screenW ? screenW : 0);
            if (dx < bestDx) { bestDx = dx; best = v; }
        }
        if (best == null || !best.requestFocus()) return false;
        // Keep it in the middle of the screen, so whatever is under it (titles, the next row) shows too.
        final View target = best;
        for (Object p0 = best.getParent(); p0 instanceof View; p0 = ((View) p0).getParent()) {
            if (p0 instanceof android.widget.ScrollView) {
                final android.widget.ScrollView sv = (android.widget.ScrollView) p0;
                android.graphics.Rect r = new android.graphics.Rect();
                target.getDrawingRect(r);
                sv.offsetDescendantRectToMyCoords(target, r);
                int y = r.centerY() - sv.getHeight() / 2;
                int max = Math.max(0, sv.getChildAt(0).getHeight() - sv.getHeight());
                sv.smoothScrollTo(0, Math.max(0, Math.min(max, y)));
                break;
            }
        }
        return true;
    }

    private static void focusables(View v, List<View> out) {
        if (v.getVisibility() != View.VISIBLE || v.getAlpha() == 0f) return;
        // A row that scrolls sideways is itself "focusable" but holds the real items: look inside it.
        boolean container = v instanceof ViewGroup && hasFocusableInside((ViewGroup) v);
        if (v.isFocusable() && v.getWidth() > 0 && !container) { out.add(v); return; }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) focusables(((ViewGroup) v).getChildAt(i), out);
    }

    private static boolean hasFocusableInside(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.isFocusable() || (c instanceof ViewGroup && hasFocusableInside((ViewGroup) c))) return true;
        }
        return false;
    }

    public static TextView heading(Context a, String title) {
        TextView h = text(a, 19, TEXT, true);
        h.setText(title);
        h.setPadding(dp(a, PAD), dp(a, 22), dp(a, PAD), dp(a, 0));
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
        addSongs(a, into, title, songs, open, hover, 4);
    }

    /** Song lists in columns of `rows` (an artist's Top Songs: three to a column, three columns across). */
    public static void addSongs(Activity a, LinearLayout into, String title, List<MItem> songs, Opener open, Hover hover, int rows) {
        if (songs.isEmpty()) return;
        if (title != null && !title.isEmpty()) into.addView(heading(a, title));
        LinearLayout row = new LinearLayout(a);
        LinearLayout col = null;
        for (int i = 0; i < songs.size(); i++) {
            if (i % rows == 0) {
                col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, (screenDp(a) - PAD * 2 - 32) / 3), -2);
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
            else {
                MItem more = null;
                if (!s.moreId.isEmpty()) {
                    more = new MItem();
                    more.type = MItem.MORE;
                    more.title = title;
                    more.browseId = s.moreId;
                    more.params = s.moreParams;
                }
                boolean people = MItem.ARTIST.equals(s.items.get(0).type);
                addRow(a, into, title, s.items, videos ? 138 : people ? 112 : 138, open, hover, more);
            }
        }
    }

    /** Colour tiles in rows of four. */
    public static void addTiles(Activity a, LinearLayout into, String title, List<MItem> tiles, Opener open) {
        if (title != null && !title.isEmpty()) into.addView(heading(a, title));
        int W = screenDp(a);
        int gap = 16, w = (W - PAD * 2 - gap * 3) / 4, h = Math.round(w * 9f / 16f);
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
            Mixes.visited(a, m);
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
