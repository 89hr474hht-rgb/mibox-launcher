package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.launchos.design.Focus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Guide TV, as a timeline: the hours along the top (two hours at a time, from the current half hour), the
 * channels of a group down the left, and their programmes placed along the hours, with a line at the current time.
 * Up and Down go from channel to channel; Right past the window's end moves it on half an hour, Left moves it back
 * (never before now: panels only give what is on now and next). OK plays the channel in Chaînes. The guide of a
 * channel is asked for only when its line is on screen, on {@link Bg#execute}, and kept {@link #KEEP_MS}.
 *
 * <p>Lines are recycled with their programme blocks: a bind reuses the blocks a line already has, and moving the
 * window of hours rebinds only the blocks ({@link RowAdapter#STRIP}), never the channel part. Main thread only.
 */
public final class GuideActivity extends Activity {
    private static final int WINDOW_MIN = 120;
    /** How far ahead the window may go. */
    private static final int AHEAD_MIN = 6 * 60;
    private static final int LIMIT = 12;
    private static final long KEEP_MS = 30 * 60 * 1000L;
    private static final int FAVOURITES = 16;
    /** A programme's box is never narrower than this, so a short one still shows a few letters of its title. */
    private static final int MIN_BLOCK_DP = 72;

    private static final Map<String, List<Epg.Programme>> CACHE = new HashMap<>();
    private static final Map<String, Long> FETCHED = new HashMap<>();
    private static int cacheAccount = -1;

    private static final class Group {
        final String name;
        final Xtream.Category category;
        final boolean favourites;

        Group(String name, Xtream.Category category, boolean favourites) {
            this.name = name;
            this.category = category;
            this.favourites = favourites;
        }
    }

    private Xtream api;
    private final List<Group> groups = new ArrayList<>();
    private final List<TextView> groupViews = new ArrayList<>();
    private Group group;
    private List<Item> channels = new ArrayList<>();
    private final Set<String> loading = new HashSet<>();
    private final RowAdapter adapter = new RowAdapter();
    private RecyclerView rows;
    private FrameLayout axis;
    private View nowLine;
    private TextView clock, detailTitle, detailText, status;
    private View retry;
    private long base, from;
    private float px;
    private boolean shownOnce;
    /** The first focus fell on line 0's channel because its programmes were not there yet: move it when they come. */
    private boolean firstOnChannel;
    private int stripStart;
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.FRANCE);
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis() / 1000;
            if (Timeline.windowStart(now) > base) {
                // A new half hour: the window starts from it.
                base = Timeline.windowStart(now);
                if (from < base) from = base;
                layoutTimes();
                adapter.strips();
            }
            clock.setText(hm.format(new Date()));
            placeNow();
            clock.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_guide);
        Options.applyGlobals(this);
        Account a = AccountStore.load(this);
        if (a == null) {
            finish();
            return;
        }
        api = new Xtream(a);
        if (cacheAccount != AccountStore.version()) {
            cacheAccount = AccountStore.version();
            CACHE.clear();
            FETCHED.clear();
        }
        rows = findViewById(R.id.guide_rows);
        axis = findViewById(R.id.guide_axis);
        nowLine = findViewById(R.id.guide_now);
        clock = findViewById(R.id.guide_clock);
        detailTitle = findViewById(R.id.guide_detail_title);
        detailText = findViewById(R.id.guide_detail_text);
        status = findViewById(R.id.guide_status);
        retry = findViewById(R.id.retry);
        retry.setOnClickListener(v -> load());
        Focus.lift(retry);
        rows.setLayoutManager(new LinearLayoutManager(this));
        rows.setItemAnimator(null);
        rows.setAdapter(adapter);
        base = Timeline.windowStart(System.currentTimeMillis() / 1000);
        from = base;
        rows.post(() -> {
            measure();
            layoutTimes();
            adapter.strips();
            focusFirstWhenLaid();
        });
        Library.forAccount(AccountStore.version());
        load();
    }

    /** Reads the channels' list; on failure, the reason and Réessayer, which takes the focus. */
    private void load() {
        retry.setVisibility(View.GONE);
        status(getString(R.string.loading));
        Library lib = Library.of(Xtream.Section.LIVE);
        lib.load(api, () -> {
            if (isDestroyed()) return;
            if (lib.error() != null && !lib.ready()) {
                status(lib.error().text(this));
                retry.setVisibility(View.VISIBLE);
                retry.requestFocus();
                return;
            }
            buildGroups();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        clock.removeCallbacks(tick);
        clock.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        clock.removeCallbacks(tick);
    }

    private void status(String text) {
        status.setText(text);
        status.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    // ---- groups ----

    private void buildGroups() {
        Library lib = Library.of(Xtream.Section.LIVE);
        Groups g = Groups.get(this);
        groups.clear();
        if (!favourites().isEmpty()) groups.add(new Group(getString(R.string.guide_favourites), null, true));
        if (lib.all() != null) groups.add(new Group(getString(R.string.guide_all), null, false));
        List<Xtream.Category> cats = lib.categories() == null ? new ArrayList<>() : lib.categories();
        for (Xtream.Category c : g == null ? cats : g.ordered(Xtream.Section.LIVE, cats, false)) {
            groups.add(new Group(g == null ? Groups.panelName(Xtream.Section.LIVE, c) : g.name(Xtream.Section.LIVE, c), c, false));
        }
        LinearLayout row = findViewById(R.id.guide_groups);
        row.removeAllViews();
        groupViews.clear();
        for (Group gr : groups) {
            TextView t = (TextView) LayoutInflater.from(this).inflate(R.layout.item_sort_pill, row, false);
            t.setText(gr.name);
            t.setOnClickListener(v -> choose(gr));
            Focus.lift(t);
            row.addView(t);
            groupViews.add(t);
        }
        if (groups.isEmpty()) {
            status(getString(R.string.empty));
            return;
        }
        choose(groups.get(0));
    }

    private List<Item> favourites() {
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Channels chs = Channels.get(this);
        History h = History.get(this);
        List<Item> picked = new ArrayList<>();
        if (chs != null) picked.addAll(chs.starredItems());
        if (h != null) picked.addAll(h.favourites(FAVOURITES));
        Library live = Library.of(Xtream.Section.LIVE);
        for (Item c : picked) {
            if (!seen.add(c.id) || (chs != null && chs.hidden(c.id))) continue;
            Item known = live.find(c.id);
            out.add(known != null ? known : c);
        }
        return out;
    }

    private void choose(Group g) {
        group = g;
        for (int i = 0; i < groups.size(); i++) {
            groupViews.get(i).setSelected(groups.get(i) == g);
            groupViews.get(i).setActivated(groups.get(i) == g);   // the chosen group in the accent
        }
        Library lib = Library.of(Xtream.Section.LIVE);
        Channels chs = Channels.get(this);
        if (g.favourites) {
            show(favourites());
        } else if (g.category == null) {
            List<Item> all = lib.all() == null ? new ArrayList<>() : lib.all();
            show(chs == null ? all : chs.visible(all));
        } else {
            List<Item> l = lib.items(g.category.id);
            if (l != null) {
                show(chs == null ? l : chs.visible(l));
            } else {
                status(getString(R.string.loading));
                lib.loadCategory(api, g.category.id, () -> {
                    if (isDestroyed() || group != g) return;
                    List<Item> got = lib.items(g.category.id);
                    List<Item> list = got == null ? new ArrayList<>() : got;
                    show(chs == null ? list : chs.visible(list));
                });
            }
        }
    }

    private void show(List<Item> list) {
        channels = new ArrayList<>(list);
        status(channels.isEmpty() ? getString(R.string.empty) : null);
        adapter.refresh();
        rows.scrollToPosition(0);
        // The first list goes straight to the grid; later ones keep the focus on the group just chosen.
        View f = getCurrentFocus();
        if (!shownOnce || f == null || !Root.inside(f, findViewById(R.id.guide_groups))) focusFirstWhenLaid();
        shownOnce = true;
    }

    /**
     * Puts the focus on the first line's first programme as soon as that line is laid out: now when it already is,
     * else at the end of the next layout pass of the list (one listener, removed once used).
     */
    private void focusFirstWhenLaid() {
        if (focusFirst()) return;
        rows.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            private int passes;

            @Override
            public void onGlobalLayout() {
                // A few passes at most: the list lays out its first lines in the pass after the data arrives.
                if (isDestroyed() || channels.isEmpty() || focusFirst() || ++passes > 8) {
                    rows.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
    }

    /** True when the focus is now in the lines (put there, or already there). */
    private boolean focusFirst() {
        View f = getCurrentFocus();
        if (f != null && Root.inside(f, rows)) return true;
        if (channels.isEmpty() || px <= 0) return false;
        RecyclerView.ViewHolder h = rows.findViewHolderForAdapterPosition(0);
        if (h == null || !h.itemView.isLaidOut()) return false;
        focusRow(0, -1);
        return true;
    }

    // ---- the hours ----

    /** How wide a minute is, and where the programmes start, once the screen is laid out. */
    private void measure() {
        int channel = getResources().getDimensionPixelSize(R.dimen.guide_channel_width) + Ui.dp(this, 6);
        stripStart = channel;
        px = Math.max(1, rows.getWidth() - channel) / (float) WINDOW_MIN;
    }

    private void layoutTimes() {
        if (px <= 0) return;
        axis.removeAllViews();
        for (int m = 0; m < WINDOW_MIN; m += Timeline.STEP_MIN) {
            TextView t = new TextView(this);
            t.setText(hm.format(new Date((from + m * 60L) * 1000)));
            t.setTextColor(getColor(R.color.lo_text_2));
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.lo_text_small));
            t.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            lp.leftMargin = Ui.dp(this, 6) + Math.round(m * px) + Ui.dp(this, 4);
            axis.addView(t, lp);
        }
        placeNow();
    }

    private void placeNow() {
        if (px <= 0) return;
        float m = Timeline.nowAt(System.currentTimeMillis() / 1000, from, WINDOW_MIN);
        nowLine.setVisibility(m < 0 ? View.GONE : View.VISIBLE);
        nowLine.setTranslationX(stripStart + m * px);
    }

    /** Moves the window by minutes (a step of half an hour), between now and six hours ahead. */
    private boolean shift(int minutes) {
        long next = from + minutes * 60L;
        if (next < base || next > base + (AHEAD_MIN - WINDOW_MIN) * 60L) return false;
        from = next;
        layoutTimes();
        return true;
    }

    // ---- the programmes ----

    private List<Epg.Programme> guide(Item c) {
        Long at = FETCHED.get(c.id);
        if (at != null && System.currentTimeMillis() - at > KEEP_MS) {
            CACHE.remove(c.id);
            FETCHED.remove(c.id);
        }
        List<Epg.Programme> l = CACHE.get(c.id);
        if (l == null && loading.add(c.id)) {
            String id = c.id;
            Xtream x = api;
            Bg.execute(() -> {
                List<Epg.Programme> got;
                try {
                    got = x.shortEpg(id, LIMIT);
                } catch (XtreamException | RuntimeException | OutOfMemoryError e) {
                    got = new ArrayList<>();
                }
                List<Epg.Programme> result = got;
                Bg.MAIN.post(() -> {
                    loading.remove(id);
                    CACHE.put(id, result);
                    FETCHED.put(id, System.currentTimeMillis());
                    if (isDestroyed()) return;
                    for (int i = 0; i < channels.size(); i++) {
                        if (!channels.get(i).id.equals(id)) continue;
                        adapter.notifyItemChanged(i, RowAdapter.STRIP);
                        View f = getCurrentFocus();
                        if (f == null || f.getId() != R.id.guide_channel) continue;
                        RecyclerView.ViewHolder h = rows.findViewHolderForAdapterPosition(i);
                        if (h == null || !Root.inside(f, h.itemView)) continue;
                        if (firstOnChannel && i == 0) {
                            int line = i;
                            rows.post(() -> focusRow(line, -1));   // once the blocks are bound
                        } else {
                            detail(channels.get(i), onAir(channels.get(i)));
                        }
                    }
                });
            });
        }
        return l;
    }

    private void play(Item c) {
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_PLAY_CHANNEL, c.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void detail(Item c, Epg.Programme p) {
        String name = LivePage.display(this, c);
        if (p == null) {
            detailTitle.setText(name);
            detailText.setText(R.string.live_no_guide);
            return;
        }
        detailTitle.setText(p.title);
        String when = hm.format(new Date(p.start * 1000)) + " – " + hm.format(new Date(p.end * 1000));
        String text = when + "  ·  " + name + (p.description.isEmpty() ? "" : "  ·  " + p.description);
        detailText.setText(text);
    }

    /** Focuses a line's programme at screen x (its first when x is -1), or its channel when it has none. */
    private void focusRow(int position, int x) {
        RecyclerView.ViewHolder h = rows.findViewHolderForAdapterPosition(position);
        if (h == null) return;
        ViewGroup strip = h.itemView.findViewById(R.id.guide_strip);
        View best = null;
        int bestD = Integer.MAX_VALUE;
        int[] at = new int[2];
        for (int i = 0; i < strip.getChildCount(); i++) {
            View b = strip.getChildAt(i);
            if (!b.isFocusable()) continue;
            b.getLocationOnScreen(at);
            int d = x < 0 ? i : Math.abs(at[0] + b.getWidth() / 2 - x);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        firstOnChannel = best == null && position == 0 && x < 0;
        (best != null ? best : h.itemView.findViewById(R.id.guide_channel)).requestFocus();
    }

    /** The programme on air for a channel, from the guide already fetched; null when there is none. */
    private static Epg.Programme onAir(Item c) {
        List<Epg.Programme> l = c == null ? null : CACHE.get(c.id);
        if (l == null) return null;
        long now = System.currentTimeMillis() / 1000;
        for (Epg.Programme p : l) if (p.start <= now && now < p.end) return p;
        return null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        firstOnChannel = false;   // a key pressed: the focus is the family's now, not the first one put there
        View f = getCurrentFocus();
        int k = e.getKeyCode();
        if (f != null && f.getParent() instanceof ViewGroup && ((View) f.getParent()).getId() == R.id.guide_strip
                && (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_LEFT)) {
            ViewGroup strip = (ViewGroup) f.getParent();
            boolean right = k == KeyEvent.KEYCODE_DPAD_RIGHT;
            int index = strip.indexOfChild(f);
            boolean edge = right ? index == strip.getChildCount() - 1 : index == 0;
            if (edge) {
                RecyclerView.ViewHolder h = rows.findContainingViewHolder(f);
                int position = h == null ? -1 : h.getBindingAdapterPosition();
                if (shift(right ? Timeline.STEP_MIN : -Timeline.STEP_MIN)) {
                    adapter.strips();
                    if (position >= 0) {
                        int[] at = new int[2];
                        f.getLocationOnScreen(at);
                        int x = right ? at[0] + f.getWidth() : at[0];
                        rows.post(() -> focusRow(position, x));
                    }
                    return true;
                }
                if (right) return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- lines ----

    /** Spare programme blocks, taken off lines that needed fewer, for lines that need more. */
    private final java.util.ArrayDeque<FrameLayout> spareBlocks = new java.util.ArrayDeque<>();

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        /** Payload: only the programmes changed (the window of hours moved, or a channel's guide arrived). */
        static final String STRIP = "strip";

        final class Holder extends RecyclerView.ViewHolder {
            final View channel, skeleton;
            final ImageView logo;
            final TextView initials, name;
            final FrameLayout strip;
            Item item;

            Holder(View v) {
                super(v);
                channel = v.findViewById(R.id.guide_channel);
                skeleton = v.findViewById(R.id.guide_logo_skeleton);
                logo = v.findViewById(R.id.guide_logo);
                initials = v.findViewById(R.id.guide_initials);
                name = v.findViewById(R.id.guide_name);
                strip = v.findViewById(R.id.guide_strip);
                channel.setOnClickListener(x -> {
                    if (item != null) play(item);
                });
                channel.setOnFocusChangeListener((x, focused) -> {
                    if (!focused || item == null) return;
                    detail(item, onAir(item));
                });
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // a new group: every line changes
        void refresh() {
            notifyDataSetChanged();
        }

        /** The window of hours moved: every line's programmes, not their channels. */
        void strips() {
            notifyItemRangeChanged(0, getItemCount(), STRIP);
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_guide_row, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder h, int position, List<Object> payloads) {
            if (payloads.contains(STRIP) && h.item != null) {
                bindStrip(h, h.item);
                return;
            }
            super.onBindViewHolder(h, position, payloads);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Item c = channels.get(position);
            h.item = c;
            String display = LivePage.display(GuideActivity.this, c);
            h.name.setText(c.num > 0 ? c.num + "  " + display : display);
            h.initials.setText(Ui.initials(display));
            Images.load(h.logo, c.image, Ui.dp(GuideActivity.this, 56), h.initials, h.skeleton);
            bindStrip(h, c);
        }

        /** Lays out the line's programmes, reusing the blocks it already has. */
        private void bindStrip(Holder h, Item c) {
            // What had the focus in this line, to put it back on the programme at the same time.
            long focusedAt = -1;
            for (int i = 0; i < h.strip.getChildCount(); i++) {
                View b = h.strip.getChildAt(i);
                if (b.isFocused() && b.getTag() instanceof Long) focusedAt = (Long) b.getTag();
            }
            boolean hadFocus = h.strip.hasFocus();
            if (px <= 0) {
                release(h.strip, 0);
                return;
            }
            List<Epg.Programme> list = guide(c);
            long now = System.currentTimeMillis() / 1000;
            int total = Math.max(1, rows.getWidth() - stripStart);
            int gap = Ui.dp(GuideActivity.this, 4), min = Ui.dp(GuideActivity.this, MIN_BLOCK_DP);
            View refocus = null;
            int used = 0;
            if (list == null) {
                // Not there yet: one block across the window, with a skeleton.
                refocus = block(h.strip, used++, 0, total, null, false, true, c, null);
            } else {
                List<Epg.Programme> shown = new ArrayList<>();
                List<float[]> spans = new ArrayList<>();
                for (Epg.Programme p : list) {
                    float[] span = Timeline.span(p.start, p.end, from, WINDOW_MIN);
                    if (span == null) continue;
                    shown.add(p);
                    spans.add(span);
                }
                int[][] at = Timeline.place(spans.toArray(new float[0][]), px, gap, min, total);
                for (int i = 0; i < shown.size(); i++) {
                    if (at[i] == null) continue;
                    Epg.Programme p = shown.get(i);
                    View b = block(h.strip, used++, at[i][0], at[i][1], p.title, p.start <= now && now < p.end, false, c, p);
                    b.setTag(p.start);
                    if (refocus == null || (focusedAt >= 0 && p.start <= focusedAt && focusedAt < p.end)) refocus = b;
                }
                if (used == 0) refocus = block(h.strip, used++, 0, total, getString(R.string.guide_none), false, false, c, null);
            }
            release(h.strip, used);
            if (hadFocus && refocus != null) refocus.requestFocus();
        }

        /** Gives back the blocks of a line from index from on to the spare ones. */
        private void release(FrameLayout strip, int from) {
            while (strip.getChildCount() > from) {
                View v = strip.getChildAt(strip.getChildCount() - 1);
                strip.removeViewAt(strip.getChildCount() - 1);
                v.setTag(null);
                spareBlocks.push((FrameLayout) v);
            }
        }

        /** The index-th block of a line (reused, or a spare one, or a new one), set for one programme. */
        private View block(FrameLayout strip, int index, int left, int width, String title, boolean now, boolean waiting,
                           Item c, Epg.Programme p) {
            FrameLayout box;
            if (index < strip.getChildCount()) {
                box = (FrameLayout) strip.getChildAt(index);
            } else {
                box = spareBlocks.isEmpty() ? newBlock() : spareBlocks.pop();
                strip.addView(box);
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
            if (lp.leftMargin != left || lp.width != width) {
                lp.leftMargin = left;
                lp.width = width;
                box.setLayoutParams(lp);
            }
            box.setActivated(now);
            TextView t = (TextView) box.getChildAt(0);
            t.setText(title);
            t.setVisibility(title == null ? View.GONE : View.VISIBLE);
            box.getChildAt(1).setVisibility(waiting ? View.VISIBLE : View.GONE);
            box.setContentDescription(title);
            box.setOnClickListener(v -> play(c));
            box.setOnFocusChangeListener((v, focused) -> {
                if (focused) detail(c, p);
            });
            return box;
        }

        /** A programme's box: its title (16 sp, dark on the white focus) and a skeleton while the guide loads. */
        private FrameLayout newBlock() {
            FrameLayout box = new FrameLayout(GuideActivity.this);
            box.setBackgroundResource(R.drawable.programme_bg);
            box.setFocusable(true);
            box.setLayoutParams(new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            TextView t = new TextView(GuideActivity.this);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.lo_text_body));
            t.setTextColor(getColorStateList(R.color.lo_text_on_focus));
            t.setDuplicateParentStateEnabled(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(Ui.dp(GuideActivity.this, 12), 0, Ui.dp(GuideActivity.this, 8), 0);
            box.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            SkeletonView s = new SkeletonView(GuideActivity.this);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(GuideActivity.this, 12), Gravity.CENTER_VERTICAL);
            sl.setMargins(Ui.dp(GuideActivity.this, 14), 0, Ui.dp(GuideActivity.this, 120), 0);
            box.addView(s, sl);
            return box;
        }

        @Override
        public int getItemCount() {
            return channels.size();
        }
    }
}
