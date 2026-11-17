package com.lttube.tv;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.List;

/** Tests each part of your YouTube account link and shows what worked. */
public final class AccountCheck {

    public static void run(final Activity act) {
        final StringBuilder out = new StringBuilder("Checking…\n");
        final AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle("Account sync check")
                .setMessage(out)
                .setPositiveButton("Close", null)
                .show();
        Bg.POOL.execute(() -> {
            line(act, dlg, out, check("Sign-in", () -> { Account.token(); return "OK"; }));
            line(act, dlg, out, check("Account", () -> { Yti.loadProfile(); return Account.name().isEmpty() ? "name not found" : Account.name(); }));
            line(act, dlg, out, check("Home", () -> count(Yti.HOME)));
            line(act, dlg, out, check("Subscriptions feed", () -> count(Yti.SUBS)));
            line(act, dlg, out, check("Subscribed channels", () -> count(Yti.CHANNELS)));
            final String[] latest = {null};
            line(act, dlg, out, check("History", () -> {
                List<Shelf> s = Yti.sections(Yti.HISTORY, "History");
                int n = 0;
                for (Shelf sh : s) { n += sh.items.size(); if (latest[0] == null && !sh.items.isEmpty()) latest[0] = sh.items.get(0).videoId(); }
                return n + " videos" + (n > 0 ? " (latest: " + s.get(0).items.get(0).title + ")" : "");
            }));
            line(act, dlg, out, check("Liked videos", () -> count(Yti.LIKED)));
            line(act, dlg, out, check("Watch later", () -> count(Yti.WATCH_LATER)));
            line(act, dlg, out, check("Playlists", () -> count(Yti.PLAYLISTS)));
            if (latest[0] != null) {
                line(act, dlg, out, check("Like status", () -> {
                    String[] st = Yti.videoState(latest[0]);
                    return "read OK (" + st[0].toLowerCase() + ")";
                }));
            }
            line(act, dlg, out, "\nUsing YouTube's " + (Yti.tvFlavour() ? "TV" : "web") + " API.\n"
                    + "Liking, subscribing, Watch later and history are sent when you use them in the player (≡ menu).");
        });
    }

    private interface Check { String run() throws Exception; }

    private static String check(String name, Check c) {
        try { return "✓ " + name + ": " + c.run(); }
        catch (Exception e) { return "✗ " + name + ": " + Bg.message(e); }
    }

    private static String count(String feed) throws Exception {
        List<Shelf> s = Yti.sections(feed, feed);
        int n = 0;
        for (Shelf sh : s) n += sh.items.size();
        return n + " items" + (s.size() > 1 ? " in " + s.size() + " rows" : "");
    }

    private static void line(Activity act, AlertDialog dlg, StringBuilder out, String l) {
        act.runOnUiThread(() -> {
            if (out.toString().startsWith("Checking…\n")) out.delete(0, "Checking…\n".length());
            out.append(l).append('\n');
            if (dlg.isShowing()) dlg.setMessage(out.toString());
        });
    }

    private AccountCheck() { }
}
