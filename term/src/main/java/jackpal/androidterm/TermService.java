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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.thothterm.Application;
import com.thothterm.BuildConfig;
import com.thothterm.R;
import com.thothterm.RemoteSession;
import com.thothterm.TermActivity;
import com.thothterm.compat.PackageManagerCompat;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.services.CommandService;
import com.thothterm.services.SessionsService;

import java.util.UUID;

import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.libtermexec.v1.ITerminal;
import jackpal.androidterm.util.TermSettings;


public class TermService extends SessionsService {
    private static final int RUNNING_NOTIFICATION = 1;

    private final IBinder mTSBinder = new TSBinder();
    private CommandService command_service;

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
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (TermExec.SERVICE_ACTION_V1.equals(intent.getAction())) {
            ThothLog.d(LogCategory.SESSION, "Service bound by external process");

            return new RBinder();
        } else {
            ThothLog.d(LogCategory.SESSION, "Service bound by activity");

            return mTSBinder;
        }
    }

    @Override
    public void onCreate() {
        /* Put the service in the foreground. */
        Notification notification = buildNotification();
        StartForeground.start(this, notification);

        command_service = new CommandService(this);
        command_service.start();

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

        command_service.stop();
        clearSessions();
        StopForeground.stop(this);
        super.onTimeout(startId, fgsType);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        command_service.stop();
        clearSessions();
        StopForeground.stop(this);
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
                    CharSequence msg = context.getText(R.string.service_notify_text);
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


    private static class StartForeground {
        private static void start(Service service, Notification notification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q /*API level 29*/)
                Compat29.start(service, notification);
            else
                Compat5.start(service, notification);
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

    private final class RBinder extends ITerminal.Stub {
        @Override
        public IntentSender startSession(final ParcelFileDescriptor pseudoTerminalMultiplexerFd,
                                         final ResultReceiver callback) {
            final String sessionHandle = UUID.randomUUID().toString();

            final PendingIntent result = createResultIntent(sessionHandle);

            final String niceName = getNiceName();
            if (niceName == null) return null;

            createBoundSession(pseudoTerminalMultiplexerFd, sessionHandle, niceName,
                    new RBinderCleanupCallback(result, callback));
            return result.getIntentSender();
        }

        private String getNiceName() {
            final PackageManager pm = getPackageManager();
            final String[] pkgs = pm.getPackagesForUid(getCallingUid());
            if (pkgs == null)
                return null;

            for (String packageName : pkgs) {
                try {
                    final PackageInfo pkgInfo = PackageManagerCompat.getPackageInfo(pm, packageName);
                    if (BuildConfig.APPLICATION_ID.equals(pkgInfo.packageName))
                        continue;

                    final ApplicationInfo appInfo = pkgInfo.applicationInfo;
                    if (appInfo == null)
                        continue;

                    final CharSequence label = pm.getApplicationLabel(appInfo);

                    if (!TextUtils.isEmpty(label))
                        return label.toString();
                } catch (PackageManager.NameNotFoundException ignore) {
                }
            }

            return null;
        }

        private PendingIntent createResultIntent(final String sessionHandle) {
            // distinct Intent Uri and PendingIntent requestCode must be sufficient to avoid collisions
            final Intent switchIntent = new Intent(getApplicationContext(), RemoteSession.class)
                    .setAction(Application.ACTION_OPEN_NEW_WINDOW)
                    .setData(Uri.parse(sessionHandle))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Application.ARGUMENT_TARGET_WINDOW, sessionHandle);

            int flags = PendingIntent.FLAG_ONE_SHOT;
            return ActivityPendingIntent.get(getApplicationContext(),
                    sessionHandle.hashCode(), switchIntent, flags);
        }

        private void createBoundSession(final ParcelFileDescriptor fd, String handle, String issuerTitle,
                                        final TermSession.FinishCallback callback) {
            new Handler(Looper.getMainLooper()).post(() -> {
                final TermSettings settings = new TermSettings(getApplicationContext());

                GenericTermSession session = new BoundSession(fd, settings, issuerTitle);
                session.setHandle(handle);
                session.setTitle("");
                session.initializeEmulator(80, 24);

                addSession(session, callback);
            });
        }
    }

    private final class RBinderCleanupCallback implements TermSession.FinishCallback {
        private final PendingIntent result;
        private final ResultReceiver callback;

        public RBinderCleanupCallback(PendingIntent result, ResultReceiver callback) {
            this.result = result;
            this.callback = callback;
        }

        @Override
        public void onSessionFinish(TermSession session) {
            result.cancel();

            callback.send(0, new Bundle());

            removeSession(session);
        }
    }
}
