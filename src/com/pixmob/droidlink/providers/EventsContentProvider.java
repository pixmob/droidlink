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
package com.pixmob.droidlink.providers;

import static com.pixmob.droidlink.Constants.TAG;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * Content provider for the events database.
 * @author Pixmob
 */
public class EventsContentProvider extends ContentProvider {
    public static final String KEY_ID = "_id";
    public static final String KEY_DATE = "creationtime";
    public static final String KEY_DEVICE_ID = "deviceid";
    public static final String KEY_TYPE = "type";
    public static final String KEY_FROM_NUMBER = "fromnumber";
    public static final String KEY_FROM_NAME = "fromname";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_UPLOAD_TIME = "uploadtime";
    static final String EVENTS_TABLE = "events";
    
    public static final Uri CONTENT_URI = Uri
            .parse("content://com.pixmob.droidlink.provider/events");
    
    private static final String DATABASE_NAME = "events.db";
    private static final int DATABASE_VERSION = 1;
    private static final int EVENTS = 1;
    private static final int EVENT_ID = 2;
    private static final String[] NOT_NULL_COLUMNS = { KEY_FROM_NUMBER, KEY_TYPE, KEY_DEVICE_ID };
    
    private static final UriMatcher URI_MATCHER;
    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI("com.pixmob.droidlink.provider", "events", EVENTS);
        URI_MATCHER.addURI("com.pixmob.droidlink.provider", "events/*", EVENT_ID);
    }
    
    private SQLiteDatabase db;
    
    @Override
    public boolean onCreate() {
        final EventsDatabaseHelper dbHelper = new EventsDatabaseHelper(getContext(), DATABASE_NAME,
                null, DATABASE_VERSION);
        try {
            db = dbHelper.getWritableDatabase();
        } catch (SQLiteException e) {
            Log.e(TAG, "Database opening error", e);
        }
        
        return db != null;
    }
    
    @Override
    public String getType(Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case EVENTS:
                return "vnd.android.cursor.dir/vnd.pixmob.droidlink.event";
            case EVENT_ID:
                return "vnd.android.cursor.item/vnd.pixmob.droidlink.event";
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
    }
    
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(EVENTS_TABLE);
        
        switch (URI_MATCHER.match(uri)) {
            case EVENT_ID:
                qb.appendWhere(KEY_ID + "=" + uri.getPathSegments().get(1) + "'");
                break;
            default:
                break;
        }
        
        final String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = KEY_DATE + " DESC";
        } else {
            orderBy = sortOrder;
        }
        
        final Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        
        return c;
    }
    
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final int count;
        
        switch (URI_MATCHER.match(uri)) {
            case EVENTS:
                count = db.delete(EVENTS_TABLE, selection, selectionArgs);
                break;
            case EVENT_ID:
                final String id = uri.getPathSegments().get(1);
                String fullSelection = KEY_ID + "=" + id;
                if (!TextUtils.isEmpty(selection)) {
                    fullSelection += " AND (" + selection + ")";
                }
                count = db.delete(EVENTS_TABLE, fullSelection, selectionArgs);
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
    
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        for (final String notNullColumn : NOT_NULL_COLUMNS) {
            if (!values.containsKey(notNullColumn)) {
                throw new SQLException("Missing value for " + notNullColumn);
            }
        }
        if (!values.containsKey(KEY_DATE)) {
            values.put(KEY_DATE, System.currentTimeMillis());
        }
        if (values.containsKey(KEY_FROM_NAME)) {
            final String name = values.getAsString(KEY_FROM_NAME);
            if (TextUtils.isEmpty(name)) {
                values.remove(KEY_FROM_NAME);
            }
        }
        if (values.containsKey(KEY_FROM_NUMBER)) {
            final String number = values.getAsString(KEY_FROM_NUMBER);
            if (TextUtils.isEmpty(number)) {
                values.remove(KEY_FROM_NUMBER);
            }
        }
        
        final long rowID = db.insert(EVENTS_TABLE, "not_null", values);
        if (rowID == -1) {
            throw new SQLException("Failed to insert row into " + uri);
        }
        
        final Uri itemUri = ContentUris.withAppendedId(CONTENT_URI, rowID);
        getContext().getContentResolver().notifyChange(itemUri, null);
        return itemUri;
    }
    
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final int count;
        switch (URI_MATCHER.match(uri)) {
            case EVENTS:
                count = db.update(EVENTS_TABLE, values, selection, selectionArgs);
                break;
            case EVENT_ID:
                final String id = uri.getPathSegments().get(1);
                String fullSelection = KEY_ID + "=" + id;
                if (!TextUtils.isEmpty(selection)) {
                    fullSelection += " AND (" + selection + ")";
                }
                count = db.update(EVENTS_TABLE, values, fullSelection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Uri: " + uri);
        }
        
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
