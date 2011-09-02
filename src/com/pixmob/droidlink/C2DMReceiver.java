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

import static com.pixmob.droidlink.Constants.C2DM_ACCOUNT_EXTRA;
import static com.pixmob.droidlink.Constants.C2DM_MESSAGE_EXTRA;
import static com.pixmob.droidlink.Constants.C2DM_MESSAGE_SYNC;
import static com.pixmob.droidlink.Constants.C2DM_SENDER_ID;
import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.GOOGLE_ACCOUNT;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_C2DM;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_SYNC_REQUIRED;
import static com.pixmob.droidlink.Constants.SP_KEY_FULL_SYNC;
import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.c2dm.C2DMBaseReceiver;
import com.pixmob.droidlink.features.Features;
import com.pixmob.droidlink.features.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.providers.EventsContract;
import com.pixmob.droidlink.services.DeviceInitService;

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
        prefsEditor.putBoolean(SP_KEY_DEVICE_SYNC_REQUIRED, true);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        startService(new Intent(this, DeviceInitService.class));
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
        final String account = intent.getStringExtra(C2DM_ACCOUNT_EXTRA);
        if (C2DM_MESSAGE_SYNC.equals(message) && !TextUtils.isEmpty(account)) {
            Log.i(TAG, "Sync required through C2DM");
            
            // When a push notification is received, we perform a FULL
            // synchronization: local events are uploaded/deleted, and new
            // events are downloaded.
            prefsEditor.putBoolean(SP_KEY_FULL_SYNC, true);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
            
            // Start synchronization.
            ContentResolver.requestSync(new Account(account, GOOGLE_ACCOUNT),
                EventsContract.AUTHORITY, new Bundle());
        } else {
            Log.w(TAG, "Unsupported C2DM message: " + intent);
        }
    }
}
