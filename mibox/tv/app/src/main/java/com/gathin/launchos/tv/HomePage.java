package com.gathin.launchos.tv;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Accueil, the dashboard. In the top third, the hero: what is being watched (or the newest film) over its picture,
 * full screen, fading to black at the bottom, with Reprendre or Regarder and Plus d'infos. Underneath, the shelves:
 * Reprendre la lecture, Vos chaînes, Derniers films, Dernières séries. A focused card takes over the hero after a
 * short pause, so fast scrolling stays light. While the lists load, skeleton posters hold the shelves' place.
 */
final class HomePage {
    private static final int SHELF = 30;
    private static final int CHANNELS = 16;
    private static final long SPOT_DELAY_MS = 180, PICTURE_DELAY_MS = 420, INFO_DELAY_MS = 550;
    private static final int SKELETON_CARDS = 7;

    private static final class Shelf {
        final String key, title;
        final Cards.Shape shape;
        final List<Item> items;

        Shelf(String key, String title, Cards.Shape shape, List<Item> items) {
            this.key = key;
            this.title = title;
            this.shape = shape;
            this.items = items;
        }
    }

    private final MainActivity activity;
    final View root;
    private final Backdrop backdrop;
    private final View hero, buttons, skeleton;
    private final TextView label, title, meta, plot, play, status;
    private final RecyclerView rowsView;
    private final LinearLayoutManager rowsLayout;
    private final ShelfAdapter adapter = new ShelfAdapter();
    private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
    private final Map<String, Integer> positions = new HashMap<>();

    private Xtream api;
    private Item heroItem;
    private boolean heroResume;
    private Item pending;
    private int shownGeneration = -1, shownHistory = -1, shownGroups = -1;
    private final Runnable spotNow = () -> spot(pending);
    private final Runnable pictureNow = () -> picture(pending);
    private final Runnable infoNow = () -> info(pending);

    HomePage(MainActivity activity, View root) {
        this.activity = activity;
        this.root = root;
        backdrop = new Backdrop(root.findViewById(R.id.backdrop_a), root.findViewById(R.id.backdrop_b));
        hero = root.findViewById(R.id.home_hero);
        buttons = root.findViewById(R.id.home_buttons);
        skeleton = root.findViewById(R.id.home_skeleton);
        label = root.findViewById(R.id.home_label);
        title = root.findViewById(R.id.home_title);
        meta = root.findViewById(R.id.home_meta);
        plot = root.findViewById(R.id.home_plot);
        play = root.findViewById(R.id.home_play);
        status = root.findViewById(R.id.home_status);
        rowsView = root.findViewById(R.id.home_rows);
        rowsLayout = new LinearLayoutManager(activity);
        rowsView.setLayoutManager(rowsLayout);
        rowsView.setAdapter(adapter);
        rowsView.setItemAnimator(null);
        play.setOnClickListener(v -> {
            if (heroItem != null) play(heroItem);
        });
        root.findViewById(R.id.home_info).setOnClickListener(v -> {
            if (heroItem != null) open(heroItem);
        });
        Ui.lift(play, 1.05f);
        Ui.lift(root.findViewById(R.id.home_info), 1.05f);
        LinearLayout cards = root.findViewById(R.id.home_skeleton_cards);
        for (int i = 0; i < SKELETON_CARDS; i++) {
            SkeletonView s = new SkeletonView(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    activity.getResources().getDimensionPixelSize(R.dimen.poster_width),
                    activity.getResources().getDimensionPixelSize(R.dimen.poster_height));
            lp.setMarginEnd(Ui.dp(activity, 12));
            cards.addView(s, lp);
        }
    }

    // ---- showing ----

    void show(Xtream api) {
        this.api = api;
        if (api == null) return;
        boolean same = shownGeneration == Library.generation() && shownHistory == History.version()
                && shownGroups == Groups.version() && adapter.getItemCount() > 0;
        if (same) return;
        if (adapter.getItemCount() == 0) skeleton.setVisibility(View.VISIBLE);
        build();
        for (Xtream.Section s : Xtream.Section.values()) {
            Library lib = Library.of(s);
            if (lib.ready()) continue;
            lib.load(api, () -> {
                if (!activity.isDestroyed()) build();
            });
        }
    }

    /** The shelves from what is known now; called again as each list arrives. */
    private void build() {
        shownGeneration = Library.generation();
        shownHistory = History.version();
        shownGroups = Groups.version();
        List<Shelf> shelves = new ArrayList<>();
        List<Item> resume = resume();
        if (!resume.isEmpty()) {
            shelves.add(new Shelf("resume", activity.getString(R.string.home_resume), Cards.Shape.POSTER, resume));
        }
        List<Item> channels = channels();
        if (!channels.isEmpty()) {
            shelves.add(new Shelf("channels", activity.getString(R.string.home_channels), Cards.Shape.LOGO, channels));
        }
        List<Item> films = recent(Xtream.Section.MOVIES);
        if (!films.isEmpty()) {
            shelves.add(new Shelf("films", activity.getString(R.string.row_recent_movies), Cards.Shape.POSTER, films));
        }
        List<Item> series = recent(Xtream.Section.SERIES);
        if (!series.isEmpty()) {
            shelves.add(new Shelf("series", activity.getString(R.string.row_recent_series), Cards.Shape.POSTER, series));
        }
        boolean loading = false;
        for (Xtream.Section s : Xtream.Section.values()) loading |= Library.of(s).loading();
        adapter.set(shelves);
        skeleton.setVisibility(shelves.isEmpty() && loading ? View.VISIBLE : View.GONE);
        status.setVisibility(shelves.isEmpty() && !loading ? View.VISIBLE : View.GONE);
        status.setText(R.string.home_empty);

        // The hero: what is being watched, else the newest film, else the newest series.
        heroResume = !resume.isEmpty();
        Item h = heroResume ? resume.get(0) : !films.isEmpty() ? films.get(0) : !series.isEmpty() ? series.get(0) : null;
        boolean changed = h == null ? heroItem != null : heroItem == null || !h.id.equals(heroItem.id);
        heroItem = h;
        if (changed || title.getText().length() == 0) showHero();
    }

    /** Films stopped part way and series under way, the most recent first. */
    private List<Item> resume() {
        History h = History.get(activity);
        List<Item> out = new ArrayList<>();
        if (h == null) return out;
        List<Object[]> dated = new ArrayList<>();
        Library films = Library.of(Xtream.Section.MOVIES), series = Library.of(Xtream.Section.SERIES);
        for (History.Entry e : h.films(SHELF)) {
            Item known = films.find(e.id);
            dated.add(new Object[]{e.at, known != null ? known : new Item(Item.Kind.MOVIE, e.id, e.name, e.image, e.ext, 0, "")});
        }
        for (Item s : h.recentSeries(SHELF)) {
            Item known = series.find(s.id);
            dated.add(new Object[]{s.added * 1000, known != null ? known : s});
        }
        dated.sort((x, y) -> Long.compare((Long) y[0], (Long) x[0]));
        for (Object[] d : dated) if (out.size() < SHELF) out.add((Item) d[1]);
        return out;
    }

    /** The channels starred by hand, then the most watched, without hidden ones. */
    private List<Item> channels() {
        List<Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Channels chs = Channels.get(activity);
        History h = History.get(activity);
        List<Item> picked = new ArrayList<>();
        if (chs != null) picked.addAll(chs.starredItems());
        if (h != null) picked.addAll(h.favourites(CHANNELS));
        Library live = Library.of(Xtream.Section.LIVE);
        for (Item c : picked) {
            if (out.size() >= CHANNELS || !seen.add(c.id) || (chs != null && chs.hidden(c.id))) continue;
            Item known = live.find(c.id);
            out.add(known != null ? known : c);
        }
        return out;
    }

    /** A section's newest titles, outside hidden groups. */
    private List<Item> recent(Xtream.Section s) {
        Library lib = Library.of(s);
        Groups g = Groups.get(activity);
        List<Item> out = new ArrayList<>();
        for (Item i : lib.recent()) {
            if (out.size() >= SHELF) break;
            if (g == null || !g.hidden(s, i.categoryId)) out.add(i);
        }
        return out;
    }

    // ---- the hero ----

    private void showHero() {
        buttons.setVisibility(heroItem == null ? View.GONE : View.VISIBLE);
        if (heroItem == null) {
            label.setText("");
            title.setText(R.string.nav_home);
            meta.setText("");
            plot.setText("");
            backdrop.clear();
            return;
        }
        label.setText(heroResume ? R.string.home_label_resume : R.string.home_label_new);
        play.setText(heroResume ? R.string.hero_resume : R.string.hero_play);
        fill(heroItem);
        picture(heroItem);
        info(heroItem);
    }

    private void fill(Item i) {
        title.setText(i.kind == Item.Kind.LIVE ? LivePage.display(activity, i) : i.title);
        Xtream.VodInfo vi = i.kind == Item.Kind.MOVIE ? Details.cached(i.id) : null;
        String genre = vi != null && !vi.genre.isEmpty() ? vi.genre : i.genre;
        int year = i.year != 0 ? i.year : vi != null ? vi.year : 0;
        float rating = i.rating > 0 ? i.rating : vi != null ? vi.rating : 0;
        String kind = activity.getString(i.kind == Item.Kind.SERIES ? R.string.home_kind_series
                : i.kind == Item.Kind.LIVE ? R.string.home_kind_live : R.string.home_kind_film);
        meta.setText(Ui.meta(kind, year > 0 ? String.valueOf(year) : "", Smart.firstGenre(genre), Ui.rating(rating), i.tag));
        String p = vi != null && !vi.plot.isEmpty() ? vi.plot : i.plot;
        plot.setText(p);
    }

    private void picture(Item i) {
        if (i == null) return;
        Xtream.VodInfo vi = i.kind == Item.Kind.MOVIE ? Details.cached(i.id) : null;
        backdrop.show(vi != null ? vi.backdrop : "", i.backdrop, vi != null ? vi.poster : "", i.image);
    }

    private void info(Item i) {
        if (i == null || i.kind != Item.Kind.MOVIE || api == null || Details.cached(i.id) != null) return;
        Details.get(api, i.id, vi -> {
            if (vi == null || activity.isDestroyed()) return;
            Item showing = pending != null ? pending : heroItem;
            if (showing != null && showing.id.equals(i.id)) {
                fill(i);
                picture(i);
            }
        });
    }

    /** A card got the focus: the hero follows it a moment later. */
    private void cardFocused(Shelf shelf, Item item) {
        pending = item;
        root.removeCallbacks(spotNow);
        root.removeCallbacks(pictureNow);
        root.removeCallbacks(infoNow);
        if (item == null) return;
        label.setText(shelf.title);
        root.postDelayed(spotNow, SPOT_DELAY_MS);
        root.postDelayed(pictureNow, PICTURE_DELAY_MS);
        root.postDelayed(infoNow, INFO_DELAY_MS);
    }

    private void spot(Item i) {
        if (i != null) fill(i);
    }

    /** The focus moved on this page: back on the hero's buttons, the hero shows its own title again. */
    void focusChanged(View now) {
        if (Root.inside(now, buttons) && pending != null) {
            pending = null;
            root.removeCallbacks(spotNow);
            root.removeCallbacks(pictureNow);
            root.removeCallbacks(infoNow);
            showHero();
        }
    }

    // ---- opening ----

    private void open(Item i) {
        if (i.kind == Item.Kind.LIVE) activity.playChannel(i);
        else if (i.kind == Item.Kind.SERIES) activity.startActivity(SeriesActivity.intent(activity, i, false));
        else activity.startActivity(MovieActivity.intent(activity, i));
    }

    private void play(Item i) {
        if (i.kind == Item.Kind.LIVE) {
            activity.playChannel(i);
        } else if (i.kind == Item.Kind.SERIES) {
            activity.startActivity(SeriesActivity.intent(activity, i, true));
        } else if (api != null) {
            History h = History.get(activity);
            activity.startActivity(PlayerActivity.film(activity, api, i, h == null ? 0 : h.position(Item.Kind.MOVIE, i.id)));
        }
    }

    // ---- focus ----

    View defaultFocus() {
        if (heroItem != null) return play;
        return cardIn(0, -1);
    }

    /** Where an arrow goes inside the page; null hands it to the main screen (the rail). */
    View route(View focused, int direction, View proposed) {
        boolean inRows = Root.inside(focused, rowsView);
        if (inRows) {
            RecyclerView.ViewHolder holder = rowsView.findContainingViewHolder(focused);
            int row = holder == null ? 0 : holder.getBindingAdapterPosition();
            if (direction == View.FOCUS_UP) {
                if (row <= 0) return heroItem != null ? play : focused;
                return moveRow(focused, row - 1, -1);
            }
            if (direction == View.FOCUS_DOWN) {
                if (row >= adapter.getItemCount() - 1) return focused;
                return moveRow(focused, row + 1, 1);
            }
            if (proposed != null && holder != null && Root.inside(proposed, holder.itemView)) return proposed;
            return direction == View.FOCUS_LEFT ? null : focused;
        }
        if (Root.inside(focused, buttons)) {
            if (direction == View.FOCUS_DOWN) {
                View card = cardIn(0, -1);
                return card != null ? card : focused;
            }
            if (direction == View.FOCUS_UP) return focused;
            if (direction == View.FOCUS_LEFT) return Root.inside(proposed, buttons) ? proposed : null;
            if (direction == View.FOCUS_RIGHT) return Root.inside(proposed, buttons) ? proposed : focused;
        }
        return proposed;
    }

    /** Moves to another shelf, keeping it at the top of the shelves, onto the card under the focus. */
    private View moveRow(View focused, int target, int step) {
        int[] at = new int[2];
        focused.getLocationOnScreen(at);
        int x = at[0] + focused.getWidth() / 2;
        rowsLayout.scrollToPositionWithOffset(target, 0);
        rowsView.setTranslationY(step * Ui.dp(activity, 30));
        rowsView.animate().translationY(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        rowsView.post(() -> {
            View card = cardIn(target, x);
            if (card != null) card.requestFocus();
        });
        return focused;
    }

    /** The card in a shelf nearest to screen x (or where the shelf was left when x is -1). */
    private View cardIn(int row, int x) {
        View rowView = rowsLayout.findViewByPosition(row);
        if (rowView == null) return null;
        RecyclerView list = rowView.findViewById(R.id.row_list);
        if (list.getChildCount() == 0) return null;
        ShelfAdapter.Holder h = (ShelfAdapter.Holder) rowsView.getChildViewHolder(rowView);
        Integer left = h.shelf == null ? null : positions.get(h.shelf.key);
        if (x < 0 && left != null && list.getLayoutManager() != null) {
            View v = list.getLayoutManager().findViewByPosition(left);
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

    // ---- shelves ----

    private final class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.Holder> {
        List<Shelf> shelves = new ArrayList<>();

        final class Holder extends RecyclerView.ViewHolder {
            final TextView title, count;
            final RecyclerView list;
            final Cards.Adapter cards;
            Shelf shelf;

            Holder(View v, Cards.Shape shape) {
                super(v);
                title = v.findViewById(R.id.row_title);
                count = v.findViewById(R.id.row_count);
                list = v.findViewById(R.id.row_list);
                list.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
                if (shape == Cards.Shape.POSTER) list.setRecycledViewPool(pool);
                list.setItemAnimator(null);
                cards = new Cards.Adapter(shape, new Cards.Listener() {
                    @Override
                    public void focused(Item item) {
                        if (shelf == null) return;
                        positions.put(shelf.key, cards.lastPosition());
                        cardFocused(shelf, item);
                    }

                    @Override
                    public void clicked(Item item, int position) {
                        open(item);
                    }
                });
                list.setAdapter(cards);
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Shelf> list) {
            shelves = list;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return shelves.get(position).shape.ordinal();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false),
                    Cards.Shape.values()[type]);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Shelf s = shelves.get(position);
            h.shelf = s;
            h.title.setText(s.title);
            h.count.setText("");
            h.cards.set(s.items, -1);
            Integer left = positions.get(s.key);
            ((LinearLayoutManager) h.list.getLayoutManager()).scrollToPositionWithOffset(left == null ? 0 : left, 0);
        }

        @Override
        public int getItemCount() {
            return shelves.size();
        }
    }
}
