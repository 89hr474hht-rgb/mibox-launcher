package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * The small card in the top-right corner of the home screen: "Update" or "Later". It is its own window, so the
 * home screen's key handling never sees the remote's keys while it is open; Back means "Later".
 */
final class UpdatePrompt extends Dialog {
    private final Activity owner;
    private final Release release;
    private boolean quiet;

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
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setGravity(Gravity.TOP | Gravity.END);
            WindowManager.LayoutParams lp = w.getAttributes();
            float dp = getContext().getResources().getDisplayMetrics().density;
            lp.x = Math.round(40 * dp);
            lp.y = Math.round(32 * dp);
            lp.width = Math.round(400 * dp);
            w.setAttributes(lp);
        }
        ((TextView) findViewById(R.id.mibox_prompt_version))
                .setText(getContext().getString(R.string.mibox_prompt_version, release.version.toString()));
        TextView summary = findViewById(R.id.mibox_prompt_summary);
        String s = release.summary(3);
        summary.setText(s);
        summary.setVisibility(s.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        findViewById(R.id.mibox_prompt_update).setOnClickListener(v -> {
            quiet = true;
            dismiss();
            owner.startActivity(new Intent(owner, UpdatesActivity.class).putExtra(UpdatesActivity.EXTRA_INSTALL, true));
        });
        findViewById(R.id.mibox_prompt_later).setOnClickListener(v -> dismiss());
        setOnDismissListener(d -> {
            if (!quiet) new UpdateState(owner).snooze(release.version);
        });
        findViewById(R.id.mibox_prompt_update).requestFocus();
    }

    /** Closes without counting as "Later", when the home screen goes to the background. */
    void dismissQuietly() {
        quiet = true;
        if (isShowing()) dismiss();
    }
}
