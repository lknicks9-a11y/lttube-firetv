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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lttube.tv.Bg;

import java.util.ArrayList;
import java.util.List;

/** Search with a keyboard on screen (left) and results that update as you type (right). */
public class MusicSearchActivity extends Activity {
    private final StringBuilder q = new StringBuilder();
    private TextView field;
    private LinearLayout suggest, results;
    private ScrollView resultsScroll;
    private ProgressBar spinner;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int seq;
    private LinearLayout keys;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(MusicUi.background());
        setContentView(root);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(MusicUi.PAD), dp(MusicUi.TOP + 6), 0, 0);

        // ---- left: what you typed, the keyboard, suggestions
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        field = MusicUi.line(this, 22, Color.WHITE, true);
        field.setHint("What do you want to play?");
        field.setHintTextColor(MusicUi.TEXT2);
        field.setTextSize(18);
        field.setPadding(dp(18), dp(13), dp(18), dp(13));
        android.graphics.drawable.Drawable si = getDrawable(com.lttube.tv.R.drawable.ic_search).mutate();
        si.setBounds(0, 0, dp(18), dp(18));
        si.setTint(Color.WHITE);
        field.setCompoundDrawables(si, null, null, null);
        field.setCompoundDrawablePadding(dp(10));
        GradientDrawable fb = MusicUi.round(0x26FFFFFF, dp(24));
        fb.setStroke(dp(2), Color.WHITE);
        field.setBackground(fb);
        left.addView(field, new LinearLayout.LayoutParams(-1, -2));
        keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        keys.setPadding(0, dp(14), 0, 0);
        String[] rows = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890"};
        for (String r : rows) {
            LinearLayout kr = new LinearLayout(this);
            for (char c : r.toCharArray()) kr.addView(key(String.valueOf(c), () -> type(String.valueOf(c))), keyLp(1));
            keys.addView(kr);
        }
        LinearLayout last = new LinearLayout(this);
        last.addView(key("space", () -> type(" ")), keyLp(2));
        last.addView(key("⌫", this::backspace), keyLp(2));
        last.addView(key("clear", () -> { q.setLength(0); changed(); }), keyLp(2));
        keys.addView(last);
        left.addView(keys);
        suggest = new LinearLayout(this);
        suggest.setOrientation(LinearLayout.VERTICAL);
        suggest.setPadding(0, dp(14), 0, 0);
        left.addView(suggest);
        row.addView(left, new LinearLayout.LayoutParams(dp(330), -1));

        // ---- right: results
        resultsScroll = new ScrollView(this);
        resultsScroll.setVerticalScrollBarEnabled(false);
        resultsScroll.setClipChildren(false);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        results.setPadding(0, 0, 0, dp(60));
        resultsScroll.addView(results);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, -1, 1);
        rl.leftMargin = dp(4);
        row.addView(resultsScroll, rl);
        root.addView(row, MusicUi.match());
        spinner = new ProgressBar(this);
        spinner.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER);
        sp.leftMargin = dp(180);
        root.addView(spinner, sp);
        hint("Search YouTube Music");
        ((LinearLayout) keys.getChildAt(0)).getChildAt(0).requestFocus();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    private LinearLayout.LayoutParams keyLp(int span) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), span);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private TextView key(String label, Runnable r) {
        TextView k = MusicUi.text(this, label.length() > 1 ? 15 : 19, Color.WHITE, true);
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setFocusable(true);
        final GradientDrawable bg = MusicUi.round(0x1FFFFFFF, dp(10));
        k.setBackground(bg);
        k.setOnFocusChangeListener((v, has) -> { bg.setColor(has ? Color.WHITE : 0x1FFFFFFF); k.setTextColor(has ? 0xFF111111 : Color.WHITE); });
        k.setOnClickListener(v -> r.run());
        return k;
    }

    private void type(String s) { if (q.length() < 80) { q.append(s); changed(); } }

    private void backspace() { if (q.length() > 0) { q.setLength(q.length() - 1); changed(); } }

    private void changed() {
        field.setText(q);
        h.removeCallbacksAndMessages(null);
        final String text = q.toString().trim();
        if (text.isEmpty()) { suggest.removeAllViews(); results.removeAllViews(); hint("Search YouTube Music"); return; }
        h.postDelayed(() -> suggestions(text), 250);
        h.postDelayed(() -> search(text), 800);
    }

    private void suggestions(String text) {
        Bg.run(() -> Ytm.suggestions(text), (l, e) -> {
            if (!text.equals(q.toString().trim())) return;
            suggest.removeAllViews();
            if (l == null) return;
            for (String s : l.subList(0, Math.min(5, l.size()))) {
                TextView t = MusicUi.line(this, 17, 0xDDFFFFFF, false);
                t.setText("🔍  " + s);
                t.setFocusable(true);
                t.setPadding(dp(14), dp(10), dp(14), dp(10));
                final GradientDrawable bg = MusicUi.round(0, dp(10));
                t.setBackground(bg);
                t.setOnFocusChangeListener((v, has) -> bg.setColor(has ? 0x33FFFFFF : 0));
                t.setOnClickListener(v -> { q.setLength(0); q.append(s); field.setText(q); h.removeCallbacksAndMessages(null); search(s); });
                suggest.addView(t);
            }
        });
    }

    private void search(String text) {
        final int my = ++seq;
        spinner.setVisibility(View.VISIBLE);
        Bg.run(() -> {
            List<Ytm.Shelf> out = new ArrayList<>();
            Ytm.Page all = null;
            try { all = Ytm.search(text, false); } catch (Exception ignored) { }
            boolean hasSongs = false;
            if (all != null) for (Ytm.Shelf s : all.shelves) { out.add(s); if (s.list && s.title.toLowerCase(java.util.Locale.US).contains("song")) hasSongs = true; }
            if (!hasSongs) {
                Ytm.Page songs = Ytm.search(text, true);
                for (Ytm.Shelf s : songs.shelves) { if (s.title.isEmpty()) s.title = "Songs"; out.add(Math.min(out.size(), 1), s); }
            }
            return out;
        }, (l, e) -> {
            if (my != seq) return;
            spinner.setVisibility(View.GONE);
            results.removeAllViews();
            resultsScroll.scrollTo(0, 0);
            if (l == null || l.isEmpty()) { hint(e != null ? "Couldn't search: " + Bg.message(e) : "No results for “" + text + "”"); return; }
            MusicUi.addShelves(this, results, l, (m, ctx) -> MusicUi.open(this, m, ctx), null);
        });
    }

    private void hint(String s) {
        results.removeAllViews();
        TextView t = MusicUi.text(this, 20, MusicUi.TEXT2, false);
        t.setText(s);
        t.setPadding(dp(40), dp(80), dp(20), 0);
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
        }
        return super.dispatchKeyEvent(e);
    }
}
