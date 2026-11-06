package com.gathin.tvhome.mibox;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's Netflix, YouTube and Prime Video buttons open the app chosen for each in LaunchOS's settings
 * (RemoteRemap), from any screen. Google TV's home used to answer them; with it disabled, they reach no one. Every
 * other key, volume included, passes through untouched. It listens to keys only, never to what is on screen.
 * Switched on once: Settings, Accessibility, "LaunchOS remote buttons", or
 * {@code settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.mibox.RemoteKeysService}.
 */
public final class RemoteKeysService extends AccessibilityService {
    private int downKey = -1;
    private VolumeBar volumeBar;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        volumeBar = new VolumeBar(this);
    }

    @Override
    public void onDestroy() {
        if (volumeBar != null) volumeBar.stop();
        volumeBar = null;
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (!RemoteRemap.isAppButton(code)) return false;
        String target = RemoteRemap.target(this, code);
        // A press opens on release, once; the held repeats and the release are kept from the app in front too.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) downKey = code;
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean pressed = downKey == code && !event.isCanceled();
            downKey = -1;
            if (pressed && !RemoteRemap.NONE.equals(target)) open(target);
        }
        return true;
    }

    private void open(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;   // uninstalled or disabled since: the button does nothing
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            // Nothing to open.
        }
    }

    /** Only Android's own volume panel matters here: its window class, never anything on screen. */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (volumeBar == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cls = event.getClassName();
        if (cls != null && "com.android.systemui".contentEquals(String.valueOf(event.getPackageName()))
                && cls.toString().contains(".volume.")) {
            volumeBar.systemPanel();
        }
    }

    @Override
    public void onInterrupt() {
    }
}
