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
import com.lttube.tv.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Now Playing: the song's cover between the one before and the one after, a progress bar along the bottom, and
 * controls in the corner. Lyrics turns it into a sing-along screen with the line being sung lit up.
 */
public class NowPlayingActivity extends Activity implements MusicPlayer.Listener {
    private MusicPlayer mp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private int W, H;

    // carousel
    private FrameLayout stage;
    private ImageView artPrev, artCur, artNext, playGlyph;
    private TextView tPrev, tNext, tCurTitle, tCurArtist, tPrevSub, tNextSub, note;
    private LinearLayout curInfo;
    private ImageView likeBtn, moreBtn;

    // controls
    private ImageView bShuffle, bRepeat, bAuto, bLyrics, bQueue;
    private Bar bar;
    private TextView tElapsed, tLeft;

    // lyrics
    private FrameLayout lyricsBox;
    private ImageView lyArt;
    private TextView lyTitle, lyArtist, lySource;
    private ScrollView lyScroll;
    private LinearLayout lyLines;
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
    private int bgColor;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mp = MusicPlayer.get(this);
        float d = getResources().getDisplayMetrics().density;
        W = Math.round(getResources().getDisplayMetrics().widthPixels / d);
        H = Math.round(getResources().getDisplayMetrics().heightPixels / d);
        root = new FrameLayout(this);
        root.setBackground(MusicUi.background(0));
        setContentView(root);
        build();
        lyricsOn = getIntent().getBooleanExtra("lyrics", false);
        refresh(true);
        setLyrics(lyricsOn);
        artCur.requestFocus();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    // ================================================================== layout
    private void build() {
        View mark = MusicUi.wordmark(this);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        ml.setMargins(dp(52), dp(36), 0, 0);
        root.addView(mark, ml);

        LinearLayout top = new LinearLayout(this);
        bShuffle = MusicUi.iconButton(this, R.drawable.ic_m_shuffle, 46);
        bRepeat = MusicUi.iconButton(this, R.drawable.ic_m_repeat, 46);
        bAuto = MusicUi.iconButton(this, R.drawable.ic_m_autoplay, 46);
        bLyrics = MusicUi.iconButton(this, R.drawable.ic_m_lyrics, 46);
        bQueue = MusicUi.iconButton(this, R.drawable.ic_m_queue, 46);
        for (ImageView v : new ImageView[]{bShuffle, bRepeat, bAuto, bLyrics, bQueue}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
            lp.leftMargin = dp(16);
            top.addView(v, lp);
        }
        bShuffle.setOnClickListener(v -> { mp.setShuffle(!mp.shuffle); toast(mp.shuffle ? "Shuffle on" : "Shuffle off"); });
        bRepeat.setOnClickListener(v -> { mp.cycleRepeat(); toast(mp.repeat == 0 ? "Repeat off" : mp.repeat == 1 ? "Repeat all" : "Repeat this song"); });
        bAuto.setOnClickListener(v -> { mp.setAutoplay(!mp.autoplay); toast(mp.autoplay ? "Autoplay on: similar songs keep playing" : "Autoplay off"); });
        bLyrics.setOnClickListener(v -> setLyrics(!lyricsOn));
        bQueue.setOnClickListener(v -> showQueue(true));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        tl.setMargins(0, dp(30), dp(52), 0);
        root.addView(top, tl);

        // ---- carousel: previous · current · next
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        int cur = Math.round(H * 0.50f), side = Math.round(cur * 0.86f);
        int gap = Math.max(16, (W - 104 - cur - side * 2) / 2);
        int cx = W / 2;
        artPrev = sideArt(); artNext = sideArt();
        artCur = new ImageView(this);
        artCur.setFocusable(true);
        artCur.setElevation(dp(18));
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(14));
        ring.setColor(Color.TRANSPARENT);
        artCur.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(160).start());
        artCur.setOnClickListener(v -> { mp.toggle(); flashGlyph(); });
        int artTop = Math.round(H * 0.17f);
        place(artPrev, cx - cur / 2 - gap - side, artTop + (cur - side) / 2, side, side);
        place(artCur, cx - cur / 2, artTop, cur, cur);
        place(artNext, cx + cur / 2 + gap, artTop + (cur - side) / 2, side, side);
        playGlyph = new ImageView(this);
        playGlyph.setImageResource(R.drawable.ic_play);
        playGlyph.setColorFilter(Color.WHITE);
        playGlyph.setBackground(MusicUi.round(0x66000000, dp(40)));
        playGlyph.setPadding(dp(18), dp(18), dp(18), dp(18));
        playGlyph.setAlpha(0f);
        playGlyph.setElevation(dp(20));
        place(playGlyph, cx - 40, artTop + cur / 2 - 40, 80, 80);

        tPrev = caption(true); tPrevSub = caption(false);
        tNext = caption(true); tNextSub = caption(false);
        placeText(tPrev, tPrevSub, cx - cur / 2 - gap - side, artTop + (cur - side) / 2 + side + 12, side);
        placeText(tNext, tNextSub, cx + cur / 2 + gap, artTop + (cur - side) / 2 + side + 12, side);
        curInfo = new LinearLayout(this);
        curInfo.setOrientation(LinearLayout.VERTICAL);
        curInfo.setGravity(Gravity.CENTER_HORIZONTAL);
        tCurTitle = MusicUi.line(this, 19, Color.WHITE, true);
        tCurTitle.setGravity(Gravity.CENTER);
        tCurArtist = MusicUi.line(this, 17, 0xDDFFFFFF, false);
        tCurArtist.setGravity(Gravity.CENTER);
        tCurArtist.setPadding(0, dp(4), 0, 0);
        curInfo.addView(tCurTitle, new LinearLayout.LayoutParams(-1, -2));
        curInfo.addView(tCurArtist, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout acts = new LinearLayout(this);
        acts.setGravity(Gravity.CENTER);
        acts.setPadding(0, dp(12), 0, 0);
        likeBtn = MusicUi.iconButton(this, R.drawable.ic_m_heart_o, 38);
        moreBtn = MusicUi.iconButton(this, R.drawable.ic_more, 38);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(dp(38), dp(38));
        acts.addView(likeBtn, al);
        LinearLayout.LayoutParams al2 = new LinearLayout.LayoutParams(dp(38), dp(38));
        al2.leftMargin = dp(18);
        acts.addView(moreBtn, al2);
        curInfo.addView(acts);
        likeBtn.setOnClickListener(v -> like());
        moreBtn.setOnClickListener(v -> more());
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(dp(cur + 40), -2);
        cl.leftMargin = dp(cx - cur / 2 - 20);
        cl.topMargin = dp(artTop + cur + 14);
        stage.addView(curInfo, cl);
        note = MusicUi.text(this, 15, MusicUi.TEXT2, false);
        note.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        nl.topMargin = dp(artTop - 34);
        stage.addView(note, nl);
        root.addView(stage, MusicUi.match());

        // ---- lyrics (hidden until asked for)
        lyricsBox = new FrameLayout(this);
        lyricsBox.setVisibility(View.GONE);
        LinearLayout lyLeft = new LinearLayout(this);
        lyLeft.setOrientation(LinearLayout.VERTICAL);
        lyArt = new ImageView(this);
        int la = Math.round(H * 0.50f);
        lyLeft.addView(lyArt, new LinearLayout.LayoutParams(dp(la), dp(la)));
        lyTitle = MusicUi.line(this, 20, Color.WHITE, true);
        lyTitle.setPadding(0, dp(14), 0, 0);
        lyArtist = MusicUi.line(this, 17, MusicUi.TEXT2, false);
        lyArtist.setPadding(0, dp(4), 0, 0);
        lyLeft.addView(lyTitle, new LinearLayout.LayoutParams(dp(la), -2));
        lyLeft.addView(lyArtist, new LinearLayout.LayoutParams(dp(la), -2));
        FrameLayout.LayoutParams lll = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP);
        lll.setMargins(dp(Math.round(W * 0.09f)), dp(Math.round(H * 0.20f)), 0, 0);
        lyricsBox.addView(lyLeft, lll);
        lyScroll = new ScrollView(this) {
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent ev) { return false; }
        };
        lyScroll.setVerticalScrollBarEnabled(false);
        lyScroll.setVerticalFadingEdgeEnabled(true);
        lyScroll.setFadingEdgeLength(dp(70));
        lyLines = new LinearLayout(this);
        lyLines.setOrientation(LinearLayout.VERTICAL);
        lyLines.setPadding(0, dp(H / 3), dp(30), dp(H / 2));
        lyScroll.addView(lyLines);
        FrameLayout.LayoutParams lsl = new FrameLayout.LayoutParams(dp(Math.round(W * 0.48f)), dp(Math.round(H * 0.70f)), Gravity.END | Gravity.TOP);
        lsl.setMargins(0, dp(Math.round(H * 0.11f)), dp(Math.round(W * 0.05f)), 0);
        lyricsBox.addView(lyScroll, lsl);
        lySource = MusicUi.text(this, 13, MusicUi.TEXT3, false);
        FrameLayout.LayoutParams srl = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        srl.setMargins(0, 0, dp(52), dp(118));
        lyricsBox.addView(lySource, srl);
        root.addView(lyricsBox, MusicUi.match());

        // ---- progress
        bar = new Bar(this);
        bar.setFocusable(true);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, dp(30), Gravity.BOTTOM);
        bl.setMargins(dp(52), 0, dp(52), dp(62));
        root.addView(bar, bl);
        tElapsed = MusicUi.text(this, 16, 0xDDFFFFFF, true);
        tLeft = MusicUi.text(this, 16, 0xDDFFFFFF, true);
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        el.setMargins(dp(54), 0, 0, dp(34));
        root.addView(tElapsed, el);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        rl.setMargins(0, 0, dp(54), dp(34));
        root.addView(tLeft, rl);

        // ---- queue panel
        queuePanel = new FrameLayout(this);
        queuePanel.setVisibility(View.GONE);
        queuePanel.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xE6101114, 0xF2101114}));
        ScrollView qs = new ScrollView(this);
        qs.setVerticalScrollBarEnabled(false);
        queueList = new LinearLayout(this);
        queueList.setOrientation(LinearLayout.VERTICAL);
        queueList.setPadding(dp(26), dp(30), dp(26), dp(30));
        qs.addView(queueList);
        queuePanel.addView(qs, MusicUi.match());
        root.addView(queuePanel, new FrameLayout.LayoutParams(dp(440), -1, Gravity.END));
    }

    private ImageView sideArt() {
        ImageView v = new ImageView(this);
        v.setAlpha(0.9f);
        return v;
    }

    private TextView caption(boolean bold) {
        TextView t = MusicUi.line(this, bold ? 17 : 15, bold ? 0xDDFFFFFF : MusicUi.TEXT3, bold);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(w), dp(hgt));
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        stage.addView(v, lp);
    }

    private void placeText(TextView a, TextView b, int x, int y, int w) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(a, new LinearLayout.LayoutParams(-1, -2));
        b.setPadding(0, dp(4), 0, 0);
        l.addView(b, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(w), -2);
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        stage.addView(l, lp);
    }

    // ================================================================== state
    private void refresh(boolean force) {
        MItem cur = mp.current(), prev = mp.peekPrev(), next = mp.peekNext();
        String id = cur == null ? "" : cur.videoId;
        if (force || !id.equals(shownId)) {
            shownId = id;
            setArt(artPrev, tPrev, tPrevSub, prev);
            setArt(artNext, tNext, tNextSub, next);
            if (cur != null) {
                MusicUi.art(artCur, cur.thumb, dp(14));
                MusicUi.art(lyArt, cur.thumb, dp(16));
                tCurTitle.setText(cur.title);
                tCurArtist.setText(cur.artist.isEmpty() ? cur.subtitle : cur.artist);
                lyTitle.setText(cur.title);
                lyArtist.setText(cur.artist);
                MusicUi.colorOf(this, cur.thumb, c -> { bgColor = c; paintBg(); });
                Boolean l = liked.get(cur.videoId);
                likeBtn.setImageResource(l != null && l ? R.drawable.ic_m_heart : R.drawable.ic_m_heart_o);
                if (lyricsOn) loadLyrics(cur);
            } else {
                artCur.setImageDrawable(MusicUi.round(0xFF2A2D35, dp(14)));
                tCurTitle.setText("Nothing playing");
                tCurArtist.setText("Pick something from Home");
            }
            if (queuePanel.getVisibility() == View.VISIBLE) fillQueue();
        } else {
            // The neighbours change when the queue fills in.
            setArt(artNext, tNext, tNextSub, next);
            setArt(artPrev, tPrev, tPrevSub, prev);
        }
        MusicUi.setOn(bShuffle, mp.shuffle);
        bRepeat.setImageResource(mp.repeat == MusicPlayer.REPEAT_ONE ? R.drawable.ic_m_repeat_one : R.drawable.ic_m_repeat);
        MusicUi.setOn(bRepeat, mp.repeat != MusicPlayer.REPEAT_OFF);
        MusicUi.setOn(bAuto, mp.autoplay);
        MusicUi.setOn(bLyrics, lyricsOn);
        note.setText(mp.error != null ? mp.error : mp.resolving ? "Loading…" : "");
        playGlyph.setImageResource(mp.player.getPlayWhenReady() ? R.drawable.ic_play : R.drawable.ic_pause);
    }

    private void paintBg() {
        // Lyrics: a richer, deeper wash of the cover's colour (like the sing-along screen).
        int c = bgColor;
        if (lyricsOn && c != 0) {
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            hsv[1] = Math.min(1f, hsv[1] + 0.25f);
            hsv[2] = 0.55f;
            int a = Color.HSVToColor(hsv);
            hsv[0] = (hsv[0] + 40) % 360;
            hsv[2] = 0.32f;
            int b = Color.HSVToColor(hsv);
            root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{b, a, MusicUi.blend(b, 0xFF000000, 0.4f)}));
        } else root.setBackground(MusicUi.background(c));
    }

    private void setArt(ImageView iv, TextView t, TextView s, MItem m) {
        if (m == null) { iv.setImageDrawable(null); t.setText(""); s.setText(""); return; }
        if (!m.videoId.equals(iv.getTag())) { MusicUi.art(iv, m.thumb, dp(12)); iv.setTag(m.videoId); }
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
            tLeft.setText(dur > 0 ? "-" + MItem.time((dur - pos) / 1000) : "");
            if (lyricsOn) syncLyrics(pos);
            h.postDelayed(this, lyricsOn ? 120 : 400);
        }
    };

    @Override public void changed() { refresh(false); }

    @Override protected void onResume() { super.onResume(); mp.addListener(this); refresh(true); h.post(tick); }
    @Override protected void onPause() { mp.removeListener(this); h.removeCallbacks(tick); super.onPause(); }

    private void flashGlyph() {
        playGlyph.setImageResource(mp.player.getPlayWhenReady() ? R.drawable.ic_play : R.drawable.ic_pause);
        playGlyph.animate().cancel();
        playGlyph.setAlpha(1f);
        playGlyph.animate().alpha(0f).setStartDelay(700).setDuration(400).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ================================================================== like / more
    private void like() {
        MItem cur = mp.current();
        if (cur == null) return;
        if (!Ytm.signedIn()) { toast("Sign in to like songs"); return; }
        Boolean was = liked.get(cur.videoId);
        boolean on = was == null || !was;
        liked.put(cur.videoId, on);
        likeBtn.setImageResource(on ? R.drawable.ic_m_heart : R.drawable.ic_m_heart_o);
        Bg.run(() -> { Ytm.like(cur.videoId, on); return true; }, (ok, e) -> {
            if (e != null) { liked.put(cur.videoId, !on); likeBtn.setImageResource(!on ? R.drawable.ic_m_heart : R.drawable.ic_m_heart_o); toast("Couldn't update your likes"); }
            else toast(on ? "Added to Liked songs" : "Removed from Liked songs");
        });
    }

    private void more() {
        MItem cur = mp.current();
        if (cur == null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final com.lttube.tv.LtDialog[] d = new com.lttube.tv.LtDialog[1];
        String[] labels = {"Start radio from this song", "Show the queue", lyricsOn ? "Hide lyrics" : "Show lyrics"};
        Runnable[] acts = {() -> mp.playRadio(cur), () -> showQueue(true), () -> setLyrics(!lyricsOn)};
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView t = MusicUi.text(this, 18, Color.WHITE, false);
            t.setText(labels[i]);
            t.setFocusable(true);
            t.setPadding(dp(18), dp(14), dp(18), dp(14));
            final GradientDrawable bg = MusicUi.round(0, dp(10));
            t.setBackground(bg);
            t.setOnFocusChangeListener((v, has) -> bg.setColor(has ? 0x33FFFFFF : 0));
            t.setOnClickListener(v -> { if (d[0] != null) d[0].dismiss(); acts[k].run(); });
            box.addView(t);
        }
        d[0] = new com.lttube.tv.LtDialog.Builder(this).setTitle(cur.title).setView(box).show();
    }

    // ================================================================== queue
    private void showQueue(boolean on) {
        queuePanel.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) { fillQueue(); queueList.post(() -> { if (queueList.getChildCount() > 1) queueList.getChildAt(1).requestFocus(); }); }
        else artCur.requestFocus();
    }

    private void fillQueue() {
        queueList.removeAllViews();
        TextView t = MusicUi.text(this, 22, Color.WHITE, true);
        t.setText("Up next");
        t.setPadding(dp(8), 0, 0, dp(14));
        queueList.addView(t);
        for (int i = mp.index + 1; i < Math.min(mp.queue.size(), mp.index + 60); i++) {
            final int at = i;
            View r = MusicUi.songRow(this, mp.queue.get(i), -1, true);
            r.setOnClickListener(v -> { mp.jump(at); showQueue(false); });
            queueList.addView(r, new LinearLayout.LayoutParams(-1, dp(68)));
        }
        if (queueList.getChildCount() == 1) {
            TextView e = MusicUi.text(this, 16, MusicUi.TEXT2, false);
            e.setText(mp.autoplay ? "Similar songs will keep playing." : "Nothing after this song.");
            e.setPadding(dp(8), 0, 0, 0);
            queueList.addView(e);
        }
    }

    // ================================================================== lyrics
    private void setLyrics(boolean on) {
        lyricsOn = on;
        stage.setVisibility(on ? View.GONE : View.VISIBLE);
        lyricsBox.setVisibility(on ? View.VISIBLE : View.GONE);
        MusicUi.setOn(bLyrics, on);
        paintBg();
        MItem cur = mp.current();
        if (on && cur != null) loadLyrics(cur);
        if (!on && !bLyrics.hasFocus()) artCur.requestFocus();
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
        Lyrics hit = lyricsCache.get(cur.videoId);
        if (hit != null) { showLyrics(hit); return; }
        addLine("Finding lyrics…", false);
        final String id = cur.videoId;
        Bg.run(() -> Lyrics.find(cur, null), (l, e) -> {
            if (!id.equals(lyricsFor)) return;
            if (l != null) lyricsCache.put(id, l);
            showLyrics(l);
        });
    }

    private void showLyrics(Lyrics l) {
        lyrics = l;
        lyLines.removeAllViews();
        lyAt = -2;
        if (l == null || l.lines.isEmpty()) {
            addLine("No lyrics for this song.", false);
            lySource.setText("");
            return;
        }
        for (Lyrics.Line ln : l.lines) addLine(ln.text.isEmpty() ? "♪" : ln.text, l.timed);
        lySource.setText(l.source);
        lyScroll.setFocusable(!l.timed);
        if (!l.timed) for (int i = 0; i < lyLines.getChildCount(); i++) ((TextView) lyLines.getChildAt(i)).setTextColor(0xE6FFFFFF);
    }

    private void addLine(String s, boolean timed) {
        TextView t = MusicUi.text(this, 32, timed ? 0x59FFFFFF : 0xB3FFFFFF, true);
        t.setText(s);
        t.setLineSpacing(0, 1.05f);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setPivotX(0);
        lyLines.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void syncLyrics(long pos) {
        if (lyrics == null || !lyrics.timed) return;
        int at = lyrics.at(pos);
        if (at == lyAt) return;
        lyAt = at;
        for (int i = 0; i < lyLines.getChildCount(); i++) {
            TextView t = (TextView) lyLines.getChildAt(i);
            boolean now = i == at;
            // The line being sung is bright; the ones coming dim; the ones gone dimmer.
            t.setTextColor(now ? Color.WHITE : i > at ? 0x66FFFFFF : 0x33FFFFFF);
            t.animate().scaleX(now ? 1.04f : 1f).scaleY(now ? 1.04f : 1f).setDuration(220).start();
        }
        if (at >= 0 && at < lyLines.getChildCount()) {
            View v = lyLines.getChildAt(at);
            int y = v.getTop() - Math.round(lyScroll.getHeight() * 0.30f);
            lyScroll.smoothScrollTo(0, Math.max(0, y));
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
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (down) mp.next();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (down) mp.prev();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) mp.seek(mp.player.getCurrentPosition() + (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? 10_000 : -10_000));
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (!down) more();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) return true;
                if (queuePanel.getVisibility() == View.VISIBLE) showQueue(false);
                else if (lyricsOn) setLyrics(false);
                else finish();
                return true;
            default:
                break;
        }
        if (queuePanel.getVisibility() == View.VISIBLE) return super.dispatchKeyEvent(e);
        // On the cover: left / right change song, like flipping through the carousel.
        if (f == artCur && down && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (e.getRepeatCount() == 0) {
                boolean fwd = k == KeyEvent.KEYCODE_DPAD_RIGHT;
                float dx = dp(fwd ? -60 : 60);
                stage.animate().translationX(dx).alpha(0.4f).setDuration(120).withEndAction(() -> {
                    if (fwd) mp.next(); else mp.prev();
                    stage.setTranslationX(-dx);
                    stage.animate().translationX(0).alpha(1f).setDuration(180).start();
                }).start();
            }
            return true;
        }
        // On the bar: left / right seek.
        if (f == bar && down && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int step = e.getRepeatCount() > 10 ? 30_000 : e.getRepeatCount() > 3 ? 15_000 : 5_000;
            mp.seek(mp.player.getCurrentPosition() + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? step : -step));
            return true;
        }
        if (f == bar && down && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) { mp.toggle(); flashGlyph(); return true; }
        // Lyrics: the screen has no cover to stand on — up/down/OK still work.
        if (lyricsOn && down && f != null && f != bar && f.getParent() != bShuffle.getParent()) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { mp.toggle(); return true; }
        }
        if (lyricsOn && down && k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f.getParent() == bShuffle.getParent()) { bar.requestFocus(); return true; }
        return super.dispatchKeyEvent(e);
    }

    /** The thin progress bar with the played part bright, like the photos. */
    static class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long pos, dur, buf;
        private final RectF r = new RectF();

        Bar(Context c) { super(c); }

        void set(long pos, long dur, long buf) { this.pos = pos; this.dur = dur; this.buf = buf; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float hh = (hasFocus() ? 9 : 6) * d;
            float y = getHeight() / 2f;
            r.set(0, y - hh / 2, getWidth(), y + hh / 2);
            p.setColor(0x40FFFFFF);
            c.drawRoundRect(r, hh, hh, p);
            if (dur > 0) {
                float bw = getWidth() * Math.min(1f, buf / (float) dur);
                r.set(0, y - hh / 2, bw, y + hh / 2);
                p.setColor(0x26FFFFFF);
                c.drawRoundRect(r, hh, hh, p);
                float w = getWidth() * Math.min(1f, pos / (float) dur);
                r.set(0, y - hh / 2, w, y + hh / 2);
                p.setColor(hasFocus() ? Color.WHITE : 0xE6FFFFFF);
                c.drawRoundRect(r, hh, hh, p);
                if (hasFocus()) { p.setColor(Color.WHITE); c.drawCircle(w, y, 9 * d, p); }
            }
        }

        @Override protected void onFocusChanged(boolean f, int dir, android.graphics.Rect prev) { super.onFocusChanged(f, dir, prev); invalidate(); }
    }
}
