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
package com.pixmob.droidlink;

import static com.pixmob.droidlink.Constants.ACTION_INIT;
import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import android.content.Intent;

import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.StrictModeFeature;

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
        
        // Make sure a device id is generated for this device.
        startService(new Intent(ACTION_INIT));
    }
}
