package com.lttube.tv;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.VerticalGridSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.VerticalGridPresenter;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs;

import java.util.ArrayList;
import java.util.List;

/** A channel (with Subscribe and tabs) or a playlist, as a grid of videos. */
public class GridActivity extends FragmentActivity {
    public static final String MODE = "mode", URL = "url", TITLE = "title";
    public static final String CHANNEL = "channel", PLAYLIST = "playlist";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state == null) {
            GridFragment f = new GridFragment();
            f.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content, f).commit();
        }
    }

    public static class GridFragment extends VerticalGridSupportFragment {
        private final ArrayObjectAdapter adapter = new ArrayObjectAdapter(new CardPresenter());
        private String mode, url, title;
        private String tab = ChannelTabs.VIDEOS;
        private ChannelInfo channel;
        private Page next;
        private boolean loading;
        private int seq;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle a = requireArguments();
            mode = a.getString(MODE, CHANNEL);
            url = a.getString(URL, "");
            title = a.getString(TITLE, "");
            setTitle(title);

            VerticalGridPresenter gp = new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false);
            gp.setNumberOfColumns(3);
            gp.setShadowEnabled(false);
            setGridPresenter(gp);
            setAdapter(adapter);

            setOnItemViewClickedListener((vh, o, rvh, row) -> {
                Item it = (Item) o;
                if (Item.ACTION.equals(it.type)) { onAction(it.action); return; }
                Nav.open(requireContext(), it, items());
            });
            setOnItemViewSelectedListener((vh, o, rvh, row) -> {
                if (o == null || next == null || loading) return;
                if (adapter.indexOf(o) >= adapter.size() - 10) loadMore();
            });
            load();
            // Your real subscription state from YouTube
            if (CHANNEL.equals(mode) && Account.signedIn()) {
                final String cid = Store.channelId(url);
                Bg.run(() -> Yti.channelSubscribed(cid), (sub, e) -> {
                    if (sub == null || !isAdded()) return;
                    if (sub && !Store.isSubscribed(url)) Store.subscribe(url, title, "");
                    if (!sub && Store.isSubscribed(url)) Store.unsubscribe(url);
                    if (adapter.size() > 0) adapter.replace(0, header().get(0));
                });
            }
        }

        private List<Item> items() {
            List<Item> l = new ArrayList<>();
            for (int i = 0; i < adapter.size(); i++) l.add((Item) adapter.get(i));
            return l;
        }

        private List<Item> header() {
            List<Item> h = new ArrayList<>();
            if (!CHANNEL.equals(mode)) return h;
            boolean subbed = Store.isSubscribed(url);
            h.add(Item.action("sub", subbed ? "Subscribed ✓" : "Subscribe",
                    subbed ? "Select to unsubscribe" : "Add to your subscriptions", subbed ? 0xFF303030 : 0xFFFF0033));
            h.add(tabCard(ChannelTabs.VIDEOS, "Videos"));
            h.add(tabCard(ChannelTabs.SHORTS, "Shorts"));
            h.add(tabCard(ChannelTabs.LIVESTREAMS, "Live"));
            h.add(tabCard(ChannelTabs.PLAYLISTS, "Playlists"));
            return h;
        }

        private Item tabCard(String id, String name) {
            boolean on = id.equals(tab);
            return Item.action("tab:" + id, name, on ? "Showing" : "", on ? 0xFF3EA6FF : 0xFF262626);
        }

        private void load() {
            final int s = ++seq;
            loading = true;
            next = null;
            adapter.clear();
            adapter.addAll(0, header());
            Bg.run(() -> CHANNEL.equals(mode) ? Feeds.channel(url, tab) : Feeds.playlist(url), (r, err) -> {
                if (s != seq || !isAdded()) return;
                loading = false;
                if (r == null) {
                    Toast.makeText(requireContext(), "Couldn't load: " + Bg.message(err), Toast.LENGTH_LONG).show();
                    return;
                }
                if (r.extra instanceof ChannelInfo) {
                    channel = (ChannelInfo) r.extra;
                    String t = channel.getName();
                    if (channel.getSubscriberCount() > 0) t += "  •  " + Item.count(channel.getSubscriberCount()) + " subscribers";
                    setTitle(t);
                    if (title.isEmpty()) title = channel.getName();
                } else if (!r.title.isEmpty()) {
                    setTitle(r.title);
                }
                next = r.next;
                adapter.addAll(adapter.size(), r.items);
                if (r.items.isEmpty()) Toast.makeText(requireContext(), "Nothing here", Toast.LENGTH_SHORT).show();
            });
        }

        private void loadMore() {
            loading = true;
            final int s = seq;
            final Page page = next;
            Bg.run(() -> CHANNEL.equals(mode) ? Feeds.channelMore(channel, tab, page) : Feeds.playlistMore(url, page), (r, err) -> {
                if (s != seq || !isAdded()) return;
                loading = false;
                if (r == null) return;
                next = r.next;
                adapter.addAll(adapter.size(), r.items);
            });
        }

        private void onAction(String a) {
            if ("sub".equals(a)) {
                final boolean on = !Store.isSubscribed(url);
                if (Account.signedIn()) {
                    final String cid = Store.channelId(url);
                    Bg.run(() -> { Yti.subscribe(cid, on); return true; }, (r, e) -> {
                        if (r == null && isAdded()) Toast.makeText(requireContext(), "YouTube didn't save that: " + Bg.message(e), Toast.LENGTH_LONG).show();
                    });
                }
                if (!on) {
                    Store.unsubscribe(url);
                    Toast.makeText(requireContext(), "Unsubscribed", Toast.LENGTH_SHORT).show();
                } else {
                    String thumb = channel != null ? Item.bestImage(channel.getAvatars(), 176) : "";
                    Store.subscribe(url, channel != null ? channel.getName() : title, thumb);
                    Toast.makeText(requireContext(), "Subscribed", Toast.LENGTH_SHORT).show();
                }
                adapter.replace(0, header().get(0));
            } else if (a.startsWith("tab:")) {
                String t = a.substring(4);
                if (t.equals(tab)) return;
                tab = t;
                load();
            }
        }
    }
}
