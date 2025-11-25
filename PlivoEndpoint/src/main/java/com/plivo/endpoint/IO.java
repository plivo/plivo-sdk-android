package com.plivo.endpoint;

import android.text.TextUtils;

import com.plivo.endpoint.backend.plivo;

class IO {
    private static final int MAX_DTMF_DIGITS = 24;
    protected String toContact;
    protected String callId;
    protected int pjsuaCallId;

    protected boolean isOnMute;
    protected boolean isOnHold;

    private boolean isActive;

    /**
     * Send DTMF digit
     * @param digit
     */
    public boolean sendDigits(String digit) {
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot send DTMF digit when call is not active.");
            return false;
        }
        if (isOnHold) {
            Log.E("Cannot send DTMF digit when call is on hold.");
            return false;
        }
        if (TextUtils.isEmpty(digit) || digit.length() > MAX_DTMF_DIGITS) {
            Log.E("digit.length() is empty or greater than MAX_LIMIT 24 "+ digit.length());
            return false;
        }

        if (!checkDtmfDigit(digit)) {
            Log.E("Invalid DTMF digit: " + digit);
            return false;
        }

        Log.D("pjsuaCallId: " + pjsuaCallId + " send DTMF digit: " + digit);
        try {
            plivo.SendDTMF(this.pjsuaCallId, digit);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Hang up a call
     */
    public boolean hangup() {
        Log.D("hangup");
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot hangup: Call is not active.");
            return false;
        }

        try {
            plivo.Hangup(this.pjsuaCallId);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("hangup failed");
        }
        return false;
    }

    public boolean mute() {
        Log.D("mute");
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot mute() without an active call.");
            return false;
        }
        if (isOnMute) {
            Log.D("Already on mute");
            return true;
        }

        try {
            if (plivo.Mute(pjsuaCallId) != 0) {
                Log.E("mute failed");
                return false;
            }

            isOnMute = true;
            Log.D("mute success");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("mute failed");
        }

        return false;
    }

    public boolean unmute() {
        Log.D("unmute");
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot unmute() without an active call.");
            return false;
        }
        if (!isOnMute) {
            Log.D("Already unmute");
            return true;
        }

        try {
            if (plivo.UnMute(pjsuaCallId) != 0) {
                Log.E("unmute failed");
                return false;
            }

            isOnMute = false;
            Log.D("unmute success");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("unmute failed");
        }

        return false;
    }

    public boolean hold() {
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot hold() without an active call.");
            return false;
        }
        if (isOnHold) {
            Log.D("Already hold");
            return true;
        }

        Log.D("hold pjsuaCallId " + pjsuaCallId);
        try {
            if (plivo.Hold(pjsuaCallId) != 0) {
                Log.E("hold failed");
                return false;
            }

            isOnHold = true;
            Log.D("hold success");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("hold failed");
        }

        return false;
    }

    public boolean unhold() {
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot unhold() without an active call.");
            return false;
        }
        if (!isOnHold) {
            Log.D("already unhold");
            return true;
        }

        Log.D("unhold pjsuaCallId " + pjsuaCallId);
        try {
            if (plivo.UnHold(pjsuaCallId) != 0) {
                Log.D("unhold failed");
                return false;
            }

            isOnHold = false;

            // apply mute if already on mute before hold was applied
            if (isOnMute) {
                isOnMute = false;
                mute();
            }
            Log.D("unhold success");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("unhold failed");
        }

        return false;
    }

    public boolean checkDtmfDigit(String digit) {
        return Utils.VALID_DTMF.contains(digit);
    }

    public void setToContact(String toContact) {
        this.toContact = toContact;
    }

    // To format: <sip:username@phone.plivo.com>
    public String getToContact() {
        return toContact;
    }

    protected boolean isActive() {
        return isActive;
    }

    protected void setActive(boolean active) {
        isActive = active;
    }
}


