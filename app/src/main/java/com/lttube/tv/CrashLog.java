package com.lttube.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Remembers a crash and shows it on the next launch, so it can be photographed and fixed. */
public final class CrashLog {
    private static SharedPreferences p;

    public static void install(Context c) {
        p = c.getSharedPreferences("crash", Context.MODE_PRIVATE);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String trace = sw.toString();
                if (trace.length() > 1800) trace = trace.substring(0, 1800);
                p.edit().putString("last", trace).commit();
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    public static void showIfAny(Activity a) {
        if (p == null) return;
        String last = p.getString("last", "");
        if (last.isEmpty()) return;
        p.edit().remove("last").apply();
        new AlertDialog.Builder(a)
                .setTitle("LTTube closed unexpectedly last time")
                .setMessage("Take a photo of this and send it so it can be fixed:\n\n" + last)
                .setPositiveButton("OK", null)
                .show();
    }

    private CrashLog() { }
}
