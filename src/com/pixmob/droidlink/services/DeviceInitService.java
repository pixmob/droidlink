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

import static com.pixmob.droidlink.Constants.C2DM_SENDER_ID;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_C2DM;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.TAG;

import java.util.UUID;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.android.c2dm.C2DMessaging;
import com.pixmob.droidlink.features.Features;
import com.pixmob.droidlink.features.SharedPreferencesSaverFeature;

/**
 * Initialize this device: generate an unique identifier, register to C2DM.
 * @author Pixmob
 */
public class DeviceInitService extends IntentService {
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    
    public DeviceInitService() {
        super("DeviceIdGenerator");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        prefsEditor = prefs.edit();
    }
    
    @Override
    protected void onHandleIntent(Intent intent) {
        generateId();
    }
    
    private void generateId() {
        if (!prefs.contains(SP_KEY_DEVICE_ID)) {
            final String deviceName = Build.MODEL;
            Log.i(TAG, "Generating a new identifier for this device (" + deviceName + ")");
            
            final String deviceId = UUID.randomUUID().toString();
            prefsEditor.putString(SP_KEY_DEVICE_ID, deviceId);
            prefsEditor.putString(SP_KEY_DEVICE_NAME, deviceName);
            
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        }
        if (!prefs.contains(SP_KEY_DEVICE_C2DM)) {
            C2DMessaging.register(this, C2DM_SENDER_ID);
        }
    }
}
