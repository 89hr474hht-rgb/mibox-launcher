package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.Context;
import android.view.View;

/**
 * The launcher's own ids and styles, looked up by name: this library cannot see the app's R class. Every name used
 * here is checked against the launcher's resources by UpstreamHooksTest, so an upstream rename fails the build rather
 * than a screen; at run time a name that is not found is logged and the feature that needs it stays off.
 */
final class UpstreamViews {
    private UpstreamViews() {
    }

    @SuppressWarnings("DiscouragedApi") // the app's R is not visible from this library
    static int id(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "id", c.getPackageName());
        if (id == 0) Logs.missing("UpstreamViews.id", name);
        return id;
    }

    @SuppressWarnings("DiscouragedApi")
    static int style(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "style", c.getPackageName());
        if (id == 0) Logs.missing("UpstreamViews.style", name);
        return id;
    }

    /** A string array of the launcher's config.xml (dock_apps), or none. */
    @SuppressWarnings("DiscouragedApi")
    static String[] array(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "array", c.getPackageName());
        if (id == 0) {
            Logs.missing("UpstreamViews.array", name);
            return new String[0];
        }
        return c.getResources().getStringArray(id);
    }

    /** The view with that id name in the activity, or null (logged). */
    static View find(Activity a, String name) {
        int id = id(a, name);
        if (id == 0) return null;
        View v = a.findViewById(id);
        if (v == null) Logs.missing("UpstreamViews.find", name);
        return v;
    }
}
