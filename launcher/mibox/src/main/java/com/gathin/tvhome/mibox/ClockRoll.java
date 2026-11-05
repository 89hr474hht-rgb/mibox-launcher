package com.gathin.tvhome.mibox;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * When the home screen's clock changes, the digits that changed roll like an odometer: the old one slides up and
 * out of its line while the new one rises in from below with a small overshoot, one after the other from the
 * right. The digits that stay the same do not move. The clock is upstream's TextClock, left in place: during
 * the roll the changed digits are drawn in its overlay, and its own copies are made transparent.
 */
final class ClockRoll implements TextWatcher {
    private static final long DIGIT_MS = 520, STAGGER_MS = 70;

    private final TextView clock;
    private String before;
    private float[] beforeX;
    private ValueAnimator running;
    private Roll roll;

    private ClockRoll(TextView clock) {
        this.clock = clock;
    }

    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static ClockRoll attach(Activity home) {
        int id = home.getResources().getIdentifier("clock", "id", home.getPackageName());
        View v = id == 0 ? null : home.findViewById(id);
        if (!(v instanceof TextView)) return null;
        ClockRoll r = new ClockRoll((TextView) v);
        ((TextView) v).addTextChangedListener(r);
        return r;
    }

    void destroy() {
        clock.removeTextChangedListener(this);
        finish();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        before = s.toString();
        beforeX = positions(clock, before.length());
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        String old = before, now = s.toString();
        float[] oldX = beforeX;
        before = null;
        if (old == null || old.isEmpty() || old.equals(now) || oldX == null || !clock.isShown()) return;
        if (Settings.Global.getFloat(clock.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return;
        // The new text is laid out at the next pass: start once it is, just before it is drawn.
        clock.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                clock.getViewTreeObserver().removeOnPreDrawListener(this);
                start(old, oldX, now);
                return true;
            }
        });
    }

    /** x of each character in the clock's current layout, in view coordinates; null before the first layout. */
    private static float[] positions(TextView v, int n) {
        Layout l = v.getLayout();
        if (l == null || l.getText().length() < n) return null;
        float[] x = new float[n];
        for (int i = 0; i < n; i++) x[i] = v.getTotalPaddingLeft() + l.getPrimaryHorizontal(i);
        return x;
    }

    private void start(String old, float[] oldX, String now) {
        finish();
        float[] newX = positions(clock, now.length());
        if (newX == null || !(clock.getText() instanceof Spannable)) return;
        boolean sameShape = old.length() == now.length();
        int n = now.length();
        boolean[] changed = new boolean[Math.max(n, old.length())];
        int count = 0;
        for (int i = 0; i < changed.length; i++) {
            changed[i] = !sameShape || i >= n || i >= old.length() || old.charAt(i) != now.charAt(i);
            if (changed[i]) count++;
        }
        if (count == 0) return;
        Spannable text = (Spannable) clock.getText();
        ForegroundColorSpan[] hidden = new ForegroundColorSpan[n];
        for (int i = 0; i < n; i++) {
            if (!changed[i]) continue;
            hidden[i] = new ForegroundColorSpan(Color.TRANSPARENT);
            text.setSpan(hidden[i], i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        roll = new Roll(old, oldX, now, newX, changed, hidden);
        roll.setBounds(0, 0, clock.getWidth(), clock.getHeight());
        clock.getOverlay().add(roll);
        long total = DIGIT_MS + STAGGER_MS * (count - 1);
        running = ValueAnimator.ofFloat(0f, total);
        running.setDuration(total);
        running.setInterpolator(null);   // linear time; each digit eases on its own
        running.addUpdateListener(a -> {
            if (roll == null) return;
            roll.ms = (float) a.getAnimatedValue();
            roll.invalidateSelf();
        });
        running.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finish();
            }
        });
        running.start();
    }

    /** Ends a roll: the clock shows its own digits again and the overlay is cleared. */
    private void finish() {
        ValueAnimator a = running;
        running = null;
        if (a != null) a.cancel();
        Roll r = roll;
        roll = null;
        if (r == null) return;
        clock.getOverlay().remove(r);
        if (clock.getText() instanceof Spannable) {
            Spannable text = (Spannable) clock.getText();
            for (ForegroundColorSpan s : r.hidden) if (s != null) text.removeSpan(s);
        }
    }

    /** The rolling digits, drawn over the clock with its own paint and clipped to its line. */
    private final class Roll extends Drawable {
        final String old, now;
        final float[] oldX, newX;
        final boolean[] changed;
        final ForegroundColorSpan[] hidden;
        final int[] order;       // stagger rank of each changed index, rightmost first
        final TextPaint paint;
        final Interpolator in = new OvershootInterpolator(1.3f), out = new DecelerateInterpolator(1.4f);
        float ms;

        Roll(String old, float[] oldX, String now, float[] newX, boolean[] changed, ForegroundColorSpan[] hidden) {
            this.old = old;
            this.oldX = oldX;
            this.now = now;
            this.newX = newX;
            this.changed = changed;
            this.hidden = hidden;
            order = new int[changed.length];
            int rank = 0;
            for (int i = changed.length - 1; i >= 0; i--) if (changed[i]) order[i] = rank++;
            paint = new TextPaint(clock.getPaint());
            paint.setColor(clock.getCurrentTextColor());
        }

        @Override
        public void draw(Canvas canvas) {
            float baseline = clock.getBaseline();
            float travel = clock.getTextSize() * 0.8f;
            int alpha = Color.alpha(clock.getCurrentTextColor());
            canvas.save();
            canvas.clipRect(0, 0, clock.getWidth(), clock.getHeight());
            for (int i = 0; i < changed.length; i++) {
                if (!changed[i]) continue;
                float t = Math.max(0f, Math.min(1f, (ms - order[i] * STAGGER_MS) / DIGIT_MS));
                if (i < old.length()) {
                    float p = out.getInterpolation(Math.min(1f, t * 1.25f));
                    paint.setAlpha(Math.round(alpha * (1 - p)));
                    canvas.drawText(old, i, i + 1, oldX[i], baseline - p * travel, paint);
                }
                if (i < now.length()) {
                    float p = in.getInterpolation(t);
                    paint.setAlpha(Math.round(alpha * Math.min(1f, t * 1.6f)));
                    canvas.drawText(now, i, i + 1, newX[i], baseline + (1 - p) * travel, paint);
                }
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
