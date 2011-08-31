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
import static com.pixmob.droidlink.Constants.SERVER_HOST;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_SYNC_REQUIRED;
import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.droidlink.features.Features;
import com.pixmob.droidlink.features.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.providers.EventsContract;
import com.pixmob.droidlink.services.DeviceInitService;
import com.pixmob.droidlink.util.Accounts;

/**
 * Check an account and register a device.
 * @author Pixmon.
 */
public class LoginTask extends AsyncTask<String, Void, Integer> {
    private static final int AUTH_OK = 0;
    private static final int AUTH_FAIL = 1;
    private static final int AUTH_PENDING = 2;
    private final Context context;
    private Intent authPendingIntent;
    
    public LoginTask(final Context context) {
        this.context = context.getApplicationContext();
    }
    
    @Override
    protected Integer doInBackground(String... params) {
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE);
        final String newAccount = params[0];
        final String oldAccount = prefs.getString(SP_KEY_ACCOUNT, null);
        
        final SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putString(SP_KEY_ACCOUNT, newAccount);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        
        final NetworkClient client = NetworkClient.newInstance(context);
        int authResult = AUTH_FAIL;
        if (client != null) {
            try {
                client.execute(new HttpGet("https://" + SERVER_HOST));
                authResult = AUTH_OK;
            } catch (AppEngineAuthenticationException e) {
                if (e.isAuthenticationPending()) {
                    authPendingIntent = e.getPendingAuthenticationPermissionActivity();
                    authResult = AUTH_PENDING;
                }
                Log.w(TAG, "Failed to authenticate account", e);
            } catch (IOException e) {
                Log.i(TAG, "Failed to check account availability", e);
            } finally {
                client.close();
            }
        }
        
        if (AUTH_OK == authResult) {
            if (oldAccount != null && !newAccount.equals(oldAccount)) {
                // The user is different: clear events.
                final ContentValues cv = new ContentValues(1);
                cv.put(EventsContract.Event.STATE, EventsContract.PENDING_DELETE_STATE);
                context.getContentResolver().update(EventsContract.CONTENT_URI, cv, null, null);
            }
            
            prefsEditor.putBoolean(SP_KEY_DEVICE_SYNC_REQUIRED, true);
            prefsEditor.putString(SP_KEY_ACCOUNT, newAccount);
            
            // Enable synchronization only for our user.
            for (final Account account : Accounts.list(context)) {
                final boolean syncable = account.name.equals(newAccount);
                context.getContentResolver();
                ContentResolver.setIsSyncable(account, EventsContract.AUTHORITY, syncable ? 1 : 0);
            }
            ContentResolver.setSyncAutomatically(new Account(newAccount, GOOGLE_ACCOUNT),
                EventsContract.AUTHORITY, true);
            
            // Start synchronization.
            ContentResolver.requestSync(new Account(newAccount, GOOGLE_ACCOUNT),
                EventsContract.AUTHORITY, new Bundle());
            
            // Make sure a device is initialized for this user.
            context.startService(new Intent(context, DeviceInitService.class));
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
