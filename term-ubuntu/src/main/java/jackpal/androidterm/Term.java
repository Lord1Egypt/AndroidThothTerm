/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2017-2025 Roumen Petrov.  All rights reserved.
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

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.thothterm.AppCompatActivity;
import com.thothterm.Application;
import com.thothterm.R;
import com.thothterm.Settings;
import com.thothterm.TermActionBar;
import com.thothterm.TerminalZoom;
import com.thothterm.TermPreferencesActivity;
import com.thothterm.WindowListActivity;
import com.thothterm.compat.SoftInputCompat;
import com.thothterm.logging.LogCategory;
import com.thothterm.logging.ThothLog;
import com.thothterm.remote.CommandCollector;
import com.thothterm.services.ServiceManager;
import com.thothterm.utils.ConsoleStartupScript;
import com.thothterm.utils.SimpleClipboardManager;
import com.thothterm.utils.WakeLock;
import com.thothterm.utils.WifiLock;
import com.thothterm.utils.WrapOpenURL;
import com.thothterm.widget.ScreenMessage;
import com.thothterm.widget.ExtraKeysView;

import java.io.IOException;

import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.emulatorview.UpdateCallback;
import jackpal.androidterm.emulatorview.compat.KeycodeConstants;
import jackpal.androidterm.util.SessionList;
import jackpal.androidterm.util.TermSettings;


/**
 * A terminal emulator activity.
 */
public class Term extends AppCompatActivity
        implements UpdateCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    protected static final String WINDOW_ACTION_NEW = "internal.NEW_WINDOW";
    protected static final String WINDOW_ACTION_SWITCH = "internal.SWITCH_WINDOW";

    private final ServiceManager service_manager = new ServiceManager();

    private final ActivityResultLauncher<Intent> request_choose_window =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> onRequestChooseWindow(result.getResultCode(), result.getData())
            );

    /**
     * The ViewFlipper which holds the collection of EmulatorView widgets.
     */
    private TermViewFlipper mViewFlipper;
    private SessionList mTermSessions;
    private TermSettings mSettings;
    private boolean mAlreadyStarted = false;
    private boolean mStopServiceOnFinish = false;
    private int onResumeSelectWindow = -1;
    private boolean command_collected;
    private TermService mTermService;
    private TermActionBar mActionBar;
    private ExtraKeysView mExtraKeys;
    private TermSession mExtraKeysSession;
    private int mLastDisplayedSession = -1;
    private int mActionBarMode;
    private boolean mHaveFullHwKeyboard = false;
    /**
     * Should we use keyboard shortcuts?
     */
    private boolean mUseKeyboardShortcuts;
    /**
     * Intercepts keys before the view/terminal gets it.
     */
    private final View.OnKeyListener mKeyListener = new View.OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            return backkeyInterceptor(keyCode, event) || keyboardShortcuts(keyCode, event);
        }

        /**
         * Keyboard shortcuts (tab management, paste)
         */
        private boolean keyboardShortcuts(int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (!mUseKeyboardShortcuts) {
                return false;
            }
            boolean isCtrlPressed = (event.getMetaState() & KeycodeConstants.META_CTRL_ON) != 0;
            boolean isShiftPressed = (event.getMetaState() & KeycodeConstants.META_SHIFT_ON) != 0;

            if (keyCode == KeycodeConstants.KEYCODE_TAB && isCtrlPressed) {
                if (isShiftPressed) {
                    mViewFlipper.showPrevious();
                } else {
                    mViewFlipper.showNext();
                }

                return true;
            } else if (keyCode == KeycodeConstants.KEYCODE_N && isCtrlPressed && isShiftPressed) {
                doCreateNewWindow();

                return true;
            } else if (keyCode == KeycodeConstants.KEYCODE_V && isCtrlPressed && isShiftPressed) {
                doPaste();

                return true;
            } else {
                return false;
            }
        }

        /**
         * Make sure the back button always leaves the application.
         */
        private boolean backkeyInterceptor(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES && mActionBar.isShowing()) {
                /* We need to intercept the key event before the view sees it,
                   otherwise the view will handle it before we get it */
                onKeyUp(keyCode, event);
                return true;
            } else {
                return false;
            }
        }
    };

    private void onServiceConnection(TermService service) {
        if (service != null) {
            ThothLog.d(LogCategory.SESSION, "Activity connected to terminal service");
            mTermService = service;
            populateSessions();
        } else {
            ThothLog.d(LogCategory.SESSION, "Activity disconnected from terminal service");
            mTermService = null;
        }
    }

    private Handler mHandler;

    protected static TermSession createTermSession(Context context, String extraCommand) throws IOException {
        TermSettings settings = new TermSettings(context);

        String initialCommand = Settings.prepareInitialCommand(context, extraCommand);

        GenericTermSession session = new com.thothterm.linux.UbuntuTermSession(settings, initialCommand);
        // XXX We should really be able to fetch this from within TermSession
        session.setProcessExitMessage(context.getString(R.string.process_exit_message));

        return session;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Application.settings.parsePreference(this, sharedPreferences, key);

        if (key.equals(getString(R.string.key_shellrc_preference))) {
            String value = sharedPreferences.getString(key, null);
            ConsoleStartupScript.write(mSettings.getHomePath(), value);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(key);
            editor.apply();
        }

        if (key.equals(getString(R.string.key_home_path_preference))) {
            String value = sharedPreferences.getString(key, null);
            ConsoleStartupScript.rename(mSettings.getHomePath(), value);
            mSettings.setHomePath(value);
        }

        mSettings.readPrefs(this, sharedPreferences);
        if (mExtraKeys != null) {
            mExtraKeys.applyPreferences(sharedPreferences);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        ThothLog.d(LogCategory.UI, "Term activity created");
        command_collected = false;
        mHandler = new Handler(getMainLooper());

        if (icicle == null)
            onNewIntent(getIntent());

        mSettings = new TermSettings(this);

        mActionBarMode = mSettings.actionBarMode();

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

        mActionBar = TermActionBar.setTermContentView(this,
                mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES);
        mActionBar.setOnTitleClickListener(
                view -> request_choose_window.launch(new Intent(this, WindowListActivity.class)));
        mActionBar.setOnNavigationItemSelectedListener(this::onNavigationItemSelected);

        mViewFlipper = findViewById(R.id.view_flipper);
        mExtraKeys = findViewById(R.id.extra_keys);
        mExtraKeys.setTerminalProvider(this::getCurrentEmulatorView);
        mExtraKeys.applyPreferences(
                PreferenceManager.getDefaultSharedPreferences(this));

        if (!command_collected) {
            CommandCollector.collect(this, () -> {
                command_collected = true;
                populateSessions();
            });
        }

        service_manager.onCreate(this);

        WakeLock.create(this);
        WifiLock.create(this);

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().getConfiguration());

        updatePrefs();
        // Ubuntu V1 is intentionally app-private; shared storage is a separate milestone.
        mAlreadyStarted = true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        service_manager.setOnServiceConnectionListener(Term.this::onServiceConnection);
        service_manager.onStart(this);
    }

    private synchronized void populateSessions() {
        if (mTermService == null) return;
        if (!command_collected) return;

        if (mTermService.getSessionCount() == 0) {
            try {
                mTermService.addSession(createTermSession());
            } catch (IOException e) {
                ScreenMessage.show(getApplicationContext(),
                        "Failed to start terminal session");
                finish();
                return;
            }
        }

        mTermSessions = mTermService.getSessions();
        mTermSessions.addCallback(this);
        mTermSessions.addTitleChangedListener(this);

        populateViewFlipper();
        populateWindowList();
    }

    private void populateViewFlipper() {
        for (TermSession session : mTermSessions) {
            EmulatorView view = createEmulatorView(session);
            mViewFlipper.addView(view);
        }

        updatePrefs();

        if (onResumeSelectWindow >= 0) {
            onResumeSelectWindow = Math.min(onResumeSelectWindow, mViewFlipper.getChildCount() - 1);
            mViewFlipper.setDisplayedChild(onResumeSelectWindow);
            onResumeSelectWindow = -1;
        }
        mViewFlipper.onResume();
    }

    private void populateWindowList() {
        mViewFlipper.addCallback(this);
        synchronizeActionBar();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);

        if (mStopServiceOnFinish)
            service_manager.onDestroy(this);
        mTermService = null;
        WifiLock.release();
        WakeLock.release();
    }

    private TermSession createTermSession() throws IOException {
        return createTermSession(this, null);
    }

    /** Font size captured when the current pinch began; -1 while not pinching. */
    private int zoom_base_size = -1;

    private void onTerminalZoom(float scaleFactor) {
        if (zoom_base_size < 0) zoom_base_size = mSettings.getFontSize();
        applyFontSize(TerminalZoom.scaled(zoom_base_size, scaleFactor));
    }

    /** Ends the pinch so the next one measures from the size now on screen. */
    private void endTerminalZoom() {
        zoom_base_size = -1;
    }

    protected void zoomIn() {
        applyFontSize(TerminalZoom.zoomIn(mSettings.getFontSize()));
        announceFontSize();
    }

    protected void zoomOut() {
        applyFontSize(TerminalZoom.zoomOut(mSettings.getFontSize()));
        announceFontSize();
    }

    protected void zoomReset() {
        applyFontSize(TerminalZoom.DEFAULT_SIZE);
        announceFontSize();
    }

    /**
     * Confirms the resulting size for the menu actions. The pinch gesture does
     * not announce, as it would post a message per motion event.
     */
    private void announceFontSize() {
        ScreenMessage.show(getApplicationContext(),
                getString(R.string.zoom_toast, mSettings.getFontSize()));
    }

    /**
     * Stores the new size in the existing global font-size preference and pushes
     * it through {@link #updatePrefs()}, which re-lays out every session and so
     * resizes each PTY. Writing a string keeps the value readable both by
     * TermSettings and by the Settings list that shares this key.
     */
    private void applyFontSize(int size) {
        int wanted = TerminalZoom.clamp(size);
        if (wanted == mSettings.getFontSize()) return;

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putString("fontsize", Integer.toString(wanted))
                .apply();
        mSettings.readPrefs(this,
                PreferenceManager.getDefaultSharedPreferences(this));
        updatePrefs();
    }

    private TermView createEmulatorView(TermSession session) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        TermView emulatorView = new TermView(this, session, metrics);

        emulatorView.setExtGestureListener(new EmulatorViewGestureListener(emulatorView));
        emulatorView.setZoomListener(new EmulatorView.ZoomListener() {
            @Override
            public void onZoom(float scaleFactor) {
                onTerminalZoom(scaleFactor);
            }

            @Override
            public void onZoomEnd() {
                endTerminalZoom();
            }
        });
        emulatorView.setOnKeyListener(mKeyListener);
        emulatorView.setOnToggleSelectingTextListener(
                () -> mActionBar.lockDrawer(emulatorView.getSelectingText()));
        emulatorView.setOnSelectionActionListener(
                new EmulatorView.OnSelectionActionListener() {
                    @Override
                    public boolean canPaste() {
                        return Term.this.canPaste();
                    }

                    @Override
                    public void onPaste() {
                        Term.this.doPaste();
                    }
                });
        emulatorView.setOnExtraModifierStateChangedListener(mExtraKeys);

        return emulatorView;
    }

    protected TermSession getCurrentTermSession() {
        if (mTermService == null) return null;

        return mTermService.getSession(mViewFlipper.getDisplayedChild());
    }

    protected EmulatorView getCurrentEmulatorView() {
        return (EmulatorView) mViewFlipper.getCurrentView();
    }

    protected void updatePrefs() {
        mUseKeyboardShortcuts = mSettings.getUseKeyboardShortcutsFlag();

        mViewFlipper.updatePrefs(mSettings);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        for (View v : mViewFlipper) {
            ((EmulatorView) v).setDensity(metrics);
            ((TermView) v).updatePrefs(mSettings);
        }

        if (mTermSessions != null) {
            for (TermSession session : mTermSessions) {
                ((GenericTermSession) session).updatePrefs(mSettings);
            }
        }

        {
            int flag = FullScreenCompat.update(this);
            if (flag < 0) {
                // Cannot switch to/from fullscreen after starting activity.
                restart(R.string.restart_statusbar_change);
                return;
            }
            mViewFlipper.setFullScreen(flag > 0);
        }

        if (mActionBarMode != mSettings.actionBarMode()) {
            if (mAlreadyStarted) {
                // Can't switch to new layout after
                // starting the activity.
                restart(R.string.restart_actionbar_change);
                return;
            } else {
                if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES) {
                    mActionBar.hide();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThothLog.d(LogCategory.UI, "Term activity resumed");
    }

    @Override
    public void onPause() {
        super.onPause();

        /* Explicitly close the input method
           Otherwise, the soft keyboard could cover up whatever activity takes
           our place */
        final IBinder token = mViewFlipper.getWindowToken();
        new Thread() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(token, 0);
            }
        }.start();
    }

    @Override
    protected void onStop() {
        ThothLog.d(LogCategory.UI, "Term activity stopped");
        mViewFlipper.onPause();
        if (mTermSessions != null) {
            mTermSessions.removeCallback(this);
            mTermSessions.removeTitleChangedListener(this);
        }

        mViewFlipper.removeCallback(this);
        mViewFlipper.removeAllViews();

        service_manager.onStop(this);

        super.onStop();
    }

    private boolean checkHaveFullHwKeyboard(Configuration c) {
        return (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
                (c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(newConfig);

        EmulatorView v = (EmulatorView) mViewFlipper.getCurrentView();
        if (v != null) {
            v.updateSize(false);
        }

        // Refresh the session label, e.g. after a rotation.
        synchronizeActionBar();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_new_window) {
            doCreateNewWindow();
        } else if (id == R.id.menu_close_window) {
            confirmCloseWindow();
        } else if (id == R.id.menu_zoom_in) {
            zoomIn();
        } else if (id == R.id.menu_zoom_out) {
            zoomOut();
        } else if (id == R.id.menu_zoom_reset) {
            zoomReset();
        } else if (id == R.id.menu_clear_scrollback) {
            doClearScrollback();
            ScreenMessage.show(getApplicationContext(),
                    R.string.clear_scrollback_toast_notification);
        } else if (id == R.id.menu_reset) {
            doResetTerminal();
            ScreenMessage.show(getApplicationContext(),
                    R.string.reset_toast_notification);
        } else if (id == R.id.menu_exit) {
            doExit();
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard();
        } else if (id == R.id.menu_toggle_wakelock) {
            doToggleWakeLock();
        } else if (id == R.id.menu_toggle_wifilock) {
            doToggleWifiLock();
        }
        // Hide the action bar if appropriate
        if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES) {
            mActionBar.hide();
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        /* NOTE: Resource IDs will be non-final in Android Gradle Plugin version 5.0,
           avoid using them in switch case statements */
        if (id == R.id.nav_window_list)
            request_choose_window.launch(new Intent(this, WindowListActivity.class));
        else if (id == R.id.nav_preferences)
            doPreferences();
        else if (id == R.id.nav_action_help)
            doShowAbout();
        else
            return false;
        return true;
    }

    private void doCreateNewWindow() {
        if (mTermService == null) {
            ThothLog.w(LogCategory.UI, "New window requested before service was ready");
            return;
        }

        try {
            TermSession session = createTermSession();

            mTermService.addSession(session);

            TermView view = createEmulatorView(session);
            view.updatePrefs(mSettings);

            mViewFlipper.addView(view);
            mViewFlipper.setDisplayedChild(mViewFlipper.getChildCount() - 1);
        } catch (IOException e) {
            ScreenMessage.show(getApplicationContext(),
                    "Failed to create a session");
        }
    }

    private void confirmCloseWindow() {
        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.confirm_window_close_message);
        b.setPositiveButton(android.R.string.ok, (dialog, id) -> {
            dialog.dismiss();
            mHandler.post(this::doCloseWindow);
        });
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    private void doCloseWindow() {
        if (mTermService == null) return;

        EmulatorView view = getCurrentEmulatorView();
        if (view == null) return;

        view.onPause();
        mViewFlipper.removeView(view);
        TermSession session = view.getTermSession();
        if (session != null) session.finish();

        if (mTermService.getSessionCount() > 0)
            mViewFlipper.showNext();
    }

    private void onRequestChooseWindow(int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            int position = data.getIntExtra(Application.ARGUMENT_WINDOW_ID, -2);
            if (position >= 0) {
                // Switch windows after session list is in sync, not here
                onResumeSelectWindow = position;
            } else if (position == -1) {
                // NOTE do not create new windows (view) here as launch of a
                // activity cleans indirectly view flipper - see method onStop.
                // Create only new session and then on service connection view
                // flipper and etc. will be updated...
                //doCreateNewWindow();
                if (mTermService != null) {
                    try {
                        TermSession session = createTermSession();
                        mTermService.addSession(session);
                        onResumeSelectWindow = mTermService.getSessionCount() - 1;
                    } catch (IOException e) {
                        ScreenMessage.show(this.getApplicationContext(),
                                "Failed to create a session");
                        onResumeSelectWindow = -1;
                    }
                } else
                    onResumeSelectWindow = -1;
            }
        } else {
            // Close the activity if user closed all sessions
            // TODO the left path will be invoked when nothing happened, but this Activity was destroyed!
            if (mTermService == null || mTermService.getSessionCount() == 0) {
                mStopServiceOnFinish = true;
                finish();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); // make static code analysis tool happy
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            // Don't repeat action if intent comes from history
            return;
        }

        ComponentName component = intent.getComponent();
        if (component == null ||
                !Application.ID.equals(component.getPackageName())) {
            /* not from application */
            return;
        }

        String action = intent.getAction();
        if (TextUtils.isEmpty(action)) {
            // TODO: always define action?
            return;
        }

        // huge number simply opens new window
        // TODO: add a way to restrict max number of windows per caller (possibly via reusing BoundSession)
        switch (action) {
            case WINDOW_ACTION_NEW:
                onResumeSelectWindow = Integer.MAX_VALUE;
                break;
            case WINDOW_ACTION_SWITCH:
                int target = intent.getIntExtra(Application.ARGUMENT_TARGET_WINDOW, -1);
                if (target >= 0) {
                    onResumeSelectWindow = target;
                }
                break;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem wakeLockItem = menu.findItem(R.id.menu_toggle_wakelock);
        MenuItem wifiLockItem = menu.findItem(R.id.menu_toggle_wifilock);
        if (WakeLock.isHeld()) {
            wakeLockItem.setTitle(R.string.disable_wakelock);
        } else {
            wakeLockItem.setTitle(R.string.enable_wakelock);
        }
        if (WifiLock.isHeld()) {
            wifiLockItem.setTitle(R.string.disable_wifilock);
        } else {
            wifiLockItem.setTitle(R.string.enable_wifilock);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                EmulatorView currentView = getCurrentEmulatorView();
                if (currentView != null && currentView.getSelectingText()) {
                    currentView.finishSelectingText();
                    return true;
                }
                if (mActionBarMode == TermSettings.ACTION_BAR_MODE_HIDES && mActionBar.isShowing()) {
                    mActionBar.hide();
                    return true;
                }
                switch (mSettings.getBackKeyAction()) {
                    case TermSettings.BACK_KEY_STOPS_SERVICE:
                        mStopServiceOnFinish = true;
                    case TermSettings.BACK_KEY_CLOSES_ACTIVITY:
                        finish();
                        return true;
                    case TermSettings.BACK_KEY_CLOSES_WINDOW:
                        doCloseWindow();
                        return true;
                    default:
                        return false;
                }
            case KeyEvent.KEYCODE_MENU:
                if (!mActionBar.isShowing()) {
                    mActionBar.show();
                    return true;
                } else {
                    return super.onKeyUp(keyCode, event);
                }
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    // Called when the list of sessions changes
    public void onUpdate() {
        if (mTermService == null) return;

        if (mTermService.getSessionCount() == 0) {
            mStopServiceOnFinish = true;
            finish();
            return;
        }

        SessionList sessions = mTermService.getSessions();
        if (sessions.size() < mViewFlipper.getChildCount()) {
            for (int i = 0; i < mViewFlipper.getChildCount(); ++i) {
                EmulatorView v = (EmulatorView) mViewFlipper.getChildAt(i);
                if (!sessions.contains(v.getTermSession())) {
                    v.onPause();
                    mViewFlipper.removeView(v);
                    --i;
                }
            }
        }

        synchronizeActionBar();
    }

    protected boolean canPaste() {
        return canPaste(new SimpleClipboardManager(this));
    }

    private boolean canPaste(SimpleClipboardManager clip) {
        return clip.hasText();
    }

    private void doPreferences() {
        startActivity(new Intent(this, TermPreferencesActivity.class));
    }

    private void doResetTerminal() {
        TermSession session = getCurrentTermSession();
        if (session == null) return;

        session.reset();
    }

    /** How long the shells get to honour SIGHUP before Exit stops waiting. */
    private static final long EXIT_GRACE_MS = 400;

    private void doExit() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_title)
                .setMessage(R.string.exit_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.exit, (dialog, which) -> performExit())
                .show();
    }

    /**
     * Shut the application down as far as an ordinary app is allowed to.
     * <p>
     * Android reserves Settings' "Force stop" for the system, so this does the
     * strongest correct equivalent: hang up every session, let the PRoot trees
     * unwind, then kill whatever is left of the process groups we started --
     * by the pids the service tracks, never by matching process names. The
     * activity task goes with it, and we end our own process last so that the
     * service, its notification and its threads go down with us.
     * <p>
     * Nothing on disk is touched: the rootfs, /home/thoth and the preferences
     * are all still there for the next launch.
     */
    private void performExit() {
        ThothLog.i(LogCategory.APP, "Exit requested; shutting down all sessions");

        WifiLock.release();
        WakeLock.release();

        final int[] pids;
        if (mTermService != null) {
            pids = mTermService.sessionProcessIds();
            mTermService.shutdownAll();
        } else {
            pids = new int[0];
        }

        // Let onStop()/onDestroy() unbind and stop the service on the way out.
        mStopServiceOnFinish = true;
        finishAffinity();
        // finishAndRemoveTask() only drops the task when it is called on the
        // task root, and ours is the setup activity, so ask for the app's
        // tasks by hand instead -- otherwise a dead card is left in Recents.
        ActivityManager activityManager = getSystemService(ActivityManager.class);
        if (activityManager != null) {
            for (ActivityManager.AppTask task : activityManager.getAppTasks()) {
                task.finishAndRemoveTask();
            }
        }

        mHandler.postDelayed(() -> {
            for (int pid : pids) {
                com.thothterm.Process.killChilds(pid);
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        }, EXIT_GRACE_MS);
    }

    /** Drop the current window's scrollback without disturbing its shell. */
    private void doClearScrollback() {
        TermSession session = getCurrentTermSession();
        if (session == null) return;

        session.clearScrollback();
    }

    private void doShowAbout() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(R.string.about_title);
        b.setMessage(getString(R.string.application_positioning)
                + "\n\n" + getString(R.string.about_version, Application.VER));
        b.setPositiveButton(R.string.about_site,
                (dialog, id) -> WrapOpenURL.launch(Term.this, R.string.help_url));
        b.setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    protected void doCopyAll() {
        TermSession session = getCurrentTermSession();
        if (session == null) return;

        SimpleClipboardManager clip = new SimpleClipboardManager(this);
        clip.setText(session.getTranscriptText().trim());
    }

    protected void doPaste() {
        TermSession session = getCurrentTermSession();
        if (session == null) return;

        SimpleClipboardManager clip = new SimpleClipboardManager(this);
        if (!canPaste(clip)) return;

        CharSequence paste = clip.getText();
        if (TextUtils.isEmpty(paste)) return;

        session.write(paste.toString());
    }

    private void doToggleSoftKeyboard() {
        SoftInputCompat.toggle(getCurrentEmulatorView());
    }

    private void doToggleSoftKeyboard(View view) {
        SoftInputCompat.toggle(view);
    }

    private void doToggleWakeLock() {
        WakeLock.toggle(this);
        invalidateOptionsMenu();
    }

    private void doToggleWifiLock() {
        WifiLock.toggle(this);
        invalidateOptionsMenu();
    }

    private void doToggleUI(MotionEvent e, EmulatorView view) {
        int y = (int) e.getY();
        int height = view.getVisibleHeight();

        switch (mActionBarMode) {
            case TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE:
                if (!mHaveFullHwKeyboard) {
                    doToggleSoftKeyboard(view);
                }
                break;
            case TermSettings.ACTION_BAR_MODE_HIDES:
                if (mHaveFullHwKeyboard || y < height / 2) {
                    mActionBar.doToggleActionBar();
                } else {
                    doToggleSoftKeyboard(view);
                }
                break;
        }
        view.requestFocus();
    }

    private void synchronizeActionBar() {
        if (mActionBar == null || mViewFlipper == null) return;

        int position = mViewFlipper.getDisplayedChild();
        if (position != mLastDisplayedSession) {
            mLastDisplayedSession = position;
            ThothLog.d(LogCategory.SESSION, "Session switched index=" + position);
        }
        TermSession session = getCurrentTermSession();
        CharSequence title = (session != null) ? session.getTitle() : null;
        if (TextUtils.isEmpty(title))
            title = getString(R.string.window_title, position + 1);
        mActionBar.setSessionTitle(title);
        synchronizeExtraKeys(session);
    }

    private void synchronizeExtraKeys(TermSession session) {
        if (mExtraKeys == null || session == mExtraKeysSession) return;
        for (View view : mViewFlipper) {
            ((EmulatorView) view).clearExtraModifiers();
        }
        mExtraKeysSession = session;
        mExtraKeys.resetModifiers();
    }

    private static class FullScreenCompat {
        private static int update(Term activity) {
            return Compat1.update(activity);
        }

        private static class Compat1 {
            private final static int FULLSCREEN = WindowManager.LayoutParams.FLAG_FULLSCREEN;

            private static int update(Term activity) {
                Window win = activity.getWindow();
                WindowManager.LayoutParams params = win.getAttributes();
                int desired = activity.mSettings.showStatusBar() ? 0 : FULLSCREEN;
                if (desired != (params.flags & FULLSCREEN)) {
                    if (activity.mAlreadyStarted) return -1;
                    win.setFlags(desired, FULLSCREEN);
                }
                return (params.flags & FULLSCREEN) != 0 ? 1 : 0;
            }
        }
    }

    private class EmulatorViewGestureListener extends SimpleOnGestureListener {
        private final EmulatorView view;

        public EmulatorViewGestureListener(EmulatorView view) {
            this.view = view;
        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            // Let the EmulatorView handle taps if mouse tracking is active
            if (view.isMouseTrackingActive()) return false;

            //Check for link at tap location
            String link = view.getURLat(e.getX(), e.getY());
            if (link != null) {
                WrapOpenURL.launch(Term.this, link);
                return true;
            }

            doToggleUI(e, view);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            float absVelocityX = Math.abs(velocityX);
            float absVelocityY = Math.abs(velocityY);
            if (absVelocityX > Math.max(1000.0f, 2.0 * absVelocityY)) {
                // Assume user wanted side to side movement
                if (velocityX > 0) {
                    // Left to right swipe -- previous window
                    mViewFlipper.showPrevious();
                } else {
                    // Right to left swipe -- next window
                    mViewFlipper.showNext();
                }
                return true;
            } else {
                return false;
            }
        }
    }
}
