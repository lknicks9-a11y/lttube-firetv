package com.lttube.tv;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.ArrayList;
import java.util.List;

/** Home: the YouTube TV side menu, and rows / Subscriptions / Settings on the right. */
public class MainActivity extends FragmentActivity {

    private static final String[][] NAV = {
            {"search", "Search"}, {"home", "Home"}, {"shorts", "Shorts"}, {"subs", "Subscriptions"}, {"library", "Library"},
            {"-", ""},
            {"music", "Music"}, {"movies", "Movies & TV"}, {"live", "Live"}, {"gaming", "Gaming"}, {"news", "News"},
            {"sports", "Sports"}, {"podcasts", "Podcasts"},
            {"-", ""},
            {"settings", "Settings"}};
    private static final int[] NAV_ICONS = {R.drawable.ic_search, R.drawable.ic_home, R.drawable.ic_shorts,
            R.drawable.ic_subs, R.drawable.ic_library, 0, R.drawable.ic_music, R.drawable.ic_live, R.drawable.ic_live,
            R.drawable.ic_gaming, R.drawable.ic_info, R.drawable.ic_check, R.drawable.ic_comment, 0, R.drawable.ic_settings};

    /** Open menu width: just room for the names (YouTube's is narrow, no dead space). */
    private static final int NAV_OPEN = 236;
    private RowsController rows;
    private FrameLayout rowsHolder, paneHolder;
    private SubsPane subsPane;
    private SettingsPane settingsPane;
    private LinearLayout nav, navList, content;
    private final List<View> navItems = new ArrayList<>();
    private final List<String> navKeys = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private LinearLayout profileItem;
    private ImageView avatar;
    private TextView profileName;
    private TextView searchPill;
    private String section = "";
    private boolean navOpen;
    private ImageView ambient;
    private String ambientUrl = "";
    private final Handler timer = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        // liquid-glass backdrop: a soft blurred glow of the video you're on
        ambient = new ImageView(this);
        ambient.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ambient.setAlpha(0.55f);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        View veil = new View(this);
        veil.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xE614161E, 0xB314161E, 0xD914161E}));
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));

        // ---- content
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -1);
        cl.leftMargin = dp(88);
        root.addView(content, cl);

        searchPill = Ui.text(this, 18, Color.WHITE, true);
        searchPill.setText("Search");
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setPadding(dp(24), 0, dp(24), 0);
        searchPill.setFocusable(true);
        searchPill.setBackground(Ui.pill(this, Ui.SURFACE, 24));
        searchPill.setOnFocusChangeListener((v, has) -> {
            searchPill.setBackground(Ui.pill(this, has ? Ui.FOCUS : Ui.SURFACE, 24));
            searchPill.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
        });
        searchPill.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(520), dp(46));
        sl.setMargins(dp(26), dp(26), 0, dp(6));
        content.addView(searchPill, sl);

        FrameLayout stack = new FrameLayout(this);
        content.addView(stack, new LinearLayout.LayoutParams(-1, 0, 1f));
        rowsHolder = new FrameLayout(this);
        rowsHolder.setId(View.generateViewId());
        stack.addView(rowsHolder, new FrameLayout.LayoutParams(-1, -1));
        paneHolder = new FrameLayout(this);
        paneHolder.setVisibility(View.GONE);
        stack.addView(paneHolder, new FrameLayout.LayoutParams(-1, -1));

        // ---- side menu
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setBackgroundColor(Color.TRANSPARENT);
        nav.setPadding(dp(14), dp(26), dp(14), dp(14));
        root.addView(nav, new FrameLayout.LayoutParams(dp(88), -1));

        profileItem = navRow(0, "");
        avatar = (ImageView) profileItem.getChildAt(0);
        profileName = (TextView) profileItem.getChildAt(1);
        profileItem.setOnClickListener(v -> openProfiles());
        nav.addView(profileItem, rowLp());

        ScrollView navScroll = new ScrollView(this);
        navScroll.setVerticalScrollBarEnabled(false);
        navList = new LinearLayout(this);
        navList.setOrientation(LinearLayout.VERTICAL);
        navScroll.addView(navList);
        nav.addView(navScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        for (int i = 0; i < NAV.length; i++) {
            final String key = NAV[i][0];
            if ("-".equals(key)) {
                View line = new View(this);
                line.setBackgroundColor(0x33FFFFFF);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, dp(1));
                ll.setMargins(dp(16), dp(10), dp(16), dp(12));
                navList.addView(line, ll);
                continue;
            }
            LinearLayout item = navRow(NAV_ICONS[i], NAV[i][1]);
            item.setOnClickListener(v -> {
                if ("search".equals(key)) { startActivity(new Intent(this, SearchActivity.class)); return; }
                show(key, true);
            });
            navList.addView(item, rowLp());
            navItems.add(item);
            navKeys.add(key);
        }
        setContentView(root);

        rows = new RowsController(this, rowsHolder.getId(), (it, row) -> {
            if (Item.ACTION.equals(it.type)) { onAction(it.action); return; }
            Nav.open(this, it, row);
        });
        rows.onSelected(this::setAmbient);
        loadProfile();
        show("home", false);
        updateLoop.run();
        CrashLog.showIfAny(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfile();
        if ("library".equals(section)) show(section, false);
        if (settingsPane != null && "settings".equals(section)) settingsPane.refresh();
    }

    // ------------------------------------------------------------------ side menu
    private LinearLayout navRow(int icon, String label) {
        final LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setFocusable(true);
        item.setPadding(dp(16), 0, dp(12), 0);
        ImageView ic = new ImageView(this);
        if (icon != 0) { ic.setImageResource(icon); ic.setColorFilter(Color.WHITE); }
        item.addView(ic, new LinearLayout.LayoutParams(icon != 0 ? dp(26) : dp(34), icon != 0 ? dp(26) : dp(34)));
        TextView lab = Ui.text(this, 17, Color.WHITE, false);
        lab.setText(label);
        lab.setSingleLine(true);
        lab.setEllipsize(android.text.TextUtils.TruncateAt.END);
        lab.setPadding(dp(16), 0, 0, 0);
        lab.setVisibility(View.GONE);
        item.addView(lab);
        navLabels.add(lab);
        item.setOnFocusChangeListener((v, has) -> {
            styleNav();
            if (has) openNav(true); else nav.post(this::maybeCloseNav);
        });
        item.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT) { focusContent(); return true; }
            return false;
        });
        return item;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.bottomMargin = dp(6);
        return lp;
    }

    private void openNav(boolean open) {
        if (navOpen == open) return;
        navOpen = open;
        ViewGroup.LayoutParams lp = nav.getLayoutParams();
        lp.width = dp(open ? NAV_OPEN : 88);
        nav.setLayoutParams(lp);
        // Like YouTube on TV: the open menu darkens the left of the screen (no box), rounded pills inside.
        nav.setBackground(open ? new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFA0F0F12, 0xF20F0F12, 0xE60F0F12}) : null);
        nav.setElevation(open ? dp(12) : 0);
        // Like YouTube on TV: opening the menu slides the videos over to the right instead of covering them.
        content.animate().translationX(open ? dp(NAV_OPEN - 88) : 0).setDuration(180).start();
        for (TextView t : navLabels) t.setVisibility(open ? View.VISIBLE : View.GONE);
        styleNav();
    }

    private void maybeCloseNav() {
        View f = getCurrentFocus();
        if (f == null || !(navItems.contains(f) || f == profileItem)) openNav(false);
    }

    private void styleNav() {
        for (int i = 0; i < navItems.size(); i++) {
            View item = navItems.get(i);
            boolean focused = item.isFocused();
            boolean current = navKeys.get(i).equals(section);
            item.setBackground(focused ? roundPill(0xFFF1F1F1) : current ? roundPill(0x33FFFFFF) : null);
            ImageView ic = (ImageView) ((LinearLayout) item).getChildAt(0);
            TextView lab = (TextView) ((LinearLayout) item).getChildAt(1);
            ic.setColorFilter(focused ? 0xFF0F0F0F : current || navOpen ? Color.WHITE : 0xFFB0B4BE);
            lab.setTextColor(focused ? 0xFF0F0F0F : Color.WHITE);
            lab.setTypeface(lab.getTypeface(), focused || current ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        boolean pf = profileItem.isFocused();
        profileItem.setBackground(pf ? roundPill(0xFFF1F1F1) : null);
        profileName.setTextColor(pf ? 0xFF0F0F0F : Color.WHITE);
    }

    /** A fully rounded pill, like YouTube's menu on TV. */
    private android.graphics.drawable.GradientDrawable roundPill(int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(26));
        g.setColor(color);
        return g;
    }

    private void focusContent() {
        if (paneHolder.getVisibility() == View.VISIBLE) {
            if (subsPane != null && "subs".equals(section)) subsPane.focus();
            else if (settingsPane != null && "settings".equals(section)) settingsPane.focus();
        } else if (!rows.isEmpty()) {
            rows.focus();
        }
    }

    /** Tints the background with a heavily blurred copy of the selected video's thumbnail. */
    void setAmbient(Item it) {
        if (it == null || it.thumb == null || it.thumb.isEmpty() || it.thumb.equals(ambientUrl)) return;
        ambientUrl = it.thumb;
        Glide.with(this).load(it.thumb).override(24, 14).centerCrop()
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(450))
                .into(ambient);
    }

    private void loadProfile() {
        if (Account.signedIn()) {
            profileName.setText(Account.name().isEmpty() ? "You" : Account.name());
            avatar.setColorFilter(null);
            if (!Account.photo().isEmpty()) {
                Glide.with(this).load(Account.photo()).apply(RequestOptions.circleCropTransform()).into(avatar);
            } else {
                avatar.setImageResource(R.drawable.ic_account);
                avatar.setColorFilter(Color.WHITE);
                Bg.run(() -> { Yti.loadProfile(); return true; }, (r, e) -> { if (!Account.photo().isEmpty()) loadProfile(); });
            }
        } else {
            profileName.setText("Guest • Sign in");
            avatar.setImageResource(R.drawable.ic_account);
            avatar.setColorFilter(Color.WHITE);
        }
    }

    private void openProfiles() {
        Intent i = new Intent(this, WelcomeActivity.class);
        if (Account.profiles().isEmpty()) i.putExtra(WelcomeActivity.MODE, WelcomeActivity.SIGN_IN);
        startActivity(i);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() != KeyEvent.ACTION_UP) return true;
            View f = getCurrentFocus();
            boolean inNav = f != null && (navItems.contains(f) || f == profileItem);
            if (!inNav) {
                int idx = navKeys.indexOf(section);
                navItems.get(Math.max(0, idx)).requestFocus();
                return true;
            }
            if (!"home".equals(section)) { show("home", false); navItems.get(navKeys.indexOf("home")).requestFocus(); return true; }
            finish();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------------ sections
    void show(String key, boolean focusContent) {
        section = key;
        styleNav();
        boolean pane = "subs".equals(key) || "settings".equals(key);
        paneHolder.setVisibility(pane ? View.VISIBLE : View.GONE);
        rowsHolder.setVisibility(pane ? View.GONE : View.VISIBLE);
        searchPill.setVisibility("home".equals(key) ? View.VISIBLE : View.GONE);
        rows.clear();
        if (pane) {
            paneHolder.removeAllViews();
            if ("subs".equals(key)) {
                subsPane = new SubsPane(this);
                paneHolder.addView(subsPane.view, new FrameLayout.LayoutParams(-1, -1));
                if (focusContent) subsPane.focusWhenReady();
            } else {
                settingsPane = new SettingsPane(this);
                paneHolder.addView(settingsPane.view, new FrameLayout.LayoutParams(-1, -1));
                if (focusContent) settingsPane.focus();
            }
            return;
        }
        if (focusContent) rows.onFirstRow(() -> rows.focus());
        else rows.onFirstRow(() -> { View f = getCurrentFocus(); if (f == null) rows.focus(); });
        boolean in = Account.signedIn();
        switch (key) {
            case "home":
                // Signed in: exactly YouTube's own Home, shelf after shelf as you scroll (no extra rows).
                if (in) {
                    rows.add(() -> {
                        List<Shelf> l = Yti.sections(Yti.HOME, "Recommended");
                        if (!l.isEmpty() && "Recommended".equals(l.get(0).title) && l.get(0).subtitle.isEmpty())
                            l.get(0).subtitle = "Based on your Subscriptions and History";
                        return l;
                    });
                } else {
                    rows.add(Sources::recommended);
                    rows.add(() -> Sources.kiosk("trending_music", "Trending music"));
                    rows.add(() -> Sources.kiosk("trending_gaming", "Trending in gaming"));
                    rows.add(() -> Sources.kiosk("live", "Live now"));
                    rows.add(() -> Sources.kiosk("trending_podcasts_episodes", "Podcasts"));
                    rows.addNow(signInShelf());
                }
                break;
            case "shorts":
                if (in) rows.add(Sources::accountShorts);
                rows.add(() -> Sources.searchShorts("#shorts", "Trending Shorts"));
                rows.add(() -> Sources.searchShorts("#shorts funny", "Comedy"));
                rows.add(() -> Sources.searchShorts("#shorts sports", "Sports"));
                rows.add(() -> Sources.searchShorts("#shorts gaming", "Gaming"));
                rows.add(() -> Sources.searchShorts("#shorts music", "Music"));
                break;
            case "library":
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
                break;
            case "movies":
                rows.add(() -> Sources.kiosk("trending_movies_and_shows", "Trending movies & shows"));
                rows.add(() -> Sources.search("official trailer 2026", "New trailers"));
                rows.add(() -> Sources.search("full movie free", "Free movies"));
                break;
            case "live":
                rows.add(() -> Sources.kiosk("live", "Live now"));
                rows.add(() -> Sources.search("live stream now", "Streaming now"));
                break;
            case "gaming":
                rows.add(() -> Sources.kiosk("trending_gaming", "Trending in gaming"));
                rows.add(() -> Sources.search("gaming highlights", "Highlights"));
                rows.add(() -> Sources.search("new game trailer", "Game trailers"));
                break;
            case "news":
                rows.add(() -> Sources.search("news today", "Top news"));
                rows.add(() -> Sources.search("breaking news live", "Live news"));
                rows.add(() -> Sources.search("world news", "World"));
                break;
            case "sports":
                rows.add(() -> Sources.search("nba highlights", "NBA"));
                rows.add(() -> Sources.search("nfl highlights", "NFL"));
                rows.add(() -> Sources.search("college football highlights", "College football"));
                rows.add(() -> Sources.search("sports highlights today", "Top highlights"));
                break;
            case "podcasts":
                rows.add(() -> Sources.kiosk("trending_podcasts_episodes", "Trending podcasts"));
                rows.add(() -> Sources.search("podcast full episode", "Full episodes"));
                break;
            default:
                break;
        }
    }

    private Shelf signInShelf() {
        List<Item> l = new ArrayList<>();
        l.add(Item.action("signin", "Sign in to YouTube", "Your home, subscriptions, history and playlists", Ui.RED, R.drawable.ic_account));
        l.add(Item.action("import", "Import subscriptions", "One time, from LTTube on your PC", 0xFF3A3A3A, R.drawable.ic_subs));
        return new Shelf("Make it yours", l);
    }

    void onAction(String a) {
        switch (a) {
            case "account": openProfiles(); break;
            case "signin": {
                Intent i = new Intent(this, WelcomeActivity.class);
                i.putExtra(WelcomeActivity.MODE, WelcomeActivity.SIGN_IN);
                startActivity(i);
                break;
            }
            case "check": AccountCheck.run(this); break;
            case "import": new Importer(this, added -> show(section, false)).start(); break;
            case "update":
                if (Updater.updateReady(this)) Updater.offer(this, Updater.latest);
                else Updater.check(this, true, () -> { if (settingsPane != null && "settings".equals(section)) settingsPane.refresh(); });
                break;
            default: break;
        }
    }

    // Update Center: check now and every 20 minutes, like LTTV
    private final Runnable updateLoop = new Runnable() {
        @Override public void run() {
            Updater.check(MainActivity.this, false, null);
            timer.postDelayed(this, Updater.EVERY);
        }
    };

    @Override
    protected void onDestroy() {
        timer.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    Drawable icon(int res, int color, int sizeDp) {
        Drawable dr = getResources().getDrawable(res, getTheme()).mutate();
        dr.setTint(color);
        dr.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return dr;
    }

    int dp(float v) { return Ui.dp(this, v); }
}
