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

import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.SP_KEY_IGNORE_MISSED_CALLS;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.provider.EventsContract.Event.DEVICE_ID;
import static com.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static com.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static com.pixmob.droidlink.provider.EventsContract.Event.TYPE;

import java.util.Date;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.CallLog;
import android.util.Log;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.actionservice.ActionService;
import com.pixmob.droidlink.provider.EventsContract;
import com.pixmob.droidlink.util.PhoneUtils;

/**
 * Find and store the last missed call to the database.
 * @author Pixmob
 */
public class MissedCallHandlerService extends ActionService {
    private static final String[] CALL_FIELDS = { CallLog.Calls.TYPE, CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE };
    private SharedPreferences prefs;
    
    public MissedCallHandlerService() {
        super("DroidLink/MissedCallHandler");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
    }
    
    @Override
    protected void onHandleAction(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        if (prefs.getBoolean(SP_KEY_IGNORE_MISSED_CALLS, false)) {
            Log.i(TAG, "Ignore missed call");
            return;
        }
        
        // Wait some time to ensure the missed call is written to the call log.
        SystemClock.sleep(2000);
        
        final Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, CALL_FIELDS, null,
            null, CallLog.Calls.DEFAULT_SORT_ORDER);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    final int type = c.getInt(c.getColumnIndex(CallLog.Calls.TYPE));
                    
                    if (CallLog.Calls.MISSED_TYPE == type) {
                        final long callTime = c.getLong(c.getColumnIndex(CallLog.Calls.DATE));
                        final String fromNumber = PhoneUtils.getPhoneNumber(c.getString(c
                                .getColumnIndex(CallLog.Calls.NUMBER)));
                        final String fromName = c.getString(c
                                .getColumnIndex(CallLog.Calls.CACHED_NAME));
                        
                        Log.i(TAG, "Got missed call: number=" + fromNumber + ", name=" + fromName
                                + ", time=" + new Date(callTime));
                        writeMissedCallEvent(fromNumber, fromName, callTime);
                        
                        // Start synchronization.
                        final String accountName = prefs.getString(SP_KEY_ACCOUNT, null);
                        if (accountName != null) {
                            EventsContract.sync(accountName, EventsContract.LIGHT_SYNC);
                        }
                    } else {
                        if (DEVELOPER_MODE) {
                            Log.w(TAG, "Missed call not found!");
                        }
                    }
                }
            } finally {
                c.close();
            }
        }
    }
    
    private void writeMissedCallEvent(String number, String name, long date) {
        final String deviceId = prefs.getString(SP_KEY_DEVICE_ID, null);
        if (deviceId == null) {
            Log.wtf(TAG, "No device id set");
            return;
        }
        
        final ContentValues cv = new ContentValues();
        cv.put(DEVICE_ID, deviceId);
        cv.put(CREATED, date);
        cv.put(NUMBER, number);
        cv.put(NAME, name);
        cv.put(TYPE, EventsContract.MISSED_CALL_TYPE);
        
        final Uri uri = getContentResolver().insert(EventsContract.CONTENT_URI, cv);
        if (DEVELOPER_MODE) {
            Log.i(TAG, "New event created for missed call: " + uri);
        }
    }
}
