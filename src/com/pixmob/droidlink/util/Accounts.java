package com.pixmob.droidlink.util;

import java.util.Arrays;
import java.util.Comparator;

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
        final Account[] accounts = accountManager.getAccountsByType("com.google");
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
