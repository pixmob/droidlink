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
package org.pixmob.droidlink.ui;

import static org.pixmob.droidlink.Constants.TAG;

import org.pixmob.droidlink.util.Accounts;

import android.accounts.Account;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;
import android.widget.ListView;


/**
 * Fragment for displaying accounts.
 * @author Pixmob
 */
public class AccountsFragment extends ListFragment {
    private static final int GRANT_AUTH_PERMISSION_REQUEST = 1;
    private AccountAdapter accountAdapter;
    private String selectedAccount;
    private AuthenticationProgressDialog authDialog;
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        authDialog = AuthenticationProgressDialog.newInstance();
        
        final Account[] accounts = Accounts.list(getActivity());
        if (accounts.length == 0) {
            // This may happen on a device without Google apps:
            // the application cannot run.
            Log.wtf(TAG, "No accounts available");
        } else {
            if (selectedAccount != null) {
                // Check if the selected account still exists:
                // the user may have deleted an account before going back to
                // this activity.
                boolean accountFound = false;
                for (final Account account : accounts) {
                    if (account.name.equals(selectedAccount)) {
                        // The selected account exists: select it.
                        accountFound = true;
                        break;
                    }
                }
                if (!accountFound) {
                    selectedAccount = null;
                }
            }
            
            accountAdapter = new AccountAdapter(getActivity(), accounts);
            setListAdapter(accountAdapter);
        }
    }
    
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        selectedAccount = ((Account) l.getItemAtPosition(position)).name;
        checkAccount();
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (GRANT_AUTH_PERMISSION_REQUEST == requestCode) {
            if (Activity.RESULT_OK == resultCode) {
                checkAccount();
            } else {
                selectedAccount = null;
            }
        }
    }
    
    private void checkAccount() {
        new InternalAccountInitTask().execute(selectedAccount);
    }
    
    /**
     * Internal task for checking a Google account. A new dialog may be opened,
     * asking the user for granting its permission to use its account.
     * @author Pixmob
     */
    private class InternalAccountInitTask extends AccountInitTask {
        public InternalAccountInitTask() {
            super(AccountsFragment.this);
        }
        
        @Override
        protected void onPreExecute() {
            authDialog.show(getSupportFragmentManager(), "auth");
        }
        
        @Override
        protected void onAuthenticationSuccess() {
            authDialog.dismiss();
            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        }
        
        @Override
        protected void onAuthenticationError() {
            authDialog.dismiss();
            AuthenticationErrorDialog.newInstance().show(getSupportFragmentManager(), "error");
        }
        
        @Override
        protected void onAuthenticationPending(Intent authPendingIntent) {
            authDialog.dismiss();
            getActivity().startActivityForResult(authPendingIntent, GRANT_AUTH_PERMISSION_REQUEST);
        }
    }
}
