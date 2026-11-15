package com.gathin.launchos.tv;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;
import com.gathin.launchos.design.SegmentBar;

import java.util.function.IntConsumer;

/**
 * Builds the parts of the secondary screens (Réglages, the hidden channels, the groups): a header with the screen's
 * icon, section labels, and rows (item_setting_row) with an icon, a title, a line under it, and a state, a switch or
 * an arrow. Main thread only.
 */
final class Rows {
    enum Tone { NORMAL, OK, ERROR }

    private Rows() {
    }

    /** The screen's icon, title and the line under it (none when hint is 0). */
    static void header(Activity a, int icon, int title, int hint) {
        ((ImageView) a.findViewById(R.id.page_icon)).setImageResource(icon);
        ((TextView) a.findViewById(R.id.page_title)).setText(title);
        TextView h = a.findViewById(R.id.page_hint);
        if (hint == 0) h.setVisibility(View.GONE);
        else h.setText(hint);
    }

    /** A small label above a group of rows. */
    static void section(ViewGroup column, int label) {
        TextView t = (TextView) LayoutInflater.from(column.getContext()).inflate(R.layout.item_section, column, false);
        t.setText(label);
        column.addView(t);
    }

    /** A row: icon, title, a line under it (hidden until set) and room for a state, a switch or an arrow. */
    static Row add(ViewGroup column, int icon, int title) {
        View v = LayoutInflater.from(column.getContext()).inflate(R.layout.item_setting_row, column, false);
        Row r = new Row(v);
        r.icon.setImageResource(icon);
        r.title.setText(title);
        r.sub.setVisibility(View.GONE);
        Focus.lift(v, Focus.SCALE_LARGE);
        column.addView(v);
        return r;
    }

    /**
     * A setting with a few values: the values sit on the right in a {@link SegmentBar}, the chosen one marked by a
     * pill that glides to it. OK on a value chooses it and calls pick with its index.
     */
    static Choice choice(ViewGroup column, int icon, int title, CharSequence[] labels, IntConsumer pick) {
        LayoutInflater in = LayoutInflater.from(column.getContext());
        View v = in.inflate(R.layout.item_setting_choice, column, false);
        Choice c = new Choice(v, labels.length);
        ((ImageView) v.findViewById(R.id.icon)).setImageResource(icon);
        c.title.setText(title);
        c.sub.setVisibility(View.GONE);
        c.bar.setPill(column.getContext().getColor(R.color.lo_surface_3), column.getContext().getColor(R.color.lo_accent));
        c.bar.setTrack(column.getContext().getColor(R.color.lo_surface_2));
        for (int i = 0; i < labels.length; i++) {
            TextView t = (TextView) in.inflate(R.layout.item_segment, c.bar, false);
            t.setText(labels[i]);
            t.setId(View.generateViewId());
            t.setContentDescription(c.title.getText() + ", " + labels[i]);
            int index = i;
            t.setOnClickListener(x -> {
                c.select(index);
                pick.accept(index);
            });
            c.bar.addView(t);
            c.items[i] = t;
        }
        column.addView(v);
        return c;
    }

    static final class Choice {
        final View view;
        final TextView title, sub;
        final SegmentBar bar;
        final TextView[] items;

        Choice(View v, int n) {
            view = v;
            title = v.findViewById(R.id.title);
            sub = v.findViewById(R.id.sub);
            bar = v.findViewById(R.id.segments);
            items = new TextView[n];
        }

        /** Marks the value at index as the chosen one; the pill glides there. */
        Choice select(int index) {
            for (int i = 0; i < items.length; i++) items[i].setSelected(i == index);
            return this;
        }

        Choice sub(int text) {
            sub.setText(text);
            sub.setVisibility(View.VISIBLE);
            return this;
        }
    }

    static final class Row {
        final View view;
        final ImageView icon;
        final TextView title, sub, state;
        private final View toggle, chevron;

        Row(View v) {
            view = v;
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            sub = v.findViewById(R.id.sub);
            state = v.findViewById(R.id.state);
            toggle = v.findViewById(R.id.toggle);
            chevron = v.findViewById(R.id.chevron);
        }

        Row sub(CharSequence text) {
            sub.setText(text);
            sub.setVisibility(text == null || text.length() == 0 ? View.GONE : View.VISIBLE);
            return this;
        }

        Row sub(int text) {
            return sub(view.getContext().getText(text));
        }

        Row click(View.OnClickListener l) {
            view.setOnClickListener(l);
            return this;
        }

        /** The row opens another screen. */
        Row opens() {
            chevron.setVisibility(View.VISIBLE);
            return this;
        }

        /** An on/off switch, said aloud as Activé or Désactivé. */
        Row toggle(boolean on) {
            toggle.setVisibility(View.VISIBLE);
            toggle.setActivated(on);
            state.setText("");
            view.setContentDescription(title.getText() + ", " + view.getContext().getString(on ? R.string.on : R.string.off));
            return this;
        }

        Row state(CharSequence text, Tone tone) {
            state.setText(text);
            state.setTextColor(view.getContext().getColorStateList(tone == Tone.ERROR ? R.color.row_error
                    : tone == Tone.OK ? R.color.row_ok : R.color.lo_text_2_on_focus));
            return this;
        }

        String stateText() {
            return state.getText().toString();
        }
    }
}
