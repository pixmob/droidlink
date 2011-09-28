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
package com.pixmob.droidlink;

import static com.pixmob.droidlink.Constants.C2DM_MESSAGE_EXTRA;
import static com.pixmob.droidlink.Constants.C2DM_MESSAGE_SYNC;
import static com.pixmob.droidlink.Constants.C2DM_SENDER_ID;
import static com.pixmob.droidlink.Constants.C2DM_SYNC_EXTRA;
import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.EXTRA_FORCE_UPLOAD;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_C2DM;
import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.c2dm.C2DMBaseReceiver;
import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.provider.EventsContract;
import com.pixmob.droidlink.service.DeviceInitService;

/**
 * Handle C2DM events.
 * @author Pixmob
 */
public class C2DMReceiver extends C2DMBaseReceiver {
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    
    public C2DMReceiver() {
        super(C2DM_SENDER_ID);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        prefsEditor = prefs.edit();
    }
    
    @Override
    public void onError(Context context, String errorId) {
        Log.e(TAG, "C2DM error: " + errorId);
    }
    
    @Override
    public void onRegistered(Context context, String registrationId) throws IOException {
        if (DEVELOPER_MODE) {
            Log.d(TAG, "C2DM registered: " + registrationId);
        }
        prefsEditor.putString(SP_KEY_DEVICE_C2DM, registrationId);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        
        final Intent i = new Intent(this, DeviceInitService.class);
        i.putExtra(EXTRA_FORCE_UPLOAD, true);
        startService(i);
    }
    
    @Override
    public void onUnregistered(Context context) {
        if (DEVELOPER_MODE) {
            Log.d(TAG, "Unregistered from C2DM");
        }
        prefsEditor.remove(SP_KEY_DEVICE_C2DM);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
    }
    
    @Override
    protected void onMessage(Context context, Intent intent) {
        final String message = intent.getStringExtra(C2DM_MESSAGE_EXTRA);
        if (C2DM_MESSAGE_SYNC.equals(message)) {
            Log.i(TAG, "Sync required by a push notification");
            
            String syncToken = intent.getStringExtra(C2DM_SYNC_EXTRA);
            if (TextUtils.isEmpty(syncToken)) {
                syncToken = null;
            }
            
            // When a push notification is received, we perform a FULL
            // synchronization: local events are uploaded/deleted, and remote
            // events are synchronized.
            EventsContract.sync(this, EventsContract.FULL_SYNC, syncToken);
        } else {
            Log.w(TAG, "Unsupported C2DM message: " + intent);
        }
    }
}
