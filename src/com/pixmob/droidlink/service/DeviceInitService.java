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

import static com.pixmob.droidlink.Constants.C2DM_SENDER_ID;
import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.EXTRA_FORCE_UPLOAD;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_C2DM;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.android.c2dm.C2DMessaging;
import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.droidlink.R;
import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.ui.EventsActivity;
import com.pixmob.droidlink.util.DeviceUtils;

/**
 * Initialize this device: generate an unique identifier, register to C2DM,
 * upload device name.
 * @author Pixmob
 */
public class DeviceInitService extends AbstractNetworkService {
    private static final String SP_KEY_UPLOAD_DONE = "uploadDone";
    private PendingIntent openMainActivity;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    
    public DeviceInitService() {
        super("DroidLink/DeviceInit", 30 * 1000, 4);
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        prefsEditor = prefs.edit();
        openMainActivity = PendingIntent.getActivity(this, 0,
            new Intent(this, EventsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
    }
    
    @Override
    protected void onPreHandleActionInternal(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        // No network is required for generating a device identifier.
        generateId();
    }
    
    @Override
    protected void onHandleActionInternal(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        // Network connectivity is required for registering to C2DM and
        // uploading device configuration.
        registerC2DM();
        
        final boolean upload = intent.getBooleanExtra(EXTRA_FORCE_UPLOAD, false)
                || !prefs.getBoolean(SP_KEY_UPLOAD_DONE, false);
        if (upload) {
            uploadDeviceConf();
        }
    }
    
    private void generateId() {
        if (!prefs.contains(SP_KEY_DEVICE_NAME)) {
            final String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
            prefsEditor.putString(SP_KEY_DEVICE_NAME, deviceName);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        }
        
        final String account = prefs.getString(SP_KEY_ACCOUNT, null);
        if (account == null) {
            Log.i(TAG, "No account set: cannot generate device identifier");
            return;
        }
        
        if (!prefs.contains(SP_KEY_DEVICE_ID)) {
            final String deviceId = DeviceUtils.getDeviceId(this, account);
            Log.i(TAG, "Device identifier generated for " + account + ": " + deviceId);
            
            prefsEditor.putString(SP_KEY_DEVICE_ID, deviceId);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        }
    }
    
    private void registerC2DM() {
        if (!prefs.contains(SP_KEY_DEVICE_C2DM)) {
            C2DMessaging.register(getApplicationContext(), C2DM_SENDER_ID);
        }
    }
    
    private void uploadDeviceConf() throws ActionExecutionFailedException {
        final String deviceName = prefs.getString(SP_KEY_DEVICE_NAME, null);
        final String deviceC2dm = prefs.getString(SP_KEY_DEVICE_C2DM, null);
        
        final NetworkClient client = NetworkClient.newInstance(this);
        if (client != null) {
            final Notification n = new Notification(android.R.drawable.stat_notify_sync,
                    getString(R.string.device_setup_running), System.currentTimeMillis());
            n.setLatestEventInfo(this, getString(R.string.app_name),
                getString(R.string.device_setup_running), openMainActivity);
            startForeground(R.string.device_setup_running, n);
            
            try {
                final JSONObject data = new JSONObject();
                data.put("name", deviceName);
                data.put("c2dm", deviceC2dm);
                
                if (DEVELOPER_MODE) {
                    Log.i(TAG, "Initializing device " + client.getDeviceId() + ": name="
                            + deviceName + ", c2dm=" + deviceC2dm);
                }
                client.put("/devices/" + client.getDeviceId(), data);
                
                prefsEditor.putBoolean(SP_KEY_UPLOAD_DONE, true);
                Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
            } catch (JSONException e) {
                throw new ActionExecutionFailedException("JSON error: cannot init device", e);
            } catch (IOException e) {
                throw new ActionExecutionFailedException("I/O error: cannot init device", e);
            } catch (AppEngineAuthenticationException e) {
                throw new ActionExecutionFailedException(
                        "Authentication error: cannot init device", e);
            } finally {
                client.close();
                stopForeground(true);
            }
        }
    }
}
