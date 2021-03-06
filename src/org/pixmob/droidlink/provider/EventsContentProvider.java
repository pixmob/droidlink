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
package org.pixmob.droidlink.provider;

import static android.provider.BaseColumns._ID;
import static org.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static org.pixmob.droidlink.Constants.TAG;
import static org.pixmob.droidlink.provider.EventsContract.Event.CONTENT_ITEM_TYPE;
import static org.pixmob.droidlink.provider.EventsContract.Event.CONTENT_TYPE;
import static org.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static org.pixmob.droidlink.provider.EventsContract.Event.DEFAULT_SORT_ORDER;
import static org.pixmob.droidlink.provider.EventsContract.Event.DEVICE_ID;
import static org.pixmob.droidlink.provider.EventsContract.Event.MESSAGE;
import static org.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static org.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static org.pixmob.droidlink.provider.EventsContract.Event.STATE;
import static org.pixmob.droidlink.provider.EventsContract.Event.TYPE;

import java.util.ArrayList;
import java.util.UUID;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * Content provider for the events database.
 * @author Pixmob
 */
public class EventsContentProvider extends ContentProvider {
    private static final String DATABASE_NAME = "events.db";
    private static final int DATABASE_VERSION = 2;
    private static final String EVENTS_TABLE = "events";
    private static final String[] NOT_NULL_COLUMNS = { TYPE, DEVICE_ID };
    
    private static final int EVENTS = 1;
    private static final int EVENT_ID = 2;
    
    private static final UriMatcher URI_MATCHER;
    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(EventsContract.AUTHORITY, "events", EVENTS);
        URI_MATCHER.addURI(EventsContract.AUTHORITY, "events/*", EVENT_ID);
    }
    
    private SQLiteOpenHelper dbHelper;
    
    @Override
    public boolean onCreate() {
        dbHelper = new EventsDatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION);
        return true;
    }
    
    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case EVENTS:
                return CONTENT_TYPE;
            case EVENT_ID:
                return CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
    }
    
    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        if (operations.isEmpty()) {
            return new ContentProviderResult[0];
        }
        
        // Execute batch operations in a single transaction for performance.
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                results[i] = operations.get(i).apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
        }
    }
    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(EVENTS_TABLE);
        
        switch (URI_MATCHER.match(uri)) {
            case EVENT_ID:
                qb.appendWhere(_ID + "='" + uri.getPathSegments().get(1) + "'");
                break;
            default:
                break;
        }
        
        final String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }
        
        final SQLiteDatabase db = dbHelper.getReadableDatabase();
        final Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        
        return c;
    }
    
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int count;
        
        switch (URI_MATCHER.match(uri)) {
            case EVENTS:
                count = db.delete(EVENTS_TABLE, selection, selectionArgs);
                break;
            case EVENT_ID:
                final String id = uri.getPathSegments().get(1);
                String fullSelection = _ID + "='" + id + "'";
                if (!TextUtils.isEmpty(selection)) {
                    fullSelection += " AND (" + selection + ")";
                }
                count = db.delete(EVENTS_TABLE, fullSelection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
    
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        for (final String notNullColumn : NOT_NULL_COLUMNS) {
            if (!values.containsKey(notNullColumn)) {
                throw new SQLException("Missing value for " + notNullColumn);
            }
        }
        if (!values.containsKey(_ID)) {
            values.put(_ID, UUID.randomUUID().toString());
        }
        if (!values.containsKey(CREATED)) {
            values.put(CREATED, System.currentTimeMillis());
        }
        if (!values.containsKey(STATE)) {
            values.put(STATE, Integer.valueOf(EventsContract.PENDING_UPLOAD_STATE));
        }
        if (values.containsKey(NAME)) {
            final String name = values.getAsString(NAME);
            if (TextUtils.isEmpty(name)) {
                values.remove(NAME);
            }
        }
        if (values.containsKey(NUMBER)) {
            final String number = values.getAsString(NUMBER);
            if (TextUtils.isEmpty(number)) {
                values.remove(NUMBER);
            }
        }
        
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final long rowID = db.insert(EVENTS_TABLE, "not_null", values);
        if (rowID == -1) {
            throw new SQLException("Failed to insert row into " + uri);
        }
        
        final Uri itemUri = Uri.withAppendedPath(EventsContract.CONTENT_URI, values
                .getAsString(_ID));
        getContext().getContentResolver().notifyChange(itemUri, null, false);
        return itemUri;
    }
    
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final int count;
        switch (URI_MATCHER.match(uri)) {
            case EVENTS:
                count = db.update(EVENTS_TABLE, values, selection, selectionArgs);
                break;
            case EVENT_ID:
                final String id = uri.getPathSegments().get(1);
                String fullSelection = _ID + "='" + id + "'";
                if (!TextUtils.isEmpty(selection)) {
                    fullSelection += " AND (" + selection + ")";
                }
                count = db.update(EVENTS_TABLE, values, fullSelection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
    
    /**
     * Helper class for managing the application database.
     * @author Pixmob
     */
    static class EventsDatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_CREATE = "CREATE TABLE " + EVENTS_TABLE + " (" + _ID
                + " TEXT PRIMARY KEY, " + DEVICE_ID + " TEXT, " + CREATED + " LONG, " + TYPE
                + " INT, " + NUMBER + " TEXT, " + NAME + " TEXT, " + MESSAGE + " TEXT, " + STATE
                + " INT);";
        
        public EventsDatabaseHelper(final Context context, final String name,
                final CursorFactory factory, final int version) {
            super(context, name, factory, version);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Creating database: " + DATABASE_CREATE);
            }
            db.execSQL(DATABASE_CREATE);
            
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Inserting sample data");
                
                final String deviceId = "12345";
                
                ContentValues cv = new ContentValues();
                cv.put(DEVICE_ID, deviceId);
                cv.put(CREATED, System.currentTimeMillis());
                cv.put(NUMBER, "1234567890");
                cv.put(NAME, "John Doe");
                cv.put(MESSAGE, "Hello world!");
                cv.put(TYPE, EventsContract.RECEIVED_SMS_TYPE);
                db.insert(EVENTS_TABLE, "not_null", cv);
                
                cv = new ContentValues();
                cv.put(DEVICE_ID, deviceId);
                cv.put(CREATED, System.currentTimeMillis() - 100000);
                cv.put(TYPE, EventsContract.MISSED_CALL_TYPE);
                db.insert(EVENTS_TABLE, "not_null", cv);
            }
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + EVENTS_TABLE);
            onCreate(db);
        }
    }
}
