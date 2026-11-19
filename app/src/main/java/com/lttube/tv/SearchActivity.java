package com.lttube.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;

import org.schabi.newpipe.extractor.Page;

import java.util.ArrayList;
import java.util.List;

/** Search box with suggestions, and results in rows (videos, channels, playlists). */
public class SearchActivity extends FragmentActivity {

    private EditText box;
    private TextView status;
    private RowsSupportFragment rowsFragment;
    private ArrayObjectAdapter rows;
    private final CardPresenter cards = new CardPresenter();
    private final ArrayObjectAdapter suggestRow = new ArrayObjectAdapter(cards);
    private final ArrayObjectAdapter videos = new ArrayObjectAdapter(cards);
    private final ArrayObjectAdapter channels = new ArrayObjectAdapter(cards);
    private final ArrayObjectAdapter playlists = new ArrayObjectAdapter(cards);
    private ListRow suggestListRow, videoRow, channelRow, playlistRow;
    private String query = "";
    private Page next;
    private boolean loadingMore;
    private int searchSeq;
    private int suggestSeq;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Like YouTube on TV: the search bar on top, an on-screen keyboard under it (no Fire TV keyboard),
        // suggestions beside it, and videos below.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF26272D, 0xFF1C1D22}));
        root.setPadding(dp(48), dp(26), dp(24), 0);

        box = new EditText(this); // kept for the typed text; never shown
        box.setVisibility(View.GONE);
        root.addView(box);

        pill = new TextView(this);
        pill.setTextColor(0xFFB8BAC2);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        pill.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        pill.setText("Search");
        pill.setSingleLine(true);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(22), 0, dp(22), 0);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xFF4A4C55);
        pb.setCornerRadius(dp(26));
        pill.setBackground(pb);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(560), dp(44));
        pl.leftMargin = dp(90);
        root.addView(pill, pl);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setPadding(0, dp(14), 0, dp(6));
        suggestList = new LinearLayout(this);
        suggestList.setOrientation(LinearLayout.VERTICAL);
        mid.addView(suggestList, new LinearLayout.LayoutParams(dp(330), -2));
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        mid.addView(keyboard);
        root.addView(mid);
        midArea = mid;
        buildKeys();

        status = new TextView(this);
        status.setTextColor(0xFFAAAAAA);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setPadding(dp(8), 0, 0, 0);
        root.addView(status);

        FrameLayout holder = new FrameLayout(this);
        holder.setId(View.generateViewId());
        root.addView(holder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        ListRowPresenter rp = new ListRowPresenter(androidx.leanback.widget.FocusHighlight.ZOOM_FACTOR_NONE, false);
        rp.setShadowEnabled(false);
        rp.setSelectEffectEnabled(false);
        rows = new ArrayObjectAdapter(rp);
        suggestListRow = new ListRow(new HeaderItem("Recent searches"), suggestRow);
        videoRow = new ListRow(new HeaderItem("Videos"), videos);
        channelRow = new ListRow(new HeaderItem("Channels"), channels);
        playlistRow = new ListRow(new HeaderItem("Playlists"), playlists);

        rowsFragment = new RowsSupportFragment();
        rowsFragment.setAdapter(rows);
        getSupportFragmentManager().beginTransaction().replace(holder.getId(), rowsFragment).commit();

        rowsFragment.setOnItemViewClickedListener((vh, o, rvh, row) -> {
            Item it = (Item) o;
            if (Item.ACTION.equals(it.type)) {
                setTyped(it.title);
                search(it.title);
                return;
            }
            List<Item> ctx = new ArrayList<>();
            if (row == videoRow) for (int i = 0; i < videos.size(); i++) ctx.add((Item) videos.get(i));
            Nav.open(this, it, ctx);
        });
        rowsFragment.setOnItemViewSelectedListener((vh, o, rvh, row) -> {
            if (row != videoRow || o == null || next == null || loadingMore) return;
            if (videos.indexOf(o) >= videos.size() - 6) loadMore();
        });

        if (false) box.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                search(box.getText().toString());
                return true;
            }
            return false;
        });
        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { suggest(s.toString()); }
        });

        // On the videos: the keyboard folds away so whole rows fit on screen (nothing cut off).
        // Up from the top row brings it back.
        root.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (now == null || rowsFragment.getView() == null) return;
            boolean inRows = isInside(now, rowsFragment.getView());
            midArea.setVisibility(inRows ? View.GONE : View.VISIBLE);
        });
        showRecent();
        startRow();
        if (keyboard.getChildCount() > 0) ((ViewGroup) keyboard.getChildAt(0)).getChildAt(0).requestFocus();
    }

    // ------------------------------------------------------------------ on-screen keyboard
    private TextView pill;
    private View midArea;
    private LinearLayout keyboard, suggestList;
    private boolean symbols;

    private void buildKeys() {
        keyboard.removeAllViews();
        String[][] rowsK = symbols
                ? new String[][]{{"1", "2", "3", "4", "5", "6", "7", "⌫"}, {"8", "9", "0", "&", "#", "@", "!", "ABC"}, {"?", ",", ".", ":", "(", ")", "+", "$"}, {"/", "%", "\"", "_", "=", "-", "'"}}
                : new String[][]{{"A", "B", "C", "D", "E", "F", "G", "⌫"}, {"H", "I", "J", "K", "L", "M", "N", "&123"}, {"O", "P", "Q", "R", "S", "T", "U", "⌫"}, {"V", "W", "X", "Y", "Z", "-", "'"}};
        if (!symbols) rowsK[2][7] = "";
        for (String[] r : rowsK) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : r) {
                if (k.isEmpty()) continue;
                row.addView(key(k, k.length() > 1 && !k.equals("⌫") ? dp(64) : dp(46)), new LinearLayout.LayoutParams(-2, dp(42)));
            }
            keyboard.addView(row);
        }
        LinearLayout last = new LinearLayout(this);
        last.setOrientation(LinearLayout.HORIZONTAL);
        last.setPadding(0, dp(8), 0, 0);
        for (String k : new String[]{"SPACE", "CLEAR", "SEARCH"}) {
            TextView b = key(k, dp(96));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.rightMargin = dp(10);
            last.addView(b, lp);
        }
        keyboard.addView(last);
    }

    private TextView key(final String k, int width) {
        final TextView t = new TextView(this);
        t.setText(k);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        boolean big = k.length() > 1 && !k.equals("⌫");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, big ? 14 : 20);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setMinWidth(width);
        t.setFocusable(true);
        final boolean button = k.equals("SPACE") || k.equals("CLEAR") || k.equals("SEARCH");
        final Runnable paint = () -> {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(t.isFocused() ? 0xFFF1F1F1 : button ? 0xFF4A4C55 : 0x00000000);
            t.setBackground(g);
            t.setTextColor(t.isFocused() ? 0xFF0F0F0F : Color.WHITE);
        };
        paint.run();
        t.setOnFocusChangeListener((v, has) -> paint.run());
        t.setOnClickListener(v -> press(k));
        return t;
    }

    private void press(String k) {
        String cur = box.getText().toString();
        switch (k) {
            case "⌫": if (!cur.isEmpty()) setTyped(cur.substring(0, cur.length() - 1)); return;
            case "&123": symbols = true; buildKeys(); ((ViewGroup) keyboard.getChildAt(1)).getChildAt(7).requestFocus(); return;
            case "ABC": symbols = false; buildKeys(); ((ViewGroup) keyboard.getChildAt(1)).getChildAt(7).requestFocus(); return;
            case "SPACE": setTyped(cur + " "); return;
            case "CLEAR": setTyped(""); return;
            case "SEARCH": search(cur); return;
            default: setTyped(cur + (symbols ? k : k.toLowerCase()));
        }
    }

    private void setTyped(String s) {
        box.setText(s);
        pill.setText(s.isEmpty() ? "Search" : s);
        pill.setTextColor(s.isEmpty() ? 0xFFB8BAC2 : Color.WHITE);
    }

    /** Suggestions as a list beside the keyboard (like YouTube); choosing one searches it. */
    private void showSuggestList(List<String> list) {
        suggestList.removeAllViews();
        for (final String q : list) {
            if (suggestList.getChildCount() >= 6) break;
            final TextView t = new TextView(this);
            t.setText(q);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            t.setPadding(dp(16), dp(7), dp(16), dp(7));
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(20));
                g.setColor(has ? 0xFFF1F1F1 : 0x00000000);
                t.setBackground(g);
                t.setTextColor(has ? 0xFF0F0F0F : Color.WHITE);
            });
            t.setOnClickListener(v -> { setTyped(q); search(q); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.rightMargin = dp(24);
            suggestList.addView(t, lp);
        }
    }

    /** Before you type: videos from your recent searches (or what's trending), like YouTube's "From recommended searches". */
    private void startRow() {
        final List<String> recent = Store.searches();
        final String q = recent.isEmpty() ? "" : recent.get(0);
        Bg.run(() -> q.isEmpty() ? Feeds.search("trending today") : Feeds.search(q), (r, err) -> {
            if (r == null || !query.isEmpty()) return;
            List<Item> v = new ArrayList<>();
            for (Item i : r.items) if (Item.VIDEO.equals(i.type)) v.add(i);
            videos.clear();
            videos.addAll(0, v);
            videoRow = new ListRow(new HeaderItem(q.isEmpty() ? "Trending" : "From recommended searches"), videos);
            rows.add(videoRow);
        });
    }

    private static boolean isInside(View v, View parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) if (p == parent) return true;
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && e.getAction() == KeyEvent.ACTION_DOWN && rowsFragment.getView() != null) {
            View before = getCurrentFocus();
            if (before != null && isInside(before, rowsFragment.getView())) {
                boolean r = super.dispatchKeyEvent(e);
                View after = getCurrentFocus();
                if (after == before || after == null || !isInside(after, rowsFragment.getView())) {
                    midArea.setVisibility(View.VISIBLE);
                    ViewGroup lastRow = (ViewGroup) keyboard.getChildAt(keyboard.getChildCount() - 1);
                    lastRow.getChildAt(lastRow.getChildCount() - 1).requestFocus();
                    return true;
                }
                return r;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void showRecent() {
        List<Item> recent = new ArrayList<>();
        for (String q : Store.searches()) recent.add(Item.action("q", q, "Recent search", 0xFF303030));
        setSuggestions("Recent searches", recent);
    }

    private void setSuggestions(String title, List<Item> list) {
        suggestRow.clear();
        suggestRow.addAll(0, list);
        int idx = rows.indexOf(suggestListRow);
        if (idx >= 0) rows.removeItems(idx, 1);
        if (!list.isEmpty()) {
            suggestListRow = new ListRow(new HeaderItem(title), suggestRow);
            rows.add(0, suggestListRow);
        }
    }

    private void suggest(final String q) {
        final int seq = ++suggestSeq;
        if (q.trim().isEmpty()) { showRecent(); return; }
        Bg.UI.postDelayed(() -> {
            if (seq != suggestSeq) return;
            Bg.run(() -> Feeds.suggestions(q), (list, err) -> {
                if (seq != suggestSeq || list == null) return;
                showSuggestList(list);
            });
        }, 300);
    }

    private void search(String q) {
        q = q.trim();
        if (q.isEmpty()) return;
        query = q;
        suggestSeq++;
        Store.searched(q);
        status.setText("Searching for \"" + q + "\"…");
        final int seq = ++searchSeq;
        final String fq = q;
        Bg.run(() -> Feeds.search(fq), (r, err) -> {
            if (seq != searchSeq) return;
            if (r == null) { status.setText("Search failed: " + Bg.message(err)); return; }
            status.setText("");
            List<Item> v = new ArrayList<>(), c = new ArrayList<>(), p = new ArrayList<>();
            for (Item i : r.items) {
                if (Item.CHANNEL.equals(i.type)) c.add(i);
                else if (Item.PLAYLIST.equals(i.type)) p.add(i);
                else v.add(i);
            }
            next = r.next;
            rows.clear();
            videos.clear(); videos.addAll(0, v);
            channels.clear(); channels.addAll(0, c);
            playlists.clear(); playlists.addAll(0, p);
            if (!c.isEmpty()) rows.add(channelRow);
            if (!v.isEmpty()) rows.add(videoRow);
            if (!p.isEmpty()) rows.add(playlistRow);
            if (rows.size() == 0) status.setText("No results for \"" + fq + "\"");
            else rowsFragment.getView().post(() -> {
                if (rowsFragment.getView() != null) rowsFragment.getView().requestFocus();
            });
        });
    }

    private void loadMore() {
        loadingMore = true;
        final String q = query;
        final Page page = next;
        Bg.run(() -> Feeds.searchMore(q, page), (r, err) -> {
            loadingMore = false;
            if (r == null || !q.equals(query)) return;
            next = r.next;
            for (Item i : r.items) {
                if (Item.CHANNEL.equals(i.type)) channels.add(i);
                else if (Item.PLAYLIST.equals(i.type)) playlists.add(i);
                else videos.add(i);
            }
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
