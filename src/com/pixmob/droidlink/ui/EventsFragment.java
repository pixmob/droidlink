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
import static android.support.v4.view.MenuItem.SHOW_AS_ACTION_ALWAYS;
import static android.view.Menu.NONE;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.provider.EventsContract.Event.CREATED;
import static com.pixmob.droidlink.provider.EventsContract.Event.MESSAGE;
import static com.pixmob.droidlink.provider.EventsContract.Event.NAME;
import static com.pixmob.droidlink.provider.EventsContract.Event.NUMBER;
import static com.pixmob.droidlink.provider.EventsContract.Event.STATE;
import static com.pixmob.droidlink.provider.EventsContract.Event.TYPE;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.MenuInflater;
import android.view.View;
import android.widget.ListView;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.provider.EventsContract;

/**
 * Fragment for displaying device events.
 * @author Pixmob
 */
public class EventsFragment extends ListFragment implements LoaderCallbacks<Cursor> {
    private static final String[] EVENT_COLUMNS = { _ID, CREATED, STATE, NUMBER, NAME, TYPE,
            MESSAGE };
    private EventCursorAdapter cursorAdapter;
    private SharedPreferences prefs;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        prefs = getActivity().getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
        
        cursorAdapter = new EventCursorAdapter(getActivity(), null);
        setListAdapter(cursorAdapter);
        setHasOptionsMenu(true);
        
        // The list is hidden until event cursor is loaded.
        setListShown(false);
        setEmptyText(getString(R.string.no_events));
        
        // Start event loading.
        getLoaderManager().initLoader(0, null, this);
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(NONE, R.string.refresh, NONE, R.string.refresh)
                .setIcon(R.drawable.ic_menu_refresh).setShowAsAction(SHOW_AS_ACTION_ALWAYS);
        
        menu.add(NONE, R.string.account_selection, NONE, R.string.account_selection).setIcon(
            R.drawable.ic_menu_account_list);
        menu.add(NONE, R.string.settings, NONE, R.string.settings).setIcon(
            android.R.drawable.ic_menu_preferences);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.string.refresh:
                onRefresh();
                return true;
            case R.string.settings:
                onSettings();
                return true;
            case R.string.account_selection:
                onAccountSelection();
                return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void onSettings() {
        startActivity(new Intent(getActivity(), PreferencesActivity.class));
    }
    
    private void onAccountSelection() {
        startActivity(new Intent(getActivity(), AccountsActivity.class));
    }
    
    /**
     * Start event synchronization.
     */
    private void onRefresh() {
        // Check if an account is set.
        final String accountName = prefs.getString(SP_KEY_ACCOUNT, null);
        if (accountName != null) {
            EventsContract.sync(accountName, EventsContract.FULL_SYNC);
        }
    }
    
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        
        final String eventId = (String) v.getTag(EventCursorAdapter.TAG_ID);
        Log.i(TAG, "Opening event details for " + eventId);
        
        final Uri eventUri = Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId);
        startActivity(new Intent(getActivity(), EventDetailsActivity.class).setData(eventUri));
    }
    
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), EventsContract.CONTENT_URI, EVENT_COLUMNS, null,
                null, null);
    }
    
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        cursorAdapter.swapCursor(data);
        
        if (isResumed()) {
            // Events are available: the list is shown.
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }
    
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        cursorAdapter.swapCursor(null);
    }
}
