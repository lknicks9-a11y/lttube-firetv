package com.lttube.tv;

import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One card: a video, channel, playlist, or a button like "Search". */
public class Item {
    public static final String VIDEO = "video", CHANNEL = "channel", PLAYLIST = "playlist", ACTION = "action";

    public String type = VIDEO;
    public String url = "";
    public String title = "";
    public String sub = "";
    public String thumb = "";
    public String channel = "";
    public String channelUrl = "";
    public long duration;      // seconds
    public long uploaded;      // epoch ms, 0 if unknown
    public boolean live;
    public boolean isShort;
    public long position;      // resume point, ms
    public String action = ""; // for ACTION cards
    public int color;          // for ACTION cards
    public int icon;           // drawable for ACTION cards

    public static Item action(String id, String title, String sub, int color, int icon) {
        Item i = action(id, title, sub, color);
        i.icon = icon;
        return i;
    }

    public static Item action(String id, String title, String sub, int color) {
        Item i = new Item();
        i.type = ACTION;
        i.action = id;
        i.title = title;
        i.sub = sub;
        i.color = color;
        return i;
    }

    public static Item from(InfoItem in) {
        Item i = new Item();
        i.url = in.getUrl();
        i.title = in.getName() == null ? "" : in.getName();
        i.thumb = bestImage(in.getThumbnails(), 360);
        switch (in.getInfoType()) {
            case STREAM: {
                StreamInfoItem s = (StreamInfoItem) in;
                i.type = VIDEO;
                i.channel = nz(s.getUploaderName());
                i.channelUrl = nz(s.getUploaderUrl());
                i.duration = Math.max(0, s.getDuration());
                i.live = s.getStreamType() == StreamType.LIVE_STREAM || s.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
                try { i.isShort = s.isShortFormContent(); } catch (Throwable ignored) { }
                try {
                    if (s.getUploadDate() != null) i.uploaded = s.getUploadDate().getInstant().toEpochMilli();
                } catch (Throwable ignored) { }
                List<String> bits = new ArrayList<>();
                if (!i.channel.isEmpty()) bits.add(i.channel);
                if (s.getViewCount() >= 0) bits.add(count(s.getViewCount()) + (i.live ? " watching" : " views"));
                if (s.getTextualUploadDate() != null && !i.live) bits.add(s.getTextualUploadDate());
                i.sub = join(bits);
                if (i.thumb.isEmpty()) i.thumb = videoThumb(i.url);
                break;
            }
            case CHANNEL: {
                ChannelInfoItem c = (ChannelInfoItem) in;
                i.type = CHANNEL;
                i.channel = i.title;
                i.channelUrl = i.url;
                i.sub = c.getSubscriberCount() >= 0 ? count(c.getSubscriberCount()) + " subscribers" : "Channel";
                break;
            }
            case PLAYLIST: {
                PlaylistInfoItem p = (PlaylistInfoItem) in;
                i.type = PLAYLIST;
                i.channel = nz(p.getUploaderName());
                List<String> bits = new ArrayList<>();
                bits.add("Playlist");
                if (p.getStreamCount() >= 0) bits.add(p.getStreamCount() + " videos");
                if (!i.channel.isEmpty()) bits.add(i.channel);
                i.sub = join(bits);
                break;
            }
            default:
                i.type = ACTION;
        }
        return i;
    }

    public String durationText() {
        if (live) return "LIVE";
        if (duration <= 0) return "";
        long h = duration / 3600, m = (duration % 3600) / 60, s = duration % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    public String videoId() {
        int i = url.indexOf("v=");
        if (i >= 0) {
            String id = url.substring(i + 2);
            int amp = id.indexOf('&');
            return amp >= 0 ? id.substring(0, amp) : id;
        }
        int s = url.lastIndexOf('/');
        return s >= 0 ? url.substring(s + 1) : url;
    }

    public static String videoThumb(String url) {
        Item tmp = new Item();
        tmp.url = url;
        return "https://i.ytimg.com/vi/" + tmp.videoId() + "/hqdefault.jpg";
    }

    public static String bestImage(List<Image> imgs, int wantHeight) {
        if (imgs == null || imgs.isEmpty()) return "";
        Image best = null;
        for (Image im : imgs) {
            if (best == null) { best = im; continue; }
            int h = im.getHeight(), bh = best.getHeight();
            boolean better = (h >= wantHeight && (bh < wantHeight || h < bh)) || (bh < wantHeight && h > bh);
            if (better) best = im;
        }
        String u = best.getUrl();
        if (u.startsWith("//")) u = "https:" + u;
        return u;
    }

    public static String count(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return trim(n / 1000.0) + "K";
        if (n < 1_000_000_000) return trim(n / 1_000_000.0) + "M";
        return trim(n / 1_000_000_000.0) + "B";
    }

    private static String trim(double d) {
        return d >= 10 ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d).replace(".0", "");
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String join(List<String> bits) {
        StringBuilder b = new StringBuilder();
        for (String s : bits) { if (b.length() > 0) b.append(" • "); b.append(s); }
        return b.toString();
    }

    // ---- saving ----
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type).put("url", url).put("title", title).put("sub", sub).put("thumb", thumb)
                    .put("channel", channel).put("channelUrl", channelUrl).put("duration", duration)
                    .put("uploaded", uploaded).put("live", live).put("short", isShort).put("position", position);
        } catch (Exception ignored) { }
        return o;
    }

    public static Item fromJson(JSONObject o) {
        Item i = new Item();
        i.type = o.optString("type", VIDEO);
        i.url = o.optString("url");
        i.title = o.optString("title");
        i.sub = o.optString("sub");
        i.thumb = o.optString("thumb");
        i.channel = o.optString("channel");
        i.channelUrl = o.optString("channelUrl");
        i.duration = o.optLong("duration");
        i.uploaded = o.optLong("uploaded");
        i.live = o.optBoolean("live");
        i.isShort = o.optBoolean("short");
        i.position = o.optLong("position");
        return i;
    }
}
