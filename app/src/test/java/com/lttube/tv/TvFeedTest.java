package com.lttube.tv;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Dumps how YouTube's TV home (signed out) is parsed, to check titles. Never fails. */
public class TvFeedTest {
    @Test
    public void tvHome() {
        for (String id : new String[]{"FEtopics", "FEwhat_to_watch"}) {
            boolean tv = id.equals("FEtopics");
            try {
                JSONObject client = new JSONObject().put("clientName", tv ? "TVHTML5" : "WEB")
                        .put("clientVersion", tv ? "7.20251113.10.00" : "2.20251113.01.00").put("hl", "en").put("gl", "US");
                JSONObject body = new JSONObject().put("browseId", id)
                        .put("context", new JSONObject().put("client", client));
                Request r = new Request.Builder().url("https://www.youtube.com/youtubei/v1/browse?prettyPrint=false")
                        .header("User-Agent", tv ? Account.TV_UA : NpDownloader.USER_AGENT)
                        .post(RequestBody.create(body.toString(), MediaType.get("application/json"))).build();
                String text;
                try (Response res = NpDownloader.get().client.newCall(r).execute()) { text = res.body().string(); System.out.println("LTT " + id + " HTTP " + res.code() + " " + text.length() + " chars"); }
                JSONObject o = new JSONObject(text);
                List<Shelf> shelves = Yti.shelves(o, "Recommended", tv, null);
                for (Shelf s : shelves) {
                    System.out.println("LTT SHELF [" + s.title + "] sub=[" + s.subtitle + "] items=" + s.items.size() + " cont=" + (s.cont != null));
                    for (int i = 0; i < Math.min(3, s.items.size()); i++) {
                        Item it = s.items.get(i);
                        System.out.println("LTT    " + it.type + " | " + it.title + " | ch=" + it.channel + " | sub=" + it.sub + " | dur=" + it.duration + " short=" + it.isShort);
                    }
                }
                java.util.Map<String,Integer> keys = new java.util.TreeMap<>();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"(\\w+(Renderer|ViewModel|Model))\"").matcher(text);
                while (m.find()) keys.merge(m.group(1), 1, Integer::sum);
                System.out.println("LTT KEYS " + keys);
                for (String k : new String[]{"lockupViewModel", "tileRenderer", "shelfRenderer", "gridRenderer", "horizontalListRenderer", "richItemRenderer"}) {
                    int at = text.indexOf("\"" + k + "\"");
                    if (at > 0) System.out.println("LTT RAW " + k + " " + text.substring(at, Math.min(text.length(), at + 3000)));
                }
                // raw shape of the first tile, to see the fields
                int t = text.indexOf("\"tileRenderer\"");
                if (t > 0) System.out.println("LTT RAW TILE " + text.substring(t, Math.min(text.length(), t + 2500)));
                int sh = text.indexOf("\"shelfRenderer\"");
                if (sh > 0) System.out.println("LTT RAW SHELF " + text.substring(sh, Math.min(text.length(), sh + 600)));
            } catch (Throwable e) {
                System.out.println("LTT " + id + " FAILED " + e);
            }
        }
    }
}
