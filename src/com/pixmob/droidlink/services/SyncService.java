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
import static com.pixmob.droidlink.Constants.REMOTE_API_VERSION;
import static com.pixmob.droidlink.Constants.SERVER_HOST;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.SP_KEY_SYNC_REQUIRED;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.Constants.USER_AGENT;
import static com.pixmob.droidlink.providers.EventsContentProvider.CONTENT_URI;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_DATE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NAME;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NUMBER;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_ID;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_MESSAGE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_TYPE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_UPLOAD_TIME;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.actionservice.ActionService;
import com.pixmob.appengine.client.AppEngineClient;
import com.pixmob.droidlink.features.Features;
import com.pixmob.droidlink.features.SharedPreferencesSaverFeature;

/**
 * Synchronize with the server.
 * @author Pixmob
 */
public class SyncService extends ActionService {
    private static final int REQUEST_OK = 200;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    
    public SyncService() {
        super("Sync");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        prefsEditor = prefs.edit();
    }
    
    @Override
    protected void onHandleAction(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        if (!isReadyToSync()) {
            Log.i(TAG, "Device is not ready for synchronization: try later");
            return;
        }
        
        final String account = prefs.getString(SP_KEY_ACCOUNT, null);
        if (account == null) {
            Log.w(TAG, "No account was selected: cannot synchronize");
            return;
        }
        
        final AppEngineClient client = new AppEngineClient(this, SERVER_HOST);
        client.setAccount(account);
        client.setHttpUserAgent(USER_AGENT);
        
        try {
            doSync(client);
        } catch (Exception e) {
            throw new ActionExecutionFailedException("Sync error", e);
        } finally {
            client.close();
        }
    }
    
    /**
     * Check if the device is ready to synchronize (is there a network
     * connection?).
     */
    private boolean isReadyToSync() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (!cm.getBackgroundDataSetting()) {
            // The user disabled background data: this service should not be
            // running.
            return false;
        }
        
        final NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isAvailable() && netInfo.isConnected();
    }
    
    /**
     * Perform the synchronization with the server. First, local events are
     * uploaded to the server (a local event has no upload time). Then, events
     * from the server are downloaded. The server sends a list of known events
     * (event id/device id pairs): missing events are downloaded, and local
     * expired events are removed.
     */
    private void doSync(AppEngineClient client) throws Exception {
        uploadEvents(client);
    }
    
    private void uploadEvents(AppEngineClient client) throws Exception {
        // Prepare JSON body.
        final JSONObject root = new JSONObject();
        root.put("id", prefs.getString(SP_KEY_DEVICE_ID, null));
        root.put("name", prefs.getString(SP_KEY_DEVICE_NAME, null));
        
        // Get events to upload.
        final String[] eventToUploadColumns = { KEY_ID, KEY_TYPE, KEY_DATE, KEY_FROM_NUMBER,
                KEY_FROM_NAME, KEY_MESSAGE };
        final Cursor c = getContentResolver().query(CONTENT_URI, eventToUploadColumns,
            KEY_UPLOAD_TIME + " IS NULL", null, null);
        final int eventCount = c.getCount();
        final List<Long> eventIds = new ArrayList<Long>(eventCount);
        try {
            if (c.moveToFirst()) {
                Log.d(TAG, "Found " + eventCount + " event(s) to upload");
                
                final JSONArray events = new JSONArray();
                do {
                    final long eventId = c.getLong(c.getColumnIndex(KEY_ID));
                    final int eventType = c.getInt(c.getColumnIndex(KEY_TYPE));
                    final long eventDate = c.getLong(c.getColumnIndex(KEY_DATE));
                    final String eventNumber = c.getString(c.getColumnIndex(KEY_FROM_NUMBER));
                    final String eventName = c.getString(c.getColumnIndex(KEY_FROM_NAME));
                    final String eventMessage = c.getString(c.getColumnIndex(KEY_MESSAGE));
                    
                    final JSONObject event = new JSONObject();
                    event.put("id", eventId).put("type", eventType).put("date", eventDate)
                            .put("number", eventNumber).put("name", eventName)
                            .put("message", eventMessage);
                    events.put(event);
                    
                    // Store the event id to update upload time later.
                    eventIds.add(eventId);
                } while (c.moveToNext());
                
                root.put("events", events);
            }
        } finally {
            c.close();
        }
        
        // The request is sent if there are new events to upload or if the sync
        // is required (the device name was updated).
        final boolean sendRequest = eventCount != 0
                || prefs.getBoolean(SP_KEY_SYNC_REQUIRED, false);
        if (sendRequest) {
            final HttpPost req = new HttpPost(createServiceUri("events"));
            req.setEntity(new StringEntity(root.toString()));
            
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Sending event upload request to " + req.getURI() + ": " + root);
            }
            
            // Send the request.
            final HttpResponse resp = client.execute(req);
            final int sc = resp.getStatusLine().getStatusCode();
            if (sc == REQUEST_OK) {
                if (eventCount != 0) {
                    // Update event upload time.
                    final long now = System.currentTimeMillis();
                    final ContentValues cv = new ContentValues(1);
                    cv.put(KEY_UPLOAD_TIME, now);
                    
                    final int eventsUpdated = getContentResolver().update(CONTENT_URI, cv,
                        KEY_ID + " in (" + TextUtils.join(",", eventIds) + ")", null);
                    if (eventsUpdated != eventCount) {
                        Log.w(TAG, "Failed to update event upload time");
                    } else {
                        Log.i(TAG, "Event upload successful");
                        
                        prefsEditor.putBoolean(SP_KEY_SYNC_REQUIRED, false);
                        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
                    }
                }
            } else {
                Log.w(TAG, "Failed to upload events: error " + sc);
            }
        } else {
            Log.d(TAG, "Event upload aborted: nothing to send");
        }
    }
    
    private static String createServiceUri(String serviceName) {
        return "https://" + SERVER_HOST + "/api/" + REMOTE_API_VERSION + "/" + serviceName;
    }
}
