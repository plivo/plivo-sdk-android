package com.plivo.endpoint;

import com.plivo.endpoint.backend.plivo;

import java.util.*;

public class Incoming extends IO {
	private String fromContact;
	private String callId;
	private String header;

	public Incoming(int pjsuaCallId, String callId, String fromContact, String toContact, String header) {
		this.callId = callId;
		this.fromContact = fromContact;
		this.toContact = toContact;
		this.pjsuaCallId = pjsuaCallId;
		this.header = header;
		this.isOnMute = false;
	}

	/**
	 * Answer an incoming call
	 */
	public boolean answer() {
		Log.D("answer" );
		if (!NetworkChangeReceiver.isConnected()) {
			return false;
		}
		if (isActive()) {
			Log.E("Cannot answer: Call is already answered. Try hangup");
			return false;
		}

		try {
			if (plivo.Answer(this.pjsuaCallId) != 0) {
				Log.E("Cannot answer this call again. This call is expired now. Make sure you are calling answer() on right 'Incoming' object.");
                setActive(false);
				return false;
			}
            setActive(true);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
            Log.E("answer failed");
		}
        setActive(false);
		return false;
	}

	public boolean reject() {
		Log.D("reject");
		if (!NetworkChangeReceiver.isConnected()) {
			return false;
		}
		if (isActive()) {
			Log.E("Cannot reject: Call is already active. Try hangup");
			return false;
		}

		try {
            if (plivo.Reject(this.pjsuaCallId) != 0) {
				Log.E("Cannot reject this call again. This call is expired now. Make sure you are calling reject on right 'Incoming' object.");
            	return false;
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
            Log.E("reject failed");
		}
		return false;
	}

	public String getHeader() {
		return header;
	}

	public Map<String, String> getHeaderDict(){
		Map<String, String> map = Utils.stringToMap(header);
		Log.D("getHeaderDict: " + map);
		return map;
	}

	// From format: <sip:android1181024115518@phone.plivo.com>;tag=1r7387ubi7
	public String getFromContact() {
		return fromContact;
	}

	public String getFromSip() {
		String from = getFromContact();
		if (!from.contains("<") || !from.contains("@")) return null;

		String sipName = from.substring(from.indexOf("<") + 1, from.indexOf("@"));
		Log.D("getFromSip: " + sipName);
		return sipName;
	}

	public String getToSip() {
		String to = getToContact();
		if (!to.contains("<") || !to.contains("@")) return null;

		String sipId = to.substring(to.indexOf("<") + 1, to.indexOf("@"));
		Log.D("getToSip: " + sipId);
		return sipId;
	}

	public String getCallId() {
		return callId;
	}
}
