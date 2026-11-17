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
        Store.init(this);
        Account.init(this);
        Yti.init(this);
        Locale l = Locale.getDefault();
        String country = l.getCountry().isEmpty() ? "US" : l.getCountry();
        NewPipe.init(NpDownloader.get(), new Localization(l.getLanguage(), country), new ContentCountry(country));
    }
}
