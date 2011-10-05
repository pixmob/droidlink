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

import static android.support.v4.view.MenuItem.SHOW_AS_ACTION_ALWAYS;
import static android.support.v4.view.MenuItem.SHOW_AS_ACTION_IF_ROOM;
import static android.view.Menu.NONE;
import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.provider.EventsContract.Event.MESSAGE;
import static com.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static com.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static com.pixmob.droidlink.provider.EventsContract.Event.TYPE;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.provider.EventsContract;
import com.pixmob.droidlink.util.Cache;

/**
 * Fragment for displaying event details.
 * @author Pixmob
 */
public class EventDetailsFragment extends Fragment {
    private static final String[] EVENT_PROJECTION = { CREATED, TYPE, NAME, NUMBER, MESSAGE };
    private static ContactPictureCache contactPictureCache;
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
    private String number;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (contactPictureCache == null) {
            // Lazy load the contact picture cache.
            contactPictureCache = new ContactPictureCache(getActivity().getApplicationContext()
                    .getContentResolver());
        }
    }
    
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
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        
        // Include actions for calling and writing SMS only for phones.
        final TelephonyManager tm = (TelephonyManager) getActivity().getSystemService(
            Context.TELEPHONY_SERVICE);
        final boolean deviceIsPhone = tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        if (deviceIsPhone) {
            menu.add(NONE, R.string.call, NONE, R.string.call).setIcon(R.drawable.ic_menu_call)
                    .setShowAsAction(SHOW_AS_ACTION_ALWAYS);
            menu.add(NONE, R.string.compose_sms, NONE, R.string.compose_sms)
                    .setIcon(R.drawable.ic_menu_compose).setShowAsAction(SHOW_AS_ACTION_ALWAYS);
        }
        
        menu.add(NONE, R.string.delete_event, NONE, R.string.delete_event)
                .setIcon(R.drawable.ic_menu_delete).setShowAsAction(SHOW_AS_ACTION_IF_ROOM);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.string.call:
                onCallNumber();
                return true;
            case R.string.compose_sms:
                onComposeSMS();
                return true;
            case R.string.delete_event:
                onConfirmDeleteEvent();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void onCallNumber() {
        if (number != null) {
            final Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            startActivity(i);
        }
    }
    
    private void onComposeSMS() {
        if (number != null) {
            final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + number));
            startActivity(i);
        }
    }
    
    private void onConfirmDeleteEvent() {
        new ConfirmEventDeletionDialog().show(getSupportFragmentManager(), "dialog");
    }
    
    private void onDeleteEvent() {
        final Thread deleteEventTask = new Thread("DroidLink/DeleteEvent") {
            @Override
            public void run() {
                final ContentValues cv = new ContentValues(1);
                cv.put(EventsContract.Event.STATE, EventsContract.PENDING_DELETE_STATE);
                getActivity().getContentResolver().update(eventUri, cv, null, null);
                
                EventsContract.sync(getActivity(), EventsContract.LIGHT_SYNC, null);
            }
        };
        deleteEventTask.start();
        
        if (getResources().getBoolean(R.bool.large_screen)) {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.hide(EventDetailsFragment.this);
            ft.commit();
        } else {
            getActivity().finish();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        initFields();
    }
    
    private void initFields() {
        if (eventUri == null) {
            return;
        }
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
        String message;
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
        
        if (EventsContract.MISSED_CALL_TYPE == type) {
            message = getString(R.string.missed_call);
        }
        messageView.setText(message);
        
        new GetContactPictureTask(this, eventNumber).execute();
    }
    
    public void setEvent(Uri eventUri) {
        this.eventUri = eventUri;
        
        if (dateView != null) {
            initFields();
        }
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
            // Set a default contact picture.
            fragment.contactView.setImageResource(R.drawable.ic_contact_picture);
        }
        
        @Override
        protected Drawable doInBackground(Void... params) {
            // First, the contact identifier is searched from its phone number.
            final ContentResolver contentResolver = fragment.getActivity().getContentResolver();
            Cursor c = contentResolver.query(
                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                new String[] { BaseColumns._ID }, null, null, null);
            int contactKey = -1;
            try {
                if (c.moveToNext()) {
                    // We found a contact with the same phone number.
                    contactKey = c.getInt(c.getColumnIndexOrThrow(BaseColumns._ID));
                }
            } finally {
                c.close();
            }
            
            // At this point, we may not have found a contact identifier for
            // this phone number. In this case, the contact picture is not
            // updated: the default one is used.
            
            Drawable contactPicture = null;
            if (contactKey != -1) {
                // Try to load the contact picture using a cache in order to
                // reuse instances.
                contactPicture = contactPictureCache.get(contactKey);
            }
            
            if (DEVELOPER_MODE) {
                if (contactPicture != null) {
                    Log.i(TAG, "Found contact picture for '" + number + "'");
                } else {
                    Log.i(TAG, "Found NO contact picture for '" + number + "'");
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
    
    /**
     * Confirmation dialog for deleting an event.
     * @author Pixmob
     */
    public static class ConfirmEventDeletionDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.confirm_delete_event).setTitle(R.string.delete_event)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final EventDetailsFragment fragment = (EventDetailsFragment) ((FragmentActivity) getActivity())
                                    .getSupportFragmentManager().findFragmentById(
                                        R.id.event_details);
                            fragment.onDeleteEvent();
                        }
                    }).create();
        }
    }
    
    /**
     * Contact picture cache.
     * @author Pixmob
     */
    private static class ContactPictureCache extends Cache<Integer, Drawable> {
        private final ContentResolver contentResolver;
        
        public ContactPictureCache(final ContentResolver contentResolver) {
            super(1000 * 60);
            this.contentResolver = contentResolver;
        }
        
        @Override
        protected Drawable createEntry(Integer key) {
            Drawable contactPicture = null;
            final InputStream input = Contacts.openContactPhotoInputStream(contentResolver,
                Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(key)));
            if (input != null) {
                // The contact has a profile picture.
                contactPicture = Drawable.createFromStream(input, "contactpicture");
            }
            return contactPicture;
        }
        
        @Override
        protected void entryRemoved(Integer key, Drawable value) {
            if (value != null && value instanceof BitmapDrawable) {
                // Free bitmap resource when the contact picture is no longer
                // needed.
                final BitmapDrawable b = (BitmapDrawable) value;
                b.getBitmap().recycle();
            }
        }
    }
}
