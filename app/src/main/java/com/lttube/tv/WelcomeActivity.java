package com.lttube.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

/** First screen: "Who's watching?" profiles, or "Welcome back" sign-in with a QR code. */
public class WelcomeActivity extends FragmentActivity {
    public static final String MODE = "mode";
    public static final String SIGN_IN = "signin";

    private FrameLayout root;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int signSeq;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.addView(new Ui.Blobs(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (SIGN_IN.equals(getIntent().getStringExtra(MODE)) || Account.profiles().isEmpty()) showSignIn();
        else showPicker();
        Updater.check(this, false, null);
        CrashLog.showIfAny(this);
    }

    private void clear() {
        signSeq++;
        h.removeCallbacksAndMessages(null);
        while (root.getChildCount() > 1) root.removeViewAt(1);
    }

    // ================================================================== Who's watching?
    private void showPicker() {
        clear();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_wordmark);
        logo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, dp(34));
        ll.topMargin = dp(70);
        ll.bottomMargin = dp(44);
        col.addView(logo, ll);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));

        final List<Account.Profile> profiles = Account.profiles();
        View first = null;
        for (int i = 0; i < profiles.size(); i++) {
            final int idx = i;
            final Account.Profile pr = profiles.get(i);
            View v = profileTile(pr.name, pr.handle, pr.photo, 0, () -> choose(idx), () -> removeDialog(pr));
            row.addView(v);
            Account.Profile act = Account.active();
            if (first == null || (act != null && act.acct.equals(pr.acct) && act.pageId.equals(pr.pageId))) first = v;
        }
        row.addView(profileTile("Add account", "", null, R.drawable.ic_account, this::showSignInFromPicker, null));

        TextView guest = pillButton("Watch as guest");
        guest.setOnClickListener(v -> { Account.watchAsGuest(); openHome(); });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.topMargin = dp(40);
        col.addView(guest, gl);

        TextView hint = Ui.text(this, 14, 0x99FFFFFF, false);
        hint.setText("Press ≡ on a profile to remove it from this TV");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = dp(18);
        col.addView(hint, hl);

        if (first != null) first.requestFocus();
    }

    private void showSignInFromPicker() { showSignIn(); }

    private View profileTile(String name, String handle, String photo, int icon, Runnable click, Runnable menu) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setFocusable(true);
        tile.setPadding(dp(16), dp(10), dp(16), dp(10));

        final FrameLayout ring = new FrameLayout(this);
        ring.setPadding(dp(5), dp(5), dp(5), dp(5));
        ImageView img = new ImageView(this);
        if (photo != null && !photo.isEmpty()) {
            Glide.with(this).load(photo).apply(RequestOptions.circleCropTransform()).into(img);
        } else {
            img.setBackground(Ui.circle(0x55FFFFFF, 0, 0));
            img.setImageResource(icon != 0 ? icon : R.drawable.ic_account);
            img.setColorFilter(Color.WHITE);
            img.setPadding(dp(34), dp(34), dp(34), dp(34));
        }
        ring.addView(img, new FrameLayout.LayoutParams(dp(124), dp(124), Gravity.CENTER));
        // wrap the picture so it sits centered over the name (a full-width ring pushed it left)
        tile.addView(ring, new LinearLayout.LayoutParams(-2, -2));

        TextView n = Ui.text(this, 19, Color.WHITE, true);
        n.setText(name.isEmpty() ? "YouTube account" : name);
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setPadding(0, dp(10), 0, 0);
        tile.addView(n, new LinearLayout.LayoutParams(dp(170), -2));
        TextView hd = Ui.text(this, 14, 0xCCFFFFFF, false);
        hd.setText(handle == null ? "" : handle);
        hd.setGravity(Gravity.CENTER);
        hd.setSingleLine(true);
        tile.addView(hd, new LinearLayout.LayoutParams(dp(170), -2));

        tile.setOnFocusChangeListener((v, has) -> {
            ring.setBackground(has ? Ui.circle(Color.TRANSPARENT, dp(4), Color.WHITE) : null);
            tile.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(120).start();
        });
        tile.setOnClickListener(v -> click.run());
        if (menu != null) tile.setOnKeyListener((v, code, ev) -> {
            if (code == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_UP) { menu.run(); return true; }
            return false;
        });
        return tile;
    }

    private void choose(int idx) {
        Account.setActive(idx);
        openHome();
    }

    private void removeDialog(Account.Profile pr) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + (pr.name.isEmpty() ? "this account" : pr.name) + "?")
                .setMessage("This signs that Google account (and its channels) out of LTTube on this TV.")
                .setPositiveButton("Remove", (d, w) -> {
                    Account.removeAccount(pr.acct);
                    if (Account.profiles().isEmpty()) showSignIn(); else showPicker();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    // ================================================================== Welcome back / sign in
    private void showSignIn() {
        clear();
        final int seq = signSeq;
        boolean returning = !Account.profiles().isEmpty();

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(80), dp(70), dp(20), dp(40));
        FrameLayout.LayoutParams lfl = new FrameLayout.LayoutParams(dp(470), -1, Gravity.START);
        root.addView(left, lfl);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_wordmark);
        logo.setAdjustViewBounds(true);
        left.addView(logo, new LinearLayout.LayoutParams(-2, dp(28)));
        TextView title = Ui.text(this, 40, Color.WHITE, true);
        title.setText(returning ? "Welcome back" : "Sign in");
        title.setPadding(0, dp(22), 0, dp(26));
        left.addView(title);

        TextView phone = menuButton(R.drawable.ic_account, "Sign in with your phone");
        left.addView(phone, menuLp());
        if (returning) {
            TextView back = menuButton(R.drawable.ic_library, "Choose a profile");
            back.setOnClickListener(v -> showPicker());
            left.addView(back, menuLp());
        }
        View line = new View(this);
        line.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams linel = new LinearLayout.LayoutParams(-1, dp(1));
        linel.setMargins(dp(10), dp(26), dp(30), dp(26));
        left.addView(line, linel);
        TextView guest = menuButton(R.drawable.ic_play, "Watch as guest");
        guest.setOnClickListener(v -> { Account.watchAsGuest(); openHome(); });
        left.addView(guest, menuLp());

        // right side: QR, address, code
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams rfl = new FrameLayout.LayoutParams(dp(420), -2, Gravity.END | Gravity.CENTER_VERTICAL);
        rfl.rightMargin = dp(80);
        root.addView(right, rfl);

        final ImageView qr = new ImageView(this);
        qr.setBackground(Ui.pill(this, Color.WHITE, 22));
        qr.setPadding(dp(14), dp(14), dp(14), dp(14));
        right.addView(qr, new LinearLayout.LayoutParams(dp(250), dp(250)));
        TextView scan = Ui.text(this, 17, Color.WHITE, false);
        scan.setText("Scan QR code or go to");
        scan.setPadding(0, dp(18), 0, 0);
        right.addView(scan);
        final TextView url = Ui.text(this, 26, Color.WHITE, true);
        url.setText("yt.be/activate");
        right.addView(url);
        TextView enter = Ui.text(this, 17, Color.WHITE, false);
        enter.setText("Enter the code");
        enter.setPadding(0, dp(12), 0, 0);
        right.addView(enter);
        final TextView code = Ui.text(this, 30, Color.WHITE, true);
        code.setText("…");
        code.setLetterSpacing(0.06f);
        right.addView(code);
        final TextView status = Ui.text(this, 15, 0xDDFFFFFF, false);
        status.setGravity(Gravity.CENTER);
        status.setText("Getting a code…");
        status.setPadding(0, dp(12), 0, 0);
        right.addView(status);

        phone.setOnClickListener(v -> showSignIn());
        phone.requestFocus();

        Bg.run(Account::start, (dc, err) -> {
            if (seq != signSeq) return;
            if (dc == null) { status.setText("Couldn't get a code: " + Bg.message(err) + "\nSelect \"Sign in with your phone\" to try again."); return; }
            code.setText(dc.userCode);
            String shown = dc.url.replace("https://", "").replace("http://", "").replace("www.", "");
            url.setText(shown);
            Bitmap b = Ui.qr(dc.url + (dc.url.contains("?") ? "&" : "?") + "user_code=" + dc.userCode, 480);
            if (b != null) qr.setImageBitmap(b);
            status.setText("Pick the account and channel you use");
            poll(dc, seq, status);
        });
    }

    private void poll(final Account.DeviceCode dc, final int seq, final TextView status) {
        h.postDelayed(() -> {
            if (seq != signSeq) return;
            Bg.run(() -> Account.poll(dc), (res, e) -> {
                if (seq != signSeq) return;
                if (res == null) { status.setText(Bg.message(e) + "\nSelect \"Sign in with your phone\" for a new code."); return; }
                if ("pending".equals(res) || "slow".equals(res)) {
                    if ("slow".equals(res)) dc.interval += 2;
                    poll(dc, seq, status);
                    return;
                }
                status.setText("Signed in! Loading your channels…");
                final String key = res;
                Bg.run(() -> Yti.accountsList(key), (list, e2) -> {
                    List<Account.Profile> l = list;
                    if (l == null || l.isEmpty()) {
                        l = new ArrayList<>();
                        Account.Profile pr = new Account.Profile();
                        pr.acct = key;
                        l.add(pr);
                    }
                    Account.setProfilesFor(key, l, false);
                    if (l.size() == 1) {
                        // only one profile: go straight in
                        List<Account.Profile> all = Account.profiles();
                        for (int i = 0; i < all.size(); i++) if (all.get(i).acct.equals(key)) { Account.setActive(i); break; }
                        Bg.run(() -> { Yti.loadProfile(); return true; }, (r, e3) -> openHome());
                    } else {
                        Toast.makeText(this, "Choose who's watching", Toast.LENGTH_SHORT).show();
                        showPicker();
                    }
                });
            });
        }, dc.interval * 1000L);
    }

    private TextView menuButton(int icon, String label) {
        final TextView t = Ui.text(this, 19, Color.WHITE, true);
        t.setText(label);
        t.setFocusable(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(22), dp(12), dp(22), dp(12));
        final android.graphics.drawable.Drawable ic = getResources().getDrawable(icon, getTheme()).mutate();
        ic.setBounds(0, 0, dp(22), dp(22));
        ic.setTint(Color.WHITE);
        t.setCompoundDrawables(ic, null, null, null);
        t.setCompoundDrawablePadding(dp(16));
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(has ? Ui.pill(this, 0xFFF1F1F1, 10) : null);
            t.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
            ic.setTint(has ? 0xFF0F0F0F : Color.WHITE);
        });
        return t;
    }

    private LinearLayout.LayoutParams menuLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        return lp;
    }

    private TextView pillButton(String label) {
        final TextView t = Ui.text(this, 16, Color.WHITE, true);
        t.setText(label);
        t.setFocusable(true);
        t.setPadding(dp(26), dp(10), dp(26), dp(10));
        t.setBackground(Ui.pill(this, 0x55FFFFFF, 22));
        t.setOnFocusChangeListener((v, has) -> {
            t.setBackground(Ui.pill(this, has ? 0xFFF1F1F1 : 0x55FFFFFF, 22));
            t.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
        });
        return t;
    }

    private int dp(float v) { return Ui.dp(this, v); }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        signSeq++;
        super.onDestroy();
    }
}
