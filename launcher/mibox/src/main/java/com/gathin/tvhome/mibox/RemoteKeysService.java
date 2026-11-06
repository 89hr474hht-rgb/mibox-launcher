package com.gathin.tvhome.mibox;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's Netflix, YouTube and Prime Video buttons open the app chosen for each in LaunchOS's settings
 * (RemoteRemap), from any screen. Google TV's home used to answer them; with it disabled, they reach no one. Every
 * other key, volume included, passes through untouched. It never reads what is on screen; the one thing it watches is
 * when an app of mibox_a11y_pause_packages comes up, to take LaunchOS's helpers away while it is in front (A11yPause).
 * It also puts the box to sleep at the end of the sleep timer (SleepTimer), the one way an app can do that.
 * Switched on once: Settings, Accessibility, "LaunchOS remote buttons", or
 * {@code settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.mibox.RemoteKeysService}.
 */
public final class RemoteKeysService extends AccessibilityService {
    /** The running service, while Android has it bound; main thread only. */
    private static RemoteKeysService running;
    private int downKey = -1;

    @Override
    protected void onServiceConnected() {
        running = this;
        watchPausedApps();
    }

    /**
     * A11yPause, checked again whenever a LaunchOS screen comes up: a permission granted (or taken back) after the
     * service started then counts without restarting it.
     */
    static void refreshPause() {
        RemoteKeysService s = running;
        if (s != null) s.watchPausedApps();
    }

    /** A11yPause: window changes of the listed apps only (packageNames), and only while it can act on them. */
    private void watchPausedApps() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        boolean on = A11yPause.active(this);
        int events = watchedEvents(info.eventTypes, on);
        if (events == info.eventTypes) return;
        info.eventTypes = events;
        info.packageNames = on ? A11yPause.packages(this) : null;
        setServiceInfo(info);
    }

    /** The event types with window changes added when {@code on}, taken away otherwise. */
    static int watchedEvents(int eventTypes, boolean on) {
        return on ? eventTypes | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                : eventTypes & ~AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (running == this) running = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
    }

    /**
     * Puts the box to sleep, as its power button does; with HDMI-CEC the TV follows. False when the service is off.
     * Android's lock-screen action sends the device to sleep before it locks; a TV has no lock screen to show.
     */
    static boolean sleep() {
        RemoteKeysService s = running;
        if (s == null || android.os.Build.VERSION.SDK_INT < 28) return false;   // the action came with Android 9
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (A11yPause.pausesFor(A11yPause.packages(this), event.getPackageName())) A11yPause.pause(this);
    }

    @Override
    public void onInterrupt() {
    }
}
