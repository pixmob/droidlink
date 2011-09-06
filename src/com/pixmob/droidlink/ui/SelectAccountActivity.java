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

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;

import com.pixmob.droidlink.R;
import com.pixmob.droidlink.services.DeviceInitService;
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
    private AccountAdapter accountAdapter;
    private String accountName;
    private State state;
    
    public void onSelectAccount(View v) {
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
                    if (state.loginTask != null) {
                        state.loginTask.cancel(true);
                        state.loginTask = null;
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
            
            accountAdapter = new AccountAdapter(this, accounts);
            setListAdapter(accountAdapter);
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
        accountAdapter.setSelectedAccount(accountName);
        accountAdapter.notifyDataSetInvalidated();
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
        state.loginTask = new InternalLoginTask(state);
        state.loginTask.execute(accountName);
    }
    
    private static void dismissDialog(Activity a, int dialogId) {
        try {
            a.dismissDialog(dialogId);
        } catch (IllegalArgumentException e) {
        }
    }
    
    private static class State {
        public SelectAccountActivity activity;
        public InternalLoginTask loginTask;
    }
    
    /**
     * Internal task for checking a Google account. A new dialog may be opened,
     * asking the user for granting its permission to use its account.
     * @author Pixmob
     */
    private static class InternalLoginTask extends LoginTask {
        private final State state;
        
        public InternalLoginTask(final State state) {
            super(state.activity);
            this.state = state;
        }
        
        @Override
        protected void onPreExecute() {
            final SelectAccountActivity a = state.activity;
            if (a != null) {
                a.showDialog(SelectAccountActivity.AUTH_PROGRESS_DIALOG);
            }
        }
        
        @Override
        protected void onAuthenticationSuccess() {
            final SelectAccountActivity a = state.activity;
            if (a != null) {
                a.setResult(RESULT_OK);
                a.finish();
                
                // Make sure a device is initialized for this user.
                a.startService(new Intent(a, DeviceInitService.class));
            }
        }
        
        @Override
        protected void onAuthenticationError() {
            final SelectAccountActivity a = state.activity;
            if (a != null) {
                dismissDialog(a, AUTH_PROGRESS_DIALOG);
                a.showDialog(AUTH_ERROR_DIALOG);
            }
        }
        
        @Override
        protected void onAuthenticationPending(Intent authPendingIntent) {
            final SelectAccountActivity a = state.activity;
            if (a != null) {
                dismissDialog(a, AUTH_PROGRESS_DIALOG);
                a.startActivityForResult(authPendingIntent, GRANT_AUTH_PERMISSION_REQUEST);
            }
        }
    }
}
