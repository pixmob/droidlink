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
package com.pixmob.droidlink.net;

import static com.pixmob.droidlink.Constants.DEVELOPER_MODE;
import static com.pixmob.droidlink.Constants.REMOTE_API_VERSION;
import static com.pixmob.droidlink.Constants.SERVER_HOST;
import static com.pixmob.droidlink.Constants.SHARED_PREFERENCES_FILE;
import static com.pixmob.droidlink.Constants.SP_KEY_ACCOUNT;
import static com.pixmob.droidlink.Constants.SP_KEY_DEVICE_ID;
import static com.pixmob.droidlink.Constants.TAG;
import static com.pixmob.droidlink.Constants.USER_AGENT;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.pixmob.appengine.client.AppEngineAuthenticationException;
import com.pixmob.appengine.client.AppEngineClient;

/**
 * Network client for sending requests to the remote server. Requests are made
 * using the REST pattern, where data is encoded with JSON.
 * @author Pixmob
 */
public class NetworkClient {
    private static final String CHARSET = "UTF-8";
    private final AppEngineClient client;
    private final String deviceId;
    
    private NetworkClient(final AppEngineClient client, final String deviceId) {
        this.client = client;
        this.deviceId = deviceId;
    }
    
    public static NetworkClient newInstance(Context context) {
        final SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFERENCES_FILE,
            Context.MODE_PRIVATE);
        
        // An account name is required for sending authenticated requests.
        final String accountName = prefs.getString(SP_KEY_ACCOUNT, null);
        if (accountName == null) {
            Log.w(TAG, "No account set for this device");
            return null;
        }
        
        // A device identifier is nearly required for every requests.
        final String deviceId = prefs.getString(SP_KEY_DEVICE_ID, null);
        if (deviceId == null) {
            Log.w(TAG, "No device identifier set");
            return null;
        }
        
        final AppEngineClient gaeClient = new AppEngineClient(context, SERVER_HOST);
        gaeClient.setAccount(accountName);
        gaeClient.setHttpUserAgent(USER_AGENT);
        
        return new NetworkClient(gaeClient, deviceId);
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public JSONObject get(String serviceUri) throws IOException, AppEngineAuthenticationException {
        return execute(HttpMethod.GET, serviceUri, null);
    }
    
    public JSONObject put(String serviceUri, JSONObject data) throws IOException,
            AppEngineAuthenticationException {
        return execute(HttpMethod.PUT, serviceUri, data);
    }
    
    public JSONObject post(String serviceUri, JSONObject data) throws IOException,
            AppEngineAuthenticationException {
        return execute(HttpMethod.POST, serviceUri, data);
    }
    
    public void delete(String serviceUri) throws IOException, AppEngineAuthenticationException {
        execute(HttpMethod.DELETE, serviceUri, null);
    }
    
    public void close() {
        client.close();
    }
    
    private JSONObject execute(HttpMethod httpMethod, String serviceUri, JSONObject data)
            throws IOException, AppEngineAuthenticationException {
        final String requestUri = createServiceUri(serviceUri);
        final HttpUriRequest request = httpMethod.createRequest(requestUri, data);
        prepareJsonRequest(request);
        
        Log.i(TAG, "Sending request to remote server: " + requestUri);
        if (DEVELOPER_MODE) {
            final HttpEntityEnclosingRequestBase r = (HttpEntityEnclosingRequestBase) request;
            final HttpEntity body = r.getEntity();
            if (body != null) {
                final String strBody = EntityUtils.toString(body, CHARSET);
                Log.d(TAG, "Body for request " + requestUri + ": " + strBody);
            }
        }
        
        HttpResponse resp = null;
        try {
            resp = client.execute(request);
            
            final int statusCode = resp.getStatusLine().getStatusCode();
            if (DEVELOPER_MODE) {
                Log.i(TAG, "Result for request " + requestUri + ": " + statusCode);
            }
            
            if (isStatusNotFound(statusCode)) {
                throw new NetworkClientException(requestUri, "Resource not found");
            }
            if (isStatusError(statusCode)) {
                throw new NetworkClientException(requestUri, "Request failed on remote server");
            }
            if (!isStatusOK(statusCode)) {
                throw new NetworkClientException(requestUri, "Request failed with error "
                        + statusCode);
            }
            
            final HttpEntity entity = resp.getEntity();
            if (entity == null) {
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "No JSON result for request " + requestUri);
                }
                return null;
            }
            
            final String strResp = EntityUtils.toString(entity);
            if (TextUtils.isEmpty(strResp)) {
                if (DEVELOPER_MODE) {
                    Log.d(TAG, "Empty JSON result for request " + requestUri);
                }
                return null;
            }
            
            if (DEVELOPER_MODE) {
                Log.d(TAG, "JSON result for request " + requestUri + ": " + strResp);
            }
            try {
                return new JSONObject(strResp);
            } catch (JSONException e) {
                throw new NetworkClientException(requestUri, "Invalid JSON result", e);
            }
        } catch (AppEngineAuthenticationException e) {
            closeResources(request, resp);
            throw e;
        } catch (IOException e) {
            closeResources(request, resp);
            throw e;
        }
    }
    
    private static void closeResources(HttpUriRequest request, HttpResponse response) {
        try {
            request.abort();
        } catch (UnsupportedOperationException ignore) {
        }
        if (response != null) {
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    entity.consumeContent();
                } catch (IOException ignore) {
                }
            }
        }
    }
    
    /**
     * Create a service Uri.
     */
    private static String createServiceUri(String resource) {
        if (!resource.startsWith("/")) {
            resource = "/" + resource;
        }
        return "https://" + SERVER_HOST + "/api/" + REMOTE_API_VERSION + resource;
    }
    
    /**
     * Check if the status code is a valid response.
     */
    private static boolean isStatusOK(int statusCode) {
        return statusCode == 200 || statusCode == 204 || statusCode == 201;
    }
    
    /**
     * Check if the status code is an error indicating that a resource was not
     * found.
     */
    private static boolean isStatusNotFound(int statusCode) {
        return statusCode == 404;
    }
    
    /**
     * Check if the status code is an error indicating the server failed to
     * process the request.
     */
    private static boolean isStatusError(int statusCode) {
        return statusCode == 500;
    }
    
    /**
     * Prepare a request for sending a Json formatted query.
     */
    private static void prepareJsonRequest(HttpRequest req) {
        req.setHeader(HTTP.CONTENT_TYPE, "application/json");
        req.addHeader("Accept", "application/json");
    }
    
    private static enum HttpMethod {
        GET, PUT, POST, DELETE;
        
        public HttpUriRequest createRequest(String requestUri, JSONObject data)
                throws UnsupportedEncodingException {
            switch (this) {
                case GET:
                    return new HttpGet(requestUri);
                case POST:
                    final HttpPost post = new HttpPost(requestUri);
                    if (data != null) {
                        post.setEntity(new StringEntity(data.toString(), CHARSET));
                    }
                    return post;
                case PUT:
                    final HttpPut put = new HttpPut(requestUri);
                    if (data != null) {
                        put.setEntity(new StringEntity(data.toString(), CHARSET));
                    }
                    return put;
                case DELETE:
                    return new HttpDelete(requestUri);
                default:
                    // Unlikely to happen.
                    throw new IllegalStateException("Unsupported Http method: " + this);
            }
        }
    }
}
