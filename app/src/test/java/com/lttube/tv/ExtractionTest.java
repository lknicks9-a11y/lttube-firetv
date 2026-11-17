package com.lttube.tv;

import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import okhttp3.Request;
import okhttp3.Response;

/** Smoke test run on GitHub: can we get a playable stream from YouTube right now? Never fails the build. */
public class ExtractionTest {
    @Test
    public void youtubeStillWorks() {
        NewPipe.init(NpDownloader.get(), new Localization("en", "US"), new ContentCountry("US"));
        try {
            StreamInfo si = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=aqz-KE-bpKQ");
            System.out.println("LTT title: " + si.getName());
            System.out.println("LTT hls: " + (si.getHlsUrl().isEmpty() ? "none" : "yes"));
            System.out.println("LTT video-only: " + si.getVideoOnlyStreams().size() + ", audio: " + si.getAudioStreams().size()
                    + ", muxed: " + si.getVideoStreams().size() + ", related: " + si.getRelatedItems().size()
                    + ", subtitles: " + si.getSubtitles().size());
            for (AudioStream a : si.getAudioStreams())
                System.out.println("LTT audio " + a.getFormat() + " " + a.getAverageBitrate() + " " + a.getAudioTrackType() + " " + a.getAudioLocale() + " " + a.getDeliveryMethod());
            String ua = YoutubeParsingHelper.getVisionOsUserAgent(new Localization("en", "US"));
            if (!si.getHlsUrl().isEmpty()) {
                String master = get(si.getHlsUrl(), ua, null);
                String variant = null;
                for (String line : master.split("\n")) if (line.startsWith("http")) { variant = line.trim(); break; }
                System.out.println("LTT master lines: " + master.split("\n").length);
                if (variant != null) {
                    String media = get(variant, ua, null);
                    String seg = null;
                    for (String line : media.split("\n")) if (line.startsWith("http")) { seg = line.trim(); break; }
                    if (seg != null) get(seg, ua, "bytes=0-1999999");
                }
            }
            for (VideoStream v : si.getVideoOnlyStreams()) {
                System.out.println("LTT first video-only " + v.getResolution() + " " + v.getCodec() + " " + v.getDeliveryMethod());
                get(v.getContent(), ua, "bytes=2000000-4000000");
                break;
            }
        } catch (Throwable t) {
            System.out.println("LTT STREAM FAILED: " + t);
            t.printStackTrace(System.out);
        }
        try {
            String[] id = Account.clientIdentity();
            System.out.println("LTT tv client id found: " + id[0].substring(0, 12) + "… (fallback=" + id[0].startsWith("861556708454") + ")");
            org.json.JSONObject code = Account.requestCode(id[0]);
            System.out.println("LTT device code: " + (code.has("user_code") ? "OK, url " + code.optString("verification_url") : code.toString()));
        } catch (Throwable t) {
            System.out.println("LTT SIGN-IN CODE FAILED: " + t);
        }
        try {
            String url = ServiceList.YouTube.getKioskList().getListLinkHandlerFactoryByType("trending_music").fromId("trending_music").getUrl();
            System.out.println("LTT music kiosk items: " + KioskInfo.getInfo(ServiceList.YouTube, url).getRelatedItems().size());
            System.out.println("LTT search items: " + SearchInfo.getInfo(ServiceList.YouTube,
                    ServiceList.YouTube.getSearchQHFactory().fromQuery("lofi")).getRelatedItems().size());
        } catch (Throwable t) {
            System.out.println("LTT LISTS FAILED: " + t);
        }
    }

    private static String get(String url, String ua, String range) throws Exception {
        Request.Builder b = new Request.Builder().url(url).header("User-Agent", ua);
        if (range != null) b.header("Range", range);
        try (Response r = NpDownloader.get().client.newCall(b.build()).execute()) {
            byte[] body = r.body() == null ? new byte[0] : r.body().bytes();
            System.out.println("LTT GET " + url.substring(0, Math.min(60, url.length())) + "… -> " + r.code() + " (" + body.length + " bytes)");
            return range == null ? new String(body, "UTF-8") : "";
        }
    }
}
