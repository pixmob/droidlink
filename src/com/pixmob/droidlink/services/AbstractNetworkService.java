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
package com.pixmob.droidlink.services;

import static com.pixmob.droidlink.Constants.TAG;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.pixmob.actionservice.ActionExecutionFailedException;
import com.pixmob.actionservice.ActionService;

/**
 * Base service implementation for network access.
 * @author Pixmob
 */
abstract class AbstractNetworkService extends ActionService {
    protected ConnectivityManager connectivityManager;
    private final String serviceName;
    
    public AbstractNetworkService(final String serviceName) {
        super(serviceName, 30 * 1000, 2);
        this.serviceName = serviceName;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    }
    
    /**
     * Check if the device is ready to synchronize (is there a network
     * connection?).
     */
    protected boolean isNetworkAvailable() {
        if (!connectivityManager.getBackgroundDataSetting()) {
            // The user disabled background data: this service should not run.
            return false;
        }
        
        final NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return netInfo != null && netInfo.isAvailable() && netInfo.isConnected();
    }
    
    /**
     * Check if the network is available before delegating to
     * {@link #onHandleActionInternal(Intent)}.
     * @see #isNetworkAvailable()
     */
    @Override
    protected final void onHandleAction(Intent intent) throws ActionExecutionFailedException,
            InterruptedException {
        onPreHandleActionInternal(intent);
        if (!isNetworkAvailable()) {
            Log.w(TAG, "Network is not reachable: cannot run service " + serviceName);
            return;
        }
        onHandleActionInternal(intent);
    }
    
    /**
     * Handle the intent while the network is available.
     */
    protected abstract void onHandleActionInternal(Intent intent)
            throws ActionExecutionFailedException, InterruptedException;
    
    /**
     * Handle the intent before {@link #onHandleActionInternal(Intent)}, without
     * checking for network connectivity.
     * @param intent
     * @throws ActionExecutionFailedException
     * @throws InterruptedException
     */
    protected abstract void onPreHandleActionInternal(Intent intent)
            throws ActionExecutionFailedException, InterruptedException;
}
