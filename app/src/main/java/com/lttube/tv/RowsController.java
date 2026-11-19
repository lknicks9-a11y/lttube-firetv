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
    public interface Selected { void selected(Item item); }
    private Selected onSelected;

    public void onSelected(Selected s) { onSelected = s; }

    public RowsController(FragmentActivity act, int containerId, Click click) {
        ListRowPresenter rp = new ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false);
        rp.setShadowEnabled(false);
        rp.setSelectEffectEnabled(false);
        rp.setKeepChildForeground(true);
        rp.setHeaderPresenter(new LtHeaderPresenter());
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
            if (onSelected != null && o instanceof Item) onSelected.selected((Item) o);
            ListRow lr = (ListRow) row;
            Shelf sh = shelves.get(lr);
            if (sh != null && sh.pageCont != null) moreRows(lr, sh);
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
                // Shorts hidden (Settings): no Shorts rows, no Shorts in other rows.
                if (Store.hideShorts()) {
                    if (sh.title != null && sh.title.toLowerCase().contains("shorts")) continue;
                    java.util.Iterator<Item> it = sh.items.iterator();
                    while (it.hasNext()) if (it.next().isShort) it.remove();
                }
                if (sh.items.isEmpty()) continue;
                ArrayObjectAdapter a = new ArrayObjectAdapter(cards);
                a.addAll(0, sh.items);
                HeaderItem hi = new HeaderItem(sh.title);
                if (sh.subtitle != null && !sh.subtitle.isEmpty()) hi.setDescription(sh.subtitle);
                ListRow lr = new ListRow(hi, a);
                shelves.put(lr, sh);
                slots.get(slot).add(lr);
                rows.add(at++, lr);
            }
            if (keepTop) toTop();
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

    private final Set<String> pagesLoading = new HashSet<>();

    /** Adds YouTube's next shelves after this row. */
    private void moreRows(ListRow lr, Shelf sh) {
        final String c = sh.pageCont;
        if (c == null || !pagesLoading.add(c)) return;
        final int s = seq;
        Bg.run(() -> Yti.moreShelves(c, sh.pageTv), (list, err) -> {
            if (s != seq || list == null) return;
            sh.pageCont = null;
            int at = rows.indexOf(lr) + 1;
            List<ListRow> slot = null;
            for (List<ListRow> sl : slots) if (sl.contains(lr)) slot = sl;
            Set<String> seen = new HashSet<>();
            for (ListRow r : shelves.keySet()) for (Item i : shelves.get(r).items) seen.add(i.url);
            for (Shelf n : list) {
                List<Item> fresh = new ArrayList<>();
                for (Item i : n.items) if (seen.add(i.url)) fresh.add(i);
                if (fresh.isEmpty()) {
                    if (n.pageCont != null) { sh.pageCont = n.pageCont; sh.pageTv = n.pageTv; }
                    continue;
                }
                n.items.clear();
                n.items.addAll(fresh);
                ArrayObjectAdapter a = new ArrayObjectAdapter(cards);
                a.addAll(0, n.items);
                HeaderItem hi = new HeaderItem(n.title);
                if (n.subtitle != null && !n.subtitle.isEmpty()) hi.setDescription(n.subtitle);
                ListRow nr = new ListRow(hi, a);
                shelves.put(nr, n);
                if (slot != null) slot.add(nr);
                rows.add(at++, nr);
            }
        });
    }

    private static List<Item> list(ListRow lr) {
        List<Item> l = new ArrayList<>();
        ArrayObjectAdapter a = (ArrayObjectAdapter) lr.getAdapter();
        for (int i = 0; i < a.size(); i++) l.add((Item) a.get(i));
        return l;
    }

    public boolean isEmpty() { return rows.size() == 0; }

    /** Back to the first row: rows that arrive later are put above the one showing, which pushed it down. */
    public void toTop() {
        try {
            androidx.leanback.widget.VerticalGridView g = fragment.getVerticalGridView();
            if (g == null) return;
            g.setSelectedPosition(0);
            g.scrollToPosition(0);
            g.post(() -> { if (keepTop) { g.setSelectedPosition(0); g.scrollToPosition(0); } });
        } catch (Exception ignored) { }
    }

    /** While true, rows loading in keep the list at its first row (until you start moving). */
    public boolean keepTop;

    public void focus() {
        View v = fragment.getView();
        if (v != null) v.requestFocus();
    }

    public View view() { return fragment.getView(); }

    public void scrollToTop() {
        if (rows.size() > 0) fragment.setSelectedPosition(0, false);
    }
}
