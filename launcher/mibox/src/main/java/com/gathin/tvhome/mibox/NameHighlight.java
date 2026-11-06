package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * The focused app's name, highlighted. Under a grid tile: dark text on a bright pill sized to the name, with a soft
 * shadow, instead of white text on the wallpaper. Above the dock (upstream's big "heroName"): a heavier weight, a soft
 * light around the letters, and a sheen that runs across the name each time it changes. Upstream still fades and
 * slides the names in and out on focus; this only restyles their views.
 */
final class NameHighlight implements ViewTreeObserver.OnGlobalLayoutListener {
    private static final Object STYLED = new Object();

    private final ViewGroup grid;
    private final int nameId;
    private final float dp;
    private final Typeface face;

    private TextView hero;
    private android.text.TextWatcher heroWatcher;
    private android.animation.ValueAnimator sheen;

    private NameHighlight(Activity home, ViewGroup grid, int nameId) {
        this.grid = grid;
        this.nameId = nameId;
        dp = home.getResources().getDisplayMetrics().density;
        face = UiFonts.semibold(home);
    }

    /** The big name above the dock: heavier, glowing, and swept by a sheen when it changes. */
    @SuppressWarnings("DiscouragedApi")
    private void attachHero(Activity home) {
        int id = home.getResources().getIdentifier("heroName", "id", home.getPackageName());
        View v = id == 0 ? null : home.findViewById(id);
        if (!(v instanceof TextView)) return;
        hero = (TextView) v;
        Typeface medium = UiFonts.medium(home);
        if (medium != null) hero.setTypeface(medium);
        hero.setShadowLayer(6 * dp, 0, 0, 0x4DFFFFFF);   // wider gets cut off at the view's edges
        // A glass capsule that hugs the name; shifted left by its padding so the letters stay where upstream puts them.
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x38FFFFFF, 0x14FFFFFF});
        glass.setCornerRadius(999 * dp);
        glass.setStroke(Math.round(1.2f * dp), 0x52FFFFFF);
        int ph = Math.round(24 * dp), pv = Math.round(7 * dp);
        hero.setBackground(glass);
        hero.setPadding(ph, pv, ph, pv);
        hero.setTranslationX(-ph);
        hero.setPivotX(ph);
        // Its parents would cut off the part of the capsule that sits left of them.
        for (android.view.ViewParent p = hero.getParent(); p instanceof ViewGroup; p = p.getParent()) {
            ((ViewGroup) p).setClipChildren(false);
            ((ViewGroup) p).setClipToPadding(false);
            if (p == hero.getRootView()) break;
        }
        if (android.provider.Settings.Global.getFloat(home.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return;
        heroWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                hero.post(NameHighlight.this::runSheen);
                hero.setPivotY(hero.getHeight() / 2f);
                hero.setScaleX(0.94f);
                hero.setScaleY(0.94f);
                hero.animate().scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
            }
        };
        hero.addTextChangedListener(heroWatcher);
    }

    private void runSheen() {
        if (sheen != null) sheen.cancel();
        float w = hero.getWidth();
        if (w <= 0 || !hero.isShown()) return;
        android.text.TextPaint paint = hero.getPaint();
        sheen = android.animation.ValueAnimator.ofFloat(0f, 1f);
        sheen.setDuration(800);
        sheen.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        sheen.addUpdateListener(a -> {
            float x = -w * 0.5f + (float) a.getAnimatedValue() * w * 2f;
            paint.setShader(new android.graphics.LinearGradient(x - w * 0.35f, 0, x, 0,
                    new int[]{0xFFFFFFFF, 0xFFFFF4D6, 0xFFB9CCFF, 0xFFFFFFFF}, new float[]{0f, 0.45f, 0.7f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));
            hero.invalidate();
        });
        sheen.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                paint.setShader(null);
                hero.invalidate();
            }
        });
        sheen.start();
    }

    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static NameHighlight attach(Activity home) {
        int gridId = home.getResources().getIdentifier("grid", "id", home.getPackageName());
        int nameId = home.getResources().getIdentifier("name", "id", home.getPackageName());
        View grid = gridId == 0 ? null : home.findViewById(gridId);
        if (!(grid instanceof ViewGroup) || nameId == 0) return null;
        NameHighlight h = new NameHighlight(home, (ViewGroup) grid, nameId);
        h.attachHero(home);
        grid.getViewTreeObserver().addOnGlobalLayoutListener(h);
        h.onGlobalLayout();
        return h;
    }

    void destroy() {
        if (sheen != null) sheen.cancel();
        if (hero != null && heroWatcher != null) hero.removeTextChangedListener(heroWatcher);
        if (grid.getViewTreeObserver().isAlive()) grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
    }

    @Override
    public void onGlobalLayout() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View n = grid.getChildAt(i).findViewById(nameId);
            if (n instanceof TextView) style((TextView) n);
        }
    }

    private void style(TextView name) {
        ViewGroup.LayoutParams lp = name.getLayoutParams();
        // Upstream sizes the name to the tile's width (again when the columns change): that becomes the pill's
        // largest width, and the pill itself wraps the name.
        if (lp != null && lp.width > 0) {
            name.setMaxWidth(lp.width);
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            }
            name.setLayoutParams(lp);
        }
        if (name.getTag(R.id.mibox_name_styled) == STYLED) return;
        name.setTag(R.id.mibox_name_styled, STYLED);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xF7FFFFFF);
        pill.setCornerRadius(12 * dp);
        name.setBackground(pill);
        int pad = Math.round(12 * dp);
        name.setPadding(pad, 0, pad, 0);
        name.setTextColor(0xFF111216);
        name.setShadowLayer(0, 0, 0, 0);
        name.setElevation(5 * dp);
        if (face != null) name.setTypeface(face);
    }
}
