package com.lttube.tv;

import android.app.Activity;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * One-time copy of your YouTube subscriptions from LTTube on your PC.
 * After this the TV app doesn't need the PC at all.
 */
public class Importer {
    public interface Done { void done(int added); }

    private static final int PORT = 8080;
    private final Activity act;
    private final Done done;
    private final OkHttpClient http;
    private LtDialog waiting;
    private volatile boolean cancelled;

    public Importer(Activity act, Done done) {
        this.act = act;
        this.done = done;
        this.http = NpDownloader.get().client.newBuilder()
                .connectTimeout(800, TimeUnit.MILLISECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public void start() {
        new LtDialog.Builder(act)
                .setTitle("Import subscriptions")
                .setMessage("This copies the channels you subscribe to from LTTube on your PC, one time.\n\n"
                        + "Start LTTube on your PC (signed in to YouTube) and make sure it's on the same Wi-Fi as this TV.")
                .setPositiveButton("Find my PC", (d, w) -> scan())
                .setNeutralButton("Type address", (d, w) -> askAddress())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void wait(String msg) {
        if (waiting != null) waiting.dismiss();
        waiting = new LtDialog.Builder(act)
                .setTitle("Import subscriptions")
                .setMessage(msg)
                .setNegativeButton("Cancel", (d, w) -> cancelled = true)
                .setCancelable(false)
                .show();
    }

    private void stopWaiting() {
        if (waiting != null) waiting.dismiss();
        waiting = null;
    }

    private void fail(String msg) {
        stopWaiting();
        if (cancelled || act.isFinishing()) return;
        new LtDialog.Builder(act)
                .setTitle("Couldn't import")
                .setMessage(msg)
                .setPositiveButton("Try again", (d, w) -> start())
                .setNegativeButton("Close", null)
                .show();
    }

    // ---------------------------------------------------------------- find the PC
    private void scan() {
        cancelled = false;
        wait("Looking for LTTube on your Wi-Fi…");
        Bg.run(() -> {
            List<String> prefixes = subnets();
            ExecutorService pool = Executors.newFixedThreadPool(40);
            AtomicReference<String> found = new AtomicReference<>();
            for (String prefix : prefixes) {
                for (int i = 1; i <= 254; i++) {
                    final String url = "http://" + prefix + i + ":" + PORT;
                    pool.execute(() -> {
                        if (found.get() != null || cancelled) return;
                        if (hello(url) != null) found.compareAndSet(null, url);
                    });
                }
            }
            pool.shutdown();
            pool.awaitTermination(40, TimeUnit.SECONDS);
            return found.get();
        }, (url, err) -> {
            if (cancelled) return;
            if (url == null) {
                stopWaiting();
                new LtDialog.Builder(act)
                        .setTitle("Didn't find LTTube")
                        .setMessage("Make sure LTTube is running on your PC, then try again — or type the address LTTube shows on your PC.")
                        .setPositiveButton("Search again", (d, w) -> scan())
                        .setNeutralButton("Type address", (d, w) -> askAddress())
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                connect(url);
            }
        });
    }

    private void askAddress() {
        final EditText in = new EditText(act);
        in.setHint("192.168.1.20:8080");
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new LtDialog.Builder(act)
                .setTitle("LTTube address on your PC")
                .setView(in)
                .setPositiveButton("Connect", (d, w) -> {
                    String s = in.getText().toString().trim();
                    if (s.isEmpty()) return;
                    if (!s.startsWith("http")) s = "http://" + s;
                    if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
                    if (!s.matches(".*:\\d+$")) s = s + ":" + PORT;
                    cancelled = false;
                    connect(s);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- talk to it
    private void connect(final String server) {
        wait("Connecting to LTTube at " + server.replace("http://", "") + "…");
        Bg.run(() -> hello(server), (h, err) -> {
            if (cancelled) return;
            if (h == null) { fail("No LTTube at " + server.replace("http://", "") + "."); return; }
            if (h.optBoolean("locked")) askPassword(server, false);
            else download(server, null);
        });
    }

    private void askPassword(final String server, boolean wrong) {
        stopWaiting();
        final EditText in = new EditText(act);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new LtDialog.Builder(act)
                .setTitle(wrong ? "Wrong password — try again" : "LTTube app password")
                .setMessage("Enter the app password you set in LTTube's Settings on your PC.")
                .setView(in)
                .setPositiveButton("OK", (d, w) -> login(server, in.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void login(final String server, final String password) {
        wait("Signing in to LTTube…");
        Bg.run(() -> {
            JSONObject body = new JSONObject().put("password", password);
            Request r = new Request.Builder().url(server + "/api/login")
                    .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                    .build();
            try (Response res = http.newCall(r).execute()) {
                if (res.code() == 401) return "";
                for (String c : res.headers("Set-Cookie")) {
                    if (c.startsWith("lt_auth=")) return c.substring(0, c.indexOf(';') > 0 ? c.indexOf(';') : c.length());
                }
                return res.isSuccessful() ? "none" : null;
            }
        }, (cookie, err) -> {
            if (cancelled) return;
            if (cookie == null) { fail("Couldn't sign in to LTTube: " + Bg.message(err)); return; }
            if (cookie.isEmpty()) { askPassword(server, true); return; }
            download(server, "none".equals(cookie) ? null : cookie);
        });
    }

    private void download(final String server, final String cookie) {
        wait("Copying your subscriptions… (this can take a minute with lots of channels)");
        Bg.run(() -> {
            Request.Builder b = new Request.Builder().url(server + "/api/channels");
            if (cookie != null) b.header("Cookie", cookie);
            try (Response res = http.newCall(b.build()).execute()) {
                String text = res.body() == null ? "" : res.body().string();
                JSONObject o = new JSONObject(text);
                if (!res.isSuccessful()) throw new Exception(o.optString("error", "HTTP " + res.code()));
                JSONArray a = o.optJSONArray("items");
                List<Item> out = new ArrayList<>();
                if (a != null) {
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject c = a.getJSONObject(i);
                        String id = c.optString("id");
                        if (!id.startsWith("UC")) continue;
                        Item it = new Item();
                        it.type = Item.CHANNEL;
                        it.url = "https://www.youtube.com/channel/" + id;
                        it.title = c.optString("title");
                        it.channel = it.title;
                        it.channelUrl = it.url;
                        String t = c.optString("thumb");
                        it.thumb = t.startsWith("//") ? "https:" + t : t;
                        it.sub = "Subscribed";
                        out.add(it);
                    }
                }
                return out;
            }
        }, (list, err) -> {
            if (cancelled) return;
            if (list == null) {
                String m = Bg.message(err);
                if (m.toLowerCase().contains("sign")) m = "LTTube on your PC isn't signed in to YouTube. Sign in there first.";
                fail(m);
                return;
            }
            stopWaiting();
            int added = Store.addSubs(list);
            Toast.makeText(act, "Added " + added + " channels (" + list.size() + " found)", Toast.LENGTH_LONG).show();
            done.done(added);
        });
    }

    // ---------------------------------------------------------------- helpers
    private JSONObject hello(String server) {
        Request r = new Request.Builder().url(server + "/api/hello").build();
        try (Response res = http.newCall(r).execute()) {
            if (!res.isSuccessful() || res.body() == null) return null;
            JSONObject o = new JSONObject(res.body().string());
            return "LTTube".equals(o.optString("app")) ? o : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> subnets() {
        List<String> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String ip = a.getHostAddress();
                    String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
                    if (!out.contains(prefix)) out.add(prefix);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }
}
