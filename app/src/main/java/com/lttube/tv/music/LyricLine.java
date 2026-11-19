package com.lttube.tv.music;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Layout;
import android.widget.TextView;

/** A lyric line whose light sweeps across it as it's sung (the rest of it stays dim). */
public class LyricLine extends TextView {
    private float progress = -1f;
    private int dim = 0x66FFFFFF;

    public LyricLine(Context c) { super(c); }

    /** -1: not the current line (plain colour); 0..1: how far through it the singing is. */
    public void setProgress(float p, int dimColor) {
        if (p == progress && dimColor == dim) return;
        progress = p;
        dim = dimColor;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        Layout l = getLayout();
        if (progress < 0 || l == null) { super.onDraw(c); return; }
        // Draw the text straight from its layout: once dim, then the lit part again in white on top.
        int left = getCompoundPaddingLeft(), top = getExtendedPaddingTop();
        int keep = getPaint().getColor();
        c.save();
        c.translate(left, top);
        getPaint().setColor(dim);
        l.draw(c);
        float total = 0;
        for (int i = 0; i < l.getLineCount(); i++) total += l.getLineWidth(i);
        float lit = total * progress;
        getPaint().setColor(0xFFFFFFFF);
        for (int i = 0; i < l.getLineCount() && lit > 0; i++) {
            float w = l.getLineWidth(i), x0 = l.getLineLeft(i), take = Math.min(w, lit);
            lit -= take;
            c.save();
            c.clipRect(x0, l.getLineTop(i), x0 + take, l.getLineBottom(i));
            l.draw(c);
            c.restore();
        }
        c.restore();
        getPaint().setColor(keep);
    }

    @Override public void setTextColor(int color) { super.setTextColor(color); dim = color; }
}
