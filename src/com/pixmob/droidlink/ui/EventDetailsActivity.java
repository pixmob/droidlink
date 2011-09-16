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

import static com.pixmob.droidlink.Constants.TAG;
import android.app.NotificationManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.pixmob.droidlink.R;

/**
 * Display event details thanks to {@link EventsFragment}.
 * @author Pixmob
 */
public class EventDetailsActivity extends FragmentActivity {
    private NotificationManager notificationManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.event_details);
        
        // Customize action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setCustomView(R.layout.nav);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setBackgroundDrawable(getResources().getDrawable(R.drawable.nav_background));
        
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Enable dithering, ie better gradients.
        getWindow().setFormat(PixelFormat.RGBA_8888);
    }
    
    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof EventDetailsFragment) {
            final Uri eventUri = getIntent().getData();
            if (eventUri == null) {
                Log.e(TAG, "Missing event URI");
            } else {
                final EventDetailsFragment details = (EventDetailsFragment) fragment;
                details.setEvent(eventUri);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        notificationManager.cancel(R.string.received_new_event);
    }
}
