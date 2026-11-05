package com.gathin.tvhome.mibox;

import android.content.Context;
import android.graphics.Typeface;

/** The launcher's own font (Outfit), looked up by name since the app's R is not visible from this library. */
final class UiFonts {
    private UiFonts() {
    }

    /** Outfit at weight 500, or null when the app no longer bundles it (the view keeps Android's font). */
    @SuppressWarnings("DiscouragedApi")
    static Typeface medium(Context context) {
        int id = context.getResources().getIdentifier("outfit_medium", "font", context.getPackageName());
        if (id == 0) return null;
        try {
            return context.getResources().getFont(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
