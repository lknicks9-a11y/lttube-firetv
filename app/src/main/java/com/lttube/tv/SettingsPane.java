package com.lttube.tv;

import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

/** Settings like YouTube TV: a list on the left, details and buttons on the right. */
public class SettingsPane {
    final LinearLayout view;
    private final MainActivity a;
    private final LinearLayout list, detail;
    private final List<View> rows = new ArrayList<>();
    private String current = "account";

    SettingsPane(MainActivity act) {
        a = act;
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setPadding(dp(24), dp(26), dp(30), dp(20));

        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        view.addView(left, new LinearLayout.LayoutParams(dp(330), -1));

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView av = new ImageView(a);
        if (Account.signedIn() && !Account.photo().isEmpty())
            Glide.with(a).load(Account.photo()).apply(RequestOptions.circleCropTransform()).into(av);
        else { av.setImageResource(R.drawable.ic_account); av.setColorFilter(Color.WHITE); }
        head.addView(av, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView t = Ui.text(a, 26, Color.WHITE, true);
        t.setText("Settings");
        t.setPadding(dp(16), 0, 0, 0);
        head.addView(t);
        head.setPadding(dp(14), 0, 0, dp(16));
        left.addView(head);

        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        left.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        View line = new View(a);
        line.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(1), -1);
        ll.setMargins(dp(16), 0, dp(36), 0);
        view.addView(line, ll);

        ScrollView dsv = new ScrollView(a);
        detail = new LinearLayout(a);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setGravity(Gravity.CENTER_HORIZONTAL);
        detail.setPadding(0, dp(20), 0, dp(20));
        dsv.addView(detail);
        view.addView(dsv, new LinearLayout.LayoutParams(0, -1, 1f));

        build();
    }

    private void build() {
        list.removeAllViews();
        rows.clear();
        header("ACCOUNT");
        entry("account", R.drawable.ic_account, Account.signedIn() ? "Account: " + Account.name() : "Sign in");
        if (Account.signedIn()) entry("check", R.drawable.ic_check, "Check account sync");
        header("PLAYBACK");
        entry("autoplay", R.drawable.ic_next, "Autoplay next video");
        entry("quality", R.drawable.ic_settings, "Video quality");
        entry("captions", R.drawable.ic_cc, "Subtitles");
        entry("sponsor", R.drawable.ic_speed, "Skip sponsors");
        header("MENU");
        entry("menu", R.drawable.ic_library, "Side menu & Shorts");
        header("APP");
        entry("update", R.drawable.ic_replay, "Update Center");
        if (!Account.signedIn()) entry("import", R.drawable.ic_subs, "Import subscriptions");
        entry("history", R.drawable.ic_history, "Clear history on this TV");
        entry("about", R.drawable.ic_info, "About LTTube");
        render(current);
    }

    void refresh() { build(); }

    void focus() {
        for (View r : rows) if (current.equals(r.getTag())) { r.requestFocus(); return; }
        if (!rows.isEmpty()) rows.get(0).requestFocus();
    }

    private void header(String text) {
        TextView t = Ui.text(a, 13, Ui.TEXT2, true);
        t.setText(text);
        t.setPadding(dp(14), dp(12), 0, dp(4));
        list.addView(t);
    }

    private void entry(final String key, int icon, String label) {
        final LinearLayout r = new LinearLayout(a);
        r.setTag(key);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        final ImageView ic = new ImageView(a);
        ic.setImageResource(icon);
        ic.setColorFilter(Color.WHITE);
        r.addView(ic, new LinearLayout.LayoutParams(dp(22), dp(22)));
        final TextView t = Ui.text(a, 17, Color.WHITE, true);
        t.setText(label);
        t.setSingleLine(true);
        t.setPadding(dp(16), 0, 0, 0);
        r.addView(t);
        r.setOnFocusChangeListener((v, has) -> {
            r.setBackground(has ? Ui.pill(a, Ui.FOCUS, 10) : null);
            t.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
            ic.setColorFilter(has ? 0xFF0F0F0F : Color.WHITE);
            if (has && !key.equals(current)) render(key);
        });
        r.setOnClickListener(v -> focusDetail());
        r.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT) { focusDetail(); return true; }
            return false;
        });
        list.addView(r, new LinearLayout.LayoutParams(-1, -2));
        rows.add(r);
    }

    private void focusDetail() {
        for (int i = 0; i < detail.getChildCount(); i++) {
            View c = findFocusable(detail.getChildAt(i));
            if (c != null) { c.requestFocus(); return; }
        }
    }

    private View findFocusable(View v) {
        if (v.isFocusable()) return v;
        if (v instanceof LinearLayout) {
            LinearLayout l = (LinearLayout) v;
            for (int i = 0; i < l.getChildCount(); i++) { View f = findFocusable(l.getChildAt(i)); if (f != null) return f; }
        }
        return null;
    }

    // ------------------------------------------------------------------ the right side
    private void render(String key) {
        current = key;
        detail.removeAllViews();
        switch (key) {
            case "account":
                if (Account.signedIn()) {
                    title(Account.name().isEmpty() ? "Your account" : Account.name());
                    body((Account.handle().isEmpty() ? "" : Account.handle() + "\n") + "Home, Subscriptions, Library, likes, history and Watch later come from this YouTube account.");
                    buttons(new String[]{"Switch account", "Add account", "Sign out"}, -1, i -> {
                        if (i == 0) a.onAction("account");
                        else if (i == 1) a.onAction("signin");
                        else new LtDialog.Builder(a).setTitle("Sign out " + Account.name() + "?")
                                    .setMessage("Removes this Google account and its channels from LTTube on this TV.")
                                    .setPositiveButton("Sign out", (d, w) -> { Account.signOut(); a.onAction("account"); })
                                    .setNegativeButton("Cancel", null).show();
                    });
                } else {
                    title("Sign in to YouTube");
                    body("See your recommendations, subscriptions, history, playlists and Watch later. Sign in with your phone at yt.be/activate.");
                    buttons(new String[]{"Sign in", "Choose a profile"}, -1, i -> a.onAction(i == 0 ? "signin" : "account"));
                }
                break;
            case "check":
                title("Check account sync");
                body("Tests your Home, Subscriptions, channels, History, Liked videos, Watch later and Playlists, and shows what loaded.");
                buttons(new String[]{"Run check"}, -1, i -> a.onAction("check"));
                break;
            case "menu": {
                title("Side menu & Shorts");
                body("Hide Shorts everywhere: Home, Subscriptions, channels, the list under the player and the menu.");
                buttons(new String[]{"Show Shorts", "Hide Shorts"}, Store.hideShorts() ? 1 : 0, i -> { Store.setHideShorts(i == 1); a.applyNav(); render(key); focusDetail(); });
                String[][] items = {{"home", "Home"}, {"shorts", "Shorts"}, {"subs", "Subscriptions"}, {"library", "Library"}, {"music", "Music"},
                        {"movies", "Movies & TV"}, {"live", "Live"}, {"gaming", "Gaming"}, {"news", "News"}, {"sports", "Sports"}, {"podcasts", "Podcasts"}};
                for (String[] it : items) {
                    final String k = it[0];
                    body(it[1] + " in the side menu");
                    buttons(new String[]{"Shown", "Hidden"}, Store.navHidden(k) ? 1 : 0, i -> { Store.setNavHidden(k, i == 1); a.applyNav(); render(key); focusDetail(); });
                }
                break;
            }
            case "autoplay":
                title("Autoplay next video");
                body("When a video ends, play the next one after a short countdown.");
                buttons(new String[]{"On", "Off"}, Store.autoplay() ? 0 : 1, i -> { Store.setAutoplay(i == 0); render(key); focusDetail(); });
                break;
            case "quality": {
                title("Video quality");
                body("The highest quality LTTube plays. Lower it if videos buffer.");
                final int[] hs = {2160, 1440, 1080, 720, 480, 360};
                String[] names = {"4K", "1440p", "1080p", "720p", "480p", "360p"};
                int sel = 2;
                for (int i = 0; i < hs.length; i++) if (hs[i] == Store.maxHeight()) sel = i;
                buttons(names, sel, i -> { Store.setMaxHeight(hs[i]); render(key); focusDetail(); });
                break;
            }
            case "captions":
                title("Subtitles");
                body("Show subtitles when a video has them, in your TV's language.");
                buttons(new String[]{"On", "Off"}, Store.captions() ? 0 : 1, i -> { Store.setCaptions(i == 0); render(key); focusDetail(); });
                break;
            case "sponsor":
                title("Skip sponsors");
                body("Skips sponsor segments, self-promotion, subscribe reminders, intros and outros, using SponsorBlock.");
                buttons(new String[]{"On", "Off"}, Store.sponsorBlock() ? 0 : 1, i -> { Store.setSponsorBlock(i == 0); render(key); focusDetail(); });
                break;
            case "update": {
                title("Update Center");
                boolean upd = Updater.updateReady(a);
                body("You have LTTube " + Updater.currentVersion(a) + ".\n" + Updater.lastStatus
                        + "\nLTTube checks for updates every 20 minutes.");
                buttons(new String[]{upd ? "Install " + Updater.latest.version : "Check for updates"}, -1, i -> a.onAction("update"));
                break;
            }
            case "import":
                title("Import subscriptions");
                body("Copy your subscriptions once from LTTube on your PC (same Wi-Fi). Signing in to YouTube does this automatically.");
                buttons(new String[]{"Import"}, -1, i -> a.onAction("import"));
                break;
            case "history":
                title("Clear history on this TV");
                body(Store.history().size() + " videos in Continue watching on this TV. This doesn't change your YouTube history.");
                buttons(new String[]{"Clear"}, -1, i -> { Store.clearHistory(); Toast.makeText(a, "Cleared", Toast.LENGTH_SHORT).show(); render(key); });
                break;
            case "about":
                title("LTTube " + Updater.currentVersion(a));
                body("Ad-free YouTube for Fire TV.\nPlays videos directly on the TV, signs in like the YouTube TV app, and updates itself from GitHub.");
                break;
            default:
                break;
        }
    }

    private void title(String s) {
        TextView t = Ui.text(a, 30, Color.WHITE, true);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(30), 0, dp(14));
        detail.addView(t);
    }

    private void body(String s) {
        TextView t = Ui.text(a, 17, Ui.TEXT2, false);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(dp(30), 0, dp(30), dp(26));
        detail.addView(t);
    }

    private interface Pick { void picked(int i); }

    private void buttons(String[] labels, int selected, Pick pick) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            final boolean sel = i == selected;
            final TextView b = Ui.text(a, 16, Color.WHITE, true);
            b.setText(sel ? "✓  " + labels[i] : labels[i]);
            b.setFocusable(true);
            b.setPadding(dp(24), dp(10), dp(24), dp(10));
            b.setBackground(Ui.pill(a, sel ? 0xFF3A3F50 : Ui.SURFACE, 22));
            b.setOnFocusChangeListener((v, has) -> {
                b.setBackground(Ui.pill(a, has ? Ui.FOCUS : sel ? 0xFF3A3F50 : Ui.SURFACE, 22));
                b.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
            });
            b.setOnClickListener(v -> pick.picked(idx));
            b.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_BACK) { focus(); return true; }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            row.addView(b, lp);
        }
        detail.addView(row);
    }

    private int dp(float v) { return Ui.dp(a, v); }
}
