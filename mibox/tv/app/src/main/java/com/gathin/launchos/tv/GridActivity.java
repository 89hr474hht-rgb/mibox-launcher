package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A whole group, or every film or series, as a grid of posters: Récents, A → Z, Mieux notés or Année. */
public final class GridActivity extends Activity {
    static final String EXTRA_SECTION = "section", EXTRA_CATEGORY = "category", EXTRA_TITLE = "title";
    private static final String PREFS = "grid";
    private static final int COLUMNS = 6;

    private enum Sort { RECENT, AZ, RATING, YEAR }

    private Xtream.Section section;
    private String categoryId;
    private List<Item> items = new ArrayList<>();
    private Sort sort = Sort.RECENT;
    private RecyclerView grid;
    private TextView status, count;
    private final TextView[] sortViews = new TextView[Sort.values().length];
    private Cards.Adapter adapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_grid);
        section = Xtream.Section.valueOf(getIntent().getStringExtra(EXTRA_SECTION));
        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY);
        ((TextView) findViewById(R.id.title)).setText(getIntent().getStringExtra(EXTRA_TITLE));
        status = findViewById(R.id.status);
        count = findViewById(R.id.count);
        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setItemAnimator(null);
        adapter = new Cards.Adapter(Cards.Shape.POSTER, new Cards.Listener() {
            @Override
            public void focused(Item item) {
            }

            @Override
            public void clicked(Item item, int position) {
                startActivity(item.kind == Item.Kind.SERIES ? SeriesActivity.intent(GridActivity.this, item, false)
                        : MovieActivity.intent(GridActivity.this, item));
            }
        });
        grid.setAdapter(adapter);
        try {
            sort = Sort.valueOf(getSharedPreferences(PREFS, MODE_PRIVATE).getString("sort", Sort.RECENT.name()));
        } catch (IllegalArgumentException e) {
            sort = Sort.RECENT;
        }
        LinearLayout sorts = findViewById(R.id.sorts);
        int[] names = {R.string.sort_recent, R.string.sort_az, R.string.sort_rating, R.string.sort_year};
        int[] icons = {R.drawable.ic_history, R.drawable.ic_text, R.drawable.ic_star_line, R.drawable.ic_guide};
        for (Sort s : Sort.values()) {
            TextView t = (TextView) LayoutInflater.from(this).inflate(R.layout.item_sort_pill, sorts, false);
            t.setText(names[s.ordinal()]);
            t.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[s.ordinal()], 0, 0, 0);
            t.setOnClickListener(v -> {
                sort = s;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("sort", s.name()).apply();
                apply();
            });
            sorts.addView(t);
            sortViews[s.ordinal()] = t;
        }
        Account a = AccountStore.load(this);
        Library lib = Library.of(section);
        status.setText(R.string.loading);
        status.setVisibility(View.VISIBLE);
        lib.load(a == null ? null : new Xtream(a), () -> {
            if (isDestroyed()) return;
            if (categoryId == null) {
                items = visible(lib);
                apply();
            } else {
                lib.loadCategory(a == null ? null : new Xtream(a), categoryId, () -> {
                    if (isDestroyed()) return;
                    List<Item> l = lib.items(categoryId);
                    items = l == null ? new ArrayList<>() : new ArrayList<>(l);
                    apply();
                });
            }
        });
    }

    /** Every item outside hidden groups. */
    private List<Item> visible(Library lib) {
        Groups g = Groups.get(this);
        List<Item> out = new ArrayList<>();
        List<Item> all = lib.all();
        if (all == null) {
            all = new ArrayList<>();
            if (lib.categories() != null) {
                for (Xtream.Category c : lib.categories()) {
                    List<Item> l = lib.items(c.id);
                    if (l != null) all.addAll(l);
                }
            }
        }
        for (Item i : all) if (g == null || !g.hidden(section, i.categoryId)) out.add(i);
        return out;
    }

    private void apply() {
        for (Sort s : Sort.values()) sortViews[s.ordinal()].setSelected(s == sort);
        List<Item> l = new ArrayList<>(items);
        switch (sort) {
            case RECENT:
                l.sort((x, y) -> Long.compare(y.added, x.added));
                break;
            case AZ:
                l.sort((x, y) -> x.key.compareTo(y.key));
                break;
            case RATING:
                l.sort((x, y) -> Float.compare(y.rating, x.rating));
                break;
            case YEAR:
                l.sort((x, y) -> y.year != x.year ? Integer.compare(y.year, x.year) : Long.compare(y.added, x.added));
                break;
        }
        adapter.set(l, -1);
        count.setText(String.format(Locale.FRANCE, "%d titre%s", l.size(), l.size() > 1 ? "s" : ""));
        status.setVisibility(l.isEmpty() ? View.VISIBLE : View.GONE);
        status.setText(R.string.empty);
        grid.scrollToPosition(0);
        if (getCurrentFocus() == null || !Root.inside(getCurrentFocus(), findViewById(R.id.sorts))) {
            grid.post(() -> {
                View first = grid.getLayoutManager() == null ? null : grid.getLayoutManager().findViewByPosition(0);
                if (first != null) first.requestFocus();
                else sortViews[sort.ordinal()].requestFocus();
            });
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Up from the first line of posters goes to the order buttons, onto the one in use.
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
            View f = getCurrentFocus();
            if (f != null && Root.inside(f, grid)) {
                RecyclerView.ViewHolder h = grid.findContainingViewHolder(f);
                if (h != null && h.getBindingAdapterPosition() < COLUMNS) {
                    sortViews[sort.ordinal()].requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }
}
