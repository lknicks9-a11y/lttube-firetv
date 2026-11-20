package com.lttube.tv.music;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/**
 * The song's cover, blown up and blurred into a soft wash of its colours that slowly drifts — the background of
 * Now Playing, lyrics and album pages. (Blur comes cheaply: a 16-pixel copy of the cover, smoothly scaled up.)
 */
public class Backdrop extends FrameLayout {
    private final ImageView img;
    private final View shade;
    private String url;
    private ObjectAnimator spin;

    public Backdrop(Context c) {
        super(c);
        setBackgroundColor(0xFF15161A);
        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(img, new LayoutParams(-1, -1));
        shade = new View(c);
        addView(shade, new LayoutParams(-1, -1));
        setMood(false);
    }

    /** vivid: the sing-along look (richer colours, lighter shade); otherwise calmer and darker. */
    public void setMood(boolean vivid) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(vivid ? 1.7f : 1.15f);
        img.setColorFilter(new ColorMatrixColorFilter(m));
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                vivid ? new int[]{0x0D000000, 0x14000000, 0x40000000} : new int[]{0x66000000, 0x80000000, 0xCC000000}));
    }

    public void setArt(String u) {
        if (u == null || u.isEmpty() || u.equals(url)) return;
        url = u;
        Glide.with(this).load(u).override(16, 16).transition(DrawableTransitionOptions.withCrossFade(700)).into(img);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Slow drift, so the colours move like a living background.
        img.setScaleX(2.6f);
        img.setScaleY(2.6f);
        spin = ObjectAnimator.ofFloat(img, View.ROTATION, 0f, 360f);
        spin.setDuration(120_000);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        // Still (no constant spinning): redrawing the whole screen every frame took processor time from the music.
    }

    @Override protected void onDetachedFromWindow() {
        if (spin != null) spin.cancel();
        super.onDetachedFromWindow();
    }
}
