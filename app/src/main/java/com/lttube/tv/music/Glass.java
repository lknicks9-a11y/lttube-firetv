package com.lttube.tv.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * Liquid glass: a frame that shows a blurred, slightly brightened copy of whatever is scrolling underneath it,
 * with a soft white sheen across the top and a thin bright rim, like Apple's glass controls. The copy is made from
 * the view behind (drawn small, then blurred), refreshed as things move, so covers really do slide by under it.
 */
public class Glass extends FrameLayout {
    private static final int SCALE = 8;
    private final View source;
    private final View page;
    private final float radius;
    private Bitmap small;
    private Canvas sc;
    private boolean broken;
    private long last;
    private long sum;
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final int[] gl = new int[2], sl = new int[2];

    private final ViewTreeObserver.OnPreDrawListener watch = () -> { refresh(false); return true; };

    /** source: the scrolling page under the glass; page: the view whose background colour is behind everything. */
    public Glass(Context c, View source, View page, float radiusPx) {
        super(c);
        this.source = source;
        this.page = page;
        this.radius = radiusPx;
        setWillNotDraw(false);
        // A little more colour and light than what's behind, like looking through polished glass.
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.5f);
        ColorMatrix bright = new ColorMatrix(new float[]{1.06f, 0, 0, 0, 14, 0, 1.06f, 0, 0, 14, 0, 0, 1.06f, 0, 14, 0, 0, 0, 1, 0});
        cm.postConcat(bright);
        img.setColorFilter(new ColorMatrixColorFilter(cm));
        fill.setColor(0x1FFFFFFF);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, radiusPx / 18f));
        // No drop shadow: glass floats; a shadow only showed as a dark smudge beside it.
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(watch);
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(watch);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        box.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        sheen.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x40FFFFFF, 0x0DFFFFFF, 0x00FFFFFF, 0x14FFFFFF}, new float[]{0f, 0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x8CFFFFFF, 0x26FFFFFF, 0x14FFFFFF, 0x40FFFFFF}, new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        refresh(true);
    }

    /** Re-copies what's under the glass (at most ~30 times a second, and only redraws if it changed). */
    private void refresh(boolean force) {
        if (broken) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (!force && now - last < 33) return;
        last = now;
        int bw = Math.max(1, w / SCALE), bh = Math.max(1, h / SCALE);
        if (small == null || small.getWidth() != bw || small.getHeight() != bh) {
            small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            sc = new Canvas(small);
        }
        Drawable bg = page.getBackground();
        small.eraseColor(bg instanceof ColorDrawable ? ((ColorDrawable) bg).getColor() : MusicUi.BG);
        getLocationInWindow(gl);
        source.getLocationInWindow(sl);
        int save = sc.save();
        sc.scale(1f / SCALE, 1f / SCALE);
        sc.translate(sl[0] - gl[0], sl[1] - gl[1]);
        sc.clipRect(new Rect(gl[0] - sl[0], gl[1] - sl[1], gl[0] - sl[0] + w, gl[1] - sl[1] + h));
        // Drawn directly, a scrolling view doesn't apply its own scroll: do it here.
        sc.translate(-source.getScrollX(), -source.getScrollY());
        try { source.draw(sc); } catch (Throwable e) { broken = true; }
        sc.restoreToCount(save);
        blur(small, 2);
        blur(small, 2);
        long s = 0;
        int[] px = new int[bw * bh];
        small.getPixels(px, 0, bw, 0, 0, bw, bh);
        for (int i = 0; i < px.length; i += 3) s = s * 31 + px[i];
        if (force || s != sum) { sum = s; invalidate(); }
    }

    /** A quick box blur on the small copy (on top of the shrinking, that's a big soft blur). */
    private static void blur(Bitmap b, int r) {
        int w = b.getWidth(), h = b.getHeight();
        int[] p = new int[w * h], o = new int[w * h];
        b.getPixels(p, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            boolean horiz = pass == 0;
            int[] src = horiz ? p : o, dst = horiz ? o : p;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int ra = 0, ga = 0, ba = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = horiz ? Math.min(w - 1, Math.max(0, x + k)) : x, yy = horiz ? y : Math.min(h - 1, Math.max(0, y + k));
                    int c = src[yy * w + xx];
                    ra += (c >> 16) & 255; ga += (c >> 8) & 255; ba += c & 255; n++;
                }
                dst[y * w + x] = 0xFF000000 | ((ra / n) << 16) | ((ga / n) << 8) | (ba / n);
            }
        }
        b.setPixels(p, 0, w, 0, 0, w, h);
    }

    @Override protected void onDraw(Canvas c) {
        int save = c.save();
        c.clipPath(clip);
        if (small != null && !broken) c.drawBitmap(small, null, box, img);
        else c.drawColor(0x55303034);
        c.drawRect(box, fill);
        c.drawRect(box, sheen);
        c.restoreToCount(save);
        float in = rim.getStrokeWidth() / 2f;
        c.drawRoundRect(in, in, getWidth() - in, getHeight() - in, radius - in, radius - in, rim);
        super.onDraw(c);
    }

    @Override public void setBackground(Drawable d) { /* the glass is its own background */ }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
    }
}
