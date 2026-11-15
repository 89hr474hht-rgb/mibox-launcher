package com.gathin.launchos.tv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Predicate;

/**
 * Four digits typed with the remote's arrows, which has no number keys: Up and Down change the digit in the box shown
 * white, Left and Right go from box to box, OK gives the code. A remote with number keys types them straight in.
 * accept answers true to close, false to say the code is wrong and start again. Main thread only.
 */
final class PinDialog {
    static final int LENGTH = 4;

    private PinDialog() {
    }

    static void ask(Activity a, String title, String sub, Predicate<String> accept) {
        ask(a, title, sub, R.string.pin_wrong, accept);
    }

    /** wrong: what is said when accept refuses the code. */
    static void ask(Activity a, String title, String sub, int wrong, Predicate<String> accept) {
        Dialog d = GroupOptions.dialog(a, R.layout.dialog_group);
        ((TextView) d.findViewById(R.id.dialog_title)).setText(title);
        TextView subView = d.findViewById(R.id.dialog_sub);
        subView.setText(sub);
        subView.setVisibility(View.VISIBLE);
        LinearLayout body = d.findViewById(R.id.dialog_body);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        TextView[] boxes = new TextView[LENGTH];
        int[] digits = new int[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            TextView t = new TextView(a);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26);
            t.setTypeface(a.getResources().getFont(R.font.launchos_bold), Typeface.NORMAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 68));
            if (i > 0) lp.setMarginStart(Ui.dp(a, 12));
            row.addView(t, lp);
            boxes[i] = t;
        }
        body.addView(row);
        TextView hint = new TextView(a);
        hint.setText(R.string.pin_keys);
        hint.setTextColor(a.getColor(R.color.lo_text_3));
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = Ui.dp(a, 16);
        body.addView(hint, hl);

        int[] at = {0};
        Runnable paint = () -> {
            for (int i = 0; i < LENGTH; i++) {
                boolean on = i == at[0];
                boxes[i].setText(String.valueOf(digits[i]));
                boxes[i].setBackgroundResource(on ? R.drawable.pin_box_on : R.drawable.pin_box);
                boxes[i].setTextColor(a.getColor(on ? R.color.lo_on_focus : R.color.lo_text));
            }
        };
        Runnable submit = () -> {
            StringBuilder code = new StringBuilder();
            for (int x : digits) code.append(x);
            if (accept.test(code.toString())) {
                d.dismiss();
                return;
            }
            subView.setText(wrong);
            subView.setTextColor(a.getColor(R.color.lo_accent));
            java.util.Arrays.fill(digits, 0);
            at[0] = 0;
            paint.run();
            row.animate().translationX(Ui.dp(a, 10)).setDuration(60).withEndAction(() ->
                    row.animate().translationX(-Ui.dp(a, 8)).setDuration(60).withEndAction(() ->
                            row.animate().translationX(0).setDuration(80).start()).start()).start();
        };
        d.setOnKeyListener((dialog, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return code != KeyEvent.KEYCODE_BACK;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    digits[at[0]] = (digits[at[0]] + 1) % 10;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    digits[at[0]] = (digits[at[0]] + 9) % 10;
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    at[0] = Math.max(0, at[0] - 1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    at[0] = Math.min(LENGTH - 1, at[0] + 1);
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    submit.run();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    return false;
                default:
                    if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
                        digits[at[0]] = code - KeyEvent.KEYCODE_0;
                        if (at[0] == LENGTH - 1) {
                            paint.run();
                            submit.run();
                            return true;
                        }
                        at[0]++;
                        break;
                    }
                    return false;
            }
            paint.run();
            return true;
        });
        paint.run();
        d.show();
    }
}
