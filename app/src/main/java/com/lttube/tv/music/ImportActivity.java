package com.lttube.tv.music;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.lttube.tv.Bg;
import com.lttube.tv.Ui;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Import a playlist from Spotify or Apple Music. Typing a long link with a remote is painful, so the TV shows a QR
 * code: open it on your phone (same Wi-Fi), paste the playlist's share link, and the TV does the rest.
 */
public class ImportActivity extends Activity {
    private ServerSocket server;
    private volatile String state = "waiting", message = "", now = "";
    private volatile int done, total;
    /** The songs that didn't come over, and why. */
    private volatile List<String> missed = new java.util.ArrayList<>();
    private TextView status, sub;
    private ProgressBar bar;
    private TextView openBtn;
    private MItem result;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(MusicUi.background());
        setContentView(root);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(MusicUi.PAD + 20), dp(30), dp(MusicUi.PAD + 20), dp(30));
        root.addView(row, MusicUi.match());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = MusicUi.text(this, 32, Color.WHITE, true);
        t.setText("Import a playlist");
        left.addView(t);
        TextView how = MusicUi.text(this, 17, 0xD9FFFFFF, false);
        how.setLineSpacing(0, 1.25f);
        how.setText("1.  In Spotify or Apple Music on your phone, open the playlist and choose Share → Copy link.\n"
                + "2.  Scan the code (your phone on the same Wi-Fi as the TV).\n"
                + "3.  Paste the link and press Import.\n\n"
                + "LT Music finds each song's official release and adds the playlist to Your Library"
                + (Ytm.signedIn() ? " (and to your YouTube Music account)." : "."));
        how.setPadding(0, dp(16), 0, dp(18));
        left.addView(how);
        status = MusicUi.text(this, 19, Color.WHITE, true);
        left.addView(status);
        sub = MusicUi.line(this, 15, MusicUi.TEXT2, false);
        sub.setPadding(0, dp(6), 0, dp(10));
        left.addView(sub);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setVisibility(View.GONE);
        left.addView(bar, new LinearLayout.LayoutParams(-1, dp(10)));
        openBtn = MusicUi.pill(this, com.lttube.tv.R.drawable.ic_m_play, "Open playlist");
        openBtn.setVisibility(View.GONE);
        openBtn.setOnClickListener(v -> openResult());
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, dp(44));
        ol.topMargin = dp(16);
        left.addView(openBtn, ol);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setPadding(dp(40), 0, 0, 0);
        ImageView qr = new ImageView(this);
        right.addView(qr, new LinearLayout.LayoutParams(dp(220), dp(220)));
        TextView addr = MusicUi.text(this, 16, Color.WHITE, true);
        addr.setGravity(Gravity.CENTER);
        addr.setPadding(0, dp(12), 0, 0);
        right.addView(addr);
        row.addView(right);

        String url = start();
        if (url == null) {
            addr.setText("Couldn't start — is the TV on Wi-Fi?");
        } else {
            addr.setText(url.replace("http://", ""));
            qr.setImageBitmap(Ui.qr(url, 440));
        }
        paint();
    }

    private int dp(float v) { return MusicUi.dp(this, v); }

    private void paint() {
        runOnUiThread(() -> {
            switch (state) {
                case "waiting": status.setText("Waiting for a link…"); sub.setText(""); bar.setVisibility(View.GONE); break;
                case "reading": status.setText("Reading the playlist…"); sub.setText(""); bar.setVisibility(View.GONE); break;
                case "matching":
                    status.setText("Finding songs: " + done + " of " + total);
                    sub.setText(now);
                    bar.setVisibility(View.VISIBLE);
                    bar.setMax(Math.max(1, total));
                    bar.setProgress(done);
                    break;
                case "done":
                    status.setText(message);
                    sub.setText(missed.isEmpty() ? "Send another link to import more."
                            : missed.size() + (missed.size() == 1 ? " song" : " songs") + " didn't come over \u2014 your phone lists which, and why.");
                    bar.setVisibility(View.GONE);
                    if (result != null) { openBtn.setVisibility(View.VISIBLE); openBtn.requestFocus(); }
                    break;
                default: status.setText(message); sub.setText("Check the link and try again."); bar.setVisibility(View.GONE);
            }
        });
    }

    private void openResult() {
        if (result == null) return;
        Intent i = new Intent(this, PageActivity.class);
        i.putExtra(PageActivity.ITEM, result.toJson().toString());
        startActivity(i);
    }

    // ------------------------------------------------------------------ the import itself
    private synchronized void begin(String link) {
        if (state.equals("reading") || state.equals("matching")) return;
        state = "reading";
        done = 0;
        total = 0;
        missed = new java.util.ArrayList<>();
        paint();
        Bg.run(() -> {
            Imports.Source src = Imports.read(link);
            state = "matching";
            total = src.tracks.size();
            paint();
            List<String> miss = new java.util.ArrayList<>();
            List<MItem> songs = Imports.match(src, (d, t, n) -> { done = d; total = t; now = n; paint(); }, miss);
            if (songs.isEmpty()) throw new Exception("None of the songs could be found on YouTube Music");
            String where = Imports.save(this, src, songs);
            int count = songs.size();
            if (where.equals("account") && !Imports.lastPlaylistId.isEmpty()) {
                // Count what YouTube really kept (it can turn down a song it won't play in a playlist).
                java.util.Set<String> in = Imports.landed(Imports.lastPlaylistId);
                if (!in.isEmpty()) {
                    count = 0;
                    for (MItem m : songs) {
                        if (in.contains(m.videoId)) count++;
                        else miss.add(m.title + (m.artist.isEmpty() ? "" : " \u2014 " + m.artist) + ": YouTube Music wouldn't add it to a playlist");
                    }
                }
            }
            missed = miss;
            message = "Added “" + src.name + "” — " + count + " of " + src.tracks.size() + " songs"
                    + (where.equals("account") ? " (in your YouTube Music library)" : "");
            if (where.equals("tv")) {
                List<MItem> mine = Imports.local(this);
                result = mine.isEmpty() ? null : mine.get(mine.size() - 1);
            } else {
                MItem pl = new MItem();
                pl.type = MItem.PLAYLIST;
                pl.title = src.name;
                result = null;
            }
            return true;
        }, (ok, e) -> {
            if (e != null) { state = "error"; message = Bg.message(e); } else state = "done";
            paint();
        });
    }

    // ------------------------------------------------------------------ the little page for your phone
    private String start() {
        String ip = localIp();
        if (ip == null) return null;
        for (int port = 8097; port < 8105; port++) {
            try {
                server = new ServerSocket(port);
                final ServerSocket s = server;
                Thread th = new Thread(() -> serve(s), "import-server");
                th.setDaemon(true);
                th.start();
                return "http://" + ip + ":" + port;
            } catch (Exception ignored) { }
        }
        return null;
    }

    private void serve(ServerSocket s) {
        while (!s.isClosed()) {
            try {
                Socket c = s.accept();
                new Thread(() -> handle(c)).start();
            } catch (Exception e) { return; }
        }
    }

    private void handle(Socket c) {
        try (Socket sock = c) {
            sock.setSoTimeout(10_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String first = in.readLine();
            if (first == null) return;
            int len = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase(java.util.Locale.US).startsWith("content-length:")) len = Integer.parseInt(line.substring(15).trim());
            }
            String path = first.split(" ").length > 1 ? first.split(" ")[1] : "/";
            OutputStream out = sock.getOutputStream();
            if (first.startsWith("POST") && path.startsWith("/import")) {
                char[] body = new char[Math.min(len, 8192)];
                int r = 0;
                while (r < body.length) { int n = in.read(body, r, body.length - r); if (n < 0) break; r += n; }
                String form = new String(body, 0, r);
                String link = "";
                for (String kv : form.split("&")) if (kv.startsWith("link=")) link = URLDecoder.decode(kv.substring(5), "UTF-8");
                // Shared text sometimes has words around the link: keep just the link.
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://\\S+").matcher(link);
                if (m.find()) link = m.group();
                final String l = link;
                runOnUiThread(() -> begin(l));
                reply(out, "application/json", "{\"ok\":true}");
            } else if (path.startsWith("/status")) {
                JSONObject o = new JSONObject().put("state", state).put("done", done).put("total", total).put("now", now).put("message", message)
                        .put("missed", new org.json.JSONArray(missed));
                reply(out, "application/json", o.toString());
            } else {
                reply(out, "text/html; charset=utf-8", PAGE);
            }
        } catch (Exception ignored) { }
    }

    private static void reply(OutputStream out, String type, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length + "\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) { }
        return null;
    }

    @Override
    protected void onDestroy() {
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private static final String PAGE = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<title>LT Music — Import a playlist</title><style>"
            + "body{margin:0;font-family:-apple-system,system-ui,sans-serif;background:#2b2d30;color:#fff;padding:28px 22px}"
            + "h1{font-size:24px;margin:0 0 6px}p{color:#bbb;margin:0 0 20px;line-height:1.4}"
            + "input{width:100%;box-sizing:border-box;font-size:17px;padding:14px 16px;border-radius:26px;border:2px solid #fff;background:#3a3c40;color:#fff}"
            + "button{margin-top:14px;width:100%;font-size:18px;font-weight:700;padding:14px;border:0;border-radius:26px;background:#ff4f7b;color:#fff}"
            + "#s{margin-top:22px;font-size:16px;color:#ddd}.bar{height:8px;background:#444;border-radius:4px;margin-top:10px;overflow:hidden}"
            + ".bar i{display:block;height:100%;width:0;background:#fff}"
            + "h2{font-size:17px;margin:26px 0 10px}.miss{padding:10px 0;border-top:1px solid #444}.miss b{display:block;font-weight:600}"
            + ".miss span{display:block;color:#aaa;font-size:14px;margin-top:3px}</style></head><body>"
            + "<h1>Import a playlist</h1><p>Paste a Spotify or Apple Music playlist link (Share → Copy link). It has to be a public / shared playlist.</p>"
            + "<input id=l placeholder='https://open.spotify.com/playlist/…' autofocus><button onclick='go()'>Import to LT Music</button>"
            + "<div id=s></div><div class=bar><i id=b></i></div><div id=m></div>"
            + "<script>function go(){var v=document.getElementById('l').value;if(!v)return;"
            + "fetch('/import',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'link='+encodeURIComponent(v)});poll()}"
            + "function poll(){fetch('/status').then(r=>r.json()).then(j=>{var s=document.getElementById('s');"
            + "if(j.state=='reading')s.textContent='Reading the playlist…';"
            + "else if(j.state=='matching'){s.textContent='Finding songs: '+j.done+' of '+j.total;document.getElementById('b').style.width=(100*j.done/Math.max(1,j.total))+'%'}"
            + "else if(j.state=='done'){s.textContent='✓ '+j.message;document.getElementById('b').style.width='100%';"
            + "var m=document.getElementById('m');m.innerHTML='';if(j.missed&&j.missed.length){var h=document.createElement('h2');"
            + "h.textContent=j.missed.length+(j.missed.length==1?' song':' songs')+' didn\\'t come over';m.appendChild(h);"
            + "j.missed.forEach(function(x){var i=x.indexOf(': ');var d=document.createElement('div');d.className='miss';"
            + "var b=document.createElement('b');b.textContent=i>0?x.substring(0,i):x;d.appendChild(b);"
            + "var w=document.createElement('span');w.textContent=i>0?x.substring(i+2):'';d.appendChild(w);m.appendChild(d)})}return}"
            + "else if(j.state=='error'){s.textContent='✗ '+j.message;return}"
            + "setTimeout(poll,800)})}</script></body></html>";
}
