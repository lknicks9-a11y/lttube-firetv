package com.lttube.tv.music;

import android.content.Context;
import android.view.Gravity;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;

/**
 * Motion artwork: the short silent looping video some artists, albums and playlists have on Apple Music. It fills
 * its frame edge to edge (cropped like a photo, never stretched or stopping short) at full quality, drawn right
 * where it sits on the page (so the page's colour never hides it), over the still picture until it's playing.
 * Always muted; never touches the music.
 */
public class Motion extends FrameLayout {
    private final AspectRatioFrameLayout frame;
    private final TextureView tv;
    private ExoPlayer player;

    public Motion(Context c) {
        super(c);
        setClipChildren(true);
        frame = new AspectRatioFrameLayout(c);
        frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        tv = new TextureView(c);
        frame.addView(tv, new LayoutParams(-1, -1));
        addView(frame, new LayoutParams(-1, -1, Gravity.CENTER));
        setAlpha(0f);
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
            player.setVideoTextureView(tv);
            player.addListener(new Player.Listener() {
                @Override public void onVideoSizeChanged(VideoSize s) {
                    if (s.width > 0 && s.height > 0) frame.setAspectRatio(s.width * s.pixelWidthHeightRatio / s.height);
                }
                @Override public void onRenderedFirstFrame() { animate().alpha(1f).setDuration(600).start(); }
                @Override public void onPlayerError(androidx.media3.common.PlaybackException e) { setAlpha(0f); }
            });
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
        } catch (Throwable e) { release(); }
    }

    public void pause() { if (player != null) player.pause(); }
    public void resume() { if (player != null) player.play(); }

    public void release() {
        if (player != null) { try { player.release(); } catch (Throwable ignored) { } player = null; }
    }

    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
}
