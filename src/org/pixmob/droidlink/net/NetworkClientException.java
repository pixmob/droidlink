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
package org.pixmob.droidlink.net;

import java.io.IOException;

/**
 * A network operation failed.
 * @author Pixmob
 */
public class NetworkClientException extends IOException {
    private static final long serialVersionUID = 1L;
    private final String requestUri;
    private final int statusCode;
    
    public NetworkClientException(final String requestUri, final String message) {
        this(requestUri, 500, message, null);
    }
    
    public NetworkClientException(final String requestUri, final int statusCode,
            final String message) {
        this(requestUri, statusCode, message, null);
    }
    
    public NetworkClientException(final String requestUri, final String message,
            final Throwable cause) {
        this(requestUri, 0, message, cause);
    }
    
    public NetworkClientException(final String requestUri, final int statusCode,
            final String message, final Throwable cause) {
        super(message, cause);
        this.requestUri = requestUri;
        this.statusCode = statusCode;
    }
    
    public String getRequestUri() {
        return requestUri;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
}
