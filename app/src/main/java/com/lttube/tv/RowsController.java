package com.lttube.tv;

import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rows of cards that load in the background, keep their order, and load more
 * when you scroll near the end of a row.
 */
public class RowsController {
    public interface Source { List<Shelf> load() throws Exception; }
    public interface Click { void clicked(Item item, List<Item> row); }

    private final RowsSupportFragment fragment = new RowsSupportFragment();
    private final ArrayObjectAdapter rows;
    private final CardPresenter cards = new CardPresenter();
    private final List<List<ListRow>> slots = new ArrayList<>();
    private final Map<ListRow, Shelf> shelves = new IdentityHashMap<>();
    private final Set<ListRow> loading = new HashSet<>();
    private int seq;
    private Runnable onFirstRow;

    public RowsController(FragmentActivity act, int containerId, Click click) {
        ListRowPresenter rp = new ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false);
        rp.setShadowEnabled(false);
        rp.setSelectEffectEnabled(false);
        rp.setKeepChildForeground(true);
        rows = new ArrayObjectAdapter(rp);
        fragment.setAdapter(rows);
        act.getSupportFragmentManager().beginTransaction().replace(containerId, fragment).commitNow();

        fragment.setOnItemViewClickedListener((vh, o, rvh, row) -> {
            if (!(o instanceof Item)) return;
            Shelf sh = shelves.get(row);
            click.clicked((Item) o, sh == null ? null : list((ListRow) row));
        });
        fragment.setOnItemViewSelectedListener((vh, o, rvh, row) -> {
            if (!(row instanceof ListRow) || o == null) return;
            ListRow lr = (ListRow) row;
            Shelf sh = shelves.get(lr);
            if (sh == null || !sh.canLoadMore() || loading.contains(lr)) return;
            ArrayObjectAdapter a = (ArrayObjectAdapter) lr.getAdapter();
            if (a.indexOf(o) >= a.size() - 6) more(lr, sh);
        });
    }

    /** Starts over with no rows. */
    public void clear() {
        seq++;
        slots.clear();
        shelves.clear();
        loading.clear();
        rows.clear();
    }

    /** Called once when the first row appears after clear(). */
    public void onFirstRow(Runnable r) { onFirstRow = r; }

    /** Adds rows from a source; they appear in the order sources were added. */
    public void add(Source src) {
        final int s = seq;
        final int slot = slots.size();
        slots.add(new ArrayList<>());
        Bg.run(src::load, (list, err) -> {
            if (s != seq || list == null) return;
            int at = 0;
            for (int i = 0; i < slot; i++) at += slots.get(i).size();
            boolean wasEmpty = rows.size() == 0;
            for (Shelf sh : list) {
                if (sh.items.isEmpty()) continue;
                ArrayObjectAdapter a = new ArrayObjectAdapter(cards);
                a.addAll(0, sh.items);
                ListRow lr = new ListRow(new HeaderItem(sh.title), a);
                shelves.put(lr, sh);
                slots.get(slot).add(lr);
                rows.add(at++, lr);
            }
            if (wasEmpty && rows.size() > 0 && onFirstRow != null) {
                Runnable r = onFirstRow;
                onFirstRow = null;
                r.run();
            }
        });
    }

    /** Adds rows that are already known. */
    public void addNow(Shelf... list) {
        List<Shelf> l = new ArrayList<>();
        for (Shelf sh : list) l.add(sh);
        add(() -> l);
    }

    private void more(ListRow lr, Shelf sh) {
        final int s = seq;
        loading.add(lr);
        Bg.run(sh::loadMore, (next, err) -> {
            loading.remove(lr);
            if (s != seq || next == null) return;
            sh.cont = next.cont;
            sh.next = next.next;
            ArrayObjectAdapter a = (ArrayObjectAdapter) lr.getAdapter();
            Set<String> have = new HashSet<>();
            for (int i = 0; i < a.size(); i++) have.add(((Item) a.get(i)).url);
            for (Item it : next.items) if (have.add(it.url)) { a.add(it); sh.items.add(it); }
        });
    }

    private static List<Item> list(ListRow lr) {
        List<Item> l = new ArrayList<>();
        ArrayObjectAdapter a = (ArrayObjectAdapter) lr.getAdapter();
        for (int i = 0; i < a.size(); i++) l.add((Item) a.get(i));
        return l;
    }

    public boolean isEmpty() { return rows.size() == 0; }

    public void focus() {
        View v = fragment.getView();
        if (v != null) v.requestFocus();
    }

    public View view() { return fragment.getView(); }

    public void scrollToTop() {
        if (rows.size() > 0) fragment.setSelectedPosition(0, false);
    }
}
