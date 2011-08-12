package com.pixmob.droidlink.services;

import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_NAME;
import static com.pixmob.droidlink.Constants.TAG;

import java.util.UUID;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * Generate an unique identifier for this device.
 * @author Pixmob
 */
public class DeviceIdGeneratorService extends IntentService {
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    
    public DeviceIdGeneratorService() {
        super("DeviceIdGenerator");
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_PREFERENCES_FILE, MODE_PRIVATE);
        prefsEditor = prefs.edit();
    }
    
    @Override
    protected void onHandleIntent(Intent intent) {
        if (!prefs.contains(SP_KEY_DEVICE_ID)) {
            final String deviceName = Build.MODEL;
            Log.i(TAG, "Generating a new identifier for this device (" + deviceName + ")");
            
            final String deviceId = UUID.randomUUID().toString();
            prefsEditor.putString(SP_KEY_DEVICE_ID, deviceId);
            prefsEditor.putString(SP_KEY_DEVICE_NAME, deviceName);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                prefsEditor.apply();
            } else {
                prefsEditor.commit();
            }
        }
    }
}
