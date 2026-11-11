package com.gathin.tvhome.mibox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Keeps LaunchOS running while music plays, so it goes on behind the sleep screen or another app's menus. It does
 * nothing else: {@link MusicPlayer} plays. Its notification says what is playing and opens the player.
 */
public final class MusicService extends Service {
    private static final String CHANNEL = "mibox_music";
    private static final int ID = 0x6d75;

    static void start(Context c) {
        try {
            c.startForegroundService(new Intent(c, MusicService.class));
        } catch (RuntimeException ignored) {
            // Android refused a foreground start from the background: the music still plays while LaunchOS is open.
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, MusicService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.mibox_music_title),
                    NotificationManager.IMPORTANCE_LOW));
        }
        MusicPlayer p = MusicPlayer.get(this);
        MusicLibrary.Track t = p.track();
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MusicActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.mibox_ic_music)
                .setContentTitle(t == null ? getString(R.string.mibox_music_title) : t.title)
                .setContentText(p.collection() == null ? "" : p.collection().name)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID, n);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
