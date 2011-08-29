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
package com.pixmob.droidlink.services;

import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.providers.EventsContentProvider.CONTENT_URI;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_DATE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_DEVICE_ID;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NAME;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NUMBER;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_ID;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_MESSAGE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_TYPE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_UPLOADED;

import java.io.IOException;

import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.util.SparseArray;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.droidlink.R;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.ui.EventsActivity;

/**
 * Upload events to the server.
 * @author Pixmob
 */
public class EventUploadService extends AbstractNetworkService {
    private PendingIntent openMainActivity;
    
    public EventUploadService() {
        super("EventUpload");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        openMainActivity = PendingIntent.getActivity(this, 0,
            new Intent(this, EventsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
    }
    
    @Override
    protected void onHandleActionInternal(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        final NetworkClient client = NetworkClient.newInstance(this);
        if (client == null) {
            throw new ActionExecutionFailedException("Failed to create NetworkClient");
        }
        
        final Notification n = new Notification(android.R.drawable.stat_sys_upload,
                getString(R.string.event_upload_running), System.currentTimeMillis());
        n.setLatestEventInfo(this, getString(R.string.app_name),
            getString(R.string.event_upload_running), openMainActivity);
        startForeground(R.string.event_upload_running, n);
        
        try {
            uploadEvents(client);
        } catch (ActionExecutionFailedException e) {
            showErrorNotification();
            throw e;
        } catch (Exception e) {
            showErrorNotification();
            throw new ActionExecutionFailedException("Event upload error", e);
        } finally {
            stopForeground(true);
            client.close();
        }
    }
    
    private void uploadEvents(NetworkClient client) throws Exception {
        // Get events to upload.
        final String[] eventToUploadColumns = { KEY_ID, KEY_TYPE, KEY_DATE, KEY_FROM_NUMBER,
                KEY_FROM_NAME, KEY_MESSAGE };
        final Cursor c = getContentResolver().query(CONTENT_URI, eventToUploadColumns,
            KEY_UPLOADED + "=?", new String[] { "0" }, null);
        final int eventCount = c.getCount();
        final SparseArray<JSONObject> jsonEvents = new SparseArray<JSONObject>(eventCount);
        try {
            if (c.moveToFirst()) {
                Log.d(TAG, "Found " + eventCount + " event(s) to upload");
                
                do {
                    final int eventId = c.getInt(c.getColumnIndex(KEY_ID));
                    final int eventType = c.getInt(c.getColumnIndex(KEY_TYPE));
                    final long eventDate = c.getLong(c.getColumnIndex(KEY_DATE));
                    final String eventNumber = c.getString(c.getColumnIndex(KEY_FROM_NUMBER));
                    final String eventName = c.getString(c.getColumnIndex(KEY_FROM_NAME));
                    final String eventMessage = c.getString(c.getColumnIndex(KEY_MESSAGE));
                    
                    final JSONObject jsonEvent = new JSONObject();
                    jsonEvent.put("type", eventType).put("date", eventDate).put("number",
                        eventNumber).put("name", eventName).put("message", eventMessage);
                    jsonEvents.append(eventId, jsonEvent);
                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        
        if (eventCount == 0) {
            Log.i(TAG, "No event to upload");
        } else {
            final String[] updateArgs = new String[2];
            
            for (int i = 0; i < eventCount; ++i) {
                final int eventId = jsonEvents.keyAt(i);
                final JSONObject jsonEvent = jsonEvents.get(eventId);
                
                if (DEVELOPER_MODE) {
                    Log.i(TAG, "Sending event " + eventId);
                }
                try {
                    client.put("/device/" + client.getDeviceId() + "/" + eventId, jsonEvent);
                } catch (AppEngineAuthenticationException e) {
                    throw new ActionExecutionFailedException(
                            "Authentication failed: cannot upload events", e);
                } catch (IOException e) {
                    throw new ActionExecutionFailedException("I/O error: cannot upload events", e);
                }
                
                // Update event upload status.
                final ContentValues cv = new ContentValues(1);
                cv.put(KEY_UPLOADED, 1);
                
                updateArgs[0] = String.valueOf(eventId);
                updateArgs[1] = client.getDeviceId();
                final int eventsUpdated = getContentResolver().update(CONTENT_URI, cv,
                    KEY_ID + "=? and " + KEY_DEVICE_ID + "=?", updateArgs);
                if (eventsUpdated != 1) {
                    Log.w(TAG, "Failed to update upload status for event " + eventId);
                } else {
                    Log.i(TAG, "Event " + eventId + ": upload successful");
                }
            }
        }
    }
    
    private void showErrorNotification() {
        final Notification nError = new Notification(android.R.drawable.stat_sys_warning,
                getString(R.string.event_upload_error), System.currentTimeMillis());
        nError.setLatestEventInfo(this, getString(R.string.app_name),
            getString(R.string.event_upload_error), openMainActivity);
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(R.string.event_upload_error, nError);
    }
}
