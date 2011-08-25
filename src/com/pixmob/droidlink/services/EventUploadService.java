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
import static com.pixmob.droidlink.Constants.SERVER_HOST;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.Constants.USER_AGENT;
import static com.pixmob.droidlink.providers.EventsContentProvider.CONTENT_URI;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_DATE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_DEVICE_ID;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NAME;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NUMBER;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_ID;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_MESSAGE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_TYPE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_UPLOADED;

import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;
import android.util.SparseArray;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.appengine.client.AppEngineClient;
import com.pixmob.droidlink.util.HttpUtils;

/**
 * Upload events to the server.
 * @author Pixmob
 */
public class EventUploadService extends AbstractNetworkService {
    private SharedPreferences prefs;
    
    public EventUploadService() {
        super("EventUpload");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
    }
    
    @Override
    protected void onHandleActionInternal(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        final String accountName = prefs.getString(SP_KEY_ACCOUNT, null);
        if (accountName == null) {
            Log.w(TAG, "No account is selected: cannot upload events");
            return;
        }
        final String deviceId = prefs.getString(SP_KEY_DEVICE_ID, null);
        if (deviceId == null) {
            Log.w(TAG, "No device id set: cannot upload events");
            return;
        }
        
        final AppEngineClient client = new AppEngineClient(this, SERVER_HOST);
        client.setAccount(accountName);
        client.setHttpUserAgent(USER_AGENT);
        
        try {
            uploadEvents(client, deviceId);
        } catch (Exception e) {
            throw new ActionExecutionFailedException("Event upload error", e);
        } finally {
            client.close();
        }
    }
    
    private void uploadEvents(AppEngineClient client, String deviceId) throws Exception {
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
                    jsonEvent.put("type", eventType).put("date", eventDate)
                            .put("number", eventNumber).put("name", eventName)
                            .put("message", eventMessage);
                    jsonEvents.append(eventId, jsonEvent);
                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }
        
        if (eventCount == 0) {
            Log.i(TAG, "No event to upload");
        } else {
            final HttpPut req = new HttpPut();
            HttpUtils.prepareJsonRequest(req);
            HttpResponse resp;
            int statusCode = 500;
            
            for (int i = 0; i < eventCount; ++i) {
                final int eventId = jsonEvents.keyAt(i);
                final JSONObject jsonEvent = jsonEvents.get(eventId);
                
                req.setURI(URI.create(HttpUtils.createServiceUri("/device/" + deviceId + "/"
                        + eventId)));
                req.setEntity(new StringEntity(jsonEvent.toString()));
                
                if (DEVELOPER_MODE) {
                    Log.i(TAG, "Sending event " + eventId + " to " + req.getURI());
                }
                
                // Send the request.
                for (int remainingRetries = 3; remainingRetries != 0; --remainingRetries) {
                    try {
                        resp = client.execute(req);
                        statusCode = resp.getStatusLine().getStatusCode();
                        break;
                    } catch (ConnectTimeoutException e) {
                        Log.w(TAG, "Event " + eventId + " upload failed: retrying", e);
                    }
                }
                
                if (HttpUtils.isStatusOK(statusCode)) {
                    // Update event upload status.
                    final ContentValues cv = new ContentValues(1);
                    cv.put(KEY_UPLOADED, 1);
                    
                    final int eventsUpdated = getContentResolver().update(CONTENT_URI, cv,
                        KEY_ID + "=? and " + KEY_DEVICE_ID + "=?",
                        new String[] { String.valueOf(eventId), deviceId });
                    if (eventsUpdated != 1) {
                        Log.w(TAG, "Failed to update upload status for event " + eventId);
                    } else {
                        Log.i(TAG, "Event " + eventId + ": upload successful");
                    }
                } else {
                    Log.w(TAG, "Failed to upload event " + eventId + ": error " + statusCode);
                }
            }
        }
    }
}
