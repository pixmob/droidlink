/*
 * Copyright (C) 2011 Pixmob (http://github.com/pixmob)
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
package com.pixmob.droidlink.ui;

import static com.pixmob.droidlink.Constants.ACTION_SYNC;
import static com.pixmob.droidlink.Constants.EXTRA_RUNNING;
import static com.pixmob.droidlink.Constants.NEW_EVENT_NOTIFICATION;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_EVENT_LIST_VISIBLE;
import static com.pixmob.droidlink.Constants.SP_KEY_UNREAD_EVENT_COUNT;
import android.accounts.Account;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.Window;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.util.Accounts;

/**
 * Application entry point, displaying events thanks to {@link EventsFragment}.
 * @author Pixmob
 */
public class EventsActivity extends FragmentActivity implements OnEventSelectionListener {
    private static final int NO_ACCOUNT_AVAILABLE = 1;
    private NotificationManager notificationManager;
    private SyncActionReceiver syncActionReceiver;
    private SharedPreferences.Editor prefsEditor;
    
    @Override
    public void onEventSelected(Uri eventUri) {
        final EventDetailsFragment df = (EventDetailsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.event_details);
        if (df == null) {
            startActivity(new Intent(this, EventDetailsActivity.class).setData(eventUri));
        } else {
            df.setEvent(eventUri);
        }
    }
    
    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof EventsFragment) {
            final EventsFragment ef = (EventsFragment) fragment;
            ef.setOnEventSelectionListener(this);
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(android.view.Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.events);
        
        // A spinner is displayed when events are synchronizing.
        setProgressBarIndeterminateVisibility(Boolean.FALSE);
        setProgressBarVisibility(false);
        
        // Customize action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setCustomView(R.layout.nav);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.nav_background));
        
        syncActionReceiver = new SyncActionReceiver();
        
        prefsEditor = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE).edit();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        final Intent lastSyncIntent = registerReceiver(syncActionReceiver, new IntentFilter(
                ACTION_SYNC));
        if (lastSyncIntent != null) {
            onSyncAction(lastSyncIntent);
        }
        
        // Check if there are any Google accounts on this device.
        final Account[] accounts = Accounts.list(this);
        if (accounts.length == 0) {
            showDialog(NO_ACCOUNT_AVAILABLE);
        }
        
        prefsEditor.putBoolean(SP_KEY_EVENT_LIST_VISIBLE, true);
        prefsEditor.remove(SP_KEY_UNREAD_EVENT_COUNT);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        
        notificationManager.cancel(NEW_EVENT_NOTIFICATION);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(syncActionReceiver);
        
        prefsEditor.putBoolean(SP_KEY_EVENT_LIST_VISIBLE, false);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        if (NO_ACCOUNT_AVAILABLE == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.error).setIcon(
                R.drawable.ic_dialog_alert).setCancelable(false).setMessage(
                R.string.no_account_available).setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // There is no Google account on this device:
                        // the application cannot run.
                        finish();
                    }
                }).create();
        }
        
        return super.onCreateDialog(id);
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Enable dithering, ie better gradients.
        getWindow().setFormat(PixelFormat.RGBA_8888);
    }
    
    private void onSyncAction(Intent intent) {
        if (ACTION_SYNC.equals(intent.getAction())) {
            final boolean running = intent.getBooleanExtra(EXTRA_RUNNING, false);
            setProgressBarIndeterminateVisibility(Boolean.valueOf(running));
        }
    }
    
    private class SyncActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            onSyncAction(intent);
        }
    }
}
