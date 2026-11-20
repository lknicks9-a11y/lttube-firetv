package com.lttube.tv.music;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lttube.tv.Bg;

import java.util.List;

/**
 * Search, laid out like Apple Music's: the search bar centred at the top, a one-line keyboard under it, suggestions
 * as you type, then Apple's own results — Top Results (wide tiles, three across), Artists, Albums, Songs,
 * Playlists. With nothing typed: Browse Categories.
 */
public class MusicSearchActivity extends Activity {
    private static final String[] ROWS = {"abcdefghijklm", "nopqrstuvwxyz", "1234567890"};
    private final StringBuilder q = new StringBuilder();
    private TextView field;
    private LinearLayout content, keyRow, hints, results;
    private View firstKey;
    private ScrollView scroll;
    private ProgressBar spinner;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int seq;
    private boolean digits;
    private String shown = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(MusicUi.BG);
        setContentView(root);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(0, dp(MusicUi.TOP), 0, dp(60));
        scroll.addView(content);
        root.addView(scroll, MusicUi.match());

        // The search bar: a pill in the middle.
        field = MusicUi.line(this, 16, MusicUi.TEXT, false);
        field.setHint("Artists, Songs, Lyrics, and More");
        field.setHintTextColor(MusicUi.TEXT3);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(16), 0, dp(16), 0);
        android.graphics.drawable.Drawable si = getDrawable(com.lttube.tv.R.drawable.ic_search).mutate();
        si.setBounds(0, 0, dp(16), dp(16));
        si.setTint(MusicUi.TEXT2);
        field.setCompoundDrawables(si, null, null, null);
        field.setCompoundDrawablePadding(dp(10));
        GradientDrawable fb = MusicUi.round(0x14FFFFFF, dp(20));
        fb.setStroke(Math.max(1, dp(1)), 0x33FFFFFF);
        field.setBackground(fb);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(dp(Math.round(screenW() * 0.46f)), dp(40));
        fl.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(field, fl);

        // The keyboard: three rows (a–m, n–z, numbers with space and delete), centred under the bar.
        keyRow = new LinearLayout(this);
        keyRow.setOrientation(LinearLayout.VERTICAL);
        keyRow.setPadding(dp(MusicUi.PAD + 20), dp(14), dp(MusicUi.PAD + 20), 0);
        content.addView(keyRow);
        buildKeys();

        hints = new LinearLayout(this);
        hints.setClipChildren(false);
        hints.setPadding(dp(MusicUi.PAD), dp(4), dp(MusicUi.PAD), 0);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.addView(hints);
        content.addView(hs, new LinearLayout.LayoutParams(-1, dp(52)));

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        content.addView(results);

        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        categories();
        firstKey.requestFocus();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    private int screenW() { return MusicUi.screenDp(this); }

    private void buildKeys() {
        keyRow.removeAllViews();
        for (int r = 0; r < ROWS.length; r++) {
            LinearLayout row = new LinearLayout(this);
            for (char c : ROWS[r].toCharArray()) row.addView(key(String.valueOf(c), () -> type(String.valueOf(c))), keyLp(1));
            if (r == 2) {
                row.addView(key("space", () -> type(" ")), keyLp(2));
                row.addView(key("\u232B", this::backspace), keyLp(1));
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.bottomMargin = dp(6);
            keyRow.addView(row, rl);
        }
        firstKey = ((LinearLayout) keyRow.getChildAt(0)).getChildAt(0);
    }

    private LinearLayout.LayoutParams keyLp(int span) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), span);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private TextView key(String label, Runnable r) {
        TextView k = MusicUi.medium(this, label.length() > 1 ? 14 : 18, MusicUi.TEXT);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setFocusable(true);
        final GradientDrawable bg = MusicUi.round(0x14FFFFFF, dp(8));
        k.setBackground(bg);
        k.setOnFocusChangeListener((v, has) -> { bg.setColor(has ? Color.WHITE : 0x14FFFFFF); k.setTextColor(has ? 0xFF111111 : MusicUi.TEXT); });
        k.setOnClickListener(v -> r.run());
        return k;
    }

    private void type(String s) { if (q.length() < 80) { q.append(s); changed(); } }

    private void backspace() { if (q.length() > 0) { q.setLength(q.length() - 1); changed(); } }

    private void changed() {
        field.setText(q);
        h.removeCallbacksAndMessages(null);
        final String text = q.toString().trim();
        if (text.isEmpty()) { hints.removeAllViews(); categories(); return; }
        h.postDelayed(() -> suggestions(text), 200);
        h.postDelayed(() -> search(text), 650);
    }

    /** What people search for that starts like this, as chips under the keyboard. */
    private void suggestions(String text) {
        Bg.run(() -> Ytm.suggestions(text), (l, e) -> {
            if (!text.equals(q.toString().trim())) return;
            hints.removeAllViews();
            if (l == null) return;
            for (String s : l.subList(0, Math.min(6, l.size()))) {
                TextView t = MusicUi.chip(this, s, false);
                t.setTextSize(14);
                t.setOnClickListener(v -> { q.setLength(0); q.append(s); field.setText(q); h.removeCallbacksAndMessages(null); search(s); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
                lp.rightMargin = dp(10);
                lp.gravity = Gravity.CENTER_VERTICAL;
                hints.addView(t, lp);
            }
        });
    }

    private void search(String text) {
        if (text.equals(shown)) return;
        final int my = ++seq;
        spinner.setVisibility(View.VISIBLE);
        Bg.run(() -> AppleWeb.searchAll(text).shelves, (l, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            shown = text;
            results.removeAllViews();
            if (l == null || l.isEmpty()) { note(e != null ? "Couldn't search: " + Bg.message(e) : "No results for “" + text + "”"); return; }
            MusicUi.addShelves(this, results, l, (m, ctx) -> MusicUi.open(this, m, ctx), null);
        });
    }

    /** Nothing typed: Browse Categories — Apple Music's own tiles, fetched fresh, so they change when Apple's do. */
    private void categories() {
        shown = "";
        final int my = ++seq;
        results.removeAllViews();
        spinner.setVisibility(View.VISIBLE);
        Bg.run(AppleWeb::categories, (l, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            results.removeAllViews();
            if (l == null || l.isEmpty()) l = AppleWeb.genres();
            MusicUi.addTiles(this, results, "Browse Categories", l, (m, ctx) -> MusicUi.open(this, m, ctx));
        });
    }

    private void note(String s) {
        TextView t = MusicUi.text(this, 18, MusicUi.TEXT2, false);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(40), dp(60), dp(40), 0);
        results.addView(t);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            // A real keyboard (or the remote's voice text) types straight in.
            int u = e.getUnicodeChar();
            if (u >= 32 && u < 0xF000 && k != KeyEvent.KEYCODE_DPAD_CENTER && k != KeyEvent.KEYCODE_ENTER && !Character.isISOControl(u)) { type(String.valueOf((char) u).toLowerCase()); return true; }
            if (k == KeyEvent.KEYCODE_DEL) { backspace(); return true; }
            if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { MusicPlayer.get(this).toggle(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                boolean up = k == KeyEvent.KEYCODE_DPAD_UP;
                if (MusicUi.moveVertical(content, getCurrentFocus(), up)) return true;
                if (up) scroll.smoothScrollTo(0, 0);
                return true;
            }
        }
        if (k == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && scroll.getScrollY() > 10) {
            scroll.smoothScrollTo(0, 0);
            firstKey.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
