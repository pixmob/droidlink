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

/**
 * Application constants.
 * @author Pixmob
 */
public final class Constants {
    /**
     * Server host.
     */
    public static final String SERVER_HOST = "mydroidlink.appspot.com";
    /**
     * HTTP User Agent.
     */
    public static final String USER_AGENT = "Droid Link";
    /**
     * Remote API version.
     */
    public static final int REMOTE_API_VERSION = 1;
    
    /**
     * Set to <code>true</code> to enable development mode. When a release is
     * being built, make sure this flag is set to <code>false</code>.
     */
    public static final boolean DEVELOPER_MODE = true;
    
    /**
     * Use this tag for every logging statements.
     */
    public static final String TAG = "DroidLink";
    
    public static final String SHARED_PREFERENCES_FILE = "sharedprefs";
    public static final String SP_KEY_ACCOUNT = "account";
    public static final String SP_KEY_ENABLED = "enabled";
    public static final String SP_KEY_LAST_CALL_STATE = "lastCallState";
    public static final String SP_KEY_DEVICE_ID = "deviceId";
    public static final String SP_KEY_DEVICE_NAME = "deviceName";
    public static final String SP_KEY_DEVICE_C2DM = "deviceC2dm";
    public static final String SP_KEY_DEVICE_SYNC_REQUIRED = "deviceSyncRequired";
    public static final String SP_KEY_LAST_SYNC = "lastSync";
    public static final String SP_KEY_FULL_SYNC = "syncRequired";
    
    public static final String C2DM_SENDER_ID = "pixmobstudio@gmail.com";
    public static final String C2DM_MESSAGE_EXTRA = "message";
    public static final String C2DM_MESSAGE_SYNC = "sync";
    public static final String C2DM_ACCOUNT_EXTRA = "account";
    
    public static final String ACTION_LOGIN = "com.pixmob.droidlink.login";
    public static final String EXTRA_ACCOUNT = "account";
    
    /**
     * Google account type.
     */
    public static final String GOOGLE_ACCOUNT = "com.google";
    
    /**
     * When opening the application, if the last sync time is over this limit, a
     * synchronization is started.
     */
    public static final long MAX_SYNC_AGE = 1000 * 60 * 30;
    
    private Constants() {
    }
}
