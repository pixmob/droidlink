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
import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.providers.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.providers.EventsContract.Event.MESSAGE;
import static com.pixmob.droidlink.providers.EventsContract.Event.NAME;
import static com.pixmob.droidlink.providers.EventsContract.Event.NUMBER;
import static com.pixmob.droidlink.providers.EventsContract.Event.TYPE;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.pixmob.droidlink.Constants;
import com.pixmob.droidlink.R;

/**
 * Data source for device events.
 * @author Pixmob
 */
class EventCursorAdapter extends SimpleCursorAdapter implements OnClickListener {
    public static final int TAG_ID = R.id.event_date;
    private static final int TAG_NUMBER = R.id.event_number;
    private static final Map<Integer, Integer> EVENT_ICONS = new HashMap<Integer, Integer>(2);
    static {
        EVENT_ICONS.put(Constants.MISSED_CALL_EVENT, R.drawable.ic_missed_call);
        EVENT_ICONS.put(Constants.RECEIVED_SMS_EVENT_TYPE, R.drawable.ic_sms_mms);
    }
    private static Boolean deviceCanCall;
    private final boolean showMessage;
    
    public EventCursorAdapter(Context context, Cursor c) {
        super(
                context,
                R.layout.event_row,
                c,
                new String[] { NAME, NUMBER, CREATED, MESSAGE },
                new int[] { R.id.event_name, R.id.event_number, R.id.event_date, R.id.event_message });
        
        if (deviceCanCall == null) {
            // The icon for calling back a contact is hidden if the current
            // device is actually not a phone.
            final TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            deviceCanCall = tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        }
        
        showMessage = context.getResources().getBoolean(R.bool.show_event_message);
    }
    
    @Override
    public void bindView(View v, Context context, Cursor cursor) {
        final String name = cursor.getString(cursor.getColumnIndex(NAME));
        final String number = cursor.getString(cursor.getColumnIndex(NUMBER));
        final long date = cursor.getLong(cursor.getColumnIndex(CREATED));
        
        final int type = cursor.getInt(cursor.getColumnIndex(TYPE));
        final Integer typeResourceId = EVENT_ICONS.get(type);
        
        final CharSequence eventDate = DateUtils.getRelativeTimeSpanString(date, System
                .currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
        
        final String message = cursor.getString(cursor.getColumnIndex(MESSAGE));
        
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
        tv.setVisibility(showMessage ? View.VISIBLE : View.GONE);
        
        ImageView iv = (ImageView) v.findViewById(R.id.event_icon);
        if (typeResourceId != null) {
            iv.setImageResource(typeResourceId);
            iv.setVisibility(View.VISIBLE);
        } else {
            iv.setVisibility(View.INVISIBLE);
        }
        
        v.setTag(TAG_ID, cursor.getLong(cursor.getColumnIndex(_ID)));
        
        iv = (ImageView) v.findViewById(R.id.event_call_back);
        iv.setTag(TAG_NUMBER, number);
        iv.setOnClickListener(this);
        
        View divider = v.findViewById(R.id.vertical_divider);
        if (deviceCanCall) {
            iv.setVisibility(number != null ? View.VISIBLE : View.INVISIBLE);
            divider.setVisibility(View.VISIBLE);
        } else {
            iv.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
        }
    }
    
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(R.layout.event_row, parent, false);
    }
    
    @Override
    public void onClick(View v) {
        final String number = (String) v.getTag(EventCursorAdapter.TAG_NUMBER);
        if (number == null) {
            Log.w(TAG, "No number found for current event");
        } else {
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Opening dialer for " + number);
            }
            
            // Prefill the dialer with the contact number.
            final Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            v.getContext().startActivity(i);
        }
    }
}
