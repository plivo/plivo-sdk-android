package com.plivo.endpoint;

import android.content.Context;
import android.provider.Browser;

import com.plivo.endpoint.java_websocket.client.WebSocketClient;
import com.plivo.endpoint.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


public class CallInsights {
    private final String ANSWERED_EVENT = "CALL_ANSWERED";
    private final String ANSWERED_OUT_EVENT_INFO = "Outgoing call answered";
    private final String ANSWERED_IN_EVENT_INFO = "Incoming call answered";
    private final String SUMMARY_EVENT = "CALL_SUMMARY";
    private final String FEEDBACK_EVENT = "FEEDBACK";
    private final String CALL_STATS = "CALL_STATS";
    private final String VERSION= "v1";

    private final String SDK_NAME= "PlivoAndroidSDK";

    private final String STATS_SOURCE= "AndroidSDK";
    private final String[] CLIENT_VERSION = Global.VERSION.split("\\.");
    private final String USER_AGENT = SDK_NAME + " " + Global.VERSION;

    private final String CLIENT_VERSION_MAJOR = CLIENT_VERSION[0];
    private final String CLIENT_VERSION_MINOR = CLIENT_VERSION[1];
    private final String CLIENT_VERSION_PATCH = CLIENT_VERSION[2];

    private final String OS_VERSION = "Android " + android.os.Build.VERSION.SDK_INT;

    private final String ARCH = System.getProperty("os.arch");

    private WebSocketClient mWebSocketClient;
    private String endpointName;
    private String callInsightsKey;
    private String endpointDomain;
    private Timer timer ;
    private Boolean rtpFlagEnabled ;
    private ArrayList<JSONObject> statBuffer = new ArrayList<>();
    private RtpStats rtpStat;
    private Options option;

    public CallInsights(String username, String password, String domain, HashMap setupOptions) {
        endpointName = username;
        endpointDomain = domain;
        initOptions(setupOptions);
        getCallStatsKey(username, password, domain);
    }

    public  void initRTPStats(Context context, EventListener eventListener) {
        rtpStat = new RtpStats(context,eventListener);
    }

    public  void initOptions(HashMap setupOptions) {
        option = new Options(setupOptions);
    }

    private void sendStats(JSONObject stats) {
        try {
            if (!stats.has("xcallUUID") || stats.getString("xcallUUID").isEmpty()) {
                Log.I("No xcallUUID so not sending stats");
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        try {
            if (stats.getString("msg").equals("CALL_SUMMARY") || stats.getString("msg").equals("CALL_ANSWERED") || stats.getString("msg").equals("FEEDBACK") || statBuffer.size() >= Global.rtpBatchSize) {
                if (mWebSocketClient != null && mWebSocketClient.isOpen()) {
                    String json = stats.toString();
                    for (JSONObject stat: statBuffer) {
                        mWebSocketClient.send(stat.toString());
                        Log.I("Call insights Websocket stats sent" + stat);
                    }
                    statBuffer.clear();
                    return;
                }

                URI uri;
                try {
                    uri = new URI(Global.statsWSURL);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    return;
                }

                mWebSocketClient = new WebSocketClient(uri) {
                    @Override
                    public void onOpen(ServerHandshake serverHandshake) {
                        Log.I("Call insights  Websocket Opened");
                        String json = stats.toString();
                        for (JSONObject stat: statBuffer) {
                            mWebSocketClient.send(stat.toString());
                            Log.I("Call insights Websocket stats sent" + stat);
                        }
                        statBuffer.clear();
                    }

                    @Override
                    public void onMessage(String s) {
                    }

                    @Override
                    public void onClose(int i, String s, boolean b) {
                        Log.I("Call insights Websocket Closed" + s);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.I("Call insights Websocket Error " + e.getMessage());
                    }
                };
                Log.I("websocket URI Connect");
                mWebSocketClient.connect();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
    }

    private JSONObject getBasicStatsInfo(SignallingStats stats) {
        JSONObject event = new JSONObject();
        try {
            event.put("userAgent", USER_AGENT);
            event.put("sdkVersionMajor", CLIENT_VERSION_MAJOR);
            event.put("sdkVersionMinor", CLIENT_VERSION_MINOR);
            event.put("sdkVersionPatch", CLIENT_VERSION_PATCH);
            event.put("clientName", "Android");
            event.put("deviceOs", OS_VERSION);
            event.put("devicePlatform", ARCH);
            event.put("domain", endpointDomain);
            event.put("sdkName", SDK_NAME);
            event.put("source", STATS_SOURCE);
            event.put("version", "v1");
            event.put("timeStamp", Utils.getCurrentTimeInMilliSeconds());
            event.put("username", endpointName);
            event.put("callstats_key", callInsightsKey);
            event.put("corelationId", stats.getCallUUID());
            event.put("callUUID", stats.getCallUUID());
            event.put("xcallUUID", stats.getXCallUUID());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return event;
    }

    private void getCallStatsKey(String username, String password, String domain) {
        JSONObject postBody = new JSONObject();
        try {
            postBody.put("username", username);
            postBody.put("password", password);
            postBody.put("domain", domain);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        HttpPostAsyncTask client1 = new HttpPostAsyncTask(postBody, "POST", new HTTPRequestCallback(){
            @Override
            public void onFailure(int StatusCode) {
                Log.D("Did not get insights key" + StatusCode);
            }

            @Override
            public void onResponse(String response) {
                Log.I("Successful call insights response." + response);
                if (response.equals("")) {
                    Log.I("Call insights is not activated.");
                    return;
                }
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    callInsightsKey = jsonResponse.getString("data");
                    rtpFlagEnabled = jsonResponse.getBoolean("is_rtp_enabled");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        client1.execute(Global.statsKeyURL);
    }

    public void sendAnswerEvent(SignallingStats stats, Boolean isIncoming) {
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;
        JSONObject answerEvent = getBasicStatsInfo(stats);
        try {
            answerEvent.put("msg", ANSWERED_EVENT);
            answerEvent.put("setupOptions",option.getOptions().toString());
            if (isIncoming) {
                answerEvent.put("info", ANSWERED_IN_EVENT_INFO);
            } else {
                answerEvent.put("info", ANSWERED_OUT_EVENT_INFO);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(answerEvent);
        sendStats(answerEvent);
    }

    public void sendSummaryEvent(SignallingStats stats) {
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;
        JSONObject summaryEvent = getBasicStatsInfo(stats);
        try {
            summaryEvent.put("msg", SUMMARY_EVENT);
            summaryEvent.put("signalling", stats.getSignallingData());
            summaryEvent.put("setupOptions",option.getOptions().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(summaryEvent);
        sendStats(summaryEvent);
    }

    public void sendFeedbackEvent(JSONObject feedbackEvent) {
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;

        try {
            feedbackEvent.put("domain", Global.DOMAIN);
            feedbackEvent.put("msg", FEEDBACK_EVENT);
            feedbackEvent.put("source", STATS_SOURCE);
            feedbackEvent.put("sdkVersion", Global.VERSION);
            feedbackEvent.put("timeStamp", Utils.getCurrentTimeInMilliSeconds());
            feedbackEvent.put("userName", endpointName);
            feedbackEvent.put("version",  VERSION);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(feedbackEvent);
        sendStats(feedbackEvent);
    }

    public void sendRtpStats(SignallingStats stats){
        if ((callInsightsKey == null || callInsightsKey.isEmpty()) && !option.isEnableTacking()) return;
        Log.I("rtpFlagEnabled : "+rtpFlagEnabled+"\t"+"EnableTracking : "+option.isEnableTacking());
        if (!option.isEnableTacking()){
            Log.I("EnableTracking Flag is not set to True");
            return;
        }
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                JSONObject rtpStats = rtpStat.getRTPStats();
                if (rtpStats != null) {
                    try {
                        rtpStats.put("msg", CALL_STATS);
                        rtpStats.put("callstats_key", callInsightsKey);
                        rtpStats.put("corelationId", stats.getCallUUID());
                        rtpStats.put("callUUID", stats.getCallUUID());
                        rtpStats.put("xcallUUID", stats.getXCallUUID());
                        rtpStats.put("username", endpointName);
                        rtpStats.put("source", STATS_SOURCE);
                        rtpStats.put("timeStamp", Utils.getCurrentTimeInMilliSeconds());
                        rtpStats.put("domain", endpointDomain);
                        rtpStats.put("version", VERSION);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (rtpFlagEnabled == null  || !rtpFlagEnabled){
                        Log.D("RTP Flag is not enabled");
                    }else {
                        statBuffer.add(rtpStats);
                        sendStats(rtpStats);
                    }
                }
            }
            }, Global.firstRTPCall, Global.rtpColectionFequency);

    }

    public void stopTimer() {
        if ((callInsightsKey == null || callInsightsKey.isEmpty()) && !option.isEnableTacking()) return;
        if (this.mWebSocketClient != null && this.mWebSocketClient.isOpen()) {
            this.mWebSocketClient.close();
        }
        if (this.timer != null) {
            this.timer.cancel();
            Log.I("Timer stopped");
        }
    }

    public String getStatsKey() {
        return callInsightsKey;
    }
}
