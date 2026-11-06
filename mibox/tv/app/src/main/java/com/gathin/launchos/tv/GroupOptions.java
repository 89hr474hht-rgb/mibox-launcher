package com.gathin.launchos.tv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The small sheet for one group: Renommer, Masquer or Afficher, Épingler en haut, Reprendre le nom d'origine. */
final class GroupOptions {
    private GroupOptions() {
    }

    static void show(Activity a, Xtream.Section s, Xtream.Category c, Runnable changed) {
        Groups g = Groups.get(a);
        if (g == null) return;
        Dialog d = dialog(a, R.layout.dialog_group);
        ((TextView) d.findViewById(R.id.dialog_title)).setText(g.name(s, c));
        TextView sub = d.findViewById(R.id.dialog_sub);
        sub.setText(g.renamed(s, c.id) ? a.getString(R.string.group_was, Groups.panelName(s, c)) : "");
        sub.setVisibility(g.renamed(s, c.id) ? View.VISIBLE : View.GONE);
        LinearLayout body = d.findViewById(R.id.dialog_body);
        boolean hidden = g.hidden(s, c.id), pinned = g.pinned(s, c.id);
        TextView first = action(a, body, R.drawable.ic_edit, a.getString(R.string.group_rename), () -> {
            d.dismiss();
            rename(a, s, c, changed);
        });
        action(a, body, R.drawable.ic_hide, a.getString(hidden ? R.string.group_show : R.string.group_hide), () -> {
            g.setHidden(s, c.id, !hidden);
            d.dismiss();
            changed.run();
        });
        action(a, body, R.drawable.ic_pin, a.getString(pinned ? R.string.group_unpin : R.string.group_pin), () -> {
            g.setPinned(s, c.id, !pinned);
            d.dismiss();
            changed.run();
        });
        if (g.renamed(s, c.id)) {
            action(a, body, R.drawable.ic_restart, a.getString(R.string.group_original), () -> {
                g.rename(s, c.id, null);
                d.dismiss();
                changed.run();
            });
        }
        d.show();
        first.requestFocus();
    }

    static void rename(Activity a, Xtream.Section s, Xtream.Category c, Runnable changed) {
        Groups g = Groups.get(a);
        if (g == null) return;
        Dialog d = dialog(a, R.layout.dialog_rename);
        EditText name = d.findViewById(R.id.name);
        name.setText(g.name(s, c));
        name.setSelection(name.getText().length());
        Runnable save = () -> {
            g.rename(s, c.id, name.getText().toString().equals(Groups.panelName(s, c)) ? null : name.getText().toString());
            d.dismiss();
            changed.run();
        };
        name.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_DONE) {
                save.run();
                return true;
            }
            return false;
        });
        d.findViewById(R.id.ok).setOnClickListener(v -> save.run());
        d.findViewById(R.id.cancel).setOnClickListener(v -> d.dismiss());
        d.show();
        name.requestFocus();
    }

    private static Dialog dialog(Activity a, int layout) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        d.setContentView(layout);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setDimAmount(0.6f);
        }
        return d;
    }

    private static TextView action(Activity a, LinearLayout body, int icon, String text, Runnable run) {
        TextView t = (TextView) LayoutInflater.from(a).inflate(R.layout.item_sheet_action, body, false);
        t.setText(text);
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        t.setOnClickListener(v -> run.run());
        body.addView(t);
        return t;
    }
}
