package com.lttube.tv;

import android.view.ViewGroup;

import androidx.leanback.widget.Presenter;

/** Shows Items as YouTube-style cards. */
public class CardPresenter extends Presenter {
    private final int widthDp;

    public CardPresenter() { this(0); }

    /** Cards exactly this wide (for grids, so they fit their columns). 0 = normal size. */
    public CardPresenter(int widthDp) { this.widthDp = widthDp; }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        YtCard c = new YtCard(parent.getContext());
        c.fixedWidthDp = widthDp;
        return new ViewHolder(c);
    }

    @Override
    public void onBindViewHolder(ViewHolder vh, Object o) {
        ((YtCard) vh.view).bind((Item) o);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder vh) {
        ((YtCard) vh.view).unbind();
    }
}
