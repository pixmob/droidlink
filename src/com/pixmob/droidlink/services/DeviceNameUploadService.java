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

import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.droidlink.R;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.ui.EventsActivity;

/**
 * Upload the device name to the server.
 * @author Pixmob
 */
public class DeviceNameUploadService extends AbstractNetworkService {
    private PendingIntent openMainActivity;
    private SharedPreferences prefs;
    
    public DeviceNameUploadService() {
        super("DeviceNameUpload");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        openMainActivity = PendingIntent.getActivity(this, 0,
            new Intent(this, EventsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
    }
    
    private void uploadDeviceName(NetworkClient client) throws ActionExecutionFailedException {
        final String deviceName = prefs.getString(SP_KEY_DEVICE_NAME, null);
        Log.i(TAG, "Upload device name: " + deviceName);
        
        final JSONObject data = new JSONObject();
        try {
            data.put("name", deviceName);
        } catch (JSONException e) {
            throw new ActionExecutionFailedException(
                    "Cannot build JSON data for updating device name", e);
        }
        
        try {
            client.post("/device/" + client.getDeviceId(), data);
        } catch (AppEngineAuthenticationException e) {
            throw new ActionExecutionFailedException(
                    "Authentication failed: cannot update device name", e);
        } catch (IOException e) {
            throw new ActionExecutionFailedException("I/O error: cannot update device name", e);
        }
    }
    
    @Override
    protected void onHandleActionInternal(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        final NetworkClient client = NetworkClient.newInstance(this);
        if (client == null) {
            throw new ActionExecutionFailedException("Failed to create NetworkClient");
        }
        
        final Notification n = new Notification(android.R.drawable.stat_sys_upload,
                getString(R.string.device_init_running), System.currentTimeMillis());
        n.setLatestEventInfo(this, getString(R.string.app_name),
            getString(R.string.device_init_running), openMainActivity);
        startForeground(R.string.device_init_running, n);
        
        try {
            uploadDeviceName(client);
        } catch (ActionExecutionFailedException e) {
            showErrorNotification();
            throw e;
        } catch (Exception e) {
            showErrorNotification();
            throw new ActionExecutionFailedException("Device name upload error", e);
        } finally {
            stopForeground(true);
            client.close();
        }
    }
    
    private void showErrorNotification() {
        final Notification nError = new Notification(android.R.drawable.stat_sys_warning,
                getString(R.string.device_init_error), System.currentTimeMillis());
        nError.setLatestEventInfo(this, getString(R.string.app_name),
            getString(R.string.device_init_error), openMainActivity);
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(R.string.device_init_error, nError);
    }
}
