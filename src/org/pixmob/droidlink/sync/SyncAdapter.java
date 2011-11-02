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
package org.pixmob.droidlink.sync;

import static android.provider.BaseColumns._ID;
import static org.pixmob.droidlink.Constants.ACTION_NEW_EVENT;
import static org.pixmob.droidlink.Constants.ACTION_SYNC;
import static org.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static org.pixmob.droidlink.Constants.EXTRA_EVENT_COUNT;
import static org.pixmob.droidlink.Constants.EXTRA_EVENT_ID;
import static org.pixmob.droidlink.Constants.EXTRA_FORCE_UPLOAD;
import static org.pixmob.droidlink.Constants.EXTRA_RUNNING;
import static org.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static org.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static org.pixmob.droidlink.Constants.TAG;
import static org.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static org.pixmob.droidlink.provider.EventsContract.Event.DEVICE_ID;
import static org.pixmob.droidlink.provider.EventsContract.Event.MESSAGE;
import static org.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static org.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static org.pixmob.droidlink.provider.EventsContract.Event.STATE;
import static org.pixmob.droidlink.provider.EventsContract.Event.TYPE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.pixmob.appengine.client.AppEngineAuthenticationException;
import org.pixmob.droidlink.feature.Features;
import org.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import org.pixmob.droidlink.net.NetworkClient;
import org.pixmob.droidlink.net.NetworkClientException;
import org.pixmob.droidlink.provider.EventsContract;
import org.pixmob.droidlink.service.DeviceInitService;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;


/**
 * Synchronize events between this device and the remote server.
 * @author Pixmob
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String[] PROJECTION = { _ID, TYPE, CREATED, NUMBER, NAME, MESSAGE, STATE };
    private static final String[] PROJECTION_ID = { _ID };
    private static final String SP_KEY_LAST_SYNC = "lastSync";
    private static final String SP_KEY_SYNC_TOKEN = "syncToken";
    
    public SyncAdapter(final Context context) {
        super(context, false);
    }
    
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        try {
            doPerformSync(account, extras, authority, provider, syncResult);
        } catch (Exception e) {
            Log.e(TAG, "Synchronization error", e);
        }
    }
    
    private void doPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        final SharedPreferences prefs = getContext().getSharedPreferences(SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE);
        
        final String lastSyncToken = prefs.getString(SP_KEY_SYNC_TOKEN, null);
        final String syncToken = extras.getString(EventsContract.SYNC_TOKEN);
        if (DEVELOPER_MODE) {
            Log.d(TAG, "Sync token: " + syncToken + "; last sync token: " + lastSyncToken);
        }
        
        if (lastSyncToken != null && lastSyncToken.equals(syncToken)) {
            Log.w(TAG, "Skip synchronization since this device is already synchronized");
            return;
        }
        
        // Make sure this sync is about our user.
        final String accountName = prefs.getString(SP_KEY_ACCOUNT, null);
        if (!account.type.equals("com.google") || !account.name.equals(accountName)) {
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Skip sync for user account " + account.name + "/" + account.type);
            }
            return;
        }
        
        // Check if a synchronization was just made: do not run twice.
        final long lastSync = prefs.getLong(SP_KEY_LAST_SYNC, 0);
        if (System.currentTimeMillis() - lastSync < 1000) {
            Log.w(TAG, "Skip synchronization for user account " + account.name
                    + " since it has just ran");
            return;
        }
        
        // Check if a network client can be created
        // (a device identifier is required).
        final NetworkClient client = NetworkClient.newInstance(getContext());
        if (client == null) {
            Log.w(TAG, "Failed to create NetworkClient: cannot sync");
            syncResult.stats.numAuthExceptions++;
            return;
        }
        
        Log.i(TAG, "Start synchronization for user " + accountName);
        
        final boolean fullSync = extras.getInt(EventsContract.SYNC_STRATEGY,
            EventsContract.FULL_SYNC) == EventsContract.FULL_SYNC;
        if (fullSync) {
            Log.i(TAG, "Performing FULL sync");
        } else {
            Log.i(TAG, "Performing LIGHT sync");
        }
        
        // Notify that synchronization is starting.
        Intent syncIntent = new Intent(ACTION_SYNC);
        syncIntent.putExtra(EXTRA_RUNNING, true);
        getContext().sendStickyBroadcast(syncIntent);
        
        try {
            doPerformSync(client, prefs, provider, syncResult, fullSync);
        } finally {
            client.close();
            
            // Notify synchronization end.
            syncIntent.putExtra(EXTRA_RUNNING, false);
            getContext().sendStickyBroadcast(syncIntent);
            
            Log.i(TAG, "Synchronization done for user " + accountName);
        }
    }
    
    private void doPerformSync(NetworkClient client, SharedPreferences prefs,
            ContentProviderClient provider, SyncResult syncResult, boolean fullSync) {
        // Prepare the query.
        final String selection = DEVICE_ID + "=? AND " + STATE + "=? OR " + STATE + "=?";
        final String[] selectionArgs = { client.getDeviceId(),
                String.valueOf(EventsContract.PENDING_UPLOAD_STATE),
                String.valueOf(EventsContract.PENDING_DELETE_STATE) };
        
        // Get local data to sync.
        final Map<String, JSONObject> eventsToUpload = new HashMap<String, JSONObject>(8);
        final Set<String> eventsToDelete = new HashSet<String>(4);
        Cursor c = null;
        try {
            c = provider.query(EventsContract.CONTENT_URI, PROJECTION, selection, selectionArgs,
                null);
            
            final int idIdx = c.getColumnIndexOrThrow(_ID);
            final int typeIdx = c.getColumnIndexOrThrow(TYPE);
            final int createdIdx = c.getColumnIndexOrThrow(CREATED);
            final int numberIdx = c.getColumnIndexOrThrow(NUMBER);
            final int nameIdx = c.getColumnIndexOrThrow(NAME);
            final int messageIdx = c.getColumnIndexOrThrow(MESSAGE);
            final int stateIdx = c.getColumnIndexOrThrow(STATE);
            
            while (c.moveToNext()) {
                final String eventId = c.getString(idIdx);
                final int eventState = c.getInt(stateIdx);
                
                if (EventsContract.PENDING_UPLOAD_STATE == eventState) {
                    // This is a newly created event.
                    final JSONObject event = new JSONObject();
                    try {
                        event.put("deviceId", client.getDeviceId());
                        event.put("created", c.getLong(createdIdx));
                        event.put("type", c.getInt(typeIdx));
                        event.put("number", c.getString(numberIdx));
                        event.put("name", c.getString(nameIdx));
                        event.put("message", c.getString(messageIdx));
                    } catch (JSONException e) {
                        Log.w(TAG, "Invalid event " + eventId + ": cannot sync", e);
                        syncResult.stats.numSkippedEntries++;
                        continue;
                    }
                    eventsToUpload.put(eventId, event);
                } else if (EventsContract.PENDING_DELETE_STATE == eventState) {
                    // The user wants this event to be deleted.
                    eventsToDelete.add(eventId);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get events: cannot sync", e);
            syncResult.stats.numIoExceptions++;
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
        
        final ArrayList<ContentProviderOperation> batch = new ArrayList<ContentProviderOperation>(
                32);
        final ContentValues values = new ContentValues(8);
        
        if (eventsToDelete.isEmpty()) {
            Log.i(TAG, "No events to delete");
        } else {
            Log.i(TAG, "Found " + eventsToDelete.size() + " event(s) to delete");
        }
        
        // Delete events on the remote server.
        for (final String eventId : eventsToDelete) {
            if (DEVELOPER_MODE) {
                Log.d(TAG, "Deleting event: " + eventId);
            }
            
            try {
                client.delete("/events/" + eventId);
                
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "Deleting event in local database: " + eventId);
                }
                batch.add(ContentProviderOperation.newDelete(
                    Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId)).build());
                syncResult.stats.numDeletes++;
            } catch (IOException e) {
                Log.e(TAG, "Event deletion error: cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            } catch (AppEngineAuthenticationException e) {
                Log.e(TAG, "Authentication error: cannot sync", e);
                syncResult.stats.numAuthExceptions++;
                return;
            }
        }
        
        try {
            provider.applyBatch(batch);
        } catch (Exception e) {
            Log.w(TAG, "Database error: cannot sync", e);
            syncResult.stats.numIoExceptions++;
            return;
        }
        batch.clear();
        
        if (fullSync) {
            // Get all events from the remote server.
            final JSONArray events;
            if (DEVELOPER_MODE) {
                Log.d(TAG, "Fetching events from the remote server");
            }
            try {
                events = client.getAsArray("/events");
            } catch (IOException e) {
                Log.e(TAG, "Event listing error: cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            } catch (AppEngineAuthenticationException e) {
                Log.e(TAG, "Authentication error: cannot sync", e);
                syncResult.stats.numAuthExceptions++;
                return;
            }
            
            final int eventsLen = events != null ? events.length() : 0;
            if (eventsLen == 0) {
                Log.i(TAG, "No events from the remote server");
            } else {
                Log.i(TAG, "Found " + eventsLen + " event(s) from the remote server");
            }
            
            // Build a collection with local event identifiers.
            // This collection will be used to identify which events have
            // been deleted on the remote server.
            final Set<String> localEventIds;
            try {
                c = provider.query(EventsContract.CONTENT_URI, PROJECTION_ID, STATE + "=?",
                    new String[] { String.valueOf(EventsContract.UPLOADED_STATE) }, null);
                localEventIds = new HashSet<String>(c.getCount());
                
                final int idIdx = c.getColumnIndexOrThrow(_ID);
                while (c.moveToNext()) {
                    final String eventId = c.getString(idIdx);
                    localEventIds.add(eventId);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to get events from local database", e);
                syncResult.stats.numIoExceptions++;
                return;
            } finally {
                if (c != null) {
                    c.close();
                    c = null;
                }
            }
            
            String newEventId = null;
            int newEventCount = 0;
            
            // Reconcile remote events with local events.
            for (int i = 0; i < eventsLen; ++i) {
                String eventId = null;
                try {
                    final JSONObject event = events.getJSONObject(i);
                    eventId = event.getString("id");
                    
                    // Check if this event exists in the local database.
                    if (localEventIds.contains(eventId)) {
                        // Found the event: update it.
                        values.clear();
                        values.put(NUMBER, trimToNull(event.getString("number")));
                        values.put(NAME, trimToNull(event.getString("name")));
                        values.put(MESSAGE, trimToNull(event.getString("message")));
                        
                        if (DEVELOPER_MODE) {
                            Log.d(TAG, "Updating event in local database: " + eventId);
                        }
                        batch.add(ContentProviderOperation.newUpdate(
                            Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId))
                                .withExpectedCount(1).withValues(values).build());
                        syncResult.stats.numUpdates++;
                    } else {
                        // The event was not found: insert it.
                        values.clear();
                        values.put(_ID, eventId);
                        values.put(DEVICE_ID, event.getString("deviceId"));
                        values.put(CREATED, event.getLong("created"));
                        values.put(TYPE, event.getInt("type"));
                        values.put(NUMBER, trimToNull(event.getString("number")));
                        values.put(NAME, trimToNull(event.getString("name")));
                        values.put(MESSAGE, trimToNull(event.getString("message")));
                        values.put(STATE, EventsContract.UPLOADED_STATE);
                        
                        if (DEVELOPER_MODE) {
                            Log.d(TAG, "Adding event to local database: " + eventId);
                        }
                        batch.add(ContentProviderOperation.newInsert(
                            Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId)).withValues(
                            values).build());
                        syncResult.stats.numInserts++;
                        
                        ++newEventCount;
                        if (newEventId == null) {
                            newEventId = eventId;
                        }
                    }
                    
                    // This event now exists in the local database:
                    // remove its identifier from this collection as we
                    // don't want to delete it.
                    localEventIds.remove(eventId);
                } catch (JSONException e) {
                    Log.w(TAG, "Invalid event at index " + i + ": cannot sync", e);
                    syncResult.stats.numSkippedEntries++;
                    continue;
                }
            }
            
            // The remaining event identifiers was removed on the remote
            // server: there are still present in the local database. These
            // events are now being deleted.
            for (final String eventId : localEventIds) {
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "Deleting event in local database: " + eventId);
                }
                batch.add(ContentProviderOperation.newDelete(
                    Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId)).build());
                syncResult.stats.numDeletes++;
            }
            
            try {
                provider.applyBatch(batch);
            } catch (Exception e) {
                Log.e(TAG, "Database error: cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            }
            batch.clear();
            
            if (newEventCount > 1) {
                newEventId = null;
            }
            if (newEventCount != 0) {
                startSyncNotificationService(newEventCount, newEventId);
            }
        }
        
        final int numEventsToUpload = eventsToUpload.size();
        if (numEventsToUpload == 0) {
            Log.i(TAG, "No events to upload");
        } else {
            Log.i(TAG, "Found " + numEventsToUpload + " event(s) to upload");
        }
        
        // Send local events to the remote server.
        for (final Map.Entry<String, JSONObject> entry : eventsToUpload.entrySet()) {
            final String eventId = entry.getKey();
            
            if (DEVELOPER_MODE) {
                Log.d(TAG, "Uploading event: " + eventId);
            }
            
            final JSONObject event = entry.getValue();
            try {
                client.put("/events/" + eventId, event);
                
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "Updating event state to UPLOADED: " + eventId);
                }
                values.clear();
                values.put(STATE, EventsContract.UPLOADED_STATE);
                batch.add(ContentProviderOperation.newUpdate(
                    Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId)).withValues(values)
                        .withExpectedCount(1).build());
                syncResult.stats.numUpdates++;
                
                Log.i(TAG, "Event upload successful: " + eventId);
            } catch (NetworkClientException e) {
                if (e.getStatusCode() == 404) {
                    Log.e(TAG, "Device not found: cannot sync", e);
                    registerDevice();
                } else {
                    Log.e(TAG, "Network error: cannot sync", e);
                }
                syncResult.stats.numIoExceptions++;
                return;
            } catch (IOException e) {
                Log.e(TAG, "Event upload error: cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            } catch (AppEngineAuthenticationException e) {
                Log.e(TAG, "Authentication error: cannot sync", e);
                syncResult.stats.numAuthExceptions++;
                return;
            }
        }
        
        try {
            provider.applyBatch(batch);
        } catch (Exception e) {
            Log.w(TAG, "Database error: cannot sync", e);
            syncResult.stats.numIoExceptions++;
            return;
        }
        batch.clear();
        
        final SharedPreferences.Editor prefsEditor = prefs.edit();
        final boolean syncRequired = !eventsToDelete.isEmpty() || !eventsToUpload.isEmpty();
        if (syncRequired) {
            // Generate an unique sync token: the server will send this token to
            // every devices. If this token is received on this device, the sync
            // will not start.
            final String syncToken = UUID.randomUUID().toString();
            prefsEditor.putString(SP_KEY_SYNC_TOKEN, syncToken);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
            
            // Sync user devices.
            try {
                final JSONObject data = new JSONObject();
                data.put("token", syncToken);
                client.post("/devices/" + client.getDeviceId() + "/sync", data);
            } catch (NetworkClientException e) {
                if (e.getStatusCode() == 404) {
                    registerDevice();
                }
            } catch (IOException e) {
                Log.e(TAG, "Device sync error: cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            } catch (AppEngineAuthenticationException e) {
                Log.e(TAG, "Authentication error: cannot sync", e);
                syncResult.stats.numAuthExceptions++;
                return;
            } catch (JSONException e) {
                Log.w(TAG, "Invalid sync token " + syncToken + ": cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            }
        }
        
        // Store sync time.
        prefsEditor.putLong(SP_KEY_LAST_SYNC, System.currentTimeMillis());
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
    }
    
    private void registerDevice() {
        final Intent i = new Intent(getContext(), DeviceInitService.class);
        i.putExtra(EXTRA_FORCE_UPLOAD, true);
        getContext().startService(i);
    }
    
    private void startSyncNotificationService(int newEventCount, String newEventId) {
        final Intent i = new Intent(ACTION_NEW_EVENT);
        i.putExtra(EXTRA_EVENT_COUNT, newEventCount);
        if (newEventId != null) {
            i.putExtra(EXTRA_EVENT_ID, newEventId);
        }
        getContext().startService(i);
    }
    
    /**
     * Remove trailing spaces.
     */
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        final String s2 = s.trim();
        if ("null".equals(s2) || s2.length() == 0) {
            return null;
        }
        return s2;
    }
}
