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
package org.pixmob.droidlink.service;

import static org.pixmob.droidlink.Constants.ACTION_NEW_EVENT;
import static org.pixmob.droidlink.Constants.EXTRA_EVENT_COUNT;
import static org.pixmob.droidlink.Constants.EXTRA_EVENT_ID;
import static org.pixmob.droidlink.Constants.NEW_EVENT_NOTIFICATION;
import static org.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static org.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static org.pixmob.droidlink.Constants.SP_KEY_EVENT_LIST_VISIBLE;
import static org.pixmob.droidlink.Constants.SP_KEY_UNREAD_EVENT_COUNT;
import static org.pixmob.droidlink.Constants.TAG;

import org.pixmob.actionservice.ActionExecutionFailedException;
import org.pixmob.actionservice.ActionService;
import org.pixmob.appengine.client.R;
import org.pixmob.droidlink.feature.Features;
import org.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import org.pixmob.droidlink.provider.EventsContract;
import org.pixmob.droidlink.ui.EventDetailsActivity;
import org.pixmob.droidlink.ui.EventsActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

/**
 * Update the notification bar when event synchronization ends.
 * @author Pixmob
 */
public class SyncNotificationService extends ActionService {
    private NotificationManager notificationManager;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    private PendingIntent openMainActivity;
    
    public SyncNotificationService() {
        super("DroidLink/SyncNotification");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        prefsEditor = prefs.edit();
        
        openMainActivity = PendingIntent.getActivity(this, 0,
            new Intent(this, EventsActivity.class), PendingIntent.FLAG_CANCEL_CURRENT);
    }
    
    @Override
    protected void onHandleAction(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        if (ACTION_NEW_EVENT.equals(intent.getAction())
                && !prefs.getBoolean(SP_KEY_EVENT_LIST_VISIBLE, false)) {
            final int eventCount = intent.getIntExtra(EXTRA_EVENT_COUNT, 0);
            final String eventId = intent.getStringExtra(EXTRA_EVENT_ID);
            
            if (eventCount == 1 && eventId == null) {
                Log.wtf(TAG, "Missing event identifier: cannot add notification");
            } else {
                handleNewEvent(eventCount, eventId);
            }
        }
    }
    
    private void handleNewEvent(int eventCount, String eventId) {
        final int unreadEventCount = prefs.getInt(SP_KEY_UNREAD_EVENT_COUNT, 0) + eventCount;
        prefsEditor.putInt(SP_KEY_UNREAD_EVENT_COUNT, unreadEventCount);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        
        final PendingIntent pi;
        if (unreadEventCount > 1) {
            pi = openMainActivity;
            Log.i(TAG, "Add event notification for " + unreadEventCount + " events");
        } else {
            final Uri eventUri = Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId);
            pi = PendingIntent.getActivity(this, 0, new Intent(this, EventDetailsActivity.class)
                    .setData(eventUri), PendingIntent.FLAG_CANCEL_CURRENT);
            Log.i(TAG, "Add event notification for a single event");
        }
        
        final String account = prefs.getString(SP_KEY_ACCOUNT, null);
        final Notification n = createNotification(this, account, unreadEventCount, pi);
        notificationManager.notify(NEW_EVENT_NOTIFICATION, n);
    }
    
    private static Notification createNotification(Context context, String account,
            int unreadEventCount, PendingIntent pi) {
        final Notification n = new Notification(R.drawable.stat_notify_new_event, context
                .getString(R.string.received_new_event), System.currentTimeMillis());
        n.defaults = Notification.DEFAULT_ALL;
        n.setLatestEventInfo(context.getApplicationContext(), context
                .getString(R.string.received_new_event), account, pi);
        n.number = unreadEventCount;
        return n;
    }
}
