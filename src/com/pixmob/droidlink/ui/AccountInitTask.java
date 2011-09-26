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
package com.pixmob.droidlink.ui;

import static com.pixmob.droidlink.Constants.GOOGLE_ACCOUNT;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_C2DM;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.provider.EventsContract;
import com.pixmob.droidlink.util.Accounts;
import com.pixmob.droidlink.util.DeviceUtils;

/**
 * Check an account and register a device.
 * @author Pixmon.
 */
class AccountInitTask extends AsyncTask<String, Void, Integer> {
    private static final int AUTH_OK = 0;
    private static final int AUTH_FAIL = 1;
    private static final int AUTH_PENDING = 2;
    private final Fragment fragment;
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor prefsEditor;
    private final ContentResolver contentResolver;
    private final Account[] accounts;
    private Intent authPendingIntent;
    
    public AccountInitTask(final Fragment fragment) {
        this.fragment = fragment;
        
        final Activity context = fragment.getActivity();
        prefs = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
        prefsEditor = prefs.edit();
        contentResolver = context.getContentResolver();
        accounts = Accounts.list(context);
    }
    
    protected Fragment getFragment() {
        return fragment;
    }
    
    @Override
    protected Integer doInBackground(String... params) {
        final String newAccount = params[0];
        final String oldAccount = prefs.getString(SP_KEY_ACCOUNT, null);
        
        // Make sure this user has an unique device identifier.
        if (!newAccount.equals(oldAccount)) {
            prefsEditor.putString(SP_KEY_DEVICE_ID,
                DeviceUtils.getDeviceId(fragment.getActivity(), newAccount));
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        }
        
        prefsEditor.putString(SP_KEY_ACCOUNT, newAccount);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        
        final NetworkClient client = NetworkClient.newInstance(fragment.getActivity());
        
        int authResult = AUTH_FAIL;
        if (client != null) {
            final JSONObject data = new JSONObject();
            
            try {
                data.put("name", prefs.getString(SP_KEY_DEVICE_NAME, null));
                data.put("c2dm", prefs.getString(SP_KEY_DEVICE_C2DM, null));
                client.put("/devices/" + client.getDeviceId(), data);
                authResult = AUTH_OK;
            } catch (AppEngineAuthenticationException e) {
                if (e.isAuthenticationPending()) {
                    authPendingIntent = e.getPendingAuthenticationPermissionActivity();
                    authResult = AUTH_PENDING;
                }
                Log.w(TAG, "Failed to authenticate account", e);
            } catch (IOException e) {
                Log.w(TAG, "Failed to check account availability", e);
            } catch (JSONException e) {
                Log.w(TAG, "JSON error", e);
            } finally {
                client.close();
            }
        }
        
        if (AUTH_OK == authResult) {
            if (!newAccount.equals(oldAccount)) {
                // The user is different: clear events.
                contentResolver.delete(EventsContract.CONTENT_URI, null, null);
            }
            
            prefsEditor.putString(SP_KEY_ACCOUNT, newAccount);
            
            // Enable synchronization only for our user.
            for (final Account account : accounts) {
                final boolean syncable = account.name.equals(newAccount);
                ContentResolver.setIsSyncable(account, EventsContract.AUTHORITY, syncable ? 1 : 0);
            }
            ContentResolver.setSyncAutomatically(new Account(newAccount, GOOGLE_ACCOUNT),
                EventsContract.AUTHORITY, true);
            
            // Start synchronization.
            EventsContract.sync(newAccount, EventsContract.FULL_SYNC);
        } else {
            // Restore old account.
            prefsEditor.putString(SP_KEY_ACCOUNT, oldAccount);
        }
        
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        
        return authResult;
    }
    
    protected void onAuthenticationSuccess() {
    }
    
    protected void onAuthenticationPending(Intent authPendingIntent) {
    }
    
    protected void onAuthenticationError() {
    }
    
    @Override
    protected void onPostExecute(Integer result) {
        try {
            switch (result) {
                case AUTH_PENDING:
                    onAuthenticationPending(authPendingIntent);
                    break;
                case AUTH_FAIL:
                    onAuthenticationError();
                    break;
                case AUTH_OK:
                    onAuthenticationSuccess();
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to handle authentication result: " + result, e);
        }
    }
}
