package com.lttube.tv.music;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.lttube.tv.R;

/** Keeps the music playing while you use other apps on the TV. */
public class MusicService extends Service {
    private static final String CH = "music";
    private static boolean running;

    static void update(Context c, boolean on) {
        if (on == running) return;
        try {
            Intent i = new Intent(c, MusicService.class);
            if (on) {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
            } else c.stopService(i);
            running = on;
        } catch (Throwable ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CH) == null)
                nm.createNotificationChannel(new NotificationChannel(CH, "Music", NotificationManager.IMPORTANCE_LOW));
        }
        MusicPlayer mp = MusicPlayer.peek();
        MItem cur = mp == null ? null : mp.current();
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, NowPlayingActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        android.app.Notification n = new NotificationCompat.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_music)
                .setContentTitle(cur == null ? "LT Music" : cur.title)
                .setContentText(cur == null ? "" : cur.artist)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(7, n);
        } catch (Throwable e) {
            running = false;
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
