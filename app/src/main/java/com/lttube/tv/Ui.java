package com.lttube.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/** Small shared look-and-feel helpers (YouTube TV colors, text, pills, QR codes). */
public final class Ui {
    public static final int BG = 0xFF14161E;          // page background
    public static final int NAV = 0xFF181B24;         // side menu
    public static final int SURFACE = 0xFF262A36;     // pills, unselected chips
    public static final int FOCUS = 0xFFF1F1F1;       // focused pill
    public static final int TEXT2 = 0xFFAAAEB8;       // secondary text
    public static final int RED = 0xFFFF0033;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    public static GradientDrawable pill(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable circle(int color, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    /** A QR code bitmap (black on white). */
    public static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, size, 0, 0, size, size);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** The purple-to-pink background of YouTube's sign-in and "Who's watching?" screens. */
    public static class Blobs extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Blobs(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.drawColor(0xFF2A1B4D);
            blob(c, w * 0.15f, h * 0.2f, w * 0.6f, 0xFF3B2470, 0x002A1B4D);
            blob(c, w * 0.85f, h * 0.95f, w * 0.55f, 0xFFE0245E, 0x00E0245E);
            blob(c, w * 0.62f, h * 0.55f, w * 0.38f, 0xCCB4237A, 0x00B4237A);
            blob(c, w * 0.95f, h * 0.15f, w * 0.30f, 0x99F0508C, 0x00F0508C);
        }

        private void blob(Canvas c, float x, float y, float r, int from, int to) {
            p.setShader(new RadialGradient(x, y, r, from, to, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }
    }

    private Ui() { }
}
