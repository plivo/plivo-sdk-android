package com.plivo.endpoint;

import android.content.Context;

import com.plivo.endpoint.backend.PlivoAppCallback;
import com.plivo.endpoint.backend.plivo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BackendListener extends PlivoAppCallback {
    /**
     * Endpoint object: root of the sdk usage
     */
    private Endpoint endpoint;

    /**
     * EventListener interface that need to be implemented by user.
     */
    private EventListener eventListener;

    /**
     * Holds the outgoing/incoming calls for multiple call handlings.
     */
    private List<Outgoing> outgoingList;
    private List<Incoming> incomingList;

    private CallInsights callInsights;

    private boolean isLoggedIn;
    private SignallingStats signallingStats;
    private String lastXCallUUID;
    private String lastCallUUID;
    private String lastStatsKey;
    private Context context;
    private long maxAverageBitrate = Global.maxAverageBitrate;

    public BackendListener(boolean debug, Endpoint endpoint, EventListener eventListener) {
        super();

        this.endpoint = endpoint;
        this.eventListener = eventListener;
        this.isLoggedIn = false;
    }

    private void logDebug(String str) {
        Log.D("[backend-logs]" + str);
    }

    private void addToIncomingList(Incoming incoming) {
        if (incomingList == null) {
            incomingList = new ArrayList<>();
        }

        incomingList.add(incoming);
        logDebug("addToIncomingList " + incoming.getCallId());
    }

    private void addToOutgoingList(Outgoing outgoing) {
        if (outgoingList == null) {
            outgoingList = new ArrayList<>();
        }

        outgoingList.add(outgoing);
        logDebug("addToOutgoingList " + outgoing.getCallId());
    }

    private Incoming getIncoming(String callId) {
        if (incomingList == null) return null;

        try {
            for (Incoming call : incomingList) {
                if (call.getCallId().equals(callId)) {
                    return call;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("getIncoming failed");
        }

        return null;
    }

    private Outgoing getOutgoing(String callId) {
        if (outgoingList == null) return null;

        try {
            for (Outgoing call : outgoingList) {
                if (call.getCallId().equals(callId)) {
                    return call;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("getOutgoing failed");
        }

        return null;
    }



    public String getLastXCallUUID(){
        return this.lastXCallUUID;
    }

    public String getXCallUUID(){
        if (signallingStats != null) {
            return signallingStats.getXCallUUID();
        }
        return null;
    }

    public String getLastCallUUID(){
        return this.lastCallUUID;
    }

    public String getCallUUID(){
        if (signallingStats != null) {
            return signallingStats.getCallUUID();
        }
        return null;
    }

    public String getLastStatsKey(){
        return this.lastStatsKey;
    }

    public String getStatsKey(){
        if (callInsights != null) {
            return callInsights.getStatsKey();
        }
        return null;
    }


    boolean isCallAvailable() {
        return (outgoingList != null && outgoingList.size() > 0) ||
                (incomingList != null && incomingList.size() > 0);
    }

    public  void initCallInsights(String username, String password, String domain, Context context, HashMap setupOptions) {
        this.context =  context;
        setupOptions.put("maxAverageBitrate", maxAverageBitrate);
        callInsights = new CallInsights(username, password, domain, setupOptions);
    }

    public void sendFeedbackStats(ArrayList<String> issueList, String xcallUUID, Integer rating, String notes) {
        String qualityFeedback = issueList.toString().replace("[", "").replace("]", "")
                .replace(", ", ",");

        JSONObject feedbackData = new JSONObject();
        JSONObject info = new JSONObject();
        try {
            info.put("overall",rating);
            info.put("comment",qualityFeedback+ " "+notes);
            if (xcallUUID == getLastXCallUUID()) {
                feedbackData.put("callUUID", getLastCallUUID());
                feedbackData.put("callstats_key", getLastStatsKey());
                feedbackData.put("corelationId", getLastCallUUID());
                feedbackData.put("xcallUUID", getLastXCallUUID());
            } else {
                feedbackData.put("callUUID", getCallUUID());
                feedbackData.put("callstats_key", getStatsKey());
                feedbackData.put("corelationId", getCallUUID());
                feedbackData.put("xcallUUID", getXCallUUID());
            }
            feedbackData.put("info", info);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
        callInsights.sendFeedbackEvent(feedbackData);
    }

    public void checkBitrate(HashMap setupOptions) {
        try {
            if (setupOptions.containsKey("maxAverageBitrate") && !(setupOptions.get("maxAverageBitrate") instanceof String)) {
                long bitrate = Long.valueOf(String.valueOf(setupOptions.get("maxAverageBitrate")));
                if (bitrate >= Global.minAverageBitrate && bitrate <= Global.maxAverageBitrate) {
                    maxAverageBitrate = bitrate;
                } else {
                    Log.E("maxAverageBitrate should be in between "+ Global.minAverageBitrate+ " and "+ Global.maxAverageBitrate);
                }
            }
        } catch (Exception e) {
            Log.E(e.getMessage());
        }
        plivo.updateOpusBitrate(maxAverageBitrate);
    }

    @Override
    public void onStarted(String msg) {
        logDebug("onStarted : " + msg);
    }

    @Override
    public void onStopped(int restart) {
        logDebug("onStopped: " + restart);
    }

    @Override
    public void onLogin() {
        logDebug("onLogin");

        if (!this.isLoggedIn) {
            this.isLoggedIn = true;
            endpoint.setRegistered(true);
            if(eventListener != null) {
                eventListener.onLogin();
            }
        }
    }

    @Override
    public void onLogout() {
        logDebug("onLogout");
        this.isLoggedIn = false;
        endpoint.setRegistered(false);
        if (eventListener != null) {
            eventListener.onLogout();
        }
    }

    @Override
    public void onLoginFailed() {
        // this.isLoggedIn = false;
        if (eventListener != null && !this.isLoggedIn) {
            Log.E("onLoginFailed");
            eventListener.onLoginFailed();
        }
    }

    @Override
    public void onDebugMessage(String message) {
        logDebug("[onDebugMessage]" + message);
    }

    @Override
    public void onDebugMessage(int element) {
        logDebug("[onDebugMessage]" + element);
    }

    @Override
    public void onIncomingCall(int pjsuaCallId, String callId, String fromContact, String toContact, String header, String xCallUUID) {
        logDebug("onIncomingCall " + pjsuaCallId + " callId: " + callId + " header: " + header + " fromContact: " + fromContact + " toContact: " + toContact);

        Incoming incoming = new Incoming(pjsuaCallId, callId, fromContact, toContact, header);
        // currently allowing only 1 incoming call.
        if (isCallAvailable()) {
            plivo.Reject(pjsuaCallId);
            return;
        }

        addToIncomingList(incoming);
        if (eventListener != null)
            eventListener.onIncomingCall(incoming);

        signallingStats = new SignallingStats();
        signallingStats.setXCallUUID(xCallUUID);
        signallingStats.setCallUUID(callId);
    }

    @Override
    public void onIncomingCallRinging(int pjsuaCallId, String callId) {
        logDebug("onIncomingCallRinging " + pjsuaCallId + " callId: " + callId);
        Incoming incoming = getIncoming(callId);
        if (incoming != null && signallingStats != null) {
            signallingStats.setCallProgressTime();
        }
    }

    @Override
    public void onIncomingCallConnecting(int pjsuaCallId, String callId) {
        logDebug("onIncomingCallConnecting " + pjsuaCallId + " callId: " + callId);
        Incoming incoming = getIncoming(callId);
        if (incoming != null && signallingStats != null) {
            signallingStats.setAnswerTime();
        }
    }

    @Override
    public void onIncomingCallAnswered(int pjsuaCallId, String callId) {
        logDebug("onIncomingCallAnswered " + pjsuaCallId + " callId: " + callId);
        Incoming incoming = getIncoming(callId);
        if (incoming != null && signallingStats != null) {
            signallingStats.setCallConfirmedTime();
            callInsights.initRTPStats(this.context,eventListener);
            callInsights.sendAnswerEvent(signallingStats, true);
            callInsights.sendRtpStats(signallingStats);
        }
    }

    @Override
    public void onIncomingCallHangup(int pjsuaCallId, String callId) {
        logDebug("onIncomingCallHangup " + pjsuaCallId + " callId: " + callId);
        Incoming incoming = getIncoming(callId);
        if (incoming != null && eventListener != null) {
            eventListener.onIncomingCallHangup(incoming);
            incoming.setActive(false);
            incomingList.remove(incoming);
            callInsights.stopTimer();
        }
        if (signallingStats != null) {
            signallingStats.setHangupTime();
            callInsights.sendSummaryEvent(signallingStats);
            this.lastXCallUUID=signallingStats.getXCallUUID();
            this.lastCallUUID=signallingStats.getCallUUID();
            this.lastStatsKey = callInsights.getStatsKey();
            signallingStats = null;
        }
    }

    @Override
    public void onIncomingCallRejected(int pjsuaCallId, String callId) {
        logDebug("onIncomingCallRejected " + pjsuaCallId + " callId: " + callId);
        Incoming incoming = getIncoming(callId);
        if (incoming != null && eventListener != null) {
            eventListener.onIncomingCallRejected(incoming);
            incoming.setActive(false);
            incomingList.remove(incoming);
            if (signallingStats != null) {
                signallingStats.setHangupTime();
                callInsights.sendSummaryEvent(signallingStats);
                this.lastXCallUUID = signallingStats.getXCallUUID();
                this.lastCallUUID=signallingStats.getCallUUID();
                this.lastStatsKey = callInsights.getStatsKey();
                signallingStats = null;
            }
        }
    }
    @Override
    public void onIncomingCallInvalid(int pjsuaCallId, String callId) {
        Log.E("onIncomingCallInvalid " + pjsuaCallId + " callId: " + callId);
        Incoming incoming = getIncoming(callId);
        if (incoming != null && eventListener != null) {
            eventListener.onIncomingCallInvalid(incoming);
            incoming.setActive(false);
            incomingList.remove(incoming);
            if (signallingStats != null) {
                signallingStats.setHangupTime();
                callInsights.sendSummaryEvent(signallingStats);
                this.lastCallUUID=signallingStats.getXCallUUID();
                signallingStats = null;
            }
        }
    }

    @Override
    public void onOutgoingCall(int pjsuaCallId, String callId) {
        logDebug("onOutgoingCall " + pjsuaCallId + " callId: " + callId);
        Outgoing out = this.endpoint.getOutgoing();
        out.pjsuaCallId = pjsuaCallId;
        out.setCallId(callId);
        addToOutgoingList(out);
        if (eventListener != null) {
            eventListener.onOutgoingCall(out);
        }
        signallingStats = new SignallingStats();
    }

    @Override
    public void onOutgoingCallRinging(int pjsuaCallId, String callId, String xCallUUID) {
        logDebug("onOutgoingCallRinging " + pjsuaCallId + " callId: " + callId + " " +  xCallUUID);
        Outgoing outgoing = getOutgoing(callId);
        if (outgoing != null && signallingStats != null && eventListener != null) {
            eventListener.onOutgoingCallRinging(outgoing);
            signallingStats.setRingStartTime();
            signallingStats.setPostDialDelay();
            signallingStats.setXCallUUID(xCallUUID);
            signallingStats.setCallUUID(callId);
        }
    }

    @Override
    public void onOutgoingCallConnecting(int pjsuaCallId, String callId, String xCallUUID) {
        logDebug("onOutgoingCallConnecting " + pjsuaCallId + " callId: " + callId);
        Outgoing outgoing = getOutgoing(callId);
        if (outgoing != null && eventListener != null) {
            signallingStats.setXCallUUID(xCallUUID);
            signallingStats.setCallUUID(callId);
            signallingStats.setAnswerTime();
        }
    }

    @Override
    public void onOutgoingCallAnswered(int pjsuaCallId, String callId) {
        logDebug("onOutgoingCallAnswered " + pjsuaCallId + " callId: " + callId);
        Outgoing outgoing = getOutgoing(callId);
        if (outgoing != null && eventListener != null) {
            eventListener.onOutgoingCallAnswered(outgoing);
            signallingStats.setCallConfirmedTime();
            callInsights.initRTPStats(this.context,eventListener);
            callInsights.sendAnswerEvent(signallingStats, false);
            callInsights.sendRtpStats(signallingStats);
        }
    }

    @Override
    public void onOutgoingCallHangup(int pjsuaCallId, String callId) {
        logDebug("onOutgoingCallHangup " + pjsuaCallId + " callId: " + callId);
        Outgoing outgoing = getOutgoing(callId);
        if (outgoing != null && eventListener != null) {
            eventListener.onOutgoingCallHangup(outgoing);
            callInsights.stopTimer();
            outgoing.setActive(false);
            outgoingList.remove(outgoing);
            if (signallingStats != null) {
                signallingStats.setHangupTime();
                //signallingStats.setXCallUUID(xCallUUID);
                signallingStats.setCallUUID(callId);
                callInsights.sendSummaryEvent(signallingStats);
                this.lastXCallUUID=signallingStats.getXCallUUID();
                this.lastCallUUID=signallingStats.getCallUUID();
                this.lastStatsKey = callInsights.getStatsKey();
                signallingStats = null;
            }

        }
    }
    @Override
    public void onOutgoingCallRejected(int pjsuaCallId, String callId, String xCallUUID) {
        logDebug("onOutgoingCallRejected " + pjsuaCallId + " callId: " + callId);
        Outgoing outgoing = getOutgoing(callId);
        if (outgoing != null && eventListener != null) {
            eventListener.onOutgoingCallRejected(outgoing);
            outgoing.setActive(false);
            outgoingList.remove(outgoing);
            if (signallingStats != null) {
                signallingStats.setHangupTime();
                signallingStats.setXCallUUID(xCallUUID);
                signallingStats.setCallUUID(callId);
                callInsights.sendSummaryEvent(signallingStats);
                this.lastXCallUUID=signallingStats.getXCallUUID();
                this.lastCallUUID=signallingStats.getCallUUID();
                this.lastStatsKey = callInsights.getStatsKey();
                signallingStats = null;
            }

        }
    }

    @Override
    public void onOutgoingCallInvalid(int pjsuaCallId, String callId) {
        Log.E("onOutgoingCallInvalid " + pjsuaCallId + " callId: " + callId);
        Outgoing outgoing = getOutgoing(callId);
        if (outgoing != null && eventListener != null) {
            eventListener.onOutgoingCallInvalid(outgoing);
            outgoing.setActive(false);
            outgoingList.remove(outgoing);
            if (signallingStats != null) {
                signallingStats.setHangupTime();
                callInsights.sendSummaryEvent(signallingStats);
                this.lastXCallUUID=signallingStats.getXCallUUID();
                this.lastCallUUID=signallingStats.getCallUUID();
                this.lastStatsKey = callInsights.getStatsKey();
                signallingStats = null;
            }
        }
    }

    @Override
    public void onIncomingDigitNotification(int digit) {
        logDebug("onIncomingDigitNotification " + digit);
        String new_digit = "";
        try {
            new_digit = Integer.toString(digit);
        }  catch (Exception e) {
            e.printStackTrace();
        }

        if (eventListener != null) {
            eventListener.onIncomingDigitNotification(new_digit);
        }
    }

}

