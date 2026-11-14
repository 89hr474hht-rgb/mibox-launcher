package com.gathin.launchos.tv;

import android.view.View;
import android.widget.TextView;

/**
 * The second step of a deletion, the same everywhere (the account, the favourites, the resume points): the first OK
 * turns the button's text into "Confirmer la suppression ?" for {@link #ARMED_MS}; OK again within that time
 * deletes; Back, the focus leaving the button or the time running out puts it back as it was. Main thread only.
 */
final class Confirm {
    static final long ARMED_MS = 5000;

    private final View button;
    private final TextView label;
    private final Runnable action;
    private CharSequence normal;
    private boolean armed;
    private final Runnable disarm = this::cancel;

    /** Wires button's clicks; label is the text that changes (the button itself, or a row's title). */
    Confirm(View button, TextView label, Runnable action) {
        this.button = button;
        this.label = label;
        this.action = action;
        button.setOnClickListener(v -> click());
        View.OnFocusChangeListener before = button.getOnFocusChangeListener();
        button.setOnFocusChangeListener((v, focused) -> {
            if (before != null) before.onFocusChange(v, focused);
            if (!focused) cancel();
        });
    }

    boolean armed() {
        return armed;
    }

    private void click() {
        if (!armed) {
            armed = true;
            normal = label.getText();
            label.setText(R.string.confirm_delete);
            label.announceForAccessibility(label.getText());
            button.postDelayed(disarm, ARMED_MS);
            return;
        }
        cancel();
        action.run();
    }

    /** Puts the button back as it was; true when it was waiting for the second OK (Back is then used up). */
    boolean cancel() {
        if (!armed) return false;
        armed = false;
        button.removeCallbacks(disarm);
        label.setText(normal);
        return true;
    }
}
