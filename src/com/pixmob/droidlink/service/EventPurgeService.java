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

import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_EVENT_MAX_AGE;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.provider.EventsContract.Event.STATE;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.format.DateUtils;
import android.util.Log;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.actionservice.ActionService;
import com.pixmob.droidlink.provider.EventsContract;

/**
 * Purge events in the database which are too old.
 * @author Pixmob
 */
public class EventPurgeService extends ActionService {
    private SharedPreferences prefs;
    
    public EventPurgeService() {
        super("DroidLink/EventPurge", 1000 * 30, 2);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
    }
    
    @Override
    protected void onHandleAction(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        // If an event is older than this date, it is deleted.
        final long maxCreated = System.currentTimeMillis()
                - prefs.getLong(SP_KEY_EVENT_MAX_AGE, 86400 * 7) * 1000;
        
        Log.i(TAG, "Purge oldest events: limit set to '"
                + DateUtils.formatDateTime(this, maxCreated, DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_SHOW_TIME) + "'");
        
        final ContentValues cv = new ContentValues();
        cv.put(STATE, EventsContract.PENDING_DELETE_STATE);
        
        // Mark for deletion every event which is older than this date.
        getContentResolver().update(EventsContract.CONTENT_URI, cv, CREATED + "<=?",
            new String[] { String.valueOf(maxCreated) });
        
        // Start event synchronization.
        EventsContract.sync(this, EventsContract.LIGHT_SYNC);
    }
}
