package com.pixmob.droidlink;

/**
 * Application constants.
 * @author Pixmob
 */
public final class Constants {
    /**
     * Event type for a missed call.
     */
    public static final int MISSED_CALL_EVENT = 1;
    /**
     * Event type for a SMS.
     */
    public static final int SMS_EVENT_TYPE = 2;
    
    /**
     * Set to <code>true</code> to enable development mode. When a release is
     * being built, make sure this flag is set to <code>false</code>.
     */
    public static final boolean DEVELOPER_MODE = true;
    
    /**
     * Use this tag for every logging statements.
     */
    public static final String TAG = "DroidLink";
    
    public static final String SHARED_PREFERENCES_FILE = "sharedprefs";
    public static final String SP_KEY_ACCOUNT = "account";
    public static final String SP_KEY_ENABLED = "enabled";
    public static final String SP_KEY_LAST_CALL_STATE = "lastCallState";
    public static final String SP_KEY_DEVICE_ID = "deviceId";
    public static final String SP_KEY_DEVICE_NAME = "deviceName";
    
    private Constants() {
    }
}
