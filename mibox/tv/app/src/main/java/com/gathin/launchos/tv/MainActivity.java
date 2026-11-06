package com.gathin.launchos.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The main screen: the sections on the left (Chaînes, Films, Séries, Compte), the section's categories, then the
 * category's channels or posters. Moving onto a category loads it; OK on a card plays it (or opens the series).
 */
public final class MainActivity extends Activity {
    private static final long CATEGORY_DELAY_MS = 280;

    private final Map<String, List<Xtream.Category>> categoryCache = new HashMap<>();
    private final Map<String, List<Item>> itemCache = new HashMap<>();

    private TextView[] nav;
    private TextView sectionTitle, sectionCount, status;
    private RecyclerView categoriesView, grid;
    private final CategoryAdapter categoryAdapter = new CategoryAdapter();
    private final CardAdapter cardAdapter = new CardAdapter();

    private Xtream api;
    private int accountVersion = -1;
    private Xtream.Section section = Xtream.Section.LIVE;
    private Xtream.Category category;
    private int request;   // ignores answers that arrive after the owner moved on
    private final Runnable loadFocusedCategory = this::loadItems;
    private TextView navUpdates, banner;
    private static String bannerShownFor;
    private final Updater.Listener updates = this::showUpdate;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        sectionTitle = findViewById(R.id.section_title);
        sectionCount = findViewById(R.id.section_count);
        status = findViewById(R.id.status);
        categoriesView = findViewById(R.id.categories);
        grid = findViewById(R.id.grid);

        nav = new TextView[]{findViewById(R.id.nav_live), findViewById(R.id.nav_movies), findViewById(R.id.nav_series)};
        Xtream.Section[] sections = Xtream.Section.values();
        for (int i = 0; i < nav.length; i++) {
            Xtream.Section s = sections[i];
            nav[i].setOnFocusChangeListener((v, focused) -> {
                if (focused && s != section) showSection(s);
            });
            nav[i].setOnClickListener(v -> categoriesView.requestFocus());
        }
        findViewById(R.id.nav_account).setOnClickListener(v -> startActivity(new Intent(this, AccountActivity.class)));
        navUpdates = findViewById(R.id.nav_updates);
        navUpdates.setOnClickListener(v -> startActivity(new Intent(this, UpdatesActivity.class)));
        banner = findViewById(R.id.update_banner);
        // Same logic as LaunchOS: a daily check in the background, and one when the app opens (at most hourly).
        UpdateScheduler.schedule(this);
        UpdateScheduler.checkSoon(this);

        categoriesView.setLayoutManager(new LinearLayoutManager(this));
        categoriesView.setAdapter(categoryAdapter);
        categoriesView.setItemAnimator(null);
        grid.setItemAnimator(null);
        grid.setAdapter(cardAdapter);
        applyGridColumns();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Updater.get().removeListener(updates);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.get().addListener(updates);
        showUpdate();
        if (accountVersion == AccountStore.version()) return;
        accountVersion = AccountStore.version();
        categoryCache.clear();
        itemCache.clear();
        Account a = AccountStore.load(this);
        api = a == null ? null : new Xtream(a);
        if (api == null) {
            categoryAdapter.set(new ArrayList<>());
            cardAdapter.set(new ArrayList<>(), section);
            sectionTitle.setText(sectionName(section));
            sectionCount.setText("");
            showStatus(getString(R.string.no_account));
            findViewById(R.id.nav_account).requestFocus();
            return;
        }
        showSection(section);
        nav[section.ordinal()].requestFocus();
    }

    /** The rail item says when a newer version is waiting; the banner says it once per version and session. */
    private void showUpdate() {
        UpdateState state = new UpdateState(this);
        Release r = state.available();
        boolean newer = r != null && r.version.isNewerThan(UpdateState.installed(this));
        navUpdates.setText(newer ? R.string.nav_update_ready : R.string.nav_updates);
        navUpdates.setCompoundDrawablesRelativeWithIntrinsicBounds(getDrawable(R.drawable.ic_update), null,
                newer ? getDrawable(R.drawable.dot_accent) : null, null);
        if (newer && state.shouldPrompt(this) && !String.valueOf(r.version).equals(bannerShownFor)) {
            bannerShownFor = String.valueOf(r.version);
            banner.setText(getString(R.string.upd_banner, bannerShownFor));
            banner.setAlpha(0f);
            banner.setTranslationX(Ui.dp(this, 40));
            banner.setVisibility(View.VISIBLE);
            banner.animate().alpha(1f).translationX(0).setDuration(320).start();
            banner.postDelayed(() -> banner.animate().alpha(0f).setDuration(400)
                    .withEndAction(() -> banner.setVisibility(View.GONE)).start(), 9000);
        }
    }

    private String sectionName(Xtream.Section s) {
        return getString(s == Xtream.Section.LIVE ? R.string.nav_live
                : s == Xtream.Section.MOVIES ? R.string.nav_movies : R.string.nav_series);
    }

    private void showSection(Xtream.Section s) {
        section = s;
        for (int i = 0; i < nav.length; i++) nav[i].setSelected(i == s.ordinal());
        sectionTitle.setText(sectionName(s));
        sectionCount.setText("");
        category = null;
        categoryAdapter.set(new ArrayList<>());
        cardAdapter.set(new ArrayList<>(), s);
        applyGridColumns();
        if (api == null) return;
        List<Xtream.Category> cached = categoryCache.get(s.name());
        if (cached != null) {
            showCategories(cached);
            return;
        }
        showStatus(getString(R.string.loading));
        int r = ++request;
        Xtream x = api;
        Bg.run(() -> x.categories(s), (list, error) -> {
            if (r != request || isDestroyed()) return;
            if (error != null) {
                showStatus(error.french());
                return;
            }
            categoryCache.put(s.name(), list);
            showCategories(list);
        });
    }

    private void showCategories(List<Xtream.Category> list) {
        categoryAdapter.set(list);
        sectionCount.setText(String.format(Locale.FRANCE, "%d catégorie%s", list.size(), list.size() > 1 ? "s" : ""));
        if (list.isEmpty()) {
            showStatus(getString(R.string.empty));
            return;
        }
        selectCategory(list.get(0));
    }

    private void selectCategory(Xtream.Category c) {
        if (c == category) return;
        category = c;
        // Only the rows' selected state changes: rebinding the list would move the focus.
        for (int i = 0; i < categoriesView.getChildCount(); i++) {
            View row = categoriesView.getChildAt(i);
            int pos = categoriesView.getChildAdapterPosition(row);
            row.setSelected(pos != RecyclerView.NO_POSITION && categoryAdapter.items.get(pos) == c);
        }
        grid.removeCallbacks(loadFocusedCategory);
        grid.postDelayed(loadFocusedCategory, CATEGORY_DELAY_MS);
    }

    private void loadItems() {
        Xtream.Category c = category;
        Xtream.Section s = section;
        if (c == null || api == null) return;
        String key = s.name() + "/" + c.id;
        List<Item> cached = itemCache.get(key);
        if (cached != null) {
            showItems(cached, s);
            return;
        }
        cardAdapter.set(new ArrayList<>(), s);
        showStatus(getString(R.string.loading));
        int r = ++request;
        Xtream x = api;
        Bg.run(() -> x.items(s, c.id), (list, error) -> {
            if (r != request || isDestroyed()) return;
            if (error != null) {
                showStatus(error.french());
                return;
            }
            itemCache.put(key, list);
            showItems(list, s);
        });
    }

    private void showItems(List<Item> list, Xtream.Section s) {
        cardAdapter.set(list, s);
        grid.scrollToPosition(0);
        if (list.isEmpty()) showStatus(getString(R.string.empty));
        else status.setVisibility(View.GONE);
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    private void applyGridColumns() {
        grid.setLayoutManager(new GridLayoutManager(this, section == Xtream.Section.LIVE ? 3 : 4));
    }

    private void open(Item item, int position) {
        if (api == null) return;
        if (item.kind == Item.Kind.SERIES) {
            startActivity(new Intent(this, SeriesActivity.class).putExtra(SeriesActivity.EXTRA_ID, item.id)
                    .putExtra(SeriesActivity.EXTRA_NAME, item.name).putExtra(SeriesActivity.EXTRA_COVER, item.image));
            return;
        }
        if (item.kind == Item.Kind.LIVE) {
            Playlist.channels = cardAdapter.items;
            Playlist.category = category != null ? category.name : "";
            startActivity(new Intent(this, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_LIVE_INDEX, position));
            return;
        }
        startActivity(new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_URL, api.movieUrl(item)).putExtra(PlayerActivity.EXTRA_TITLE, item.name));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Back steps out one column at a time before leaving the app.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            View f = getCurrentFocus();
            if (f != null && grid.findContainingItemView(f) != null) {
                View row = categoriesView.getLayoutManager() == null ? null
                        : categoriesView.getLayoutManager().findViewByPosition(categoryAdapter.indexOf(category));
                if (row != null) row.requestFocus();
                else categoriesView.requestFocus();
                return true;
            }
            if (f != null && categoriesView.findContainingItemView(f) != null) {
                nav[section.ordinal()].requestFocus();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- adapters ----

    private final class CategoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Xtream.Category> items = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Xtream.Category> list) {
            items = list;
            notifyDataSetChanged();
        }

        int indexOf(Xtream.Category c) {
            return Math.max(0, items.indexOf(c));
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            Xtream.Category c = items.get(position);
            TextView t = (TextView) h.itemView;
            t.setText(c.name);
            t.setSelected(c == category);
            t.setOnFocusChangeListener((v, focused) -> {
                if (focused) selectCategory(c);
            });
            t.setOnClickListener(v -> {
                selectCategory(c);
                if (cardAdapter.getItemCount() > 0) grid.requestFocus();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private final class CardAdapter extends RecyclerView.Adapter<CardAdapter.Holder> {
        List<Item> items = new ArrayList<>();
        private Xtream.Section kind = Xtream.Section.LIVE;

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView title;
            final TextView initials;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.image);
                title = v.findViewById(R.id.title);
                initials = v.findViewById(R.id.initials);
                View frame = v.findViewById(R.id.frame);
                Ui.roundClip(frame != null ? frame : (View) image.getParent());
                Ui.lift(v, 1.06f);
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Item> list, Xtream.Section s) {
            items = list;
            kind = s;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return kind == Xtream.Section.LIVE ? 0 : 1;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(type == 0 ? R.layout.item_channel : R.layout.item_poster, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Item item = items.get(position);
            if (item.kind == Item.Kind.LIVE) {
                h.title.setText(item.num > 0 ? item.num + "  " + item.name : item.name);
                h.initials.setText(initials(item.name));
                Images.load(h.image, item.image, Ui.dp(h.itemView.getContext(), 140));
            } else {
                h.title.setText(item.name);
                Images.load(h.image, item.image, Ui.dp(h.itemView.getContext(), 120));
            }
            h.itemView.setOnClickListener(v -> open(item, h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name.trim().split("\\s+")) {
            if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(0))) b.appendCodePoint(w.codePointAt(0));
            if (b.length() >= 2) break;
        }
        return b.toString().toUpperCase(Locale.FRANCE);
    }
}
