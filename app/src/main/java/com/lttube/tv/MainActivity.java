package com.lttube.tv;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LTTube for Fire TV: shows the LTTube web app that runs on your PC, with the
 * remote's buttons wired up. The PC does all the YouTube work.
 */
public class MainActivity extends Activity {

    private static final int PORT = 8080;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FrameLayout root;
    private WebView web;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private LinearLayout setupList;
    private TextView setupStatus;
    private ExecutorService scanPool;
    private boolean menuLongHandled;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("lttube", MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        String saved = prefs.getString("server", null);
        if (saved != null) {
            showMessage("Connecting to LTTube…", saved, false);
            checkServer(saved, ok -> {
                if (ok) openWeb(saved);
                else showSetup("Couldn't reach LTTube at " + saved + ". Is it running on your PC?");
            });
        } else {
            showSetup(null);
        }
    }

    // ------------------------------------------------------------------
    // Web app
    // ------------------------------------------------------------------
    private void openWeb(String server) {
        stopScan();
        root.removeAllViews();
        if (web == null) {
            web = new WebView(this);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            s.setSupportZoom(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setUserAgentString(s.getUserAgentString() + " LTTubeTV/1.0");
            CookieManager.getInstance().setAcceptCookie(true);
            web.setBackgroundColor(Color.BLACK);
            web.setFocusable(true);
            web.setFocusableInTouchMode(true);

            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                    Uri u = req.getUrl();
                    Uri base = Uri.parse(currentServer());
                    // Stay inside LTTube; ignore links that would leave it.
                    return u.getHost() == null || !u.getHost().equals(base.getHost());
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                    if (req.isForMainFrame()) {
                        showMessage("Lost connection to LTTube",
                                "Make sure LTTube is running on your PC and both are on the same Wi-Fi.", true);
                    }
                }
            });

            web.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onShowCustomView(View view, CustomViewCallback cb) {
                    if (customView != null) { cb.onCustomViewHidden(); return; }
                    customView = view;
                    customCallback = cb;
                    root.addView(view, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    view.requestFocus();
                }

                @Override
                public void onHideCustomView() {
                    exitFullscreen();
                }
            });
        }
        if (web.getParent() != null) ((ViewGroup) web.getParent()).removeView(web);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String url = web.getUrl();
        if (url == null || !url.startsWith(server)) web.loadUrl(server + "/");
        else web.reload();
        web.requestFocus();
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        if (web != null) web.requestFocus();
    }

    private String currentServer() {
        return prefs.getString("server", "http://localhost:" + PORT);
    }

    private void js(String code, ValueCallback<String> cb) {
        if (web != null && web.getParent() != null) web.evaluateJavascript(code, cb);
    }

    // ------------------------------------------------------------------
    // Remote buttons
    // ------------------------------------------------------------------
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean webShown = web != null && web.getParent() != null;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = e.getAction() == KeyEvent.ACTION_UP;

        // Hold the menu (≡) button: change which PC to connect to.
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && e.getRepeatCount() == 6 && !menuLongHandled) {
                menuLongHandled = true;
                showSetup(null);
                return true;
            }
            if (up) {
                boolean wasLong = menuLongHandled;
                menuLongHandled = false;
                if (!wasLong && webShown) js("window.ltTV && window.ltTV.menu()", null);
            }
            return true;
        }

        if (!webShown) return super.dispatchKeyEvent(e);

        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                if (up && !e.isCanceled()) {
                    if (customView != null) {
                        js("document.fullscreenElement && document.exitFullscreen()", null);
                        exitFullscreen();
                    } else {
                        js("window.ltTV ? window.ltTV.back() : false", v -> {
                            if (!"true".equals(v)) {
                                if (web.canGoBack()) web.goBack();
                                else moveTaskToBack(true);
                            }
                        });
                    }
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && e.getRepeatCount() == 0) js("window.ltTV && window.ltTV.playPause()", null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (down) js("window.ltTV && window.ltTV.ff()", null);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (down) js("window.ltTV && window.ltTV.rw()", null);
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    // ------------------------------------------------------------------
    // Setup: find the PC running LTTube
    // ------------------------------------------------------------------
    private void showSetup(String problem) {
        stopScan();
        exitFullscreen();
        root.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(48), dp(36), dp(48), dp(36));
        scroll.addView(col);

        TextView title = text("LTTube", 40, Color.WHITE, true);
        col.addView(title);
        TextView sub = text("Pick the PC that's running LTTube", 20, 0xFFAAAAAA, false);
        sub.setPadding(0, dp(6), 0, dp(20));
        col.addView(sub);

        if (problem != null) {
            TextView p = text(problem, 18, 0xFFFF8A80, false);
            p.setPadding(0, 0, 0, dp(16));
            col.addView(p);
        }

        setupStatus = text("Searching your Wi-Fi…", 18, 0xFFCCCCCC, false);
        col.addView(setupStatus);

        setupList = new LinearLayout(this);
        setupList.setOrientation(LinearLayout.VERTICAL);
        setupList.setGravity(Gravity.CENTER_HORIZONTAL);
        setupList.setPadding(0, dp(12), 0, dp(12));
        col.addView(setupList);

        TextView or = text("Or type the address LTTube shows on your PC (for example 192.168.1.20:8080):", 17, 0xFFAAAAAA, false);
        or.setPadding(0, dp(16), 0, dp(8));
        col.addView(or);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        final EditText addr = new EditText(this);
        addr.setSingleLine(true);
        addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addr.setImeOptions(EditorInfo.IME_ACTION_GO);
        addr.setTextColor(Color.WHITE);
        addr.setHintTextColor(0xFF777777);
        addr.setHint("192.168.1.20:8080");
        addr.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        addr.setBackground(box(0xFF272727, 0xFF555555));
        addr.setPadding(dp(16), dp(10), dp(16), dp(10));
        String saved = prefs.getString("server", null);
        if (saved != null) addr.setText(saved.replace("http://", ""));
        row.addView(addr, new LinearLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button go = button("Connect");
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.leftMargin = dp(12);
        row.addView(go, gp);
        col.addView(row);

        final Runnable connect = () -> {
            String u = normalize(addr.getText().toString());
            if (u == null) { setupStatus.setText("Type an address like 192.168.1.20:8080"); return; }
            setupStatus.setText("Checking " + u + "…");
            checkServer(u, ok -> {
                if (ok) choose(u);
                else setupStatus.setText("No LTTube at " + u + ". Check the address and that LTTube is running.");
            });
        };
        go.setOnClickListener(v -> connect.run());
        addr.setOnEditorActionListener((v, actionId, ev) -> { connect.run(); return true; });

        Button again = button("Search again");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(20);
        col.addView(again, ap);
        again.setOnClickListener(v -> scan());

        TextView tip = text("Tip: hold the ≡ button on the remote any time to come back here.", 15, 0xFF888888, false);
        tip.setPadding(0, dp(24), 0, 0);
        col.addView(tip);

        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        again.requestFocus();
        scan();
    }

    private void choose(String server) {
        prefs.edit().putString("server", server).apply();
        openWeb(server);
    }

    private void scan() {
        stopScan();
        setupList.removeAllViews();
        setupStatus.setText("Searching your Wi-Fi for LTTube…");
        final List<String> subnets = localSubnets();
        if (subnets.isEmpty()) { setupStatus.setText("This TV isn't connected to Wi-Fi."); return; }
        final ExecutorService pool = Executors.newFixedThreadPool(40);
        scanPool = pool;
        final AtomicInteger left = new AtomicInteger(subnets.size() * 254);
        final List<String> found = Collections.synchronizedList(new ArrayList<String>());
        for (String prefix : subnets) {
            for (int i = 1; i <= 254; i++) {
                final String url = "http://" + prefix + i + ":" + PORT;
                pool.execute(() -> {
                    if (hello(url, 700)) {
                        found.add(url);
                        ui.post(() -> { if (scanPool == pool) addFound(url); });
                    }
                    if (left.decrementAndGet() == 0) {
                        ui.post(() -> {
                            if (scanPool != pool) return;
                            if (found.isEmpty()) setupStatus.setText("Didn't find LTTube. Start it on your PC, then choose Search again — or type the address below.");
                            else if (found.size() == 1 && prefs.getString("server", null) == null) choose(found.get(0));
                            else setupStatus.setText("Found LTTube:");
                        });
                    }
                });
            }
        }
        pool.shutdown();
    }

    private void addFound(final String url) {
        setupStatus.setText("Found LTTube:");
        Button b = button("LTTube on " + url.replace("http://", ""));
        b.setOnClickListener(v -> choose(url));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        setupList.addView(b, lp);
        if (setupList.getChildCount() == 1) b.requestFocus();
    }

    private void stopScan() {
        if (scanPool != null) scanPool.shutdownNow();
        scanPool = null;
    }

    private static List<String> localSubnets() {
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

    private static String normalize(String s) {
        s = s.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        Uri u = Uri.parse(s);
        if (u.getHost() == null) return null;
        if (u.getPort() == -1) s = s + ":" + PORT;
        return s;
    }

    /** True if an LTTube server answers at this address. */
    private static boolean hello(String server, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(server + "/api/hello").openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs * 2);
            if (c.getResponseCode() != 200) return false;
            InputStream in = c.getInputStream();
            byte[] buf = new byte[256];
            int n = in.read(buf);
            in.close();
            return n > 0 && new String(buf, 0, n, "UTF-8").contains("LTTube");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private interface Result { void done(boolean ok); }

    private void checkServer(final String server, final Result r) {
        new Thread(() -> {
            final boolean ok = hello(server, 3000);
            ui.post(() -> r.done(ok));
        }).start();
    }

    // ------------------------------------------------------------------
    // Simple screens
    // ------------------------------------------------------------------
    private void showMessage(String title, String detail, boolean withButtons) {
        exitFullscreen();
        root.removeAllViews();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(48), dp(48), dp(48), dp(48));
        col.addView(text(title, 30, Color.WHITE, true));
        TextView d = text(detail, 18, 0xFFAAAAAA, false);
        d.setPadding(0, dp(10), 0, dp(24));
        col.addView(d);
        if (withButtons) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            Button retry = button("Try again");
            Button change = button("Change PC");
            row.addView(retry);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(16);
            row.addView(change, lp);
            col.addView(row);
            retry.setOnClickListener(v -> {
                final String s = currentServer();
                showMessage("Connecting to LTTube…", s, false);
                checkServer(s, ok -> {
                    if (ok) openWeb(s);
                    else showMessage("Still can't reach LTTube", "Make sure LTTube is running on your PC and both are on the same Wi-Fi.", true);
                });
            });
            change.setOnClickListener(v -> showSetup(null));
            retry.requestFocus();
        }
        root.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label) {
        final Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(28), dp(12), dp(28), dp(12));
        b.setFocusable(true);
        b.setBackground(box(0xFF303030, 0));
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(has ? box(0xFFFFFFFF, 0) : box(0xFF303030, 0));
            b.setTextColor(has ? Color.BLACK : Color.WHITE);
        });
        return b;
    }

    private GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(24));
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------
    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        if (web != null) { web.onPause(); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        stopScan();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
