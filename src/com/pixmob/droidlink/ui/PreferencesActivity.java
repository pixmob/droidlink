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

import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;

import java.util.Arrays;

import android.accounts.Account;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.util.Accounts;

/**
 * Application preferences.
 * @author Pixmob
 */
public class PreferencesActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        
        final Account[] accounts = Accounts.list(this);
        final String[] accountNames = new String[accounts.length];
        for (int i = 0; i < accounts.length; ++i) {
            accountNames[i] = accounts[i].name;
        }
        Arrays.sort(accountNames);
        
        final PreferenceManager prefManager = getPreferenceManager();
        final ListPreference accountPref = (ListPreference) prefManager
                .findPreference(SP_KEY_ACCOUNT);
        accountPref.setEntries(accountNames);
        accountPref.setEntryValues(accountNames);
    }
}
