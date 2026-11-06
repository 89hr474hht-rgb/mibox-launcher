package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Search across channels, films and series as letters are typed, without accents or case. Names that start with
 * the words come first. Hidden groups stay out. From Films (or Séries), that section's results come first.
 */
public final class SearchActivity extends Activity {
    static final String EXTRA_SECTION = "section";
    private static final int MAX = 40;
    private static final long DELAY_MS = 220;

    private EditText query;
    private TextView status;
    private View scroll;
    private LinearLayout results;
    private Xtream api;
    private final List<Xtream.Section> order = new ArrayList<>();
    private int request;
    private final Runnable run = this::search;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_search);
        query = findViewById(R.id.query);
        status = findViewById(R.id.status);
        scroll = findViewById(R.id.results_scroll);
        results = findViewById(R.id.results);
        Account a = AccountStore.load(this);
        api = a == null ? null : new Xtream(a);
        String first = getIntent().getStringExtra(EXTRA_SECTION);
        if (first != null) order.add(Xtream.Section.valueOf(first));
        for (Xtream.Section s : new Xtream.Section[]{Xtream.Section.MOVIES, Xtream.Section.SERIES, Xtream.Section.LIVE}) {
            if (!order.contains(s)) order.add(s);
        }
        if (first != null) {
            query.setHint(Xtream.Section.valueOf(first) == Xtream.Section.MOVIES ? R.string.browse_search_movies
                    : R.string.browse_search_series);
        }
        for (Xtream.Section s : order) {
            Library.of(s).load(api, () -> {
                if (!isDestroyed() && query.length() > 0) schedule();
            });
        }
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                schedule();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        query.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                focusResults();
                return true;
            }
            return false;
        });
        query.requestFocus();
        query.postDelayed(() -> {
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
        }, 300);
    }

    private void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    private void focusResults() {
        if (results.getChildCount() == 0) return;
        RecyclerView list = results.getChildAt(0).findViewById(R.id.block_list);
        View first = list.getLayoutManager() == null ? null : list.getLayoutManager().findViewByPosition(0);
        if (first != null) first.requestFocus();
    }

    private void schedule() {
        query.removeCallbacks(run);
        query.postDelayed(run, DELAY_MS);
    }

    private void search() {
        String q = Names.fold(query.getText().toString());
        int r = ++request;
        if (q.isEmpty()) {
            results.removeAllViews();
            scroll.setVisibility(View.GONE);
            status.setText(R.string.search_type);
            status.setVisibility(View.VISIBLE);
            return;
        }
        // The lists and hidden groups are read here, then matched off the main thread.
        List<List<Item>> pools = new ArrayList<>();
        Groups g = Groups.get(this);
        for (Xtream.Section s : order) pools.add(known(Library.of(s), s, g));
        Bg.run(() -> {
            List<List<Item>> out = new ArrayList<>();
            for (List<Item> p : pools) out.add(match(p, q, MAX));
            return out;
        }, (found, error) -> {
            if (r != request || isDestroyed() || found == null) return;
            show(found);
        });
    }

    private static List<Item> known(Library lib, Xtream.Section s, Groups g) {
        List<Item> all = lib.all();
        if (all == null) {
            all = new ArrayList<>(lib.recent());
            if (lib.categories() != null) {
                for (Xtream.Category c : lib.categories()) {
                    List<Item> l = lib.items(c.id);
                    if (l != null) all.addAll(l);
                }
            }
        }
        if (g == null) return all;
        List<Item> out = new ArrayList<>(all.size());
        for (Item i : all) if (!g.hidden(s, i.categoryId)) out.add(i);
        return out;
    }

    /** The best matches: names starting with the query, then a word starting with it, then the rest. */
    static List<Item> match(List<Item> items, String foldedQuery, int max) {
        List<Item>[] byScore = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Item i : items) {
            int m = Names.match(i.key, foldedQuery);
            // The same title is often in several groups under other ids: shown once.
            if (m >= 0 && seen.add(i.id) && seen.add(i.key + "|" + i.year + "|" + i.tag)) byScore[m].add(i);
        }
        List<Item> out = new ArrayList<>();
        for (List<Item> l : byScore) {
            l.sort((a, b) -> a.key.length() != b.key.length() ? Integer.compare(a.key.length(), b.key.length())
                    : Long.compare(b.added, a.added));
            for (Item i : l) {
                if (out.size() >= max) return out;
                out.add(i);
            }
        }
        return out;
    }

    private void show(List<List<Item>> found) {
        results.removeAllViews();
        boolean any = false;
        for (int k = 0; k < order.size(); k++) {
            List<Item> l = found.get(k);
            if (l.isEmpty()) continue;
            any = true;
            Xtream.Section s = order.get(k);
            View block = LayoutInflater.from(this).inflate(R.layout.item_search_block, results, false);
            ((TextView) block.findViewById(R.id.block_title)).setText(s == Xtream.Section.LIVE ? R.string.search_channels
                    : s == Xtream.Section.MOVIES ? R.string.search_movies : R.string.search_series);
            RecyclerView list = block.findViewById(R.id.block_list);
            list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            list.setItemAnimator(null);
            Cards.Adapter a = new Cards.Adapter(s == Xtream.Section.LIVE ? Cards.Shape.LOGO : Cards.Shape.POSTER,
                    new Cards.Listener() {
                        @Override
                        public void focused(Item item) {
                        }

                        @Override
                        public void clicked(Item item, int position) {
                            open(item);
                        }
                    });
            list.setAdapter(a);
            a.set(l, -1);
            results.addView(block);
        }
        scroll.setVisibility(any ? View.VISIBLE : View.GONE);
        status.setVisibility(any ? View.GONE : View.VISIBLE);
        status.setText(R.string.search_empty);
    }

    private void open(Item item) {
        if (item.kind == Item.Kind.LIVE) {
            startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_PLAY_CHANNEL, item.id)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        } else if (item.kind == Item.Kind.SERIES) {
            startActivity(SeriesActivity.intent(this, item, false));
        } else {
            startActivity(MovieActivity.intent(this, item));
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Down from the field goes to the first result; Up from the first block goes back to the field.
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            View f = getCurrentFocus();
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && f == query) {
                focusResults();
                return true;
            }
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && f != null && results.getChildCount() > 0
                    && Root.inside(f, results.getChildAt(0))) {
                query.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }
}
