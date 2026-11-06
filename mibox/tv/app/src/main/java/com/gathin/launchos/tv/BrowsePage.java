package com.gathin.launchos.tv;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Films or Séries, full screen. On top, the spotlight: the newest titles one after the other with a slow fade
 * (Regarder, Plus d'infos), or, once the focus is in the rows, the focused title over its picture. Underneath, the
 * rows: Reprendre, the newest, then each group (pinned first, then A to Z), each ending with Tout voir. Up and Down
 * go from row to row, the focused row always at the same height.
 */
final class BrowsePage {
    private static final int HERO_COUNT = 7;
    private static final long HERO_MS = 8000;
    private static final long SPOT_DELAY_MS = 160, PICTURE_DELAY_MS = 420, INFO_DELAY_MS = 550;
    private static final int ROW_ITEMS = 40;

    private enum RowKind { CONTINUE, RECENT, GROUP }

    private static final class Row {
        final RowKind kind;
        final String title;
        final Xtream.Category category;
        List<Item> items;
        int total;

        Row(RowKind kind, String title, Xtream.Category category, List<Item> items, int total) {
            this.kind = kind;
            this.title = title;
            this.category = category;
            this.items = items;
            this.total = total;
        }

        String key() {
            return kind == RowKind.GROUP ? "g:" + category.id : kind.name();
        }
    }

    private final MainActivity activity;
    final View root;
    final Xtream.Section section;
    private final boolean movies;
    private final Backdrop backdrop;
    private final View spotlight, heroButtons, topActions, scrim;
    private final TextView label, title, meta, plot, heroPlay, browseStatus;
    private final LinearLayout dots;
    private final RecyclerView rowsView;
    private final LinearLayoutManager rowsLayout;
    private final RowAdapter rowAdapter = new RowAdapter();
    private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
    /** Where each row was left, so coming back to it lands on the same card. */
    private final Map<String, Integer> rowPositions = new HashMap<>();

    private Xtream api;
    private List<Item> hero = new ArrayList<>();
    private int heroIndex;
    private boolean heroMode = true;
    private Item spotItem;
    private int shownGeneration = -1, shownGroups = -1, shownHistory = -1;
    private final Runnable nextHero = new Runnable() {
        @Override
        public void run() {
            if (!hero.isEmpty() && heroMode && activity.showing(BrowsePage.this)) {
                heroIndex = (heroIndex + 1) % hero.size();
                showHero(true);
            }
            root.postDelayed(this, HERO_MS);
        }
    };
    private Item pendingSpot;
    private final Runnable spotNow = () -> spot(pendingSpot);
    private final Runnable pictureNow = () -> picture(pendingSpot);
    private final Runnable infoNow = () -> fetchInfo(pendingSpot);

    BrowsePage(MainActivity activity, View root, Xtream.Section section) {
        this.activity = activity;
        this.root = root;
        this.section = section;
        movies = section == Xtream.Section.MOVIES;
        backdrop = new Backdrop(root.findViewById(R.id.backdrop_a), root.findViewById(R.id.backdrop_b));
        spotlight = root.findViewById(R.id.spotlight);
        heroButtons = root.findViewById(R.id.hero_buttons);
        topActions = root.findViewById(R.id.top_actions);
        scrim = root.findViewById(R.id.scrim_bottom);
        label = root.findViewById(R.id.spot_label);
        title = root.findViewById(R.id.spot_title);
        meta = root.findViewById(R.id.spot_meta);
        plot = root.findViewById(R.id.spot_plot);
        dots = root.findViewById(R.id.hero_dots);
        heroPlay = root.findViewById(R.id.hero_play);
        browseStatus = root.findViewById(R.id.browse_status);
        rowsView = root.findViewById(R.id.rows);
        rowsLayout = new LinearLayoutManager(activity);
        rowsView.setLayoutManager(rowsLayout);
        rowsView.setAdapter(rowAdapter);
        rowsView.setItemAnimator(null);
        TextView search = root.findViewById(R.id.action_search);
        TextView all = root.findViewById(R.id.action_all);
        search.setText(movies ? R.string.browse_search_movies : R.string.browse_search_series);
        all.setText(movies ? R.string.browse_all_movies : R.string.browse_all_series);
        search.setOnClickListener(v -> activity.startActivity(new Intent(activity, SearchActivity.class)
                .putExtra(SearchActivity.EXTRA_SECTION, section.name())));
        all.setOnClickListener(v -> openGrid(null, all.getText().toString()));
        heroPlay.setOnClickListener(v -> {
            Item i = heroItem();
            if (i != null) play(i);
        });
        root.findViewById(R.id.hero_info).setOnClickListener(v -> {
            Item i = heroItem();
            if (i != null) open(i);
        });
        heroPlay.setText(movies ? R.string.hero_play : R.string.hero_play);
        rowsView.setTranslationY(heroShift());
    }

    private float heroShift() {
        return Ui.dp(activity, 150);
    }

    // ---- showing ----

    void show(Xtream api) {
        this.api = api;
        root.removeCallbacks(nextHero);
        root.postDelayed(nextHero, HERO_MS);
        Library lib = Library.of(section);
        if (api == null) {
            rowAdapter.set(new ArrayList<>());
            return;
        }
        if (lib.ready() && shownGeneration == Library.generation() && shownGroups == Groups.version()
                && rowAdapter.getItemCount() > 0) {
            if (shownHistory != History.version()) refreshContinue();
            return;
        }
        if (!lib.ready()) {
            status(activity.getString(R.string.loading_section));
            spotlight.setVisibility(View.INVISIBLE);
        }
        lib.load(api, () -> {
            if (activity.isDestroyed()) return;
            if (lib.error() != null && !lib.ready()) {
                status(lib.error().french());
                return;
            }
            build();
        });
    }

    void hidden() {
        root.removeCallbacks(nextHero);
    }

    private void status(String text) {
        browseStatus.setText(text);
        browseStatus.setVisibility(View.VISIBLE);
    }

    private void build() {
        Library lib = Library.of(section);
        Groups g = Groups.get(activity);
        shownGeneration = Library.generation();
        shownGroups = Groups.version();
        shownHistory = History.version();
        browseStatus.setVisibility(View.GONE);
        spotlight.setVisibility(View.VISIBLE);

        // Hidden groups stay out of the hero, the newest row and search.
        java.util.Set<String> hidden = new java.util.HashSet<>();
        List<Xtream.Category> cats = lib.categories() == null ? new ArrayList<>() : lib.categories();
        if (g != null) for (Xtream.Category c : cats) if (g.hidden(section, c.id)) hidden.add(c.id);
        List<Item> recent = new ArrayList<>();
        for (Item i : lib.recent()) if (!hidden.contains(i.categoryId)) recent.add(i);

        hero = new ArrayList<>();
        for (Item i : recent) {
            if (hero.size() >= HERO_COUNT) break;
            if (!i.image.isEmpty() || !i.backdrop.isEmpty()) hero.add(i);
        }
        heroIndex = 0;
        buildDots();

        List<Row> rows = new ArrayList<>();
        Row cont = continueRow();
        if (cont != null) rows.add(cont);
        if (!recent.isEmpty()) {
            rows.add(new Row(RowKind.RECENT, activity.getString(movies ? R.string.row_recent_movies : R.string.row_recent_series),
                    null, cap(recent), -1));
        }
        for (Xtream.Category c : g == null ? cats : g.ordered(section, cats, false)) {
            List<Item> items = lib.items(c.id);
            if (!lib.partial() && (items == null || items.isEmpty())) continue;
            String name = g == null ? Groups.panelName(section, c) : g.name(section, c);
            rows.add(new Row(RowKind.GROUP, name, c, items == null ? null : newestFirst(items),
                    items == null ? 0 : items.size()));
        }
        rowAdapter.set(rows);
        if (rows.isEmpty()) status(activity.getString(R.string.empty));
        rowsLayout.scrollToPositionWithOffset(0, 0);
        if (heroMode || hero.isEmpty()) showHero(false);
    }

    private Row continueRow() {
        History h = History.get(activity);
        if (h == null) return null;
        Library lib = Library.of(section);
        List<Item> items = new ArrayList<>();
        if (movies) {
            for (History.Entry e : h.films(20)) {
                Item known = lib.find(e.id);
                items.add(known != null ? known : new Item(Item.Kind.MOVIE, e.id, e.name, e.image, e.ext, 0, ""));
            }
        } else {
            for (Item stored : h.recentSeries(20)) {
                Item known = lib.find(stored.id);
                items.add(known != null ? known : stored);
            }
        }
        return items.isEmpty() ? null
                : new Row(RowKind.CONTINUE, activity.getString(R.string.row_continue), null, items, -1);
    }

    /** After a film was watched: only Reprendre changes. */
    private void refreshContinue() {
        shownHistory = History.version();
        Row cont = continueRow();
        List<Row> rows = new ArrayList<>(rowAdapter.rows);
        boolean had = !rows.isEmpty() && rows.get(0).kind == RowKind.CONTINUE;
        if (had && cont != null) {
            rows.set(0, cont);
            rowAdapter.rows = rows;
            rowAdapter.notifyItemChanged(0);
        } else if (had) {
            rows.remove(0);
            rowAdapter.rows = rows;
            rowAdapter.notifyItemRemoved(0);
        } else if (cont != null) {
            rows.add(0, cont);
            rowAdapter.rows = rows;
            rowAdapter.notifyItemInserted(0);
        }
    }

    private static List<Item> cap(List<Item> l) {
        return l.size() > ROW_ITEMS ? new ArrayList<>(l.subList(0, ROW_ITEMS)) : l;
    }

    /** A group's row: its newest first (panels list them oldest first more often than not). */
    private static List<Item> newestFirst(List<Item> items) {
        List<Item> l = new ArrayList<>(items);
        boolean dated = false;
        for (Item i : l) if (i.added > 0) dated = true;
        if (dated) l.sort((a, b) -> Long.compare(b.added, a.added));
        return cap(l);
    }

    // ---- the hero ----

    private Item heroItem() {
        return hero.isEmpty() ? null : hero.get(Math.min(heroIndex, hero.size() - 1));
    }

    private void buildDots() {
        dots.removeAllViews();
        dots.setVisibility(hero.size() > 1 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < hero.size(); i++) {
            View d = new View(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(activity, 6), Ui.dp(activity, 6));
            lp.setMarginEnd(Ui.dp(activity, 6));
            d.setLayoutParams(lp);
            dots.addView(d);
        }
    }

    private void markDots() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == heroIndex;
            d.setBackgroundResource(on ? R.drawable.dot_on : R.drawable.dot_off);
            ViewGroup.LayoutParams lp = d.getLayoutParams();
            lp.width = Ui.dp(activity, on ? 20 : 6);
            d.setLayoutParams(lp);
        }
    }

    private void showHero(boolean fade) {
        Item i = heroItem();
        heroButtons.setVisibility(i == null ? View.GONE : View.VISIBLE);
        if (i == null) {
            label.setText("");
            title.setText(movies ? R.string.nav_movies : R.string.nav_series);
            meta.setText("");
            plot.setText("");
            backdrop.clear();
            return;
        }
        markDots();
        Runnable fill = () -> {
            label.setText(movies ? R.string.hero_label_movies : R.string.hero_label_series);
            fillText(i);
            History h = History.get(activity);
            boolean resume = movies && h != null && h.position(Item.Kind.MOVIE, i.id) > 0;
            heroPlay.setText(resume ? R.string.hero_resume : R.string.hero_play);
        };
        if (fade) crossfadeText(fill);
        else fill.run();
        picture(i);
        fetchInfo(i);
        // The next one's picture, so the fade is ready in time.
        if (hero.size() > 1) {
            Item next = hero.get((heroIndex + 1) % hero.size());
            Xtream.VodInfo vi = movies ? Details.cached(next.id) : null;
            Images.get(activity, vi != null && !vi.backdrop.isEmpty() ? vi.backdrop
                    : !next.backdrop.isEmpty() ? next.backdrop : next.image, activity.getResources().getDisplayMetrics().widthPixels / 2, b -> {
            });
            if (movies) Details.get(api, next.id, x -> {
            });
        }
    }

    private void crossfadeText(Runnable change) {
        spotlight.animate().cancel();
        spotlight.animate().alpha(0f).translationX(-Ui.dp(activity, 12)).setDuration(180).withEndAction(() -> {
            change.run();
            spotlight.setTranslationX(Ui.dp(activity, 16));
            spotlight.animate().alpha(1f).translationX(0).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    /** Title, the line under it and the summary, from what is known now (a film's sheet comes later). */
    private void fillText(Item i) {
        title.setText(i.title);
        Xtream.VodInfo vi = movies ? Details.cached(i.id) : null;
        String genre = vi != null && !vi.genre.isEmpty() ? vi.genre : i.genre;
        int year = i.year != 0 ? i.year : vi != null ? vi.year : 0;
        float rating = i.rating > 0 ? i.rating : vi != null ? vi.rating : 0;
        String length = vi != null && vi.seconds > 0 ? Players.duration(vi.seconds * 1000L) : "";
        meta.setText(Ui.meta(year > 0 ? String.valueOf(year) : "", length, firstGenre(genre), Ui.rating(rating), i.tag));
        String p = vi != null && !vi.plot.isEmpty() ? vi.plot : i.plot;
        plot.setText(p);
        plot.setVisibility(p.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    private static String firstGenre(String genre) {
        if (genre == null) return "";
        String[] parts = genre.split("[,/|]");
        return parts.length == 0 ? "" : parts[0].trim();
    }

    private void picture(Item i) {
        if (i == null) return;
        Xtream.VodInfo vi = movies ? Details.cached(i.id) : null;
        backdrop.show(vi != null ? vi.backdrop : "", i.backdrop, vi != null ? vi.poster : "", i.image);
    }

    private void fetchInfo(Item i) {
        if (i == null || !movies || Details.cached(i.id) != null) return;
        Details.get(api, i.id, info -> {
            if (info == null || activity.isDestroyed()) return;
            Item showing = heroMode ? heroItem() : spotItem;
            if (showing != null && showing.id.equals(i.id)) {
                fillText(i);
                picture(i);
            }
        });
    }

    // ---- modes ----

    /** The focus moved somewhere on this page. */
    void focusChanged(View now) {
        boolean inRows = Root.inside(now, rowsView);
        if (inRows && heroMode) setHeroMode(false);
        else if (!inRows && (Root.inside(now, heroButtons) || Root.inside(now, topActions)) && !heroMode) setHeroMode(true);
    }

    private void setHeroMode(boolean on) {
        heroMode = on;
        rowsView.animate().translationY(on ? heroShift() : 0).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
        spotlight.animate().translationY(on ? 0 : -Ui.dp(activity, 44)).setDuration(320).start();
        heroButtons.setVisibility(on && !hero.isEmpty() ? View.VISIBLE : View.GONE);
        dots.setVisibility(on && hero.size() > 1 ? View.VISIBLE : View.GONE);
        topActions.animate().alpha(on ? 1f : 0f).setDuration(200).start();
        scrim.animate().alpha(on ? 0.8f : 1f).setDuration(320).start();
        if (on) {
            root.removeCallbacks(nextHero);
            root.postDelayed(nextHero, HERO_MS);
            crossfadeText(() -> {
                Item i = heroItem();
                if (i == null) return;
                label.setText(movies ? R.string.hero_label_movies : R.string.hero_label_series);
                fillText(i);
            });
            picture(heroItem());
        }
    }

    /** A card got the focus: the spotlight follows it, its picture a little later (fast scrolling stays light). */
    private void cardFocused(Row row, Item item) {
        pendingSpot = item;
        spotItem = item;
        root.removeCallbacks(spotNow);
        root.removeCallbacks(pictureNow);
        root.removeCallbacks(infoNow);
        root.postDelayed(spotNow, SPOT_DELAY_MS);
        if (item != null) {
            root.postDelayed(pictureNow, PICTURE_DELAY_MS);
            root.postDelayed(infoNow, INFO_DELAY_MS);
        }
        label.setText(row.title);
    }

    private void spot(Item i) {
        if (heroMode) return;
        if (i == null) {
            title.setText(R.string.row_more);
            meta.setText("");
            plot.setText("");
            return;
        }
        fillText(i);
    }

    // ---- opening ----

    private void open(Item i) {
        if (i.kind == Item.Kind.SERIES) {
            activity.startActivity(SeriesActivity.intent(activity, i, false));
        } else {
            activity.startActivity(MovieActivity.intent(activity, i));
        }
    }

    private void play(Item i) {
        if (i.kind == Item.Kind.SERIES) {
            activity.startActivity(SeriesActivity.intent(activity, i, true));
            return;
        }
        if (api == null) return;
        History h = History.get(activity);
        long at = h == null ? 0 : h.position(Item.Kind.MOVIE, i.id);
        activity.startActivity(PlayerActivity.film(activity, api, i, at));
    }

    private void openGrid(Xtream.Category c, String name) {
        activity.startActivity(new Intent(activity, GridActivity.class)
                .putExtra(GridActivity.EXTRA_SECTION, section.name())
                .putExtra(GridActivity.EXTRA_CATEGORY, c == null ? null : c.id)
                .putExtra(GridActivity.EXTRA_TITLE, name));
    }

    // ---- focus ----

    View defaultFocus() {
        if (!hero.isEmpty()) return heroPlay;
        View card = cardIn(0, -1);
        return card != null ? card : root.findViewById(R.id.action_search);
    }

    /** Where an arrow goes inside the page; null hands it to the main screen (the rail). */
    View route(View focused, int direction, View proposed) {
        boolean inRows = Root.inside(focused, rowsView);
        boolean inHero = Root.inside(focused, heroButtons);
        boolean inTop = Root.inside(focused, topActions);
        if (inRows) {
            RecyclerView.ViewHolder rowHolder = rowsView.findContainingViewHolder(focused);
            int row = rowHolder == null ? 0 : rowHolder.getBindingAdapterPosition();
            if (direction == View.FOCUS_UP) {
                if (row <= 0) {
                    View up = !hero.isEmpty() ? heroPlay : root.findViewById(R.id.action_search);
                    setHeroMode(true);
                    return up;
                }
                return moveRow(focused, row - 1, -1);
            }
            if (direction == View.FOCUS_DOWN) {
                if (row >= rowAdapter.getItemCount() - 1) return focused;
                return moveRow(focused, row + 1, 1);
            }
            // Left and Right stay in the row; Left at its first card opens the rail.
            if (proposed != null && rowHolder != null && Root.inside(proposed, rowHolder.itemView)) return proposed;
            return direction == View.FOCUS_LEFT ? null : focused;
        }
        if (inHero) {
            if (direction == View.FOCUS_DOWN) {
                View card = cardIn(0, -1);
                return card != null ? card : focused;
            }
            if (direction == View.FOCUS_UP) return root.findViewById(R.id.action_search);
            if (direction == View.FOCUS_LEFT) {
                return Root.inside(proposed, heroButtons) ? proposed : null;
            }
            if (direction == View.FOCUS_RIGHT) {
                if (Root.inside(proposed, heroButtons)) return proposed;
                // Right after the last button: the next title.
                if (hero.size() > 1) {
                    heroIndex = (heroIndex + 1) % hero.size();
                    showHero(true);
                    root.removeCallbacks(nextHero);
                    root.postDelayed(nextHero, HERO_MS);
                }
                return focused;
            }
        }
        if (inTop) {
            if (direction == View.FOCUS_DOWN) return !hero.isEmpty() ? heroPlay : cardIn(0, -1);
            if (direction == View.FOCUS_UP) return focused;
            if (direction == View.FOCUS_LEFT) return Root.inside(proposed, topActions) ? proposed : null;
            if (direction == View.FOCUS_RIGHT) return Root.inside(proposed, topActions) ? proposed : focused;
        }
        return proposed;
    }

    /** Moves to another row, keeping it at the top of the rows area, onto the card under the focus. */
    private View moveRow(View focused, int target, int step) {
        int[] at = new int[2];
        focused.getLocationOnScreen(at);
        int x = at[0] + focused.getWidth() / 2;
        rowsLayout.scrollToPositionWithOffset(target, 0);
        rowsView.setTranslationY(step * Ui.dp(activity, 36));
        rowsView.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        rowsView.post(() -> {
            View card = cardIn(target, x);
            if (card != null) card.requestFocus();
        });
        return focused;
    }

    /** The card in a row nearest to screen x (or where the row was left when x is -1). */
    private View cardIn(int row, int x) {
        View rowView = rowsLayout.findViewByPosition(row);
        if (rowView == null) return null;
        RecyclerView list = rowView.findViewById(R.id.row_list);
        if (list.getChildCount() == 0) return null;
        RowAdapter.Holder h = (RowAdapter.Holder) rowsView.getChildViewHolder(rowView);
        Integer left = h.row == null ? null : rowPositions.get(h.row.key());
        if (x < 0 && left != null) {
            RecyclerView.LayoutManager lm = list.getLayoutManager();
            View v = lm == null ? null : lm.findViewByPosition(left);
            if (v != null) return v;
        }
        View best = null;
        int bestDistance = Integer.MAX_VALUE;
        int[] at = new int[2];
        for (int i = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            c.getLocationOnScreen(at);
            if (at[0] < 0) continue;
            int d = x < 0 ? at[0] : Math.abs(at[0] + c.getWidth() / 2 - x);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }

    // ---- rows ----

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        List<Row> rows = new ArrayList<>();

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title, count;
            final RecyclerView list;
            Row row;
            final Cards.Adapter cards;

            Holder(View v) {
                super(v);
                title = v.findViewById(R.id.row_title);
                count = v.findViewById(R.id.row_count);
                list = v.findViewById(R.id.row_list);
                list.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
                list.setRecycledViewPool(pool);
                list.setItemAnimator(null);
                cards = new Cards.Adapter(Cards.Shape.POSTER, new Cards.Listener() {
                    @Override
                    public void focused(Item item) {
                        if (row == null) return;
                        rowPositions.put(row.key(), cards.lastPosition());
                        cardFocused(row, item);
                    }

                    @Override
                    public void clicked(Item item, int position) {
                        open(item);
                    }

                    @Override
                    public void more() {
                        if (row != null && row.kind == RowKind.GROUP) openGrid(row.category, row.title);
                    }
                });
                list.setAdapter(cards);
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Row> list) {
            rows = list;
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Row r = rows.get(position);
            h.row = r;
            h.title.setText(r.title);
            h.count.setText(r.total > 0 ? String.format(Locale.FRANCE, "%d", r.total) : "");
            if (r.items == null && r.kind == RowKind.GROUP) {
                h.cards.set(new ArrayList<>(), -1);
                h.count.setText(R.string.row_loading);
                Library lib = Library.of(section);
                lib.loadCategory(api, r.category.id, () -> {
                    List<Item> items = lib.items(r.category.id);
                    r.items = items == null ? new ArrayList<>() : newestFirst(items);
                    r.total = items == null ? 0 : items.size();
                    int p = rows.indexOf(r);
                    if (p >= 0) notifyItemChanged(p);
                });
                return;
            }
            h.cards.set(r.items, r.kind == RowKind.GROUP ? r.total : -1);
            Integer left = rowPositions.get(r.key());
            ((LinearLayoutManager) h.list.getLayoutManager()).scrollToPositionWithOffset(left == null ? 0 : left, 0);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
