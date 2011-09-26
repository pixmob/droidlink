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
package com.pixmob.droidlink.util;

import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;

import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;

import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;

/**
 * Device utilities.
 * @author Pixmob
 */
public final class DeviceUtils {
    private DeviceUtils() {
    }
    
    /**
     * Get the device identifier for this user. A new identifier is generated if
     * this user never got an identifier. The same identifier is reused for each
     * user.
     */
    public static String getDeviceId(Context context, String user) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE);
        
        // A device identifier is generated for each user. Once an
        // identifier is given to an user, the same identifier is reused
        // when the same user is selected again.
        final String idKey = "user-" + user;
        
        String deviceId = prefs.getString(idKey, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString();
            
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putString(idKey, deviceId);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        }
        
        return deviceId;
    }
}
