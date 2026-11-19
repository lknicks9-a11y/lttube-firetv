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

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        int w = getWidth(), h = getHeight();
        p.setShader(new LinearGradient(0, 0, 0, topFade, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, topFade, p);
        p.setShader(new LinearGradient(0, h - bottomFade, 0, h, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h - bottomFade, w, h, p);
    }
}
