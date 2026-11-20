package com.lttube.tv.music;

import android.content.Context;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Motion artwork: the short silent looping video some artists, albums and playlists have on Apple Music. It sits
 * over the still picture and fades in once it's actually playing (the picture shows until then, or if it can't play).
 * Always muted; never touches the music.
 */
public class Motion extends FrameLayout {
    private final TextureView tv;
    private ExoPlayer player;
    private int vw, vh;

    public Motion(Context c) {
        super(c);
        tv = new TextureView(c);
        addView(tv, new LayoutParams(-1, -1));
        setAlpha(0f);
    }

    public void play(String url) {
        if (url == null || url.isEmpty() || player != null) return;
        try {
            player = new ExoPlayer.Builder(getContext()).build();
            player.setVolume(0f);
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
            player.setVideoTextureView(tv);
            player.addListener(new Player.Listener() {
                @Override public void onVideoSizeChanged(VideoSize s) { vw = s.width; vh = s.height; fit(); }
                @Override public void onRenderedFirstFrame() { animate().alpha(1f).setDuration(600).start(); }
                @Override public void onPlayerError(androidx.media3.common.PlaybackException e) { setAlpha(0f); }
            });
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
        } catch (Throwable e) { release(); }
    }

    /**
     * Fills the frame like a cover photo: the video view stays exactly the frame's size and the picture inside it
     * is scaled up evenly and centred (the overflow cropped), so it never stretches or stops partway across.
     */
    private void fit() {
        int w = tv.getWidth(), h = tv.getHeight();
        if (vw == 0 || vh == 0 || w == 0 || h == 0) { post(this::fit); return; }
        float s = Math.max(w / (float) vw, h / (float) vh);
        android.graphics.Matrix m = new android.graphics.Matrix();
        // The view already stretches the video to w x h; undo that, then scale evenly by s, about the centre.
        m.setScale(vw * s / w, vh * s / h, w / 2f, h / 2f);
        tv.setTransform(m);
        tv.invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (vw > 0) post(this::fit);
    }

    public void pause() { if (player != null) player.pause(); }
    public void resume() { if (player != null) player.play(); }

    public void release() {
        if (player != null) { try { player.release(); } catch (Throwable ignored) { } player = null; }
    }

    @Override protected void onDetachedFromWindow() { release(); super.onDetachedFromWindow(); }
}
