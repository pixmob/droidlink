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
import static com.pixmob.droidlink.Constants.SP_KEY_IGNORE_MISSED_CALLS;
import static com.pixmob.droidlink.Constants.SP_KEY_IGNORE_RECEIVED_SMS;
import static com.pixmob.droidlink.Constants.TAG;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.provider.EventsContract;

/**
 * Application preferences.
 * @author Pixmob
 */
public class PreferencesActivity extends PreferenceActivity implements OnPreferenceClickListener {
    private static final String DELETE_DATA_PREF = "deleteData";
    private static final String USER_ACCOUNT_PREF = "switchUserAccount";
    private static final int DELETE_DATA_CONFIRM_DIALOG = 1;
    private SharedPreferences prefs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        final PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesMode(Context.MODE_PRIVATE);
        pm.setSharedPreferencesName(SHARED_PREFERENCES_FILE);
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            // Use a regular preference screen for Android systems < Honeycomb.
            addPreferencesFromResource(R.xml.preferences);
            configure(this);
        } else {
            // Use a fragment for Honeycomb+ systems.
            PreferencesInitializer.init(this);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        final Preference userAccountPref = getPreferenceManager().findPreference(USER_ACCOUNT_PREF);
        userAccountPref.setSummary(prefs.getString(SP_KEY_ACCOUNT, null));
    }
    
    private static void configure(PreferencesActivity activity) {
        final PreferenceManager pm = activity.getPreferenceManager();
        activity.prefs = pm.getSharedPreferences();
        
        final Preference purgeEventsPref = pm.findPreference(DELETE_DATA_PREF);
        purgeEventsPref.setOnPreferenceClickListener(activity);
        
        final Preference userAccountPref = pm.findPreference(USER_ACCOUNT_PREF);
        userAccountPref.setOnPreferenceClickListener(activity);
        
        // Disable some preferences if this device is actually not a phone.
        final TelephonyManager tm = (TelephonyManager) activity
                .getSystemService(Context.TELEPHONY_SERVICE);
        final boolean deviceIsPhone = tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        if (!deviceIsPhone) {
            // This device is NOT a phone.
            pm.findPreference(SP_KEY_IGNORE_MISSED_CALLS).setEnabled(false);
            pm.findPreference(SP_KEY_IGNORE_RECEIVED_SMS).setEnabled(false);
        }
    }
    
    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (DELETE_DATA_PREF.equals(preference.getKey())) {
            showDialog(DELETE_DATA_CONFIRM_DIALOG);
            return true;
        }
        if (USER_ACCOUNT_PREF.equals(preference.getKey())) {
            startActivity(new Intent(this, AccountsActivity.class));
            return true;
        }
        
        return false;
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        if (DELETE_DATA_CONFIRM_DIALOG == id) {
            return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle(R.string.delete_data).setMessage(R.string.confirm_delete_data)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final String account = prefs.getString(SP_KEY_ACCOUNT, null);
                            if (account != null) {
                                new EventPurgeTask(account, getContentResolver()).start();
                            }
                        }
                    }).setNegativeButton(android.R.string.cancel, null).create();
        }
        
        return super.onCreateDialog(id);
    }
    
    /**
     * Internal task for updating events for deletion.
     * @author Pixmob
     */
    private static class EventPurgeTask extends Thread {
        private final String account;
        private final ContentResolver contentResolver;
        
        public EventPurgeTask(final String account, final ContentResolver contentResolver) {
            super("DroidLink/EventPurge");
            this.account = account;
            this.contentResolver = contentResolver;
        }
        
        @Override
        public void run() {
            try {
                doRun();
            } catch (Exception e) {
                Log.w(TAG, "Event purge error", e);
            }
        }
        
        private void doRun() throws Exception {
            Log.i(TAG, "Delete data");
            
            // Events are updated for deletion.
            final ContentValues values = new ContentValues();
            values.put(EventsContract.Event.STATE, EventsContract.PENDING_DELETE_STATE);
            contentResolver.update(EventsContract.CONTENT_URI, values, null, null);
            
            // The deletion is done when the synchronization is started.
            EventsContract.sync(account, EventsContract.LIGHT_SYNC);
        }
    }
    
    /**
     * Internal class for setting a fragment for preferences.
     * @author Pixmob
     */
    private static class PreferencesInitializer {
        public static void init(PreferencesActivity activity) {
            final ApplicationPreferencesFragment fragment = new ApplicationPreferencesFragment();
            final FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
            ft.add(fragment, "preferences");
            ft.commit();
            
            PreferencesInitializer.init(activity);
        }
    }
    
    /**
     * {@link Fragment} for application preferences.
     * @author Pixmob
     */
    public static class ApplicationPreferencesFragment extends PreferenceFragment {
        public ApplicationPreferencesFragment() {
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}
