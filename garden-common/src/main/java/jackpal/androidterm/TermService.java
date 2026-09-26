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
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.StatusBarNotification;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.thothterm.Application;
import com.thothterm.NotificationPermission;
import com.thothterm.R;
import com.thothterm.TermActivity;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.services.SessionsService;

import java.util.ArrayList;
import java.util.List;

import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.util.TermSettings;


public class TermService extends SessionsService {
    private static final int RUNNING_NOTIFICATION = 1;

    private final IBinder mTSBinder = new TSBinder();
    /** True while this service holds its foreground state and notification. */
    private boolean mForeground;

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
                // Android 12+ otherwise holds a new foreground-service
                // notification back for up to ten seconds.
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
        // Not exported: only this app's activities bind.
        ThothLog.d(LogCategory.SESSION, "Service bound by activity");
        return mTSBinder;
    }

    @Override
    public void onCreate() {
        /* Put the service in the foreground. */
        Notification notification = buildNotification();
        if (!StartForeground.start(this, notification)) return;
        mForeground = true;

        ThothLog.i(LogCategory.APP, "Terminal service started");
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        ThothLog.w(LogCategory.SESSION, "Foreground service timeout; clearing sessions");
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
        StopForeground.stop(this);
        super.onTimeout(startId, fgsType);
        stopSelf();
    }

    /**
     * The pids of the shell process groups this service owns, captured before
     * the sessions are torn down so Exit can make sure nothing of ours
     * outlives the hangup.
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
     * Hang up every session and drop the ongoing
     * notification. Used by the Exit action; ordinary teardown still goes
     * through {@link #onDestroy()}.
     */
    public void shutdownAll() {
        clearSessions();
        removeRunningNotification();
    }

    /**
     * Post the ongoing notification again if the system is not showing it.
     * <p>
     * The service starts before the user has answered the Android 13+
     * notification prompt, and a notification blocked at that moment is not
     * shown later when consent arrives. Posting under the same id as the
     * foreground notification replaces it in place, so this is safe to call
     * whenever the terminal comes to the front.
     */
    public void refreshRunningNotification() {
        if (!mForeground) return;
        if (!NotificationPermission.isGranted(this)) return;

        NotificationManager manager = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M /*API level 23*/
                && ActiveNotificationsCompat23.isShowing(manager, RUNNING_NOTIFICATION))
            return;
        manager.notify(RUNNING_NOTIFICATION, buildNotification());
    }

    @RequiresApi(23)
    private static class ActiveNotificationsCompat23 {
        private static boolean isShowing(NotificationManager manager, int id) {
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                if (notification.getId() == id) return true;
            }
            return false;
        }
    }

    /**
     * Take the ongoing notification down with the service.
     * <p>
     * {@link StopForeground} only detaches it, which is right while the app
     * keeps running, but leaves "ThothTerm is running" on screen after
     * the service is gone. Cancelling it separately races the detach, so ask
     * for removal through the same call that ends the foreground state.
     */
    private void removeRunningNotification() {
        mForeground = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N /*API level 24*/)
            RemoveForegroundCompat24.stop(this);
        else
            StopForeground.stop(this);

        NotificationManager manager = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
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
        clearSessions();
        removeRunningNotification();
        super.onDestroy();

        ThothLog.i(LogCategory.APP, "Terminal service stopped");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM /*API Level 35*/) {
            // forced VM exist may start new timeout counter ...
            System.exit(0);
        }
    }


    private Notification buildNotification() {
        return buildNotification(this.getApplicationContext(),
                (context, builder) -> {
                    CharSequence msg = context.getString(R.string.garden_notify_text,
                            context.getString(R.string.application_terminal));
                    builder.setContentText(msg).setTicker(msg);
                }
        );
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
                        "ThothTerm",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("ThothTerm running notification");
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
                service.startForeground(RUNNING_NOTIFICATION, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
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
