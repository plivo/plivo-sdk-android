package com.plivo.endpoint;

import android.content.Context;

import com.plivo.endpoint.backend.plivo;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Endpoint {
	private static final int MIN_REG_TIMEOUT = (int) TimeUnit.MINUTES.toSeconds(2);
	private static final int MAX_REG_TIMEOUT = (int) TimeUnit.HOURS.toSeconds(24);
	private static Context context;
	private static HashMap setupOptions = new HashMap();
	/**
	 * Listener for PJSIP event.
	 */
	private BackendListener backendListener;

	/**
	 * Event listener that need to be implemented by user.
	 */
	private EventListener eventListener;

	/**
	 * isInitialized flag.
	 */
	private boolean isInitialized;

	/**
	 * Current isActive outgoing call.
	 */
	private Outgoing curOutgoing;

	/**
	 * Registration Timeout in seconds - value to be specified between 120 & 86400 only
	 */
	private int regTimeout = (int) TimeUnit.MINUTES.toSeconds(10);

	private boolean isRegistered;
	private boolean isLogoutInProgress;
	private boolean isRegWithDeviceToken;
	private String userName,password;


	/**
	 * Creates endpoint object
	 * @param debug - true to turn ON Plivo SDK debug logs, false otherwise
	 * @param eventListener - Login, Call events Callback listener
	 */
	public Endpoint(boolean debug, EventListener eventListener) {
		Log.enable(debug);
		this.eventListener = eventListener;
		isInitialized = initLib(this.eventListener);
	}

	/**
	 * Create Plivo endpoint instance
	 * @param debug true if we want to set debug flag.
	 * @param eventListener event listener object.
	 * @return
	 */

	public static Endpoint newInstance(boolean debug, EventListener eventListener) {
        Endpoint endpoint = new Endpoint(debug, eventListener);
        Log.D("newInstance " + debug + "eventListener: " + eventListener);
        return endpoint.isInitialized ? endpoint : null;
	}


    /**
     * Create Plivo endpoint instance
     * @param debug true if we want to set debug flag.
	 * @param eventListener event listener object.
     * @param options object that contains context and enableTracking Flag.
     * @return
     */
    public static Endpoint newInstance(boolean debug, EventListener eventListener,HashMap options) {
        context = (Context) options.get("context");
		options.remove("context");
		setupOptions = options;
		Endpoint endpoint = new Endpoint(debug, eventListener);
		Log.D("newInstance " + debug + "eventListener: " + eventListener);
		return endpoint.isInitialized ? endpoint : null;
    }

	/**
	 * Login to plivo cloud
	 * @param username Username of the endpoint
	 * @param password Password of the endpoint
	 * @return
	 */
	public boolean login(String username, String password) {
		return login(username, password, "");
	}


	/**
	 * Login to plivo cloud
	 * @param username Username of the endpoint
	 * @param password Password of the endpoint
	 * @param regTimeout Registration Timeout(in seconds) of the endpoint
	 * @return
	 */
	public boolean login(String username, String password, int regTimeout) {
		if (regTimeout < MIN_REG_TIMEOUT || regTimeout > MAX_REG_TIMEOUT) {
			Log.E("Allowed values of regTimeout are between 120 and 86400 seconds only");
			return false;
		}
		this.regTimeout = regTimeout;
		return login(username, password, "");
	}

	/**
	 * Login to plivo cloud
	 * @param username Username of the endpoint
	 * @param password Password of the endpoint
	 * @param deviceToken DeviceToken from FCM
	 * @return
	 */
	public boolean login(String username, String password, String deviceToken) {
		return login(username, password, deviceToken, "");
	}

	/**
	 * Login to plivo cloud
	 * @param username Username of the endpoint
	 * @param password Password of the endpoint
	 * @param deviceToken DeviceToken from FCM
	 * @param certificateId certificateId that was uploaded in Plivo console
	 * @return
	 */
	public boolean login(String username, String password, String deviceToken, String certificateId) {
		if (!NetworkChangeReceiver.isConnected()) {
			return false;
		}

		if (deviceToken == null) {
			Log.E("Device token shouldn't be null. Pass in the token received from FCM.");
			return false;
		}
		this.userName=username;
		this.password=password;
		if (isRegistered) {
			Log.E("Already logged in with the endpoint. Logout and then login.");
			return true;
		}

		isRegWithDeviceToken = false;
		if (deviceToken.length() > 0) {
			this.regTimeout = 3600*24*30; /* 30 days */
			isRegWithDeviceToken = true;
		}

		backendListener.initCallInsights(username, password, Global.DOMAIN, context, setupOptions);

		try {
			if (plivo.LoginSip(username, password, this.regTimeout, Global.DOMAIN, deviceToken, certificateId) != 0) {
				Log.E("Login attempt failed. Check your username and password");
				return false;
			}
			logDebug(isRegWithDeviceToken ? "Logging in with device token..." : "Logging in...");
			return true;
		} catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Logout
	 * @return
	 */
	public boolean logout() {
		if (!NetworkChangeReceiver.isConnected()) {
			return false;
		}
		if (!isRegistered) {
			Log.E("Cannot logout without endpoint already logged in.");
			return false;
		}
		if (isLogoutInProgress) {
			Log.E("Logout is already in progress. Check for onLogout() callback.");
			return false;
		}

		try {
			if (plivo.Logout() != 0) {
				Log.E("Logout failed");
				return false;
			}
			logDebug("Logout success");
			isLogoutInProgress = true;
			return true;
		} catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Create outgoing call instance.
	 * @return
	 */
	public Outgoing createOutgoingCall() {
		logDebug("createOutgoingCall");
		if (!NetworkChangeReceiver.isConnected()) {
			return null;
		}
		if (!isRegistered) {
			Log.E("Cannot createOutgoingCall() without endpoint logged in. Call login() before.");
			return null;
		}

		Outgoing out = new Outgoing(this);
		this.curOutgoing = out;
		Log.I("outgoing object created");
		return out;
	}

	/**
	 * Check if a digit is valid dtmf digit
	 * @param digit Digit to be checked.
	 * @return
	 */
	public boolean checkDtmfDigit(String digit) {
		return Utils.VALID_DTMF.contains(digit);
	}

	protected Outgoing getOutgoing() {
		return this.curOutgoing;
	}

	/**
	 * Set onLogin() or onLogout()
	 * @param status true when login success, false when logout success
	 */
	void setRegistered(boolean status) {
		this.isRegistered = status;

		// logout success
		if (isRegistered == false) {
			isLogoutInProgress = false;
			isRegWithDeviceToken = false;
		}
	}

	// kept to not break the backward compatibility
	public boolean getRegistered(){
		return this.isRegistered;
	}

	@Deprecated
	public void setRegTimeout(int regTimeout) {
		if (regTimeout == this.regTimeout) return;

		Log.W("setRegTimeout will be deprecated in upcoming release. " +
				"Use login(username, password, regTimeout) instead");

		if (!isRegistered) {
			Log.E("Cannot setRegTimeout() without endpoint logged in. Call login() before.");
			return;
		}

		if (regTimeout < MIN_REG_TIMEOUT || regTimeout > MAX_REG_TIMEOUT) {
			Log.E("Allowed values of regTimeout are between 120 and 86400 seconds only");
			return;
		}

		this.regTimeout = regTimeout;

		try {
			plivo.setRegTimeout(regTimeout);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// reatining this to not break the backward compatibility
	private void logDebug(String str) {
		Log.D("[endpoint]" + str);
	}

	private boolean initLib(EventListener eventListener) {
		loadJNI();

		if (backendListener == null) {
			backendListener = new BackendListener(Global.DEBUG, this, eventListener);
		}
		plivo.setCallbackObject(backendListener);

		logDebug("Starting module..");

		int rc = plivo.plivoStart();

		if (rc != 0) {
			Log.E("plivolib failed. rc = " + rc + ". Failed to initialize Plivo Endpoint object.");
			return false;
		}

		backendListener.checkBitrate(setupOptions);
		logDebug("plivolib started.....");
		return true;
	}

	private void loadJNI() {
		try {
			System.loadLibrary("pjplivo");
			Global.isJniLoaded = true;
			logDebug("libpjplivo loaded");
		} catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Creating application in keep alive
	public void keepAlive(){
		logDebug("keepAlive");
		if (!isRegistered) {
			Log.E("Cannot call keep alive without endpoint logged in.");
			return;
		}


		try {
			plivo.keepAlive();
		} catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	//reset the endpoint when the network has change
	public void resetEndpoint(){
		logDebug("resetEndpoint");
		try {
			plivo.resetEndpoint();
		} catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//Push_headers is the Map object forwarded by the GCM or FCM push notification service.
	public void relayVoipPushNotification(Map<String, String> push_headers) {
		logDebug("relayVoipPushNotification: " + push_headers);
		if (!isRegistered && !isRegWithDeviceToken) {

			Log.E("Cannot call relayVoipPushNotification() without successful login with device token. Use login(String username, String password, String deviceToken).");
			return;
		}

		if (!Utils.validatePushHeaders(push_headers)) {
            Log.E("Invalid Notification");
            return;
        }

		try {
			plivo.relayVoipPushNotification(Utils.mapToString(push_headers));
		} catch (UnsatisfiedLinkError ule) {
			ule.printStackTrace();
			Log.E("errload loading libpjplivo:" + ule.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getLastCallUUID(){
		return backendListener.getLastXCallUUID();
	}

	public String getCallUUID(){
		return backendListener.getXCallUUID();
	}

	public Map<String,String> getValidationStatus(String callUUID, Integer starRating, ArrayList<String> issues, String note, Boolean sendConsoleLogs){

		Map<String,String> status = new HashMap<String, String>();
		status.put("true","No Error");
		if (sendConsoleLogs==null){
			Log.E("Flag 'sendConsoleLogs' can't be null");
			status.put("false","Flag 'sendConsoleLogs' can't be null");
			return status;
		}
		if (!isRegistered) {
			Log.E("Cannot submit feedback without endpoint logged in");
			status.put("false","Cannot submit feedback without endpoint logged in.");
			return status;
		}

		//check for empty callUUID
		if(callUUID==null || callUUID.isEmpty()){
			Log.E("Caller UUID is mandatory");
			status.put("false","Caller UUID is mandatory");
			return status;
		}

		//check for empty starRating
		if(starRating==null || starRating<=0 || starRating>5){
			Log.E("Star rating should be between 1 to 5");
			status.put("false","Star rating should be between 1 to 5");
			return status;
		}
		ArrayList <String> issue_final= new ArrayList<String>();
		ArrayList<String> issuesNotFromPredefinedList = new ArrayList<String>();
		if (starRating > 0 && starRating <= 5) {
			//validate note
			if (note != null && note != "" && note.length() > 280) {
				Log.E("Note can be maximum 280 characters");
				status.put("false","Note can be maximum 280 characters");
				return status;
			}

			//validate issue
			if (starRating != 5 && (issues == null || issues.isEmpty())) {
				Log.E("Atleast one issue is mandatory for feedback");
				status.put("false","Atleast one issue is mandatory for feedback");
				return status;
			}
			if (issues != null && !issues.isEmpty()) {
				//Map the existing issues and push to final list of issues enum (issue_final)
				for (String issue : issues) {
					String _issue = issue.toUpperCase();
					Log.I("Issue : "+_issue);
					if (Global.DEFAULT_COMMENTS.containsKey(_issue)) {
						String extractedIssue = Global.DEFAULT_COMMENTS.get(_issue);
						Log.I("Extracted Issue : "+extractedIssue);
						issue_final.add(extractedIssue);
					}else{
						issuesNotFromPredefinedList.add(issue);
					}
				}
				issues.removeAll(issuesNotFromPredefinedList);
			}

			if (issue_final.isEmpty()) {
				Set<String> validIssues = new HashSet<>();
				validIssues = Global.DEFAULT_COMMENTS.keySet();
				if (starRating == 5) {
					Log.D("Feedback with full rating without any Issues or matches from predefined list of issues -" + validIssues);
				} else {
					Log.E("Issues must be from the predefined list of issues for feedback -" + validIssues);
					status.put("false","Issues must be from the predefined list of issues for feedback -" + validIssues);
					return status;
				}

			}
		}
		return status;
	}

	public static JSONObject getRequestPayload(String callUUID, String userName, String password){
		Map consoleBody = new HashMap();
		try {
			String url = Global.S3BUCKET_API_URL;
			consoleBody.put("username", userName);
			consoleBody.put("password", "\"" + password + "\"");
			consoleBody.put("domain", Global.DOMAIN);
			consoleBody.put("calluuid", callUUID);
			consoleBody.put("source",Global.SOURCE);
			JSONObject payload = new JSONObject(consoleBody.toString());
			return payload;
		}catch (JSONException exception) {
			exception.printStackTrace();
			return null;
		}
	}

	public void submitCallQualityFeedback(String callUUID, Integer starRating, ArrayList <String> issues, String note, Boolean sendConsoleLogs, FeedbackCallback callback){

		//Do the initial vaidation and send respective status
		Map <String,String> status = getValidationStatus(callUUID, starRating, issues, note, sendConsoleLogs);
		if (status.containsKey("false")){
			if (callback!=null){
				callback.onValidationFail(status.get("false"));
			}
			else{
				Log.D("Validation error : "+status.get("false"));
			}
			return;
		}

		//build the payload
		JSONObject postBody = getRequestPayload(callUUID,this.userName,this.password);

		try {
			HttpPostAsyncTask postClient = new HttpPostAsyncTask(postBody, "POST", new HTTPRequestCallback() {
				@Override
				public void onResponse(String response) {
					if (response.equals("")) {
						Log.E(" s3 url is empty");
						return;
					}
					try {
						JSONObject jsonObject = new JSONObject(response);
						String s3URL = jsonObject.get("data").toString();
						Map <String,Object> feedback = new HashMap<String, Object>();
						String putRequestLoad= new String();
						ArrayList<String> finalPayload = new ArrayList<>();
						feedback.put("overall",starRating);
						String _note = note;
						if(_note==null){
							_note="";
						}
						ArrayList<String> finalIssueList = new ArrayList<String>();

						//remove duplicates from issue list if any
						for (String issue : issues){
							if (! finalIssueList.contains(issue.toLowerCase())){
								finalIssueList.add(issue.toLowerCase());
							}
						}

						feedback.put("comment",finalIssueList+ " "+_note);
						putRequestLoad += feedback.toString()+"\n";
						// Send Feedback Stats
						backendListener.sendFeedbackStats(finalIssueList, callUUID, starRating, note);
						if (sendConsoleLogs) {
							putRequestLoad = putRequestLoad + Log.deviceLog.toString();
						}
						try {
							HttpPutAsyncTask putClient = new HttpPutAsyncTask(putRequestLoad, "PUT", new HTTPRequestCallback() {
								@Override
								public void onResponse(String response) {
									if (callback!=null) {
										callback.onSuccess(response);
									}
									else{
										Log.D("Success : " + response);
									}
									return;
								}

								@Override
								public void onFailure(int statusCode) {
									Log.E("Log file was not uploaded to server");
									if(callback!=null){
										callback.onFailure(statusCode);
									}
									else{
										Log.D("Failure : " + Integer.toString(statusCode));
									}
									return;
								}
							});
							putClient.execute(s3URL);
						}catch (Exception exception) {
							exception.printStackTrace();
						};
					} catch (Exception exception) {
						exception.printStackTrace();
					}
				}

				@Override
				public void onFailure(int statusCode) {
					Log.E(" Error while making the POST request to get s3url");
					if (callback!=null){
						callback.onFailure(statusCode);
					}
					else{
						Log.D("Failure : " + Integer.toString(statusCode));
					}
					return;
				}
			});
			postClient.execute(Global.S3BUCKET_API_URL);
		}
		catch (Exception exception){
			exception.printStackTrace();
		}
	}


}

