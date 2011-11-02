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
package org.pixmob.droidlink.receiver;

import org.pixmob.droidlink.service.SmsHandlerService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;


/**
 * When a message is received, this receiver is notified: the message is stored
 * as an event, which will get uploaded to the remote server at a later time.
 * @author Pixmob
 */
public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            // Not enough data.
            return;
        }
        
        final Object[] pdus = (Object[]) extras.get("pdus");
        if (pdus == null) {
            // No message to process.
            return;
        }
        
        // We delegate to a background service to free this broadcast receiver.
        final Intent i = new Intent(context, SmsHandlerService.class);
        i.putExtra("pdus", pdus);
        context.startService(i);
    }
}
