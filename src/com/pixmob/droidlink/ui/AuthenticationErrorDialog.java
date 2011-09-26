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

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import com.pixmob.droidlink.R;

/**
 * Error dialog when the authentication failed.
 * @author Pixmob
 */
public class AuthenticationErrorDialog extends DialogFragment {
    public static AuthenticationErrorDialog newInstance() {
        return new AuthenticationErrorDialog();
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity()).setTitle(R.string.error)
                .setIcon(R.drawable.ic_dialog_alert).setMessage(R.string.auth_error)
                .setPositiveButton(android.R.string.ok, null).create();
    }
}
