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
