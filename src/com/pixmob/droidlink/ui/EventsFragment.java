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

import java.lang.ref.WeakReference;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
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
import com.pixmob.droidlink.feature.Features;
import com.pixmob.droidlink.feature.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.provider.EventsContract;
import com.pixmob.droidlink.util.Accounts;

/**
 * Fragment for displaying device events.
 * @author Pixmob
 */
public class EventsFragment extends ListFragment implements LoaderCallbacks<Cursor> {
    private static final int GRANT_AUTH_PERMISSION_REQUEST = 1;
    private static final String[] EVENT_COLUMNS = { _ID, CREATED, STATE, NUMBER, NAME, TYPE,
            MESSAGE };
    private EventCursorAdapter cursorAdapter;
    private SharedPreferences prefs;
    private WeakReference<OnEventSelectionListener> selectionListenerRef;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        prefs = getActivity().getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
        
        cursorAdapter = new EventCursorAdapter(getActivity());
        setListAdapter(cursorAdapter);
        setHasOptionsMenu(true);
        
        // One event selected at a time.
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        // The list is hidden until event cursor is loaded.
        setListShown(false);
        setEmptyText(getString(R.string.no_events));
        
        // Start event loading.
        getLoaderManager().initLoader(0, null, this);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Refresh events.
        getLoaderManager().restartLoader(0, null, this);
    }
    
    @Override
    public void onDetach() {
        final Loader<Cursor> loader = getLoaderManager().getLoader(0);
        if (loader != null) {
            loader.reset();
        }
        super.onDetach();
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(NONE, R.string.refresh, NONE, R.string.refresh)
                .setIcon(R.drawable.ic_menu_refresh).setShowAsAction(SHOW_AS_ACTION_ALWAYS);
        
        // Include this menu item if there are several accounts.
        if (Accounts.list(getActivity()).length > 1) {
            menu.add(NONE, R.string.account_selection, NONE, R.string.account_selection).setIcon(
                R.drawable.ic_menu_account_list);
        }
        
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
        if (getResources().getBoolean(R.bool.large_screen)) {
            final AccountsDialogFragment df = new AccountsDialogFragment();
            df.show(getSupportFragmentManager(), "account");
        } else {
            startActivity(new Intent(getActivity(), AccountsActivity.class));
        }
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
        l.setItemChecked(position, true);
        
        final String eventId = (String) v.getTag(EventCursorAdapter.TAG_ID);
        Log.i(TAG, "Opening event details for " + eventId);
        
        final Uri eventUri = Uri.withAppendedPath(EventsContract.CONTENT_URI, eventId);
        if (selectionListenerRef != null) {
            final OnEventSelectionListener selectionListener = selectionListenerRef.get();
            if (selectionListener != null) {
                try {
                    selectionListener.onEventSelected(eventUri);
                } catch (Exception e) {
                    Log.e(TAG, "Event selection listener error", e);
                }
            }
        }
    }
    
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final CursorLoader loader = new CursorLoader(getActivity(), EventsContract.CONTENT_URI,
                EVENT_COLUMNS, null, null, null);
        loader.setUpdateThrottle(1000);
        return loader;
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
    
    public void setOnEventSelectionListener(OnEventSelectionListener l) {
        this.selectionListenerRef = l != null ? new WeakReference<OnEventSelectionListener>(l)
                : null;
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (GRANT_AUTH_PERMISSION_REQUEST == requestCode) {
            if (Activity.RESULT_OK == resultCode) {
                final String account = prefs.getString(SP_KEY_ACCOUNT, null);
                if (account != null) {
                    new InternalAccountInitTask(this).execute(account);
                }
            }
        }
    }
    
    /**
     * Dialog fragment for selecting an account. This dialog is shown when the
     * screen is large enough.
     * @author Pixmob
     */
    public static class AccountsDialogFragment extends DialogFragment implements OnClickListener {
        private AccountAdapter accountAdapter;
        
        public static AccountsDialogFragment newInstance() {
            return new AccountsDialogFragment();
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Account[] accounts = Accounts.list(getActivity());
            accountAdapter = new AccountAdapter(getActivity(), accounts);
            
            final SharedPreferences prefs = getActivity().getSharedPreferences(
                SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
            final String account = prefs.getString(SP_KEY_ACCOUNT, null);
            int accountIndex = -1;
            if (account != null) {
                for (int i = 0; i < accounts.length && accountIndex == -1; ++i) {
                    if (accounts[i].name.equals(account)) {
                        accountIndex = i;
                    }
                }
            }
            
            return new AlertDialog.Builder(getActivity()).setTitle(R.string.select_account)
                    .setSingleChoiceItems(accountAdapter, accountIndex, this).create();
        }
        
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final String account = accountAdapter.getItem(which).name;
            
            final SharedPreferences prefs = getActivity().getSharedPreferences(
                SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putString(SP_KEY_ACCOUNT, account);
            Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
            
            dialog.dismiss();
            new InternalAccountInitTask(getFragmentManager().findFragmentById(R.id.events))
                    .execute(account);
        }
    }
    
    /**
     * Internal task for checking a Google account. A new dialog may be opened,
     * asking the user for granting its permission to use its account.
     * @author Pixmob
     */
    private static class InternalAccountInitTask extends AccountInitTask {
        private AuthenticationProgressDialog authDialog;
        
        public InternalAccountInitTask(Fragment fragment) {
            super(fragment);
            authDialog = AuthenticationProgressDialog.newInstance();
        }
        
        @Override
        protected void onPreExecute() {
            authDialog.show(getFragment().getSupportFragmentManager(), "auth");
        }
        
        @Override
        protected void onAuthenticationSuccess() {
            authDialog.dismiss();
        }
        
        @Override
        protected void onAuthenticationError() {
            authDialog.dismiss();
            AuthenticationErrorDialog.newInstance().show(getFragment().getSupportFragmentManager(),
                "error");
        }
        
        @Override
        protected void onAuthenticationPending(Intent authPendingIntent) {
            authDialog.dismiss();
            getFragment().startActivityForResult(authPendingIntent, GRANT_AUTH_PERMISSION_REQUEST);
        }
    }
}
