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
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.lttube.tv.Bg;
import com.lttube.tv.Feeds;
import com.lttube.tv.NpDownloader;
import com.lttube.tv.R;

import org.json.JSONArray;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;

/**
 * LT Music's own music video screen (not YouTube's look): the video full screen, and over it — when you press a
 * button — the title with its "E", the artist, a thin progress line with times, in LT Music's style. Next /
 * previous move through the videos it was opened from. The music pauses while you watch.
 */
public class MusicVideoActivity extends Activity {
    public static final String LIST = "list", AT = "at";

    private final List<MItem> list = new ArrayList<>();
    private int at;
    private ExoPlayer player;
    private PlayerView view;
    private LinearLayout chrome;
    private TextView title, artist, elapsed, left;
    private NowPlayingActivity.Bar bar;
    private ImageView playIcon;
    private ProgressBar spinner;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int seq;
    private OkHttpDataSource.Factory http;

    private final Runnable hide = () -> chrome.animate().alpha(0f).setDuration(600).start();
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null) {
                long pos = player.getCurrentPosition(), dur = Math.max(0, player.getDuration());
                bar.set(pos, dur);
                elapsed.setText(time(pos));
                left.setText(dur > 0 ? "-" + time(Math.max(0, dur - pos)) : "");
            }
            h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            JSONArray a = new JSONArray(getIntent().getStringExtra(LIST));
            for (int i = 0; i < a.length(); i++) list.add(MItem.fromJson(a.optJSONObject(i)));
        } catch (Exception e) { finish(); return; }
        at = Math.max(0, Math.min(list.size() - 1, getIntent().getIntExtra(AT, 0)));
        MusicPlayer.get(this).pause();

        String ua = YoutubeParsingHelper.getVisionOsUserAgent(new Localization("en", "US"));
        http = new OkHttpDataSource.Factory(NpDownloader.get().client).setUserAgent(ua);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        view = new PlayerView(this);
        view.setUseController(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setShutterBackgroundColor(Color.BLACK);
        root.addView(view, MusicUi.match());

        // The overlay: a soft shade at the bottom, the song on the left, a thin line of progress with times.
        chrome = new LinearLayout(this);
        chrome.setOrientation(LinearLayout.VERTICAL);
        chrome.setGravity(Gravity.BOTTOM);
        chrome.setPadding(dp(MusicUi.PAD), 0, dp(MusicUi.PAD), dp(28));
        chrome.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x66000000, 0x00000000}));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        playIcon = new ImageView(this);
        playIcon.setColorFilter(Color.WHITE);
        top.addView(playIcon, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tt.setPadding(dp(14), 0, 0, 0);
        title = MusicUi.line(this, 22, Color.WHITE, true);
        artist = MusicUi.line(this, 16, MusicUi.TEXT2, false);
        artist.setTypeface(MusicUi.font(600));
        tt.addView(title);
        tt.addView(artist);
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        chrome.addView(top);
        bar = new NowPlayingActivity.Bar(this);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(16));
        bl.topMargin = dp(14);
        chrome.addView(bar, bl);
        LinearLayout times = new LinearLayout(this);
        elapsed = MusicUi.text(this, 12, 0xCCFFFFFF, false);
        left = MusicUi.text(this, 12, 0xCCFFFFFF, false);
        left.setGravity(Gravity.END);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        chrome.addView(times);
        root.addView(chrome, new FrameLayout.LayoutParams(-1, dp(220), Gravity.BOTTOM));

        spinner = new ProgressBar(this);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));


        // Smooth on a TV: YouTube's adaptive stream (chunks that match the connection), up to 1080p in the format
        // every Fire TV decodes in hardware, with a good buffer ahead.
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector ts = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this);
        ts.setParameters(ts.buildUponParameters().setMaxVideoSize(1920, 1080).setPreferredVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264));
        androidx.media3.exoplayer.DefaultLoadControl load = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(20_000, 60_000, 1_500, 3_000).setPrioritizeTimeOverSizeThresholds(true).build();
        player = new ExoPlayer.Builder(this).setTrackSelector(ts).setLoadControl(load)
                .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http)).build();
        player.setVideoChangeFrameRateStrategy(androidx.media3.common.C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF);
        view.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                spinner.setVisibility(s == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (s == Player.STATE_ENDED) { if (at + 1 < list.size()) go(at + 1); else finish(); }
            }
            @Override public void onIsPlayingChanged(boolean p) { paintPlay(); }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException e) {
                android.widget.Toast.makeText(MusicVideoActivity.this, "Couldn't play this video", android.widget.Toast.LENGTH_SHORT).show();
                if (at + 1 < list.size()) go(at + 1);
            }
        });
        go(at);
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    private static String time(long ms) { long s = ms / 1000; return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60); }

    private void paintPlay() { playIcon.setImageResource(player != null && player.getPlayWhenReady() ? R.drawable.ic_m_pause : R.drawable.ic_m_play); }

    /** Plays video i of the list (finding it on YouTube first if it came from Apple Music). */
    private void go(int i) {
        at = i;
        final int my = ++seq;
        final MItem m = list.get(i);
        title.setText(MusicUi.named(m));
        artist.setText(m.artist);
        show();
        spinner.setVisibility(View.VISIBLE);
        player.stop();
        Bg.run(() -> {
            if (m.videoId.isEmpty()) MusicPlayer.resolveVideo(m);
            return source(StreamInfo.getInfo(Feeds.yt(), "https://www.youtube.com/watch?v=" + m.videoId));
        }, (src, e) -> {
            if (my != seq || isFinishing()) return;
            if (src == null) {
                android.widget.Toast.makeText(this, "Couldn't play " + m.title, android.widget.Toast.LENGTH_SHORT).show();
                if (at + 1 < list.size()) go(at + 1); else finish();
                return;
            }
            player.setMediaSource(src);
            player.prepare();
            player.play();
        });
    }

    /** The best picture up to 1080p (AVC first: every Fire TV plays it smoothly) with the best original audio. */
    private MediaSource source(StreamInfo si) throws Exception {
        String hls = si.getHlsUrl();
        if (hls != null && !hls.isEmpty())
            return new androidx.media3.exoplayer.hls.HlsMediaSource.Factory(http)
                    .createMediaSource(new MediaItem.Builder().setUri(hls).setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8).build());
        VideoStream best = null;
        for (VideoStream v : si.getVideoOnlyStreams()) {
            if (!v.isUrl() || v.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP || v.getHeight() > 1080) continue;
            boolean avc = v.getCodec() != null && v.getCodec().startsWith("avc");
            if (best == null) { best = v; continue; }
            boolean bestAvc = best.getCodec() != null && best.getCodec().startsWith("avc");
            if ((avc && !bestAvc) || (avc == bestAvc && v.getHeight() > best.getHeight())) best = v;
        }
        AudioStream audio = null;
        for (AudioStream a : si.getAudioStreams()) {
            if (!a.isUrl() || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            boolean orig = a.getAudioTrackType() == null || a.getAudioTrackType() == AudioTrackType.ORIGINAL;
            if (!orig) continue;
            boolean aac = a.getFormat() == org.schabi.newpipe.extractor.MediaFormat.M4A;
            if (audio == null || (aac && audio.getFormat() != org.schabi.newpipe.extractor.MediaFormat.M4A)
                    || (aac == (audio.getFormat() == org.schabi.newpipe.extractor.MediaFormat.M4A) && a.getAverageBitrate() > audio.getAverageBitrate())) audio = a;
        }
        ProgressiveMediaSource.Factory f = new ProgressiveMediaSource.Factory(http);
        if (best != null && audio != null)
            return new MergingMediaSource(f.createMediaSource(MediaItem.fromUri(best.getContent())), f.createMediaSource(MediaItem.fromUri(audio.getContent())));
        VideoStream muxed = null;
        for (VideoStream v : si.getVideoStreams()) if (v.isUrl() && (muxed == null || v.getHeight() > muxed.getHeight())) muxed = v;
        if (muxed == null) throw new Exception("no stream");
        return f.createMediaSource(MediaItem.fromUri(muxed.getContent()));
    }

    private void show() {
        h.removeCallbacks(hide);
        chrome.animate().alpha(1f).setDuration(200).start();
        h.postDelayed(hide, 3000);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (k == KeyEvent.KEYCODE_BACK) { if (!down) finish(); return true; }
        if (!down) return true;
        boolean hidden = chrome.getAlpha() < 0.5f;
        show();
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (e.getRepeatCount() == 0 && !(hidden && k == KeyEvent.KEYCODE_DPAD_CENTER)) player.setPlayWhenReady(!player.getPlayWhenReady());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                player.seekTo(Math.min(Math.max(0, player.getDuration()), player.getCurrentPosition() + 10_000)); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_MEDIA_REWIND:
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10_000)); return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_DPAD_DOWN:
                if (at + 1 < list.size()) go(at + 1); return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: case KeyEvent.KEYCODE_DPAD_UP:
                if (at > 0) go(at - 1); return true;
            default: return true;
        }
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); if (player != null) player.play(); }
    @Override protected void onPause() { h.removeCallbacks(tick); if (player != null) player.pause(); super.onPause(); }
    @Override protected void onDestroy() { h.removeCallbacksAndMessages(null); if (player != null) player.release(); player = null; super.onDestroy(); }
}
