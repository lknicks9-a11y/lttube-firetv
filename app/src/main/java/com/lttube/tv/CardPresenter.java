package com.lttube.tv;

import android.view.ViewGroup;

import androidx.leanback.widget.Presenter;

/** Shows Items as YouTube-style cards. */
public class CardPresenter extends Presenter {
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(new YtCard(parent.getContext()));
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
