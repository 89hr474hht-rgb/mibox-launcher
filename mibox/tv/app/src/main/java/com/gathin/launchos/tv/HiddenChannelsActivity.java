package com.gathin.launchos.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/** The channels hidden with a long press on OK: OK on one shows it again, or all of them at once. */
public final class HiddenChannelsActivity extends Activity {
    private ViewGroup column;
    private int fixedChildren;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_refresh);
        Rows.header(this, R.drawable.ic_hide, R.string.settings_hidden_channels, R.string.hidden_hint);
        column = findViewById(R.id.column);
        fixedChildren = column.getChildCount();
        fill();
    }

    private void fill() {
        while (column.getChildCount() > fixedChildren) column.removeViewAt(fixedChildren);
        Channels chs = Channels.get(this);
        List<Item> hidden = chs == null ? java.util.Collections.emptyList() : chs.hiddenItems();
        if (hidden.isEmpty()) {
            Rows.Row none = Rows.add(column, R.drawable.ic_check_circle, R.string.settings_hidden_none)
                    .sub(R.string.hidden_empty_sub);
            none.click(v -> finish());
            none.view.requestFocus();
            return;
        }
        Rows.Row all = Rows.add(column, R.drawable.ic_restart, R.string.hidden_show_all).click(v -> {
            chs.showAll();
            fill();
        });
        Rows.section(column, R.string.hidden_section);
        for (Item i : hidden) {
            Rows.Row r = Rows.add(column, R.drawable.ic_live, R.string.settings_hidden_channels);
            r.title.setText(LivePage.display(this, i));
            r.sub(R.string.hidden_row_sub).click(v -> {
                int at = column.indexOfChild(r.view);
                chs.setHidden(i, false);
                fill();
                View next = column.getChildAt(Math.min(at, column.getChildCount() - 1));
                if (next != null && next.isFocusable()) next.requestFocus();
            });
        }
        all.view.requestFocus();
    }
}
