package com.lttube.tv;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

/** Draws a card for a video, channel, playlist or action button. */
public class CardPresenter extends Presenter {
    private static final int W = 313, H = 176, CH = 176;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ImageCardView card = new ImageCardView(parent.getContext());
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setCardType(BaseCardView.CARD_TYPE_INFO_UNDER);
        card.setInfoVisibility(BaseCardView.CARD_REGION_VISIBLE_ALWAYS);
        card.setBackgroundColor(0xFF181818);
        card.setInfoAreaBackgroundColor(0xFF181818);
        card.getMainImageView().setScaleType(ImageView.ScaleType.CENTER_CROP);
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(ViewHolder vh, Object o) {
        Item it = (Item) o;
        ImageCardView card = (ImageCardView) vh.view;
        float d = card.getResources().getDisplayMetrics().density;
        int w = Math.round(W * d / 1.5f), h = Math.round(H * d / 1.5f);
        card.setTitleText(it.title);
        String dur = it.durationText();
        String content = it.sub;
        if (Item.VIDEO.equals(it.type)) {
            if (it.position > 0 && it.duration > 0) {
                long left = Math.max(0, it.duration - it.position / 1000);
                dur = dur + " • " + (left / 60) + " min left";
            }
            content = dur.isEmpty() ? it.sub : dur + "  •  " + it.sub;
        }
        card.setContentText(content);

        if (Item.ACTION.equals(it.type)) {
            card.setMainImageDimensions(w, h);
            card.getMainImageView().setImageDrawable(new ColorDrawable(it.color == 0 ? 0xFF303030 : it.color));
            return;
        }
        if (Item.CHANNEL.equals(it.type)) {
            int s = Math.round(CH * d / 1.5f);
            card.setMainImageDimensions(s, s);
            Glide.with(card.getContext()).load(it.thumb).apply(RequestOptions.circleCropTransform())
                    .placeholder(new ColorDrawable(0xFF303030)).into(card.getMainImageView());
            return;
        }
        card.setMainImageDimensions(w, h);
        Glide.with(card.getContext()).load(it.thumb).centerCrop()
                .placeholder(new ColorDrawable(0xFF262626)).error(new ColorDrawable(Color.DKGRAY))
                .into(card.getMainImageView());
    }

    @Override
    public void onUnbindViewHolder(ViewHolder vh) {
        ImageCardView card = (ImageCardView) vh.view;
        card.setBadgeImage(null);
        card.setMainImage(null);
    }
}
