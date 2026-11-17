package com.lttube.tv;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

/** Plays one video at a time, then the next one. */
public class PlayerActivity extends FragmentActivity {
    public static final String ITEM = "item";

    private ExoPlayer player;
    private DefaultTrackSelector selector;
    private PlayerView view;
    private TextView titleView, metaView, status, skipNote, upNext;
    private ProgressBar spinner;
    private LinearLayout top;

    private Item item;
    private StreamInfo info;
    private OkHttpDataSource.Factory http;
    private boolean triedFallback;
    private int loadSeq;
    private String originalLang;
    private boolean portraitChecked;
    private final List<long[]> skips = new ArrayList<>();
    private final List<Boolean> skipped = new ArrayList<>();
    private Item nextItem;
    private int countdown;
    private long lastSave;
    private String likeStatus = "INDIFFERENT";
    private final Handler h = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------ setup
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        view = new PlayerView(this);
        view.setControllerShowTimeoutMs(4000);
        view.setShowSubtitleButton(true);
        view.setShowNextButton(true);
        view.setShowPreviousButton(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setKeepScreenOn(true);
        root.addView(view, match());

        top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(48), dp(28), dp(48), dp(40));
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC000000, 0x00000000});
        top.setBackground(shade);
        titleView = text(26, Color.WHITE, true);
        titleView.setMaxLines(2);
        metaView = text(17, 0xFFDDDDDD, false);
        top.addView(titleView);
        top.addView(metaView);
        TextView hint = text(14, 0xFF9E9E9E, false);
        hint.setText("◀ ▶ skip 10s   •   ≡ menu: quality, audio, subtitles, channel, up next");
        hint.setPadding(0, dp(6), 0, 0);
        top.addView(hint);
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        spinner = new ProgressBar(this);
        root.addView(spinner, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));

        status = text(20, Color.WHITE, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(80), dp(120), dp(80), 0);
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        skipNote = pill();
        root.addView(skipNote, corner(Gravity.BOTTOM | Gravity.END));

        upNext = pill();
        upNext.setMaxWidth(dp(520));
        root.addView(upNext, corner(Gravity.BOTTOM | Gravity.END));

        setContentView(root);

        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility ->
                top.setVisibility(visibility == View.VISIBLE ? View.VISIBLE : View.GONE));

        String ua = YoutubeParsingHelper.getVisionOsUserAgent(new Localization("en", "US"));
        http = new OkHttpDataSource.Factory(NpDownloader.get().client).setUserAgent(ua);

        item = Item.fromJson(parse(getIntent().getStringExtra(ITEM)));
        createPlayer();
        load(item);
        tick();
    }

    private void createPlayer() {
        selector = new DefaultTrackSelector(this);
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setTrackSelector(selector)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build();
        view.setPlayer(player);
        player.setPlaybackParameters(new PlaybackParameters(Store.speed()));
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int s) {
                spinner.setVisibility(s == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (s == Player.STATE_READY) status.setVisibility(View.GONE);
                if (s == Player.STATE_ENDED) ended();
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                if (!triedFallback && info != null && playProgressive(player.getCurrentPosition())) return;
                showError("Couldn't play this video (" + e.getErrorCodeName() + ").");
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                fixPortraitLimit(tracks);
            }
        });
    }

    // ------------------------------------------------------------------ loading a video
    private void load(final Item it) {
        saveProgress();
        item = it;
        info = null;
        triedFallback = false;
        portraitChecked = false;
        originalLang = null;
        nextItem = null;
        skips.clear();
        skipped.clear();
        upNext.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
        player.stop();
        player.clearMediaItems();
        titleView.setText(it.title);
        metaView.setText(it.channel);
        top.setVisibility(View.VISIBLE);
        final int seq = ++loadSeq;

        Bg.run(() -> StreamInfo.getInfo(Feeds.yt(), it.url), (si, err) -> {
            if (seq != loadSeq || isFinishing()) return;
            if (si == null) { showError(Bg.message(err)); return; }
            info = si;
            fillItemFromInfo(si);
            start(si);
            loadSponsorBlock(it.videoId(), seq);
            if (Account.signedIn()) loadAccountState(it.videoId(), seq);
        });
    }

    private void fillItemFromInfo(StreamInfo si) {
        item.title = si.getName();
        item.channel = si.getUploaderName() == null ? item.channel : si.getUploaderName();
        item.channelUrl = si.getUploaderUrl() == null ? item.channelUrl : si.getUploaderUrl();
        item.duration = si.getDuration();
        String thumb = Item.bestImage(si.getThumbnails(), 360);
        if (!thumb.isEmpty()) item.thumb = thumb;
        List<String> bits = new ArrayList<>();
        if (!item.channel.isEmpty()) bits.add(item.channel);
        if (si.getViewCount() >= 0) bits.add(Item.count(si.getViewCount()) + " views");
        if (si.getTextualUploadDate() != null) bits.add(si.getTextualUploadDate());
        if (si.getLikeCount() >= 0) bits.add("👍 " + Item.count(si.getLikeCount()));
        StringBuilder b = new StringBuilder();
        for (String s : bits) { if (b.length() > 0) b.append("  •  "); b.append(s); }
        titleView.setText(item.title);
        metaView.setText(b.toString());
        item.sub = b.toString();
        Store.watched(item, 0, 0); // shows up in history right away
    }

    private void start(StreamInfo si) {
        // Original (not auto-dubbed) audio language
        for (AudioStream a : si.getAudioStreams()) {
            if (a.getAudioTrackType() == AudioTrackType.ORIGINAL && a.getAudioLocale() != null) {
                originalLang = a.getAudioLocale().getLanguage();
                break;
            }
        }
        applyTrackPrefs(Store.maxHeight());

        long resume = Store.resumeAt(item.videoId());
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

    /** Backup plan: separate video + audio files instead of the HLS stream. */
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
            for (VideoStream v : info.getVideoStreams()) {
                if (v.isUrl() && (muxed == null || v.getHeight() > muxed.getHeight())) muxed = v;
            }
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
    }

    /** Vertical videos (Shorts) are taller than wide: swap the size limit so they still play sharp. */
    private void fixPortraitLimit(Tracks tracks) {
        if (portraitChecked) return;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_VIDEO || g.length == 0) continue;
            portraitChecked = true;
            Format fmt = g.getTrackFormat(0);
            if (fmt.height > fmt.width && fmt.width > 0) {
                int h = Store.maxHeight();
                selector.setParameters(selector.buildUponParameters()
                        .setMaxVideoSize(h + 8, h * 16 / 9 + 8).build());
            }
            return;
        }
    }

    // ------------------------------------------------------------------ your YouTube account
    private void loadAccountState(final String id, final int seq) {
        likeStatus = "INDIFFERENT";
        Bg.run(() -> { Yti.markWatched(id); return Yti.videoState(id); }, (st, e) -> {
            if (seq != loadSeq || st == null) return;
            likeStatus = st[0];
            if ("true".equals(st[1]) && !item.channelUrl.isEmpty() && !Store.isSubscribed(item.channelUrl))
                Store.subscribe(item.channelUrl, item.channel, info != null ? Item.bestImage(info.getUploaderAvatars(), 176) : "");
            if ("false".equals(st[1]) && Store.isSubscribed(item.channelUrl)) Store.unsubscribe(item.channelUrl);
        });
    }

    private void rate(final String kind, final String msg) {
        if (!Account.signedIn()) { Toast.makeText(this, "Sign in to YouTube first (Settings row on the home screen)", Toast.LENGTH_LONG).show(); return; }
        final String id = item.videoId();
        Bg.run(() -> { Yti.like(id, kind); return true; }, (r, e) -> {
            if (r == null) { Toast.makeText(this, "YouTube didn't save that: " + Bg.message(e), Toast.LENGTH_LONG).show(); return; }
            likeStatus = "like".equals(kind) ? "LIKE" : "dislike".equals(kind) ? "DISLIKE" : "INDIFFERENT";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    // ------------------------------------------------------------------ SponsorBlock
    private void loadSponsorBlock(final String id, final int seq) {
        if (!Store.sponsorBlock()) return;
        Bg.run(() -> {
            String cats = URLEncoder.encode("[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"music_offtopic\"]", "UTF-8");
            Request r = new Request.Builder()
                    .url("https://sponsor.ajay.app/api/skipSegments?videoID=" + id + "&categories=" + cats)
                    .build();
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

    /** Runs twice a second: skip sponsors, save the resume point, count down to the next video. */
    private void tick() {
        h.postDelayed(this::tick, 500);
        if (player == null) return;
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
        if (player == null || info == null || item == null) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        if (dur == C.TIME_UNSET) dur = item.duration * 1000;
        if (pos > 5000) Store.watched(item, pos, dur);
    }

    // ------------------------------------------------------------------ what plays next
    private Item findNext() {
        if (!Nav.queue.isEmpty()) return Nav.queue.get(0);
        if (!Store.autoplay() || info == null) return null;
        for (InfoItem ii : info.getRelatedItems()) {
            Item it = Item.from(ii);
            if (Item.VIDEO.equals(it.type) && !it.live && !it.isShort && !it.url.equals(item.url)) return it;
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
        nextItem = findNext();
        if (nextItem == null) return;
        countdown = 6;
        countdownStep();
    }

    private void countdownStep() {
        if (countdown <= 0 || nextItem == null) { upNext.setVisibility(View.GONE); return; }
        countdown--;
        upNext.setText("Up next in " + (countdown + 1) + "s:  " + nextItem.title + "\n(Select: play now   Back: cancel)");
        upNext.setVisibility(View.VISIBLE);
        if (countdown == 0) { playNext(nextItem); return; }
        h.postDelayed(this::countdownStep, 1000);
    }

    // ------------------------------------------------------------------ remote buttons
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (upNext.getVisibility() == View.VISIBLE && nextItem != null) {
            if (!down) return true;
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { playNext(nextItem); return true; }
            if (k == KeyEvent.KEYCODE_BACK) { countdown = 0; nextItem = null; upNext.setVisibility(View.GONE); return true; }
        }

        if (k == KeyEvent.KEYCODE_MENU) {
            if (down && e.getRepeatCount() == 0) menu();
            return true;
        }
        if (status.getVisibility() == View.VISIBLE && player.getPlaybackState() == Player.STATE_IDLE
                && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER)) {
            if (down) load(item);
            return true;
        }
        if (k == KeyEvent.KEYCODE_MEDIA_NEXT) {
            if (down) { Item n = findNext(); if (n != null) playNext(n); }
            return true;
        }

        boolean controls = view.isControllerFullyVisible();
        if (!controls && down) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    player.seekBack();
                    flash();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    player.seekForward();
                    flash();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (player.isPlaying()) player.pause(); else player.play();
                    view.showController();
                    return true;
                default:
                    break;
            }
        }
        if (k == KeyEvent.KEYCODE_BACK && controls) {
            if (!down) view.hideController();
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
        return view.dispatchKeyEvent(e) || super.dispatchKeyEvent(e);
    }

    private void flash() {
        top.setVisibility(View.VISIBLE);
        view.showController();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
    }

    // ------------------------------------------------------------------ menu (≡ button)
    private void menu() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final boolean subbed = Store.isSubscribed(item.channelUrl);

        if (Account.signedIn()) {
            boolean liked = "LIKE".equals(likeStatus), disliked = "DISLIKE".equals(likeStatus);
            labels.add(liked ? "👍 Liked (select to remove)" : "👍 Like");
            actions.add(() -> rate(liked ? "removelike" : "like", liked ? "Removed from Liked videos" : "Added to Liked videos"));
            labels.add(disliked ? "👎 Disliked (select to remove)" : "👎 Dislike");
            actions.add(() -> rate(disliked ? "removelike" : "dislike", disliked ? "Dislike removed" : "You disliked this"));
            labels.add("Save to Watch later");
            actions.add(() -> {
                final String id = item.videoId();
                Bg.run(() -> { Yti.watchLater(id, true); return true; }, (r, e) -> Toast.makeText(this,
                        r == null ? "YouTube didn't save that: " + Bg.message(e) : "Saved to Watch later", Toast.LENGTH_SHORT).show());
            });
        }
        labels.add("Quality: " + (Store.maxHeight() >= 2160 ? "4K" : Store.maxHeight() + "p") + " max");
        actions.add(this::qualityMenu);
        labels.add("Audio language");
        actions.add(this::audioMenu);
        labels.add("Subtitles: " + (Store.captions() ? "On" : "Off"));
        actions.add(() -> { Store.setCaptions(!Store.captions()); applyTrackPrefs(Store.maxHeight()); });
        labels.add("Speed: " + speedName(Store.speed()));
        actions.add(this::speedMenu);
        if (!item.channelUrl.isEmpty()) {
            labels.add("Go to " + (item.channel.isEmpty() ? "channel" : item.channel));
            actions.add(() -> Nav.channel(this, item.channelUrl, item.channel));
            labels.add(subbed ? "Unsubscribe" : "Subscribe to " + item.channel);
            actions.add(() -> {
                if (Account.signedIn()) {
                    final String cid = Store.channelId(item.channelUrl);
                    Bg.run(() -> { Yti.subscribe(cid, !subbed); return true; }, (r, e) -> {
                        if (r == null) Toast.makeText(this, "YouTube didn't save that: " + Bg.message(e), Toast.LENGTH_LONG).show();
                    });
                }
                if (subbed) Store.unsubscribe(item.channelUrl);
                else Store.subscribe(item.channelUrl, item.channel,
                        info != null ? Item.bestImage(info.getUploaderAvatars(), 176) : "");
                Toast.makeText(this, subbed ? "Unsubscribed" : "Subscribed", Toast.LENGTH_SHORT).show();
            });
        }
        labels.add("Up next…");
        actions.add(this::relatedMenu);
        labels.add("Description");
        actions.add(this::description);
        labels.add("Autoplay: " + (Store.autoplay() ? "On" : "Off"));
        actions.add(() -> Store.setAutoplay(!Store.autoplay()));
        labels.add("Skip sponsors: " + (Store.sponsorBlock() ? "On" : "Off"));
        actions.add(() -> Store.setSponsorBlock(!Store.sponsorBlock()));

        new AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private void qualityMenu() {
        final int[] hs = {2160, 1440, 1080, 720, 480, 360};
        String[] names = {"4K (2160p)", "1440p", "1080p", "720p", "480p", "360p"};
        int cur = 2;
        for (int i = 0; i < hs.length; i++) if (hs[i] == Store.maxHeight()) cur = i;
        new AlertDialog.Builder(this).setTitle("Highest quality")
                .setSingleChoiceItems(names, cur, (d, w) -> {
                    Store.setMaxHeight(hs[w]);
                    portraitChecked = false;
                    applyTrackPrefs(hs[w]);
                    fixPortraitLimit(player.getCurrentTracks());
                    d.dismiss();
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
                .setItems(names.toArray(new String[0]), (d, w) -> selector.setParameters(
                        selector.buildUponParameters().setPreferredAudioLanguage(langs.get(w)).build()))
                .show();
    }

    private void speedMenu() {
        final float[] ss = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] names = new String[ss.length];
        int cur = 2;
        for (int i = 0; i < ss.length; i++) { names[i] = speedName(ss[i]); if (ss[i] == Store.speed()) cur = i; }
        new AlertDialog.Builder(this).setTitle("Playback speed")
                .setSingleChoiceItems(names, cur, (d, w) -> {
                    Store.setSpeed(ss[w]);
                    player.setPlaybackParameters(new PlaybackParameters(ss[w]));
                    d.dismiss();
                }).show();
    }

    private static String speedName(float s) {
        return s == 1f ? "Normal" : (s == (int) s ? String.valueOf((int) s) : String.valueOf(s)) + "x";
    }

    private void relatedMenu() {
        if (info == null) return;
        final List<Item> list = new ArrayList<>(Nav.queue);
        for (InfoItem ii : info.getRelatedItems()) {
            Item it = Item.from(ii);
            if (Item.VIDEO.equals(it.type) && list.size() < 30) list.add(it);
        }
        if (list.isEmpty()) return;
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Item it = list.get(i);
            names[i] = it.title + (it.channel.isEmpty() ? "" : "  —  " + it.channel)
                    + (it.durationText().isEmpty() ? "" : "  (" + it.durationText() + ")");
        }
        new AlertDialog.Builder(this).setTitle("Up next")
                .setItems(names, (d, w) -> {
                    Item it = list.get(w);
                    if (!Nav.queue.contains(it)) Nav.queue.clear();
                    else while (!Nav.queue.isEmpty() && Nav.queue.get(0) != it) Nav.queue.remove(0);
                    playNext(it);
                }).show();
    }

    private void description() {
        if (info == null) return;
        String d = info.getDescription() == null ? "" : info.getDescription().content();
        d = d.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        new AlertDialog.Builder(this).setTitle(item.title).setMessage(item.sub + "\n\n" + d)
                .setPositiveButton("Close", null).show();
    }

    // ------------------------------------------------------------------ helpers
    private void showError(String msg) {
        spinner.setVisibility(View.GONE);
        status.setText(msg + "\n\nSelect: try again    Back: go back");
        status.setVisibility(View.VISIBLE);
        top.setVisibility(View.VISIBLE);
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setShadowLayer(4, 0, 1, Color.BLACK);
        return t;
    }

    private TextView pill() {
        TextView t = text(17, Color.WHITE, false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xDD202020);
        g.setCornerRadius(dp(10));
        t.setBackground(g);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        t.setVisibility(View.GONE);
        return t;
    }

    private FrameLayout.LayoutParams corner(int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(dp(48), dp(48), dp(48), dp(110));
        return lp;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static JSONObject parse(String s) {
        try { return new JSONObject(s == null ? "{}" : s); } catch (Exception e) { return new JSONObject(); }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

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
