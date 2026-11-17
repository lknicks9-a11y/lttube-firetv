package com.lttube.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** YouTube-style progress bar: buffered, played, chapter gaps, and a knob. */
public class TimeBar extends View {
    long duration, position, buffered, scrub = -1;
    long[] chapters = new long[0];
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public TimeBar(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setOnFocusChangeListener((v, has) -> invalidate());
    }

    public long shown() { return scrub >= 0 ? scrub : position; }

    @Override
    protected void onDraw(Canvas c) {
        float pad = 12 * d;               // room for the knob at both ends
        c.save();
        c.translate(pad, 0);
        float w = getWidth() - 2 * pad, h = getHeight();
        boolean f = isFocused();
        float th = (f ? 6 : 4) * d;
        float cy = h / 2f;
        float top = cy - th / 2, bottom = cy + th / 2;
        float r = th / 2;
        p.setColor(0x4DFFFFFF);
        c.drawRoundRect(0, top, w, bottom, r, r, p);
        if (duration > 0) {
            p.setColor(0x80FFFFFF);
            c.drawRoundRect(0, top, w * Math.min(1f, buffered / (float) duration), bottom, r, r, p);
            float px = w * Math.min(1f, shown() / (float) duration);
            p.setColor(0xFFFF0033);
            c.drawRoundRect(0, top, px, bottom, r, r, p);
            // chapter gaps
            p.setColor(0xFF000000);
            for (long ch : chapters) {
                if (ch <= 0 || ch >= duration) continue;
                float x = w * ch / (float) duration;
                c.drawRect(x - d, top - 1, x + d, bottom + 1, p);
            }
            p.setColor(0xFFFF0033);
            c.drawCircle(px, cy, (f ? 10 : 7) * d, p);
            if (f) {
                p.setColor(0xFFFFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * d);
                c.drawCircle(px, cy, 10 * d, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        c.restore();
    }
}
