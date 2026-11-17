package com.lttube.tv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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

/** Home: a YouTube TV-style side rail, top bar, and rows of videos. */
public class MainActivity extends FragmentActivity {

    private static final String[][] RAIL = {
            {"search", "Search"}, {"home", "Home"}, {"shorts", "Shorts"}, {"subs", "Subscriptions"},
            {"you", "You"}, {"music", "Music"}, {"gaming", "Gaming"}, {"live", "Live"}, {"settings", "Settings"}};
    private static final int[] RAIL_ICONS = {R.drawable.ic_search, R.drawable.ic_home, R.drawable.ic_shorts,
            R.drawable.ic_subs, R.drawable.ic_library, R.drawable.ic_music, R.drawable.ic_gaming, R.drawable.ic_live,
            R.drawable.ic_settings};

    private RowsController rows;
    private LinearLayout rail;
    private final List<LinearLayout> railItems = new ArrayList<>();
    private final List<TextView> railLabels = new ArrayList<>();
    private final List<ImageView> railIcons = new ArrayList<>();
    private ImageView avatar;
    private String section = "";
    private boolean railOpen;
    private float d;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0F0F0F);

        // ---- content: top bar + rows
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -1);
        cl.leftMargin = dp(84);
        root.addView(content, cl);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(18), dp(40), dp(4));

        avatar = new ImageView(this);
        avatar.setFocusable(true);
        avatar.setPadding(dp(3), dp(3), dp(3), dp(3));
        avatar.setBackground(focusCircle(false));
        avatar.setOnFocusChangeListener((v, has) -> avatar.setBackground(focusCircle(has)));
        avatar.setOnClickListener(v -> accountMenu());
        top.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView search = new TextView(this);
        search.setText("Search");
        search.setTextColor(0xFFAAAAAA);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setCompoundDrawablesRelativeWithIntrinsicBounds(tinted(R.drawable.ic_search, 0xFFAAAAAA), null, null, null);
        search.setCompoundDrawablePadding(dp(12));
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setFocusable(true);
        search.setBackground(pill(0xFF272727, 0));
        search.setOnFocusChangeListener((v, has) -> {
            search.setBackground(pill(has ? 0xFFFFFFFF : 0xFF272727, 0));
            search.setTextColor(has ? 0xFF0F0F0F : 0xFFAAAAAA);
        });
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(380), dp(40));
        sl.leftMargin = dp(18);
        top.addView(search, sl);

        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_wordmark);
        logo.setAdjustViewBounds(true);
        top.addView(logo, new LinearLayout.LayoutParams(-2, dp(26)));
        content.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout holder = new FrameLayout(this);
        holder.setId(View.generateViewId());
        content.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ---- side rail (expands with labels when focused)
        rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(14), dp(80), dp(14), dp(20));
        for (int i = 0; i < RAIL.length; i++) {
            final String key = RAIL[i][0];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setFocusable(true);
            item.setPadding(dp(14), 0, dp(18), 0);
            ImageView ic = new ImageView(this);
            ic.setImageResource(RAIL_ICONS[i]);
            item.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView lab = new TextView(this);
            lab.setText(RAIL[i][1]);
            lab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            lab.setPadding(dp(20), 0, 0, 0);
            lab.setVisibility(View.GONE);
            item.addView(lab);
            item.setOnFocusChangeListener((v, has) -> { styleRail(); if (has) openRail(true); else rail.post(this::maybeCloseRail); });
            item.setOnClickListener(v -> {
                if ("search".equals(key)) { startActivity(new Intent(this, SearchActivity.class)); return; }
                show(key, true);
            });
            item.setOnKeyListener((v, code, ev) -> {
                if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT && !rows.isEmpty()) {
                    rows.focus();
                    return true;
                }
                return false;
            });
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-1, dp(48));
            il.bottomMargin = dp(6);
            if ("settings".equals(key)) il.topMargin = dp(24);
            rail.addView(item, il);
            railItems.add(item);
            railLabels.add(lab);
            railIcons.add(ic);
        }
        root.addView(rail, new FrameLayout.LayoutParams(dp(84), -1));
        setContentView(root);

        rows = new RowsController(this, holder.getId(), (it, row) -> {
            if (Item.ACTION.equals(it.type)) { onAction(it.action); return; }
            Nav.open(this, it, row);
        });
        loadAvatar();
        show("home", false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAvatar();
        // refresh lists that change while watching
        if ("you".equals(section) || "settings".equals(section)) show(section, false);
    }

    // ------------------------------------------------------------------ rail
    private void openRail(boolean open) {
        if (railOpen == open) return;
        railOpen = open;
        ViewGroup.LayoutParams lp = rail.getLayoutParams();
        lp.width = dp(open ? 250 : 84);
        rail.setLayoutParams(lp);
        rail.setBackground(open ? new android.graphics.drawable.GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF20F0F0F, 0xE60F0F0F}) : null);
        for (TextView t : railLabels) t.setVisibility(open ? View.VISIBLE : View.GONE);
        styleRail();
    }

    private void maybeCloseRail() {
        View f = getCurrentFocus();
        if (f == null || !railItems.contains(f)) openRail(false);
    }

    private void styleRail() {
        for (int i = 0; i < railItems.size(); i++) {
            LinearLayout item = railItems.get(i);
            boolean focused = item.hasFocus() || item.isFocused();
            boolean current = RAIL[i][0].equals(section);
            int fg = focused ? 0xFF0F0F0F : current ? Color.WHITE : 0xFF9E9E9E;
            item.setBackground(focused ? pill(Color.WHITE, 0) : current && railOpen ? pill(0xFF272727, 0) : null);
            railIcons.get(i).setColorFilter(fg);
            railLabels.get(i).setTextColor(fg);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
            View f = getCurrentFocus();
            boolean inRail = f != null && railItems.contains(f);
            if (!inRail) {
                // like YouTube: Back jumps to the menu first
                int idx = indexOf(section);
                railItems.get(Math.max(0, idx)).requestFocus();
                return true;
            }
            if (!"home".equals(section)) { show("home", false); railItems.get(1).requestFocus(); return true; }
            finish();
            return true;
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) return true;
        return super.dispatchKeyEvent(e);
    }

    private int indexOf(String key) {
        for (int i = 0; i < RAIL.length; i++) if (RAIL[i][0].equals(key)) return i;
        return 1;
    }

    // ------------------------------------------------------------------ sections
    private void show(String key, boolean focusContent) {
        section = key;
        styleRail();
        rows.clear();
        if (focusContent) rows.onFirstRow(() -> rows.focus());
        else rows.onFirstRow(() -> { View f = getCurrentFocus(); if (f == null) rows.focus(); });
        boolean in = Account.signedIn();
        switch (key) {
            case "home":
                rows.add(Sources::continueWatching);
                if (in) rows.add(() -> Yti.sections(Yti.HOME, "Recommended"));
                else rows.add(Sources::recommended);
                rows.add(() -> Sources.kiosk("trending_music", "Trending music"));
                rows.add(() -> Sources.kiosk("trending_gaming", "Trending in gaming"));
                rows.add(() -> Sources.kiosk("live", "Live now"));
                rows.add(() -> Sources.kiosk("trending_podcasts_episodes", "Podcasts"));
                rows.add(() -> Sources.kiosk("trending_movies_and_shows", "Movies & trailers"));
                if (!in) rows.addNow(signInShelf());
                break;
            case "shorts":
                if (in) rows.add(Sources::accountShorts);
                rows.add(() -> Sources.searchShorts("#shorts", "Trending Shorts"));
                rows.add(() -> Sources.searchShorts("#shorts funny", "Comedy Shorts"));
                rows.add(() -> Sources.searchShorts("#shorts music", "Music Shorts"));
                rows.add(() -> Sources.searchShorts("#shorts gaming", "Gaming Shorts"));
                break;
            case "subs":
                if (in) {
                    rows.add(() -> Sources.accountRow(Yti.CHANNELS, "Your channels"));
                    rows.add(() -> Yti.sections(Yti.SUBS, "Latest"));
                } else {
                    rows.add(Sources::localChannels);
                    rows.add(Sources::localSubsFeed);
                    rows.addNow(signInShelf());
                }
                break;
            case "you":
                rows.add(Sources::continueWatching);
                if (in) {
                    rows.add(() -> Sources.accountRow(Yti.HISTORY, "History"));
                    rows.add(() -> Sources.accountRow(Yti.WATCH_LATER, "Watch later"));
                    rows.add(() -> Sources.accountRow(Yti.LIKED, "Liked videos"));
                    rows.add(() -> Sources.accountRow(Yti.PLAYLISTS, "Playlists"));
                } else {
                    rows.add(Sources::localHistory);
                    rows.addNow(signInShelf());
                }
                break;
            case "music":
                rows.add(() -> Sources.kiosk("trending_music", "Trending music"));
                rows.add(() -> Sources.search("new music this week official video", "New releases"));
                rows.add(() -> Sources.search("top hits 2026", "Top hits"));
                rows.add(() -> Sources.search("live performance", "Live performances"));
                rows.add(() -> Sources.kiosk("trending_podcasts_episodes", "Podcasts"));
                break;
            case "gaming":
                rows.add(() -> Sources.kiosk("trending_gaming", "Trending in gaming"));
                rows.add(() -> Sources.search("gaming highlights", "Highlights"));
                rows.add(() -> Sources.search("new game trailer", "Game trailers"));
                break;
            case "live":
                rows.add(() -> Sources.kiosk("live", "Live now"));
                rows.add(() -> Sources.search("live news", "News"));
                rows.add(() -> Sources.search("live sports", "Sports"));
                break;
            case "settings":
                rows.addNow(settingsShelf());
                break;
            default:
                break;
        }
    }

    private Shelf signInShelf() {
        List<Item> l = new ArrayList<>();
        l.add(Item.action("account", "Sign in to YouTube", "Your home, subscriptions, history and playlists", 0xFFFF0033, R.drawable.ic_account));
        l.add(Item.action("import", "Import subscriptions", "One time, from LTTube on your PC", 0xFF3A3A3A, R.drawable.ic_subs));
        return new Shelf("Make it yours", l);
    }

    private Shelf settingsShelf() {
        List<Item> s = new ArrayList<>();
        s.add(Account.signedIn()
                ? Item.action("account", Account.name().isEmpty() ? "Signed in" : Account.name(), "Select to sign out", 0xFFFF0033, R.drawable.ic_account)
                : Item.action("account", "Sign in to YouTube", "Code at yt.be/activate", 0xFFFF0033, R.drawable.ic_account));
        if (Account.signedIn()) s.add(Item.action("check", "Check account sync", "Test history, likes, subscriptions", 0xFF1E88E5, R.drawable.ic_check));
        s.add(Item.action("quality", "Video quality", qualityName(Store.maxHeight()) + " max", 0xFF26A69A, R.drawable.ic_settings));
        s.add(Item.action("autoplay", "Autoplay", Store.autoplay() ? "On" : "Off", 0xFF5C6BC0, R.drawable.ic_next));
        s.add(Item.action("sponsor", "Skip sponsors", Store.sponsorBlock() ? "On (SponsorBlock)" : "Off", 0xFFFFA000, R.drawable.ic_speed));
        s.add(Item.action("captions", "Subtitles", Store.captions() ? "On when available" : "Off", 0xFF8D6E63, R.drawable.ic_cc));
        s.add(Item.action("clear", "Clear history on this TV", Store.history().size() + " videos", 0xFF546E7A, R.drawable.ic_history));
        if (!Account.signedIn()) s.add(Item.action("import", "Import subscriptions", "From LTTube on your PC", 0xFF6D4C41, R.drawable.ic_subs));
        return new Shelf("Settings", s);
    }

    private void onAction(String a) {
        switch (a) {
            case "account": accountMenu(); break;
            case "check": AccountCheck.run(this); break;
            case "import":
                new Importer(this, added -> show(section, false)).start();
                break;
            case "quality": {
                final int[] hs = {2160, 1440, 1080, 720, 480, 360};
                String[] names = new String[hs.length];
                int cur = 2;
                for (int i = 0; i < hs.length; i++) { names[i] = qualityName(hs[i]); if (hs[i] == Store.maxHeight()) cur = i; }
                new AlertDialog.Builder(this).setTitle("Highest video quality")
                        .setSingleChoiceItems(names, cur, (dd, w) -> { Store.setMaxHeight(hs[w]); dd.dismiss(); show("settings", true); })
                        .show();
                break;
            }
            case "autoplay": Store.setAutoplay(!Store.autoplay()); show("settings", true); break;
            case "sponsor": Store.setSponsorBlock(!Store.sponsorBlock()); show("settings", true); break;
            case "captions": Store.setCaptions(!Store.captions()); show("settings", true); break;
            case "clear":
                new AlertDialog.Builder(this).setTitle("Clear watch history on this TV?")
                        .setPositiveButton("Clear", (dd, w) -> { Store.clearHistory(); show("settings", true); })
                        .setNegativeButton("Cancel", null).show();
                break;
            default: break;
        }
    }

    private void accountMenu() {
        if (!Account.signedIn()) {
            SignIn.show(this, () -> { loadAvatar(); show("home", true); });
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Signed in as " + (Account.name().isEmpty() ? "your YouTube account" : Account.name()))
                .setItems(new String[]{"Check account sync", "Sign out"}, (dd, w) -> {
                    if (w == 0) AccountCheck.run(this);
                    else {
                        Account.signOut();
                        loadAvatar();
                        show("home", false);
                        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void loadAvatar() {
        if (Account.signedIn() && !Account.photo().isEmpty()) {
            avatar.setColorFilter(null);
            Glide.with(this).load(Account.photo()).apply(RequestOptions.circleCropTransform()).into(avatar);
        } else {
            avatar.setImageResource(R.drawable.ic_account);
            avatar.setColorFilter(Account.signedIn() ? 0xFFFF0033 : 0xFF9E9E9E);
            if (Account.signedIn() && Account.photo().isEmpty())
                Bg.run(() -> { Yti.loadProfile(); return true; }, (r, e) -> {
                    if (!Account.photo().isEmpty()) loadAvatar();
                });
        }
    }

    private static String qualityName(int h) {
        return h == 2160 ? "4K" : h + "p";
    }

    // ------------------------------------------------------------------ helpers
    private GradientDrawable pill(int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(24));
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    private GradientDrawable focusCircle(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.TRANSPARENT);
        if (focused) g.setStroke(dp(3), Color.WHITE);
        return g;
    }

    private android.graphics.drawable.Drawable tinted(int res, int color) {
        android.graphics.drawable.Drawable dr = getResources().getDrawable(res, getTheme()).mutate();
        dr.setTint(color);
        dr.setBounds(0, 0, dp(22), dp(22));
        return dr;
    }

    private int dp(int v) { return Math.round(v * d); }

    @SuppressWarnings("unused")
    private static ColorDrawable none() { return new ColorDrawable(Color.TRANSPARENT); }
}
