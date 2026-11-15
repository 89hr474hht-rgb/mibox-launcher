package com.gathin.launchos.tv;

import android.content.Context;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.launchos.design.Focus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TV, En direct: three panels side by side. The shelves (Favoris, Récentes, the themes, other countries, see
 * {@link TvShelves}; the panel's own groups one step further), the chosen shelf's channels with what is on now and
 * the versions each comes in, and the focused channel's details above the small player's slot. Guide, next to En
 * direct, opens the guide. Left and Right move from panel to panel, Up and Down stay inside one. Main thread only; the list
 * comes from {@link Library}, the programmes from {@link Epg}, and playing goes to the {@link LivePlayer}.
 */
final class LivePage {
    private static final long GROUP_DELAY_MS = 250;
    private static final int FAVOURITES = 12;

    /**
     * SHELF: one of TV's shelves (Favoris, Récentes, a theme, a country). PROVIDERS opens the panel's own groups, BACK
     * comes back to the shelves; there, ALL and GROUP are the panel's lists as they were.
     */
    private enum Kind { SHELF, PROVIDERS, BACK, ALL, GROUP }

    private static final class Group {
        final Kind kind;
        final Xtream.Category category;
        final TvShelves.Shelf shelf;
        final String name;
        List<Item> channels;
        /** For a shelf: every version of each channel shown, by the shown one's id. */
        Map<String, List<Item>> versions = new HashMap<>();
        /** For PROVIDERS: how many groups it opens. */
        int count;

        Group(Kind kind, Xtream.Category category, String name, List<Item> channels) {
            this.kind = kind;
            this.category = category;
            this.shelf = null;
            this.name = name;
            this.channels = channels;
        }

        Group(TvShelves.Shelf shelf, String name) {
            this.kind = Kind.SHELF;
            this.category = null;
            this.shelf = shelf;
            this.name = name;
            this.channels = new ArrayList<>();
            for (TvShelves.Entry e : shelf.entries) {
                channels.add(e.main);
                versions.put(e.main.id, e.versions);
            }
        }

        String key() {
            return kind == Kind.GROUP ? "g:" + category.id : kind == Kind.SHELF ? "s:" + shelf.key() : kind.name();
        }

        /** A row the remote can stop on without changing the list of channels shown. */
        boolean opensList() {
            return kind == Kind.PROVIDERS || kind == Kind.BACK;
        }
    }

    private final MainActivity activity;
    final View root;
    private final LivePlayer player;
    private final RecyclerView groupsView, channelsView;
    private final TextView groupsCount, groupTitle, groupCount, channelsStatus;
    private final View groupsColumn, channelsColumn, pipSlot, retry, tabs, tabLive;
    private final GroupAdapter groupAdapter = new GroupAdapter();
    private final ChannelAdapter channelAdapter = new ChannelAdapter();
    private final Detail detail;

    private Xtream api;
    private Group group;
    private String groupKey;
    private int lastChannel;
    private int shownGroupsVersion = -1, shownHistory = -1;
    private String shownStamp = "";
    private final Runnable applyGroup = this::showGroup;
    private Group pendingGroup;
    /** The panel's own groups are shown instead of the shelves. */
    private boolean providerMode;
    private static final int RECENT = 12;

    LivePage(MainActivity activity, View root, LivePlayer player) {
        this.activity = activity;
        this.root = root;
        this.player = player;
        groupsView = root.findViewById(R.id.groups);
        channelsView = root.findViewById(R.id.channels);
        groupsCount = root.findViewById(R.id.groups_count);
        groupTitle = root.findViewById(R.id.group_title);
        groupCount = root.findViewById(R.id.group_count);
        channelsStatus = root.findViewById(R.id.channels_status);
        groupsColumn = root.findViewById(R.id.groups_column);
        channelsColumn = root.findViewById(R.id.channels_column);
        pipSlot = root.findViewById(R.id.pip_slot);
        retry = root.findViewById(R.id.retry);
        retry.setOnClickListener(v -> {
            retry.setVisibility(View.GONE);
            show(this.api);
        });
        Focus.lift(retry);
        // TV's two views: En direct is this page (marked as shown), Guide opens the guide's own screen.
        tabs = root.findViewById(R.id.tv_tabs);
        tabLive = root.findViewById(R.id.tab_live);
        View tabGuide = root.findViewById(R.id.tab_guide);
        tabLive.setActivated(true);
        tabLive.setOnClickListener(v -> focusGroups());
        tabGuide.setOnClickListener(v -> activity.startActivity(new android.content.Intent(activity, GuideActivity.class)));
        Focus.lift(tabLive);
        Focus.lift(tabGuide);
        detail = new Detail(root);
        groupsView.setLayoutManager(new LinearLayoutManager(activity));
        groupsView.setAdapter(groupAdapter);
        groupsView.setItemAnimator(null);
        channelsView.setLayoutManager(new LinearLayoutManager(activity));
        channelsView.setAdapter(channelAdapter);
        channelsView.setItemAnimator(null);
    }

    /** The name to show for a channel: tidied unless the owner turned that off. */
    static String display(Context c, Item channel) {
        return Options.tidyNames(c) ? channel.title : channel.name;
    }

    // ---- showing ----

    void show(Xtream api) {
        this.api = api;
        Library lib = Library.of(Xtream.Section.LIVE);
        boolean changed = shownGroupsVersion != Groups.version() || !shownStamp.equals(stamp());
        if (lib.ready() && !changed && groupAdapter.getItemCount() > 0) {
            if (shownHistory != History.version() && player.mode() == LivePlayer.Mode.HIDDEN) rebuild(false);
            else channelAdapter.guides();
            return;
        }
        if (api == null) {
            groupAdapter.set(new ArrayList<>());
            channelAdapter.set(new ArrayList<>());
            return;
        }
        if (!lib.ready()) {
            groupsCount.setText("");
            status(activity.getString(R.string.loading));
        }
        lib.load(api, () -> {
            if (activity.isDestroyed()) return;
            if (lib.error() != null && !lib.ready()) {
                status(lib.error().text(activity));
                // The list could not be read: Réessayer, which takes the focus unless the bar has it.
                retry.setVisibility(View.VISIBLE);
                if (activity.showing(this) && !activity.inBar() && !player.isFull()) retry.requestFocus();
                return;
            }
            rebuild(true);
        });
    }

    /** Builds the shelves, or the panel's groups, again (after a load, a change in Réglages, or new favourites). */
    private void rebuild(boolean focusDefault) {
        Library lib = Library.of(Xtream.Section.LIVE);
        Groups g = Groups.get(activity);
        History h = History.get(activity);
        Channels chs = Channels.get(activity);
        shownGroupsVersion = Groups.version();
        shownHistory = History.version();
        shownStamp = stamp();
        List<Group> list = new ArrayList<>();
        List<Xtream.Category> cats = lib.categories() == null ? new ArrayList<>() : lib.categories();
        // The shelves need the whole list; while only some groups are read, the panel's groups are shown.
        boolean shelves = !providerMode && lib.all() != null && !lib.all().isEmpty();
        if (shelves) {
            TvShelves.Input in = new TvShelves.Input();
            in.all = lib.all();
            for (Xtream.Category c : cats) in.groupNames.put(c.id, c.name);
            in.hidden = i -> (chs != null && chs.hidden(i.id)) || (g != null && g.hidden(Xtream.Section.LIVE, i.categoryId));
            in.showAdult = !Options.hideAdult(activity);
            // Favoris: the channels added by hand, in that order, then the most watched ones.
            if (chs != null) in.favourites.addAll(chs.starredItems());
            if (h != null) {
                in.favourites.addAll(h.favourites(FAVOURITES));
                in.recent.addAll(h.recentChannels(RECENT));
            }
            for (TvShelves.Shelf sh : TvShelves.build(in)) list.add(new Group(sh, shelfName(sh)));
            Group providers = new Group(Kind.PROVIDERS, null, activity.getString(R.string.shelf_providers), null);
            for (Xtream.Category c : g == null ? cats : g.ordered(Xtream.Section.LIVE, cats, false)) {
                List<Item> items = lib.items(c.id);
                if (items != null && !items.isEmpty()) providers.count++;
            }
            list.add(providers);
            groupsCount.setText(activity.getResources().getQuantityString(R.plurals.count_shelves, list.size() - 1,
                    list.size() - 1));
        } else {
            if (providerMode) list.add(new Group(Kind.BACK, null, activity.getString(R.string.shelf_back), null));
            if (lib.all() != null && !lib.all().isEmpty()) {
                list.add(new Group(Kind.ALL, null, activity.getString(R.string.group_all_channels), arranged(lib.all(), chs)));
            }
            int shownGroups = 0;
            for (Xtream.Category c : g == null ? cats : g.ordered(Xtream.Section.LIVE, cats, false)) {
                List<Item> items = lib.items(c.id);
                if (!lib.partial() && (items == null || items.isEmpty())) continue;   // empty groups are left out
                list.add(new Group(Kind.GROUP, c, g == null ? Groups.panelName(Xtream.Section.LIVE, c) : g.name(Xtream.Section.LIVE, c),
                        items == null ? null : arranged(items, chs)));
                shownGroups++;
            }
            groupsCount.setText(activity.getResources().getQuantityString(R.plurals.count_groups, shownGroups, shownGroups));
        }
        groupAdapter.set(list);
        if (list.isEmpty()) {
            group = null;
            channelAdapter.set(new ArrayList<>());
            groupTitle.setText("");
            groupCount.setText("");
            status(activity.getString(R.string.empty));
            return;
        }
        // Stay on the list shown before, else the first one that shows channels.
        Group keep = null;
        for (Group x : list) if (keep == null && !x.opensList()) keep = x;
        for (Group x : list) if (x.key().equals(groupKey)) keep = x;
        if (keep == null) keep = list.get(0);
        group = null;
        select(keep, true);
        // Not while the remote is in the bar: previewing TV from there must not pull the focus away.
        if (focusDefault && activity.showing(this) && !player.isFull() && !activity.inBar()) focusGroups();
    }

    /** What decides how the lists look, apart from the groups themselves: a change means building them again. */
    private String stamp() {
        return Options.tidyNames(activity) + "|" + Options.channelOrder(activity) + "|" + Options.dedupe(activity) + "|"
                + Options.hideAdult(activity) + "|" + Options.groupOrder(activity) + "|" + Channels.version();
    }

    /** A shelf's name: Favoris, Récentes, the theme, or the country. */
    private String shelfName(TvShelves.Shelf sh) {
        switch (sh.kind) {
            case FAVOURITES:
                return activity.getString(R.string.group_favourites);
            case RECENT:
                return activity.getString(R.string.shelf_recent);
            case COUNTRY:
                return countryName(sh.country);
            default:
                return themeName(sh.theme);
        }
    }

    private String themeName(Smart.Theme t) {
        int id;
        switch (t) {
            case GENERAL: id = R.string.shelf_general; break;
            case NEWS: id = R.string.shelf_news; break;
            case SPORT: id = R.string.shelf_sport; break;
            case MOVIES: id = R.string.shelf_movies; break;
            case SERIES: id = R.string.shelf_series; break;
            case KIDS: id = R.string.shelf_kids; break;
            case DOCS: id = R.string.shelf_docs; break;
            case ENTERTAINMENT: id = R.string.shelf_entertainment; break;
            case MUSIC: id = R.string.shelf_music; break;
            case REGIONAL: id = R.string.shelf_regional; break;
            case ADULT: id = R.string.shelf_adult; break;
            default: id = R.string.shelf_other; break;
        }
        return activity.getString(id);
    }

    /** A country's name in French, from its code; the code itself for one without a name here. */
    private String countryName(String code) {
        String[] codes = activity.getResources().getStringArray(R.array.shelf_country_codes);
        String[] names = activity.getResources().getStringArray(R.array.shelf_country_names);
        for (int i = 0; i < codes.length && i < names.length; i++) if (codes[i].equals(code)) return names[i];
        return code.toUpperCase(java.util.Locale.ROOT);
    }

    /** A group's channels as they are shown: hidden ones out, doubles merged, in the order chosen in Réglages. */
    private List<Item> arranged(List<Item> items, Channels chs) {
        List<Item> l = chs == null ? items : chs.visible(items);
        return Smart.arrange(l, Options.channelOrder(activity), Options.dedupe(activity));
    }

    private void status(String text) {
        channelsStatus.setText(text);
        channelsStatus.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
    }

    private void select(Group g, boolean now) {
        if (g.opensList()) return;   // Groupes du fournisseur and Rayons change the list on OK only
        pendingGroup = g;
        groupAdapter.markSelected(g);
        channelsView.removeCallbacks(applyGroup);
        if (now) showGroup();
        else channelsView.postDelayed(applyGroup, GROUP_DELAY_MS);
    }

    private void showGroup() {
        Group g = pendingGroup;
        if (g == null || g == group) return;
        boolean sameGroup = group != null && g.key().equals(group.key());
        group = g;
        groupKey = g.key();
        groupTitle.setText(g.name);
        if (!sameGroup) lastChannel = 0;
        if (g.channels == null && g.kind == Kind.GROUP) {
            channelAdapter.set(new ArrayList<>());
            groupCount.setText("");
            status(activity.getString(R.string.loading));
            Library.of(Xtream.Section.LIVE).loadCategory(api, g.category.id, () -> {
                List<Item> loaded = Library.of(Xtream.Section.LIVE).items(g.category.id);
                g.channels = loaded == null ? new ArrayList<>() : arranged(loaded, Channels.get(activity));
                if (group == g) {
                    group = null;
                    pendingGroup = g;
                    showGroup();
                }
            });
            return;
        }
        List<Item> items = g.channels == null ? new ArrayList<>() : g.channels;
        groupCount.setText(activity.getResources().getQuantityString(R.plurals.count_channels, items.size(), items.size()));
        channelAdapter.set(items, g.versions);
        if (items.isEmpty()) status(activity.getString(R.string.empty));
        else channelsStatus.setVisibility(View.GONE);
        channelsView.scrollToPosition(sameGroup ? lastChannel : 0);
        if (!items.isEmpty()) detail.show(items.get(Math.min(lastChannel, items.size() - 1)));
    }

    /** Shows the panel's own groups, or the shelves again; the focus goes to the first row that lists channels. */
    private void openProviders(boolean on) {
        providerMode = on;
        groupKey = null;
        group = null;
        rebuild(false);
        if (on) {
            groupsView.scrollToPosition(0);
            groupsView.post(this::focusGroups);
            return;
        }
        // Back on the shelves: the focus goes back to Groupes du fournisseur, where the remote left them.
        int at = groupAdapter.getItemCount() - 1;
        groupsView.scrollToPosition(at);
        groupsView.post(() -> {
            RecyclerView.ViewHolder h = groupsView.findViewHolderForAdapterPosition(at);
            if (h != null) h.itemView.requestFocus();
            else focusGroups();
        });
    }

    // ---- the player ----

    /** Where the small window goes: over the slot at the bottom right, in the root's coordinates. */
    Rect miniBounds() {
        if (root.getVisibility() != View.VISIBLE || pipSlot.getWidth() == 0) return null;
        int[] s = new int[2], r = new int[2];
        pipSlot.getLocationInWindow(s);
        activity.root().getLocationInWindow(r);
        int x = s[0] - r[0], y = s[1] - r[1];
        return new Rect(x, y, x + pipSlot.getWidth(), y + pipSlot.getHeight());
    }

    void channelChanged(Item c) {
        channelAdapter.markPlaying(c == null ? null : c.id);
    }

    void modeChanged(LivePlayer.Mode mode) {
        if (mode == LivePlayer.Mode.FULL) return;
        if (mode == LivePlayer.Mode.HIDDEN) channelAdapter.markPlaying(null);
        // Back on the list: the focus goes to the channel that was playing.
        if (activity.showing(this)) focusChannels(true);
        if (shownHistory != History.version() && mode == LivePlayer.Mode.HIDDEN) rebuild(false);
    }

    /** Plays a channel found by search, inside Toutes les chaînes (or its group). */
    void play(Item channel) {
        Library lib = Library.of(Xtream.Section.LIVE);
        List<Item> list = lib.all() != null ? lib.all() : lib.items(channel.categoryId);
        if (list == null || !list.contains(channel)) {
            list = new ArrayList<>();
            list.add(channel);
        }
        player.play(list, list.indexOf(channel), activity.getString(R.string.group_all_channels));
    }

    // ---- focus ----

    View defaultFocus() {
        if (retry.getVisibility() == View.VISIBLE) return retry;
        View v = channelRow(true);
        return v != null ? v : groupRow();
    }

    void focusGroups() {
        View v = groupRow();
        if (v != null) v.requestFocus();
        else groupsView.requestFocus();
    }

    private View groupRow() {
        int i = groupAdapter.indexOf(pendingGroup != null ? pendingGroup : group);
        if (i < 0) return null;
        RecyclerView.LayoutManager lm = groupsView.getLayoutManager();
        View v = lm == null ? null : lm.findViewByPosition(i);
        if (v == null && lm != null) {
            lm.scrollToPosition(i);
            groupsView.post(() -> {
                View later = lm.findViewByPosition(i);
                if (later != null) later.requestFocus();
            });
        }
        return v;
    }

    /** After an edit the list is new: the focus goes back to the same place, or the last row when it got shorter. */
    private void focusChannelAt(int position) {
        if (channelAdapter.getItemCount() == 0) {
            focusGroups();
            return;
        }
        lastChannel = Math.max(0, Math.min(position, channelAdapter.getItemCount() - 1));
        focusChannels(false);
    }

    /** Focuses the playing channel, else the last one focused in this group. */
    boolean focusChannels(boolean preferPlaying) {
        View v = channelRow(preferPlaying);
        if (v != null) {
            v.requestFocus();
            return true;
        }
        return false;
    }

    private View channelRow(boolean preferPlaying) {
        if (channelAdapter.getItemCount() == 0) return null;
        int i = preferPlaying ? channelAdapter.playingIndex() : -1;
        if (i < 0) i = Math.min(lastChannel, channelAdapter.getItemCount() - 1);
        int target = i;
        RecyclerView.LayoutManager lm = channelsView.getLayoutManager();
        View v = lm == null ? null : lm.findViewByPosition(target);
        if (v == null && lm != null) {
            lm.scrollToPosition(target);
            channelsView.post(() -> {
                View later = lm.findViewByPosition(target);
                if (later != null) later.requestFocus();
            });
        }
        return v;
    }

    /** Where an arrow goes inside the page; null hands it back to the main screen (Up: the bar; else nothing moves). */
    View route(View focused, int direction, View proposed) {
        boolean inGroups = Root.inside(focused, groupsColumn);
        boolean inChannels = Root.inside(focused, channelsColumn);
        boolean inPip = focused == activity.liveLayer();
        boolean vertical = direction == View.FOCUS_UP || direction == View.FOCUS_DOWN;
        if (!vertical && Root.inside(focused, tabs)) {
            return Root.inside(proposed, tabs) ? proposed : focused;   // Left and Right stay on En direct / Guide
        }
        if (vertical) {
            // Up and Down stay in a panel; Up at the top of the shelves goes to En direct / Guide, then to the bar.
            boolean up = direction == View.FOCUS_UP;
            if (Root.inside(focused, tabs)) {
                View row = groupRow();
                return up ? null : row != null ? row : focused;
            }
            if (inGroups && up && !Root.inside(proposed, groupsView)) return tabLive;
            if (inGroups && !Root.inside(proposed, groupsColumn)) return up ? null : focused;
            if (inChannels && !Root.inside(proposed, channelsColumn)) return up ? null : focused;
            if (inPip) return focused;
            return proposed;
        }
        if (direction == View.FOCUS_RIGHT) {
            if (inGroups) {
                channelsView.removeCallbacks(applyGroup);
                showGroup();
                View v = channelRow(true);
                return v != null ? v : focused;
            }
            if (inChannels) {
                return player.mode() == LivePlayer.Mode.MINI ? activity.liveLayer() : focused;
            }
            if (inPip) return focused;
        }
        if (direction == View.FOCUS_LEFT) {
            if (inPip) {
                View v = channelRow(true);
                return v != null ? v : focused;
            }
            if (inChannels) {
                View v = groupRow();
                return v != null ? v : focused;
            }
            if (inGroups) return focused;
        }
        return proposed;
    }

    /** Back inside the page: closes the small window first, then steps out one panel at a time. */
    boolean back(View focused) {
        if (player.mode() == LivePlayer.Mode.MINI) {
            player.stop();
            return true;
        }
        if (Root.inside(focused, channelsColumn)) {
            focusGroups();
            return true;
        }
        if (providerMode && Root.inside(focused, groupsColumn)) {
            openProviders(false);   // from the panel's groups, Back comes back to the shelves first
            return true;
        }
        return false;
    }

    // ---- the details panel ----

    private final class Detail {
        final TextView name, tag, watched, now, nowTime, desc, next, noGuide, initials;
        final View guide, bar, nowLabel, track;
        final ImageView logo;
        String shownId;

        Detail(View v) {
            name = v.findViewById(R.id.detail_name);
            tag = v.findViewById(R.id.detail_tag);
            watched = v.findViewById(R.id.detail_watched);
            now = v.findViewById(R.id.detail_now);
            nowTime = v.findViewById(R.id.detail_now_time);
            desc = v.findViewById(R.id.detail_desc);
            next = v.findViewById(R.id.detail_next);
            noGuide = v.findViewById(R.id.detail_no_guide);
            guide = v.findViewById(R.id.detail_guide);
            bar = v.findViewById(R.id.detail_bar);
            nowLabel = v.findViewById(R.id.detail_now_label);
            track = v.findViewById(R.id.detail_track);
            logo = v.findViewById(R.id.slot_logo);
            initials = v.findViewById(R.id.slot_initials);
        }

        void show(Item c) {
            shownId = c.id;
            name.setText(display(activity, c));
            boolean tidy = Options.tidyNames(activity);
            tag.setText(c.tag);
            tag.setVisibility(tidy && !c.tag.isEmpty() ? View.VISIBLE : View.GONE);
            History h = History.get(activity);
            long ms = h == null ? 0 : h.watchedMs(c.id);
            watched.setText(ms >= 60_000 ? activity.getString(R.string.live_watched, Players.duration(ms))
                    : c.num > 0 ? activity.getString(R.string.live_number, c.num) : "");
            initials.setText(Ui.initials(c.title));
            Images.load(logo, c.image, Ui.dp(activity, 150), initials);
            guide.setVisibility(View.INVISIBLE);
            noGuide.setVisibility(View.GONE);
            Epg.get(api, c.id, new Epg.Callback() {
                @Override
                public void done(Epg.Listing l) {
                    if (c.id.equals(shownId)) guide(l);
                }

                @Override
                public boolean stillWanted() {
                    return c.id.equals(shownId);
                }
            });
        }

        void guide(Epg.Listing l) {
            long sec = System.currentTimeMillis() / 1000;
            Epg.Programme p = l == null ? null : l.now(sec);
            Epg.Programme n = l == null ? null : l.next(sec);
            if (p == null && n == null) {
                guide.setVisibility(View.GONE);
                noGuide.setVisibility(View.VISIBLE);
                return;
            }
            guide.setVisibility(View.VISIBLE);
            // Between two programmes only "Ensuite" is shown.
            int nowShown = p == null ? View.GONE : View.VISIBLE;
            for (View v : new View[]{nowLabel, now, nowTime, track}) v.setVisibility(nowShown);
            now.setText(p == null ? "" : p.title);
            nowTime.setText(p == null ? "" : Players.clock(p.start) + " – " + Players.clock(p.end));
            bar.setPivotX(0);
            bar.setScaleX(p == null ? 0 : p.progress(sec));
            desc.setText(p == null ? "" : p.description);
            desc.setVisibility(p == null || p.description.isEmpty() ? View.GONE : View.VISIBLE);
            next.setText(n == null ? "—" : Players.clock(n.start) + "  " + n.title);
        }
    }

    // ---- adapters ----

    private final class GroupAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Group> items = new ArrayList<>();
        Group selected;

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Group> list) {
            items = list;
            notifyDataSetChanged();
        }

        int indexOf(Group g) {
            if (g == null) return items.isEmpty() ? -1 : 0;
            for (int i = 0; i < items.size(); i++) if (items.get(i).key().equals(g.key())) return i;
            return items.isEmpty() ? -1 : 0;
        }

        /** Only the rows' state changes: rebinding would move the focus. */
        void markSelected(Group g) {
            selected = g;
            for (int i = 0; i < groupsView.getChildCount(); i++) {
                View row = groupsView.getChildAt(i);
                int p = groupsView.getChildAdapterPosition(row);
                row.setActivated(p != RecyclerView.NO_POSITION && items.get(p) == g);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_live_group, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            Group g = items.get(position);
            View v = h.itemView;
            ((TextView) v.findViewById(R.id.name)).setText(g.name);
            TextView count = v.findViewById(R.id.count);
            count.setText(g.kind == Kind.PROVIDERS ? String.valueOf(g.count)
                    : g.channels == null || g.kind == Kind.BACK ? "" : String.valueOf(g.channels.size()));
            ImageView icon = v.findViewById(R.id.icon);
            icon.setRotation(g.kind == Kind.BACK ? 180 : 0);
            icon.setImageResource(icon(g));
            v.setActivated(g == selected);
            v.setOnFocusChangeListener((view, focused) -> {
                if (focused) select(g, false);
            });
            v.setOnClickListener(view -> {
                if (g.opensList()) {
                    openProviders(g.kind == Kind.PROVIDERS);
                    return;
                }
                channelsView.removeCallbacks(applyGroup);
                select(g, true);
                focusChannels(true);
            });
            v.setOnLongClickListener(view -> {
                if (g.kind != Kind.GROUP) return false;
                GroupOptions.show(activity, Xtream.Section.LIVE, g.category, () -> {
                    // Redrawing the list puts the focus on its first row, which would select Favoris: once it
                    // is drawn, the group shown (the same one, or the first when it was hidden) is focused again.
                    rebuild(false);
                    Group kept = group;
                    groupsView.post(() -> {
                        if (kept != null) select(kept, true);
                        focusGroups();
                    });
                });
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    /** A row's icon: the shelf's kind or theme, or the panel's group's theme. */
    private static int icon(Group g) {
        switch (g.kind) {
            case PROVIDERS:
                return R.drawable.ic_folder;
            case BACK:
                return R.drawable.ic_chevron;
            case ALL:
                return R.drawable.ic_all;
            case GROUP:
                return themeIcon(Smart.theme(g.category.name));
            default:
                if (g.shelf.kind == TvShelves.Kind.FAVOURITES) return R.drawable.ic_star;
                if (g.shelf.kind == TvShelves.Kind.RECENT) return R.drawable.ic_history;
                if (g.shelf.kind == TvShelves.Kind.COUNTRY) return R.drawable.ic_globe;
                return themeIcon(g.shelf.theme);
        }
    }

    /** A group's icon, from what it is about. */
    static int themeIcon(Smart.Theme t) {
        switch (t) {
            case GENERAL:
                return R.drawable.ic_live;
            case NEWS:
                return R.drawable.ic_news;
            case SPORT:
                return R.drawable.ic_sport;
            case MOVIES:
                return R.drawable.ic_movie;
            case SERIES:
                return R.drawable.ic_series;
            case KIDS:
                return R.drawable.ic_kids;
            case DOCS:
                return R.drawable.ic_docs;
            case MUSIC:
                return R.drawable.ic_music;
            case REGIONAL:
                return R.drawable.ic_server;
            case ENTERTAINMENT:
                return R.drawable.ic_smile;
            case ADULT:
                return R.drawable.ic_hide;
            default:
                return R.drawable.ic_folder;
        }
    }

    private final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
        static final String GUIDE = "guide";
        static final String PLAYING = "playing";
        List<Item> items = new ArrayList<>();
        /** Every version of each channel shown, by the shown one's id (a shelf); empty for the panel's lists. */
        Map<String, List<Item>> versions = new HashMap<>();
        String playingId;

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView num, title, tag, now, initials;
            final View track, bar, playing, star;
            volatile String boundId;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.image);
                num = v.findViewById(R.id.num);
                title = v.findViewById(R.id.title);
                tag = v.findViewById(R.id.tag);
                now = v.findViewById(R.id.now);
                initials = v.findViewById(R.id.initials);
                track = v.findViewById(R.id.track);
                bar = v.findViewById(R.id.bar);
                playing = v.findViewById(R.id.playing);
                star = v.findViewById(R.id.star);
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Item> list) {
            set(list, new HashMap<>());
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Item> list, Map<String, List<Item>> versions) {
            items = list;
            this.versions = versions;
            notifyDataSetChanged();
        }

        List<Item> versionsOf(Item item) {
            List<Item> v = versions.get(item.id);
            if (v != null) return v;
            List<Item> one = new ArrayList<>();
            one.add(item);
            return one;
        }

        void guides() {
            notifyItemRangeChanged(0, getItemCount(), GUIDE);
        }

        int playingIndex() {
            if (playingId == null) return -1;
            for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(playingId)) return i;
            return -1;
        }

        void markPlaying(String id) {
            playingId = id;
            notifyItemRangeChanged(0, getItemCount(), PLAYING);
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_live_channel, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder h, int position, List<Object> payloads) {
            if (!payloads.isEmpty()) {
                Item item = items.get(position);
                if (payloads.contains(GUIDE)) guide(h, item);
                if (payloads.contains(PLAYING)) playing(h, item);
                return;
            }
            super.onBindViewHolder(h, position, payloads);
        }

        @Override
        public void onViewRecycled(Holder h) {
            h.boundId = null;
        }

        private void playing(Holder h, Item item) {
            boolean on = item.id.equals(playingId);
            h.playing.setVisibility(on ? View.VISIBLE : View.GONE);
            h.itemView.setActivated(on);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Item item = items.get(position);
            int tnt = Options.channelOrder(activity) == Smart.ChannelOrder.TNT ? Smart.tntFolded(item.key) : 0;
            int number = tnt > 0 ? tnt : item.num;
            h.num.setText(number > 0 ? String.valueOf(number) : "");
            Channels chs = Channels.get(activity);
            List<Item> all = versionsOf(item);
            boolean starred = false;
            for (Item v : all) starred |= chs != null && chs.starred(v.id);
            h.star.setVisibility(starred ? View.VISIBLE : View.GONE);
            h.title.setText(display(activity, item));
            boolean tidy = Options.tidyNames(activity);
            String tags = qualities(all);
            h.tag.setText(tags);
            h.tag.setVisibility(tidy && !tags.isEmpty() ? View.VISIBLE : View.GONE);
            h.initials.setText(Ui.initials(item.title));
            Images.load(h.image, item.image, Ui.dp(activity, 64), h.initials);
            playing(h, item);
            guide(h, item);
            h.itemView.setOnFocusChangeListener((v, focused) -> {
                if (!focused) return;
                int p = h.getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) lastChannel = p;
                detail.show(item);
            });
            h.itemView.setOnClickListener(v -> {
                int p = h.getBindingAdapterPosition();
                if (p == RecyclerView.NO_POSITION || group == null) return;
                lastChannel = p;
                player.play(items, p, group.name);
            });
            h.itemView.setOnLongClickListener(v -> {
                int at = h.getBindingAdapterPosition();
                ChannelOptions.show(activity, item, all, version -> {
                    // Another version plays in the list's place of this channel, so zapping goes on from there.
                    List<Item> list = new ArrayList<>(items);
                    int p = at == RecyclerView.NO_POSITION ? 0 : at;
                    list.set(p, version);
                    lastChannel = p;
                    player.play(list, p, group == null ? "" : group.name);
                }, () -> {
                    int keep = lastChannel;
                    rebuild(false);
                    channelsView.post(() -> focusChannelAt(keep));
                });
                return true;
            });
        }

        /**
         * The versions a channel comes in, as its tag: "FHD · HD · SD"; "2 versions" when they carry the same mark (the
         * same channel in two groups); the one tag when there is one version.
         */
        private String qualities(List<Item> all) {
            if (all.size() < 2) return all.isEmpty() ? "" : all.get(0).tag;
            java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
            for (Item v : all) if (!v.tag.isEmpty()) tags.add(v.tag);
            if (tags.size() >= 2) return String.join(" · ", tags);
            return activity.getResources().getQuantityString(R.plurals.count_versions, all.size(), all.size());
        }

        private void guide(Holder h, Item item) {
            h.boundId = item.id;
            showGuide(h, null);
            Epg.get(api, item.id, new Epg.Callback() {
                @Override
                public void done(Epg.Listing listing) {
                    if (item.id.equals(h.boundId)) showGuide(h, listing);
                }

                @Override
                public boolean stillWanted() {
                    return item.id.equals(h.boundId);
                }
            });
        }

        private void showGuide(Holder h, Epg.Listing listing) {
            long sec = System.currentTimeMillis() / 1000;
            Epg.Programme p = listing == null ? null : listing.now(sec);
            h.now.setText(p == null ? "" : p.title);
            h.track.setVisibility(p == null ? View.INVISIBLE : View.VISIBLE);
            if (p == null) return;
            h.bar.setPivotX(0);
            h.bar.setScaleX(p.progress(sec));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
