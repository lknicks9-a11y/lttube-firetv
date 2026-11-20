package com.lttube.tv.music;

import android.content.Context;
import android.graphics.Color;
import android.widget.FrameLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/**
 * Motion artwork: the short silent looping video some artists, albums and playlists have on Apple Music. It fills
 * its frame edge to edge (cropped like a photo, never stretched or stopping short) at full quality, over the
 * still picture, which shows until the video is actually playing. Always muted; never touches the music.
 */
public class Motion extends FrameLayout {
    private final PlayerView view;
    private ExoPlayer player;

    public Motion(Context c) {
        super(c);
        view = new PlayerView(c);
        view.setUseController(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        view.setShutterBackgroundColor(Color.TRANSPARENT);
        view.setKeepContentOnPlayerReset(true);
        view.setFocusable(false);
        view.setVisibility(INVISIBLE);
        addView(view, new LayoutParams(-1, -1));
    }

    public void play(String url) { play(url, 1920, 1080); }

    /** maxW x maxH: the sharpest picture worth decoding for where it shows (a cover needs less than a header). */
    public void play(String url, int maxW, int maxH) {
        if (url == null || url.isEmpty() || player != null) return;
        try {
            androidx.media3.exoplayer.trackselection.DefaultTrackSelector ts = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(getContext());
            // The best version that fits, straight away (not the blurry smallest one first).
            ts.setParameters(ts.buildUponParameters().setMaxVideoSize(maxW, maxH).setForceHighestSupportedBitrate(true));
            player = new ExoPlayer.Builder(getContext()).setTrackSelector(ts).build();
            player.setVolume(0f);
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
            player.addListener(new Player.Listener() {
                @Override public void onRenderedFirstFrame() { view.setVisibility(VISIBLE); }
                @Override public void onPlayerError(androidx.media3.common.PlaybackException e) { view.setVisibility(INVISIBLE); }
            });
            view.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
        } catch (Throwable e) { release(); }
    }

    public void pause() { if (player != null) player.pause(); }
    public void resume() { if (player != null) player.play(); }

    public void release() {
        view.setPlayer(null);
        if (player != null) { try { player.release(); } catch (Throwable ignored) { } player = null; }
    }

    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
}
