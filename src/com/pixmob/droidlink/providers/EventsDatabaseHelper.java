package com.pixmob.droidlink.providers;

import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.providers.EventsContentProvider.EVENTS_TABLE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_DATE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_DEVICE_ID;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NAME;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_FROM_NUMBER;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_ID;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_MESSAGE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_TYPE;
import static com.pixmob.droidlink.providers.EventsContentProvider.KEY_UPLOAD_TIME;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

import com.pixmob.droidlink.Constants;

/**
 * Helper class for managing the application database.
 * @author Pixmob
 */
class EventsDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_CREATE = "CREATE TABLE " + EVENTS_TABLE + " (" + KEY_ID
            + " TEXT PRIMARY KEY, " + KEY_DEVICE_ID + " TEXT, " + KEY_DATE + " LONG, "
            + KEY_TYPE + " INT, " + KEY_FROM_NUMBER + " TEXT, " + KEY_FROM_NAME + " TEXT, "
            + KEY_MESSAGE + " TEXT, " + KEY_UPLOAD_TIME + " LONG);";
    
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
            cv.put(KEY_DEVICE_ID, deviceId);
            cv.put(KEY_DATE, System.currentTimeMillis());
            cv.put(KEY_FROM_NUMBER, "1234567890");
            cv.put(KEY_FROM_NAME, "John Doe");
            cv.put(KEY_MESSAGE, "Hello world!");
            cv.put(KEY_TYPE, Constants.SMS_EVENT_TYPE);
            db.insert(EVENTS_TABLE, "not_null", cv);
            
            cv = new ContentValues();
            cv.put(KEY_DEVICE_ID, deviceId);
            cv.put(KEY_DATE, System.currentTimeMillis() - 100000);
            cv.put(KEY_TYPE, Constants.MISSED_CALL_EVENT);
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
