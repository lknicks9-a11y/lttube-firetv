package com.lttube.tv;

import com.lttube.tv.music.AppleWeb;
import com.lttube.tv.music.MItem;
import com.lttube.tv.music.Ytm;

import org.junit.Test;

/** Checks the Apple Music side of LT Music with real data. Never fails; prints what it finds. */
public class AppleProbeTest {
    static void dump(String name, java.util.List<Ytm.Shelf> shelves) {
        System.out.println("APL " + name + " shelves=" + shelves.size());
        for (Ytm.Shelf s : shelves) {
            MItem f = s.items.isEmpty() ? null : s.items.get(0);
            System.out.println("APL   [" + s.title + "] list=" + s.list + " n=" + s.items.size() + " more=" + s.moreId
                    + (f == null ? "" : " first=" + f.type + " | " + f.title + " | " + f.artist + " | b=" + f.browseId + " a=" + f.appleId + " s=" + f.seconds + " thumb=" + (f.thumb.isEmpty() ? "-" : f.thumb.substring(Math.max(0, f.thumb.length() - 30)))));
        }
    }
    @Test public void keys() throws Exception {
        java.lang.reflect.Method pm = AppleWeb.class.getDeclaredMethod("page", String.class);
        pm.setAccessible(true);
        Object[] pg = (Object[]) pm.invoke(null, "https://music.apple.com/us/artist/x/1456793166");
        java.util.List<org.json.JSONObject> raw = new java.util.ArrayList<>();
        java.lang.reflect.Method cs = AppleWeb.class.getDeclaredMethod("collectSections", Object.class, java.util.List.class, int.class);
        cs.setAccessible(true);
        cs.invoke(null, pg[1], raw, 0);
        for (org.json.JSONObject o : raw) {
            org.json.JSONArray it = o.optJSONArray("items");
            org.json.JSONObject f = it == null || it.length() == 0 ? null : it.optJSONObject(0);
            java.util.List<String> k = new java.util.ArrayList<>();
            if (f != null) { java.util.Iterator<String> i = f.keys(); while (i.hasNext()) k.add(i.next()); }
            System.out.println("APL KEYS " + o.optString("itemKind") + " header=" + (o.opt("header") == null ? "-" : o.opt("header").toString().substring(0, Math.min(200, o.opt("header").toString().length()))) + " itemkeys=" + k);
            if (f != null && f.has("title")) System.out.println("APL   title=" + f.opt("title"));
        }
    }

    @Test public void seeAll() throws Exception {
        java.lang.reflect.Method pm = AppleWeb.class.getDeclaredMethod("page", String.class);
        pm.setAccessible(true);
        java.lang.reflect.Method sm = AppleWeb.class.getDeclaredMethod("sections", Object.class);
        sm.setAccessible(true);
        for (String sec : new String[]{"full-albums", "singles", "appears-on-albums", "top-songs", "compilation-albums", "live-albums"}) {
            try {
                Object[] pg = (Object[]) pm.invoke(null, "https://music.apple.com/us/artist/x/1456793166/see-all?section=" + sec);
                java.util.List<?> secs = (java.util.List<?>) sm.invoke(null, pg[1]);
                StringBuilder b = new StringBuilder();
                for (Object o : secs) {
                    java.lang.reflect.Field items = o.getClass().getDeclaredField("items"); items.setAccessible(true);
                    java.lang.reflect.Field kind = o.getClass().getDeclaredField("kind"); kind.setAccessible(true);
                    java.util.List<?> it = (java.util.List<?>) items.get(o);
                    b.append(" | ").append(kind.get(o)).append(" n=").append(it.size());
                    if (!it.isEmpty()) { MItem m = (MItem) it.get(0); b.append(" first=").append(m.title).append("/").append(m.artist); }
                }
                System.out.println("APL SEEALL " + sec + b);
            } catch (Throwable e) { System.out.println("APL SEEALL " + sec + " FAILED " + e.getCause()); }
        }
    }

    @Test public void check() {
        org.schabi.newpipe.extractor.NewPipe.init(NpDownloader.get(), new org.schabi.newpipe.extractor.localization.Localization("en", "US"), new org.schabi.newpipe.extractor.localization.ContentCountry("US"));
        try { dump("BROWSE", AppleWeb.browse()); } catch (Throwable e) { System.out.println("APL BROWSE FAILED " + e); }
        try { dump("CHARTS", AppleWeb.charts()); } catch (Throwable e) { System.out.println("APL CHARTS FAILED " + e); }
        try { Ytm.Page g = AppleWeb.genre("AMG:18"); System.out.println("APL GENRE " + g.title); dump("GENRE", g.shelves); } catch (Throwable e) { System.out.println("APL GENRE FAILED " + e); }
        try { Ytm.Page a = AppleWeb.artist("1456793166"); System.out.println("APL ARTIST [" + a.title + "] banner=" + a.banner); dump("ARTIST", a.shelves); } catch (Throwable e) { System.out.println("APL ARTIST FAILED " + e); }
        try { Ytm.Page p = AppleWeb.playlist("https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb"); System.out.println("APL PLAYLIST [" + p.title + "] " + p.subtitle + " thumb=" + p.thumb + " desc=" + p.description.length()); dump("PLAYLIST", p.shelves); } catch (Throwable e) { System.out.println("APL PLAYLIST FAILED " + e); }
        try { dump("SEARCH", AppleWeb.search("drake").shelves); } catch (Throwable e) { System.out.println("APL SEARCH FAILED " + e); }
        try { java.util.List<com.lttube.tv.music.MItem> cats = com.lttube.tv.music.AppleWeb.categories(); System.out.println("APL CATS n=" + cats.size() + (cats.isEmpty() ? "" : " first=" + cats.get(0).title + " " + cats.get(0).browseId + " " + cats.get(0).thumb));
            if (!cats.isEmpty()) dump("CATPAGE", com.lttube.tv.music.AppleWeb.anyPage(cats.get(2).browseId.substring(4)).shelves); } catch (Throwable e) { System.out.println("APL CATS FAILED " + e); }
        try { dump("EXTRAS", com.lttube.tv.music.AppleWeb.albumExtras("1440824484")); } catch (Throwable e) { System.out.println("APL EXTRAS FAILED " + e); }
        try { for (com.lttube.tv.music.Ytm.Shelf s : com.lttube.tv.music.AppleWeb.anyPage("https://music.apple.com/us/curator/apple-music-sports/1555172867").shelves) System.out.println("APL SPORTS [" + s.title + "] " + s.moreId + " " + (s.items.isEmpty() ? "" : s.items.get(0).thumb)); } catch (Throwable e) { System.out.println("APL SPORTS FAILED " + e); }
        System.out.println("APL ART " + com.lttube.tv.music.MItem.art("https://is1-ssl.mzstatic.com/image/thumb/Features/x/y.png/300x300cc.jpg", 600) + " " + com.lttube.tv.music.MItem.art("https://is1-ssl.mzstatic.com/image/thumb/Features/x/y.png/200x200SC.DN02.jpg?l=en-US", 600));
        try { System.out.println("APL ALBUMLOOK " + java.util.Arrays.toString(com.lttube.tv.music.AppleWeb.albumLook("6812412381"))); } catch (Throwable e) { System.out.println("APL ALBUMLOOK FAILED " + e); }
        try { dump("SEARCHALL", com.lttube.tv.music.AppleWeb.searchAll("sofaygo").shelves); } catch (Throwable e) { System.out.println("APL SEARCHALL FAILED " + e); }
        try {
            java.util.List<Ytm.Shelf> c = AppleWeb.charts();
            MItem s = c.get(0).items.get(0);
            java.lang.reflect.Method r = com.lttube.tv.music.MusicPlayer.class.getDeclaredMethod("resolve", MItem.class);
            r.setAccessible(true);
            r.invoke(null, s);
            System.out.println("APL RESOLVE " + s.title + " / " + s.artist + " -> v=" + s.videoId + " mv=" + s.mvType + " album=" + s.album);
        } catch (Throwable e) { System.out.println("APL RESOLVE FAILED " + e); }
        for (String k : new String[]{"albums", "singles", "appears", "songs"}) {
            try { Ytm.Page p = AppleWeb.seeAll("1456793166", k); Ytm.Shelf sh = p.shelves.get(0); System.out.println("APL ALL " + k + " n=" + sh.items.size() + (sh.items.isEmpty() ? "" : " first=" + sh.items.get(0).title + " / " + sh.items.get(0).artist + " last=" + sh.items.get(sh.items.size() - 1).title)); }
            catch (Throwable e) { System.out.println("APL ALL " + k + " FAILED " + e); }
        }
        try { for (MItem m : AppleWeb.seeAll("1456793166", "appears").shelves.get(0).items) System.out.println("APL APPEARS " + m.title + " | " + m.artist + " | " + m.subtitle); } catch (Throwable e) { System.out.println("APL APPEARS FAILED " + e); }
        try { for (MItem m : AppleWeb.seeAll("1456793166", "singles").shelves.get(0).items) System.out.println("APL SINGLE " + m.title + " | " + m.artist + " | " + m.subtitle); } catch (Throwable e) { System.out.println("APL SINGLE FAILED " + e); }
        try { System.out.println("APL BIO " + AppleWeb.artist("1456793166").description.length()); } catch (Throwable e) { System.out.println("APL BIO FAILED " + e); }
        // Matching: the top 25 chart songs, each with what it matched on YouTube Music.
        try {
            java.lang.reflect.Method r = com.lttube.tv.music.MusicPlayer.class.getDeclaredMethod("resolve", MItem.class);
            r.setAccessible(true);
            int ok = 0, n = 0;
            for (MItem s : AppleWeb.charts().get(0).items.subList(0, 25)) {
                n++;
                try {
                    r.invoke(null, s);
                    ok++;
                    System.out.println("APL MATCH " + s.title + " / " + s.artist + " (" + s.seconds + "s, " + s.album + ") -> " + s.videoId + " " + s.mvType);
                } catch (Throwable e) { System.out.println("APL NOMATCH " + s.title + " / " + s.artist + " " + e.getCause()); }
            }
            System.out.println("APL MATCHED " + ok + "/" + n);
        } catch (Throwable e) { System.out.println("APL MATCH FAILED " + e); }
        MItem y = new MItem(); y.title = "Hell Yeah"; y.artist = "SoFaygo";
        System.out.println("APL COVER " + AppleWeb.cover(y));
    }
}
