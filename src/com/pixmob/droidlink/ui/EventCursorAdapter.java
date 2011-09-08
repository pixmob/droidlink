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

import static android.provider.BaseColumns._ID;
import static com.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.provider.EventsContract.Event.MESSAGE;
import static com.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static com.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static com.pixmob.droidlink.provider.EventsContract.Event.STATE;
import static com.pixmob.droidlink.provider.EventsContract.Event.TYPE;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.provider.EventsContract;

/**
 * Data source for device events.
 * @author Pixmob
 */
class EventCursorAdapter extends SimpleCursorAdapter {
    public static final int TAG_ID = R.id.event_date;
    private static final int[] TO = new int[] { R.id.event_name, R.id.event_number,
            R.id.event_date, R.id.event_message };
    private static final String[] FROM = new String[] { NAME, NUMBER, CREATED, MESSAGE };
    private static final Map<Integer, Integer> EVENT_ICONS = new HashMap<Integer, Integer>(2);
    static {
        EVENT_ICONS.put(EventsContract.MISSED_CALL_TYPE, R.drawable.ic_missed_call);
        EVENT_ICONS.put(EventsContract.RECEIVED_SMS_TYPE, R.drawable.ic_sms_mms);
    }
    
    public EventCursorAdapter(Context context, Cursor c) {
        super(context, R.layout.event_row, c, FROM, TO, 0);
    }
    
    @Override
    public void bindView(View v, Context context, Cursor cursor) {
        final int state = cursor.getInt(cursor.getColumnIndexOrThrow(STATE));
        final String name = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
        final String number = cursor.getString(cursor.getColumnIndexOrThrow(NUMBER));
        final long date = cursor.getLong(cursor.getColumnIndexOrThrow(CREATED));
        
        final int type = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
        final Integer typeResourceId = EVENT_ICONS.get(type);
        
        final CharSequence eventDate = DateUtils.getRelativeTimeSpanString(date, System
                .currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
        
        final String message = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE));
        
        final String eventName;
        final String eventNumber;
        if (number == null) {
            eventName = context.getString(R.string.unknown_number);
            eventNumber = null;
        } else if (name == null) {
            eventName = number;
            eventNumber = null;
        } else {
            eventName = name;
            eventNumber = number;
        }
        
        TextView tv = (TextView) v.findViewById(R.id.event_name);
        tv.setText(eventName);
        
        tv = (TextView) v.findViewById(R.id.event_number);
        tv.setText(eventNumber);
        
        tv = (TextView) v.findViewById(R.id.event_date);
        tv.setText(eventDate);
        
        tv = (TextView) v.findViewById(R.id.event_message);
        tv.setText(message);
        
        ImageView iv = (ImageView) v.findViewById(R.id.event_icon);
        if (typeResourceId != null) {
            iv.setImageResource(typeResourceId);
            iv.setVisibility(View.VISIBLE);
        } else {
            iv.setVisibility(View.INVISIBLE);
        }
        
        iv = (ImageView) v.findViewById(R.id.event_pending_delete);
        iv.setVisibility(EventsContract.PENDING_DELETE_STATE == state ? View.VISIBLE : View.GONE);
        
        v.setTag(TAG_ID, cursor.getString(cursor.getColumnIndex(_ID)));
    }
    
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.event_row, parent, false);
    }
}
