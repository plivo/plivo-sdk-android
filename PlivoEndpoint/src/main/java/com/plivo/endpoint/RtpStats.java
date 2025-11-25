package com.plivo.endpoint;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;


import com.plivo.endpoint.backend.plivo;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;


public class RtpStats {
    private Context context;
    private HashMap mediaMetricMap;
    private HashMap mediaWarning;
    private EventListener eventListener;
    private static  int count = 0;
    double localAudioLevels = 0.0f;
    double remoteAudioLevels = 0.0f;
    ArrayList <Double> jitterLocalList,jitterRemoteList,rttList,mosList,packetLossLocalList,packetLossRemoteList,audioLevelLocalList,audioLevelRemoteList, microphoneAccess;
    String codec;

    public RtpStats(Context context, EventListener eventListener){
        this.eventListener = eventListener;
        this.context = context;
        mediaMetricMap = new HashMap();
        mediaWarning = new HashMap();
        jitterLocalList =  new ArrayList<Double>();
        jitterRemoteList = new ArrayList<Double>();
        rttList = new ArrayList<Double>();
        mosList = new ArrayList<Double>();
        packetLossLocalList = new ArrayList<Double>();
        packetLossRemoteList = new ArrayList<Double>();
        audioLevelLocalList = new ArrayList<Double>();
        audioLevelRemoteList = new ArrayList<Double>();
        mediaMetricMap.put("jitter_local",jitterLocalList);
        mediaMetricMap.put("jitter_remote",jitterRemoteList);
        mediaMetricMap.put("rtt",rttList);
        mediaMetricMap.put("mos",mosList);
        mediaMetricMap.put("packectloss_local",packetLossLocalList);
        mediaMetricMap.put("packectloss_remote",packetLossRemoteList);
        mediaMetricMap.put("audiolevel_local",audioLevelLocalList);
        mediaMetricMap.put("audiolevel_remote",audioLevelRemoteList);
        mediaMetricMap.put("microphone_access",microphoneAccess);
        mediaWarning.put("jitter_local",false);
        mediaWarning.put("jitter_remote",false);
        mediaWarning.put("rtt",false);
        mediaWarning.put("mos",false);
        mediaWarning.put("packectloss_local",false);
        mediaWarning.put("packectloss_remote",false);
        mediaWarning.put("audiolevel_local",false);
        mediaWarning.put("audiolevel_remote",false);
        mediaWarning.put("microphone_access",false);
        codec = "PCMU";
    }

    public String getNetworkType() {
        if(context!=null){
            if (PackageManager.PERMISSION_DENIED != context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_WIFI_STATE)) {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info == null || !info.isConnected())
                    return "unknown"; //not connected
                if (info.getType() == ConnectivityManager.TYPE_WIFI)
                    return "wifi";
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    return "mobile";
                }
            }
            else {
                Log.I("Currently network permissions are not allowed");
            }
        }
        return "unknown";
    }


    public  String getNetworkEffectiveType() {
        return "unknown";
    }

    public  Integer getNetworkDownlinkSpeed(){
        if (getNetworkType()=="mobile"){
            return -1;
        }
        if(context != null) {
            if (PackageManager.PERMISSION_DENIED != context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_WIFI_STATE)) {
                WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                int linkSpeed = wifiManager.getConnectionInfo().getRssi();
                int level = WifiManager.calculateSignalLevel(linkSpeed, 5);
                return level;
            }
            else{
                Log.I("Currently network permissions are not allowed");
            }
        }
        return -1;
    }
    public String getLocalStats(){
        return plivo.getLocalStats();
    }

    public JSONObject getAudioLevels(){
        JSONObject audioLevelsMap = new JSONObject();
        try{
            audioLevelsMap =  new JSONObject(plivo._getAudioLevels());
        }
        catch (JSONException exception){
            exception.printStackTrace();
        }
        return audioLevelsMap;
    }

    public String getRemoteStats(){
        return plivo.getRemoteStats();
    }

    private JSONObject processStats(JSONObject stats){
        try {
            double fractionLoss = stats.getDouble("fractionLoss");
            double jitter = stats.getDouble("jitter");
            stats.put("fractionLoss",(double) Math.round(fractionLoss*1000)/1000);
            stats.put("jitter",(double) Math.round(jitter*1000)/1000);
            if(stats.has("rtt")) {
                double rtt = stats.getDouble("rtt");
                stats.put("rtt",(double) Math.round(rtt*1000)/1000);
            }
            if(stats.has("mos")) {
                if (stats.get("mos").equals("null")){
                    stats.put("mos", null);
                }else {
                    double mos = stats.getDouble("mos");
                    stats.put("mos", (double) Math.round(mos * 1000) / 1000);
                }
            }
        }catch (Exception exception){
            exception.printStackTrace();
        }
        return stats;
    }

    public void fetchAudioLevels(){
        JSONObject audioLevelsMap = new JSONObject();
        try {
                audioLevelsMap = getAudioLevels();
                double localTemp = audioLevelsMap.getDouble("local");
                double remoteTemp = audioLevelsMap.getDouble("remote");
                Log.D(count + "audiolevelLocal : " + localTemp);
                Log.D(count + "audiolevelRemote: " + remoteTemp);
                localAudioLevels = localAudioLevels + localTemp;
                remoteAudioLevels = remoteAudioLevels + remoteTemp;
        }catch(JSONException exception){
            exception.printStackTrace();
        }
    }


    public Double sendAlertCallback(ArrayList metricsObject, String type){
        Integer count = 0;
        Double total = 0.0;
        for (Object value: metricsObject
             ) {
            Double val = (Double)value;
            if(type=="rtt"){
                if( val > 400){
                    count = count + 1;
                    total = total + val;
                }
            }
            else if(type=="mos"){
                if(val < 3.5){
                    count = count + 1;
                    total = total + val;
                }
            }
            else if(type=="jitter_local" || type=="jitter_remote"){
                if(val > 30){
                    count = count + 1;
                    total = total + val;
                }
            }else if(type=="packectloss_local" || type =="packectloss_remote"){
                if (codec !=null && codec.equals("PCMU")){
                    if(val >= 0.02){
                        count = count+1;
                        total = total + val;
                    }
                }else {
                    if(val >= 0.10){
                        count = count+1;
                        total =  total + val;
                    }
                }
            }
        }
         if (count>=2){
             return total/count;
         }else{
             return -1.0;
         }
    }

    public void sendMedialMetricsCallBack(String group, String level, String type, Double value, Boolean active, String description, String stream){
        Log.I("Sending media metrics");
        HashMap messageTemplate = new HashMap();
        messageTemplate.put("group",group);
        messageTemplate.put("level",level);
        messageTemplate.put("type",type);
        messageTemplate.put("value",value);
        messageTemplate.put("active", active);
        messageTemplate.put("description", description);
        messageTemplate.put("stream", stream);
        eventListener.mediaMetrics(messageTemplate);
    }

    public void callMediaMatrices(String type, Double value, String message, String description, String stream){
        ArrayList metricsObject = (ArrayList) mediaMetricMap.get(type);
        metricsObject.add(value);
        if (metricsObject.size()==3){
            Double average = sendAlertCallback(metricsObject,type);
            if(average !=-1.0){
                mediaWarning.put(type,true);
                sendMedialMetricsCallBack("network", "warning", message, average,true,description, stream);

            }else {
                if((Boolean)mediaWarning.get(type)){
                    mediaWarning.put(type,false);
                    sendMedialMetricsCallBack("network", "warning", message, 0.0,false,description, stream);
                }
            }
            metricsObject.remove(0);
        }

    }

    public void processAudioLevels(String type, Double value, String message, String description, String stream){
        ArrayList metricsObject = (ArrayList) mediaMetricMap.get(type);
        if(metricsObject.size()==2) {
            metricsObject.add(value);
            Double audioLevelVolume = 0.0;
            //Count the entries of each audio levels

            HashMap<Double, Integer> audioLevelCounts = new HashMap();

            for (Object audioLevel : metricsObject
            ) {
                Double key = (Double) audioLevel;
                if (audioLevelCounts.containsKey(key)) {
                    Integer val = audioLevelCounts.get(key);
                    audioLevelCounts.put(key, val + 1);
                } else {
                    audioLevelCounts.put(key, 1);
                }
                if (audioLevelCounts.get(key) >= 2) {
                    audioLevelVolume = key;
                }
            }
            if (audioLevelVolume == -100) {
                mediaWarning.put(type, true);
                Log.I("Audio mute detected for " + type);
                sendMedialMetricsCallBack("network", "warning", message, audioLevelVolume,true,description, stream);
            } else {
                if ((Boolean)mediaWarning.get(type)) {
                    mediaWarning.put(type, false);
                    sendMedialMetricsCallBack("network", "warning", message, 0.0,false,description, stream);
                }
            }
        }else{
            metricsObject.add(value);
        }
        if(metricsObject.size()==3){
            metricsObject.remove(0);
        }
    }

    public void checkMicrophoneAccess(String type, int bytes, double audioLevel, String message, String description, String stream){
        if(bytes == 0 && audioLevel == -100){
            mediaWarning.put(type,true);
            sendMedialMetricsCallBack("network", "warning", message, 0.0,true,description, stream);
        }else{
            if((Boolean)mediaWarning.get(type)){
                mediaWarning.put(type,false);
                sendMedialMetricsCallBack("network", "warning", message, 0.0,false,description, stream);
            }
        }
    }

    public void printMediaMetric(JSONObject localStats, JSONObject remoteStats) {
        try {
            callMediaMatrices("rtt", localStats.getDouble("rtt"), "high_rtt", "high latency detected, can result delay in audio", null);
            callMediaMatrices("mos", localStats.getDouble("mos"), "low_mos", "low Mean Opinion Score (MOS)", null);
            callMediaMatrices("jitter_local", localStats.getDouble("jitter"), "high_jitter", "high jitter detected due to network congestion, can result in audio quality problems", "local");
            callMediaMatrices("jitter_remote", remoteStats.getDouble("jitter"), "high_jitter", "high jitter detected due to network congestion, can result in audio quality problems", "remote");
            callMediaMatrices("packectloss_local",localStats.getDouble("fractionLoss"),"high_packetloss","high packet loss is detected on media stream, can result in choppy audio or dropped call","local");
            callMediaMatrices("packectloss_local",remoteStats.getDouble("fractionLoss"),"high_packetloss","high packet loss is detected on media stream, can result in choppy audio or dropped call","remote");
            processAudioLevels("audiolevel_local", localAudioLevels, "no_audio_received", "no audio packets received", "local");
            processAudioLevels("audiolevel_remote", remoteAudioLevels, "no_audio_received", "no audio packets received", "remote");
            checkMicrophoneAccess("microphone_access", localStats.getInt("bytesSent"), localAudioLevels, "no_microphone_access", "Access to microphone not given", null);
        } catch (JSONException exception) {
            exception.printStackTrace();
        }
    }

    public String getCodec(String codec) {
        if(codec.toLowerCase().startsWith("opus")) {
            return "opus";
        } else if (codec.toLowerCase().startsWith("pcmu")) {
            return "pcmu";
        } else {
            return codec;
        }
    }

    public JSONObject getRTPStats(){
        if(count!=5){
            count = count + 1;
            fetchAudioLevels();
            return null;
        }else {
            count = 1;
            localAudioLevels = localAudioLevels / 5.0f;
            remoteAudioLevels = remoteAudioLevels / 5.0f;
            if(localAudioLevels!=0.0f){
                localAudioLevels = 20 * Math.log10(localAudioLevels/255.0);
            }else{
                localAudioLevels = -100.0f;
            }
            if(remoteAudioLevels!=0.0f){
                remoteAudioLevels = 20 * Math.log10(remoteAudioLevels/255.0);
            }else{
                remoteAudioLevels = -100.0f;
            }
            JSONObject rtpStats = new JSONObject();
            try {
                JSONObject localStats = processStats(new JSONObject(getLocalStats()));
                String codec = localStats.getString("codec");
                JSONObject remoteStats = processStats(new JSONObject(getRemoteStats()));
                localStats.put("audioLevel", localAudioLevels);
                remoteStats.put("audioLevel", remoteAudioLevels);
                rtpStats.put("codec", getCodec(codec));
                localStats.remove("codec");
                rtpStats.put("local", localStats);
                rtpStats.put("remote", remoteStats);
                rtpStats.put("networkDownlinkSpeed", getNetworkDownlinkSpeed());
                rtpStats.put("networkType", getNetworkType());
                rtpStats.put("networkEffectiveType", getNetworkEffectiveType());
                printMediaMetric(localStats,remoteStats);
                localAudioLevels = 0.0f;
                remoteAudioLevels = 0.0f;
                fetchAudioLevels();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return rtpStats;
        }
    }
}
