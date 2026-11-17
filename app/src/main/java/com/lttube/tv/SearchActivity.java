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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0F0F0F);
        root.setPadding(dp(48), dp(24), dp(24), 0);

        box = new EditText(this);
        box.setSingleLine(true);
        box.setHint("Search YouTube");
        box.setTextColor(Color.WHITE);
        box.setHintTextColor(0xFF888888);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF222222);
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(2), 0xFF444444);
        box.setBackground(bg);
        box.setOnFocusChangeListener((v, has) -> bg.setStroke(dp(2), has ? 0xFFFFFFFF : 0xFF444444));
        root.addView(box, new LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(0xFFAAAAAA);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(dp(8), dp(8), 0, 0);
        root.addView(status);

        FrameLayout holder = new FrameLayout(this);
        holder.setId(View.generateViewId());
        root.addView(holder, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        rows = new ArrayObjectAdapter(new ListRowPresenter());
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
                box.setText(it.title);
                box.setSelection(it.title.length());
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

        box.setOnEditorActionListener((v, actionId, ev) -> {
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

        showRecent();
        box.requestFocus();
        box.postDelayed(() -> {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT);
        }, 300);
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
                List<Item> s = new ArrayList<>();
                for (String x : list) if (s.size() < 10) s.add(Item.action("q", x, "Search", 0xFF303030));
                setSuggestions("Suggestions", s);
            });
        }, 300);
    }

    private void search(String q) {
        q = q.trim();
        if (q.isEmpty()) return;
        query = q;
        suggestSeq++;
        Store.searched(q);
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(box.getWindowToken(), 0);
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
