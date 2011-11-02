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
package org.pixmob.droidlink.util;

import java.util.Arrays;
import java.util.Comparator;

import org.pixmob.droidlink.Constants;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;


/**
 * {@link Account} utilities.
 * @author Pixmob
 */
public final class Accounts {
    private Accounts() {
    }
    
    /**
     * Get a list of Google accounts, sorted by name.
     * @param context application context
     * @return a sorted array of Google accounts, empty if none
     */
    public static Account[] list(Context context) {
        final AccountManager accountManager = AccountManager.get(context);
        final Account[] accounts = accountManager.getAccountsByType(Constants.GOOGLE_ACCOUNT);
        Arrays.sort(accounts, AccountComparator.INSTANCE);
        
        return accounts;
    }
    
    /**
     * {@link Account} comparator, sorting by name.
     * @author Pixmob
     */
    private static class AccountComparator implements Comparator<Account> {
        /**
         * Singleton instance.
         */
        public static final AccountComparator INSTANCE = new AccountComparator();
        
        private AccountComparator() {
        }
        
        @Override
        public int compare(Account object1, Account object2) {
            return object1.name.compareTo(object2.name);
        }
    }
}
