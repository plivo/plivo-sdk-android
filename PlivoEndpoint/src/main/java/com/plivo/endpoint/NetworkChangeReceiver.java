package com.plivo.endpoint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.plivo.endpoint.backend.plivo;

public class NetworkChangeReceiver extends BroadcastReceiver {
    private static boolean isConnected = true;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.D("NetworkChangeReceiver " + "Network changed");
        if (!Global.isJniLoaded) {
            Log.D("PlivoSDK is not loaded yet!");
            return;
        }
        try {
            if (isOnline(context)) {
                plivo.handleIPChange();
            }
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
            Log.E("errload loading libpjplivo:" + ule.toString());
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("handleIPChange failed");
        }

    }

    private boolean isOnline(Context context) {
        if (context == null) return false;

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            //should check null because in airplane mode it will be null
            isConnected = netInfo != null && netInfo.isConnected();
            Log.D("isOnline: " + isConnected);
            return isConnected;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isConnected() {
        if (!isConnected) {
            Log.E("Network not available. Please check your network connection.");
        }
        return isConnected;
    }
}
