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

import static com.pixmob.droidlink.Constants.TAG;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
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
import com.pixmob.appengine.client.AppEngineClient;
import com.pixmob.droidlink.Constants;
import com.pixmob.droidlink.R;
import com.pixmob.droidlink.util.Accounts;

/**
 * Select a Google account for connecting the user device.
 * @author Pixmob
 */
public class SelectAccountActivity extends ListActivity {
    private static final int NO_ACCOUNT_AVAILABLE_DIALOG = 2;
    private String accountName;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    
    public void onSelectAccount(View v) {
        prefsEditor.putString(Constants.SP_KEY_ACCOUNT, accountName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            prefsEditor.apply();
        } else {
            prefsEditor.commit();
        }
        setResult(RESULT_OK);
        finish();
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
    
    private static class CheckAccountTask extends AsyncTask<String, Void, Boolean> {
        private SelectAccountActivity activity;
        
        public void attach(SelectAccountActivity activity) {
            this.activity = activity;
        }
        
        @Override
        protected Boolean doInBackground(String... params) {
            final SelectAccountActivity a = activity;
            if (a == null) {
                return false;
            }
            
            final String account = params[0];
            final AppEngineClient client = new AppEngineClient(a.getApplicationContext(),
                    "mydroidlink.appspot.com", null);
            boolean authSuccess = false;
            
            for (int remainingRetries = 3; !authSuccess && remainingRetries > 0; --remainingRetries) {
                try {
                    final HttpResponse resp = client.execute(new HttpGet("/"));
                    final int sc = resp.getStatusLine().getStatusCode();
                    if (sc == 200) {
                        authSuccess = true;
                    } else {
                        Log.i(TAG, "Failed to check account availability: retry");
                    }
                } catch (IOException e) {
                    Log.i(TAG, "Failed to check account availability:" + " retry", e);
                } catch (AppEngineAuthenticationException e) {
                    Log.w(TAG, "Failed to authenticate account " + account, e);
                    break;
                }
            }
            
            return authSuccess;
        }
    }
}
