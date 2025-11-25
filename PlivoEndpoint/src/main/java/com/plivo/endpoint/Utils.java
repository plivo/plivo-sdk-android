package com.plivo.endpoint;

import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.Date;

class Utils {
    static final Set<String> VALID_DTMF = new HashSet<>(Arrays.asList(
            new String[]{"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "#", "*"}
    ));

    private static final String VALID_HEADER_KEY_REGEX = "[xX]-[pP][hH]-[a-zA-Z0-9\\-]{1,19}$";
    private static final String VALID_HEADER_VALUE_REGEX = "[a-zA-Z0-9\\-\\+\\_\\(\\)\\%]{1,120}$";
    private static final String[] MIN_REQUIRED_PUSH_HEADERS = { "registrar", "index", "label" };

    static String mapToString(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;

        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!TextUtils.isEmpty(stringBuilder)) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey() + ":" + entry.getValue());
        }
        return stringBuilder.toString();
    }

    /**
     *
     * @param string: comma separated value of map k1:v1,k2:v2
     * @return
     */
    static Map<String, String> stringToMap(String string) {
        if (TextUtils.isEmpty(string)) return null;

        Map<String, String> map = new HashMap<>();
        String[] keyValuePairs = string.trim().split(",");
        int delimiterIndex;
        for (String kv : keyValuePairs) {
            delimiterIndex = kv.indexOf(":");
            if (delimiterIndex != -1)
                map.put(kv.substring(0, delimiterIndex).trim(), kv.substring(delimiterIndex+1).trim());
        }

        return map;
    }

    // this checks and removes unsupported key value pair,
    // but keeping the same name as before as it is exposed already not to break the backward compatibility.
    static void checkSpecialCharacters(Map<String, String> map) {
        validateCallHeaders(map);
    }

    static boolean validateCallHeaders(Map<String, String> callHeaders) {
        if (callHeaders == null || callHeaders.isEmpty()) return false;

        Pattern keyPattern = Pattern.compile(VALID_HEADER_KEY_REGEX, Pattern.CASE_INSENSITIVE);
        Pattern valuePattern = Pattern.compile(VALID_HEADER_VALUE_REGEX);

        Iterator<Map.Entry<String, String>> entryIterator = callHeaders.entrySet().iterator();
        Map.Entry<String, String> entry;
        while (entryIterator.hasNext()) {
            entry = entryIterator.next();
            if (!keyPattern.matcher(entry.getKey()).matches() ||
                    !valuePattern.matcher(entry.getValue()).matches()) {
                Log.W("Invalid header. Skipping " + entry);
                entryIterator.remove();
            }
        }

        if (callHeaders.isEmpty()) return false;

        return true;
    }

    // checkAndFilter push notification headers
    static boolean validatePushHeaders(Map<String, String> pushHeaders) {
        if (pushHeaders == null || pushHeaders.isEmpty()) return false;

        List<String> requiredHeaders = Arrays.asList(MIN_REQUIRED_PUSH_HEADERS);
        for (String rHeader : requiredHeaders) {
            if (!pushHeaders.containsKey(rHeader)) {
                return false;
            }
        }

        return true;
    }

    static String getCurrentTimeInMilliSeconds() {
        return new Date().getTime() + "";
    }
}

