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

import static com.pixmob.droidlink.Constants.REMOTE_API_VERSION;
import static com.pixmob.droidlink.Constants.SERVER_HOST;

import org.apache.http.HttpRequest;

/**
 * Http utilities.
 * @author Pixmob
 */
public final class HttpUtils {
    private HttpUtils() {
    }
    
    /**
     * Prepare a request for sending a Json formatted query.
     */
    public static void prepareJsonRequest(HttpRequest req) {
        req.setHeader("Content-type", "application/json");
        req.setHeader("Accept", "application/json");
    }
    
    /**
     * Create a service Uri.
     */
    public static String createServiceUri(String resource) {
        if (!resource.startsWith("/")) {
            resource = "/" + resource;
        }
        return "https://" + SERVER_HOST + "/api/" + REMOTE_API_VERSION + resource;
    }
    
    /**
     * Check if the status code is a valid response.
     */
    public static boolean isStatusOK(int statusCode) {
        return statusCode == 200 || statusCode == 204;
    }
    
    /**
     * Check if the status code is an error indicating that a resource was not
     * found.
     */
    public static boolean isStatusNotFound(int statusCode) {
        return statusCode == 404;
    }
}
