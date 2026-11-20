package com.lttube.tv;

import org.junit.Test;
import okhttp3.Request;
import okhttp3.Response;

/** Prints what Apple's public music data gives (charts, genres, browse, artist page). Never fails. */
public class AppleProbeTest {
    static String get(String u) {
        try {
            Request r = new Request.Builder().url(u).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36").build();
            try (Response res = NpDownloader.get().client.newCall(r).execute()) { return res.code() + " " + (res.body() == null ? "" : res.body().string()); }
        } catch (Throwable e) { return "ERR " + e; }
    }
    static void show(String name, String u, int n) { String b = get(u); System.out.println("APL " + name + " len=" + b.length() + " :: " + b.substring(0, Math.min(n, b.length())).replace('\n', ' ')); }
    static void grep(String name, String u, String re, int max) {
        String b = get(u);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(re).matcher(b);
        int i = 0; StringBuilder sb = new StringBuilder();
        while (m.find() && i++ < max) sb.append(" | ").append(m.group().length() > 160 ? m.group().substring(0, 160) : m.group());
        System.out.println("APL " + name + " status=" + b.substring(0, 3) + " len=" + b.length() + sb);
    }
    @Test public void probe() {
        show("RSS songs", "https://rss.applemarketingtools.com/api/v2/us/music/most-played/10/songs.json", 600);
        show("RSS albums", "https://rss.applemarketingtools.com/api/v2/us/music/most-played/10/albums.json", 300);
        show("RSS newrel", "https://rss.applemarketingtools.com/api/v2/us/music/new-releases/10/albums.json", 300);
        show("RSS newmusic", "https://rss.applemarketingtools.com/api/v2/us/music/new-music/10/albums.json", 300);
        show("OLD genre hiphop", "https://itunes.apple.com/us/rss/topsongs/limit=5/genre=18/json", 500);
        show("OLD genre albums", "https://itunes.apple.com/us/rss/topalbums/limit=5/genre=18/json", 300);
        grep("BROWSE playlists", "https://music.apple.com/us/browse", "https://music\\.apple\\.com/us/playlist/[^\"\\\\]+", 15);
        grep("BROWSE titles", "https://music.apple.com/us/browse", "\"title\":\"[^\"]{2,60}\"", 40);
        grep("ARTIST og", "https://music.apple.com/us/artist/sofaygo/1456793166", "og:image\" content=\"[^\"]+", 2);
        grep("ARTIST similar", "https://music.apple.com/us/artist/sofaygo/1456793166", "\"title\":\"(Similar Artists|Top Songs|Albums|Singles & EPs|Essential Albums|Playlists|Artist Playlists|Appears On)[^\"]*\"", 20);
        grep("PL artwork", "https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb", "\"artwork\":\\{[^}]{0,200}", 3);
        grep("SONG lookup", "https://itunes.apple.com/lookup?id=1456793166&entity=song&limit=8", "\"trackName\":\"[^\"]+\"", 8);
    }
}
