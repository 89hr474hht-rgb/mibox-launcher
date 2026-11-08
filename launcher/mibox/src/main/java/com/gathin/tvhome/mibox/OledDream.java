package com.gathin.tvhome.mibox;

import android.service.dreams.DreamService;

/**
 * LaunchOS's sleep screen, Android's screensaver once switched on in Settings → Veille: true black with the time
 * (and the date or the sleep timer), moving each minute so an OLED panel is never marked. Any key wakes the box
 * back to where it was. No network, no picture: one small frame a minute.
 */
public final class OledDream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(false);
        setContentView(new OledClockView(this));
    }
}
