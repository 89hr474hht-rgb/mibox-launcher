package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * The small card in the top-right corner of the home screen: "Update" or "Later". It glides in from the right with a
 * little spring, the logo pops and gives a short nod, and it slides back out when closed. It is its own window, so
 * the home screen's key handling never sees the remote's keys while it is open; Back means "Later".
 */
final class UpdatePrompt extends Dialog {
    private final Activity owner;
    private final Release release;
    private boolean quiet, leaving;
    private View card;

    UpdatePrompt(Activity owner, Release release) {
        super(owner);
        this.owner = owner;
        this.release = release;
    }

    Activity owner() {
        return owner;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.mibox_update_prompt);
        Window w = getWindow();
        float dp = getContext().getResources().getDisplayMetrics().density;
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setGravity(Gravity.TOP | Gravity.END);
            w.setWindowAnimations(0);   // the card animates itself
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.x = Math.round(26 * dp);
            lp.y = Math.round(18 * dp);
            lp.width = Math.round(430 * dp);
            w.setAttributes(lp);
        }
        card = findViewById(R.id.mibox_prompt_card);
        UiFonts.apply(card);
        ((TextView) findViewById(R.id.mibox_prompt_version))
                .setText(getContext().getString(R.string.mibox_prompt_version, release.version.toString()));
        TextView summary = findViewById(R.id.mibox_prompt_summary);
        String s = release.summary(3);
        summary.setText(s);
        summary.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.mibox_prompt_update).setOnClickListener(v -> {
            quiet = true;
            leave(() -> owner.startActivity(new Intent(owner, UpdatesActivity.class)
                    .putExtra(UpdatesActivity.EXTRA_INSTALL, true)));
        });
        findViewById(R.id.mibox_prompt_later).setOnClickListener(v -> leave(null));
        setOnDismissListener(d -> {
            if (!quiet) new UpdateState(owner).snooze(release.version);
        });
        findViewById(R.id.mibox_prompt_update).requestFocus();
        enter(dp);
    }

    private boolean still() {
        return Settings.Global.getFloat(owner.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
    }

    private void enter(float dp) {
        if (still()) return;
        card.setAlpha(0f);
        card.setTranslationX(70 * dp);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(520)
                .setInterpolator(new OvershootInterpolator(1.15f)).start();
        View logo = findViewById(R.id.mibox_prompt_logo);
        logo.setScaleX(0.4f);
        logo.setScaleY(0.4f);
        logo.setRotation(-25f);
        logo.animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(220).setDuration(560)
                .setInterpolator(new OvershootInterpolator(2.4f))
                .withEndAction(() -> logo.animate().rotation(-8f).setDuration(120).withEndAction(() ->
                        logo.animate().rotation(0f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start())
                        .start())
                .start();
    }

    /** Slides the card out to the right, then closes; then runs next, if any. */
    private void leave(Runnable next) {
        if (leaving) return;
        leaving = true;
        if (still() || card == null) {
            dismiss();
            if (next != null) next.run();
            return;
        }
        float dp = getContext().getResources().getDisplayMetrics().density;
        card.animate().alpha(0f).translationX(60 * dp).scaleX(0.96f).scaleY(0.96f).setStartDelay(0).setDuration(240)
                .setInterpolator(new AccelerateInterpolator(1.5f)).withEndAction(() -> {
                    if (isShowing()) dismiss();
                    if (next != null) next.run();
                }).start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) leave(null);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Closes without counting as "Later", when the home screen goes to the background. */
    void dismissQuietly() {
        quiet = true;
        if (card != null) card.animate().cancel();
        if (isShowing()) dismiss();
    }
}
