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

import com.gathin.launchos.design.Motion;

/**
 * The focused app's name, highlighted. Under a grid tile: dark text on a white pill sized to the name, the same white
 * as every focused element, instead of white text on the wallpaper. Above the dock (upstream's big "heroName"): the
 * medium weight, and a short settle when the name changes. Upstream still fades and slides the names in and out on
 * focus; this only restyles their views.
 */
final class NameHighlight implements ViewTreeObserver.OnGlobalLayoutListener {
    private static final Object STYLED = new Object();

    private final ViewGroup grid;
    private final int nameId;
    private final float dp;
    private final Typeface face;
    private final int focus, onFocus;

    private TextView hero;
    private android.text.TextWatcher heroWatcher;

    private NameHighlight(Activity home, ViewGroup grid, int nameId) {
        this.grid = grid;
        this.nameId = nameId;
        dp = home.getResources().getDisplayMetrics().density;
        face = UiFonts.semibold(home);
        focus = home.getColor(R.color.lo_focus);
        onFocus = home.getColor(R.color.lo_on_focus);
    }

    /** The big name above the dock: the medium weight, and a short settle when it changes. */
    private void attachHero(Activity home) {
        int id = UpstreamViews.id(home, "heroName");
        View v = id == 0 ? null : home.findViewById(id);
        if (!(v instanceof TextView)) return;
        hero = (TextView) v;
        Typeface medium = UiFonts.medium(home);
        if (medium != null) hero.setTypeface(medium);
        if (!Motion.on()) return;
        heroWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                hero.setPivotX(0);
                hero.setPivotY(hero.getHeight() / 2f);
                hero.setScaleX(0.97f);
                hero.setScaleY(0.97f);
                hero.animate().scaleX(1f).scaleY(1f).setDuration(Motion.MEDIUM).setInterpolator(Motion.ENTER).start();
            }
        };
        hero.addTextChangedListener(heroWatcher);
    }

    static NameHighlight attach(Activity home) {
        int gridId = UpstreamViews.id(home, "grid");
        int nameId = UpstreamViews.id(home, "name");
        View grid = gridId == 0 ? null : home.findViewById(gridId);
        if (!(grid instanceof ViewGroup) || nameId == 0) return null;
        NameHighlight h = new NameHighlight(home, (ViewGroup) grid, nameId);
        h.attachHero(home);
        grid.getViewTreeObserver().addOnGlobalLayoutListener(h);
        h.onGlobalLayout();
        return h;
    }

    void destroy() {
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
        pill.setColor(focus);
        pill.setCornerRadius(12 * dp);
        name.setBackground(pill);
        int pad = Math.round(12 * dp);
        name.setPadding(pad, 0, pad, 0);
        name.setTextColor(onFocus);
        name.setShadowLayer(0, 0, 0, 0);
        if (face != null) name.setTypeface(face);
    }
}
