package com.lttube.tv.music;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lttube.tv.Bg;
import com.lttube.tv.LtDialog;
import com.lttube.tv.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Now Playing, laid out like Apple Music on Apple TV.
 * Covers: the song's cover big in the middle, the one before and the one after beside it, title and artist under
 * each, + and ••• under the song, round buttons top right, a long thin progress bar with the time under the
 * playhead. Lyrics: the cover and song on the left, big lyrics on the right with the line being sung lighting up
 * as it goes, over a moving wash of the cover's colours.
 */
public class NowPlayingActivity extends Activity implements MusicPlayer.Listener {
    private MusicPlayer mp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private Backdrop backdrop;
    private int W, H;

    // covers
    private FrameLayout stage;
    private ImageView artPrev, artCur, artNext;
    private TextView tPrev, tPrevSub, tNext, tNextSub, tTitle, tArtist, note, glyph;
    private ImageView addBtn, moreBtn;
    private View mark;
    private LinearLayout topButtons;
    private ImageView bShuffle, bRepeat, bAuto, bLyrics, bQueue;

    // progress
    private Bar bar;
    private TextView tElapsed, tLeft;

    // lyrics
    private FrameLayout lyricsBox;
    private ImageView lyArt;
    private TextView lyTitle, lyArtist, lySource;
    private ScrollView lyScroll;
    private LinearLayout lyLines;
    private LinearLayout lyButtons, lyCorner;
    private ImageView lyAdd, lyMore, lyLyricsBtn, lyQueueBtn;
    private Lyrics lyrics;
    private String lyricsFor;
    private int lyAt = -2;
    private boolean lyricsOn;

    // queue
    private FrameLayout queuePanel;
    private LinearLayout queueList;

    private String shownId;
    private final Map<String, Lyrics> lyricsCache = new HashMap<>();
    private final Map<String, Boolean> liked = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mp = MusicPlayer.get(this);
        float d = getResources().getDisplayMetrics().density;
        W = Math.round(getResources().getDisplayMetrics().widthPixels / d);
        H = Math.round(getResources().getDisplayMetrics().heightPixels / d);
        root = new FrameLayout(this);
        setContentView(root);
        backdrop = new Backdrop(this);
        root.addView(backdrop, MusicUi.match());
        buildCovers();
        buildLyrics();
        buildProgress();
        buildQueue();
        lyricsOn = getIntent().getBooleanExtra("lyrics", false);
        refresh(true);
        setLyrics(lyricsOn);
        if (!lyricsOn) artCur.requestFocus();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    private FrameLayout.LayoutParams at(int x, int y, int w, int hh) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : dp(w), hh < 0 ? hh : dp(hh));
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        return lp;
    }

    // ================================================================== covers
    private void buildCovers() {
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, MusicUi.match());

        mark = MusicUi.wordmark(this);
        stage.addView(mark, at(Math.round(W * 0.045f), Math.round(H * 0.075f), -2, -2));

        topButtons = new LinearLayout(this);
        bShuffle = MusicUi.iconButton(this, R.drawable.ic_m_shuffle, 40);
        bRepeat = MusicUi.iconButton(this, R.drawable.ic_m_repeat, 40);
        bAuto = MusicUi.iconButton(this, R.drawable.ic_m_autoplay, 40);
        bLyrics = MusicUi.iconButton(this, R.drawable.ic_m_lyrics, 40);
        bQueue = MusicUi.iconButton(this, R.drawable.ic_m_queue, 40);
        for (ImageView v : new ImageView[]{bShuffle, bRepeat, bAuto, bLyrics, bQueue}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.leftMargin = dp(14);
            topButtons.addView(v, lp);
        }
        bShuffle.setOnClickListener(v -> { mp.setShuffle(!mp.shuffle); toast(mp.shuffle ? "Shuffle on" : "Shuffle off"); });
        bRepeat.setOnClickListener(v -> { mp.cycleRepeat(); toast(mp.repeat == 0 ? "Repeat off" : mp.repeat == 1 ? "Repeat all" : "Repeat one"); });
        bAuto.setOnClickListener(v -> { mp.setAutoplay(!mp.autoplay); toast(mp.autoplay ? "Autoplay on — similar music keeps playing" : "Autoplay off"); });
        bLyrics.setOnClickListener(v -> setLyrics(true));
        bQueue.setOnClickListener(v -> showQueue(true));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tl.setMargins(0, dp(Math.round(H * 0.07f)), dp(Math.round(W * 0.045f)), 0);
        stage.addView(topButtons, tl);

        int cur = Math.round(H * 0.47f), side = Math.round(H * 0.40f);
        int margin = Math.round(W * 0.03f);
        int sideY = Math.round(H * 0.215f), curY = sideY - (cur - side) / 2 - Math.round(H * 0.01f);
        int cx = W / 2;
        int leftX = margin, rightX = W - margin - side;
        artPrev = cover(false);
        artNext = cover(false);
        artCur = cover(true);
        stage.addView(artPrev, at(leftX, sideY, side, side));
        stage.addView(artNext, at(rightX, sideY, side, side));
        stage.addView(artCur, at(cx - cur / 2, curY, cur, cur));
        artCur.setOnClickListener(v -> { mp.toggle(); flashGlyph(); });

        glyph = MusicUi.text(this, 34, Color.WHITE, true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(MusicUi.round(0x59000000, dp(44)));
        glyph.setAlpha(0f);
        glyph.setElevation(dp(24));
        stage.addView(glyph, at(cx - 44, curY + cur / 2 - 44, 88, 88));

        tPrev = caption(17, 0xF2FFFFFF, true); tPrevSub = caption(16, 0x99FFFFFF, false);
        tNext = caption(17, 0xF2FFFFFF, true); tNextSub = caption(16, 0x99FFFFFF, false);
        stage.addView(pair(tPrev, tPrevSub), at(leftX, sideY + side + 10, side, -2));
        stage.addView(pair(tNext, tNextSub), at(rightX, sideY + side + 10, side, -2));
        tTitle = caption(18, Color.WHITE, true);
        tArtist = caption(17, 0xF2FFFFFF, true);
        LinearLayout c = pair(tTitle, tArtist);
        LinearLayout acts = new LinearLayout(this);
        acts.setGravity(Gravity.CENTER);
        acts.setPadding(0, dp(12), 0, 0);
        addBtn = MusicUi.iconButton(this, R.drawable.ic_m_add, 34);
        moreBtn = MusicUi.iconButton(this, R.drawable.ic_more, 34);
        acts.addView(addBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(dp(34), dp(34));
        ml.leftMargin = dp(18);
        acts.addView(moreBtn, ml);
        c.addView(acts, new LinearLayout.LayoutParams(-1, -2));
        addBtn.setOnClickListener(v -> addToLibrary());
        moreBtn.setOnClickListener(v -> more());
        stage.addView(c, at(cx - cur / 2 - 10, curY + cur + 12, cur + 20, -2));

        note = caption(15, 0xB3FFFFFF, false);
        stage.addView(note, at(0, curY - 26, W, -2));
    }

    private ImageView cover(boolean main) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setElevation(dp(main ? 22 : 10));
        if (main) {
            v.setFocusable(true);
            v.setOnFocusChangeListener((x, has) -> x.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).translationZ(has ? dp(10) : 0).setDuration(180).start());
        }
        return v;
    }

    private TextView caption(float sp, int color, boolean bold) {
        TextView t = MusicUi.line(this, sp, color, bold);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout pair(TextView a, TextView b) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(a, new LinearLayout.LayoutParams(-1, -2));
        b.setPadding(0, dp(3), 0, 0);
        l.addView(b, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }

    // ================================================================== lyrics
    private void buildLyrics() {
        lyricsBox = new FrameLayout(this);
        lyricsBox.setVisibility(View.GONE);
        lyricsBox.setClipChildren(false);
        root.addView(lyricsBox, MusicUi.match());
        int art = Math.round(H * 0.46f);
        int ax = Math.round(W * 0.13f), ay = Math.round(H * 0.20f);
        lyArt = new ImageView(this);
        lyArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        lyArt.setElevation(dp(18));
        lyricsBox.addView(lyArt, at(ax, ay, art, art));
        lyTitle = MusicUi.line(this, 19, Color.WHITE, true);
        lyArtist = MusicUi.line(this, 17, 0xB3FFFFFF, true);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.addView(lyTitle);
        lyArtist.setPadding(0, dp(3), 0, 0);
        tt.addView(lyArtist);
        lyricsBox.addView(tt, at(ax, ay + art + 12, art + 60, -2));

        lyScroll = new ScrollView(this);
        lyScroll.setVerticalScrollBarEnabled(false);
        lyScroll.setVerticalFadingEdgeEnabled(true);
        lyScroll.setFadingEdgeLength(dp(60));
        lyScroll.setFocusable(false);
        lyLines = new LinearLayout(this);
        lyLines.setOrientation(LinearLayout.VERTICAL);
        lyLines.setPadding(0, dp(Math.round(H * 0.16f)), dp(10), dp(Math.round(H * 0.5f)));
        lyScroll.addView(lyLines);
        int lx = Math.round(W * 0.47f);
        lyricsBox.addView(lyScroll, at(lx, Math.round(H * 0.06f), W - lx - Math.round(W * 0.05f), Math.round(H * 0.62f)));
        lySource = MusicUi.text(this, 12, 0x80FFFFFF, false);
        lyricsBox.addView(lySource, at(lx, Math.round(H * 0.70f), -2, -2));

        // Like the sing-along screen: + and ••• just above the bar on the right, lyrics and queue below it.
        lyButtons = new LinearLayout(this);
        lyAdd = MusicUi.iconButton(this, R.drawable.ic_m_add, 38);
        lyMore = MusicUi.iconButton(this, R.drawable.ic_more, 38);
        lyButtons.addView(lyAdd, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(dp(38), dp(38));
        l2.leftMargin = dp(16);
        lyButtons.addView(lyMore, l2);
        lyAdd.setOnClickListener(v -> addToLibrary());
        lyMore.setOnClickListener(v -> more());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        bl.setMargins(0, 0, dp(Math.round(W * 0.045f)), dp(Math.round(H * 0.215f)));
        lyricsBox.addView(lyButtons, bl);
        lyCorner = new LinearLayout(this);
        lyLyricsBtn = MusicUi.iconButton(this, R.drawable.ic_m_lyrics, 34);
        lyQueueBtn = MusicUi.iconButton(this, R.drawable.ic_m_queue, 34);
        lyCorner.addView(lyLyricsBtn, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout.LayoutParams l3 = new LinearLayout.LayoutParams(dp(34), dp(34));
        l3.leftMargin = dp(14);
        lyCorner.addView(lyQueueBtn, l3);
        lyLyricsBtn.setOnClickListener(v -> setLyrics(false));
        lyQueueBtn.setOnClickListener(v -> showQueue(true));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        cl.setMargins(0, 0, dp(Math.round(W * 0.045f)), dp(Math.round(H * 0.045f)));
        lyricsBox.addView(lyCorner, cl);
    }

    // ================================================================== progress
    private void buildProgress() {
        bar = new Bar(this);
        bar.setFocusable(true);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, dp(24), Gravity.BOTTOM);
        bl.setMargins(dp(Math.round(W * 0.045f)), 0, dp(Math.round(W * 0.045f)), dp(Math.round(H * 0.165f)));
        root.addView(bar, bl);
        tElapsed = MusicUi.text(this, 15, 0xF2FFFFFF, true);
        tLeft = MusicUi.text(this, 15, 0xF2FFFFFF, true);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        el.bottomMargin = dp(Math.round(H * 0.165f) - 22);
        root.addView(tElapsed, el);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        rl.setMargins(0, 0, dp(Math.round(W * 0.045f)), dp(Math.round(H * 0.165f) - 22));
        root.addView(tLeft, rl);
    }

    // ================================================================== queue
    private void buildQueue() {
        queuePanel = new FrameLayout(this);
        queuePanel.setVisibility(View.GONE);
        queuePanel.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00000000, 0xCC0E0F12, 0xF20E0F12}));
        ScrollView qs = new ScrollView(this);
        qs.setVerticalScrollBarEnabled(false);
        queueList = new LinearLayout(this);
        queueList.setOrientation(LinearLayout.VERTICAL);
        queueList.setPadding(dp(60), dp(34), dp(34), dp(34));
        qs.addView(queueList);
        queuePanel.addView(qs, MusicUi.match());
        root.addView(queuePanel, new FrameLayout.LayoutParams(dp(480), -1, Gravity.END));
    }

    // ================================================================== state
    private void refresh(boolean force) {
        MItem cur = mp.current(), prev = mp.peekPrev(), next = mp.peekNext();
        String id = cur == null ? "" : cur.videoId;
        if (force || !id.equals(shownId)) {
            shownId = id;
            if (cur != null) {
                MusicUi.art(artCur, cur.thumb, dp(12));
                MusicUi.art(lyArt, cur.thumb, dp(12));
                tTitle.setText(cur.title);
                tArtist.setText(cur.artist.isEmpty() ? cur.subtitle : cur.artist);
                lyTitle.setText(cur.title);
                lyArtist.setText(cur.artist.isEmpty() ? cur.subtitle : cur.artist);
                backdrop.setArt(cur.thumb);
                Boolean l = liked.get(cur.videoId);
                paintAdd(l != null && l);
                // New song: its lyrics load once its real length is known (see tick).
                lyricsFor = null;
                lyrics = null;
                lyLines.removeAllViews();
                lySource.setText("");
            } else {
                artCur.setImageDrawable(MusicUi.round(0xFF2A2D35, dp(12)));
                tTitle.setText("Not Playing");
                tArtist.setText("");
            }
            if (queuePanel.getVisibility() == View.VISIBLE) fillQueue();
        }
        setSide(artPrev, tPrev, tPrevSub, prev);
        setSide(artNext, tNext, tNextSub, next);
        MusicUi.setOn(bShuffle, mp.shuffle);
        bRepeat.setImageResource(mp.repeat == MusicPlayer.REPEAT_ONE ? R.drawable.ic_m_repeat_one : R.drawable.ic_m_repeat);
        MusicUi.setOn(bRepeat, mp.repeat != MusicPlayer.REPEAT_OFF);
        MusicUi.setOn(bAuto, mp.autoplay);
        MusicUi.setOn(bLyrics, false);
        MusicUi.setOn(lyLyricsBtn, true);
        note.setText(mp.error != null ? mp.error : mp.resolving ? "Loading…" : "");
    }

    private void setSide(ImageView iv, TextView t, TextView s, MItem m) {
        if (m == null) { iv.setImageDrawable(null); iv.setTag(null); t.setText(""); s.setText(""); return; }
        if (!m.videoId.equals(iv.getTag())) { MusicUi.art(iv, m.thumb, dp(10)); iv.setTag(m.videoId); }
        t.setText(m.title);
        s.setText(m.artist);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long pos = Math.max(0, mp.player.getCurrentPosition());
            long dur = mp.player.getDuration();
            MItem cur = mp.current();
            if (dur <= 0 && cur != null) dur = cur.seconds * 1000L;
            bar.set(pos, dur, mp.player.getBufferedPosition());
            tElapsed.setText(MItem.time(pos / 1000));
            tLeft.setText(dur > 0 ? "-" + MItem.time(Math.max(0, dur - pos) / 1000) : "");
            // The elapsed time rides under the playhead.
            if (bar.getWidth() > 0) {
                float x = bar.getLeft() + bar.getWidth() * (dur > 0 ? Math.min(1f, pos / (float) dur) : 0f) - tElapsed.getWidth() / 2f;
                float maxX = tLeft.getLeft() - tElapsed.getWidth() - dp(12);
                tElapsed.setTranslationX(Math.max(bar.getLeft(), Math.min(x, maxX)));
            }
            if (lyricsOn) {
                if (lyricsFor == null && cur != null && !mp.resolving && mp.player.getDuration() > 0) loadLyrics(cur);
                syncLyrics(pos);
            }
            h.postDelayed(this, lyricsOn ? 50 : 250);
        }
    };

    @Override public void changed() { refresh(false); }
    @Override protected void onResume() { super.onResume(); mp.addListener(this); refresh(true); h.post(tick); }
    @Override protected void onPause() { mp.removeListener(this); h.removeCallbacks(tick); super.onPause(); }

    private void flashGlyph() {
        glyph.setText(mp.player.getPlayWhenReady() ? "▶" : "❚❚");
        glyph.animate().cancel();
        glyph.setAlpha(1f);
        glyph.animate().alpha(0f).setStartDelay(650).setDuration(350).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ================================================================== + and •••
    private void paintAdd(boolean on) {
        addBtn.setImageResource(on ? R.drawable.ic_check : R.drawable.ic_m_add);
        lyAdd.setImageResource(on ? R.drawable.ic_check : R.drawable.ic_m_add);
    }

    /** +: adds the song to your library (your Liked songs on YouTube Music). */
    private void addToLibrary() {
        MItem cur = mp.current();
        if (cur == null) return;
        if (!Ytm.signedIn()) { toast("Sign in (Library tab) to add songs to your library"); return; }
        Boolean was = liked.get(cur.videoId);
        boolean on = was == null || !was;
        liked.put(cur.videoId, on);
        paintAdd(on);
        Bg.run(() -> { Ytm.like(cur.videoId, on); return true; }, (ok, e) -> {
            if (e != null) { liked.put(cur.videoId, !on); paintAdd(!on); toast("Couldn't update your library"); }
            else toast(on ? "Added to Library" : "Removed from Library");
        });
    }

    private void more() {
        MItem cur = mp.current();
        if (cur == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final LtDialog[] d = new LtDialog[1];
        String[] labels = {"Create Station", "Playing Next", lyricsOn ? "Hide Lyrics" : "Show Lyrics"};
        Runnable[] acts = {() -> mp.playRadio(cur), () -> showQueue(true), () -> setLyrics(!lyricsOn)};
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView t = MusicUi.text(this, 18, Color.WHITE, false);
            t.setText(labels[i]);
            t.setFocusable(true);
            t.setPadding(dp(18), dp(14), dp(18), dp(14));
            final GradientDrawable bg = MusicUi.round(0, dp(10));
            t.setBackground(bg);
            t.setOnFocusChangeListener((v, has) -> { bg.setColor(has ? Color.WHITE : 0); ((TextView) v).setTextColor(has ? 0xFF111111 : Color.WHITE); });
            t.setOnClickListener(v -> { if (d[0] != null) d[0].dismiss(); acts[k].run(); });
            box.addView(t);
        }
        d[0] = new LtDialog.Builder(this).setTitle(cur.title).setView(box).show();
    }

    // ================================================================== queue
    private void showQueue(boolean on) {
        queuePanel.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) { fillQueue(); queueList.post(() -> { if (queueList.getChildCount() > 1) queueList.getChildAt(1).requestFocus(); }); }
        else if (lyricsOn) lyQueueBtn.requestFocus();
        else artCur.requestFocus();
    }

    private void fillQueue() {
        queueList.removeAllViews();
        TextView t = MusicUi.text(this, 24, Color.WHITE, true);
        t.setText("Playing Next");
        t.setPadding(dp(8), 0, 0, dp(4));
        queueList.addView(t);
        TextView s = MusicUi.text(this, 15, 0x99FFFFFF, false);
        s.setText(mp.autoplay ? "Autoplay is on — similar music plays after these" : "");
        s.setPadding(dp(8), 0, 0, dp(14));
        queueList.addView(s);
        for (int i = mp.index + 1; i < Math.min(mp.queue.size(), mp.index + 60); i++) {
            final int k = i;
            View r = MusicUi.songRow(this, mp.queue.get(i), -1, true);
            r.setOnClickListener(v -> { mp.jump(k); showQueue(false); });
            queueList.addView(r, new LinearLayout.LayoutParams(-1, dp(68)));
        }
    }

    // ================================================================== lyrics
    private void setLyrics(boolean on) {
        lyricsOn = on;
        stage.setVisibility(on ? View.GONE : View.VISIBLE);
        lyricsBox.setVisibility(on ? View.VISIBLE : View.GONE);
        backdrop.setMood(on);
        // The bar sits a little lower on the sing-along screen.
        FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) bar.getLayoutParams();
        int left = on ? Math.round(W * 0.13f) : Math.round(W * 0.045f);
        bl.setMargins(dp(left), 0, dp(Math.round(W * 0.045f)), dp(Math.round(H * (on ? 0.14f : 0.165f))));
        bar.setLayoutParams(bl);
        for (TextView t : new TextView[]{tElapsed, tLeft}) {
            FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) t.getLayoutParams();
            l.bottomMargin = dp(Math.round(H * (on ? 0.14f : 0.165f)) - 22);
            t.setLayoutParams(l);
        }
        if (on) lyLyricsBtn.requestFocus(); else artCur.requestFocus();
        h.removeCallbacks(tick);
        h.post(tick);
    }

    private void loadLyrics(MItem cur) {
        if (cur.videoId.equals(lyricsFor)) return;
        lyricsFor = cur.videoId;
        lyrics = null;
        lyAt = -2;
        lyLines.removeAllViews();
        lySource.setText("");
        if (lyricsCache.containsKey(cur.videoId)) { showLyrics(lyricsCache.get(cur.videoId)); return; }
        addLine("…", false);
        final String id = cur.videoId;
        // Timed to the recording that's actually playing: its real length picks the right lyrics.
        final int secs = (int) Math.round(mp.player.getDuration() / 1000.0);
        Bg.run(() -> Lyrics.find(cur, null, secs), (l, e) -> {
            if (!id.equals(lyricsFor)) return;
            lyricsCache.put(id, l);
            showLyrics(l);
        });
    }

    private void showLyrics(Lyrics l) {
        lyrics = l;
        lyLines.removeAllViews();
        lyAt = -2;
        if (l == null || l.lines.isEmpty()) {
            addLine("Lyrics aren't available for this song.", false);
            return;
        }
        for (Lyrics.Line ln : l.lines) addLine(ln.text.isEmpty() ? "♪" : ln.text, l.timed);
        lySource.setText(l.source);
        if (!l.timed) { lyScroll.setFocusable(true); lyScroll.scrollTo(0, 0); }
    }

    private void addLine(String s, boolean timed) {
        LyricLine t = new LyricLine(this);
        t.setTextSize(34);
        t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        t.setIncludeFontPadding(false);
        t.setTextColor(timed ? 0x59FFFFFF : 0xE6FFFFFF);
        t.setText(s);
        t.setLineSpacing(0, 1.08f);
        t.setPadding(0, dp(9), 0, dp(9));
        t.setPivotX(0);
        lyLines.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void syncLyrics(long pos) {
        if (lyrics == null || !lyrics.timed) return;
        int at = lyrics.at(pos);
        if (at >= 0 && at < lyLines.getChildCount()) ((LyricLine) lyLines.getChildAt(at)).setProgress(lyrics.progress(at, pos), 0x73FFFFFF);
        if (at == lyAt) return;
        lyAt = at;
        for (int i = 0; i < lyLines.getChildCount(); i++) {
            LyricLine t = (LyricLine) lyLines.getChildAt(i);
            boolean now = i == at;
            // The line being sung lights up as it goes; the next ones wait softly; sung ones fade back.
            t.setProgress(now ? lyrics.progress(i, pos) : -1f, 0x73FFFFFF);
            if (!now) t.setTextColor(i > at ? (i <= at + 2 ? 0x66FFFFFF : 0x40FFFFFF) : 0x33FFFFFF);
            t.animate().scaleX(now ? 1.03f : 1f).scaleY(now ? 1.03f : 1f).setDuration(260).start();
        }
        if (at >= 0 && at < lyLines.getChildCount()) {
            View v = lyLines.getChildAt(at);
            lyScroll.smoothScrollTo(0, Math.max(0, v.getTop() - Math.round(lyScroll.getHeight() * 0.22f)));
        } else lyScroll.smoothScrollTo(0, 0);
    }

    // ================================================================== remote
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        View f = getCurrentFocus();
        switch (k) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && e.getRepeatCount() == 0) { mp.toggle(); flashGlyph(); }
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: if (down) mp.next(); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: if (down) mp.prev(); return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) mp.seek(mp.player.getCurrentPosition() + (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? 10_000 : -10_000));
                return true;
            case KeyEvent.KEYCODE_MENU: if (!down) more(); return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) return true;
                if (queuePanel.getVisibility() == View.VISIBLE) showQueue(false);
                else if (lyricsOn) setLyrics(false);
                else finish();
                return true;
            default: break;
        }
        if (queuePanel.getVisibility() == View.VISIBLE) return super.dispatchKeyEvent(e);
        // On the cover: left / right go to the song before / after, the covers sliding over.
        if (f == artCur && down && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (e.getRepeatCount() == 0) {
                boolean fwd = k == KeyEvent.KEYCODE_DPAD_RIGHT;
                if (!fwd && mp.peekPrev() == null && mp.player.getCurrentPosition() < 4000) return true;
                float dx = dp(fwd ? -80 : 80);
                stage.animate().translationX(dx).alpha(0.3f).setDuration(130).withEndAction(() -> {
                    if (fwd) mp.next(); else mp.prev();
                    stage.setTranslationX(-dx);
                    stage.animate().translationX(0).alpha(1f).setDuration(200).start();
                }).start();
            }
            return true;
        }
        if (f == artCur && down && k == KeyEvent.KEYCODE_DPAD_UP) { bLyrics.requestFocus(); return true; }
        if (f == bar && down && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int step = e.getRepeatCount() > 12 ? 30_000 : e.getRepeatCount() > 4 ? 15_000 : 5_000;
            mp.seek(mp.player.getCurrentPosition() + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? step : -step));
            return true;
        }
        if (f == bar && down && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) { mp.toggle(); flashGlyph(); return true; }
        if (f == bar && down && k == KeyEvent.KEYCODE_DPAD_DOWN && lyricsOn) { lyLyricsBtn.requestFocus(); return true; }
        if (f == bar && down && k == KeyEvent.KEYCODE_DPAD_UP) { (lyricsOn ? lyAdd : addBtn).requestFocus(); return true; }
        if (down && k == KeyEvent.KEYCODE_DPAD_DOWN && (f == addBtn || f == moreBtn)) { bar.requestFocus(); return true; }
        if (down && k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f.getParent() == topButtons) { artCur.requestFocus(); return true; }
        if (down && k == KeyEvent.KEYCODE_DPAD_UP && (f == addBtn || f == moreBtn)) { artCur.requestFocus(); return true; }
        if (down && k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f.getParent() == lyButtons) { bar.requestFocus(); return true; }
        if (down && k == KeyEvent.KEYCODE_DPAD_UP && f != null && f.getParent() == lyCorner) { bar.requestFocus(); return true; }
        // On the sing-along screen, OK anywhere outside the buttons plays / pauses.
        return super.dispatchKeyEvent(e);
    }

    /** A long thin progress line, the played part bright; it thickens when you're on it. */
    static class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long pos, dur, buf;
        private final RectF r = new RectF();

        Bar(Context c) { super(c); }

        void set(long pos, long dur, long buf) { this.pos = pos; this.dur = dur; this.buf = buf; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float hh = (hasFocus() ? 8 : 5) * d;
            float y = getHeight() / 2f;
            r.set(0, y - hh / 2, getWidth(), y + hh / 2);
            p.setColor(0x4DFFFFFF);
            c.drawRoundRect(r, hh, hh, p);
            if (dur > 0) {
                float w = getWidth() * Math.min(1f, pos / (float) dur);
                r.set(0, y - hh / 2, w, y + hh / 2);
                p.setColor(0xF2FFFFFF);
                c.drawRoundRect(r, hh, hh, p);
                p.setColor(Color.WHITE);
                c.drawRect(w - 1.5f * d, y - hh * 1.3f, w + 1.5f * d, y + hh * 1.3f, p);
            }
        }

        @Override protected void onFocusChanged(boolean f, int dir, android.graphics.Rect prev) { super.onFocusChanged(f, dir, prev); invalidate(); }
    }
}
