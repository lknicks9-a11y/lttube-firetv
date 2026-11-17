package com.lttube.tv;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Runs work off the main thread and hands the result back to it. */
public final class Bg {
    public interface Work<T> { T run() throws Exception; }
    public interface Done<T> { void done(T result, Exception error); }

    public static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    public static final Handler UI = new Handler(Looper.getMainLooper());

    public static <T> void run(Work<T> work, Done<T> done) {
        POOL.execute(() -> {
            T r = null;
            Exception err = null;
            try { r = work.run(); } catch (Exception e) { err = e; }
            final T fr = r;
            final Exception fe = err;
            UI.post(() -> done.done(fr, fe));
        });
    }

    public static String message(Exception e) {
        if (e == null) return "";
        Throwable t = e;
        while (t.getCause() != null && t.getMessage() == null) t = t.getCause();
        String m = t.getMessage();
        if (m == null) m = t.getClass().getSimpleName();
        if (m.contains("a bot") || m.contains("reCaptcha")) return "YouTube is asking this network to prove it isn't a bot. Try again in a little while.";
        if (m.contains("age-restricted") || m.contains("Age")) return "This video is age-restricted and can't be played without signing in.";
        if (m.length() > 200) m = m.substring(0, 200) + "…";
        return m;
    }

    private Bg() { }
}
