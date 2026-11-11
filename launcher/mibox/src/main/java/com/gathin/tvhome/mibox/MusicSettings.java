package com.gathin.tvhome.mibox;

import android.app.Activity;
import android.content.Intent;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Settings → Musique: open the player, show its card on the home screen or not, the access to audio files, and the
 * box's storage spaces (internal storage, USB drives) with what each holds. The list follows a drive being plugged in
 * or taken out. Run by SettingsPanes; the ids are looked up by name since the app's R is not visible here.
 */
final class MusicSettings implements MusicLibrary.Listener {
    private static final int ASK = 8;

    private final Activity activity;
    private final Runnable rewire;
    private final TextView card, access, accessHint;
    private final LinearLayout storage;
    private final MusicLibrary library;
    private final int rowStyle, dividerStyle, hintStyle;

    @SuppressWarnings("DiscouragedApi") // the app's R and styles are not visible from this library
    private MusicSettings(Activity a, Runnable rewire) {
        activity = a;
        this.rewire = rewire;
        library = MusicLibrary.get(a);
        String pkg = a.getPackageName();
        card = (TextView) find("miboxMusicCard");
        access = (TextView) find("miboxMusicAccess");
        accessHint = (TextView) find("miboxMusicAccessHint");
        storage = (LinearLayout) find("miboxStorageList");
        rowStyle = a.getResources().getIdentifier("MiboxSetRow", "style", pkg);
        dividerStyle = a.getResources().getIdentifier("MiboxSetDivider", "style", pkg);
        hintStyle = a.getResources().getIdentifier("MiboxSetHint", "style", pkg);
        View open = find("miboxMusicOpen");
        if (open != null) open.setOnClickListener(v -> a.startActivity(new Intent(a, MusicActivity.class)));
        if (card != null) card.setOnClickListener(v -> {
            MusicCard.setEnabled(a, !MusicCard.enabled(a));
            states();
        });
        if (access != null) access.setOnClickListener(v -> {
            if (!MusicLibrary.canRead(a)) a.requestPermissions(new String[]{MusicLibrary.permission()}, ASK);
        });
    }

    /** Null when the page is the upstream one (no Musique page). */
    static MusicSettings attach(Activity a, Runnable rewire) {
        MusicSettings m = new MusicSettings(a, rewire);
        if (m.storage == null || m.access == null) return null;
        return m;
    }

    @SuppressWarnings("DiscouragedApi")
    private View find(String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    /** The page is shown: read the storage again and listen while it is. */
    void shown() {
        library.addListener(this);
        library.refresh();
        states();
    }

    void hidden() {
        library.removeListener(this);
    }

    @Override
    public void libraryChanged() {
        states();
    }

    void states() {
        if (card != null) card.setSelected(MusicCard.enabled(activity));
        boolean allowed = MusicLibrary.canRead(activity);
        access.setSelected(allowed);
        if (accessHint != null) {
            accessHint.setText(allowed ? R.string.mibox_music_access_on : R.string.mibox_music_access_off);
        }
        storage.removeAllViews();
        boolean first = true;
        for (MusicLibrary.Volume v : library.volumes()) {
            if (!first && dividerStyle != 0) storage.addView(new View(activity, null, 0, dividerStyle), divider());
            first = false;
            TextView row = rowStyle == 0 ? new TextView(activity) : new TextView(activity, null, 0, rowStyle);
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(v.removable ? R.drawable.mibox_tile_storage
                    : R.drawable.mibox_tile_device, 0, 0, 0);
            String name = v.name;
            if (v.tracks > 0) {
                name += " · " + activity.getResources().getQuantityString(R.plurals.mibox_music_tracks, v.tracks, v.tracks);
            }
            row.setText(name);
            row.setFocusable(true);
            row.setOnClickListener(x -> activity.startActivity(new Intent(activity, MusicActivity.class)));
            storage.addView(row, rowParams());
            TextView hint = hintStyle == 0 ? new TextView(activity) : new TextView(activity, null, 0, hintStyle);
            hint.setText(space(v));
            storage.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (library.volumes().isEmpty()) {
            TextView hint = hintStyle == 0 ? new TextView(activity) : new TextView(activity, null, 0, hintStyle);
            hint.setText(R.string.mibox_music_searching);
            storage.addView(hint);
        }
        rewire.run();
    }

    private String space(MusicLibrary.Volume v) {
        if (!v.mounted) return activity.getString(R.string.mibox_music_volume_off);
        if (v.total <= 0) return activity.getString(v.removable ? R.string.mibox_music_volume_usb : R.string.mibox_music_volume_internal);
        return activity.getString(R.string.mibox_music_volume_space, Formatter.formatShortFileSize(activity, v.free),
                Formatter.formatShortFileSize(activity, v.total));
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(56 * activity.getResources().getDisplayMetrics().density));
    }

    private LinearLayout.LayoutParams divider() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density)));
        float d = activity.getResources().getDisplayMetrics().density;
        lp.setMargins(Math.round(60 * d), Math.round(2 * d), Math.round(12 * d), Math.round(2 * d));
        return lp;
    }
}
