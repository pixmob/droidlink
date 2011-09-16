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
package com.pixmob.droidlink.service;

import static com.pixmob.droidlink.Constants.ACTION_NEW_EVENT;
import static com.pixmob.droidlink.Constants.EXTRA_EVENT_COUNT;
import static com.pixmob.droidlink.Constants.EXTRA_EVENT_ID;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_EVENT_LIST_VISIBLE;
import static com.pixmob.droidlink.Constants.TAG;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.actionservice.ActionService;
import com.pixmob.droidlink.R;
import com.pixmob.droidlink.provider.EventsContract;
import com.pixmob.droidlink.ui.EventDetailsActivity;
import com.pixmob.droidlink.ui.EventsActivity;

/**
 * Update the notification bar when event synchronization ends.
 * @author Pixmob
 */
public class SyncNotificationService extends ActionService {
    private NotificationManager notificationManager;
    private SharedPreferences prefs;
    private PendingIntent openMainActivity;
    
    public SyncNotificationService() {
        super("DroidLink/SyncNotification");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        
        openMainActivity = PendingIntent.getActivity(this, 0,
            new Intent(this, EventsActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);
    }
    
    @Override
    protected void onHandleAction(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        if (ACTION_NEW_EVENT.equals(intent.getAction())
                && prefs.getBoolean(SP_KEY_EVENT_LIST_VISIBLE, false)) {
            final int eventCount = intent.getIntExtra(EXTRA_EVENT_COUNT, 0);
            final String eventId = intent.getStringExtra(EXTRA_EVENT_ID);
            
            if (eventCount == 1 && eventId != null) {
                Log.wtf(TAG, "Missing event identifier: cannot add notification");
            } else {
                handleNewEvent(eventCount, eventId);
            }
        }
    }
    
    private void handleNewEvent(int eventCount, String eventId) {
        final PendingIntent pi;
        if (eventCount > 1) {
            pi = openMainActivity;
        } else {
            final Uri eventUri = Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId);
            pi = PendingIntent.getActivity(this, 0, new Intent(this, EventDetailsActivity.class)
                    .setData(eventUri), PendingIntent.FLAG_CANCEL_CURRENT);
        }
        
        final Notification n = new Notification(R.drawable.stat_notify_new_event,
                getString(R.string.received_new_event), System.currentTimeMillis());
        n.setLatestEventInfo(this, getString(R.string.app_name),
            getString(R.string.received_new_event), pi);
        notificationManager.notify(R.string.received_new_event, n);
    }
}
