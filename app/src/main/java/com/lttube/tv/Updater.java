package com.lttube.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Request;
import okhttp3.Response;

/** Update Center: finds new builds on GitHub, downloads and installs them in the app. */
public final class Updater {
    private static final String REPO = "lknicks9-a11y/lttube-firetv";
    public static final long EVERY = 20 * 60 * 1000;

    public static class Release {
        public int build;
        public String version = "", notes = "", apkUrl = "";
        public long size;
    }

    // what the Update Center shows
    public static Release latest;
    public static String lastStatus = "Not checked yet";
    public static long lastCheck;
    private static boolean downloading;
    private static String promptedFor = "";

    public static int currentBuild(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    /** True if the release is a newer version than this app (1.17 > 1.16 > 1.0.15). */
    public static boolean isNewer(Context c, Release rel) {
        if (rel == null) return false;
        // Releases are told apart by build number (every build is called 1.0.0 for now).
        if (rel.build > 0) return rel.build > currentBuild(c);
        return compare(rel.version, currentVersion(c)) > 0;
    }

    public static boolean updateReady(Context c) { return isNewer(c, latest); }

    static int compare(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    /** Blocks. Returns the newest release, or null if it can't tell. */
    public static Release fetchLatest() throws Exception {
        Request r = new Request.Builder()
                .url("https://api.github.com/repos/" + REPO + "/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "LTTube-TV")
                .build();
        try (Response res = NpDownloader.get().client.newCall(r).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("GitHub answered " + res.code());
            JSONObject o = new JSONObject(res.body().string());
            Release rel = new Release();
            String tag = o.optString("tag_name");
            // New releases are tagged "b<build>" and titled "LTTube 1.0.0 (build N)".
            if (tag.matches("b\\d+")) {
                rel.build = Integer.parseInt(tag.substring(1));
                rel.version = o.optString("name", "").replaceFirst("^LTTube\\s*", "").replaceFirst("\\s*\\(build \\d+\\)$", "");
                if (rel.version.isEmpty()) rel.version = "1.0.0";
            } else {
                rel.version = tag.replaceFirst("^v", "");
            }
            rel.notes = o.optString("body", "").trim();
            JSONArray assets = o.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) {
                    rel.apkUrl = a.optString("browser_download_url");
                    rel.size = a.optLong("size");
                }
            }
            if (rel.apkUrl.isEmpty()) rel.apkUrl = "https://github.com/" + REPO + "/releases/latest/download/LTTube-TV.apk";
            return rel;
        }
    }

    /** Checks in the background. If there's a newer build, asks once whether to install it. */
    public static void check(final Activity act, final boolean userAsked, final Runnable changed) {
        lastStatus = "Checking…";
        if (changed != null) changed.run();
        Bg.run(Updater::fetchLatest, (rel, err) -> {
            lastCheck = System.currentTimeMillis();
            if (rel == null) {
                lastStatus = "Couldn't check: " + Bg.message(err);
                if (userAsked) Toast.makeText(act, lastStatus, Toast.LENGTH_LONG).show();
                if (changed != null) changed.run();
                return;
            }
            latest = rel;
            boolean newer = isNewer(act, rel);
            lastStatus = newer ? "Update available: " + rel.version : "Up to date";
            if (changed != null) changed.run();
            if (act.isFinishing()) return;
            if (newer && (userAsked || !rel.version.equals(promptedFor))) {
                promptedFor = rel.version;
                offer(act, rel);
            } else if (!newer && userAsked) {
                Toast.makeText(act, "LTTube is up to date (" + currentVersion(act) + ")", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void offer(final Activity act, final Release rel) {
        // Readable notes: headings and bullets instead of raw "## 1.0.0" / "- …" text; only the newest section.
        StringBuilder nb = new StringBuilder();
        int sections = 0;
        for (String line : rel.notes.split("\n")) {
            String t = line.trim();
            if (t.startsWith("#")) { if (++sections > 1) break; continue; }
            if (t.isEmpty()) continue;
            nb.append(t.startsWith("- ") || t.startsWith("* ") ? "•  " + t.substring(2) : t).append("\n");
        }
        String notes = nb.toString().trim();
        if (notes.length() > 900) notes = notes.substring(0, 900) + "…";
        new LtDialog.Builder(act)
                .setTitle("Update available")
                .setMessage("LTTube " + rel.version + (rel.build > 0 ? " (build " + rel.build + ")" : "") + " — you have build " + currentBuild(act) + "."
                        + (notes.isEmpty() ? "" : "\n\nWhat's new\n" + notes))
                .setPositiveButton("Install now", (d, w) -> install(act, rel))
                .setNegativeButton("Later", null)
                .show();
    }

    // ------------------------------------------------------------------ download + install
    public static void install(final Activity act, final Release rel) {
        if (downloading) { Toast.makeText(act, "Already downloading the update…", Toast.LENGTH_SHORT).show(); return; }
        if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
            new LtDialog.Builder(act)
                    .setTitle("Allow LTTube to install updates")
                    .setMessage("Fire TV needs your OK once. On the next screen turn on \"Allow from this source\" (or Settings → My Fire TV → Developer options → Install unknown apps → LTTube), then come back and choose Install again.")
                    .setPositiveButton("Open settings", (d, w) -> {
                        try {
                            act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + act.getPackageName())));
                        } catch (Exception e) {
                            Toast.makeText(act, "Open Settings → My Fire TV → Developer options → Install unknown apps → LTTube", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        downloading = true;
        final ProgressBar bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        int pad = Math.round(24 * act.getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad, pad, pad);
        final LtDialog dlg = new LtDialog.Builder(act)
                .setTitle("Downloading LTTube " + rel.version + "…")
                .setView(bar)
                .setCancelable(false)
                .show();
        Bg.run(() -> download(act, rel, p -> act.runOnUiThread(() -> bar.setProgress(p))), (file, err) -> {
            downloading = false;
            dlg.dismiss();
            if (file == null) {
                Toast.makeText(act, "Update download failed: " + Bg.message(err), Toast.LENGTH_LONG).show();
                return;
            }
            launchInstaller(act, file);
        });
    }

    private interface Progress { void at(int percent); }

    private static File download(Context c, Release rel, Progress p) throws Exception {
        // Older Fire TVs need the file somewhere the installer can read.
        File dir = Build.VERSION.SDK_INT >= 24 ? new File(c.getCacheDir(), "updates") : c.getExternalFilesDir("updates");
        if (dir == null) dir = new File(c.getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("No space for the update");
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        File out = new File(dir, "LTTube-" + rel.version + ".apk");
        Request r = new Request.Builder().url(rel.apkUrl).header("User-Agent", "LTTube-TV").build();
        try (Response res = NpDownloader.get().client.newBuilder()
                .readTimeout(2, java.util.concurrent.TimeUnit.MINUTES).build()
                .newCall(r).execute()) {
            if (!res.isSuccessful() || res.body() == null) throw new Exception("HTTP " + res.code());
            long total = res.body().contentLength() > 0 ? res.body().contentLength() : rel.size;
            try (InputStream in = res.body().byteStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[64 * 1024];
                long got = 0;
                int n, lastPct = -1;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    got += n;
                    int pct = total > 0 ? (int) (got * 100 / total) : 0;
                    if (pct != lastPct) { lastPct = pct; p.at(pct); }
                }
            }
        }
        out.setReadable(true, false);
        return out;
    }

    private static void launchInstaller(Activity act, File apk) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = FileProvider.getUriForFile(act, act.getPackageName() + ".files", apk);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(apk);
            }
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(act, "Couldn't open the installer: " + Bg.message(e), Toast.LENGTH_LONG).show();
        }
    }

    private Updater() { }
}
