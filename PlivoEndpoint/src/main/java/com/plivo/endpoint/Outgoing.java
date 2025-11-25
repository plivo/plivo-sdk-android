package com.plivo.endpoint;

import android.text.TextUtils;

import com.plivo.endpoint.backend.plivo;

import java.util.*;

public class Outgoing extends IO {
	private Endpoint endpoint;

	public Outgoing(Endpoint endpoint) {
		this.endpoint = endpoint;
		isOnMute = false;
	}

	/**
	 * Call an endpoint.
	 * @param dest
	 * @return
	 */
	public boolean call(String dest) {
		String sipUri;
		Log.D("call " + dest);
		if (!NetworkChangeReceiver.isConnected()) {
			return false;
		}
		if (TextUtils.isEmpty(dest)) {
			Log.E("Call Cannot be Placed. Entered SIP endpoint is empty.");
			return false;
		}
		if (!endpoint.getRegistered()) {
			Log.E("Cannot make call() without endpoint already logged in.");
			return false;
		}

		if(!dest.startsWith("sip:")){
			sipUri = "sip:" + dest + "@" + Global.DOMAIN;
		} else {
			sipUri = dest + "@" + Global.DOMAIN;
		}
		setToContact(sipUri);

		try {
            if (plivo.Call(sipUri) != 0) {
                Log.E("Call attempt failed. Check you destination address");
				setActive(false);
                return false;
            }
            Log.D("Call Placed");
            setActive(true);
            return true;
        } catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
		    e.printStackTrace();
			Log.E("call failed");
        }

		setActive(false);
        return false;
	}

	/* Call method with headers */
	/* the map during initialization should be ConcurrentHashMap */
	public boolean callH(String dest, Map<String, String> headers) {
		String sipUri;
		Log.D("callH " + dest + "headers:" + headers);
		if (!NetworkChangeReceiver.isConnected()) {
			return false;
		}
		if (TextUtils.isEmpty(dest)) {
			Log.E("Call Cannot be Placed. Entered SIP endpoint is empty.");
			return false;
		}
		if (!endpoint.getRegistered()) {
			Log.E("Cannot make callH() without endpoint already logged in.");
			return false;
		}

		if(!dest.startsWith("sip:")){
			sipUri = "sip:" + dest + "@" + Global.DOMAIN;
		} else {
			sipUri = dest + "@" + Global.DOMAIN;
		}
		setToContact(sipUri);

        if (!Utils.validateCallHeaders(headers)) {
		    Log.W("No Valid Header. Placing call without headers..");
        }

		String headers_str = Utils.mapToString(headers);
		try {
			int callResult = TextUtils.isEmpty(headers_str) ? plivo.Call(sipUri) : plivo.CallH(sipUri, headers_str);
			if (callResult != 0) {
				Log.E("Call attempt failed. Check you destination address");
				setActive(false);
				return false;
			}
			Log.D("Call Placed");
			setActive(true);
			return true;
		} catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
		    e.printStackTrace();
			Log.E("callH failed");
        }

		setActive(false);
        return false;
	}

	// retaining this to not break the backward compatibility
	public static void checkSpecialCharacters(Map<String, String> map) {
		Utils.checkSpecialCharacters(map);
	}

	public String getCallId() {
		return callId;
	}

	public void setCallId(String callId) {
		this.callId = callId;
	}

	public String toString() {
		String str = "[Plivo Outgoing Call]callId = " + this.callId + ". to = " + this.toContact;
		return str;
	}
}
