package com.plivo.endpoint;

public interface FeedbackCallback {
    public void onFailure( int statusCode);
    public void onSuccess(String response);
    public void onValidationFail(String message);
}
