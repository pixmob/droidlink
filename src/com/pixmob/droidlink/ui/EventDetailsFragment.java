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

import static com.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.provider.EventsContract.Event.MESSAGE;
import static com.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static com.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static com.pixmob.droidlink.provider.EventsContract.Event.TYPE;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.provider.EventsContract;

/**
 * Fragment for displaying event details.
 * @author Pixmob
 */
public class EventDetailsFragment extends Fragment {
    private static final String[] EVENT_PROJECTION = { CREATED, TYPE, NAME, NUMBER, MESSAGE };
    private static final Map<Integer, Integer> EVENT_ICONS = new HashMap<Integer, Integer>(2);
    static {
        EVENT_ICONS.put(EventsContract.MISSED_CALL_TYPE, R.drawable.ic_missed_call);
        EVENT_ICONS.put(EventsContract.RECEIVED_SMS_TYPE, R.drawable.ic_sms_mms);
    }
    private TextView dateView;
    private ImageView typeView;
    private ImageView contactView;
    private TextView nameView;
    private TextView numberView;
    private TextView messageView;
    private Uri eventUri;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.event_details_fragment, container, false);
        dateView = (TextView) v.findViewById(R.id.event_date);
        typeView = (ImageView) v.findViewById(R.id.event_type);
        contactView = (ImageView) v.findViewById(R.id.contact_picture);
        nameView = (TextView) v.findViewById(R.id.event_name);
        numberView = (TextView) v.findViewById(R.id.event_number);
        messageView = (TextView) v.findViewById(R.id.event_message);
        
        return v;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        initFields();
    }
    
    private void initFields() {
        final Cursor cursor = getActivity().getContentResolver().query(eventUri, EVENT_PROJECTION,
            null, null, null);
        if (cursor == null) {
            throw new IllegalArgumentException("Cannot event: " + eventUri);
        }
        if (!cursor.moveToNext()) {
            cursor.close();
            throw new IllegalArgumentException("Cannot find event: " + eventUri);
        }
        
        final long date;
        final int type;
        final String name;
        final String number;
        final String message;
        try {
            date = cursor.getLong(cursor.getColumnIndexOrThrow(CREATED));
            type = cursor.getInt(cursor.getColumnIndexOrThrow(TYPE));
            name = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
            number = cursor.getString(cursor.getColumnIndexOrThrow(NUMBER));
            message = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE));
        } finally {
            cursor.close();
        }
        
        final String dateStr = DateUtils.formatDateTime(getActivity(), date,
            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        dateView.setText(dateStr);
        
        final Integer typeResourceId = EVENT_ICONS.get(type);
        typeView.setVisibility(typeResourceId == null ? View.GONE : View.VISIBLE);
        if (typeResourceId != null) {
            typeView.setImageResource(typeResourceId);
        }
        
        final String eventName;
        final String eventNumber;
        if (number == null) {
            eventName = getActivity().getString(R.string.unknown_number);
            eventNumber = null;
        } else if (name == null) {
            eventName = number;
            eventNumber = null;
        } else {
            eventName = name;
            eventNumber = number;
        }
        nameView.setText(eventName);
        numberView.setText(eventNumber);
        
        messageView.setText(message);
        messageView.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        
        new GetContactPictureTask(this, eventNumber).execute();
    }
    
    public void setEvent(Uri eventUri) {
        this.eventUri = eventUri;
    }
    
    /**
     * Internal task for loading a contact picture.
     * @author Pixmob
     */
    private static class GetContactPictureTask extends AsyncTask<Void, Void, Drawable> {
        private final EventDetailsFragment fragment;
        private final String number;
        
        public GetContactPictureTask(final EventDetailsFragment fragment, final String number) {
            this.fragment = fragment;
            this.number = number;
        }
        
        @Override
        protected void onPreExecute() {
            fragment.contactView.setImageResource(R.drawable.ic_contact_picture);
        }
        
        @Override
        protected Drawable doInBackground(Void... params) {
            final ContentResolver contentResolver = fragment.getActivity().getContentResolver();
            Cursor c = contentResolver.query(Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)), new String[] { BaseColumns._ID }, null, null, null);
            int contactKey = -1;
            try {
                if (c.moveToNext()) {
                    contactKey = c.getInt(c.getColumnIndexOrThrow(BaseColumns._ID));
                }
            } finally {
                c.close();
            }
            
            Drawable contactPicture = null;
            if (contactKey != -1) {
                final InputStream input = Contacts.openContactPhotoInputStream(contentResolver, Uri
                        .withAppendedPath(Contacts.CONTENT_URI, String.valueOf(contactKey)));
                if (input != null) {
                    contactPicture = Drawable.createFromStream(input, "contactpicture");
                }
            }
            
            return contactPicture;
        }
        
        @Override
        protected void onPostExecute(Drawable result) {
            if (result != null) {
                fragment.contactView.setImageDrawable(result);
            }
        }
    }
}
