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

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.Window;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.util.Accounts;

/**
 * Application entry point, displaying events thanks to {@link EventsFragment}.
 * @author Pixmob
 */
public class EventsActivity extends FragmentActivity implements EventsFragment.Listener {
    private static final int NO_ACCOUNT_AVAILABLE = 1;
    
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
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.nav_background));
    }
    
    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof EventsFragment) {
            final EventsFragment ef = (EventsFragment) fragment;
            ef.setListener(this);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check if there are any Google accounts on this device.
        final Account[] accounts = Accounts.list(this);
        if (accounts.length == 0) {
            showDialog(NO_ACCOUNT_AVAILABLE);
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        if (NO_ACCOUNT_AVAILABLE == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.error).setIcon(
                R.drawable.ic_dialog_alert).setMessage(R.string.no_account_available)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
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
    
    @Override
    public void onEventSelected(String eventId) {
        // TODO Start event details activity.
    }
    
    @Override
    public void onSynchronizationStart() {
        setProgressBarIndeterminateVisibility(Boolean.TRUE);
    }
    
    @Override
    public void onSynchronizationStop() {
        setProgressBarIndeterminateVisibility(Boolean.FALSE);
    }
}
