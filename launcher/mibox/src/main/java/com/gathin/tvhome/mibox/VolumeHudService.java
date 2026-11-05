package com.gathin.tvhome.mibox;

import android.accessibilityservice.AccessibilityService;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The launcher's own volume bar, for sound that plays on a Bluetooth speaker or headphones. Only then does it
 * take the remote's volume keys: it changes the media volume itself, without Android's panel, and shows
 * {@link VolumeHud}. With sound on the TV (HDMI), the keys pass through untouched, so HDMI-CEC still sends them
 * to the TV, which shows its own bar: the box cannot read the TV's level, so it does not pretend to.
 *
 * Keys only: it asks for no accessibility events and reads nothing on screen. Turned on once over ADB
 * (mibox/docs/05-mises-a-jour.md); Android turns it off again if the launcher is force-stopped, and the volume
 * keys then simply work as before.
 */
public final class VolumeHudService extends AccessibilityService {
    /** A key swallowed here gets no auto-repeat from Android, so a held one repeats here. */
    static final long REPEAT_DELAY_MS = 400;
    static final long REPEAT_INTERVAL_MS = 70;
    /** AudioDeviceInfo.TYPE_BLE_HEADSET and TYPE_BLE_SPEAKER, public from Android 12. */
    private static final int TYPE_BLE_HEADSET = 26, TYPE_BLE_SPEAKER = 27;

    private AudioManager audio;
    private VolumeHud hud;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean enabled, anyOutput;
    private int heldKey;
    private Repeat repeat;

    private final class Repeat implements Runnable {
        final int direction;
        final String name;

        Repeat(int direction, String name) {
            this.direction = direction;
            this.name = name;
        }

        @Override
        public void run() {
            step(direction, name);
            handler.postDelayed(this, REPEAT_INTERVAL_MS);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        audio = getSystemService(AudioManager.class);
        enabled = getResources().getBoolean(R.bool.mibox_volume_hud);
        anyOutput = getResources().getBoolean(R.bool.mibox_volume_hud_any_output);
        if (hud == null) hud = new VolumeHud(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int direction = direction(code);
        if (direction == 0 || !enabled || audio == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) return heldKey == code;
            String name = bluetoothOutput();
            if (name == null && anyOutput) name = "";
            if (name == null) return false;   // sound on the TV: Android and HDMI-CEC handle the key
            stopRepeat();
            heldKey = code;
            step(direction, name);
            if (direction != AudioManager.ADJUST_TOGGLE_MUTE) {
                repeat = new Repeat(direction, name);
                handler.postDelayed(repeat, REPEAT_DELAY_MS);
            }
            return true;
        }
        if (heldKey != code) return false;   // its down went through, so its up does too
        stopRepeat();
        heldKey = 0;
        return true;
    }

    static int direction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP: return AudioManager.ADJUST_RAISE;
            case KeyEvent.KEYCODE_VOLUME_DOWN: return AudioManager.ADJUST_LOWER;
            case KeyEvent.KEYCODE_VOLUME_MUTE: return AudioManager.ADJUST_TOGGLE_MUTE;
            default: return 0;
        }
    }

    private void step(int direction, String name) {
        int stream = AudioManager.STREAM_MUSIC;
        int max = audio.getStreamMaxVolume(stream);
        int before = audio.getStreamVolume(stream);
        boolean mutedBefore = audio.isStreamMute(stream);
        try {
            audio.adjustStreamVolume(stream, direction, 0);   // no flags: no Android panel, no click
        } catch (SecurityException e) {
            return;   // Do Not Disturb refused the change
        }
        int after = audio.getStreamVolume(stream);
        boolean muted = audio.isStreamMute(stream);
        int push = direction == AudioManager.ADJUST_TOGGLE_MUTE || muted || mutedBefore ? 0
                : VolumeLevels.push(direction == AudioManager.ADJUST_RAISE, before, after, max);
        hud.show(before, mutedBefore, after, muted, max, name, push);
    }

    /** The name of the Bluetooth speaker or headphones media plays on, or null when it plays on the TV. */
    private String bluetoothOutput() {
        for (AudioDeviceInfo d : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int type = d.getType();
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_HEARING_AID
                    || type == TYPE_BLE_HEADSET || type == TYPE_BLE_SPEAKER) {
                CharSequence n = d.getProductName();
                return n == null ? "" : n.toString();
            }
        }
        return null;
    }

    private void stopRepeat() {
        if (repeat != null) handler.removeCallbacks(repeat);
        repeat = null;
    }

    @Override
    public void onDestroy() {
        stopRepeat();
        if (hud != null) hud.destroy();
        hud = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
