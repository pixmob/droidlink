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
package com.pixmob.droidlink.sync;

import static android.provider.BaseColumns._ID;
import static com.pixmob.droidlink.Constants.ACTION_SYNC;
import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.EXTRA_FORCE_UPLOAD;
import static com.pixmob.droidlink.Constants.EXTRA_RUNNING;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.provider.EventsContract.Event.DEVICE_ID;
import static com.pixmob.droidlink.provider.EventsContract.Event.MESSAGE;
import static com.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static com.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static com.pixmob.droidlink.provider.EventsContract.Event.STATE;
import static com.pixmob.droidlink.provider.EventsContract.Event.TYPE;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.provider.EventsContract;
import com.pixmob.droidlink.service.DeviceInitService;

/**
 * Synchronize events between this device and the remote server.
 * @author Pixmob
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String[] PROJECTION = { _ID, TYPE, CREATED, NUMBER, NAME, MESSAGE, STATE };
    private static final String[] PROJECTION_ID = { _ID };
    private static final String SP_KEY_LAST_SYNC = "lastSync";
    
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
        if (!fullSync) {
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
        // Check if the device exists on the remote server.
        try {
            client.getAsArray("/device/" + client.getDeviceId());
        } catch (IOException e) {
            Log.w(TAG, "I/O error: cannot sync", e);
            syncResult.stats.numIoExceptions++;
            
            registerDevice();
            return;
        } catch (AppEngineAuthenticationException e) {
            Log.e(TAG, "Authentication error: cannot sync", e);
            syncResult.stats.numAuthExceptions++;
            return;
        }
        
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
        
        final ContentValues values = new ContentValues(1);
        final String eventSelection = _ID + "=?";
        final String[] eventSelectionArgs = new String[1];
        
        if (eventsToDelete.isEmpty()) {
            Log.i(TAG, "No events to delete");
        } else {
            Log.i(TAG, "Found " + eventsToDelete.size() + " event(s) to delete");
        }
        
        // Delete events on the remote server.
        for (final String eventId : eventsToDelete) {
            final String eventUUID = client.getDeviceId() + "/" + eventId;
            if (DEVELOPER_MODE) {
                Log.d(TAG, "Deleting event: " + eventId);
            }
            
            try {
                client.delete("/device/" + eventUUID);
                
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "Deleting event in local database: " + eventId);
                }
                eventSelectionArgs[0] = eventId;
                provider.delete(EventsContract.CONTENT_URI, eventSelection, eventSelectionArgs);
                syncResult.stats.numDeletes++;
                
                Log.i(TAG, "Event deletion successful: " + eventId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to delete event " + eventId + " from local database", e);
                syncResult.stats.numIoExceptions++;
            } catch (IOException e) {
                Log.w(TAG, "Event deletion error: cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            } catch (AppEngineAuthenticationException e) {
                Log.e(TAG, "Authentication error: cannot sync", e);
                syncResult.stats.numAuthExceptions++;
                return;
            }
        }
        
        if (fullSync) {
            // Get all user devices.
            final Set<String> deviceIds = new HashSet<String>(4);
            if (DEVELOPER_MODE) {
                Log.d(TAG, "Fetching devices from the server");
            }
            try {
                final JSONArray devices = client.getAsArray("/device");
                final int devicesLen = devices.length();
                if (devicesLen == 0) {
                    // Unlikely to happen; at least this user device should
                    // exist.
                    Log.i(TAG, "No devices found");
                } else {
                    Log.i(TAG, "Found " + devicesLen + " device(s)");
                }
                
                for (int i = 0; i < devicesLen; ++i) {
                    try {
                        final JSONObject device = devices.getJSONObject(i);
                        final String deviceId = device.getString("id");
                        deviceIds.add(deviceId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Invalid device at index " + i + ": cannot sync", e);
                        syncResult.stats.numParseExceptions++;
                        return;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Device listing error: cannot sync", e);
                syncResult.stats.numIoExceptions++;
                return;
            } catch (AppEngineAuthenticationException e) {
                Log.e(TAG, "Authentication error: cannot sync", e);
                syncResult.stats.numAuthExceptions++;
                return;
            }
            
            for (final String deviceId : deviceIds) {
                // Get all events from the remote server for this device.
                final JSONArray events;
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "Fetching events from the server for device " + deviceId);
                }
                try {
                    events = client.getAsArray("/device/" + deviceId);
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
                    Log.i(TAG, "No events from the server for device " + deviceId);
                } else {
                    Log.i(TAG, "Found " + eventsLen + " event(s) from the server for device "
                            + deviceId);
                }
                
                // Build a collection with local event identifiers.
                // This collection will be used to identify which events have
                // been deleted on the remote server.
                final Set<String> localEventIds;
                try {
                    c = provider.query(EventsContract.CONTENT_URI, PROJECTION_ID, DEVICE_ID
                            + "=? AND " + STATE + "=?", new String[] { deviceId,
                            String.valueOf(EventsContract.UPLOADED_STATE) }, null);
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
                            eventSelectionArgs[0] = eventId;
                            
                            if (DEVELOPER_MODE) {
                                Log.d(TAG, "Updating event in local database: " + eventId);
                            }
                            provider.update(EventsContract.CONTENT_URI, values, eventSelection,
                                eventSelectionArgs);
                            syncResult.stats.numUpdates++;
                        } else {
                            // The event was not found: insert it.
                            values.clear();
                            values.put(_ID, eventId);
                            values.put(DEVICE_ID, deviceId);
                            values.put(CREATED, event.getLong("created"));
                            values.put(TYPE, event.getInt("type"));
                            values.put(NUMBER, trimToNull(event.getString("number")));
                            values.put(NAME, trimToNull(event.getString("name")));
                            values.put(MESSAGE, trimToNull(event.getString("message")));
                            values.put(STATE, EventsContract.UPLOADED_STATE);
                            
                            if (DEVELOPER_MODE) {
                                Log.d(TAG, "Adding event to local database: " + eventId);
                            }
                            provider.insert(EventsContract.CONTENT_URI, values);
                            syncResult.stats.numInserts++;
                        }
                        
                        // This event now exists in the local database:
                        // remove its identifier from this collection as we
                        // don't want to delete it.
                        localEventIds.remove(eventId);
                    } catch (JSONException e) {
                        Log.w(TAG, "Invalid event at index " + i + ": cannot sync", e);
                        syncResult.stats.numSkippedEntries++;
                        continue;
                    } catch (RemoteException e) {
                        Log.w(TAG, "Failed to get event " + eventId + " from local database", e);
                        syncResult.stats.numIoExceptions++;
                    }
                }
                
                // The remaining event identifiers was removed on the remote
                // server: there are still present in the local database. These
                // events are now being deleted.
                for (final String eventId : localEventIds) {
                    if (DEVELOPER_MODE) {
                        Log.d(TAG, "Deleting event in local database: " + eventId);
                    }
                    eventSelectionArgs[0] = eventId;
                    try {
                        provider.delete(EventsContract.CONTENT_URI, eventSelection,
                            eventSelectionArgs);
                        syncResult.stats.numDeletes++;
                    } catch (RemoteException e) {
                        Log.w(TAG, "Failed to delete event " + eventId + " from local database", e);
                        syncResult.stats.numIoExceptions++;
                    }
                }
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
            final String eventUUID = client.getDeviceId() + "/" + eventId;
            
            if (DEVELOPER_MODE) {
                Log.d(TAG, "Uploading event: " + eventId);
            }
            
            final JSONObject event = entry.getValue();
            try {
                client.put("/device/" + eventUUID, event);
                
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "Updating event state to UPLOADED: " + eventId);
                }
                values.clear();
                values.put(STATE, EventsContract.UPLOADED_STATE);
                eventSelectionArgs[0] = eventId;
                provider.update(EventsContract.CONTENT_URI, values, eventSelection,
                    eventSelectionArgs);
                syncResult.stats.numUpdates++;
                
                Log.i(TAG, "Event upload successful: " + eventId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to update event " + eventId + " to UPLOADED", e);
                syncResult.stats.numIoExceptions++;
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
        
        // Store sync time.
        final SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putLong(SP_KEY_LAST_SYNC, System.currentTimeMillis());
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
    }
    
    private void registerDevice() {
        final Intent i = new Intent(getContext(), DeviceInitService.class);
        i.putExtra(EXTRA_FORCE_UPLOAD, true);
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
