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
package com.pixmob.droidlink.provider;

import static com.pixmob.droidlink.Constants.GOOGLE_ACCOUNT;
import android.accounts.Account;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;

/**
 * The contract between the Events provider and applications.
 * @author Pixmob
 */
public class EventsContract {
    public static final String AUTHORITY = "com.pixmob.droidlink";
    public static final Uri CONTENT_URI = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
            .authority(AUTHORITY).appendPath("events").build();
    
    public static final int PENDING_UPLOAD_STATE = 0;
    public static final int UPLOADED_STATE = 1;
    public static final int PENDING_DELETE_STATE = 2;
    
    /**
     * Synchronization strategy: light or full. Set this key when calling
     * <code>ContentResolver.requestSync</code> to specify how the
     * synchronization should be done.
     */
    public static final String SYNC_STRATEGY = "syncStrategy";
    /**
     * Perform a "full" synchronization: remote events are synchronized.
     * @see #SYNC_STRATEGY
     */
    public static final int FULL_SYNC = 2;
    /**
     * Perform a "light" synchronization: remote events are not synchronized.
     * @see #SYNC_STRATEGY
     */
    public static final int LIGHT_SYNC = 4;
    
    /**
     * Event type for a missed call.
     */
    public static final int MISSED_CALL_TYPE = 0;
    /**
     * Event type for a SMS.
     */
    public static final int RECEIVED_SMS_TYPE = 1;
    
    /**
     * Synchronize events for an account.
     */
    public static void sync(String account, int syncType) {
        if (account == null) {
            throw new IllegalArgumentException("Account is required");
        }
        if (syncType != FULL_SYNC && syncType != LIGHT_SYNC) {
            throw new IllegalArgumentException("Invalid sync type: " + syncType);
        }
        
        final Bundle options = new Bundle();
        options.putInt(EventsContract.SYNC_STRATEGY, syncType);
        ContentResolver.requestSync(new Account(account, GOOGLE_ACCOUNT), EventsContract.AUTHORITY,
            options);
    }
    
    /**
     * Content type and column constants for the Events table.
     * @author Pixmob
     */
    public static class Event implements BaseColumns {
        /**
         * MIME type for a collection of events.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.pixmob.droidlink.event";
        /**
         * MIME type for a single event.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.pixmob.droidlink.event";
        /**
         * Default sort order for the Events table.
         */
        public static final String DEFAULT_SORT_ORDER = "created DESC";
        
        public static final String DEVICE_ID = "deviceid";
        public static final String CREATED = "created";
        public static final String TYPE = "type";
        public static final String NUMBER = "number";
        public static final String NAME = "name";
        public static final String MESSAGE = "message";
        public static final String STATE = "state";
    }
}
