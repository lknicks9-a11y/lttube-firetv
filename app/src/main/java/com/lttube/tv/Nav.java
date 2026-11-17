package com.lttube.tv;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/** Opening things. */
public final class Nav {
    /** Videos to play after the current one (a playlist or a row). */
    public static List<Item> queue = new ArrayList<>();

    public static void open(Context c, Item it, List<Item> context) {
        if (Item.VIDEO.equals(it.type)) {
            queue = new ArrayList<>();
            if (context != null) {
                boolean after = false;
                for (Item i : context) {
                    if (after && Item.VIDEO.equals(i.type)) queue.add(i);
                    if (i == it) after = true;
                }
            }
            play(c, it);
        } else if (Item.CHANNEL.equals(it.type)) {
            channel(c, it.url, it.title);
        } else if (Item.PLAYLIST.equals(it.type)) {
            Intent i = new Intent(c, GridActivity.class);
            i.putExtra(GridActivity.MODE, GridActivity.PLAYLIST);
            i.putExtra(GridActivity.URL, it.url);
            i.putExtra(GridActivity.TITLE, it.title);
            c.startActivity(i);
        }
    }

    public static void play(Context c, Item it) {
        Intent i = new Intent(c, PlayerActivity.class);
        i.putExtra(PlayerActivity.ITEM, it.toJson().toString());
        c.startActivity(i);
    }

    public static void channel(Context c, String url, String title) {
        if (url == null || url.isEmpty()) return;
        Intent i = new Intent(c, GridActivity.class);
        i.putExtra(GridActivity.MODE, GridActivity.CHANNEL);
        i.putExtra(GridActivity.URL, url);
        i.putExtra(GridActivity.TITLE, title);
        c.startActivity(i);
    }

    private Nav() { }
}
