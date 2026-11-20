package com.lttube.tv.music;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.lttube.tv.Bg;
import com.lttube.tv.Feeds;
import com.lttube.tv.NpDownloader;

import org.json.JSONArray;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one player the whole app shares, so music keeps going from screen to screen (and with the app in the
 * background). Songs are played straight from YouTube's audio streams — nothing else is mixed in.
 */
public final class MusicPlayer {
    public interface Listener { void changed(); }

    public static final int REPEAT_OFF = 0, REPEAT_ALL = 1, REPEAT_ONE = 2;

    private static MusicPlayer instance;

    public static synchronized MusicPlayer get(Context c) {
        if (instance == null) instance = new MusicPlayer(c.getApplicationContext());
        return instance;
    }

    public static MusicPlayer peek() { return instance; }

    private final Context app;
    public final ExoPlayer player;
    private final SharedPreferences prefs;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public final List<MItem> queue = new ArrayList<>();
    private List<MItem> unshuffled;
    public int index = -1;
    public boolean shuffle;
    public int repeat = REPEAT_OFF;
    /** Keep playing similar songs when the list runs out (YouTube Music's radio). */
    public boolean autoplay = true;
    public String error;
    public boolean resolving;

    private String contToken, contVideo, contPlaylist;
    private boolean fetchingMore;
    private int seq;
    private final Map<String, String> urls = new HashMap<>();
    private final Map<String, Long> urlAt = new HashMap<>();
    private final Map<String, Integer> fails = new HashMap<>();

    private MusicPlayer(Context c) {
        app = c;
        prefs = c.getSharedPreferences("music", Context.MODE_PRIVATE);
        matchPrefs = c.getSharedPreferences("music_matches", Context.MODE_PRIVATE);
        shuffle = prefs.getBoolean("shuffle", false);
        repeat = prefs.getInt("repeat", REPEAT_OFF);
        autoplay = prefs.getBoolean("autoplay", true);
        String ua = YoutubeParsingHelper.getVisionOsUserAgent(new Localization("en", "US"));
        OkHttpDataSource.Factory http = new OkHttpDataSource.Factory(NpDownloader.get().client).setUserAgent(ua);
        player = new ExoPlayer.Builder(c).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) ended();
                fire();
            }
            @Override public void onIsPlayingChanged(boolean playing) { fire(); }
            @Override public void onPlayerError(PlaybackException e) {
                MItem cur = current();
                if (cur == null) return;
                // A stream link can expire: get a fresh one once, then move on.
                int f = fails.containsKey(cur.videoId) ? fails.get(cur.videoId) : 0;
                fails.put(cur.videoId, f + 1);
                urls.remove(cur.videoId);
                if (f == 0) { long pos = player.getCurrentPosition(); playAt(index, pos); }
                else { error = "Couldn't play " + cur.title; next(); }
            }
        });
        restore();
        watchForeground();
    }

    // ------------------------------------------------------------------ only plays while LT Music is on screen
    private int started;
    private Runnable leftApp;

    /** When you leave LT Music (Home button, another app), the music stops; it picks up where it was when you come back. */
    private void watchForeground() {
        leftApp = () -> { if (started == 0) { seq++; resolving = false; player.pause(); fire(); } };
        if (!(app instanceof android.app.Application)) return;
        ((android.app.Application) app).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(android.app.Activity a) { started++; Bg.UI.removeCallbacks(leftApp); }
            @Override public void onActivityStopped(android.app.Activity a) {
                started = Math.max(0, started - 1);
                // A moment's grace: going from one LT Music screen to another isn't leaving.
                if (started == 0) Bg.UI.postDelayed(leftApp, 700);
            }
            @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) { }
            @Override public void onActivityResumed(android.app.Activity a) { }
            @Override public void onActivityPaused(android.app.Activity a) { }
            @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) { }
            @Override public void onActivityDestroyed(android.app.Activity a) { }
        });
    }

    /** Music videos play in the video player: the music pauses for them. */
    public void pause() { player.pause(); fire(); }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void fire() {
        for (Listener l : listeners) l.changed();
    }

    public MItem current() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }
    public MItem peekNext() { return index + 1 < queue.size() ? queue.get(index + 1) : null; }
    public MItem peekPrev() { return index > 0 ? queue.get(index - 1) : null; }
    public boolean playing() { return player.isPlaying(); }

    // ------------------------------------------------------------------ what plays
    /** A list (album, playlist, chart): plays from start, then (with autoplay) similar songs. */
    public void playList(List<MItem> songs, int start, String playlistId) {
        List<MItem> l = new ArrayList<>();
        for (MItem m : songs) if (m.playable()) l.add(m);
        if (l.isEmpty()) return;
        MItem first = songs.get(Math.max(0, Math.min(start, songs.size() - 1)));
        int at = Math.max(0, l.indexOf(first));
        setQueue(l, at);
        contToken = null;
        contPlaylist = playlistId;
        contVideo = null;
        if (shuffle) shuffleRest();
        playAt(index, 0);
    }

    /** Shuffle a whole list from a random song. */
    public void shuffleList(List<MItem> songs, String playlistId) {
        List<MItem> l = new ArrayList<>();
        for (MItem m : songs) if (m.playable()) l.add(m);
        if (l.isEmpty()) return;
        Collections.shuffle(l);
        setShuffle(true);
        unshuffled = new ArrayList<>(songs);
        setQueue(l, 0);
        contToken = null;
        contPlaylist = playlistId;
        playAt(0, 0);
    }

    /** One song, then YouTube Music's radio for it. */
    /**
     * An artist's station: every official song of theirs (Top Songs and the rest), shuffled. It goes in Recently
     * Played as their station, like Apple's.
     */
    public static void playArtist(android.app.Activity a, String appleId, String name, String thumb, List<MItem> fallback) {
        android.widget.Toast.makeText(a, "Shuffling " + name + "\u2026", android.widget.Toast.LENGTH_SHORT).show();
        Bg.run(() -> {
            List<MItem> all = new ArrayList<>();
            if (appleId != null) for (Ytm.Shelf s : AppleWeb.seeAll(appleId, "songs").shelves) for (MItem m : s.items) if (m.playable() && m.official()) all.add(m);
            if (all.isEmpty() && fallback != null) all.addAll(fallback);
            return all;
        }, (all, e) -> {
            if (all == null || all.isEmpty() || a.isFinishing()) return;
            get(a).shuffleList(all, null);
            if (appleId != null) Mixes.visited(a, Mixes.artistStation(appleId, name, thumb));
            a.startActivity(nowPlaying(a));
        });
    }

    /** "Go to song radio": a station from this song, which goes in Recently Played like Apple's stations. */
    public void startStation(MItem song) {
        playRadio(song);
        final MItem s = song;
        Bg.run(() -> { resolve(s); return s; }, (r, e) -> { if (r != null && !r.videoId.isEmpty()) Mixes.visited(app, Mixes.station(r)); });
    }

    public void playRadio(MItem song) {
        List<MItem> l = new ArrayList<>();
        l.add(song);
        setQueue(l, 0);
        playAt(0, 0);
        loadQueue(song, null, true);
    }

    /** A mix / playlist / album by its id, without its page: YouTube Music's own queue for it. */
    public void playId(String videoId, String playlistId) {
        final int my = ++seq;
        resolving = true;
        fire();
        Bg.run(() -> Ytm.queue(videoId, playlistId, playlistId == null || playlistId.isEmpty()), (q, e) -> {
            if (my != seq) return;
            resolving = false;
            List<MItem> ok = new ArrayList<>();
            for (MItem m : q.items) if (m.official()) ok.add(m);
            if (ok.isEmpty()) ok.addAll(q.items);
            if (ok.isEmpty()) { error = "Couldn't load that"; fire(); return; }
            setQueue(ok, 0);
            contToken = q.continuation;
            contVideo = videoId;
            contPlaylist = playlistId;
            if (shuffle) shuffleRest();
            playAt(0, 0);
        });
    }

    /** Play right after the current song. */
    public void playNext(MItem song) {
        if (current() == null) { playRadio(song); return; }
        queue.add(index + 1, song);
        save();
        fire();
    }

    public void addToQueue(MItem song) {
        if (current() == null) { playRadio(song); return; }
        queue.add(song);
        save();
        fire();
    }

    private void loadQueue(MItem song, String playlistId, boolean radio) {
        final int my = seq;
        final String[] vid = {song.videoId};
        Bg.run(() -> { resolve(song); vid[0] = song.videoId; return Ytm.queue(song.videoId, playlistId, radio); }, (q, e) -> {
            final String videoId = vid[0];
            if (q == null || my != seq) return;
            MItem cur = current();
            // Keep what's playing first; the rest of YouTube Music's queue follows.
            List<MItem> rest = new ArrayList<>();
            for (MItem m : q.items) if ((cur == null || !m.videoId.equals(cur.videoId)) && m.official()) rest.add(m);
            // Radio songs show with Apple Music's names where the song is the same one.
            if (cur != null) {
                for (MItem m : q.items) if (m.videoId.equals(cur.videoId)) { if (cur.album.isEmpty()) cur.album = m.album; if (cur.seconds == 0) cur.seconds = m.seconds; }
            }
            while (queue.size() > index + 1) queue.remove(queue.size() - 1);
            queue.addAll(rest);
            contToken = q.continuation;
            contVideo = videoId;
            contPlaylist = radio ? "RDAMVM" + videoId : playlistId;
            save();
            fire();
            prefetch();
        });
    }

    private void moreIfNeeded() {
        if (fetchingMore || index < queue.size() - 3) return;
        final String token = contToken;
        if (token == null) {
            // The list ran out: carry on with the radio of the last song.
            if (!autoplay || queue.isEmpty() || repeat == REPEAT_ALL) return;
            MItem last = queue.get(queue.size() - 1);
            fetchingMore = true;
            Bg.run(() -> { resolve(last); return Ytm.queue(last.videoId, null, true); }, (q, e) -> {
                fetchingMore = false;
                if (q == null) { fallbackMore(); return; }
                java.util.Set<String> have = new java.util.HashSet<>();
                for (MItem m : queue) have.add(m.videoId);
                int added = 0;
                for (MItem m : q.items) if (m.official() && have.add(m.videoId)) { queue.add(m); added++; }
                contToken = q.continuation;
                contVideo = last.videoId;
                contPlaylist = "RDAMVM" + last.videoId;
                if (added == 0) { contToken = null; fallbackMore(); return; }
                addedMore(added);
            });
            return;
        }
        fetchingMore = true;
        Bg.run(() -> Ytm.queueMore(token, contVideo, contPlaylist), (q, e) -> {
            fetchingMore = false;
            java.util.Set<String> have = new java.util.HashSet<>();
            int added = 0;
            if (q != null) {
                for (MItem m : queue) have.add(m.videoId);
                for (MItem m : q.items) if (m.official() && have.add(m.videoId)) { queue.add(m); added++; }
            }
            // That list is done: carry on with music like the last song instead.
            contToken = added == 0 || q == null ? null : q.continuation;
            if (added == 0) { moreIfNeeded(); return; }
            addedMore(added);
        });
    }

    private void setQueue(List<MItem> l, int at) {
        seq++;
        queue.clear();
        queue.addAll(l);
        index = at;
        unshuffled = null;
        error = null;
    }

    // ------------------------------------------------------------------ controls
    public void toggle() {
        if (current() == null) return;
        if (player.getPlaybackState() == Player.STATE_IDLE || player.getPlaybackState() == Player.STATE_ENDED) { playAt(index, 0); return; }
        player.setPlayWhenReady(!player.getPlayWhenReady());
    }

    /** The queue ran out while more music was still on its way: play it the moment it arrives. */
    private boolean waitingForMore;

    public void next() {
        if (queue.isEmpty()) return;
        if (index + 1 < queue.size()) playAt(index + 1, 0);
        else if (repeat == REPEAT_ALL) playAt(0, 0);
        else if (autoplay) {
            // End of the queue: never just stop — load more music like it and carry straight on.
            waitingForMore = true;
            moreIfNeeded();
        } else { player.pause(); fire(); }
    }

    /** New songs were added to the end: if we ran out waiting for them, go on. */
    private void addedMore(int added) {
        save();
        fire();
        if (waitingForMore && added > 0 && index + 1 < queue.size()) { waitingForMore = false; playAt(index + 1, 0); }
    }

    /** Last resort when nothing like the last song can be found: songs from your Discover mix. */
    private void fallbackMore() {
        fetchingMore = true;
        Bg.run(() -> Mixes.page(app, Mixes.DISCOVER), (pg, e) -> {
            fetchingMore = false;
            if (pg == null) { waitingForMore = false; return; }
            java.util.Set<String> have = new java.util.HashSet<>();
            for (MItem m : queue) have.add(m.key());
            int added = 0;
            for (MItem m : pg.songs()) if (have.add(m.key()) && added < 25) { queue.add(m); added++; }
            addedMore(added);
        });
    }

    public void prev() {
        if (player.getCurrentPosition() > 4000 || index <= 0) { player.seekTo(0); return; }
        playAt(index - 1, 0);
    }

    public void jump(int i) { if (i >= 0 && i < queue.size()) playAt(i, 0); }

    public void seek(long ms) { player.seekTo(Math.max(0, ms)); }

    public void setShuffle(boolean on) {
        if (on == shuffle) return;
        shuffle = on;
        prefs.edit().putBoolean("shuffle", on).apply();
        if (on) shuffleRest();
        else if (unshuffled != null) {
            MItem cur = current();
            queue.clear();
            queue.addAll(unshuffled);
            index = Math.max(0, queue.indexOf(cur));
            unshuffled = null;
        }
        save();
        fire();
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        prefs.edit().putInt("repeat", repeat).apply();
        fire();
    }

    public void setAutoplay(boolean on) {
        autoplay = on;
        prefs.edit().putBoolean("autoplay", on).apply();
        fire();
    }

    private void shuffleRest() {
        if (queue.size() < 3) return;
        unshuffled = new ArrayList<>(queue);
        List<MItem> rest = new ArrayList<>(queue.subList(index + 1, queue.size()));
        Collections.shuffle(rest);
        while (queue.size() > index + 1) queue.remove(queue.size() - 1);
        queue.addAll(rest);
    }

    private void ended() {
        if (repeat == REPEAT_ONE) { player.seekTo(0); player.play(); return; }
        next();
    }

    // ------------------------------------------------------------------ streams
    private void playAt(int i, long startMs) {
        if (i < 0 || i >= queue.size()) return;
        index = i;
        final int my = ++seq;
        final MItem m = queue.get(i);
        error = null;
        resolving = true;
        player.stop();
        save();
        fire();
        Bg.run(() -> audioUrl(m), (url, e) -> {
            if (my != seq) return;
            resolving = false;
            if (url == null) {
                error = "Couldn't play " + m.title + (e == null ? "" : ": " + Bg.message(e));
                fire();
                // Skip it, like YouTube Music does with songs that won't play.
                if (index + 1 < queue.size()) Bg.UI.postDelayed(() -> { if (my == seq) next(); }, 1500);
                return;
            }
            Mixes.played(app, m);
            player.setMediaItem(MediaItem.fromUri(url), startMs);
            player.prepare();
            // Started while you were leaving the app: ready, but not playing.
            player.setPlayWhenReady(started > 0);
            fire();
            moreIfNeeded();
            prefetch();
        });
    }

    /** Gets the next song's stream ready so it starts the moment this one ends. */
    private void prefetch() {
        final MItem n = repeat == REPEAT_ONE ? null : peekNext();
        // Covers: songs from the radio get Apple Music's artwork (this one and the next few).
        for (int i = Math.max(0, index); i < Math.min(queue.size(), index + 4); i++) {
            final MItem c = queue.get(i);
            if (c.appleId.isEmpty() && !c.thumb.contains("mzstatic.com") && !c.title.isEmpty())
                Bg.run(() -> AppleWeb.cover(c), (u, e) -> { if (u != null && !u.isEmpty()) { c.thumb = u; fire(); } });
        }
        if (n == null || (!n.videoId.isEmpty() && fresh(n.videoId))) return;
        Bg.run(() -> audioUrl(n), (u, e) -> { });
    }

    private boolean fresh(String id) {
        synchronized (urls) {
            Long at = urlAt.get(id);
            return urls.containsKey(id) && at != null && System.currentTimeMillis() - at < 4 * 3600_000L;
        }
    }

    /** A song from Apple Music: find its official release on YouTube Music (keeping Apple's name and cover). */
    static void resolve(MItem m) throws Exception {
        if (!m.videoId.isEmpty()) return;
        // A song matched before always plays the same recording.
        String saved = matchPrefs == null ? null : matchPrefs.getString("am" + m.appleId, null);
        if (saved != null && !m.appleId.isEmpty()) { m.videoId = saved; return; }
        // The song's own details from Apple (its real artist, title, album and length) — a page's list can
        // show something else in those places (an artist page puts the album there; featured songs belong to others).
        if (!m.appleId.isEmpty()) {
            org.json.JSONObject o = AppleWeb.lookupTrack(m.appleId);
            if (o != null && "track".equals(o.optString("wrapperType"))) {
                m.seconds = (int) (o.optLong("trackTimeMillis") / 1000);
                m.album = o.optString("collectionName").replaceAll(" - (Single|EP)$", "");
                m.artist = o.optString("artistName", m.artist);
                if (!o.optString("trackName").isEmpty()) m.title = o.optString("trackName");
                if (m.thumb.isEmpty()) m.thumb = o.optString("artworkUrl100").replace("100x100bb", "600x600bb");
            }
        }
        Imports.Track t = new Imports.Track();
        t.title = m.title;
        t.artist = m.artist;
        t.album = m.album;
        t.seconds = m.seconds;
        MItem yt = Imports.find(t);
        if (yt == null) throw new Exception("couldn't find " + m.title + " to play");
        if (!m.appleId.isEmpty() && matchPrefs != null) matchPrefs.edit().putString("am" + m.appleId, yt.videoId).apply();
        m.videoId = yt.videoId;
        m.mvType = yt.mvType;
        m.artistId = yt.artistId;
        if (m.album.isEmpty()) m.album = yt.album;
        if (m.seconds == 0) m.seconds = yt.seconds;
        if (m.thumb.isEmpty()) m.thumb = yt.thumb;
    }

    private static android.content.SharedPreferences matchPrefs;
    private static android.content.SharedPreferences matches() { return matchPrefs; }

    private String audioUrl(MItem m) throws Exception {
        resolve(m);
        synchronized (urls) { if (fresh(m.videoId)) return urls.get(m.videoId); }
        StreamInfo si = StreamInfo.getInfo(Feeds.yt(), "https://www.youtube.com/watch?v=" + m.videoId);
        if (m.thumb.isEmpty()) try { m.thumb = si.getThumbnails().get(si.getThumbnails().size() - 1).getUrl(); } catch (Exception ignored) { }
        if (m.seconds == 0) m.seconds = (int) si.getDuration();
        if (m.artist.isEmpty()) m.artist = si.getUploaderName().replaceAll("(?i)\\s*-\\s*topic$", "");
        AudioStream best = null;
        // AAC (m4a) plays on every Fire TV; Opus only on newer ones.
        for (AudioStream a : si.getAudioStreams()) {
            if (!a.isUrl() || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            boolean orig = a.getAudioTrackType() == null || a.getAudioTrackType() == AudioTrackType.ORIGINAL;
            if (!orig) continue;
            if (best == null || score(a) > score(best)) best = a;
        }
        String url = best != null ? best.getContent() : si.getHlsUrl();
        if (url == null || url.isEmpty()) throw new Exception("no audio stream");
        synchronized (urls) { urls.put(m.videoId, url); urlAt.put(m.videoId, System.currentTimeMillis()); }
        return url;
    }

    private static int score(AudioStream a) {
        boolean aac = a.getFormat() == MediaFormat.M4A;
        boolean opusOk = Build.VERSION.SDK_INT >= 29;
        int base = aac ? 1_000_000 : (opusOk ? 900_000 : 0);
        return base + Math.max(0, a.getAverageBitrate());
    }

    // ------------------------------------------------------------------ remember the queue
    private void save() {
        try {
            JSONArray a = new JSONArray();
            int from = Math.max(0, index - 20), to = Math.min(queue.size(), index + 80);
            for (int i = from; i < to; i++) a.put(queue.get(i).toJson());
            prefs.edit().putString("queue", a.toString()).putInt("index", index - from).apply();
        } catch (Exception ignored) { }
    }

    private void restore() {
        try {
            JSONArray a = new JSONArray(prefs.getString("queue", "[]"));
            for (int i = 0; i < a.length(); i++) queue.add(MItem.fromJson(a.optJSONObject(i)));
            index = queue.isEmpty() ? -1 : Math.min(prefs.getInt("index", 0), queue.size() - 1);
        } catch (Exception ignored) { }
    }

    /** For the Now Playing screen to open. */
    public static Intent nowPlaying(Context c) { return new Intent(c, NowPlayingActivity.class); }
}
