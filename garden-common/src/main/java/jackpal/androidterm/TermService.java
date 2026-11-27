/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2018-2025 Roumen Petrov.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jackpal.androidterm;

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.thothterm.Application;
import com.thothterm.R;
import com.thothterm.TermActivity;
import com.thothterm.audio.AudioBridge;
import com.thothterm.lan.LanController;
import com.thothterm.dock.ThothDock;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.services.SessionsService;

import java.util.ArrayList;
import java.util.List;

import jackpal.androidterm.emulatorview.TermSession;


public class TermService extends SessionsService {
    private static final int RUNNING_NOTIFICATION = 1;

    private final IBinder mTSBinder = new TSBinder();
    private final LanController.Listener lanListener = this::refreshNotification;
    private final ThothDock.Listener dockListener = status -> refreshNotification();
    /**
     * "Linux audio" lives here, in the process that owns the shells: the
     * bridge to the guest's sound server runs as long as the shells do and
     * follows the {@link AudioBridge#PREF_KEY} preference wherever it is
     * changed -- the menu or the Settings switch. It reports on its own
     * thread whenever sound starts or stops; that refreshes the notification.
     * A field, because SharedPreferences only holds its listeners weakly.
     */
    private AudioBridge audioBridge;
    private final SharedPreferences.OnSharedPreferenceChangeListener audioBridgeListener =
            (prefs, key) -> {
                if (AudioBridge.PREF_KEY.equals(key)) applyAudioBridge();
            };

    private static Notification buildNotification(Context context, NotificationSettings callback) {
        NotificationChannelCompat.create(context);

        Intent notifyIntent = TermActivity.getNotificationIntent(context);
        PendingIntent pendingIntent = ActivityPendingIntent.get(context, 0, notifyIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context,
                Application.NOTIFICATION_CHANNEL_SESSIONS)
                .setSmallIcon(R.drawable.ic_stat_service_notification_icon)
                .setContentTitle(context.getText(R.string.application_terminal))
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setContentIntent(pendingIntent);
        callback.set(context, builder);
        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return restartPolicy();
    }

    static int restartPolicy() {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Only this app's own activities bind; the service is not exported.
        ThothLog.d(LogCategory.SESSION, "Service bound by activity");
        return mTSBinder;
    }

    @Override
    public void onCreate() {
        // Start before the first notification is built, so it shows the state.
        audioBridge = new AudioBridge(AudioBridge.hostFifo(this));
        audioBridge.setListener(this::onAudioActiveChanged);
        if (AudioBridge.isWanted(this)) {
            audioBridge.start();
            ThothLog.i(LogCategory.APP, "Audio bridge started");
        }

        /* Put the service in the foreground. */
        Notification notification = buildNotification();
        if (!StartForeground.start(this, notification)) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(audioBridgeListener);
        LanController.get().addListener(lanListener);
        LanController.get().hostStarted();
        // Optional QA capability: never waited for, never a reason to fail.
        ThothDock dock = ThothDock.getIfInitialized();
        if (dock != null) {
            dock.addListener(dockListener);
            dock.ensureRunning();
        }

        ThothLog.i(LogCategory.APP, "Terminal service started");
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        ThothLog.w(LogCategory.SESSION, "Foreground service timeout; clearing sessions");
        // LAN Mode first, so its notification refresh cannot replace the timeout notice.
        LanController.get().removeListener(lanListener);
        LanController.get().hostStopped();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M /*API Level 23*/) {
            NotificationManager notificationManager = this.getApplicationContext().getSystemService(NotificationManager.class);
            notificationManager.notify(
                    RUNNING_NOTIFICATION,
                    buildNotification(this.getApplicationContext(), (context, builder) -> {
                                CharSequence msg = context.getText(R.string.service_timeout_text);
                                builder.setContentText(msg).setTicker(msg);
                            }
                    ));
        }

        clearSessions();
        stopThothDock();
        stopAudioBridge();
        StopForeground.stop(this);
        super.onTimeout(startId, fgsType);
        stopSelf();
    }

    /** The bridge started or stopped playing (on its own thread): the notification follows. */
    private void onAudioActiveChanged(boolean active) {
        ThothLog.d(LogCategory.APP, active ? "Linux audio: playing" : "Linux audio: idle");
        refreshNotification();
    }

    /** Whether the audio bridge is running (wanted and reading), whether or not sound flows. */
    public boolean isAudioBridgeOn() {
        return audioBridge != null && audioBridge.isRunning();
    }

    /** Whether sound from the guest is playing right now. */
    public boolean isAudioActive() {
        return audioBridge != null && audioBridge.isActive();
    }

    /**
     * Make the audio bridge follow the preference and show the result. Called
     * by the preference listener and by the terminal activity after it flips
     * the preference; idempotent, so the order and the number of calls do
     * not matter.
     */
    public void applyAudioBridge() {
        if (audioBridge == null) return;
        boolean was = audioBridge.isRunning();
        boolean wanted = AudioBridge.isWanted(this);
        if (wanted) {
            audioBridge.start();
        } else {
            audioBridge.stop();
        }
        if (wanted != was) {
            ThothLog.i(LogCategory.APP, wanted ? "Audio bridge started" : "Audio bridge stopped");
        }
        refreshNotification();
    }

    /** The service is going away: the bridge goes with it; the preference stays for the next start. */
    private void stopAudioBridge() {
        if (audioBridge == null) return;
        if (audioBridge.isRunning()) ThothLog.i(LogCategory.APP, "Audio bridge stopped");
        audioBridge.stop();
    }

    /**
     * The pids of the shell process groups this service owns, captured before
     * the sessions are torn down so Exit can check afterwards that nothing of
     * ours outlived the hangup.
     */
    public int[] sessionProcessIds() {
        List<Integer> pids = new ArrayList<>();
        for (TermSession session : getSessions()) {
            if (session instanceof ShellTermSession) {
                pids.add(((ShellTermSession) session).getProcessId());
            }
        }
        int[] result = new int[pids.size()];
        for (int i = 0; i < result.length; ++i) result[i] = pids.get(i);
        return result;
    }

    /**
     * Stop taking new work, hang up every session, and drop the ongoing
     * notification. Used by the Exit action; ordinary teardown still goes
     * through {@link #onDestroy()}.
     */
    public void shutdownAll() {
        LanController.get().removeListener(lanListener);
        LanController.get().hostStopped();
        clearSessions();
        stopThothDock();
        stopAudioBridge();
        removeRunningNotification();
    }

    private void stopThothDock() {
        ThothDock dock = ThothDock.getIfInitialized();
        if (dock == null) return;
        dock.removeListener(dockListener);
        dock.stop();
    }

    /**
     * Take the ongoing notification down with the service.
     * <p>
     * {@link StopForeground} only detaches it, which is right while the app
     * keeps running, but leaves "Terminal session is running" on screen after
     * a full shutdown. Cancelling it separately races the detach, so ask for
     * removal through the same call that ends the foreground state.
     */
    private void removeRunningNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N /*API level 24*/)
            RemoveForegroundCompat24.stop(this);
        else
            stopForeground(true);

        NotificationManager manager = getApplicationContext()
                .getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(RUNNING_NOTIFICATION);
    }

    @RequiresApi(24)
    private static class RemoveForegroundCompat24 {
        private static void stop(Service service) {
            service.stopForeground(STOP_FOREGROUND_REMOVE);
        }
    }

    @Override
    public void onDestroy() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(audioBridgeListener);
        LanController.get().removeListener(lanListener);
        LanController.get().hostStopped();
        clearSessions();
        stopThothDock();
        stopAudioBridge();
        removeRunningNotification();
        super.onDestroy();

        ThothLog.i(LogCategory.APP, "Terminal service stopped");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM /*API Level 35*/) {
            // forced VM exist may start new timeout counter ...
            System.exit(0);
        }
    }


    private Notification buildNotification() {
        final boolean lanActive = LanController.get().isOn();
        final boolean audio = isAudioBridgeOn();
        final boolean audioActive = isAudioActive();
        return buildNotification(this.getApplicationContext(),
                (context, builder) -> {
                    CharSequence msg = context.getText(R.string.service_notify_text);
                    // State only, no button: the menu and Settings flip it.
                    if (audio) {
                        msg = context.getString(audioActive ? R.string.service_notify_text_audio_playing
                                : R.string.service_notify_text_audio_idle, msg);
                    }
                    builder.setContentText(msg).setTicker(msg);
                    ThothDock dock = ThothDock.getIfInitialized();
                    if (dock != null && dock.isBundled()) {
                        // State only; the terminal is usable whatever it says.
                        msg = msg + "\n" + dock.statusLine();
                        builder.setSubText(dock.statusLine())
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg));
                    }
                    if (lanActive) {
                        // State only: never the address, the PIN or any credential.
                        CharSequence lan = context.getText(R.string.lan_notify_active);
                        builder.setSubText(lan)
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg + "\n" + lan));
                    }
                }
        );
    }

    /** Show or clear the LAN line after LAN Mode changes, or the audio state after it changes. */
    private void refreshNotification() {
        NotificationManager manager = getApplicationContext().getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(RUNNING_NOTIFICATION, buildNotification());
    }


    @FunctionalInterface
    interface NotificationSettings {
        void set(Context context, NotificationCompat.Builder builder);
    }


    private static class NotificationChannelCompat {
        private static void create(Context context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O /*API Level 26*/) return;
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            Compat26.create(context);
        }

        @RequiresApi(26)
        private static class Compat26 {
            private static void create(Context context) {
                // Register the channel with the system ...
                // Note we can't change the importance or other notification behaviors after this.
                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                if (notificationManager.getNotificationChannel(Application.NOTIFICATION_CHANNEL_SESSIONS) != null)
                    return;

                NotificationChannel channel = new NotificationChannel(
                        Application.NOTIFICATION_CHANNEL_SESSIONS,
                        context.getString(R.string.application_terminal),
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(
                        context.getString(R.string.notification_channel_sessions_description));
                channel.setShowBadge(false);

                notificationManager.createNotificationChannel(channel);
            }
        }
    }


    private static class ActivityPendingIntent {
        private static PendingIntent get(Context context, int requestCode, Intent intent, int flags) {
            /* Notes:
            It target is Android API Level 31 pending intents must set explicitly one of "mutable"
            flags. Versions before assume mutable by default.
            Let force immutable value on first available version.
            */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M /*API level 23*/)
                flags |= PendingIntent.FLAG_IMMUTABLE;
            return Compat16.get(context, requestCode, intent, flags);
        }

        private static class Compat16 {
            private static PendingIntent get(Context context, int requestCode, Intent intent, int flags) {
                // Note java.lang.IllegalArgumentException on Android 15 /*API level 35*/ if target is 35:
                // Note not required on Android 14 /*API level 34*/:
                //    ActivityOptions options = ActivityOptions.makeBasic();
                //    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                //    bundle = options.toBundle();
                // Note not required on Android 13 /*API level 33*/:
                //    ActivityOptions options = ActivityOptions.makeBasic();
                //    options.setPendingIntentBackgroundActivityLaunchAllowed(true);
                //    bundle = options.toBundle();
                return PendingIntent.getActivity(context, requestCode, intent, flags, null);
            }
        }
    }


    static class StartForeground {
        private static boolean start(Service service, Notification notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S /*API level 31*/)
                return Compat31.start(service, notification);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q /*API level 29*/)
                Compat29.start(service, notification);
            else
                Compat5.start(service, notification);
            return true;
        }

        @FunctionalInterface
        interface ForegroundStartAction {
            void start();
        }

        @RequiresApi(31)
        static class Compat31 {
            private static boolean start(Service service, Notification notification) {
                return attempt(
                        () -> Compat29.start(service, notification),
                        () -> {
                            ThothLog.w(LogCategory.APP,
                                    "Foreground service promotion rejected; stopping service");
                            service.stopSelf();
                        });
            }

            static boolean attempt(ForegroundStartAction action, Runnable onRejected) {
                try {
                    action.start();
                    return true;
                } catch (ForegroundServiceStartNotAllowedException e) {
                    onRejected.run();
                    return false;
                }
            }
        }

        @RequiresApi(29)
        private static class Compat29 {
            private static void start(Service service, Notification notification) {
                // NOTE: foregroundServiceType argument should match Android manifest:
                // - service
                // - uses-permission
                // mediaPlayback as well as specialUse: the audio bridge plays the
                // guest's sound while the app is in the background, and from
                // Android 14 that needs the type (and its permission) declared.
                service.startForeground(RUNNING_NOTIFICATION, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                                | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            }
        }

        private static class Compat5 {
            private static void start(Service service, Notification notification) {
                service.startForeground(RUNNING_NOTIFICATION, notification);
            }
        }
    }


    private static class StopForeground {
        private static void stop(Service service) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N /*API level 24*/)
                Compat24.stop(service);
            else
                Compat5.stop(service);
        }

        @RequiresApi(24)
        private static class Compat24 {
            private static void stop(Service service) {
                service.stopForeground(STOP_FOREGROUND_DETACH);
            }
        }

        // Explicitly suppress deprecation warnings
        // "stopForeground(boolean) in Service has been deprecated" in API level 33
        @SuppressWarnings({"deprecation", "RedundantSuppression"})
        private static class Compat5 {
            private static void stop(Service service) {
                service.stopForeground(true);
            }
        }
    }


    public class TSBinder extends Binder {
        public TermService getService() {
            ThothLog.d(LogCategory.SESSION, "Activity binding to service");
            return TermService.this;
        }
    }
}
