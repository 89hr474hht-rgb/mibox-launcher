package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** One section's groups, in the order they are shown (hidden ones too), each with its options on OK. */
public final class GroupsActivity extends Activity {
    static final String EXTRA_SECTION = "section";

    private Xtream.Section section;
    private RecyclerView list;
    private final Adapter adapter = new Adapter();
    private List<Xtream.Category> cats = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_groups);
        section = Xtream.Section.valueOf(getIntent().getStringExtra(EXTRA_SECTION));
        ((TextView) findViewById(R.id.title)).setText(section == Xtream.Section.LIVE ? R.string.settings_groups_live
                : section == Xtream.Section.MOVIES ? R.string.settings_groups_movies : R.string.settings_groups_series);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        Account a = AccountStore.load(this);
        Library lib = Library.of(section);
        lib.load(a == null ? null : new Xtream(a), () -> {
            if (isDestroyed()) return;
            cats = lib.categories() == null ? new ArrayList<>() : lib.categories();
            show(0);
        });
    }

    /** Lists the groups again, keeping the focus on position. */
    private void show(int position) {
        Groups g = Groups.get(this);
        adapter.set(g == null ? cats : g.ordered(section, cats, true));
        list.post(() -> {
            RecyclerView.LayoutManager lm = list.getLayoutManager();
            if (lm == null) return;
            lm.scrollToPosition(position);
            list.post(() -> {
                View v = lm.findViewByPosition(position);
                if (v != null) v.requestFocus();
            });
        });
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        List<Xtream.Category> items = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged") // the order can change entirely
        void set(List<Xtream.Category> l) {
            items = l;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_setting, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            View v = h.itemView;
            TextView name = v.findViewById(R.id.name), sub = v.findViewById(R.id.sub);
            View pinned = v.findViewById(R.id.pinned), hidden = v.findViewById(R.id.hidden);
            if (position == 0) {
                name.setText(R.string.groups_reset);
                sub.setText(R.string.groups_reset_sub);
                pinned.setVisibility(View.GONE);
                hidden.setVisibility(View.GONE);
                v.setAlpha(1f);
                v.setOnClickListener(x -> {
                    Groups g = Groups.get(GroupsActivity.this);
                    if (g != null) g.reset(section);
                    show(0);
                });
                return;
            }
            Xtream.Category c = items.get(position - 1);
            Groups g = Groups.get(GroupsActivity.this);
            name.setText(g == null ? Groups.panelName(section, c) : g.name(section, c));
            Library lib = Library.of(section);
            List<Item> l = lib.items(c.id);
            String count = l == null ? "" : l.size() + (l.size() > 1 ? " titres" : " titre");
            if (section == Xtream.Section.LIVE && l != null) count = l.size() + (l.size() > 1 ? " chaînes" : " chaîne");
            boolean renamed = g != null && g.renamed(section, c.id);
            sub.setText(renamed ? Ui.meta(getString(R.string.group_was, Groups.panelName(section, c)), count) : count);
            boolean isHidden = g != null && g.hidden(section, c.id);
            pinned.setVisibility(g != null && g.pinned(section, c.id) ? View.VISIBLE : View.GONE);
            hidden.setVisibility(isHidden ? View.VISIBLE : View.GONE);
            v.setAlpha(isHidden ? 0.55f : 1f);
            v.setOnClickListener(x -> GroupOptions.show(GroupsActivity.this, section, c, () -> {
                // The group may move (pinned, renamed): the focus follows it.
                Groups now = Groups.get(GroupsActivity.this);
                List<Xtream.Category> order = now == null ? cats : now.ordered(section, cats, true);
                show(order.indexOf(c) + 1);
            }));
        }

        @Override
        public int getItemCount() {
            return items.size() + 1;
        }
    }
}
