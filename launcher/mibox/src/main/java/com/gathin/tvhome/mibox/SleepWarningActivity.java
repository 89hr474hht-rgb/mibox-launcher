package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The sleep timer's last minute, opened over whatever is playing: a chip at the top counts down, and the screen asks
 * whether to add 30 minutes (focused, so one press of OK keeps watching) or to sleep now. Back stops the timer. At zero
 * the box goes to sleep. It ticks once a second only while it is open.
 */
public final class SleepWarningActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView time;
    private final Runnable tick = this::tick;
    private boolean sleeping;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SleepTimer.active(this)) {
            finish();
            return;
        }
        setContentView(R.layout.mibox_activity_sleep);
        UiFonts.apply(findViewById(R.id.mibox_sleep_root));
        time = findViewById(R.id.mibox_sleep_time);
        ((TextView) findViewById(R.id.mibox_sleep_sub)).setText(R.string.mibox_sleep_sub);
        View add = findViewById(R.id.mibox_sleep_add);
        add.setOnClickListener(v -> {
            SleepTimer.extend(this, SleepTimer.ADD_MS);
            close();
        });
        findViewById(R.id.mibox_sleep_now).setOnClickListener(v -> sleep());
        add.requestFocus();

        View root = findViewById(R.id.mibox_sleep_root), panel = findViewById(R.id.mibox_sleep_panel);
        View chip = findViewById(R.id.mibox_sleep_chip);
        float d = getResources().getDisplayMetrics().density;
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(260).start();
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        chip.setTranslationY(-40 * d);
        chip.animate().translationY(0f).setDuration(360).setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tick();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void tick() {
        if (!SleepTimer.active(this)) {
            if (SleepTimer.endAt(this) == 0) {   // stopped or extended elsewhere
                finish();
                return;
            }
            sleep();
            return;
        }
        long left = SleepTimer.remaining(this);
        if (left > SleepTimer.WARN_MS + 5_000) {   // extended from elsewhere: not the last minute any more
            finish();
            return;
        }
        time.setText(SleepTimer.countdown(left));
        handler.postDelayed(tick, Math.max(50, left % 1000 == 0 ? 1000 : left % 1000));
    }

    private void sleep() {
        handler.removeCallbacks(tick);
        sleeping = true;
        if (!SleepTimer.sleepNow(this)) Toast.makeText(this, R.string.mibox_sleep_failed, Toast.LENGTH_LONG).show();
        finish();
    }

    private void close() {
        handler.removeCallbacks(tick);
        View root = findViewById(R.id.mibox_sleep_root);
        root.animate().alpha(0f).setDuration(200).withEndAction(this::finish).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            SleepTimer.cancel(this);
            Toast.makeText(this, R.string.mibox_sleep_stopped, Toast.LENGTH_SHORT).show();
            close();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finish() {
        // A11yPause: an app that refuses accessibility was behind; the helpers leave again.
        A11yPause.warningClosed(this, sleeping ? 1500 : 0);
        super.finish();
        overridePendingTransition(0, 0);
    }
}
