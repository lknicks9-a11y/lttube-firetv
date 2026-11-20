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

    @Test public void check() {
        org.schabi.newpipe.extractor.NewPipe.init(NpDownloader.get(), new org.schabi.newpipe.extractor.localization.Localization("en", "US"), new org.schabi.newpipe.extractor.localization.ContentCountry("US"));
        try { dump("BROWSE", AppleWeb.browse()); } catch (Throwable e) { System.out.println("APL BROWSE FAILED " + e); }
        try { dump("CHARTS", AppleWeb.charts()); } catch (Throwable e) { System.out.println("APL CHARTS FAILED " + e); }
        try { Ytm.Page g = AppleWeb.genre("AMG:18"); System.out.println("APL GENRE " + g.title); dump("GENRE", g.shelves); } catch (Throwable e) { System.out.println("APL GENRE FAILED " + e); }
        try { Ytm.Page a = AppleWeb.artist("1456793166"); System.out.println("APL ARTIST [" + a.title + "] banner=" + a.banner); dump("ARTIST", a.shelves); } catch (Throwable e) { System.out.println("APL ARTIST FAILED " + e); }
        try { Ytm.Page p = AppleWeb.playlist("https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb"); System.out.println("APL PLAYLIST [" + p.title + "] " + p.subtitle + " thumb=" + p.thumb + " desc=" + p.description.length()); dump("PLAYLIST", p.shelves); } catch (Throwable e) { System.out.println("APL PLAYLIST FAILED " + e); }
        try { dump("SEARCH", AppleWeb.search("drake").shelves); } catch (Throwable e) { System.out.println("APL SEARCH FAILED " + e); }
        try {
            java.util.List<Ytm.Shelf> c = AppleWeb.charts();
            MItem s = c.get(0).items.get(0);
            java.lang.reflect.Method r = com.lttube.tv.music.MusicPlayer.class.getDeclaredMethod("resolve", MItem.class);
            r.setAccessible(true);
            r.invoke(null, s);
            System.out.println("APL RESOLVE " + s.title + " / " + s.artist + " -> v=" + s.videoId + " mv=" + s.mvType + " album=" + s.album);
        } catch (Throwable e) { System.out.println("APL RESOLVE FAILED " + e); }
        MItem y = new MItem(); y.title = "Hell Yeah"; y.artist = "SoFaygo";
        System.out.println("APL COVER " + AppleWeb.cover(y));
    }
}
