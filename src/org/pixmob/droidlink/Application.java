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
package org.pixmob.droidlink;

import static org.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static org.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static org.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;

import org.pixmob.droidlink.feature.Features;
import org.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import org.pixmob.droidlink.feature.StrictModeFeature;
import org.pixmob.droidlink.service.DeviceInitService;
import org.pixmob.droidlink.service.EventPurgeService;
import org.pixmob.droidlink.util.Accounts;

import android.accounts.Account;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;


/**
 * Application entry point.
 * @author Pixmob
 */
public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        
        if (DEVELOPER_MODE) {
            // Enable StrictMode if it's available.
            Features.getFeature(StrictModeFeature.class).enable();
        }
        
        // If there is only one account, select it.
        final Account[] accounts = Accounts.list(this);
        if (accounts.length == 1) {
            final SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCES_FILE,
                MODE_PRIVATE);
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putString(SP_KEY_ACCOUNT, accounts[0].name);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        }
        
        // Make sure a device id is generated for this device.
        startService(new Intent(this, DeviceInitService.class));
        
        // Purge older events every hour.
        final AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.RTC, 0, AlarmManager.INTERVAL_HOUR, PendingIntent
                .getService(this, 0, new Intent(this, EventPurgeService.class),
                    PendingIntent.FLAG_CANCEL_CURRENT));
    }
}
