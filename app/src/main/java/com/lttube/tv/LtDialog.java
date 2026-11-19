package com.lttube.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * LTTube's own pop-up (dark, rounded, pill buttons) instead of Fire TV's grey system dialog. Same calls as
 * AlertDialog.Builder for what the app uses: title, message, a view, up to three buttons.
 */
public class LtDialog extends Dialog {
    private final TextView title, message;
    private final ScrollView scroll;
    private final LinearLayout body, buttons;

    LtDialog(Context c) {
        super(c);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
        }
        float d = c.getResources().getDisplayMetrics().density;
        int screenH = c.getResources().getDisplayMetrics().heightPixels;

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF51E1F25);
        bg.setCornerRadius(22 * d);
        bg.setStroke((int) d, 0x26FFFFFF);
        card.setBackground(bg);
        card.setPadding((int) (34 * d), (int) (28 * d), (int) (34 * d), (int) (24 * d));

        title = Ui.text(c, 24, Color.WHITE, true);
        title.setVisibility(View.GONE);
        card.addView(title);

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(true);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        message = Ui.text(c, 17, 0xFFD6D7DC, false);
        message.setLineSpacing(0, 1.2f);
        message.setVisibility(View.GONE);
        message.setPadding(0, (int) (12 * d), 0, 0);
        body.addView(message);
        scroll.addView(body);
        // The text scrolls inside the card; the buttons always stay on screen.
        card.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, (int) (20 * d), 0, 0);
        card.addView(buttons);

        FrameLayout outer = new FrameLayout(c);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (680 * d), -2, Gravity.CENTER);
        outer.addView(card, lp);
        setContentView(outer);
        card.setMinimumHeight(0);
        // Never taller than most of the screen.
        card.post(() -> {
            if (card.getHeight() > screenH * 0.86f) {
                ViewGroup.LayoutParams p = card.getLayoutParams();
                p.height = (int) (screenH * 0.86f);
                card.setLayoutParams(p);
            }
        });
    }

    public void setTitle(CharSequence t) {
        title.setText(t);
        title.setVisibility(t == null || t.length() == 0 ? View.GONE : View.VISIBLE);
    }

    public void setMessage(CharSequence m) {
        message.setText(m);
        message.setVisibility(m == null || m.length() == 0 ? View.GONE : View.VISIBLE);
    }

    void setView(View v) {
        float d = getContext().getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = (int) (14 * d);
        body.addView(v, lp);
    }

    void addButton(CharSequence label, final DialogInterface.OnClickListener l, final int which) {
        Context c = getContext();
        float d = c.getResources().getDisplayMetrics().density;
        final TextView b = Ui.text(c, 17, Color.WHITE, true);
        b.setText(label);
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        b.setPadding((int) (26 * d), (int) (11 * d), (int) (26 * d), (int) (11 * d));
        b.setBackground(Ui.pill(c, 0x33FFFFFF, 24));
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(Ui.pill(c, has ? 0xFFF1F1F1 : 0x33FFFFFF, 24));
            b.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
        });
        b.setOnClickListener(v -> {
            if (l != null) l.onClick(this, which);
            dismiss();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = (int) (12 * d);
        // The main button first in focus order but last (rightmost) on screen, like the system dialog.
        if (which == DialogInterface.BUTTON_POSITIVE) buttons.addView(b, lp);
        else buttons.addView(b, 0, lp);
        if (which == DialogInterface.BUTTON_POSITIVE) b.post(b::requestFocus);
    }

    @Override
    public void show() {
        try { super.show(); } catch (Exception ignored) { }
        if (buttons.getChildCount() > 0 && getCurrentFocus() == null) buttons.getChildAt(buttons.getChildCount() - 1).requestFocus();
    }

    public static class Builder {
        private final LtDialog d;
        public Builder(Context c) { d = new LtDialog(c); }
        public Builder(Activity a) { d = new LtDialog(a); }
        public Builder setTitle(CharSequence t) { d.setTitle(t); return this; }
        public Builder setMessage(CharSequence m) { d.setMessage(m); return this; }
        public Builder setView(View v) { d.setView(v); return this; }
        public Builder setCancelable(boolean b) { d.setCancelable(b); return this; }
        public Builder setOnDismissListener(DialogInterface.OnDismissListener l) { d.setOnDismissListener(l); return this; }
        public Builder setOnCancelListener(DialogInterface.OnCancelListener l) { d.setOnCancelListener(l); return this; }
        public Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) { d.addButton(t, l, DialogInterface.BUTTON_POSITIVE); return this; }
        public Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) { d.addButton(t, l, DialogInterface.BUTTON_NEGATIVE); return this; }
        public Builder setNeutralButton(CharSequence t, DialogInterface.OnClickListener l) { d.addButton(t, l, DialogInterface.BUTTON_NEUTRAL); return this; }
        public LtDialog create() { return d; }
        public LtDialog show() { d.show(); return d; }
    }
}
