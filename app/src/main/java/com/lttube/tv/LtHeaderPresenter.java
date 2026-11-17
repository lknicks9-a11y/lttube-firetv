package com.lttube.tv;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;

/** Row titles like YouTube TV: "Recommended" with a small line under it. */
public class LtHeaderPresenter extends RowHeaderPresenter {
    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        LinearLayout box = new LinearLayout(parent.getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(parent.getContext(), 6), 0, Ui.dp(parent.getContext(), 8));
        TextView t = Ui.text(parent.getContext(), 19, Color.WHITE, true);
        TextView s = Ui.text(parent.getContext(), 14, Ui.TEXT2, false);
        box.addView(t);
        box.addView(s);
        return new RowHeaderPresenter.ViewHolder(box);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder vh, Object item) {
        HeaderItem h = item == null ? null : ((Row) item).getHeaderItem();
        LinearLayout box = (LinearLayout) vh.view;
        TextView t = (TextView) box.getChildAt(0), s = (TextView) box.getChildAt(1);
        t.setText(h == null ? "" : h.getName());
        CharSequence d = h == null ? null : h.getDescription();
        s.setText(d == null ? "" : d);
        s.setVisibility(d == null || d.length() == 0 ? View.GONE : View.VISIBLE);
        box.setVisibility(h == null ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder vh) { }

    @Override
    protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder) { }
}
