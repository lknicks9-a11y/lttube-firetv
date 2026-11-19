package com.lttube.tv.music;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/**
 * The big picture at the top of Home and of album / artist pages. A square cover is shown whole on the right
 * (never cropped); an artist's wide banner spans the width. Either way it melts into the page's background on the
 * left and bottom, so there are no hard edges.
 */
public class Hero extends FrameLayout {
    private final ImageView img;
    private final View fadeLeft, fadeBottom, fadeTop;
    private String url;
    private int bg;

    public Hero(Context c) {
        super(c);
        bg = MusicUi.BG;
        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(img, new LayoutParams(-1, -1, Gravity.END | Gravity.TOP));
        fadeLeft = new View(c);
        addView(fadeLeft);
        fadeBottom = new View(c);
        addView(fadeBottom);
        fadeTop = new View(c);
        addView(fadeTop);
        paintFades();
    }

    /** The page's colour changed (it follows the cover): the fades follow so there are never seams. */
    public void setTint(int color) {
        if (color == bg) return;
        bg = color;
        paintFades();
    }

    private void paintFades() {
        fadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{bg, bg & 0xCCFFFFFF, bg & 0x66FFFFFF, bg & 0x00FFFFFF}));
        fadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{bg, bg & 0xB3FFFFFF, bg & 0x00FFFFFF}));
        fadeTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bg & 0x99FFFFFF, bg & 0x00FFFFFF}));
        setBackgroundColor(url == null ? 0 : bg);
    }

    /** wide: an artist banner (fills the width); otherwise a square cover kept whole on the right. */
    public void set(String u, boolean wide) {
        if (u == null || u.isEmpty() || u.equals(url)) return;
        url = u;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) { post(() -> { url = null; set(u, wide); }); return; }
        int iw = wide ? w : Math.min(w, Math.round(h * 1.0f));
        img.setLayoutParams(new LayoutParams(iw, h, Gravity.END | Gravity.TOP));
        int fl = Math.round(iw * (wide ? 0.55f : 0.45f));
        LayoutParams lp = new LayoutParams(fl, h, Gravity.TOP | Gravity.START);
        lp.leftMargin = w - iw;
        fadeLeft.setLayoutParams(lp);
        // Everything left of the picture is plain background.
        setBackgroundColor(bg);
        fadeBottom.setLayoutParams(new LayoutParams(-1, Math.round(h * 0.42f), Gravity.BOTTOM));
        fadeTop.setLayoutParams(new LayoutParams(-1, Math.round(h * 0.22f), Gravity.TOP));
        Glide.with(this).load(u).transition(DrawableTransitionOptions.withCrossFade(350)).into(img);
    }
}
