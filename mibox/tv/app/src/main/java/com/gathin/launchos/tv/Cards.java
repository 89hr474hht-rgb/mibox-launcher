package com.gathin.launchos.tv;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Cards for films, series and channels: a poster (or a logo) with its title underneath, a quality tag, and how far
 * a film was watched. The rows, the full grids and search all use them. A row can end with a "Tout voir" card.
 */
final class Cards {
    enum Shape { POSTER, LOGO, WIDE }

    interface Listener {
        void focused(Item item);

        void clicked(Item item, int position);

        /** The "Tout voir" card at the end of a row. */
        default void more() {
        }
    }

    private Cards() {
    }

    static final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final Shape shape;
        private final Listener listener;
        private List<Item> items = new ArrayList<>();
        /** How many there are in all when the row ends with "Tout voir"; -1 for no such card. */
        private int moreCount = -1;
        private int position = -1;

        Adapter(Shape shape, Listener listener) {
            this.shape = shape;
            this.listener = listener;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView title, initials, badge;
            final View track, bar;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.image);
                title = v.findViewById(R.id.title);
                initials = v.findViewById(R.id.initials);
                badge = v.findViewById(R.id.badge);
                track = v.findViewById(R.id.track);
                bar = v.findViewById(R.id.bar);
                Ui.roundClip(v.findViewById(R.id.clip));
                v.setOnFocusChangeListener((view, focused) -> {
                    float s = focused ? 1.08f : 1f;
                    View frame = view.findViewById(R.id.frame);
                    frame.setPivotY(frame.getHeight());
                    frame.setPivotX(frame.getWidth() / 2f);
                    frame.animate().scaleX(s).scaleY(s).setDuration(170).start();
                    view.setTranslationZ(focused ? Ui.dp(view.getContext(), 10) : 0);
                    if (!focused) return;
                    int p = getBindingAdapterPosition();
                    if (p == RecyclerView.NO_POSITION) return;
                    position = p;
                    if (p < items.size()) listener.focused(items.get(p));
                    else listener.focused(null);
                });
            }
        }

        @SuppressWarnings("NotifyDataSetChanged") // the whole list changes
        void set(List<Item> list, int moreCount) {
            items = list == null ? new ArrayList<>() : list;
            this.moreCount = moreCount;
            notifyDataSetChanged();
        }

        List<Item> items() {
            return items;
        }

        /** The card focused last, so a row can be entered where it was left. */
        int lastPosition() {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return position < items.size() ? 0 : 1;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            int layout = shape == Shape.LOGO ? R.layout.item_card_logo
                    : shape == Shape.WIDE ? R.layout.item_card_wide : R.layout.item_card;
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            View v = h.itemView;
            if (position >= items.size()) {
                h.image.setImageDrawable(null);
                h.image.setTag(R.id.image_url, null);
                h.initials.setText(v.getContext().getString(R.string.row_more_count, moreCount));
                h.initials.setVisibility(View.VISIBLE);
                h.title.setText("");
                h.badge.setVisibility(View.GONE);
                h.track.setVisibility(View.GONE);
                v.setOnClickListener(x -> listener.more());
                return;
            }
            Item item = items.get(position);
            String title = item.kind == Item.Kind.LIVE ? LivePage.display(v.getContext(), item) : item.title;
            h.title.setText(title);
            h.initials.setText(title);
            int width = shape == Shape.LOGO ? 176 : shape == Shape.WIDE ? 236 : 124;
            Images.load(h.image, item.image, Ui.dp(v.getContext(), width), h.initials);
            String badge = item.tag;
            h.badge.setText(badge);
            h.badge.setVisibility(badge.isEmpty() ? View.GONE : View.VISIBLE);
            float progress = progress(v, item);
            h.track.setVisibility(progress > 0 ? View.VISIBLE : View.GONE);
            if (progress > 0) {
                h.bar.setPivotX(0);
                h.bar.setScaleX(progress);
            }
            v.setOnClickListener(x -> listener.clicked(item, h.getBindingAdapterPosition()));
        }

        private static float progress(View v, Item item) {
            if (item.kind != Item.Kind.MOVIE && item.kind != Item.Kind.EPISODE) return 0;
            History hist = History.get(v.getContext());
            History.Entry e = hist == null ? null : hist.resumeEntry(item.kind, item.id);
            if (e == null || e.duration <= 0) return 0;
            return Math.max(0.04f, Math.min(1f, e.position / (float) e.duration));
        }

        @Override
        public int getItemCount() {
            return items.size() + (moreCount >= 0 ? 1 : 0);
        }
    }
}
