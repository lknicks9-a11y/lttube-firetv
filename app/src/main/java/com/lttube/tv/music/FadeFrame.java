package com.lttube.tv.music;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.widget.FrameLayout;

/** A frame whose contents fade out softly at the top and bottom edges (the lyrics drift in and out of view). */
public class FadeFrame extends FrameLayout {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int topFade, bottomFade;

    public FadeFrame(Context c, int topFadePx, int bottomFadePx) {
        super(c);
        topFade = topFadePx;
        bottomFade = bottomFadePx;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /**
     * The lyrics inside are as tall as the whole song, not squeezed into this window's height (squeezing left later
     * lines with no room at all, so they vanished). The window only shows a slice of them.
     */
    @Override protected void measureChildWithMargins(android.view.View child, int wSpec, int wUsed, int hSpec, int hUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int cw = getChildMeasureSpec(wSpec, getPaddingLeft() + getPaddingRight() + lp.leftMargin + lp.rightMargin + wUsed, lp.width);
        child.measure(cw, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        int w = getWidth(), h = getHeight();
        // Eased fades (most of the softening near the edge), so lines dissolve rather than hit a line.
        p.setShader(new LinearGradient(0, 0, 0, topFade, new int[]{0xFF000000, 0xB3000000, 0x40000000, 0x00000000}, new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, topFade, p);
        p.setShader(new LinearGradient(0, h - bottomFade, 0, h, new int[]{0x00000000, 0x40000000, 0xB3000000, 0xFF000000}, new float[]{0f, 0.3f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, h - bottomFade, w, h, p);
    }
}
