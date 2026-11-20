package com.lttube.tv;

import android.app.Application;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.util.Locale;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        // LT Music's glass bar copies the page under it; that needs pictures in regular memory.
        if (getResources().getBoolean(R.bool.is_music)) {
            try {
                com.bumptech.glide.Glide.init(this, new com.bumptech.glide.GlideBuilder()
                        .setDefaultRequestOptions(new com.bumptech.glide.request.RequestOptions().disallowHardwareConfig()));
            } catch (Throwable ignored) { }
        }
        Store.init(this);
        Account.init(this);
        Yti.init(this);
        Locale l = Locale.getDefault();
        String country = l.getCountry().isEmpty() ? "US" : l.getCountry();
        NewPipe.init(NpDownloader.get(), new Localization(l.getLanguage(), country), new ContentCountry(country));
    }
}
