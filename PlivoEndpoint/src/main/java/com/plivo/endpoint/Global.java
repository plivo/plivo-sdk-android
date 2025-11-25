package com.plivo.endpoint;

import java.util.HashMap;
import java.util.Map;

public class Global {
	public static boolean DEBUG;
	static String DOMAIN = BuildConfig.SIP_DOMAIN;
	static String VERSION = BuildConfig.VERSION_NAME;
	static boolean isJniLoaded = false;

	static String statsKeyURL = "https://stats.plivo.com/v1/browser/validate/";
	static String statsWSURL  = "wss://insights.plivo.com/ws";
	static String  S3BUCKET_API_URL = "https://stats.plivo.com/v1/browser/bucketurl/";

	static String SOURCE = "android";

	static final long rtpBatchSize = 1;
	static final long batchFrequency = 10000;
    static final long rtpColectionFequency = 1000;
    static final long firstRTPCall = 0;
	static final long minAverageBitrate = 8000;
	static final long maxAverageBitrate = 48000;


    // keeping this here for backward compatibility
	public static String mapToString(Map<String, String> map) {
		return Utils.mapToString(map);
	}
	public static Map<String, String> DEFAULT_COMMENTS = new HashMap<String, String>()
	{{
		put("AUDIO_LAG","audio_lag");
		put("BROKEN_AUDIO","broken_audio");
		put("CALL_DROPPED","call_dropped");
		put("CALLERID_ISSUES","callerid_issue");
		put("DIGITS_NOT_CAPTURED","digits_not_captured");
		put("ECHO","echo");
		put("HIGH_CONNECT_TIME","high_connect_time");
		put("LOW_AUDIO_LEVEL","low_audio_level");
		put("ONE_WAY_AUDIO","one_way_audio");
		put("OTHERS","others");
		put("ROBOTIC_AUDIO","robotic_audio");
	}};
}
