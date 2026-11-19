package com.lttube.tv;

import com.lttube.tv.music.Lyrics;
import com.lttube.tv.music.MItem;
import com.lttube.tv.music.Ytm;

import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

/** Prints what YouTube Music's API gives the music app (as a guest). Never fails. */
public class MusicApiTest {
    private static void dump(String name, Ytm.Page p) {
        System.out.println("MUS " + name + " title=[" + p.title + "] sub=[" + p.subtitle + "] thumb=" + !p.thumb.isEmpty()
                + " pl=" + p.playlistId + " shelves=" + p.shelves.size() + " cont=" + (p.continuation != null)
                + " banner=" + (p.banner.length() > 0 ? p.banner.substring(0, Math.min(90, p.banner.length())) : "-") + " subs=[" + p.subscribers + "] monthly=[" + p.monthly + "] ch=" + p.channelId + " desc=" + p.description.length());
        for (Ytm.Shelf s : p.shelves) {
            System.out.println("MUS   SHELF [" + s.strap + " / " + s.title + "] list=" + s.list + " n=" + s.items.size());
            for (int i = 0; i < Math.min(3, s.items.size()); i++) {
                MItem m = s.items.get(i);
                System.out.println("MUS     " + m.type + " | " + m.title + " | sub=" + m.subtitle + " | artist=" + m.artist + " | album=" + m.album
                        + " | v=" + m.videoId + " pl=" + m.playlistId + " b=" + m.browseId + " s=" + m.seconds + " thumb=" + (m.thumb.length() > 0 ? m.thumb.substring(0, Math.min(70, m.thumb.length())) : "-"));
            }
        }
    }

    private static Ytm.Page safe(String name, java.util.concurrent.Callable<Ytm.Page> c) {
        try { Ytm.Page p = c.call(); dump(name, p); return p; }
        catch (Throwable e) { System.out.println("MUS " + name + " FAILED " + e); return null; }
    }

    @Test
    public void everything() {
        Localization loc = new Localization("en", "US");
        NewPipe.init(NpDownloader.get(), loc, new ContentCountry("US"));
        Ytm.Page home = safe("HOME", () -> Ytm.browse(Ytm.HOME));
        if (home != null && home.continuation != null) safe("HOME+", () -> Ytm.more(Ytm.HOME, home.continuation));
        safe("NEW", () -> Ytm.browse(Ytm.NEW));
        safe("CHARTS", () -> Ytm.browse(Ytm.CHARTS));
        Ytm.Page moods = safe("MOODS", () -> Ytm.browse(Ytm.MOODS));
        if (moods != null) for (Ytm.Shelf s : moods.shelves) for (MItem m : s.items) if (MItem.CATEGORY.equals(m.type)) {
            System.out.println("MUS MOOD color=" + Integer.toHexString(m.color));
            safe("MOOD " + m.title, () -> Ytm.browse(m.browseId, m.params));
            break;
        }
        Ytm.Page search = safe("SEARCH", () -> Ytm.search("coldplay", false));
        safe("SEARCH SONGS", () -> Ytm.search("yellow coldplay", true));
        System.out.println("MUS SUGGEST " + Ytm.suggestions("taylor"));
        MItem album = null, artist = null, playlist = null, song = null;
        if (search != null) for (Ytm.Shelf s : search.shelves) for (MItem m : s.items) {
            if (album == null && MItem.ALBUM.equals(m.type)) album = m;
            if (artist == null && MItem.ARTIST.equals(m.type)) artist = m;
            if (playlist == null && MItem.PLAYLIST.equals(m.type)) playlist = m;
            if (song == null && m.playable()) song = m;
        }
        safe("ALBUM fixed", () -> Ytm.browse("MPREb_pnVvyDhXXd0"));
        for (String link : new String[]{"https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", "https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb"}) {
            try {
                com.lttube.tv.music.Imports.Source src = com.lttube.tv.music.Imports.read(link);
                System.out.println("MUS IMPORT " + src.service + " name=[" + src.name + "] tracks=" + src.tracks.size() + " cover=" + !src.cover.isEmpty()
                        + (src.tracks.isEmpty() ? "" : " first=" + src.tracks.get(0).title + " / " + src.tracks.get(0).artist + " / " + src.tracks.get(0).seconds));
            } catch (Throwable e) { System.out.println("MUS IMPORT FAILED " + link + " " + e); }
        }
        try {
            Ytm.Page ar = Ytm.browse("UCIaFw5VBEK8qaW6nRpx_qnw");
            for (Ytm.Shelf s : ar.shelves) if (s.list) { int off = 0; for (MItem m : s.items) if (m.official()) off++; System.out.println("MUS OFFICIAL top=" + s.items.size() + " official=" + off + " first mv=" + (s.items.isEmpty() ? "" : s.items.get(0).mvType + " album=" + s.items.get(0).album)); }
        } catch (Throwable e) { System.out.println("MUS OFFICIAL FAILED " + e); }
        safe("PLAYLIST fixed", () -> Ytm.browse("VLRDCLAK5uy_lWy02cQBnTVTlwuRauaGKeUDH3L6PXNxI"));
        try {
            java.lang.reflect.Method m = Ytm.class.getDeclaredMethod("call", String.class, org.json.JSONObject.class, String.class);
            m.setAccessible(true);
            org.json.JSONObject r = (org.json.JSONObject) m.invoke(null, "search", new org.json.JSONObject().put("query", "coldplay"), null);
            String text = r.toString();
            java.util.Map<String,Integer> keys = new java.util.TreeMap<>();
            java.util.regex.Matcher mm = java.util.regex.Pattern.compile("\"(\\w+(Renderer|ViewModel))\"").matcher(text);
            while (mm.find()) keys.merge(mm.group(1), 1, Integer::sum);
            System.out.println("MUS SEARCHKEYS " + keys);
            java.lang.reflect.Method pg = Ytm.class.getDeclaredMethod("page", org.json.JSONObject.class);
            pg.setAccessible(true);
            dump("SEARCH REPARSE", (Ytm.Page) pg.invoke(null, r));
            org.json.JSONArray secs = r.getJSONObject("contents").getJSONObject("tabbedSearchResultsRenderer").getJSONArray("tabs").getJSONObject(0)
                    .getJSONObject("tabRenderer").getJSONObject("content").getJSONObject("sectionListRenderer").getJSONArray("contents");
            for (int i = 0; i < secs.length(); i++) {
                org.json.JSONObject o = secs.getJSONObject(i);
                String k0 = o.keys().next();
                org.json.JSONObject in = o.getJSONObject(k0);
                java.util.List<String> ks = new java.util.ArrayList<>();
                java.util.Iterator<String> it = in.keys(); while (it.hasNext()) ks.add(it.next());
                System.out.println("MUS SEC " + i + " " + k0 + " keys=" + ks + " " + in.toString().substring(0, Math.min(500, in.toString().length())));
            }
            if (home != null && home.continuation != null) {
                String c = java.net.URLEncoder.encode(home.continuation, "UTF-8");
                org.json.JSONObject h2 = (org.json.JSONObject) m.invoke(null, "browse", new org.json.JSONObject().put("browseId", "FEmusic_home"), "&ctoken=" + c + "&continuation=" + c + "&type=next");
                String t2 = h2.toString();
                System.out.println("MUS HOMECONT " + t2.length() + " " + t2.substring(0, Math.min(1200, t2.length())));
                org.json.JSONObject h3 = (org.json.JSONObject) m.invoke(null, "browse", new org.json.JSONObject().put("continuation", home.continuation), null);
                String t3 = h3.toString();
                System.out.println("MUS HOMECONT2 " + t3.length() + " " + t3.substring(0, Math.min(1200, t3.length())));
            }
            int at = text.indexOf("\"musicShelfRenderer\"");
            System.out.println("MUS SEARCHRAW " + (at < 0 ? "none" : text.substring(Math.max(0, at - 300), Math.min(text.length(), at + 400))));
        } catch (Throwable e) { System.out.println("MUS SEARCHKEYS FAILED " + e); }
        final MItem a = album, ar = artist, pl = playlist;
        if (a != null) safe("ALBUM " + a.browseId, () -> Ytm.browse(a.browseId));
        if (ar != null) safe("ARTIST " + ar.browseId, () -> Ytm.browse(ar.browseId));
        if (pl != null) safe("PLAYLIST " + pl.browseId, () -> Ytm.browse(pl.browseId));
        String vid = song != null ? song.videoId : "yKNxeF4KMsY";
        try {
            Ytm.Queue q = Ytm.queue(vid, null, true);
            System.out.println("MUS QUEUE n=" + q.items.size() + " cont=" + (q.continuation != null) + " lyrics=" + q.lyricsId + " related=" + q.relatedId);
            for (int i = 0; i < Math.min(4, q.items.size()); i++) { MItem m = q.items.get(i); System.out.println("MUS   Q " + m.title + " | " + m.artist + " [" + m.artistId + "] | " + m.album + " | " + m.seconds + " | " + m.thumb.length()); }
            if (q.continuation != null) {
                Ytm.Queue q2 = Ytm.queueMore(q.continuation, vid, "RDAMVM" + vid);
                System.out.println("MUS QUEUE+ n=" + q2.items.size());
            }
            MItem first = q.items.isEmpty() ? song : q.items.get(0);
            if (first != null) {
                Lyrics l = Lyrics.find(first, q.lyricsId, first.seconds);
                System.out.println("MUS LYRICS " + (l == null ? "none" : l.source + " timed=" + l.timed + " lines=" + l.lines.size()));
            }
            System.out.println("MUS YT LYRICS " + (Ytm.lyrics(q.lyricsId) == null ? "none" : "yes"));
            org.json.JSONArray tl = Ytm.timedLyrics(q.lyricsId);
            System.out.println("MUS YT TIMED " + (tl == null ? "none" : tl.length() + " lines; first=" + tl.optJSONObject(0)));
        } catch (Throwable e) { System.out.println("MUS QUEUE FAILED " + e); }
        try {
            StreamInfo si = StreamInfo.getInfo(NewPipe.getService(0), "https://www.youtube.com/watch?v=" + vid);
            for (AudioStream s : si.getAudioStreams()) System.out.println("MUS AUDIO " + s.getFormat() + " " + s.getAverageBitrate() + " " + s.getDeliveryMethod() + " " + s.getCodec());
        } catch (Throwable e) { System.out.println("MUS AUDIO FAILED " + e); }
    }
}
