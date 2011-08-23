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

import static com.pixmob.droidlink.Constants.SERVER_HOST;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.Constants.USER_AGENT;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.appengine.client.AppEngineClient;
import com.pixmob.droidlink.util.HttpUtils;

/**
 * Upload the device name to the server.
 * @author Pixmob
 */
public class DeviceNameUploadService extends AbstractNetworkService {
    private SharedPreferences prefs;
    
    public DeviceNameUploadService() {
        super("DeviceNameUpload");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
    }
    
    private void uploadDeviceName() throws ActionExecutionFailedException {
        final String accountName = prefs.getString(SP_KEY_ACCOUNT, null);
        if (accountName == null) {
            Log.w(TAG, "No account is selected: cannot upload device name");
            return;
        }
        final String deviceId = prefs.getString(SP_KEY_DEVICE_ID, null);
        if (deviceId == null) {
            Log.w(TAG, "No device id set: cannot upload device name");
            return;
        }
        
        final String deviceName = prefs.getString(SP_KEY_DEVICE_NAME, null);
        Log.i(TAG, "Upload device name: " + deviceName);
        
        final AppEngineClient client = new AppEngineClient(this, SERVER_HOST);
        client.setAccount(accountName);
        client.setHttpUserAgent(USER_AGENT);
        
        // Prepare a Json request: a PUT method on the device resource Uri.
        final String uri = HttpUtils.createServiceUri("/device/" + deviceId);
        final HttpPost req = new HttpPost(uri);
        HttpUtils.prepareJsonRequest(req);
        
        // Build request payload (Json).
        final JSONObject data = new JSONObject();
        try {
            data.put("name", deviceName);
        } catch (JSONException e) {
            throw new ActionExecutionFailedException(
                    "Cannot build JSON data for updating device name", e);
        }
        try {
            req.setEntity(new StringEntity(data.toString()));
        } catch (UnsupportedEncodingException e) {
            throw new ActionExecutionFailedException(
                    "Cannot build JSON data for updating device name", e);
        }
        
        try {
            // Execute the request.
            final HttpResponse resp = client.execute(req);
            final int sc = resp.getStatusLine().getStatusCode();
            if (HttpUtils.isStatusOK(sc)) {
                Log.i(TAG, "Device name upload successful");
            } else if (HttpUtils.isStatusNotFound(sc)) {
                // TODO Show a notification when the device was deleted.
            } else {
                // TODO Show a notification for displaying this error.
                throw new ActionExecutionFailedException("Device name upload "
                        + "failed with error " + sc);
            }
        } catch (AppEngineAuthenticationException e) {
            // TODO Show a notification for displaying this error.
            throw new ActionExecutionFailedException(
                    "Authentication failed: cannot update device name", e);
        } catch (IOException e) {
            // TODO Show a notification for displaying this error.
            throw new ActionExecutionFailedException("I/O error: cannot update device name", e);
        } finally {
            client.close();
        }
    }
    
    @Override
    protected void onHandleActionInternal(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        // TODO Make the service run in the foreground with a notification.
        uploadDeviceName();
    }
}
