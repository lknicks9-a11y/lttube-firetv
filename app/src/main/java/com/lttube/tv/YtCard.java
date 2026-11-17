package com.lttube.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

/** A YouTube-TV style card: thumbnail with duration and progress, title, channel, views. */
public class YtCard extends LinearLayout {
    public static final int VIDEO_W = 268, VIDEO_H = 151, SHORT_W = 150, SHORT_H = 266, CHANNEL_W = 150, AVATAR = 116;

    final FrameLayout frame;
    final ImageView image;
    final ImageView icon;
    final TextView badge, title, line1, line2, overlay;
    final View progressTrack, progress;
    private final GradientDrawable focusRing;
    private boolean round;
    /** When set (grids), video cards use this width and keep 16:9. */
    public int fixedWidthDp;
    private final float d;

    public YtCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setPadding(0, 0, 0, dp(6));

        frame = new FrameLayout(c);
        frame.setBackgroundColor(0x33FFFFFF);
        frame.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                if (round) o.setOval(0, 0, v.getWidth(), v.getHeight());
                else o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 14 * d);
            }
        });
        frame.setClipToOutline(true);

        image = new ImageView(c);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));

        icon = new ImageView(c);
        icon.setColorFilter(Color.WHITE);
        frame.addView(icon, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        overlay = text(c, 13, Color.WHITE, true);
        overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0xB3000000);
        frame.addView(overlay, new FrameLayout.LayoutParams(-1, dp(30), Gravity.BOTTOM));

        badge = text(c, 12, Color.WHITE, true);
        badge.setPadding(dp(5), dp(1), dp(5), dp(1));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        bl.setMargins(0, 0, dp(6), dp(8));
        frame.addView(badge, bl);

        progressTrack = new View(c);
        progressTrack.setBackgroundColor(0x99FFFFFF);
        frame.addView(progressTrack, new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM));
        progress = new View(c);
        progress.setBackgroundColor(0xFFFF0033);
        frame.addView(progress, new FrameLayout.LayoutParams(0, dp(3), Gravity.BOTTOM | Gravity.START));

        focusRing = new GradientDrawable();
        focusRing.setColor(Color.TRANSPARENT);
        focusRing.setStroke(dp(3), Color.WHITE);
        focusRing.setCornerRadius(14 * d);
        addView(frame);

        title = text(c, 15, Color.WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(2), dp(8), dp(2), 0);
        addView(title, new LayoutParams(-1, -2));
        line1 = text(c, 13, 0xFFAAAAAA, false);
        line1.setSingleLine(true);
        line1.setEllipsize(TextUtils.TruncateAt.END);
        line1.setPadding(dp(2), dp(3), dp(2), 0);
        addView(line1, new LayoutParams(-1, -2));
        line2 = text(c, 13, 0xFFAAAAAA, false);
        line2.setSingleLine(true);
        line2.setEllipsize(TextUtils.TruncateAt.END);
        line2.setPadding(dp(2), dp(1), dp(2), 0);
        addView(line2, new LayoutParams(-1, -2));

        setOnFocusChangeListener((v, has) -> {
            frame.setForeground(has ? focusRing : null);
            frame.setElevation(has ? 18 * d : 0);
            frame.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(140).start();
            title.setTextColor(has ? Color.WHITE : 0xFFE6E6E6);
        });
    }

    public void bind(Item it) {
        boolean action = Item.ACTION.equals(it.type);
        boolean channel = Item.CHANNEL.equals(it.type);
        boolean shortV = Item.VIDEO.equals(it.type) && it.isShort;
        int w = channel ? CHANNEL_W : shortV ? SHORT_W : VIDEO_W;
        int fw = channel ? AVATAR : w, fh = channel ? AVATAR : shortV ? SHORT_H : VIDEO_H;
        if (fixedWidthDp > 0 && !channel) {
            w = fixedWidthDp;
            fw = shortV ? Math.min(fixedWidthDp, SHORT_W) : fixedWidthDp;
            fh = shortV ? fw * 16 / 9 : fixedWidthDp * 9 / 16;
        }
        round = channel;
        focusRing.setCornerRadius(channel ? fw * d : 14 * d);
        if (channel) focusRing.setShape(GradientDrawable.OVAL); else focusRing.setShape(GradientDrawable.RECTANGLE);
        ViewGroup.LayoutParams own = getLayoutParams();
        if (own == null) {
            setLayoutParams(new ViewGroup.LayoutParams(dp(w), ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            own.width = dp(w);
            own.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            setLayoutParams(own);
        }
        LayoutParams fl = new LayoutParams(dp(fw), dp(fh));
        fl.gravity = channel ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        frame.setLayoutParams(fl);
        frame.invalidateOutline();
        setGravity(channel ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        title.setGravity(channel ? Gravity.CENTER : Gravity.START);
        line1.setGravity(channel ? Gravity.CENTER : Gravity.START);

        title.setText(it.title);
        icon.setVisibility(GONE);
        overlay.setVisibility(GONE);
        badge.setVisibility(GONE);
        progress.setVisibility(GONE);
        progressTrack.setVisibility(GONE);

        if (action) {
            frame.setBackgroundColor(it.color == 0 ? 0xFF303030 : it.color);
            Glide.with(this).clear(image);
            image.setImageDrawable(null);
            if (it.icon != 0) { icon.setImageResource(it.icon); icon.setVisibility(VISIBLE); }
            line1.setText(it.sub);
            line2.setText("");
            line2.setVisibility(GONE);
            return;
        }
        frame.setBackgroundColor(0xFF272727);
        line2.setVisibility(channel ? GONE : VISIBLE);

        if (channel) {
            Glide.with(this).load(it.thumb).apply(RequestOptions.circleCropTransform())
                    .placeholder(new ColorDrawable(0xFF303030)).into(image);
            line1.setText(it.sub);
            return;
        }
        Glide.with(this).load(it.thumb).centerCrop().placeholder(new ColorDrawable(0xFF272727)).into(image);

        if (Item.PLAYLIST.equals(it.type)) {
            overlay.setText("▶  Playlist");
            overlay.setVisibility(VISIBLE);
            line1.setText(it.channel.isEmpty() ? "Playlist" : it.channel);
            line2.setText(it.sub);
            return;
        }

        // video
        String dur = it.durationText();
        if (it.live) {
            badge.setText("LIVE");
            badge.setBackground(pill(0xFFCC0000));
            badge.setVisibility(VISIBLE);
        } else if (!dur.isEmpty() && !shortV) {
            badge.setText(dur);
            badge.setBackground(pill(0xCC000000));
            badge.setVisibility(VISIBLE);
        }
        if (it.position > 0 && it.duration > 0) {
            progressTrack.setVisibility(VISIBLE);
            progress.setVisibility(VISIBLE);
            FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) progress.getLayoutParams();
            pl.width = (int) (dp(fw) * Math.min(1.0, it.position / 1000.0 / it.duration));
            progress.setLayoutParams(pl);
        }
        // line 1: channel; line 2: views • age (the rest of the subtitle)
        String rest = it.sub;
        if (!it.channel.isEmpty() && rest.startsWith(it.channel)) {
            rest = rest.substring(it.channel.length()).replaceFirst("^\\s*•\\s*", "");
        }
        if (shortV) {
            line1.setText(rest.isEmpty() ? "Short" : rest);
            line2.setVisibility(GONE);
        } else {
            line1.setText(it.channel.isEmpty() ? rest : it.channel);
            line2.setText(it.channel.isEmpty() ? "" : rest);
        }
    }

    public void unbind() {
        Glide.with(this).clear(image);
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(4 * d);
        return g;
    }

    private static TextView text(Context c, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private int dp(int v) { return Math.round(v * d); }
}
