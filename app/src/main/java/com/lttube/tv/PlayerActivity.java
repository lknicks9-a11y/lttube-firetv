package com.lttube.tv;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Frameset;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;

/** The YouTube TV-style player. */
public class PlayerActivity extends FragmentActivity {
    public static final String ITEM = "item";

    private ExoPlayer player;
    private DefaultTrackSelector selector;
    private PlayerView video;
    private OkHttpDataSource.Factory http;
    private final Handler h = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout overlay, topInfo, controls, upNextRow, upNextCards;
    private HorizontalScrollView upNextScroll;
    private TextView titleView, channelView, metaView, timeView, chapterView, focusLabel, upNextHint;
    private ImageView avatarView;
    private TimeBar bar;
    private ImageView btnPlay, btnLike, btnDislike, btnCC;
    private TextView btnSub;
    private FrameLayout previewBox;
    private ImageView previewImg;
    private TextView previewTime;
    private TextView status, skipNote, upNextPill;
    private ProgressBar spinner;
    private FrameLayout panel;
    private LinearLayout panelList;
    private ScrollView panelScroll;

    private Item item;
    private StreamInfo info;
    private boolean triedFallback, portraitChecked;
    private int loadSeq;
    private String originalLang;
    private String likeStatus = "INDIFFERENT";
    private final List<long[]> skips = new ArrayList<>();
    private final List<Boolean> skipped = new ArrayList<>();
    private final List<Item> prevShorts = new ArrayList<>();
    private Item nextItem;
    private int countdown;
    private long lastSave;
    private Frameset frames;
    private final Map<Integer, Bitmap> pages = new HashMap<>();
    private final Map<Integer, Boolean> pageLoading = new HashMap<>();
    private Page commentsNext;
    private String commentsUrl;
    private boolean commentsLoading;
    private float d;

    // ================================================================== setup
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        String ua = YoutubeParsingHelper.getVisionOsUserAgent(new Localization("en", "US"));
        http = new OkHttpDataSource.Factory(NpDownloader.get().client).setUserAgent(ua);
        createPlayer();

        item = Item.fromJson(parse(getIntent().getStringExtra(ITEM)));
        load(item);
        tick();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setFocusable(true);

        video = new PlayerView(this);
        video.setUseController(false);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        video.setKeepScreenOn(true);
        video.setFocusable(false);
        root.addView(video, match());

        spinner = new ProgressBar(this);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));

        status = text(20, Color.WHITE, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(80), 0, dp(80), 0);
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        root.addView(overlay, match());

        topInfo = new LinearLayout(this);
        topInfo.setOrientation(LinearLayout.HORIZONTAL);
        topInfo.setGravity(Gravity.CENTER_VERTICAL);
        topInfo.setPadding(dp(56), dp(32), dp(56), dp(48));
        topInfo.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xD9000000, 0x00000000}));
        avatarView = new ImageView(this);
        topInfo.addView(avatarView, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout tcol = new LinearLayout(this);
        tcol.setOrientation(LinearLayout.VERTICAL);
        tcol.setPadding(dp(16), 0, 0, 0);
        titleView = text(24, Color.WHITE, true);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        channelView = text(16, 0xFFE0E0E0, false);
        tcol.addView(titleView);
        tcol.addView(channelView);
        topInfo.addView(tcol, new LinearLayout.LayoutParams(0, -2, 1f));
        overlay.addView(topInfo, new LinearLayout.LayoutParams(-1, -2));

        overlay.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(56), dp(60), dp(56), dp(24));
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF2000000, 0xB3000000, 0x00000000}));
        overlay.addView(bottom, new LinearLayout.LayoutParams(-1, -2));

        metaView = text(15, 0xFFBDBDBD, false);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        bottom.addView(metaView);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeView = text(16, Color.WHITE, true);
        chapterView = text(16, 0xFFE0E0E0, false);
        chapterView.setSingleLine(true);
        chapterView.setEllipsize(TextUtils.TruncateAt.END);
        chapterView.setPadding(dp(16), 0, 0, 0);
        timeRow.addView(timeView);
        timeRow.addView(chapterView, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams trl = new LinearLayout.LayoutParams(-1, -2);
        trl.topMargin = dp(6);
        bottom.addView(timeRow, trl);

        bar = new TimeBar(this);
        bar.setId(View.generateViewId());
        bottom.addView(bar, new LinearLayout.LayoutParams(-1, dp(28)));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(controls, new LinearLayout.LayoutParams(-1, dp(60)));

        button(R.drawable.ic_replay, "Restart", v -> { player.seekTo(0); player.play(); });
        btnPlay = button(R.drawable.ic_pause, "Pause", v -> togglePlay());
        button(R.drawable.ic_next, "Next video", v -> { Item n = findNext(); if (n != null) playNext(n); });
        gap(controls, 18);
        btnLike = button(R.drawable.ic_like_o, "Like", v -> rate(true));
        btnDislike = button(R.drawable.ic_dislike_o, "Dislike", v -> rate(false));
        btnSub = new TextView(this);
        btnSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnSub.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnSub.setPadding(dp(18), dp(8), dp(18), dp(8));
        btnSub.setFocusable(true);
        btnSub.setOnFocusChangeListener((v, has) -> { styleSub(); if (has) focusLabel.setText(""); });
        btnSub.setOnClickListener(v -> toggleSub());
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(-2, -2);
        sbl.setMargins(dp(6), 0, dp(6), 0);
        controls.addView(btnSub, sbl);
        button(R.drawable.ic_save, "Save to Watch later", v -> saveWatchLater());
        gap(controls, 18);
        btnCC = button(R.drawable.ic_cc, "Subtitles", v -> toggleCaptions());
        button(R.drawable.ic_comment, "Comments", v -> openComments());
        button(R.drawable.ic_info, "Description", v -> openDescription());
        button(R.drawable.ic_person, "Go to channel", v -> { if (!item.channelUrl.isEmpty()) Nav.channel(this, item.channelUrl, item.channel); });
        button(R.drawable.ic_settings, "Settings", v -> settingsMenu());
        focusLabel = text(14, 0xFFE0E0E0, false);
        focusLabel.setPadding(dp(18), 0, 0, 0);
        controls.addView(focusLabel);

        upNextHint = text(14, 0xFFBDBDBD, false);
        upNextHint.setText("▼  Up next");
        upNextHint.setPadding(0, dp(4), 0, 0);
        bottom.addView(upNextHint);

        upNextRow = new LinearLayout(this);
        upNextRow.setOrientation(LinearLayout.VERTICAL);
        upNextRow.setVisibility(View.GONE);
        TextView unTitle = text(18, Color.WHITE, true);
        unTitle.setText("Up next");
        unTitle.setPadding(0, dp(8), 0, dp(8));
        upNextRow.addView(unTitle);
        upNextScroll = new HorizontalScrollView(this);
        upNextScroll.setHorizontalScrollBarEnabled(false);
        upNextCards = new LinearLayout(this);
        upNextCards.setOrientation(LinearLayout.HORIZONTAL);
        upNextScroll.addView(upNextCards);
        upNextRow.addView(upNextScroll);
        bottom.addView(upNextRow);

        previewBox = new FrameLayout(this);
        previewBox.setVisibility(View.GONE);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xFF000000);
        pb.setStroke(dp(2), Color.WHITE);
        pb.setCornerRadius(dp(8));
        previewBox.setBackground(pb);
        previewBox.setPadding(dp(2), dp(2), dp(2), dp(2));
        previewImg = new ImageView(this);
        previewImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewBox.addView(previewImg, new FrameLayout.LayoutParams(dp(208), dp(117)));
        previewTime = text(15, Color.WHITE, true);
        previewTime.setBackgroundColor(0xB3000000);
        previewTime.setGravity(Gravity.CENTER);
        previewBox.addView(previewTime, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        root.addView(previewBox, new FrameLayout.LayoutParams(dp(212), dp(121)));

        panel = new FrameLayout(this);
        panel.setVisibility(View.GONE);
        panel.setBackgroundColor(0xF2181818);
        panelScroll = new ScrollView(this);
        panelScroll.setFillViewport(true);
        panelList = new LinearLayout(this);
        panelList.setOrientation(LinearLayout.VERTICAL);
        panelList.setPadding(dp(28), dp(28), dp(28), dp(28));
        panelScroll.addView(panelList);
        panel.addView(panelScroll, match());
        root.addView(panel, new FrameLayout.LayoutParams(dp(460), -1, Gravity.END));

        skipNote = pill();
        root.addView(skipNote, corner());
        upNextPill = pill();
        upNextPill.setMaxWidth(dp(520));
        root.addView(upNextPill, corner());

        setContentView(root);

        bar.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                scrubStep(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, ev.getRepeatCount());
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (bar.scrub >= 0) commitScrub(); else togglePlay();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) { btnPlay.requestFocus(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_UP) return true;
            return false;
        });
        for (int i = 0; i < controls.getChildCount(); i++) {
            View c = controls.getChildAt(i);
            if (!c.isFocusable()) continue;
            c.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_UP) { bar.requestFocus(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) { showUpNextRow(); return true; }
                return false;
            });
        }
    }

    private void createPlayer() {
        selector = new DefaultTrackSelector(this);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setTrackSelector(selector)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build();
        video.setPlayer(player);
        player.setPlaybackParameters(new PlaybackParameters(Store.speed()));
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int s) {
                spinner.setVisibility(s == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (s == Player.STATE_READY) status.setVisibility(View.GONE);
                if (s == Player.STATE_ENDED) ended();
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                btnPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
                btnPlay.setTag(playing ? "Pause" : "Play");
                if (playing) pokeOverlay();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException e) {
                if (!triedFallback && info != null && playProgressive(player.getCurrentPosition())) return;
                showError("Couldn't play this video (" + e.getErrorCodeName() + ").");
            }

            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                fixPortraitLimit(tracks);
            }
        });
    }

    // ================================================================== loading a video
    private void load(final Item it) {
        saveProgress();
        item = it;
        info = null;
        frames = null;
        pages.clear();
        pageLoading.clear();
        triedFallback = false;
        portraitChecked = false;
        originalLang = null;
        nextItem = null;
        likeStatus = "INDIFFERENT";
        skips.clear();
        skipped.clear();
        upNextPill.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        closePanel();
        hideUpNextRow();
        upNextCards.removeAllViews();
        bar.chapters = new long[0];
        bar.scrub = -1;
        player.stop();
        player.clearMediaItems();
        player.setRepeatMode(it.isShort ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        titleView.setText(it.title);
        channelView.setText(it.channel);
        metaView.setText(it.sub);
        avatarView.setImageDrawable(null);
        styleRating();
        styleSub();
        showOverlay(btnPlay);
        final int seq = ++loadSeq;

        Bg.run(() -> StreamInfo.getInfo(Feeds.yt(), it.url), (si, err) -> {
            if (seq != loadSeq || isFinishing()) return;
            if (si == null) { showError(Bg.message(err)); return; }
            info = si;
            fillFromInfo(si);
            start(si);
            loadSponsorBlock(it.videoId(), seq);
            if (Account.signedIn()) loadAccountState(it.videoId(), seq);
        });
    }

    private void fillFromInfo(StreamInfo si) {
        item.title = si.getName();
        if (si.getUploaderName() != null) item.channel = si.getUploaderName();
        if (si.getUploaderUrl() != null) item.channelUrl = si.getUploaderUrl();
        item.duration = si.getDuration();
        String thumb = Item.bestImage(si.getThumbnails(), 360);
        if (!thumb.isEmpty()) item.thumb = thumb;

        List<String> top = new ArrayList<>();
        top.add(item.channel);
        if (si.getUploaderSubscriberCount() > 0) top.add(Item.count(si.getUploaderSubscriberCount()) + " subscribers");
        channelView.setText(TextUtils.join("  •  ", top));
        titleView.setText(item.title);
        List<String> bits = new ArrayList<>();
        if (si.getViewCount() >= 0) bits.add(Item.count(si.getViewCount()) + " views");
        if (si.getTextualUploadDate() != null) bits.add(si.getTextualUploadDate());
        if (si.getLikeCount() >= 0) bits.add(Item.count(si.getLikeCount()) + " likes");
        metaView.setText(TextUtils.join("  •  ", bits));
        item.sub = item.channel + " • " + TextUtils.join(" • ", bits);
        String av = Item.bestImage(si.getUploaderAvatars(), 88);
        if (!av.isEmpty()) Glide.with(this).load(av).apply(RequestOptions.circleCropTransform()).into(avatarView);

        List<StreamSegment> segs = si.getStreamSegments();
        long[] ch = new long[segs == null ? 0 : segs.size()];
        for (int i = 0; i < ch.length; i++) ch[i] = segs.get(i).getStartTimeSeconds() * 1000L;
        bar.chapters = ch;

        frames = null;
        try {
            for (Frameset f : si.getPreviewFrames()) {
                if (frames == null || (f.getFrameWidth() > frames.getFrameWidth() && f.getFrameWidth() <= 320)) frames = f;
            }
        } catch (Exception ignored) { }

        buildUpNext();
        styleSub();
        Store.watched(item, 0, 0);
    }

    private void start(StreamInfo si) {
        for (AudioStream a : si.getAudioStreams()) {
            if (a.getAudioTrackType() == AudioTrackType.ORIGINAL && a.getAudioLocale() != null) {
                originalLang = a.getAudioLocale().getLanguage();
                break;
            }
        }
        applyTrackPrefs(Store.maxHeight());

        long resume = item.isShort ? 0 : Store.resumeAt(item.videoId());
        long startAt = resume > 10_000 ? resume : 0;
        String hls = si.getHlsUrl();
        if (hls != null && !hls.isEmpty()) {
            MediaItem mi = new MediaItem.Builder()
                    .setUri(hls)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setSubtitleConfigurations(subtitles(si))
                    .build();
            player.setMediaItem(mi, startAt);
            player.prepare();
            player.play();
        } else if (!playProgressive(startAt)) {
            showError("YouTube didn't give a playable stream for this video.");
        }
    }

    private List<MediaItem.SubtitleConfiguration> subtitles(StreamInfo si) {
        List<MediaItem.SubtitleConfiguration> out = new ArrayList<>();
        String want = Locale.getDefault().getLanguage();
        try {
            for (SubtitlesStream s : si.getSubtitles()) {
                if (!s.isUrl()) continue;
                String lang = s.getLanguageTag();
                boolean useful = !s.isAutoGenerated() || (lang != null && (lang.startsWith(want)
                        || (originalLang != null && lang.startsWith(originalLang))));
                if (!useful || out.size() >= 8) continue;
                String mime = s.getFormat() == MediaFormat.VTT ? MimeTypes.TEXT_VTT
                        : s.getFormat() == MediaFormat.SRT ? MimeTypes.APPLICATION_SUBRIP
                        : MimeTypes.APPLICATION_TTML;
                out.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.getContent()))
                        .setMimeType(mime)
                        .setLanguage(lang)
                        .setLabel(s.getDisplayLanguageName() + (s.isAutoGenerated() ? " (auto)" : ""))
                        .build());
            }
        } catch (Exception ignored) { }
        return out;
    }

    private boolean playProgressive(long startAt) {
        triedFallback = true;
        if (info == null) return false;
        int max = Store.maxHeight();
        VideoStream best = null;
        for (VideoStream v : info.getVideoOnlyStreams()) {
            if (!v.isUrl() || v.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            if (Math.min(v.getHeight(), v.getWidth() > 0 ? v.getWidth() : v.getHeight()) > max) continue;
            boolean avc = v.getCodec() != null && v.getCodec().startsWith("avc");
            if (best == null) { best = v; continue; }
            boolean bestAvc = best.getCodec() != null && best.getCodec().startsWith("avc");
            if (v.getHeight() > best.getHeight() || (v.getHeight() == best.getHeight() && avc && !bestAvc)) best = v;
        }
        AudioStream audio = null;
        for (AudioStream a : info.getAudioStreams()) {
            if (!a.isUrl() || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            boolean orig = a.getAudioTrackType() == null || a.getAudioTrackType() == AudioTrackType.ORIGINAL;
            if (audio == null) { audio = a; continue; }
            boolean audioOrig = audio.getAudioTrackType() == null || audio.getAudioTrackType() == AudioTrackType.ORIGINAL;
            if ((orig && !audioOrig) || (orig == audioOrig && a.getAverageBitrate() > audio.getAverageBitrate())) audio = a;
        }
        MediaSource src;
        ProgressiveMediaSource.Factory f = new ProgressiveMediaSource.Factory(http);
        if (best != null && audio != null) {
            src = new MergingMediaSource(
                    f.createMediaSource(MediaItem.fromUri(best.getContent())),
                    f.createMediaSource(MediaItem.fromUri(audio.getContent())));
        } else {
            VideoStream muxed = null;
            for (VideoStream v : info.getVideoStreams()) if (v.isUrl() && (muxed == null || v.getHeight() > muxed.getHeight())) muxed = v;
            if (muxed == null) return false;
            src = f.createMediaSource(MediaItem.fromUri(muxed.getContent()));
        }
        player.setMediaSource(src, startAt);
        player.prepare();
        player.play();
        return true;
    }

    private void applyTrackPrefs(int maxHeight) {
        TrackSelectionParameters.Builder b = selector.buildUponParameters()
                .setMaxVideoSize(maxHeight * 16 / 9 + 8, maxHeight + 8);
        if (originalLang != null) b.setPreferredAudioLanguage(originalLang);
        boolean cc = Store.captions();
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !cc);
        if (cc) b.setPreferredTextLanguage(Locale.getDefault().getLanguage()).setSelectUndeterminedTextLanguage(true);
        selector.setParameters(b.build());
        btnCC.setAlpha(cc ? 1f : 0.55f);
    }

    private void fixPortraitLimit(Tracks tracks) {
        if (portraitChecked) return;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO || g.length == 0) continue;
            portraitChecked = true;
            Format fmt = g.getTrackFormat(0);
            if (fmt.height > fmt.width && fmt.width > 0) {
                int mh = Store.maxHeight();
                selector.setParameters(selector.buildUponParameters().setMaxVideoSize(mh + 8, mh * 16 / 9 + 8).build());
            }
            return;
        }
    }

    // ================================================================== overlay
    private final Runnable hideOverlay = () -> {
        if (player != null && player.isPlaying() && panel.getVisibility() != View.VISIBLE) setOverlay(false);
    };

    private void showOverlay(@Nullable View focus) {
        setOverlay(true);
        if (focus != null) focus.requestFocus();
        else if (getCurrentFocus() == null || !isInOverlay(getCurrentFocus())) btnPlay.requestFocus();
        pokeOverlay();
    }

    private void pokeOverlay() {
        h.removeCallbacks(hideOverlay);
        h.postDelayed(hideOverlay, upNextRow.getVisibility() == View.VISIBLE ? 9000 : 5000);
    }

    private void setOverlay(boolean on) {
        overlay.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) {
            hideUpNextRow();
            previewBox.setVisibility(View.GONE);
            if (bar.scrub >= 0) commitScrub();
            root.requestFocus();
        }
    }

    private boolean overlayShown() { return overlay.getVisibility() == View.VISIBLE; }

    private boolean isInOverlay(View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) if (p == overlay) return true;
        return false;
    }

    private ImageView button(int icon, String label, View.OnClickListener click) {
        final ImageView b = new ImageView(this);
        b.setId(View.generateViewId());
        b.setImageResource(icon);
        b.setTag(label);
        b.setPadding(dp(11), dp(11), dp(11), dp(11));
        b.setFocusable(true);
        b.setColorFilter(Color.WHITE);
        b.setBackground(circle(0x33FFFFFF));
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(circle(has ? Color.WHITE : 0x33FFFFFF));
            b.setColorFilter(has ? 0xFF0F0F0F : Color.WHITE);
            if (has) focusLabel.setText(String.valueOf(b.getTag()));
        });
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lp.setMargins(dp(6), 0, dp(6), 0);
        controls.addView(b, lp);
        return b;
    }

    private void gap(LinearLayout l, int w) {
        l.addView(new View(this), new LinearLayout.LayoutParams(dp(w), 1));
    }

    private void togglePlay() {
        if (player.getPlaybackState() == Player.STATE_ENDED) { player.seekTo(0); player.play(); return; }
        if (player.isPlaying()) player.pause(); else player.play();
    }

    // ---- scrubbing with preview thumbnails
    private final Runnable commitScrubLater = this::commitScrub;

    private void scrubStep(int dir, int repeat) {
        long dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur <= 0) return;
        long base = bar.scrub >= 0 ? bar.scrub : player.getCurrentPosition();
        long step = Math.max(5_000, dur / 120);
        step = step * (1 + Math.min(repeat, 30) / 4);
        bar.scrub = Math.max(0, Math.min(dur - 1000, base + dir * step));
        bar.duration = dur;
        bar.invalidate();
        updateTime();
        showPreview(bar.scrub);
        h.removeCallbacks(commitScrubLater);
        h.postDelayed(commitScrubLater, 1100);
        pokeOverlay();
    }

    private void commitScrub() {
        h.removeCallbacks(commitScrubLater);
        if (bar.scrub >= 0) {
            player.seekTo(bar.scrub);
            bar.scrub = -1;
        }
        previewBox.setVisibility(View.GONE);
        bar.invalidate();
    }

    private void showPreview(long pos) {
        previewTime.setText(fmt(pos));
        int[] rl = new int[2], bl = new int[2];
        root.getLocationOnScreen(rl);
        bar.getLocationOnScreen(bl);
        float frac = bar.duration > 0 ? pos / (float) bar.duration : 0;
        float x = bl[0] - rl[0] + frac * bar.getWidth() - dp(106);
        x = Math.max(dp(16), Math.min(root.getWidth() - dp(228), x));
        previewBox.setX(x);
        previewBox.setY(bl[1] - rl[1] - dp(130));
        previewBox.setVisibility(View.VISIBLE);
        if (frames == null || frames.getUrls().isEmpty() || frames.getFramesPerPageX() <= 0) { previewImg.setImageBitmap(null); return; }
        int per = Math.max(1, frames.getFramesPerPageX() * frames.getFramesPerPageY());
        int n = (int) Math.min(frames.getTotalCount() - 1, pos / Math.max(1, frames.getDurationPerFrame()));
        n = Math.max(0, n);
        final int page = Math.min(frames.getUrls().size() - 1, n / per);
        int rel = n % per;
        final int cx = (rel % frames.getFramesPerPageX()) * frames.getFrameWidth();
        final int cy = (rel / frames.getFramesPerPageX()) * frames.getFrameHeight();
        Bitmap bm = pages.get(page);
        if (bm != null) {
            cropInto(bm, cx, cy);
        } else if (!Boolean.TRUE.equals(pageLoading.get(page))) {
            pageLoading.put(page, true);
            final Frameset fs = frames;
            Glide.with(this).asBitmap().load(frames.getUrls().get(page)).into(new CustomTarget<Bitmap>() {
                @Override public void onResourceReady(@NonNull Bitmap res, @Nullable Transition<? super Bitmap> t) {
                    if (fs != frames) return;
                    pages.put(page, res);
                    if (bar.scrub >= 0) showPreview(bar.scrub);
                }
                @Override public void onLoadCleared(@Nullable Drawable placeholder) { }
            });
        }
    }

    private void cropInto(Bitmap page, int x, int y) {
        int w = frames.getFrameWidth(), hh = frames.getFrameHeight();
        if (x + w > page.getWidth() || y + hh > page.getHeight()) return;
        previewImg.setImageBitmap(Bitmap.createBitmap(page, x, y, w, hh));
    }

    private void updateTime() {
        long dur = player.getDuration();
        if (dur == C.TIME_UNSET) dur = item.duration * 1000;
        bar.duration = Math.max(0, dur);
        bar.position = player.getCurrentPosition();
        bar.buffered = player.getBufferedPosition();
        long shown = bar.shown();
        timeView.setText(item.live ? "LIVE" : fmt(shown) + " / " + fmt(dur));
        String chap = "";
        if (info != null && info.getStreamSegments() != null) {
            for (StreamSegment s : info.getStreamSegments()) if (s.getStartTimeSeconds() * 1000L <= shown) chap = s.getTitle();
        }
        chapterView.setText(chap.isEmpty() ? "" : "•  " + chap);
        bar.invalidate();
    }

    // ---- up next row
    private void buildUpNext() {
        upNextCards.removeAllViews();
        List<Item> list = new ArrayList<>(Nav.queue);
        if (info != null) {
            for (InfoItem ii : info.getRelatedItems()) {
                Item it = Item.from(ii);
                if (Item.VIDEO.equals(it.type) && list.size() < 25 && !it.url.equals(item.url)) list.add(it);
            }
        }
        for (final Item it : list) {
            YtCard card = new YtCard(this);
            card.bind(it);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(card.getLayoutParams().width, -2);
            lp.rightMargin = dp(16);
            card.setOnClickListener(v -> {
                if (!Nav.queue.contains(it)) Nav.queue.clear();
                else while (!Nav.queue.isEmpty() && Nav.queue.get(0) != it) Nav.queue.remove(0);
                playNext(it);
            });
            card.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_UP) {
                    hideUpNextRow();
                    btnPlay.requestFocus();
                    return true;
                }
                if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
                return false;
            });
            upNextCards.addView(card, lp);
        }
        upNextHint.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showUpNextRow() {
        if (upNextCards.getChildCount() == 0) return;
        upNextRow.setVisibility(View.VISIBLE);
        upNextHint.setVisibility(View.GONE);
        topInfo.setVisibility(View.INVISIBLE);
        upNextScroll.scrollTo(0, 0);
        upNextCards.getChildAt(0).requestFocus();
        pokeOverlay();
    }

    private void hideUpNextRow() {
        if (upNextRow == null) return;
        upNextRow.setVisibility(View.GONE);
        topInfo.setVisibility(View.VISIBLE);
        if (upNextCards.getChildCount() > 0) upNextHint.setVisibility(View.VISIBLE);
    }

    // ================================================================== side panels
    private void openPanel(String title) {
        panelList.removeAllViews();
        TextView t = text(22, Color.WHITE, true);
        t.setText(title);
        t.setPadding(0, 0, 0, dp(16));
        panelList.addView(t);
        panel.setVisibility(View.VISIBLE);
        panelScroll.scrollTo(0, 0);
        setOverlay(false);
        h.removeCallbacks(hideOverlay);
    }

    private void closePanel() {
        if (panel == null) return;
        panel.setVisibility(View.GONE);
        panelList.removeAllViews();
        commentsUrl = null;
    }

    private TextView panelText(CharSequence s, int sp, int color, boolean focusable) {
        final TextView t = text(sp, color, false);
        t.setText(s);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        if (focusable) {
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> t.setBackground(has ? rounded(0x33FFFFFF) : null));
        }
        panelList.addView(t);
        return t;
    }

    private void openDescription() {
        if (info == null) return;
        openPanel("Description");
        panelText(item.title, 18, Color.WHITE, false).setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panelText(metaView.getText(), 15, 0xFFBDBDBD, false);
        List<StreamSegment> segs = info.getStreamSegments();
        View first = null;
        if (segs != null && !segs.isEmpty()) {
            panelText("Chapters", 17, Color.WHITE, false);
            for (final StreamSegment s : segs) {
                TextView t = panelText(fmt(s.getStartTimeSeconds() * 1000L) + "   " + s.getTitle(), 15, 0xFF3EA6FF, true);
                t.setOnClickListener(v -> { player.seekTo(s.getStartTimeSeconds() * 1000L); player.play(); closePanel(); });
                if (first == null) first = t;
            }
        }
        String desc = info.getDescription() == null ? "" : info.getDescription().content();
        desc = desc.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        for (String para : desc.split("\n\n")) {
            if (para.trim().isEmpty()) continue;
            TextView t = panelText(para.trim(), 15, 0xFFE0E0E0, true);
            if (first == null) first = t;
        }
        if (first != null) first.requestFocus();
        else panel.requestFocus();
    }

    private void openComments() {
        if (info == null) return;
        openPanel("Comments");
        final TextView loading = panelText("Loading comments…", 15, 0xFFBDBDBD, false);
        commentsUrl = item.url;
        final String url = item.url;
        commentsLoading = true;
        Bg.run(() -> CommentsInfo.getInfo(Feeds.yt(), url), (ci, err) -> {
            commentsLoading = false;
            if (!url.equals(commentsUrl)) return;
            panelList.removeView(loading);
            if (ci == null) { panelText("Comments couldn't be loaded: " + Bg.message(err), 15, 0xFFFF8A80, false); return; }
            if (ci.getCommentsCount() > 0) ((TextView) panelList.getChildAt(0)).setText(Item.count(ci.getCommentsCount()) + " Comments");
            commentsNext = ci.getNextPage();
            addComments(ci.getRelatedItems(), true);
            if (ci.getRelatedItems().isEmpty()) panelText("Comments are turned off or there aren't any yet.", 15, 0xFFBDBDBD, false);
        });
    }

    private void addComments(List<CommentsInfoItem> list, boolean focusFirst) {
        View first = null;
        for (CommentsInfoItem c : list) {
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(10), dp(10), dp(10), dp(10));
            row.setFocusable(true);
            row.setOnFocusChangeListener((v, has) -> {
                row.setBackground(has ? rounded(0x33FFFFFF) : null);
                if (has && panelList.indexOfChild(row) >= panelList.getChildCount() - 3) moreComments();
            });
            ImageView av = new ImageView(this);
            String a = Item.bestImage(c.getUploaderAvatars(), 48);
            if (!a.isEmpty()) Glide.with(this).load(a).apply(RequestOptions.circleCropTransform()).into(av);
            row.addView(av, new LinearLayout.LayoutParams(dp(32), dp(32)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, 0, 0);
            TextView who = text(13, 0xFFBDBDBD, false);
            String when = c.getTextualUploadDate() == null ? "" : "  •  " + c.getTextualUploadDate();
            who.setText((c.isPinned() ? "📌 " : "") + c.getUploaderName() + when);
            TextView body = text(15, Color.WHITE, false);
            String txt = c.getCommentText() == null ? "" : c.getCommentText().content();
            body.setText(txt.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'"));
            body.setLineSpacing(0, 1.12f);
            TextView likes = text(13, 0xFFBDBDBD, false);
            List<String> bits = new ArrayList<>();
            if (c.getLikeCount() > 0) bits.add("👍 " + Item.count(c.getLikeCount()));
            if (c.isHeartedByUploader()) bits.add("❤ by creator");
            if (c.getReplyCount() > 0) bits.add(c.getReplyCount() + " replies");
            likes.setText(TextUtils.join("    ", bits));
            col.addView(who);
            col.addView(body);
            if (!bits.isEmpty()) col.addView(likes);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            panelList.addView(row);
            if (first == null) first = row;
        }
        if (focusFirst && first != null) first.requestFocus();
    }

    private void moreComments() {
        if (commentsLoading || commentsNext == null || commentsUrl == null) return;
        commentsLoading = true;
        final String url = commentsUrl;
        final Page page = commentsNext;
        Bg.run(() -> CommentsInfo.getMoreItems(Feeds.yt(), url, page), (pg, err) -> {
            commentsLoading = false;
            if (!url.equals(commentsUrl) || pg == null) return;
            commentsNext = pg.getNextPage();
            addComments(castComments(pg), false);
        });
    }

    @SuppressWarnings("unchecked")
    private static List<CommentsInfoItem> castComments(ListExtractor.InfoItemsPage<?> pg) {
        return (List<CommentsInfoItem>) pg.getItems();
    }

    // ================================================================== account actions
    private void loadAccountState(final String id, final int seq) {
        Bg.run(() -> { Yti.markWatched(id); return Yti.videoState(id); }, (st, e) -> {
            if (seq != loadSeq || st == null) return;
            likeStatus = st[0];
            styleRating();
            if ("true".equals(st[1]) && !item.channelUrl.isEmpty() && !Store.isSubscribed(item.channelUrl))
                Store.subscribe(item.channelUrl, item.channel, info != null ? Item.bestImage(info.getUploaderAvatars(), 176) : "");
            if ("false".equals(st[1]) && Store.isSubscribed(item.channelUrl)) Store.unsubscribe(item.channelUrl);
            styleSub();
        });
    }

    private boolean needAccount() {
        if (Account.signedIn()) return false;
        Toast.makeText(this, "Sign in to YouTube first (Settings on the home screen)", Toast.LENGTH_LONG).show();
        return true;
    }

    private void rate(boolean like) {
        if (needAccount()) return;
        final String kind;
        final String next;
        if (like) { kind = "LIKE".equals(likeStatus) ? "removelike" : "like"; next = "LIKE".equals(likeStatus) ? "INDIFFERENT" : "LIKE"; }
        else { kind = "DISLIKE".equals(likeStatus) ? "removelike" : "dislike"; next = "DISLIKE".equals(likeStatus) ? "INDIFFERENT" : "DISLIKE"; }
        final String before = likeStatus;
        likeStatus = next;
        styleRating();
        final String id = item.videoId();
        Bg.run(() -> { Yti.like(id, kind); return true; }, (r, e) -> {
            if (r == null) {
                likeStatus = before;
                styleRating();
                Toast.makeText(this, "YouTube didn't save that: " + Bg.message(e), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "LIKE".equals(next) ? "Added to Liked videos" : "DISLIKE".equals(next) ? "You disliked this video" : "Rating removed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void styleRating() {
        if (btnLike == null) return;
        boolean l = "LIKE".equals(likeStatus), dl = "DISLIKE".equals(likeStatus);
        btnLike.setImageResource(l ? R.drawable.ic_like : R.drawable.ic_like_o);
        btnLike.setTag(l ? "Liked" : "Like");
        btnDislike.setImageResource(dl ? R.drawable.ic_dislike : R.drawable.ic_dislike_o);
        btnDislike.setTag(dl ? "Disliked" : "Dislike");
    }

    private void toggleSub() {
        if (item.channelUrl.isEmpty()) return;
        final boolean on = !Store.isSubscribed(item.channelUrl);
        if (on) Store.subscribe(item.channelUrl, item.channel, info != null ? Item.bestImage(info.getUploaderAvatars(), 176) : "");
        else Store.unsubscribe(item.channelUrl);
        styleSub();
        if (Account.signedIn()) {
            final String cid = Store.channelId(item.channelUrl);
            Bg.run(() -> { Yti.subscribe(cid, on); return true; }, (r, e) -> Toast.makeText(this,
                    r == null ? "YouTube didn't save that: " + Bg.message(e) : on ? "Subscribed" : "Unsubscribed", Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(this, on ? "Subscribed (on this TV)" : "Unsubscribed", Toast.LENGTH_SHORT).show();
        }
    }

    private void styleSub() {
        if (btnSub == null || item == null) return;
        boolean subbed = Store.isSubscribed(item.channelUrl);
        boolean f = btnSub.isFocused();
        btnSub.setText(subbed ? "Subscribed" : "Subscribe");
        btnSub.setBackground(roundedStroke(f ? Color.WHITE : subbed ? 0x33FFFFFF : 0xFFF1F1F1));
        btnSub.setTextColor(f || !subbed ? 0xFF0F0F0F : Color.WHITE);
        btnSub.setVisibility(item.channelUrl.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void saveWatchLater() {
        if (needAccount()) return;
        final String id = item.videoId();
        Bg.run(() -> { Yti.watchLater(id, true); return true; }, (r, e) -> Toast.makeText(this,
                r == null ? "YouTube didn't save that: " + Bg.message(e) : "Saved to Watch later", Toast.LENGTH_SHORT).show());
    }

    private void toggleCaptions() {
        Store.setCaptions(!Store.captions());
        applyTrackPrefs(Store.maxHeight());
        Toast.makeText(this, Store.captions() ? "Subtitles on" : "Subtitles off", Toast.LENGTH_SHORT).show();
    }

    // ================================================================== settings (gear / ≡)
    private void settingsMenu() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        labels.add("Quality: " + (Store.maxHeight() >= 2160 ? "4K" : Store.maxHeight() + "p") + " max");
        actions.add(this::qualityMenu);
        labels.add("Playback speed: " + speedName(Store.speed()));
        actions.add(this::speedMenu);
        labels.add("Audio language");
        actions.add(this::audioMenu);
        labels.add("Subtitles: " + (Store.captions() ? "On" : "Off"));
        actions.add(this::toggleCaptions);
        labels.add("Autoplay: " + (Store.autoplay() ? "On" : "Off"));
        actions.add(() -> Store.setAutoplay(!Store.autoplay()));
        labels.add("Skip sponsors: " + (Store.sponsorBlock() ? "On" : "Off"));
        actions.add(() -> Store.setSponsorBlock(!Store.sponsorBlock()));
        labels.add("Loop this video: " + (player.getRepeatMode() == Player.REPEAT_MODE_ONE ? "On" : "Off"));
        actions.add(() -> player.setRepeatMode(player.getRepeatMode() == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE));
        new AlertDialog.Builder(this).setTitle("Settings")
                .setItems(labels.toArray(new String[0]), (dd, w) -> actions.get(w).run())
                .show();
    }

    private void qualityMenu() {
        final int[] hs = {2160, 1440, 1080, 720, 480, 360};
        String[] names = {"4K (2160p)", "1440p", "1080p", "720p", "480p", "360p"};
        int cur = 2;
        for (int i = 0; i < hs.length; i++) if (hs[i] == Store.maxHeight()) cur = i;
        new AlertDialog.Builder(this).setTitle("Quality")
                .setSingleChoiceItems(names, cur, (dd, w) -> {
                    Store.setMaxHeight(hs[w]);
                    portraitChecked = false;
                    applyTrackPrefs(hs[w]);
                    fixPortraitLimit(player.getCurrentTracks());
                    dd.dismiss();
                }).show();
    }

    private void speedMenu() {
        final float[] ss = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] names = new String[ss.length];
        int cur = 3;
        for (int i = 0; i < ss.length; i++) { names[i] = speedName(ss[i]); if (ss[i] == Store.speed()) cur = i; }
        new AlertDialog.Builder(this).setTitle("Playback speed")
                .setSingleChoiceItems(names, cur, (dd, w) -> {
                    Store.setSpeed(ss[w]);
                    player.setPlaybackParameters(new PlaybackParameters(ss[w]));
                    dd.dismiss();
                }).show();
    }

    private void audioMenu() {
        final List<String> langs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                String l = f.language == null ? "und" : f.language;
                if (langs.contains(l)) continue;
                langs.add(l);
                String n = f.label != null ? f.label : new Locale(l).getDisplayLanguage();
                if (originalLang != null && l.startsWith(originalLang)) n += " (original)";
                names.add(n);
            }
        }
        if (langs.size() < 2) { Toast.makeText(this, "This video has one audio language", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Audio language")
                .setItems(names.toArray(new String[0]), (dd, w) -> selector.setParameters(
                        selector.buildUponParameters().setPreferredAudioLanguage(langs.get(w)).build()))
                .show();
    }

    private static String speedName(float s) {
        return s == 1f ? "Normal" : (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "x";
    }

    // ================================================================== SponsorBlock + progress
    private void loadSponsorBlock(final String id, final int seq) {
        if (!Store.sponsorBlock()) return;
        Bg.run(() -> {
            String cats = URLEncoder.encode("[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"music_offtopic\"]", "UTF-8");
            Request r = new Request.Builder().url("https://sponsor.ajay.app/api/skipSegments?videoID=" + id + "&categories=" + cats).build();
            try (Response res = NpDownloader.get().client.newCall(r).execute()) {
                if (!res.isSuccessful() || res.body() == null) return null;
                return new JSONArray(res.body().string());
            }
        }, (arr, err) -> {
            if (seq != loadSeq || arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null || !"skip".equals(o.optString("actionType", "skip"))) continue;
                JSONArray seg = o.optJSONArray("segment");
                if (seg == null) continue;
                String cat = o.optString("category");
                long kind = cat.equals("sponsor") ? 0 : cat.equals("selfpromo") ? 1 : cat.equals("interaction") ? 2
                        : cat.equals("intro") ? 3 : cat.equals("outro") ? 4 : 5;
                skips.add(new long[]{(long) (seg.optDouble(0) * 1000), (long) (seg.optDouble(1) * 1000), kind});
                skipped.add(false);
            }
        });
    }

    private static final String[] SKIP_NAMES = {"sponsor", "self-promotion", "subscribe reminder", "intro", "outro", "non-music part"};

    private void tick() {
        h.postDelayed(this::tick, 250);
        if (player == null) return;
        if (overlayShown()) updateTime();
        long pos = player.getCurrentPosition();
        if (player.isPlaying()) {
            for (int i = 0; i < skips.size(); i++) {
                long[] s = skips.get(i);
                if (!skipped.get(i) && pos >= s[0] && pos < s[1] - 500) {
                    skipped.set(i, true);
                    player.seekTo(s[1]);
                    skipNote.setText("Skipped " + SKIP_NAMES[(int) s[2]]);
                    skipNote.setVisibility(View.VISIBLE);
                    h.postDelayed(() -> skipNote.setVisibility(View.GONE), 2500);
                }
            }
            if (System.currentTimeMillis() - lastSave > 10_000) {
                lastSave = System.currentTimeMillis();
                saveProgress();
            }
        }
    }

    private void saveProgress() {
        if (player == null || info == null || item == null || item.isShort) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        if (dur == C.TIME_UNSET) dur = item.duration * 1000;
        if (pos > 5000) Store.watched(item, pos, dur);
    }

    // ================================================================== what plays next
    private Item findNext() {
        if (!Nav.queue.isEmpty()) return Nav.queue.get(0);
        if (info == null) return null;
        for (InfoItem ii : info.getRelatedItems()) {
            Item it = Item.from(ii);
            if (Item.VIDEO.equals(it.type) && !it.live && !it.url.equals(item.url) && (it.isShort == item.isShort)) return it;
        }
        for (InfoItem ii : info.getRelatedItems()) {
            Item it = Item.from(ii);
            if (Item.VIDEO.equals(it.type) && !it.live && !it.url.equals(item.url)) return it;
        }
        return null;
    }

    private void playNext(Item it) {
        if (!Nav.queue.isEmpty() && Nav.queue.get(0) == it) Nav.queue.remove(0);
        countdown = 0;
        load(it);
    }

    private void ended() {
        saveProgress();
        if (!Store.autoplay() && Nav.queue.isEmpty()) { showOverlay(btnPlay); return; }
        nextItem = findNext();
        if (nextItem == null) { showOverlay(btnPlay); return; }
        countdown = 6;
        countdownStep();
    }

    private void countdownStep() {
        if (countdown <= 0 || nextItem == null) { upNextPill.setVisibility(View.GONE); return; }
        countdown--;
        upNextPill.setText("Up next in " + (countdown + 1) + "s:  " + nextItem.title + "\nSelect: play now     Back: cancel");
        upNextPill.setVisibility(View.VISIBLE);
        if (countdown == 0) { playNext(nextItem); return; }
        h.postDelayed(this::countdownStep, 1000);
    }

    // Shorts: up/down moves between them like the YouTube app
    private void nextShort(int dir) {
        if (dir > 0) {
            Item n = findNext();
            if (n == null) return;
            prevShorts.add(item);
            n.isShort = true;
            playNext(n);
        } else if (!prevShorts.isEmpty()) {
            Item p = prevShorts.remove(prevShorts.size() - 1);
            Nav.queue.add(0, item);
            load(p);
        }
    }

    // ================================================================== remote
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (upNextPill.getVisibility() == View.VISIBLE && nextItem != null) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { if (down) playNext(nextItem); return true; }
            if (k == KeyEvent.KEYCODE_BACK) { if (!down) { countdown = 0; nextItem = null; upNextPill.setVisibility(View.GONE); showOverlay(btnPlay); } return true; }
        }

        switch (k) {
            case KeyEvent.KEYCODE_MENU:
                if (down && e.getRepeatCount() == 0) settingsMenu();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && e.getRepeatCount() == 0) { togglePlay(); showOverlay(null); }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) { showOverlay(bar); scrubStep(k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? 1 : -1, e.getRepeatCount()); }
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (down) { Item n = findNext(); if (n != null) playNext(n); }
                return true;
            default:
                break;
        }

        if (status.getVisibility() == View.VISIBLE && player.getPlaybackState() == Player.STATE_IDLE
                && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
            if (down) load(item);
            return true;
        }

        if (panel.getVisibility() == View.VISIBLE) {
            if (k == KeyEvent.KEYCODE_BACK) { if (!down) { closePanel(); showOverlay(btnPlay); } return true; }
            return super.dispatchKeyEvent(e);
        }

        if (!overlayShown()) {
            if (k == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
            if (!down) return true;
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    togglePlay();
                    showOverlay(btnPlay);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    showOverlay(bar);
                    scrubStep(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1, e.getRepeatCount());
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (item.isShort) { if (e.getRepeatCount() == 0) nextShort(k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1); return true; }
                    showOverlay(btnPlay);
                    return true;
                default:
                    return super.dispatchKeyEvent(e);
            }
        }

        pokeOverlay();
        if (k == KeyEvent.KEYCODE_BACK) {
            if (!down) {
                if (upNextRow.getVisibility() == View.VISIBLE) { hideUpNextRow(); btnPlay.requestFocus(); }
                else if (bar.scrub >= 0) { bar.scrub = -1; previewBox.setVisibility(View.GONE); bar.invalidate(); }
                else setOverlay(false);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ================================================================== helpers
    private void showError(String msg) {
        spinner.setVisibility(View.GONE);
        status.setText(msg + "\n\nSelect: try again     Back: go back");
        status.setVisibility(View.VISIBLE);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long hh = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(3, 0, 1, 0x99000000);
        return t;
    }

    private TextView pill() {
        TextView t = text(17, Color.WHITE, false);
        t.setBackground(rounded(0xE6202020));
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setVisibility(View.GONE);
        return t;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        return g;
    }

    private GradientDrawable roundedStroke(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(20));
        return g;
    }

    private FrameLayout.LayoutParams corner() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(dp(48), dp(40), dp(48), dp(48));
        return lp;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static JSONObject parse(String s) {
        try { return new JSONObject(s == null ? "{}" : s); } catch (Exception e) { return new JSONObject(); }
    }

    private int dp(int v) { return Math.round(v * d); }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (player != null) player.release();
        player = null;
        super.onDestroy();
    }
}
