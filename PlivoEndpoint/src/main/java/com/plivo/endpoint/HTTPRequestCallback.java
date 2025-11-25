package com.plivo.endpoint;

public interface HTTPRequestCallback {
    public void onResponse(String response);
    public void onFailure(int statusCode);
}
