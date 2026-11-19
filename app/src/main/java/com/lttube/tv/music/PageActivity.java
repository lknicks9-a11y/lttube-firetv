package com.lttube.tv.music;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.lttube.tv.Bg;
import com.lttube.tv.R;

import org.json.JSONObject;

import java.util.List;

/** An album or playlist (art on the left, songs on the right), or an artist / mood page (rows). */
public class PageActivity extends Activity {
    public static final String ITEM = "item";

    private MItem item;
    private FrameLayout root;
    private Backdrop backdrop;
    private ProgressBar spinner;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { item = MItem.fromJson(new JSONObject(getIntent().getStringExtra(ITEM))); } catch (Exception e) { finish(); return; }
        root = new FrameLayout(this);
        setContentView(root);
        backdrop = new Backdrop(this);
        root.addView(backdrop, MusicUi.match());
        backdrop.setArt(item.thumb);
        spinner = new ProgressBar(this);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        Bg.run(() -> Ytm.browse(item.browseId, item.params), (pg, e) -> {
            spinner.setVisibility(View.GONE);
            if (pg == null) {
                TextView t = MusicUi.text(this, 18, MusicUi.TEXT2, false);
                t.setText("Couldn't open this: " + Bg.message(e));
                root.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
                return;
            }
            if (pg.thumb.isEmpty()) pg.thumb = item.thumb;
            if (pg.title.isEmpty()) pg.title = item.title;
            backdrop.setArt(pg.thumb);
            boolean list = !MItem.ARTIST.equals(item.type) && !MItem.CATEGORY.equals(item.type) && !pg.songs().isEmpty();
            if (list) album(pg); else rows(pg);
        });
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    /** Album / playlist: the cover, Play and Shuffle on the left; the songs on the right. */
    private void album(Ytm.Page pg) {
        final List<MItem> songs = pg.songs();
        String pl = !pg.playlistId.isEmpty() ? pg.playlistId : item.playlistId;
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(70), dp(56), dp(56), 0);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        ImageView art = new ImageView(this);
        MusicUi.art(art, pg.thumb, dp(16));
        art.setElevation(dp(16));
        left.addView(art, new LinearLayout.LayoutParams(dp(290), dp(290)));
        TextView title = MusicUi.text(this, 28, Color.WHITE, true);
        title.setText(pg.title);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, dp(22), 0, 0);
        left.addView(title, new LinearLayout.LayoutParams(dp(290), -2));
        // Like Apple's album pages: the artist in the accent colour, then the kind and year in grey.
        TextView sub = MusicUi.text(this, 17, MusicUi.TEXT2, false);
        String[] parts = pg.subtitle.split("\\s+•\\s+", 2);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(pg.subtitle);
        if (parts.length > 0 && !parts[0].isEmpty()) {
            sb.setSpan(new android.text.style.ForegroundColorSpan(MusicUi.PINK), 0, parts[0].length(), 0);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, parts[0].length(), 0);
        }
        sub.setText(sb);
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        sub.setPadding(0, dp(8), 0, 0);
        left.addView(sub, new LinearLayout.LayoutParams(dp(290), -2));
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(22), 0, 0);
        TextView play = pill("▶  Play", true);
        play.setOnClickListener(v -> { MusicPlayer.get(this).setShuffle(false); MusicPlayer.get(this).playList(songs, 0, pl); startActivity(MusicPlayer.nowPlaying(this)); });
        btns.addView(play);
        TextView shuf = pill("Shuffle", false);
        shuf.setOnClickListener(v -> { MusicPlayer.get(this).shuffleList(songs, pl); startActivity(MusicPlayer.nowPlaying(this)); });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = dp(14);
        btns.addView(shuf, sl);
        left.addView(btns);
        row.addView(left, new LinearLayout.LayoutParams(dp(290), -1));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, dp(60));
        boolean numbered = MItem.ALBUM.equals(item.type) || pg.subtitle.toLowerCase(java.util.Locale.US).contains("album") || pg.subtitle.toLowerCase(java.util.Locale.US).contains("single") || pg.subtitle.toLowerCase(java.util.Locale.US).contains("ep");
        for (int i = 0; i < songs.size(); i++) {
            final int at = i;
            final MItem m = songs.get(i);
            View r = MusicUi.songRow(this, m, numbered ? i + 1 : -1, true);
            r.setOnClickListener(v -> { MusicPlayer.get(this).playList(songs, at, pl); startActivity(MusicPlayer.nowPlaying(this)); });
            r.setOnKeyListener((v, k, e) -> {
                if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { MusicUi.options(this, m); return true; }
                return false;
            });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(70)));
        }
        // Anything else on the page (more by the artist, other versions) underneath.
        for (Ytm.Shelf s : pg.shelves) if (!s.list && !s.items.isEmpty()) {
            LinearLayout extra = new LinearLayout(this);
            extra.setOrientation(LinearLayout.VERTICAL);
            java.util.List<Ytm.Shelf> one = new java.util.ArrayList<>();
            one.add(s);
            MusicUi.addShelves(this, extra, one, false, (m, ctx) -> MusicUi.open(this, m, ctx));
            list.addView(extra);
        }
        sv.addView(list);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.leftMargin = dp(48);
        row.addView(sv, lp);
        root.addView(row, MusicUi.match());
        play.requestFocus();
    }

    /** Artist / mood: a header, then rows. */
    private void rows(Ytm.Page pg) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(0, dp(40), 0, dp(60));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(58), 0, dp(58), dp(10));
        boolean artist = MItem.ARTIST.equals(item.type);
        if (artist && !pg.thumb.isEmpty()) {
            ImageView iv = new ImageView(this);
            MusicUi.circle(iv, pg.thumb);
            head.addView(iv, new LinearLayout.LayoutParams(dp(150), dp(150)));
        }
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(artist ? dp(30) : 0, 0, 0, 0);
        TextView t = MusicUi.text(this, 40, Color.WHITE, true);
        t.setText(pg.title.isEmpty() ? item.title : pg.title);
        txt.addView(t);
        if (!pg.subtitle.isEmpty()) {
            TextView s = MusicUi.text(this, 17, MusicUi.TEXT2, false);
            s.setText(pg.subtitle);
            s.setPadding(0, dp(8), 0, 0);
            txt.addView(s);
        }
        View first = null;
        if (artist) {
            LinearLayout btns = new LinearLayout(this);
            btns.setPadding(0, dp(18), 0, 0);
            final List<MItem> top = pg.songs();
            final String radio = pg.playlistId;
            TextView play = pill("▶  Play", true);
            play.setOnClickListener(v -> {
                if (!top.isEmpty()) MusicPlayer.get(this).playList(top, 0, null);
                else if (!radio.isEmpty()) MusicPlayer.get(this).playId(null, radio);
                startActivity(MusicPlayer.nowPlaying(this));
            });
            btns.addView(play);
            if (!radio.isEmpty()) {
                TextView r = pill("Radio", false);
                r.setOnClickListener(v -> { MusicPlayer.get(this).playId(null, radio); startActivity(MusicPlayer.nowPlaying(this)); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = dp(14);
                btns.addView(r, lp);
            }
            txt.addView(btns);
            first = play;
        }
        head.addView(txt);
        col.addView(head);
        MusicUi.addShelves(this, col, pg.shelves, false, (m, ctx) -> MusicUi.open(this, m, ctx));
        sv.addView(col);
        root.addView(sv, MusicUi.match());
        if (first != null) first.requestFocus();
        else col.post(() -> { for (int i = 1; i < col.getChildCount(); i++) if (col.getChildAt(i).requestFocus()) return; });
    }

    private TextView pill(String label, boolean primary) {
        TextView b = MusicUi.text(this, 17, primary ? 0xFF111111 : Color.WHITE, true);
        b.setText(label);
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(28), dp(12), dp(28), dp(12));
        final int base = primary ? 0xEEFFFFFF : 0x33FFFFFF;
        b.setBackground(MusicUi.round(base, dp(24)));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(MusicUi.round(has ? Color.WHITE : base, dp(24)));
            ((TextView) v).setTextColor(has || primary ? 0xFF111111 : Color.WHITE);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(140).start();
        });
        return b;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (e.getAction() == KeyEvent.ACTION_DOWN) MusicPlayer.get(this).toggle(); return true; }
        return super.dispatchKeyEvent(e);
    }
}
