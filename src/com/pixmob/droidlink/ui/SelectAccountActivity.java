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

import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.droidlink.Constants;
import com.pixmob.droidlink.R;
import com.pixmob.droidlink.features.Features;
import com.pixmob.droidlink.features.SharedPreferencesSaverFeature;
import com.pixmob.droidlink.net.NetworkClient;
import com.pixmob.droidlink.util.Accounts;

/**
 * Select a Google account for connecting the user device.
 * @author Pixmob
 */
public class SelectAccountActivity extends ListActivity {
    private static final int GRANT_AUTH_PERMISSION_REQUEST = 1;
    private static final int NO_ACCOUNT_AVAILABLE_DIALOG = 2;
    private static final int AUTH_PROGRESS_DIALOG = 3;
    private static final int AUTH_ERROR_DIALOG = 4;
    private String accountName;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    private CheckAccountTask checkAccountTask;
    private State state;
    
    public void onSelectAccount(View v) {
        prefsEditor.putString(Constants.SP_KEY_ACCOUNT, accountName);
        Features.getFeature(SharedPreferencesSaverFeature.class).save(prefsEditor);
        checkAccount();
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        if (NO_ACCOUNT_AVAILABLE_DIALOG == id) {
            return new AlertDialog.Builder(this).setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setResult(RESULT_CANCELED);
                        finish();
                    }
                }).setTitle(R.string.error).setMessage(R.string.no_account_available).create();
        }
        if (AUTH_ERROR_DIALOG == id) {
            return new AlertDialog.Builder(this).setPositiveButton(R.string.ok, null).setTitle(
                R.string.error).setMessage(R.string.auth_error).create();
        }
        if (AUTH_PROGRESS_DIALOG == id) {
            final ProgressDialog d = new ProgressDialog(this);
            d.setTitle(R.string.please_wait);
            d.setMessage(getString(R.string.auth_pending));
            d.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    if (checkAccountTask != null) {
                        checkAccountTask.cancel(true);
                        checkAccountTask = null;
                    }
                }
            });
            return d;
        }
        return super.onCreateDialog(id);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(Constants.SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        prefsEditor = prefs.edit();
        
        if (getResources().getBoolean(R.bool.fullscreen)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        setContentView(R.layout.select_account);
        
        state = (State) getLastNonConfigurationInstance();
        if (state == null) {
            state = new State();
            state.activity = this;
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return state;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        final Account[] accounts = Accounts.list(this);
        if (accounts.length == 0) {
            // This may happen on a device without Google apps:
            // the application cannot run.
            showDialog(NO_ACCOUNT_AVAILABLE_DIALOG);
        } else {
            setListAdapter(new AccountAdapter(accounts));
            
            if (accountName != null) {
                // Check if the selected account still exists:
                // the user may have deleted an account before going back to
                // this activity.
                boolean accountFound = false;
                for (final Account account : accounts) {
                    if (account.name.equals(accountName)) {
                        // The selected account exists: select it.
                        accountFound = true;
                        break;
                    }
                }
                if (!accountFound) {
                    accountName = null;
                }
            }
        }
        
        findViewById(R.id.ok_button).setEnabled(accountName != null);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        accountName = state.getString("account");
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("account", accountName);
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        accountName = ((Account) l.getItemAtPosition(position)).name;
        ((AccountAdapter) getListAdapter()).notifyDataSetInvalidated();
        findViewById(R.id.ok_button).setEnabled(true);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (GRANT_AUTH_PERMISSION_REQUEST == requestCode) {
            if (RESULT_OK == resultCode) {
                checkAccount();
            } else {
                accountName = null;
            }
        }
    }
    
    private void checkAccount() {
        final String deviceName = prefs.getString(SP_KEY_DEVICE_NAME, null);
        state.checkAccountTask = new CheckAccountTask(state);
        state.checkAccountTask.execute(deviceName);
    }
    
    private class AccountAdapter extends ArrayAdapter<Account> {
        public AccountAdapter(Account[] objects) {
            super(SelectAccountActivity.this, R.layout.account_row, R.id.account_name, objects);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LayoutInflater inflater = getLayoutInflater();
            final View row = inflater.inflate(R.layout.account_row, null);
            row.setTag(row.findViewById(R.id.account_name));
            
            final Account account = getItem(position);
            final CheckedTextView ctv = (CheckedTextView) row.getTag();
            ctv.setChecked(account.name.equals(SelectAccountActivity.this.accountName));
            ctv.setText(account.name);
            
            return row;
        }
    }
    
    private static void dismissDialog(Activity a, int dialogId) {
        try {
            a.dismissDialog(dialogId);
        } catch (IllegalArgumentException e) {
        }
    }
    
    private static class State {
        public SelectAccountActivity activity;
        public CheckAccountTask checkAccountTask;
    }
    
    /**
     * Internal task for checking a Google account. A new dialog may be opened,
     * asking the user for granting its permission to use its account.
     * @author Pixmob
     */
    private static class CheckAccountTask extends AsyncTask<String, Void, Integer> {
        private static final int AUTH_OK = 0;
        private static final int AUTH_FAIL = 1;
        private static final int AUTH_PENDING = 2;
        private final State state;
        private Intent authPendingIntent;
        
        public CheckAccountTask(final State state) {
            this.state = state;
        }
        
        @Override
        protected Integer doInBackground(String... params) {
            final SelectAccountActivity a = state.activity;
            if (a == null) {
                return AUTH_FAIL;
            }
            
            final String deviceName = params[0];
            final JSONObject jsonData = new JSONObject();
            try {
                jsonData.put("name", deviceName);
            } catch (JSONException ignore) {
            }
            
            final NetworkClient client = NetworkClient.newInstance(a);
            int authResult = AUTH_FAIL;
            try {
                client.put("/device/" + client.getDeviceId(), jsonData);
                authResult = AUTH_OK;
            } catch (AppEngineAuthenticationException e) {
                if (e.isAuthenticationPending()) {
                    authPendingIntent = e.getPendingAuthenticationPermissionActivity();
                    authResult = AUTH_PENDING;
                }
                Log.w(TAG, "Failed to authenticate account", e);
            } catch (IOException e) {
                Log.i(TAG, "Failed to check account availability", e);
            } finally {
                client.close();
            }
            
            return authResult;
        }
        
        @Override
        protected void onPreExecute() {
            if (state.activity != null) {
                state.activity.showDialog(AUTH_PROGRESS_DIALOG);
            }
        }
        
        @Override
        protected void onPostExecute(Integer result) {
            final SelectAccountActivity a = state.activity;
            
            switch (result) {
                case AUTH_PENDING:
                    if (a != null) {
                        dismissDialog(a, AUTH_PROGRESS_DIALOG);
                        a.startActivityForResult(authPendingIntent, GRANT_AUTH_PERMISSION_REQUEST);
                    }
                    break;
                case AUTH_FAIL:
                    if (a != null) {
                        dismissDialog(a, AUTH_PROGRESS_DIALOG);
                        a.showDialog(AUTH_ERROR_DIALOG);
                    }
                    break;
                case AUTH_OK:
                    if (a != null) {
                        a.setResult(RESULT_OK);
                        a.finish();
                    }
            }
        }
    }
}
