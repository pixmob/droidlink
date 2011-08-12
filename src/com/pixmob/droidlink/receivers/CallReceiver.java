package com.pixmob.droidlink.receivers;

import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.TAG;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.pixmob.droidlink.Constants;
import com.pixmob.droidlink.services.MissedCallHandlerService;

/**
 * When a call is received, this receiver is notified. If the call was not
 * answered, the call log is read in order to upload the event to the remote
 * server at a later time.
 * @author Pixmob
 */
public class CallReceiver extends BroadcastReceiver {
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(Constants.SHARED_PREFERENCES_FILE,
                Context.MODE_PRIVATE);
            prefsEditor = prefs.edit();
        }
        
        final String callState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        final String previousCallState = prefs.getString(Constants.SP_KEY_LAST_CALL_STATE, null);
        
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(callState)) {
            if (DEVELOPER_MODE) {
                final String incomingNumber = intent
                        .getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                Log.i(TAG, "Call state is RINGING: incoming number is [" + incomingNumber + "]");
            }
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(callState)) {
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Call state is IDLE");
            }
            
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(previousCallState)) {
                if (DEVELOPER_MODE) {
                    Log.i(TAG, "Previous call state was RINGING: "
                            + "read call log to get missed call number");
                }
                context.startService(new Intent(context, MissedCallHandlerService.class));
            }
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(callState)) {
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Call state is OFFHOOK");
            }
        } else {
            Log.w(TAG, "Unknown call state: " + callState);
        }
        
        prefsEditor.putString(Constants.SP_KEY_LAST_CALL_STATE, callState);
        storePrefs();
    }
    
    private void storePrefs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            prefsEditor.apply();
        } else {
            prefsEditor.commit();
        }
    }
}
