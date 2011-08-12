package com.pixmob.droidlink.util;

import java.util.HashSet;
import java.util.Set;

import android.provider.CallLog;

/**
 * Phone utilities.
 * @author Pixmob
 */
public final class PhoneUtils {
    private static final Set<String> NULL_INCOMING_NUMBERS = new HashSet<String>(3);
    static {
        NULL_INCOMING_NUMBERS.add("0");
        NULL_INCOMING_NUMBERS.add("-1");
        NULL_INCOMING_NUMBERS.add("");
    }
    
    private PhoneUtils() {
    }
    
    /**
     * Get a phone number from a raw value (coming from a {@link CallLog} tuple
     * or a SMS).
     * @return phone number, <code>null</code> if none
     */
    public static String getPhoneNumber(String rawPhoneNumber) {
        String incomingNumber = rawPhoneNumber;
        if (NULL_INCOMING_NUMBERS.contains(incomingNumber)) {
            incomingNumber = null;
        }
        
        return incomingNumber;
    }
}
