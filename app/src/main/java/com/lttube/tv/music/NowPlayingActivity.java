package com.lttube.tv.music;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.lttube.tv.Bg;
import com.lttube.tv.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Now Playing, big-screen style: the artist's picture fills the screen; the cover, song and artist sit bottom-left;
 * a round play button beside a long progress bar; skip / shuffle / repeat below, Lyrics and Queue on the right,
 * Save above them. The controls fade away when you leave the remote alone (just the song stays), and scrolling
 * down shows About the artist. Lyrics opens the sing-along screen.
 */
public class NowPlayingActivity extends Activity implements MusicPlayer.Listener {
    private MusicPlayer mp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private ImageView bgImage;
    private View bgShade;
    private Backdrop backdrop;
    private int W, H;

    // main
    private ScrollView scroll;
    private ImageView art;
    private TextView title, artist, saveBtn;
    private LinearLayout chrome;
    private ImageView playBtn, prevBtn, nextBtn, shufBtn, repBtn;
    private TextView lyricsPill, queuePill;
    private Bar bar;
    private TextView tElapsed, tLeft, note;
    private boolean chromeShown = true;

    // about the artist
    private LinearLayout about;
    private ImageView aboutPic;
    private TextView aboutName, aboutStats, aboutDesc, followBtn, goArtist;
    private String aboutFor;
    private Ytm.Page artistPage;
    private final Map<String, Ytm.Page> artists = new HashMap<>();
    private final Map<String, Boolean> following = new HashMap<>();

    // lyrics
    private FrameLayout lyricsBox;
    private ImageView lyArt;
    private TextView lyTitle, lyArtist, lySource, lyElapsed, lyLeft;
    private FadeFrame lyWindow;
    private LinearLayout lyLines;
    private boolean autoLyrics;
    private Bar lyBar;
    private TextView lyClose, lyQueue;
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
        root.setBackgroundColor(0xFF1E1F21);
        setContentView(root);

        bgImage = new ImageView(this);
        bgImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bgImage, MusicUi.match());
        bgShade = new View(this);
        bgShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x26000000, 0x1A000000, 0x8C000000, 0xE0000000}));
        root.addView(bgShade, MusicUi.match());
        backdrop = new Backdrop(this);
        backdrop.setVisibility(View.GONE);
        root.addView(backdrop, MusicUi.match());

        buildMain();
        buildLyrics();
        buildQueue();
        // Songs open on their lyrics; if a song has none, it switches to the picture screen by itself.
        // The player is the lyrics screen (Back goes back to where you were).
        lyricsOn = true;
        autoLyrics = false;
        refresh(true);
        setLyrics(lyricsOn);
        if (!lyricsOn) playBtn.requestFocus();
        poke();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (!lyricsOn) setLyrics(true);
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    // ================================================================== main screen
    private void buildMain() {
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        scroll.addView(page);
        root.addView(scroll, MusicUi.match());

        // First screen: everything sits along the bottom.
        FrameLayout first = new FrameLayout(this);
        first.setClipChildren(false);
        page.addView(first, new LinearLayout.LayoutParams(-1, dp(H)));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bl.setMargins(dp(MusicUi.PAD), 0, dp(MusicUi.PAD), dp(30));
        first.addView(bottom, bl);

        note = MusicUi.line(this, 14, 0xCCFFFFFF, false);
        bottom.addView(note);

        // song: cover, title, artist — and Save on the right
        LinearLayout song = new LinearLayout(this);
        song.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(this);
        art.setElevation(dp(8));
        song.addView(art, new LinearLayout.LayoutParams(dp(84), dp(84)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(18), 0, dp(16), 0);
        title = MusicUi.line(this, 32, Color.WHITE, true);
        artist = MusicUi.line(this, 16, 0xE6FFFFFF, true);
        artist.setPadding(0, dp(6), 0, 0);
        tt.addView(title);
        tt.addView(artist);
        song.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        saveBtn = MusicUi.pill(this, R.drawable.ic_m_add, "Save");
        saveBtn.setOnClickListener(v -> save());
        song.addView(saveBtn, new LinearLayout.LayoutParams(-2, dp(44)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(6);
        bottom.addView(song, sl);

        // The rest fades away when you leave the remote alone.
        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setClipChildren(false);
        bottom.addView(chrome);

        // play · progress
        LinearLayout prog = new LinearLayout(this);
        prog.setGravity(Gravity.TOP);
        prog.setClipChildren(false);
        playBtn = new ImageView(this);
        playBtn.setFocusable(true);
        playBtn.setImageResource(R.drawable.ic_m_pause);
        playBtn.setPadding(dp(11), dp(11), dp(11), dp(11));
        playBtn.setOnFocusChangeListener((v, has) -> paintPlay());
        playBtn.setOnClickListener(v -> { mp.toggle(); paintPlay(); });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(46), dp(46));
        prog.addView(playBtn, pl);
        LinearLayout barCol = new LinearLayout(this);
        barCol.setOrientation(LinearLayout.VERTICAL);
        bar = new Bar(this);
        bar.setFocusable(true);
        barCol.addView(bar, new LinearLayout.LayoutParams(-1, dp(22)));
        LinearLayout times = new LinearLayout(this);
        tElapsed = MusicUi.text(this, 14, 0xE6FFFFFF, true);
        tLeft = MusicUi.text(this, 14, 0xE6FFFFFF, true);
        times.addView(tElapsed, new LinearLayout.LayoutParams(0, -2, 1));
        tLeft.setGravity(Gravity.END);
        times.addView(tLeft, new LinearLayout.LayoutParams(-2, -2));
        barCol.addView(times);
        LinearLayout.LayoutParams bcl = new LinearLayout.LayoutParams(0, -2, 1);
        bcl.leftMargin = dp(20);
        bcl.topMargin = dp(12);
        prog.addView(barCol, bcl);
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(-1, -2);
        prl.topMargin = dp(16);
        chrome.addView(prog, prl);

        // skip · shuffle · repeat ……… Lyrics · Queue
        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setClipChildren(false);
        prevBtn = MusicUi.iconButton(this, R.drawable.ic_m_prev, 42);
        nextBtn = MusicUi.iconButton(this, R.drawable.ic_m_next, 42);
        shufBtn = MusicUi.iconButton(this, R.drawable.ic_m_shuffle, 42);
        repBtn = MusicUi.iconButton(this, R.drawable.ic_m_repeat, 42);
        prevBtn.setOnClickListener(v -> mp.prev());
        nextBtn.setOnClickListener(v -> mp.next());
        shufBtn.setOnClickListener(v -> { mp.setShuffle(!mp.shuffle); toast(mp.shuffle ? "Shuffle on" : "Shuffle off"); });
        repBtn.setOnClickListener(v -> { mp.cycleRepeat(); toast(mp.repeat == 0 ? "Repeat off" : mp.repeat == 1 ? "Repeat all" : "Repeat one"); });
        for (ImageView v : new ImageView[]{prevBtn, nextBtn, shufBtn, repBtn}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
            lp.rightMargin = dp(14);
            btns.addView(v, lp);
        }
        btns.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        lyricsPill = MusicUi.pill(this, R.drawable.ic_m_lyrics, "Lyrics");
        lyricsPill.setOnClickListener(v -> setLyrics(true));
        btns.addView(lyricsPill, new LinearLayout.LayoutParams(-2, dp(44)));
        queuePill = MusicUi.pill(this, R.drawable.ic_m_queue, "Queue");
        queuePill.setOnClickListener(v -> showQueue(true));
        LinearLayout.LayoutParams ql = new LinearLayout.LayoutParams(-2, dp(44));
        ql.leftMargin = dp(14);
        btns.addView(queuePill, ql);
        LinearLayout.LayoutParams btl = new LinearLayout.LayoutParams(-1, -2);
        btl.topMargin = dp(10);
        chrome.addView(btns, btl);

        // ---- About the artist (scroll down)
        about = new LinearLayout(this);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding(dp(MusicUi.PAD), dp(10), dp(MusicUi.PAD), dp(48));
        about.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xE0000000, 0xF2141415}));
        TextView ah = MusicUi.text(this, 20, Color.WHITE, true);
        ah.setText("About the artist");
        ah.setPadding(0, dp(8), 0, dp(14));
        about.addView(ah);
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        aboutPic = new ImageView(this);
        card.addView(aboutPic, new LinearLayout.LayoutParams(dp(190), dp(190)));
        LinearLayout at = new LinearLayout(this);
        at.setOrientation(LinearLayout.VERTICAL);
        at.setPadding(dp(26), 0, 0, 0);
        aboutName = MusicUi.line(this, 32, Color.WHITE, true);
        aboutStats = MusicUi.line(this, 16, 0xE6FFFFFF, true);
        aboutStats.setPadding(0, dp(8), 0, 0);
        aboutDesc = MusicUi.text(this, 14, 0xB3FFFFFF, false);
        aboutDesc.setMaxLines(3);
        aboutDesc.setEllipsize(TextUtils.TruncateAt.END);
        aboutDesc.setPadding(0, dp(8), 0, 0);
        at.addView(aboutName);
        at.addView(aboutStats);
        at.addView(aboutDesc);
        LinearLayout ab = new LinearLayout(this);
        ab.setPadding(0, dp(16), 0, 0);
        followBtn = MusicUi.pill(this, 0, "Follow");
        followBtn.setOnClickListener(v -> follow());
        ab.addView(followBtn, new LinearLayout.LayoutParams(-2, dp(42)));
        goArtist = MusicUi.pill(this, 0, "Go to artist");
        goArtist.setOnClickListener(v -> openArtist());
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, dp(42));
        gl.leftMargin = dp(14);
        ab.addView(goArtist, gl);
        at.addView(ab);
        card.addView(at, new LinearLayout.LayoutParams(0, -2, 1));
        about.addView(card);
        about.setVisibility(View.GONE);
        page.addView(about, new LinearLayout.LayoutParams(-1, -2));
    }

    private void paintPlay() {
        boolean playing = mp.player.getPlayWhenReady();
        playBtn.setImageResource(playing ? R.drawable.ic_m_pause : R.drawable.ic_m_play);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.WHITE);
        if (playBtn.hasFocus()) g.setStroke(dp(3), 0xFF888888);
        playBtn.setBackground(g);
        playBtn.setColorFilter(0xFF121212);
    }

    // ================================================================== lyrics
    private void buildLyrics() {
        lyricsBox = new FrameLayout(this);
        lyricsBox.setVisibility(View.GONE);
        lyricsBox.setClipChildren(false);
        root.addView(lyricsBox, MusicUi.match());
        int a = Math.round(H * 0.44f);
        int ax = Math.round(W * 0.10f), ay = Math.round(H * 0.17f);
        lyArt = new ImageView(this);
        lyArt.setElevation(dp(16));
        lyricsBox.addView(lyArt, at(ax, ay, a, a));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        lyTitle = MusicUi.line(this, 19, Color.WHITE, true);
        lyArtist = MusicUi.line(this, 16, 0xB3FFFFFF, true);
        lyArtist.setPadding(0, dp(4), 0, 0);
        tt.addView(lyTitle);
        tt.addView(lyArtist);
        lyricsBox.addView(tt, at(ax, ay + a + 14, a + 40, -2));

        // The bar sits low; the lyrics get the space above it and never reach it.
        int by = Math.round(H * 0.82f);
        // The window starts well left of the words, so a line's first letters (and their blur, and any letter
        // that leans left) always have room inside it.
        int lx = Math.round(W * 0.46f) - 44, ly = Math.round(H * 0.04f);
        // Soft, long fades at the top and bottom; the lines have room on the right so nothing is cut.
        lyWindow = new FadeFrame(this, dp(96), dp(84));
        lyLines = new LinearLayout(this);
        lyLines.setOrientation(LinearLayout.VERTICAL);
        // Every line can be drawn wherever it slides to (the window around them does the cutting, softly).
        lyLines.setClipChildren(false);
        lyLines.setClipToPadding(false);
        lyWindow.addView(lyLines, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        lyricsBox.addView(lyWindow, at(lx, ly, W - lx - 16, by - ly - 18));
        lySource = MusicUi.text(this, 12, 0x99FFFFFF, false);
        lySource.setVisibility(View.GONE);
        lySource.setPadding(0, dp(8), 0, 0);
        tt.addView(lySource);

        // progress along the bottom; Lyrics / Queue under it on the right
        lyBar = new Bar(this);
        lyBar.setFocusable(true);
        lyricsBox.addView(lyBar, at(ax, by, W - ax - MusicUi.PAD, 22));
        lyElapsed = MusicUi.text(this, 14, 0xE6FFFFFF, true);
        lyricsBox.addView(lyElapsed, at(ax, by + 24, -2, -2));
        lyLeft = MusicUi.text(this, 14, 0xE6FFFFFF, true);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
        ll.setMargins(0, dp(by + 24), dp(MusicUi.PAD), 0);
        lyricsBox.addView(lyLeft, ll);
        LinearLayout lb = new LinearLayout(this);
        lyClose = MusicUi.pill(this, R.drawable.ic_m_add, "Save");
        lyClose.setOnClickListener(v -> save());
        lb.addView(lyClose, new LinearLayout.LayoutParams(-2, dp(40)));
        TextView lyArtistBtn = MusicUi.pill(this, 0, "Artist");
        lyArtistBtn.setOnClickListener(v -> openArtist());
        LinearLayout.LayoutParams lal = new LinearLayout.LayoutParams(-2, dp(40));
        lal.leftMargin = dp(12);
        lb.addView(lyArtistBtn, lal);
        lyQueue = MusicUi.pill(this, R.drawable.ic_m_queue, "Queue");
        lyQueue.setOnClickListener(v -> showQueue(true));
        LinearLayout.LayoutParams lql = new LinearLayout.LayoutParams(-2, dp(40));
        lql.leftMargin = dp(12);
        lb.addView(lyQueue, lql);
        FrameLayout.LayoutParams lbl = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        lbl.setMargins(0, 0, dp(MusicUi.PAD), dp(10));
        lyricsBox.addView(lb, lbl);
    }

    private FrameLayout.LayoutParams at(int x, int y, int w, int hh) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : dp(w), hh < 0 ? hh : dp(hh));
        lp.leftMargin = dp(x);
        lp.topMargin = dp(y);
        return lp;
    }

    // ================================================================== queue
    private void buildQueue() {
        queuePanel = new FrameLayout(this);
        queuePanel.setVisibility(View.GONE);
        queuePanel.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00000000, 0xD9141415, 0xF7141415}));
        ScrollView qs = new ScrollView(this);
        qs.setVerticalScrollBarEnabled(false);
        queueList = new LinearLayout(this);
        queueList.setOrientation(LinearLayout.VERTICAL);
        queueList.setPadding(dp(70), dp(30), dp(MusicUi.PAD), dp(30));
        qs.addView(queueList);
        queuePanel.addView(qs, MusicUi.match());
        root.addView(queuePanel, new FrameLayout.LayoutParams(dp(Math.round(W * 0.52f)), -1, Gravity.END));
    }

    private void showQueue(boolean on) {
        queuePanel.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) { fillQueue(); queueList.post(() -> { if (queueList.getChildCount() > 2) queueList.getChildAt(2).requestFocus(); }); }
        else (lyricsOn ? lyQueue : queuePill).requestFocus();
    }

    private void fillQueue() {
        queueList.removeAllViews();
        TextView t = MusicUi.text(this, 24, Color.WHITE, true);
        t.setText("Queue");
        queueList.addView(t);
        TextView s = MusicUi.text(this, 14, 0x99FFFFFF, false);
        s.setText(mp.autoplay ? "Next up — then similar music keeps playing" : "Next up");
        s.setPadding(0, dp(6), 0, dp(12));
        queueList.addView(s);
        for (int i = mp.index + 1; i < Math.min(mp.queue.size(), mp.index + 60); i++) {
            final int k = i;
            View r = MusicUi.songRow(this, mp.queue.get(i), -1, false);
            r.setOnClickListener(v -> { mp.jump(k); showQueue(false); });
            queueList.addView(r, new LinearLayout.LayoutParams(-1, dp(62)));
        }
    }

    // ================================================================== state
    private void refresh(boolean force) {
        MItem cur = mp.current();
        String id = cur == null ? "" : cur.videoId;
        if (force || !id.equals(shownId)) {
            shownId = id;
            if (cur != null) {
                MusicUi.art(art, cur.thumb, dp(4));
                MusicUi.art(lyArt, cur.thumb, dp(10));
                title.setText(cur.title);
                artist.setText(cur.artist.isEmpty() ? cur.subtitle : cur.artist);
                lyTitle.setText(cur.title);
                lyArtist.setText(cur.artist);
                backdrop.setArt(cur.thumb);
                Boolean l = liked.get(cur.videoId);
                paintSave(l != null && l);
                lyricsFor = null;
                lyrics = null;
                lyLines.removeAllViews();
                lySource.setText("");
                loadArtist(cur);
            } else {
                art.setImageDrawable(MusicUi.round(0xFF3A3C40, dp(4)));
                title.setText("Not playing");
                artist.setText("Pick something from Home");
            }
            if (queuePanel.getVisibility() == View.VISIBLE) fillQueue();
        }
        MusicUi.setOn(shufBtn, mp.shuffle);
        repBtn.setImageResource(mp.repeat == MusicPlayer.REPEAT_ONE ? R.drawable.ic_m_repeat_one : R.drawable.ic_m_repeat);
        MusicUi.setOn(repBtn, mp.repeat != MusicPlayer.REPEAT_OFF);
        note.setText(mp.error != null ? mp.error : mp.resolving ? "Loading…" : "");
        paintPlay();
    }

    /** The artist's picture behind everything, and their About card. */
    private void loadArtist(MItem cur) {
        String key = cur.artistId;
        if (key == null || key.isEmpty()) { setBg(MItem.art(cur.thumb, 1080)); about.setVisibility(View.GONE); aboutFor = null; return; }
        if (key.equals(aboutFor)) return;
        aboutFor = key;
        Ytm.Page hit = artists.get(key);
        if (hit != null) { showArtist(hit, cur); return; }
        setBg(MItem.art(cur.thumb, 1080));
        Bg.run(() -> Ytm.browse(key), (p, e) -> {
            if (p == null || !key.equals(aboutFor)) return;
            artists.put(key, p);
            showArtist(p, cur);
        });
    }

    private void showArtist(Ytm.Page p, MItem cur) {
        artistPage = p;
        setBg(!p.banner.isEmpty() ? p.banner : !p.thumb.isEmpty() ? MItem.art(p.thumb, 1080) : MItem.art(cur.thumb, 1080));
        MusicUi.art(aboutPic, p.thumb.isEmpty() ? cur.thumb : p.thumb, dp(12));
        aboutName.setText(p.title.isEmpty() ? cur.artist : p.title);
        String stats = p.subscribers;
        if (!p.monthly.isEmpty()) stats += (stats.isEmpty() ? "" : "   ·   ") + p.monthly;
        aboutStats.setText(stats);
        aboutStats.setVisibility(stats.isEmpty() ? View.GONE : View.VISIBLE);
        aboutDesc.setText(p.description);
        aboutDesc.setVisibility(p.description.isEmpty() ? View.GONE : View.VISIBLE);
        Boolean f = following.get(aboutFor);
        followBtn.setText(f != null && f ? "Following" : "Follow");
        about.setVisibility(View.VISIBLE);
    }

    private String bgUrl;
    private void setBg(String url) {
        if (url == null || url.isEmpty() || url.equals(bgUrl)) return;
        bgUrl = url;
        Glide.with(this).load(url).transition(DrawableTransitionOptions.withCrossFade(500)).into(bgImage);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long pos = Math.max(0, mp.player.getCurrentPosition());
            long dur = mp.player.getDuration();
            MItem cur = mp.current();
            if (dur <= 0 && cur != null) dur = cur.seconds * 1000L;
            String el = MItem.time(pos / 1000), left = dur > 0 ? "-" + MItem.time(Math.max(0, dur - pos) / 1000) : "";
            if (lyricsOn) {
                lyBar.set(pos, dur);
                lyElapsed.setText(el);
                lyLeft.setText(left);
                if (lyricsFor == null && cur != null && !mp.resolving && mp.player.getDuration() > 0) loadLyrics(cur);
                syncLyrics(pos);
            } else {
                bar.set(pos, dur);
                tElapsed.setText(el);
                tLeft.setText(left);
            }
            h.postDelayed(this, lyricsOn ? 40 : 250);
        }
    };

    // ---- controls fade away when the remote is left alone
    private final Runnable hide = () -> showChrome(false);

    private void poke() {
        h.removeCallbacks(hide);
        if (!chromeShown) showChrome(true);
        h.postDelayed(hide, 6000);
    }

    private void showChrome(boolean on) {
        if (lyricsOn || queuePanel.getVisibility() == View.VISIBLE || scroll.getScrollY() > 10) { chromeShown = true; return; }
        chromeShown = on;
        chrome.animate().alpha(on ? 1f : 0f).setDuration(on ? 200 : 600).start();
        saveBtn.animate().alpha(on ? 1f : 0f).setDuration(on ? 200 : 600).start();
        note.animate().alpha(on ? 1f : 0f).setDuration(300).start();
        bgShade.animate().alpha(on ? 1f : 0.55f).setDuration(600).start();
    }

    @Override public void changed() { refresh(false); }
    @Override protected void onResume() { super.onResume(); mp.addListener(this); refresh(true); h.post(tick); poke(); }
    @Override protected void onPause() { mp.removeListener(this); h.removeCallbacks(tick); h.removeCallbacks(hide); super.onPause(); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ================================================================== save / follow / artist
    private void paintSave(boolean on) {
        for (TextView b : new TextView[]{saveBtn, lyClose}) {
            if (b == null) continue;
            b.setText(on ? "Saved" : "Save");
            android.graphics.drawable.Drawable d = getDrawable(on ? R.drawable.ic_check : R.drawable.ic_m_add).mutate();
            d.setBounds(0, 0, dp(20), dp(20));
            d.setTint(on ? MusicUi.PINK : Color.WHITE);
            b.setCompoundDrawables(d, null, null, null);
        }
    }

    /** Save: adds the song to your Liked songs on YouTube Music. */
    private void save() {
        MItem cur = mp.current();
        if (cur == null) return;
        if (!Ytm.signedIn()) { toast("Sign in (Your Library) to save songs"); return; }
        Boolean was = liked.get(cur.videoId);
        boolean on = was == null || !was;
        liked.put(cur.videoId, on);
        paintSave(on);
        Bg.run(() -> { Ytm.like(cur.videoId, on); return true; }, (ok, e) -> {
            if (e != null) { liked.put(cur.videoId, !on); paintSave(!on); toast("Couldn't update your library"); }
            else toast(on ? "Saved to Liked songs" : "Removed from Liked songs");
        });
    }

    private void follow() {
        if (artistPage == null || aboutFor == null) return;
        if (!Ytm.signedIn()) { toast("Sign in (Your Library) to follow artists"); return; }
        final String id = artistPage.channelId.isEmpty() ? aboutFor : artistPage.channelId;
        Boolean was = following.get(aboutFor);
        boolean on = was == null || !was;
        following.put(aboutFor, on);
        followBtn.setText(on ? "Following" : "Follow");
        final String key = aboutFor;
        Bg.run(() -> { Ytm.follow(id, on); return true; }, (ok, e) -> {
            if (e != null) { following.put(key, !on); if (key.equals(aboutFor)) followBtn.setText(!on ? "Following" : "Follow"); toast("Couldn't update that"); }
        });
    }

    private void openArtist() {
        MItem cur = mp.current();
        if (cur == null || cur.artist.isEmpty()) { toast("No artist page for this song"); return; }
        // The artist's page (found by name): their photo, top songs, albums and singles.
        MItem a = new MItem();
        a.type = MItem.ARTIST;
        a.title = cur.artist.split(",|&| feat\\.")[0].trim();
        a.browseId = "AMN:" + a.title;
        MusicUi.open(this, a, null);
    }

    private void openArtistOld() {
        MItem a = new MItem();
        a.type = MItem.ARTIST;
        a.browseId = aboutFor;
        a.title = aboutName.getText().toString();
        a.thumb = artistPage == null ? "" : artistPage.thumb;
        MusicUi.open(this, a, null);
    }

    // ================================================================== lyrics
    private void setLyrics(boolean on) {
        lyricsOn = on;
        scroll.setVisibility(on ? View.GONE : View.VISIBLE);
        lyricsBox.setVisibility(on ? View.VISIBLE : View.GONE);
        backdrop.setVisibility(on ? View.VISIBLE : View.GONE);
        backdrop.setMood(true);
        bgImage.setVisibility(on ? View.GONE : View.VISIBLE);
        bgShade.setVisibility(on ? View.GONE : View.VISIBLE);
        if (on) { lyAt = -2; lyClose.requestFocus(); } else { showChrome(true); lyricsPill.requestFocus(); poke(); }
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
        lyLines.setTranslationY(0);
        if (l == null || l.lines.isEmpty()) {
            addLine("Lyrics aren't available for this song.", false);
            return;
        }
        for (Lyrics.Line ln : l.lines) addLine(ln.text.isEmpty() ? "♪" : ln.text, l.timed);
        lySource.setText(l.source);
        lyLines.post(() -> { if (lyrics == l) place(l.timed ? -1 : 0, false); });
    }

    private void addLine(String s, boolean timed) {
        LyricLine t = new LyricLine(this);
        t.setTextSize(32);
        t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        t.setIncludeFontPadding(false);
        t.setTextColor(Color.WHITE);
        t.setAlpha(timed ? 0.45f : 0.9f);
        t.setText(Lyrics.censor(s));
        t.setLineSpacing(0, 1.08f);
        // Room on both sides: the first letters (and their soft blur) never touch the edge.
        t.setPadding(dp(48), dp(10), dp(MusicUi.PAD + 14), dp(10));
        t.setPivotX(0);
        t.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        lyLines.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void syncLyrics(long pos) {
        if (lyrics == null || !lyrics.timed) return;
        int at = lyrics.at(pos);
        if (at == lyAt) return;
        lyAt = at;
        place(at, true);
    }

    /**
     * Apple Music's lyric motion: the line being sung, bright and sharp, rests near the top; the lines after it are
     * dimmer and softly blurred; the one just sung fades out above. On each new line every line slides up on its
     * own, a few milliseconds after the one above it, so the lyrics ripple up like a wave.
     */
    private void place(int at, boolean animate) {
        int n = lyLines.getChildCount();
        if (n == 0) return;
        int anchor = Math.round(lyWindow.getHeight() * 0.14f);
        int ref = at >= 0 && at < n ? lyLines.getChildAt(at).getTop() : lyLines.getChildAt(0).getTop() - Math.round(lyWindow.getHeight() * 0.20f);
        float shift = anchor - ref;
        boolean timed = lyrics != null && lyrics.timed;
        for (int i = 0; i < n; i++) {
            LyricLine t = (LyricLine) lyLines.getChildAt(i);
            int d = at < 0 ? i + 1 : i - at;
            t.setProgress(-1f, 0x66FFFFFF);
            float alpha;
            float blur;
            if (!timed) { alpha = 0.92f; blur = 0; }
            else if (d == 0) { alpha = 1f; blur = 0; }
            else if (d > 0) { alpha = Math.max(0.22f, 0.55f - 0.08f * (d - 1)); blur = Math.min(dp(5), dp(2) + dp(1) * (d - 1)); }
            else { alpha = d == -1 ? 0.28f : 0.0f; blur = dp(3); }
            t.setBlur(blur);
            // Lines well off screen just jump; the ones you can see glide.
            if (!animate || d < -3 || d > 8) {
                t.animate().cancel();
                t.setTranslationY(shift);
                t.setAlpha(alpha);
                continue;
            }
            long delay = Math.max(0, d) * 45L;
            t.animate().cancel();
            t.animate().translationY(shift).alpha(alpha).setStartDelay(delay).setDuration(620)
                    .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.85f, 0.25f, 1f)).start();
        }
    }

    /** Untimed lyrics: up / down moves them. */
    private void glideTo(float y) {
        for (int i = 0; i < lyLines.getChildCount(); i++) {
            View t = lyLines.getChildAt(i);
            t.animate().cancel();
            t.animate().translationY(y).setStartDelay(0).setDuration(450).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ================================================================== remote
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        View f = getCurrentFocus();
        // Remote left alone, controls hidden: the first press just brings them back.
        if (down && !chromeShown && !lyricsOn && k != KeyEvent.KEYCODE_BACK && !isMedia(k)) { poke(); return true; }
        if (down) poke();
        switch (k) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && e.getRepeatCount() == 0) { mp.toggle(); paintPlay(); }
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: if (down) mp.next(); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: if (down) mp.prev(); return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) mp.seek(mp.player.getCurrentPosition() + (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? 10_000 : -10_000));
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (down) return true;
                if (queuePanel.getVisibility() == View.VISIBLE) showQueue(false);
                else if (lyricsOn) finish();
                else if (scroll.getScrollY() > 10) { scroll.smoothScrollTo(0, 0); playBtn.requestFocus(); }
                else finish();
                return true;
            default: break;
        }
        if (queuePanel.getVisibility() == View.VISIBLE) return super.dispatchKeyEvent(e);
        Bar b = f == bar ? bar : f == lyBar ? lyBar : null;
        if (b != null && down && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            int step = e.getRepeatCount() > 12 ? 30_000 : e.getRepeatCount() > 4 ? 15_000 : 5_000;
            mp.seek(mp.player.getCurrentPosition() + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? step : -step));
            return true;
        }
        if (b != null && down && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) { mp.toggle(); paintPlay(); return true; }
        // Main screen paths: Save ↔ bar ↔ buttons; down past the buttons to About the artist.
        if (!lyricsOn && down) {
            if (k == KeyEvent.KEYCODE_DPAD_UP && (f == playBtn || f == bar)) { saveBtn.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && f == saveBtn) { playBtn.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && (f == playBtn || f == bar)) { prevBtn.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP && f != null && f.getParent() == prevBtn.getParent()) { playBtn.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && f != null && f.getParent() == prevBtn.getParent()) {
                if (about.getVisibility() == View.VISIBLE) { followBtn.requestFocus(); scroll.smoothScrollTo(0, about.getTop() - dp(40)); }
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP && (f == followBtn || f == goArtist)) { scroll.smoothScrollTo(0, 0); playBtn.requestFocus(); return true; }
        }
        if (lyricsOn && down) {
            // Lyrics without timing: up / down on the buttons scroll them.
            if (lyrics != null && !lyrics.timed && (f == lyClose || f == lyQueue) && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
                float max = Math.max(0, lyLines.getHeight() - lyWindow.getHeight() * 0.6f);
                float cur = lyLines.getChildCount() > 0 ? lyLines.getChildAt(0).getTranslationY() : 0;
                float y = cur + dp(k == KeyEvent.KEYCODE_DPAD_UP ? 90 : -90);
                glideTo(Math.max(-max, Math.min(0, y)));
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && f == lyBar) { lyClose.requestFocus(); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_UP && (f == lyClose || f == lyQueue)) { lyBar.requestFocus(); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    private static boolean isMedia(int k) {
        return k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE
                || k == KeyEvent.KEYCODE_MEDIA_NEXT || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || k == KeyEvent.KEYCODE_MEDIA_REWIND;
    }

    /** A long thin progress line: the played part white, the rest faint; thicker when you're on it. */
    static class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long pos, dur;
        private final RectF r = new RectF();

        Bar(Context c) { super(c); }

        void set(long pos, long dur) { this.pos = pos; this.dur = dur; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float hh = (hasFocus() ? 7 : 4) * d;
            float y = getHeight() / 2f;
            r.set(0, y - hh / 2, getWidth(), y + hh / 2);
            p.setColor(0x59FFFFFF);
            c.drawRoundRect(r, hh, hh, p);
            if (dur > 0) {
                float w = getWidth() * Math.min(1f, pos / (float) dur);
                r.set(0, y - hh / 2, w, y + hh / 2);
                p.setColor(Color.WHITE);
                c.drawRoundRect(r, hh, hh, p);
                if (hasFocus()) c.drawCircle(w, y, 8 * d, p);
            }
        }

        @Override protected void onFocusChanged(boolean f, int dir, android.graphics.Rect prev) { super.onFocusChanged(f, dir, prev); invalidate(); }
    }
}
