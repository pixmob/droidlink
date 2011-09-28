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
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.TAG;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.util.Log;

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
    
    private static final ThreadPoolExecutor SYNC_EXECUTOR = new ThreadPoolExecutor(0, 1, 60,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "DroidLink/Sync");
                }
            });
    static {
        SYNC_EXECUTOR.allowCoreThreadTimeOut(true);
    }
    
    /**
     * Synchronization token. This key is set from the request sent by the
     * server to uniquely identify synchronization requests across devices.
     */
    public static final String SYNC_TOKEN = "syncToken";
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
    public static void sync(Context context, int syncType, String syncToken) {
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE);
        final String account = prefs.getString(SP_KEY_ACCOUNT, null);
        if (account == null) {
            Log.w(TAG, "No account set: cannot sync");
            return;
        }
        if (syncType != FULL_SYNC && syncType != LIGHT_SYNC) {
            throw new IllegalArgumentException("Invalid sync type: " + syncType);
        }
        
        // Start event synchronization in a thread to avoid disk writes in the
        // main thread.
        SYNC_EXECUTOR.execute(new EventsRefresher(account, syncType, syncToken));
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
    
    /**
     * Internal task for refreshing events.
     * @author Pixmob
     */
    private static class EventsRefresher implements Runnable {
        private final Logger logger = Logger.getLogger(getClass().getName());
        private final String account;
        private final int syncType;
        private final String syncToken;
        
        public EventsRefresher(final String account, final int syncType, final String syncToken) {
            this.account = account;
            this.syncType = syncType;
            this.syncToken = syncToken;
        }
        
        @Override
        public void run() {
            try {
                final Bundle options = new Bundle();
                options.putInt(EventsContract.SYNC_STRATEGY, syncType);
                options.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                if (syncToken != null) {
                    options.putString(EventsContract.SYNC_TOKEN, syncToken);
                }
                ContentResolver.requestSync(new Account(account, GOOGLE_ACCOUNT),
                    EventsContract.AUTHORITY, options);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to sync events", e);
            }
        }
    }
}
