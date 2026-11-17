package com.lttube.tv;

import org.schabi.newpipe.extractor.Page;

import java.util.ArrayList;
import java.util.List;

/** One row of cards, and how to get more of it. */
public class Shelf {
    public String title;
    public String subtitle = "";
    public final List<Item> items = new ArrayList<>();
    // more items: either a YouTube-account continuation…
    public String cont;
    // more rows after this one (YouTube home continues as you scroll down)
    public String pageCont;
    public boolean pageTv;
    public boolean contTv;
    public String only;
    // …or a public (NewPipe) page
    public Page next;
    public Feeds.More more;

    public Shelf(String title, List<Item> items) {
        this.title = title;
        if (items != null) this.items.addAll(items);
    }

    public boolean canLoadMore() {
        return cont != null || (next != null && more != null);
    }

    /** Loads the next page into a new Shelf (blocking). */
    public Shelf loadMore() throws Exception {
        Shelf s = new Shelf(title, null);
        if (cont != null) {
            Yti.Page pg = Yti.more(cont, contTv, only);
            s.items.addAll(pg.items);
            s.cont = pg.continuation;
        } else if (next != null && more != null) {
            Feeds.Result r = more.load(next);
            s.items.addAll(r.items);
            s.next = r.next;
        }
        return s;
    }

    public static Shelf of(String title, Feeds.Result r, Feeds.More more) {
        Shelf s = new Shelf(title, r.items);
        s.next = r.next;
        s.more = more;
        return s;
    }
}
