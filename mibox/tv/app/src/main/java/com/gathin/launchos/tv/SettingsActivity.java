package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;
import com.gathin.launchos.design.Motion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Réglages, master-detail: five categories on a panel at the left (30 %); focusing one shows its page at the right
 * (70 %), its rows in titled sections; Right or OK goes into the page, Left or Back comes back to the category.
 * Settings with a few values show them side by side ({@link Rows#choice}), the chosen one marked by a pill that
 * glides to it; on/off settings are switches. Données starts refreshes in {@link SyncStore} and listens to it from
 * onStart to onStop (the work itself outlives the screen), and frees the cache with a {@link Notice} that says how
 * much. Deletions ask a second OK ({@link Confirm}). Every setting keeps its key and value from earlier versions.
 */
public final class SettingsActivity extends Activity implements SyncStore.Listener {
    private static final String STATE_CATEGORY = "category";
    private static final int GENERAL = 0, CHANNELS = 1, VOD = 2, PLAY = 3, DATA = 4;
    private static final int[] CAT_ICONS = {R.drawable.ic_gear, R.drawable.ic_live, R.drawable.ic_movie,
            R.drawable.ic_play_line, R.drawable.ic_refresh};
    private static final int[] CAT_TITLES = {R.string.settings_cat_general, R.string.settings_cat_channels,
            R.string.settings_cat_vod, R.string.settings_cat_play, R.string.settings_cat_data};
    private static final int[] CAT_HINTS = {R.string.settings_pane_general, R.string.settings_pane_channels,
            R.string.settings_pane_vod, R.string.settings_pane_play, R.string.settings_pane_data};
    /** How long a finished gauge stays green (or red) before it shows the list's state again. */
    private static final long DONE_SHOWN_MS = 3000, FAILED_SHOWN_MS = 60_000;
    private static final Smart.GroupOrder[] GROUP_ORDERS = Smart.GroupOrder.values();
    private static final Smart.ChannelOrder[] CHANNEL_ORDERS = Smart.ChannelOrder.values();

    private final View[] categories = new View[CAT_TITLES.length];
    private final ViewGroup[] panes = new ViewGroup[CAT_TITLES.length];
    private SafeScrollView scroll;
    private int shown = -1;

    private Rows.Row account, updates, dedupe, adult, hiddenChannels, mini, next, tidy, zap;
    private Rows.Choice start, groupOrder, channelOrder, guideShift, picture, listHours;
    private final GaugeButton[] gauges = new GaugeButton[Xtream.Section.values().length];
    private TextView syncAll, cacheClear;
    private boolean clearing;
    private final List<Confirm> confirms = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        scroll = findViewById(R.id.detail_scroll);
        ((TextView) findViewById(R.id.master_footer)).setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME));
        ViewGroup list = findViewById(R.id.master_list);
        ViewGroup detail = findViewById(R.id.detail);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < categories.length; i++) {
            View c = inflater.inflate(R.layout.item_ds_category, list, false);
            ((ImageView) c.findViewById(R.id.cat_icon)).setImageResource(CAT_ICONS[i]);
            ((TextView) c.findViewById(R.id.cat_title)).setText(CAT_TITLES[i]);
            c.setId(View.generateViewId());
            list.addView(c);
            categories[i] = c;

            LinearLayout pane = new LinearLayout(this);
            pane.setOrientation(LinearLayout.VERTICAL);
            pane.setClipChildren(false);
            pane.setVisibility(View.GONE);
            View header = inflater.inflate(R.layout.view_ds_pane_header, pane, false);
            ((TextView) header.findViewById(R.id.pane_title)).setText(CAT_TITLES[i]);
            ((TextView) header.findViewById(R.id.pane_hint)).setText(CAT_HINTS[i]);
            pane.addView(header);
            detail.addView(pane, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            panes[i] = pane;
        }
        buildGeneral(panes[GENERAL]);
        buildChannels(panes[CHANNELS]);
        buildVod(panes[VOD]);
        buildPlay(panes[PLAY]);
        buildData(panes[DATA]);
        wireCategories();

        int first = state == null ? GENERAL : Math.min(state.getInt(STATE_CATEGORY, GENERAL), DATA);
        show(first, false);
        categories[first].requestFocus();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_CATEGORY, Math.max(shown, 0));
    }

    @Override
    protected void onStart() {
        super.onStart();
        SyncStore.get(this).observe(this);
    }

    @Override
    protected void onStop() {
        SyncStore.get(this).forget(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        states();
        cacheStats();
    }

    // ---- master: the categories ----

    private void wireCategories() {
        for (int i = 0; i < categories.length; i++) {
            int index = i;
            View c = categories[i];
            c.setOnFocusChangeListener((v, focused) -> {
                if (focused) show(index, true);
            });
            c.setOnClickListener(v -> enterPane());
            Focus.lift(c, Focus.SCALE_LARGE);
            c.setOnKeyListener((v, code, e) -> code == KeyEvent.KEYCODE_DPAD_RIGHT
                    && e.getAction() == KeyEvent.ACTION_DOWN && enterPane());
        }
        categories[0].setNextFocusUpId(categories[0].getId());
        View last = categories[categories.length - 1];
        last.setNextFocusDownId(last.getId());
    }

    /** Shows a category's page; with a short fade and rise when the remote moved to it (none with animations off). */
    private void show(int index, boolean animate) {
        if (index == shown) return;
        int before = shown;
        shown = index;
        for (int i = 0; i < panes.length; i++) {
            panes[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            categories[i].setActivated(i == index);
        }
        scroll.scrollTo(0, 0);
        ViewGroup pane = panes[index];
        pane.animate().cancel();
        pane.setAlpha(1f);
        pane.setTranslationY(0);
        if (animate && Motion.on()) {
            // As in LaunchOS: the page comes from the side the remote moved towards, below when going down.
            pane.setAlpha(0f);
            pane.setTranslationY(Ui.dp(this, index > before ? 16 : -16));
            pane.animate().alpha(1f).translationY(0).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
        }
        wireEdges(pane, categories[index]);
    }

    /** Right from a category, or OK: the page's first item. */
    private boolean enterPane() {
        List<View> f = focusables(panes[shown]);
        if (f.isEmpty()) return false;
        View first = f.get(0);
        // A line of values is entered on the chosen one, as the remote enters it from above or below.
        if (first.getParent() instanceof com.gathin.launchos.design.SegmentBar) {
            ViewGroup bar = (ViewGroup) first.getParent();
            for (int i = 0; i < bar.getChildCount(); i++) if (bar.getChildAt(i).isSelected()) first = bar.getChildAt(i);
        }
        first.requestFocus();
        return true;
    }

    /** Back from inside a page goes to its category first; from the categories it leaves. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // A deletion waiting for its second OK: Back only puts it back.
            for (Confirm c : confirms) if (c.cancel()) return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && shown >= 0) {
            View focus = getCurrentFocus();
            if (focus != null && isInside(focus, panes[shown])) {
                categories[shown].requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private static boolean isInside(View v, ViewGroup parent) {
        for (Object p = v.getParent(); p instanceof View; p = ((View) p).getParent()) if (p == parent) return true;
        return false;
    }

    /**
     * Left from the first item of a line goes back to the category; the page's first item keeps Up and its last
     * keeps Down, so the focus never falls out of the page.
     */
    private static void wireEdges(ViewGroup pane, View category) {
        List<View> f = focusables(pane);
        for (View v : f) {
            if (firstOfLine(v)) v.setNextFocusLeftId(category.getId());
        }
        if (f.isEmpty()) return;
        View first = f.get(0), last = f.get(f.size() - 1);
        first.setNextFocusUpId(first.getId());
        last.setNextFocusDownId(last.getId());
    }

    private static boolean firstOfLine(View v) {
        if (!(v.getParent() instanceof LinearLayout)) return true;
        LinearLayout line = (LinearLayout) v.getParent();
        if (line.getOrientation() == LinearLayout.VERTICAL) return true;
        for (int i = 0; i < line.getChildCount(); i++) {
            View c = line.getChildAt(i);
            if (c.getVisibility() == View.VISIBLE && c.isFocusable()) return c == v;
        }
        return true;
    }

    private static List<View> focusables(ViewGroup g) {
        List<View> out = new ArrayList<>();
        collect(g, out);
        return out;
    }

    private static void collect(ViewGroup g, List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            if (c.isFocusable()) {
                if (c.getId() == View.NO_ID) c.setId(View.generateViewId());
                out.add(c);
                continue;
            }
            if (c instanceof ViewGroup) collect((ViewGroup) c, out);
        }
    }

    // ---- detail: the pages ----

    private void buildGeneral(ViewGroup pane) {
        account = Rows.add(pane, R.drawable.ic_account, R.string.nav_account).opens()
                .click(v -> startActivity(new Intent(this, AccountActivity.class)));
        Rows.section(pane, R.string.set_sec_start);
        start = Rows.choice(pane, R.drawable.ic_power, R.string.set_start, labels(R.string.set_start_home,
                R.string.set_start_live, R.string.set_start_last, R.string.set_start_favourites), i -> {
                    Options.setStartPage(this, Options.StartPage.values()[i]);
                    states();
                });
        Rows.section(pane, R.string.set_sec_app);
        updates = Rows.add(pane, R.drawable.ic_update, R.string.nav_updates).opens()
                .click(v -> startActivity(new Intent(this, UpdatesActivity.class)));
    }

    private void buildChannels(ViewGroup pane) {
        Rows.section(pane, R.string.set_sec_sort);
        groupOrder = Rows.choice(pane, R.drawable.ic_sort, R.string.settings_group_order,
                labels(R.string.order_smart, R.string.order_az, R.string.order_panel), i -> {
                    Options.setGroupOrder(this, GROUP_ORDERS[i]);
                    Groups.touch();
                    states();
                });
        channelOrder = Rows.choice(pane, R.drawable.ic_live, R.string.settings_channel_order,
                labels(R.string.order_tnt, R.string.order_panel, R.string.order_az), i -> {
                    Options.setChannelOrder(this, CHANNEL_ORDERS[i]);
                    states();
                });
        dedupe = Rows.add(pane, R.drawable.ic_all, R.string.settings_dedupe).sub(R.string.settings_dedupe_sub)
                .click(v -> {
                    Options.setDedupe(this, !Options.dedupe(this));
                    states();
                });
        tidy = Rows.add(pane, R.drawable.ic_text, R.string.settings_tidy).sub(R.string.settings_tidy_sub).click(v -> {
            Options.setTidyNames(this, !Options.tidyNames(this));
            Groups.touch();
            states();
        });
        Rows.section(pane, R.string.set_sec_guide);
        String[] shifts = new String[Options.GUIDE_SHIFTS.length];
        for (int i = 0; i < shifts.length; i++) {
            int h = Options.GUIDE_SHIFTS[i];
            shifts[i] = h == 0 ? getString(R.string.set_guide_shift_none)
                    : getString(h > 0 ? R.string.set_guide_shift_plus : R.string.set_guide_shift_minus, Math.abs(h));
        }
        guideShift = Rows.choice(pane, R.drawable.ic_guide, R.string.set_guide_shift, shifts, i -> {
            Options.setGuideShiftHours(this, Options.GUIDE_SHIFTS[i]);
            Options.applyGlobals(this);
            states();
        }).sub(R.string.set_guide_shift_sub);
        Rows.section(pane, R.string.set_sec_hidden);
        adult = Rows.add(pane, R.drawable.ic_lock, R.string.settings_adult).sub(R.string.settings_adult_sub)
                .click(v -> {
                    Options.setHideAdult(this, !Options.hideAdult(this));
                    Groups.touch();
                    states();
                });
        hiddenChannels = Rows.add(pane, R.drawable.ic_hide, R.string.settings_hidden_channels).opens()
                .click(v -> startActivity(new Intent(this, HiddenChannelsActivity.class)));
        Rows.add(pane, R.drawable.ic_folder, R.string.settings_groups_live).sub(R.string.settings_groups_sub).opens()
                .click(v -> groups(Xtream.Section.LIVE));
    }

    private void buildVod(ViewGroup pane) {
        Rows.section(pane, R.string.set_sec_groups);
        Rows.add(pane, R.drawable.ic_movie, R.string.settings_groups_movies).sub(R.string.settings_groups_sub)
                .opens().click(v -> groups(Xtream.Section.MOVIES));
        Rows.add(pane, R.drawable.ic_series, R.string.settings_groups_series).sub(R.string.settings_groups_sub)
                .opens().click(v -> groups(Xtream.Section.SERIES));
        Rows.section(pane, R.string.set_sec_episodes);
        next = Rows.add(pane, R.drawable.ic_next, R.string.settings_next).sub(R.string.settings_next_sub).click(v -> {
            Options.setNextEpisode(this, !Options.nextEpisode(this));
            states();
        });
    }

    private void buildPlay(ViewGroup pane) {
        Rows.section(pane, R.string.set_sec_live);
        mini = Rows.add(pane, R.drawable.ic_mini, R.string.settings_mini).sub(R.string.settings_mini_sub).click(v -> {
            Options.setMiniPlayer(this, !Options.miniPlayer(this));
            states();
        });
        zap = Rows.add(pane, R.drawable.ic_zap, R.string.set_zap).sub(R.string.set_zap_sub).click(v -> {
            Options.setZapInverted(this, !Options.zapInverted(this));
            states();
        });
        Rows.section(pane, R.string.set_sec_picture);
        picture = Rows.choice(pane, R.drawable.ic_image, R.string.set_picture, labels(R.string.set_picture_fit,
                R.string.set_picture_zoom, R.string.set_picture_stretch), i -> {
                    Options.setPicture(this, Options.Picture.values()[i]);
                    states();
                });
    }

    /** Both deletions ask a second OK ({@link Confirm}), like deleting the account. */
    private void buildHistory(ViewGroup pane) {
        Rows.Row fav = Rows.add(pane, R.drawable.ic_star_line, R.string.settings_clear_fav)
                .sub(R.string.settings_clear_fav_sub);
        confirms.add(new Confirm(fav.view, fav.title, () -> {
            History h = History.get(this);
            if (h != null) h.clearWatched();
            fav.state(getString(R.string.cleared), Rows.Tone.OK);
        }));
        Rows.Row resume = Rows.add(pane, R.drawable.ic_history, R.string.settings_clear_resume)
                .sub(R.string.settings_clear_resume_sub);
        confirms.add(new Confirm(resume.view, resume.title, () -> {
            History h = History.get(this);
            if (h != null) h.clearPlayback();
            resume.state(getString(R.string.cleared), Rows.Tone.OK);
        }));
    }

    private CharSequence[] labels(int... ids) {
        CharSequence[] out = new CharSequence[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = getText(ids[i]);
        return out;
    }

    private void groups(Xtream.Section s) {
        startActivity(new Intent(this, GroupsActivity.class).putExtra(GroupsActivity.EXTRA_SECTION, s.name()));
    }

    // ---- Données: the lists, the cache, the history ----

    private void buildData(ViewGroup pane) {
        View page = LayoutInflater.from(this).inflate(R.layout.pane_sync_cache, pane, false);
        pane.addView(page);
        int[] ids = {R.id.gauge_live, R.id.gauge_movies, R.id.gauge_series};
        int[] icons = {R.drawable.ic_live, R.drawable.ic_movie, R.drawable.ic_series};
        int[] titles = {R.string.sync_live, R.string.sync_movies, R.string.sync_series};
        for (Xtream.Section s : Xtream.Section.values()) {
            GaugeButton g = page.findViewById(ids[s.ordinal()]);
            g.set(icons[s.ordinal()], titles[s.ordinal()]);
            g.setOnClickListener(v -> SyncStore.get(this).refresh(s));
            gauges[s.ordinal()] = g;
        }
        syncAll = page.findViewById(R.id.sync_all);
        syncAll.setOnClickListener(v -> SyncStore.get(this).refreshAll());
        Focus.lift(syncAll);
        cacheClear = page.findViewById(R.id.cache_clear);
        cacheClear.setOnClickListener(v -> clearCache());
        Focus.lift(cacheClear);
        String[] hours = new String[Options.LIST_HOURS.length];
        for (int i = 0; i < hours.length; i++) hours[i] = getString(R.string.set_list_hours_n, Options.LIST_HOURS[i]);
        listHours = Rows.choice(page.findViewById(R.id.sync_auto_rows), R.drawable.ic_hourglass, R.string.set_list_hours,
                hours, i -> {
                    Options.setListHours(this, Options.LIST_HOURS[i]);
                    Options.applyGlobals(this);
                    states();
                }).sub(R.string.set_list_hours_sub);
        buildHistory(page.findViewById(R.id.history_rows));
    }

    /** A section's refresh moved on: its gauge shows it (SyncStore.Listener, main thread). */
    @Override
    public void changed(SyncStore.Job j) {
        GaugeButton g = gauges[j.section.ordinal()];
        if (g == null) return;
        long age = System.currentTimeMillis() - j.at;
        switch (j.phase) {
            case RUNNING:
                g.running(j.progress, j.bytes > 0 ? getString(R.string.sync_received, Formats.size(this, j.bytes))
                        : getString(R.string.sync_connecting));
                break;
            case DONE:
                if (age < DONE_SHOWN_MS) {
                    g.done(loaded(j.section, false));
                    g.postDelayed(() -> {
                        if (!isDestroyed()) changed(SyncStore.get(this).job(j.section));
                    }, DONE_SHOWN_MS - age);
                } else {
                    g.idle(loaded(j.section, true));
                }
                cacheStats();
                break;
            case FAILED:
                if (age < FAILED_SHOWN_MS) g.failed(j.error);
                else g.idle(loaded(j.section, true));
                break;
            default:
                g.idle(loaded(j.section, true));
        }
        boolean busy = SyncStore.get(this).running();
        syncAll.setText(busy ? R.string.sync_all_running : R.string.sync_all);
    }

    /** "1 234 chaînes · il y a 2 h", or "1 234 chaînes · 56 groupes" just after a refresh. */
    private String loaded(Xtream.Section s, boolean withAge) {
        Library lib = Library.of(s);
        if (lib.categories() == null) return getString(R.string.refresh_not_loaded);
        int groups = lib.categories().size();
        String what = lib.all() == null
                ? getResources().getQuantityString(R.plurals.sync_count_groups, groups, count(groups))
                : getResources().getQuantityString(s == Xtream.Section.LIVE ? R.plurals.sync_count_live
                        : s == Xtream.Section.MOVIES ? R.plurals.sync_count_movies : R.plurals.sync_count_series,
                lib.all().size(), count(lib.all().size()));
        String then = withAge || lib.all() == null ? Formats.ago(this, lib.loadedAt())
                : getResources().getQuantityString(R.plurals.sync_count_groups, groups, count(groups));
        return getString(R.string.sync_loaded, what, then);
    }

    private static String count(int n) {
        return String.format(Locale.FRANCE, "%,d", n);
    }

    /** What the cache holds: the pictures' size is measured off the main thread, behind a skeleton. */
    private void cacheStats() {
        int guide = Epg.size();
        reveal(R.id.cache_guide_skeleton, R.id.cache_guide_value,
                getResources().getQuantityString(R.plurals.cache_guide_value, guide, guide));
        int titles = 0;
        for (Xtream.Section s : Xtream.Section.values()) {
            List<Item> all = Library.of(s).all();
            if (all != null) titles += all.size();
        }
        reveal(R.id.cache_lists_skeleton, R.id.cache_lists_value,
                getResources().getQuantityString(R.plurals.cache_lists_value, titles, count(titles)));
        skeleton(R.id.cache_images_skeleton, R.id.cache_images_value);
        Bg.run(() -> Images.diskBytes(this), (bytes, error) -> {
            if (isDestroyed()) return;
            reveal(R.id.cache_images_skeleton, R.id.cache_images_value, Formats.size(this, bytes == null ? 0 : bytes));
        });
    }

    private void skeleton(int skeletonId, int valueId) {
        findViewById(skeletonId).setVisibility(View.VISIBLE);
        findViewById(valueId).setVisibility(View.INVISIBLE);
    }

    private void reveal(int skeletonId, int valueId, String text) {
        TextView value = findViewById(valueId);
        value.setText(text);
        if (value.getVisibility() == View.VISIBLE) return;
        findViewById(skeletonId).setVisibility(View.GONE);
        value.setAlpha(0f);
        value.setVisibility(View.VISIBLE);
        value.animate().alpha(1f).setDuration(200).start();
    }

    /** Frees the pictures (memory and disk) and the programme guide; the lists and the settings stay. */
    private void clearCache() {
        if (clearing) return;
        clearing = true;
        cacheClear.setText(R.string.cache_clearing);
        skeleton(R.id.cache_images_skeleton, R.id.cache_images_value);
        Images.clearMemory();
        Epg.clear();
        Bg.run(() -> {
            long before = Images.diskBytes(this);
            Images.clearDisk(this);
            return before - Images.diskBytes(this);
        }, (freed, error) -> {
            clearing = false;
            if (isDestroyed()) return;
            cacheClear.setText(R.string.cache_clear);
            cacheStats();
            Notice.show(this, getString(R.string.cache_cleared_toast,
                    Formats.size(this, freed == null ? 0 : Math.max(0, freed))), true);
        });
    }

    // ---- what each row says ----

    /** Also after coming back from Compte, Mises à jour or the hidden channels. */
    private void states() {
        Account a = AccountStore.load(this);
        account.sub(a == null ? getString(R.string.settings_account_none) : getString(R.string.settings_account_user, a.user));
        UpdateState u = new UpdateState(this);
        Release r = u.available();
        boolean newer = r != null && r.version.isNewerThan(UpdateState.installed(this));
        updates.sub(getString(R.string.upd_installed, String.valueOf(UpdateState.installed(this))));
        updates.state(newer ? getString(R.string.upd_available, String.valueOf(r.version)) : "", Rows.Tone.OK);
        categories[GENERAL].findViewById(R.id.cat_dot).setVisibility(newer ? View.VISIBLE : View.GONE);

        Options.StartPage sp = Options.startPage(this);
        start.select(sp.ordinal()).sub(sp == Options.StartPage.HOME ? R.string.set_start_home_sub
                : sp == Options.StartPage.LIVE ? R.string.set_start_live_sub
                : sp == Options.StartPage.LAST_CHANNEL ? R.string.set_start_last_sub : R.string.set_start_favourites_sub);
        Smart.GroupOrder go = Options.groupOrder(this);
        groupOrder.select(go.ordinal()).sub(go == Smart.GroupOrder.SMART ? R.string.order_smart_sub
                : go == Smart.GroupOrder.AZ ? R.string.order_az_sub : R.string.order_panel_sub);
        Smart.ChannelOrder co = Options.channelOrder(this);
        channelOrder.select(co.ordinal()).sub(co == Smart.ChannelOrder.TNT ? R.string.order_tnt_sub
                : co == Smart.ChannelOrder.AZ ? R.string.order_az_channels_sub : R.string.order_panel_channels_sub);
        int shift = Options.guideShiftHours(this);
        for (int i = 0; i < Options.GUIDE_SHIFTS.length; i++) if (Options.GUIDE_SHIFTS[i] == shift) guideShift.select(i);
        Options.Picture pic = Options.picture(this);
        picture.select(pic.ordinal()).sub(pic == Options.Picture.FIT ? R.string.set_picture_fit_sub
                : pic == Options.Picture.ZOOM ? R.string.set_picture_zoom_sub : R.string.set_picture_stretch_sub);
        int hours = Options.listHours(this);
        for (int i = 0; i < Options.LIST_HOURS.length; i++) if (Options.LIST_HOURS[i] == hours) listHours.select(i);
        zap.toggle(Options.zapInverted(this));
        dedupe.toggle(Options.dedupe(this));
        adult.toggle(Options.hideAdult(this));
        Channels chs = Channels.get(this);
        int hidden = chs == null ? 0 : chs.hiddenItems().size();
        hiddenChannels.sub(hidden == 0 ? getString(R.string.settings_hidden_none)
                : getResources().getQuantityString(R.plurals.settings_hidden_count, hidden, hidden));
        mini.toggle(Options.miniPlayer(this));
        next.toggle(Options.nextEpisode(this));
        tidy.toggle(Options.tidyNames(this));
    }
}
