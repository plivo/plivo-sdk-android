package com.plivo.endpoint.testutils;

import android.util.Pair;

public class TestConstants {
    public static final Pair<String, String> LOGIN_TEST_ENDPOINT =
            new Pair<>("testusername", "testpassword");
    public static final String PLIVO_ENDPOINT_TEST_NUM = "testnum"; // todo: use original test endpoint
    public static final String MOBILE_TEST_NUM = "+91xxxxxxxxxx"; // todo: use original test number
    public static final String INVALID_TEST_NUM = "xxxxxxxxxxx"; // invalid state call.status>=480 && <=489
    public static final String INVALID_TEST_NUM2 = "xxxxxxxxxxx"; // invalid number call.status 404 || 408
    // todo: find and INVALID_TEST_NUM2 . current xxxxxxxxx is giving 480
}

