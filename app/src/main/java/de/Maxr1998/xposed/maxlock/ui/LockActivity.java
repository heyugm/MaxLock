/*
 * MaxLock, an Xposed applock module for Android
 * Copyright (C) 2014-2015  Maxr1998
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.Maxr1998.xposed.maxlock.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.analytics.GoogleAnalytics;

import de.Maxr1998.xposed.maxlock.AuthenticationSucceededListener;
import de.Maxr1998.xposed.maxlock.Common;
import de.Maxr1998.xposed.maxlock.R;
import de.Maxr1998.xposed.maxlock.Util;

public class LockActivity extends FragmentActivity implements AuthenticationSucceededListener {

    private String requestPkg;
    private ActivityManager am;
    private Intent app;
    private SharedPreferences prefsPackages;
    private boolean isInFocus = false, unlocked = false;

    @SuppressLint("WorldReadableFiles")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Util.cleanUp(this);
        // Preferences
        //noinspection deprecation
        prefsPackages = getSharedPreferences(Common.PREFS_PACKAGES, MODE_WORLD_READABLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        requestPkg = getIntent().getStringExtra(Common.INTENT_EXTRAS_PKG_NAME);
        app = getIntent().getParcelableExtra(Common.INTENT_EXTRAS_INTENT);

        am = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
        am.killBackgroundProcesses("de.Maxr1998.xposed.maxlock");

        Long timestamp = System.currentTimeMillis();
        Long permitTimestamp = prefsPackages.getLong(requestPkg + "_tmp", 0);
        if (permitTimestamp != 0 && timestamp - permitTimestamp <= 10000) {
            onAuthenticationSucceeded();
        } else {
            authenticate();
        }
        ((ThisApplication) getApplication()).getTracker(ThisApplication.TrackerName.APP_TRACKER);
    }

    private void authenticate() {
        Fragment frag = new LockFragment();
        Bundle b = new Bundle(1);
        b.putString(Common.INTENT_EXTRAS_PKG_NAME, requestPkg);
        frag.setArguments(b);
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_container, frag).commit();
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onAuthenticationSucceeded() {
        unlocked = true;
        prefsPackages.edit()
                .putLong(requestPkg + "_tmp", System.currentTimeMillis())
                .commit();
        am.killBackgroundProcesses("de.Maxr1998.xposed.maxlock");
        try {
            Intent intent = new Intent(app);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } catch (Exception e) {
            Intent intent_option = getPackageManager().getLaunchIntentForPackage(requestPkg);
            intent_option.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent_option);
        } finally {
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        isInFocus = hasFocus;
    }

    @Override
    protected void onStart() {
        super.onStart();
        GoogleAnalytics.getInstance(this).reportActivityStart(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        GoogleAnalytics.getInstance(this).reportActivityStop(this);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Common.ENABLE_PRO, false) &&
                PreferenceManager.getDefaultSharedPreferences(this).getBoolean(Common.ENABLE_LOGGING, false) && !unlocked) {
            Util.logFailedAuthentication(this, requestPkg);
        }
        if (!isInFocus) {
            Log.d("MaxLock/LockActivity", "Lost focus, finishing.");
            finish();
        }
    }
}