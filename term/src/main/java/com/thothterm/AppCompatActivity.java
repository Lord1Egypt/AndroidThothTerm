/*
 * Copyright (C) 2019-2023 Roumen Petrov.  All rights reserved.
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

package com.thothterm;

import android.content.pm.PackageManager;

import com.thothterm.compat.PackageManagerCompat;
import com.thothterm.utils.ThemeManager;
import com.thothterm.widget.ScreenMessage;


public class AppCompatActivity extends androidx.appcompat.app.AppCompatActivity {
    private Integer theme_resid;

    private int getComponentTheme() throws PackageManager.NameNotFoundException {
        return PackageManagerCompat.getActivityInfo(getPackageManager(), getComponentName()).theme;
    }

    @Override
    public void setTheme(int resid) {
        boolean actionbar = false;
        try {
            if (R.style.AppTheme == getComponentTheme())
                actionbar = true;
        } catch (Exception ignore) {
        }
        theme_resid = ThemeManager.presetTheme(this, actionbar, resid);
        super.setTheme(theme_resid);
    }

    protected final Integer getThemeId() {
        return theme_resid;
    }

    protected void restart(int rid) {
        if (rid != 0)
            ScreenMessage.show(getApplicationContext(), rid);

        /* Let use function from API level 11
        Intent intent = Intent.makeRestartActivityTask(getComponentName());
        startActivity(intent);
        finish();
        */
        recreate();
    }
}
