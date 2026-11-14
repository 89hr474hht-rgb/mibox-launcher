package com.gathin.launchos.tv;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gathin.launchos.design.Focus;

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
