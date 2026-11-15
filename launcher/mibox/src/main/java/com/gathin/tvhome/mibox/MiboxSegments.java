package com.gathin.tvhome.mibox;

import android.content.Context;
import android.util.AttributeSet;

import com.gathin.launchos.design.SegmentBar;

/**
 * The segmented choices of LaunchOS's settings page: the shared {@link SegmentBar} in LaunchOS's colours, its pill
 * outlined in the accent. Used in place of a plain row in layout/activity_launcher_settings.xml, so the choices keep
 * their ids and upstream code still selects them.
 */
public final class MiboxSegments extends SegmentBar {
    public MiboxSegments(Context c) {
        this(c, null);
    }

    public MiboxSegments(Context c, AttributeSet attrs) {
        super(c, attrs);
        setTrack(c.getColor(R.color.lo_surface_2));
        setPill(c.getColor(R.color.lo_surface_3), c.getColor(R.color.lo_accent));
    }
}
