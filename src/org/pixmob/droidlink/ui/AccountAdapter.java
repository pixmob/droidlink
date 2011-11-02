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

import org.pixmob.appengine.client.R;

import android.accounts.Account;
import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * {@link ArrayAdapter} implementation for displaying {@link Account} instances.
 * @author Pixmob
 */
class AccountAdapter extends ArrayAdapter<Account> {
    public AccountAdapter(final Activity context, final Account[] objects) {
        super(context, R.layout.account_row, R.id.account_name, objects);
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final LayoutInflater inflater = ((Activity) getContext()).getLayoutInflater();
        final View row = inflater.inflate(R.layout.account_row, null);
        row.setTag(row.findViewById(R.id.account_name));
        
        final Account account = getItem(position);
        final TextView ctv = (TextView) row.getTag();
        ctv.setText(account.name);
        
        return row;
    }
}
