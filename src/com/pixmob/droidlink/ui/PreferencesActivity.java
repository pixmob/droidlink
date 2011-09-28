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

import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_EVENT_MAX_AGE;
import static com.pixmob.droidlink.Constants.SP_KEY_IGNORE_MISSED_CALLS;
import static com.pixmob.droidlink.Constants.SP_KEY_IGNORE_RECEIVED_SMS;
import static com.pixmob.droidlink.Constants.TAG;

import java.util.LinkedHashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.service.EventPurgeService;

/**
 * Application preferences.
 * @author Pixmob
 */
public class PreferencesActivity extends PreferenceActivity implements OnPreferenceClickListener {
    private static final String DELETE_EVENTS_AFTER_PREF = "deleteEventsAfter";
    private static final String DELETE_DATA_PREF = "deleteData";
    private static final int DELETE_DATA_CONFIRM_DIALOG = 1;
    private static final int DELETE_DATA_PROGRESS_DIALOG = 2;
    private final Map<CharSequence, Long> durations = new LinkedHashMap<CharSequence, Long>(5);
    private SharedPreferences prefs;
    private DeleteDataTask deleteDataTask;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final long oneDay = 86400;
        durations.clear();
        durations.put(getString(R.string.one_day), oneDay);
        durations.put(getString(R.string.three_days), oneDay * 3);
        durations.put(getString(R.string.one_week), oneDay * 7);
        durations.put(getString(R.string.two_weeks), oneDay * 7 * 2);
        durations.put(getString(R.string.one_month), oneDay * 30);
        
        deleteDataTask = (DeleteDataTask) getLastNonConfigurationInstance();
        if (deleteDataTask != null) {
            deleteDataTask.activity = this;
        }
        
        final PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesMode(Context.MODE_PRIVATE);
        pm.setSharedPreferencesName(SHARED_PREFERENCES_FILE);
        
        addPreferencesFromResource(R.xml.preferences);
        prefs = pm.getSharedPreferences();
        
        final Preference purgeEventsPref = pm.findPreference(DELETE_DATA_PREF);
        purgeEventsPref.setOnPreferenceClickListener(this);
        
        final ListPreference deleteEventsAfterPrefs = (ListPreference) pm
                .findPreference(DELETE_EVENTS_AFTER_PREF);
        final CharSequence[] durationKeys = durations.keySet().toArray(
            new CharSequence[durations.size()]);
        deleteEventsAfterPrefs.setDefaultValue(durations.get(getString(R.string.one_week)));
        deleteEventsAfterPrefs.setEntries(durationKeys);
        deleteEventsAfterPrefs.setEntryValues(durationKeys);
        deleteEventsAfterPrefs.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // Save the real value (in seconds) in the preferences.
                final SharedPreferences.Editor prefsEditor = prefs.edit();
                prefsEditor.putLong(SP_KEY_EVENT_MAX_AGE, durations.get(newValue));
                Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
                
                // Update local events with the new settings.
                startService(new Intent(PreferencesActivity.this, EventPurgeService.class));
                
                return true;
            }
        });
        
        // Disable some preferences if this device is actually not a phone.
        final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        final boolean deviceIsPhone = tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        if (!deviceIsPhone) {
            // This device is NOT a phone.
            pm.findPreference(SP_KEY_IGNORE_MISSED_CALLS).setEnabled(false);
            pm.findPreference(SP_KEY_IGNORE_RECEIVED_SMS).setEnabled(false);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return deleteDataTask;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        final String account = prefs.getString(SP_KEY_ACCOUNT, null);
        final Preference deleteDataPref = getPreferenceManager().findPreference(DELETE_DATA_PREF);
        deleteDataPref.setEnabled(account != null);
    }
    
    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (DELETE_DATA_PREF.equals(preference.getKey())) {
            showDialog(DELETE_DATA_CONFIRM_DIALOG);
            return true;
        }
        
        return false;
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        if (DELETE_DATA_CONFIRM_DIALOG == id) {
            return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.delete_data).setMessage(R.string.confirm_delete_data)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            deleteDataTask = new DeleteDataTask();
                            deleteDataTask.activity = PreferencesActivity.this;
                            deleteDataTask.execute();
                        }
                    }).setNegativeButton(android.R.string.cancel, null).create();
        }
        if (DELETE_DATA_PROGRESS_DIALOG == id) {
            final ProgressDialog dialog = new ProgressDialog(this);
            dialog.setMessage(getString(R.string.deleting_data));
            dialog.setCancelable(false);
            return dialog;
        }
        
        return super.onCreateDialog(id);
    }
    
    /**
     * Internal task for deleting user events.
     * @author Pixmob
     */
    private static class DeleteDataTask extends AsyncTask<Void, Void, Void> {
        public PreferencesActivity activity;
        
        @Override
        protected Void doInBackground(Void... params) {
            try {
                Log.i(TAG, "Delete data");
                
                final NetworkClient client = NetworkClient.newInstance(activity);
                if (client != null) {
                    client.delete("/events/all");
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete data", e);
            }
            
            return null;
        }
        
        @Override
        protected void onPreExecute() {
            activity.showDialog(DELETE_DATA_PROGRESS_DIALOG);
        }
        
        @Override
        protected void onPostExecute(Void result) {
            activity.dismissDialog(DELETE_DATA_PROGRESS_DIALOG);
        }
        
        @Override
        protected void onCancelled() {
            activity.dismissDialog(DELETE_DATA_PROGRESS_DIALOG);
        }
    }
}
