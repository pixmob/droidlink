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
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;

import com.pixmob.droidlink.Constants;
import com.pixmob.droidlink.R;
import com.pixmob.droidlink.providers.EventsContract;

/**
 * Display phone events from every user devices.
 * @author Pixmob
 */
public class EventsActivity extends ListActivity {
    private static final String[] EVENT_COLUMNS = { _ID, CREATED, NUMBER, NAME, TYPE, MESSAGE };
    private static final int SELECT_ACCOUNT_REQUEST = 1;
    private SharedPreferences prefs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Constants.SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        
        if (getResources().getBoolean(R.bool.fullscreen)) {
            // The title bar is hidden in fullscreen mode.
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setContentView(R.layout.events);
        
        // On a phone device, each row contains a button to call back the
        // number. This button needs to be focusable.
        getListView().setItemsCanFocus(true);
        
        // The activity is managing its own data: the list is automatically
        // refreshed when the source is updated.
        final Cursor c = managedQuery(EventsContract.CONTENT_URI, EVENT_COLUMNS, null, null, null);
        setListAdapter(new EventCursorAdapter(this, c));
        
        findViewById(R.id.robot).setVisibility(
            getResources().getBoolean(R.bool.show_robot) ? View.VISIBLE : View.GONE);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (!isSetupDone()) {
            // The application cannot work without a Google account.
            startSetup();
        }
    }
    
    private boolean isSetupDone() {
        return prefs.getString(Constants.SP_KEY_ACCOUNT, null) != null;
    }
    
    private void startSetup() {
        final Intent i = new Intent(this, SelectAccountActivity.class);
        startActivityForResult(i, SELECT_ACCOUNT_REQUEST);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.events, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.preferences) {
            startSetup();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        
        final Long itemId = (Long) v.getTag(EventCursorAdapter.TAG_ID);
        final Uri itemUri = ContentUris.withAppendedId(EventsContract.CONTENT_URI, itemId);
        
        if (DEVELOPER_MODE) {
            Log.i(TAG, "Opening event details for " + itemUri);
        }
        
        // TODO start the event details activity
    }
}
