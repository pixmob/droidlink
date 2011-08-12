package com.pixmob.droidlink.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.pixmob.droidlink.services.SmsHandlerService;

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
