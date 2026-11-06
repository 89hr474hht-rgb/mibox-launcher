package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Typeface;

/**
 * LaunchOS's text face (Inter, in mibox/app-res/font), looked up by name since the app's R is not visible from this
 * library. Each weight is a font family built once per process. Null when the app does not bundle it (an upstream
 * build): the view keeps Android's font.
 */
final class UiFonts {
    private UiFonts() {
    }

    static Typeface light(Context context) {
        return get(context, "launchos_light");
    }

    static Typeface regular(Context context) {
        return get(context, "launchos_regular");
    }

    static Typeface medium(Context context) {
        return get(context, "launchos_medium");
    }

    static Typeface semibold(Context context) {
        return get(context, "launchos_semibold");
    }

    static Typeface bold(Context context) {
        return get(context, "launchos_bold");
    }

    /**
     * Gives every text under root that still has Android's font the LaunchOS face: semibold where the layout asks for
     * bold, regular otherwise. Texts given a face in code keep it.
     */
    static void apply(android.view.View root) {
        Typeface regular = regular(root.getContext()), semibold = semibold(root.getContext());
        if (regular == null) return;
        apply(root, regular, semibold != null ? semibold : regular);
    }

    private static void apply(android.view.View v, Typeface regular, Typeface semibold) {
        if (v instanceof android.widget.TextView) {
            android.widget.TextView t = (android.widget.TextView) v;
            Typeface now = t.getTypeface();
            if (now == null || now == Typeface.DEFAULT || now == Typeface.DEFAULT_BOLD || now == Typeface.SANS_SERIF) {
                t.setTypeface(now != null && now.isBold() ? semibold : regular);
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), regular, semibold);
        }
    }

    @SuppressWarnings("DiscouragedApi")
    private static Typeface get(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "font", context.getPackageName());
        if (id == 0) return null;
        try {
            return context.getResources().getFont(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
