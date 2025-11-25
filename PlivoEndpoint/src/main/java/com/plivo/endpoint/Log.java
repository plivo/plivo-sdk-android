package com.plivo.endpoint;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.PriorityQueue;


class Log {
    public static PriorityQueue<String> deviceLog=new PriorityQueue<>();
    private static final String TAG = "PlivoEndpoint";
    private  static final String loggingName = "PlivoSDK";
    public static SimpleDateFormat logDate = null;
    public static final String datePattern = "yyyy-MM-dd HH:mm:ss.SSS";


    public static void D(String l, boolean... force) {
        log(l, android.util.Log.DEBUG);
    }

    public static void E(String l, boolean... force) {
        updateDeviceLog("ERROR",l);
        log(l, android.util.Log.ERROR);
    }

    public static void W(String l, boolean... force) {
        updateDeviceLog("WARN",l);
        log(l, android.util.Log.WARN);
    }

    public static void I(String l, boolean... force) {
        updateDeviceLog("INFO",l);
        log(l, android.util.Log.INFO);
    }

    public static void log(String l, int priority, boolean... force) {
        if (Log.isEnabled() || (force != null && force.length > 0 && force[0])) {
            android.util.Log.println(priority, TAG, l);
        }
    }

    public static void enable(boolean enable) {
        Global.DEBUG = enable;
    }

    public static boolean isEnabled() {
        return Global.DEBUG;
    }

    public static void updateDeviceLog(String filter, String logMessage){
        DateFormat logDate = new SimpleDateFormat(datePattern);
        Date today = Calendar.getInstance().getTime();
        String logDateString = "["+ logDate.format(today)+"]";
        String preMessage = logDateString + " ["+filter+"] "+loggingName+" :: ";
        if (deviceLog.size()>=900){
            deviceLog.remove();
        }
        deviceLog.add(preMessage+logMessage);
    }
}

