package com.lttube.tv;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/** Lets the YouTube library make web requests. */
public final class NpDownloader extends Downloader {
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    private static NpDownloader instance;
    public final OkHttpClient client;

    private NpDownloader() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized NpDownloader get() {
        if (instance == null) instance = new NpDownloader();
        return instance;
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        final String url = request.url();
        final byte[] data = request.dataToSend();
        RequestBody body = null;
        if (data != null) body = RequestBody.create(data);
        else if ("POST".equals(request.httpMethod())) body = RequestBody.create(new byte[0]);

        okhttp3.Request.Builder b = new okhttp3.Request.Builder()
                .method(request.httpMethod(), body)
                .url(url)
                .addHeader("User-Agent", USER_AGENT);
        for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
            b.removeHeader(h.getKey());
            for (String v : h.getValue()) b.addHeader(h.getKey(), v);
        }

        try (okhttp3.Response r = client.newCall(b.build()).execute()) {
            if (r.code() == 429) throw new ReCaptchaException("reCaptcha Challenge requested", url);
            String text = null;
            ResponseBody rb = r.body();
            if (rb != null) text = rb.string();
            return new Response(r.code(), r.message(), r.headers().toMultimap(), text,
                    r.request().url().toString());
        }
    }
}
