#include <string>
#include <sstream>
#include <algorithm>

namespace patch
{
    template < typename T > std::string to_string( const T& n )
    {
        std::ostringstream stm ;
        stm << n ;
        return stm.str() ;
    }
}

#include <iostream>
#include <exception>
#include <vector>
#include <map>
#include <sstream>


#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#include "plivo_app_callback.h"
#include "pjmedia_audiodev.h"


using namespace std;

// #if defined(PJ_ANDROID) && PJ_ANDROID != 0;

char SIP_DOMAIN[] = "phone.plivo.com";

#define PLIVO_ENDPOINT_VER "2.0.20"

#define MAX_ENDPOINT_LENGTH 212

static PlivoAppCallback* callbackObj = NULL;

#define THIS_FILE   "pjsua_app_callback.cpp"


typedef enum {
    _PLIVOUA_INIT_FAILED,
    _PLIVOUA_TRANSPORT_CREATE_FAILED,
    _PLIVOUA_CREATE_FAILED,
    _PLIVOUA_START_FAILED,
    _PLIVOUA_ACC_ADD_FAILED,
    _PLIVOUA_LOGOUT_FAILED,
    _PLIVOUA_MUTE_FAILED,
    _PLIVOUA_UNMUTE_FAILED,
    _PLIVOUA_HOLD_FAILED,
    _PLIVOUA_UNHOLD_FAILED,
    _PLIVOUA_UNKNOWN_ERROR = -100
}plivoua_error_t;

/**
 * user_data as describe here :
 * http://www.pjsip.org/pjsip/docs/html/structpjsua__acc__config.htm#af6d109091c7130496c6014750f2c9216
 * We use it to save account id
 */
struct my_userdata {
    pjsua_acc_id acc_id;
};

/* pjsua app config */
static pjsua_config app_cfg;
static pjsua_logging_config log_cfg;
static pjsua_transport_config trans_cfg;
static pjsua_acc_id acc_id;
static pjsua_call_id outCallId;
static pjsua_media_config media_cfg;
static pj_pool_t *app_pool;
static int is_logged_in = 0;
static bool logout_requested = false;
static unsigned opt = 2;
static unsigned latency_ms = 0;
static pjmedia_echo_state *ec;
static pjmedia_port *dn_port;

/* global static variable */
static pjsua_call_id currrent_call_id;
extern pjsua_call_setting   call_opt;

/* global static variables for rtp stats*/

static int prePacketsReceived;
static int prePacketsSent;
static int preRemotePacketsLoss;
static int preLocalPacketsLoss;
//static bool baseStatsCollected = false;

/**
   Sound device values for hold/unhold
*/
int capture_snd_dev = 0;
int playback_snd_dev = 0;

pjsua_transport_id transport_id = -1;

/**
 * Check if account (pjsua_acc) with id acc_id is registered.
 * return value:
 *  1  : yes
 *  0  : no
 *  -1 : dont know
 */
static int is_registered(pjsua_acc_id acc_id)
{
    callbackObj->onDebugMessage("is_registered");

    struct pjsua_data *pjdata = pjsua_get_var();

    for (int i = 0; i < pjdata->acc_cnt; i++) {
        pjsua_acc acc = pjdata->acc[i];

        //check if this is our account & check registration status
        struct my_userdata  *userdata = (struct my_userdata *)pjsua_acc_get_user_data(acc_id);
        if (userdata == NULL) {
            callbackObj->onDebugMessage("userdata NULL");
            return -1;
        }

        if (acc_id == userdata->acc_id) {
            if (acc.regc == NULL) {
                return 0;
            } else {
                return 1;
            }
        } else {
            continue;
        }
    }
    return -1;
}

vector<string> &split(const string &s, char delim, vector<string> &elems) {
    stringstream ss(s);
    string item;
    while (getline(ss, item, delim)) {
        elems.push_back(item);
    }
    return elems;
}


vector<string> split(const string &s, char delim) {
    vector<string> elems;
    split(s, delim, elems);
    return elems;
}

static void log_writer(int level, const char *data, int len)
{
    callbackObj->onDebugMessage(data);

}

char* getXCallUUID(pjsip_msg *msg) {
    callbackObj->onDebugMessage("getXCallUUID");
    pj_str_t eventHeaderName = pj_str("X-CallUUID");
    pjsip_generic_string_hdr *eventHeader = (pjsip_generic_string_hdr*)pjsip_msg_find_hdr_by_name(msg, &eventHeaderName, NULL);
    if (eventHeader == NULL)
        return "";
    char* event_value = eventHeader->hvalue.ptr;  
    return  event_value;
}

static void on_incoming_call(pjsua_acc_id acc_id, pjsua_call_id call_id,pjsip_rx_data *rdata)
{
    callbackObj->onDebugMessage("on_incoming_call");
    pjsua_call_info info;

    char * header = rdata->msg_info.msg_buf;
    string str(header);
    int i,k;
    vector<string> hdr_vec = split(str, '\n');
    string _str_1 = "X-PH";
    string _header;
    string temp_header_val;
    for (i=0; i< hdr_vec.size();i++) {
        temp_header_val = hdr_vec[i];
        std::transform(hdr_vec[i].begin(), hdr_vec[i].end(),hdr_vec[i].begin(), ::toupper);
        size_t pos1 = hdr_vec[i].find(_str_1);
        if (pos1 != string::npos) {
            _header += temp_header_val;
            _header += ',';
        }
    }
    if (_header.length() > 0)
        _header.erase(_header.length()-1, 1);
    char * hdr = new char[_header.length() + 1];
    strcpy(hdr, _header.c_str());
    pjsua_call_get_info(call_id, &info);

    callbackObj->onDebugMessage("onIncomingCall");

    const char *fromContact = pj_strbuf(&info.remote_info);
    const char *toContact = pj_strbuf(&info.local_contact);
    const char *sipCallId = pj_strbuf(&info.call_id);
    const char *xCallUUID = getXCallUUID(rdata->msg_info.msg);
    /* Automatically answer incoming calls with 180/Ringing */
    pjsua_call_answer(call_id, 180, NULL, NULL);
    callbackObj->onIncomingCall(call_id, sipCallId, fromContact, toContact, hdr, xCallUUID);
}

static void on_call_media_state(pjsua_call_id call_id) {
    pjsua_call_info call_info;
    pjsua_call_get_info(call_id, &call_info);

    callbackObj->onDebugMessage("on_call_media_state");
    // Connecting audio here
    if (call_info.media_status == PJSUA_CALL_MEDIA_ACTIVE) {
        callbackObj->onDebugMessage("media active");

        pjsua_conf_connect(call_info.conf_slot, 0);
        pjsua_conf_connect(0, call_info.conf_slot);
        pjsua_set_snd_dev(capture_snd_dev, playback_snd_dev);
    }
}

static void on_reg_state2(pjsua_acc_id acc_id, pjsua_reg_info *info)
{
    callbackObj->onDebugMessage("on_reg_state");
    PJ_UNUSED_ARG(acc_id);

    pjsua_acc_info acc_info;
    pjsua_acc_get_info(acc_id, &acc_info);

    stringstream status;
    status << acc_info.status;
    callbackObj->onDebugMessage(status.str().c_str());

    if (acc_info.status == PJSIP_SC_OK && is_logged_in == 0){
        callbackObj->onDebugMessage("Login Success");
        struct pjsip_regc_cbparam *rp = info->cbparam;

        //static pjsip_transport *the_transport = rp->rdata->tp_info.transport;
        //Converted the static variable into local
        pjsip_transport *the_transport = rp->rdata->tp_info.transport;
        if (the_transport->factory->type & PJSIP_TRANSPORT_IPV6) {
            callbackObj->onDebugMessage("Ipv6 network");
            pjsua_var.acc[0].cfg.ipv6_media_use = PJSUA_IPV6_ENABLED;
        }
        is_logged_in = 1;
        callbackObj->onLogin();
    }
    else if (acc_info.status == PJSIP_SC_OK && is_logged_in == 1 && logout_requested == true) {
        is_logged_in = 0;
        logout_requested = false;
        pjsua_acc_del(acc_id);
        callbackObj->onLogout();
    }
    else if (PJSIP_IS_STATUS_IN_CLASS(acc_info.status, 400)) {
        callbackObj->onLoginFailed();
        callbackObj->onDebugMessage("request timeout. internet is not available");
    }
    // Internet is not available
    else if (acc_info.status == 502) {
        callbackObj->onLoginFailed();
        callbackObj->onDebugMessage("internet is not available");
    }else if (acc_info.status == 503) {
         callbackObj->onLoginFailed();
         callbackObj->onDebugMessage("Service unavailable");
     }else if (acc_info.status == 200 && is_logged_in == 1) {

        char buf[100];
        sprintf(buf, "Token registered successfully=%d", acc_info.status);
        callbackObj->onDebugMessage(buf);

      } else {
        char buf[100];
        sprintf(buf, "unhandled on_reg_state status is: %d", acc_info.status);
        callbackObj->onLoginFailed();
        callbackObj->onDebugMessage(buf);
    }
}

static void call_on_dtmf_callback(pjsua_call_id call_id, int dtmf) {
    callbackObj->onDebugMessage("call_on_dtmf_callback");
    pjsua_call_info call_info;
    pjsua_call_get_info(call_id, &call_info);

    int new_dtmf = dtmf - 48;
    callbackObj->onIncomingDigitNotification(new_dtmf);
}

static void on_call_state(pjsua_call_id call_id, pjsip_event *e) {
    currrent_call_id = call_id;
    callbackObj->onDebugMessage("on_call_state");

    PJ_UNUSED_ARG(e);

    pjsua_call_info call_info;

    pjsua_call_get_info(call_id, &call_info);
    pjsua_acc_id acc_id = call_info.acc_id;
    stringstream status;
    status << call_info.last_status;
    callbackObj->onDebugMessage(status.str().c_str());
    if (call_info.role != PJSIP_ROLE_UAC) {
        // Send out all incoming notifications
        // Check if the state is disconnected and the last status code, in
        // this case, incoming reject event will be sent
        if (call_info.state == PJSIP_INV_STATE_INCOMING) {
            callbackObj->onDebugMessage("Incoming call INVITE message");
        } 
        else if (call_info.state == PJSIP_INV_STATE_EARLY || call_info.last_status == 183 || call_info.last_status == 180) {
            callbackObj->onDebugMessage("onIncomingCallRinging");
            callbackObj->onIncomingCallRinging(call_id, pj_strbuf(&call_info.call_id));
        }
        else if (call_info.state == PJSIP_INV_STATE_CONNECTING) {
            callbackObj->onIncomingCallConnecting(call_id, pj_strbuf(&call_info.call_id));
        }
        else if (call_info.state == PJSIP_INV_STATE_CONFIRMED) {
            preRemotePacketsLoss = 0;
            preLocalPacketsLoss = 0;
            prePacketsReceived = 0;
            prePacketsSent = 0;
            callbackObj->onDebugMessage("onIncomingCallAnswered");
            callbackObj->onIncomingCallAnswered(call_id, pj_strbuf(&call_info.call_id));
        }
        else if (call_info.state == PJSIP_INV_STATE_DISCONNECTED &&
                (call_info.last_status == 487 || call_info.last_status == 486)) {
            // Send incoming reject
            callbackObj->onDebugMessage("rejection message");
            callbackObj->onIncomingCallRejected(call_id, pj_strbuf(&call_info.call_id));
        }
        else if(call_info.state == PJSIP_INV_STATE_DISCONNECTED && call_info.last_status == 200) {
            // Send incoming hangup
            callbackObj->onDebugMessage("checking message");
            callbackObj->onIncomingCallHangup(call_id, pj_strbuf(&call_info.call_id));
        } else {
            callbackObj->onDebugMessage("onCall : unknown incoming call state");
            callbackObj->onIncomingCallInvalid(call_id, pj_strbuf(&call_info.call_id));
        }
    }
    else {

          if (call_info.state == PJSIP_INV_STATE_CALLING) {
              callbackObj->onOutgoingCall(call_id, pj_strbuf(&call_info.call_id));
          }
          else if (call_info.state == PJSIP_INV_STATE_EARLY || call_info.last_status == 183 || call_info.last_status == 180) {
              callbackObj->onDebugMessage("onCallRinging");
              char* xcallUUID = getXCallUUID(e->body.tsx_state.src.rdata->msg_info.msg);
              callbackObj->onOutgoingCallRinging(call_id, pj_strbuf(&call_info.call_id), xcallUUID);
          }
          // Notify the outbound call being answered.
          else if (call_info.state == PJSIP_INV_STATE_CONFIRMED) {
              preRemotePacketsLoss = 0;
              preLocalPacketsLoss = 0;
              prePacketsReceived = 0;
              prePacketsSent = 0;
              callbackObj->onOutgoingCallAnswered(call_id, pj_strbuf(&call_info.call_id));
          }

          else if (call_info.state == PJSIP_INV_STATE_CONNECTING) {
              char* xcallUUID = getXCallUUID(e->body.tsx_state.src.rdata->msg_info.msg);
              callbackObj->onOutgoingCallConnecting(call_id, pj_strbuf(&call_info.call_id), xcallUUID);
          }

          // Call canceled or timeout from the other side before answering
          else if (call_info.state == PJSIP_INV_STATE_DISCONNECTED  && (call_info.last_status >= 480 && call_info.last_status <= 489)) {
              callbackObj->onDebugMessage("onCallDisconnected or timeout");
              char* xcallUUID = getXCallUUID(e->body.tsx_state.src.rdata->msg_info.msg);
              callbackObj->onOutgoingCallRejected(call_id, pj_strbuf(&call_info.call_id), xcallUUID);
          }


          // Check if the number is invalid or Timeout
          else if (call_info.state == PJSIP_INV_STATE_DISCONNECTED && (call_info.last_status == 404 || call_info.last_status == 408)) {
              callbackObj->onDebugMessage("onCallInvalid");
              callbackObj->onOutgoingCallInvalid(call_id, pj_strbuf(&call_info.call_id));
          }


          // Call disconnected after answering
          else if (call_info.state == PJSIP_INV_STATE_DISCONNECTED && call_info.last_status == 200) {
              //char* xcallUUID = getXCallUUID(e->body.tsx_state.src.rdata->msg_info.msg);
              callbackObj->onOutgoingCallHangup(call_id, pj_strbuf(&call_info.call_id));
          }

          else {
              callbackObj->onDebugMessage("onCall : unknown outgoing call state");
              callbackObj->onOutgoingCallInvalid(call_id, pj_strbuf(&call_info.call_id));
          }
    }
}

char* concat(const char *s1, const char *s2)
{
    char *result = (char*)malloc(strlen(s1) + strlen(s2) + 1); // +1 for the null-terminator
    strcpy(result, s1);
    strcat(result, s2);
    return result;
}

/**
 * Login to plivo cloud with domain.
 */
int LoginSip(char *username, char *password, int regTimeout, char *sip_domain, char *deviceToken, char *certificateId) {
    callbackObj->onDebugMessage("LoginSip");
    char *sip_domain_url = sip_domain;
    if (strcmp(sip_domain, "phone.plivo.com")) {
        char *sip_domain_url = "mobile.phone.plivo.com";
    }
    if(is_logged_in == 0){

        if(strlen(username) <= MAX_ENDPOINT_LENGTH){
            pj_status_t status;
            char sipUri[300];

            pjsua_acc_config cfg;
            pjsua_acc_config_default(&cfg);

            sprintf(sipUri, "sip:%s@%s;transport=tls", username, sip_domain);
            cfg.id = pj_str(sipUri);

            //sprintf(s, "sip:%s", sip_domain);
            cfg.reg_uri = pj_str(concat("sip:", sip_domain_url));
            cfg.cred_count = 1;
            cfg.cred_info[0].realm = pj_str(sip_domain);
            cfg.cred_info[0].scheme = pj_str("digest");
            cfg.cred_info[0].username = pj_str(username);
            cfg.cred_info[0].data_type = PJSIP_CRED_DATA_PLAIN_PASSWD;
            cfg.cred_info[0].data = pj_str(password);
            cfg.use_srtp = PJMEDIA_SRTP_MANDATORY;
            cfg.srtp_secure_signaling = 1;

            char proxy[300];
            sprintf(proxy, "sip:%s;transport=tls", sip_domain_url);
            cfg.proxy[cfg.proxy_cnt++] = pj_str(proxy);

            if (strlen(deviceToken) != 0) {
                callbackObj->onDebugMessage("Registering with deviceToken");
                struct pjsip_generic_string_hdr CustomHeader;

                char buffer[2048];
                char buffer2[2048];

                pj_str_t contactparam;
                pj_str_t contactparam2;

                pj_bzero(buffer,sizeof(buffer));
                contactparam.ptr = buffer;

                pj_bzero(buffer2,sizeof(buffer2));
                contactparam2.ptr = buffer2;

                pj_str_t value = pj_str(deviceToken);

                pj_strcpy2(&contactparam,";fcm=");
                pj_strcat (&contactparam,&value);

                if (strlen(certificateId) != 0) {
                    pj_str_t certidvalue = pj_str(certificateId);
                    pj_strcpy2(&contactparam2,";certid=");
                    pj_strcat (&contactparam2,&certidvalue);
                    pj_strcat (&contactparam,&contactparam2);
                }

                cfg.contact_uri_params = contactparam;
            }

            cfg.reg_timeout = regTimeout;
            cfg.ka_interval = 0;

            cfg.user_data = &acc_id;
            status = pjsua_acc_add(&cfg, PJ_TRUE, &acc_id);

            stringstream stat;
            stat << status;
            callbackObj->onDebugMessage(stat.str().c_str());

            if (status != PJ_SUCCESS) {
                return _PLIVOUA_ACC_ADD_FAILED;
            } else {
                struct my_userdata *userdata = (struct my_userdata *)pj_pool_alloc(app_pool,sizeof(struct my_userdata));
                userdata->acc_id = acc_id;
                pjsua_acc_set_user_data(acc_id, (void *)userdata);
            }
            return 0;
        } else {
            callbackObj->onDebugMessage("Invalid Endpoint");
            return _PLIVOUA_ACC_ADD_FAILED;
        }
    } else {
        callbackObj->onDebugMessage("Endpoint already registered");
        return 0;
    }
}

/**
 * Login to plivo cloud.
 */
int Login(char *username, char *password, int regTimeout, char *deviceToken, char *certificateId) {
    LoginSip(username, password, regTimeout, SIP_DOMAIN, deviceToken, certificateId);
}

/**
 * Logout
 */
int Logout() {
    callbackObj->onDebugMessage("Logout");
    if (pjsua_acc_get_count()) {

        //Account Deletion
        if (!pjsua_acc_is_valid(acc_id)) {
            callbackObj->onDebugMessage("Invalid Account-ID for deletion");
            return 0;
        } else {

            pj_status_t status;

            struct my_userdata *userdata = (struct my_userdata *)pjsua_acc_get_user_data(acc_id);
            userdata->acc_id = acc_id;
            pjsua_acc_set_user_data(acc_id, (void *)userdata);

            logout_requested = true;
            status = pjsua_acc_set_registration(acc_id, PJ_FALSE);
            if (status != PJ_SUCCESS) {
                logout_requested = false;
                return _PLIVOUA_LOGOUT_FAILED;
            }
            return 0;
        }
    } else{
        callbackObj->onDebugMessage("Endpoint not loggedIn");
        return 0;
    }
}

void setRegTimeout(int regTimeout) {
    callbackObj->onDebugMessage("setRegTimeout");
    pjsua_acc_config acc_cfg;

    pjsua_acc_get_config(acc_id, app_pool,&acc_cfg);

    acc_cfg.reg_timeout = regTimeout;
    pj_status_t status = pjsua_acc_modify(acc_id, &acc_cfg);

    if (status != PJ_SUCCESS)
         fprintf(stderr, "Error in updating Registration Timeout Interval");
}

void LoginAgain() {
    callbackObj->onDebugMessage("LoginAgain");
    if (is_logged_in != 1) {
        callbackObj->onDebugMessage("Cannot login again without endpoint already logged in");
        return;
    }

    try {
        pjsua_acc_config acc_cfg;

        pj_status_t status = pjsua_acc_get_config(acc_id, app_pool,&acc_cfg);

        if (status == PJ_SUCCESS) {
            if (is_logged_in == 1 && is_registered(acc_id) == 0) {
                pj_status_t regstatus = pjsua_acc_set_registration(acc_id, PJ_TRUE);
                if (regstatus != PJ_SUCCESS) {
                    callbackObj->onDebugMessage("Failed to log in again");
                } else {
                    callbackObj->onDebugMessage("Logged in again");
                }
            }
        } else {
            callbackObj->onDebugMessage("Error occured while logging in again");
        }
    } catch(exception& e) {
        callbackObj->onDebugMessage("!!Error occured while logging in again");
    }
}


static int initPjsua() {
    callbackObj->onDebugMessage("initPjsua");
    pj_status_t status;

    status = pjsua_create();
    if (status != PJ_SUCCESS) {
        fprintf(stderr,"pjsua_create failed\n");
        return _PLIVOUA_CREATE_FAILED;
    }

    pjsua_config_default(&app_cfg);

    app_pool = pjsua_pool_create("plivo-android-sdk", 1000, 1000);

    pjsua_logging_config_default(&log_cfg);
    log_cfg.level = 5;
    log_cfg.console_level = 4;
    log_cfg.msg_logging = 1;

    log_cfg.cb = log_writer;

    pjsua_media_config_default(&media_cfg);
    media_cfg.clock_rate = 16000;

    
    /* Set sound device latency */
    if (PJMEDIA_SND_DEFAULT_REC_LATENCY > 0)
        media_cfg.snd_rec_latency = PJMEDIA_SND_DEFAULT_REC_LATENCY;
    if (PJMEDIA_SND_DEFAULT_PLAY_LATENCY)
        media_cfg.snd_play_latency = PJMEDIA_SND_DEFAULT_PLAY_LATENCY;

    app_cfg.cb.on_reg_state2 = &on_reg_state2;
    app_cfg.cb.on_call_state = &on_call_state;
    app_cfg.cb.on_incoming_call = &on_incoming_call;
    app_cfg.cb.on_call_media_state = &on_call_media_state;
    app_cfg.cb.on_dtmf_digit = &call_on_dtmf_callback;

    // Adding plivo User-Agent
    char *str = "PlivoAndroidSDK-v";
    char *userAgent = (char*)calloc(strlen(str)+strlen(PLIVO_ENDPOINT_VER)+1, sizeof(char));
    strcpy(userAgent,str);
    strcat(userAgent,PLIVO_ENDPOINT_VER);
    pj_str_t user_agent = pj_str(userAgent);
    app_cfg.user_agent = user_agent;

    status = pjsua_init(&app_cfg, &log_cfg, &media_cfg);
    if (status != PJ_SUCCESS) {
        fprintf(stderr, "plivoua_init failed");
        return _PLIVOUA_INIT_FAILED;
    }

    pj_log_set_log_func(log_writer);


    media_cfg.audio_frame_ptime = 20;
    media_cfg.channel_count = 0;
    media_cfg.ec_tail_len = 200;
    media_cfg.ec_options = 0;
    media_cfg.no_vad = false;
    media_cfg.quality = 4;
    media_cfg.has_ioqueue = true;

    /* Create echo canceller */
    status = pjsua_set_ec(media_cfg.ec_tail_len, media_cfg.ec_options);
    if (status != PJ_SUCCESS) {
        fprintf(stderr, "Error setting Echo Cancellation");
        return 1;
    }

    pjsua_transport_config_default(&trans_cfg);
    // pjsua_transport_id transport_id = -1;

    status = pjsua_transport_create(PJSIP_TRANSPORT_TLS6,
                                    &trans_cfg,
                                    &transport_id);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Create and start a new SIP transport: TLS6 could not be created");
    }

    status = pjsua_transport_create(PJSIP_TRANSPORT_TLS,
                                    &trans_cfg,
                                    &transport_id);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Create and start a new SIP transport: TLS could not be created");
    }
    
    status = pjsua_start();
    if (status != PJ_SUCCESS) {
        return _PLIVOUA_START_FAILED;
    }

    pj_str_t codec_id = pj_str("opus");
    status = pjsua_codec_set_priority(&codec_id, PJMEDIA_CODEC_PRIO_HIGHEST);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Init: codec priority opus could not be set");
    }
    codec_id = pj_str("pcmu");
    status = pjsua_codec_set_priority(&codec_id, PJMEDIA_CODEC_PRIO_HIGHEST-1);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Init: codec priority pcmu could not be set");
    }
    codec_id = pj_str("pcma");
    status = pjsua_codec_set_priority(&codec_id, PJMEDIA_CODEC_PRIO_HIGHEST-2);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Init: codec priority pcma could not be set");
    }
    codec_id = pj_str("gsm");
    status = pjsua_codec_set_priority(&codec_id, PJMEDIA_CODEC_PRIO_HIGHEST-3);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Init: codec priority gsm could not be set");
    }

    return 0;
}

int plivoStart()
{
    callbackObj->onDebugMessage("plivoStart");
    pj_status_t status;
    int rc;

    rc = initPjsua();
    if (rc != 0) {
        return rc;
    }

    callbackObj->onStarted("onStarted");
    return 0;
}

/**
 * Call SIP URI
 */
int Call(char *dest)
{
    if(strlen(dest) > 0){

        pj_status_t status;

        const pj_str_t dst_uri = pj_str(dest);

        status = pjsua_verify_sip_url(dst_uri.ptr);

        if (status != PJ_SUCCESS)
        {
            callbackObj->onDebugMessage("Error initiating SIP call, Invalid URI");
            return 0;

        }else{

            status = pjsua_call_make_call(acc_id, &dst_uri, 0, NULL, NULL, &outCallId);
            if (status != PJ_SUCCESS)
            {
                callbackObj->onDebugMessage("Error initiating SIP call, Invalid URI");
                return 0;
            }
        }
    } else{

        callbackObj->onDebugMessage("Error initiating SIP call, Empty URI");
        return 0;
    }
    return 0;
}


int CallH(char *dest, char *headers)
{
    if(strlen(dest) > 0){

            pj_status_t status;
            const pj_str_t dst_uri = pj_str(dest);


            status = pjsua_verify_sip_url(dst_uri.ptr);

            if (status != PJ_SUCCESS) {
                callbackObj->onDebugMessage("Error initiating SIP call, Invalid URI");
                return 0;

            } else {
                int i;
                //map<string, string> map_hdr;
                vector<string> key;
                vector<string> value;
                string str(headers);
                char *head;
                char *tail;
                pj_str_t head_pj;
                pj_str_t tail_pj;
                pjsua_msg_data msg_data;
                pjsua_msg_data_init(&msg_data);

                vector<string> hdr_vec = split(str, ',');
                for (i=0; i< hdr_vec.size();i++) {
                    vector<string> each_vec = split(hdr_vec[i], ':');
                    key.push_back(each_vec[0]);
                    value.push_back(each_vec[1]);
                    //map_hdr[each_vec[0]] = each_vec[1];
                }

                int header_length = key.size();
                pjsip_generic_string_hdr CustomHeader[header_length];

                pj_str_t header_pj = pj_str(headers);

                for (i=0; i< hdr_vec.size(); i++) {
                    head = new char[key[i].length() + 1];
                    strcpy(head, key[i].c_str());

                    tail = new char[value[i].length() + 1];
                    strcpy(tail, value[i].c_str());

                    head_pj = pj_str(head);
                    tail_pj = pj_str(tail);
                    pjsip_generic_string_hdr_init2(&CustomHeader[i], &head_pj, &tail_pj);
                    pj_list_push_back(&msg_data.hdr_list, &CustomHeader[i]);
                }


                status = pjsua_call_make_call(acc_id, &dst_uri, 0, NULL, &msg_data, &outCallId);
                if (status != PJ_SUCCESS) {
                    callbackObj->onDebugMessage("Error initiating SIP call, Invalid URI");
                    return 0;
                }
            }
    } else {
          callbackObj->onDebugMessage("Error initiating SIP call, Invalid URI");
          return 0;
    }
    return 0;
}


int Answer(int pjsuaCallId) {
    pjsua_call_answer(pjsuaCallId, 200, NULL, NULL);
}

int Hangup(int pjsuaCallId) {
    pjsua_call_hangup(pjsuaCallId, 0, NULL, NULL);
}

int Reject(int pjsuaCallId) {
    pjsua_call_answer(pjsuaCallId, 486, NULL, NULL);
}

int SendDTMF(int pjsuaCallId, char *digit) {
    pj_str_t dtmfStr = pj_str(digit);
    pjsua_call_dial_dtmf(pjsuaCallId, &dtmfStr);
}

int Mute(int pjsuaCallId) {
     pjsua_call_info call_info;
     pjsua_call_get_info(pjsuaCallId, &call_info);
     if (call_info.conf_slot != PJSUA_INVALID_ID){
         pjsua_conf_disconnect(0, call_info.conf_slot);
         return 0;
     }
     return _PLIVOUA_MUTE_FAILED;
}

int UnMute(int pjsuaCallId) {
     pjsua_call_info call_info;
     pjsua_call_get_info(pjsuaCallId, &call_info);
     pjsua_conf_connect(0, call_info.conf_slot);
     return 0;
}

int Hold(int pjsuaCallId) {
    pjsua_call_info call_info;
    pj_status_t status = pjsua_call_get_info(pjsuaCallId, &call_info);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Error holding SIP call, Call Info Couldn't be fetched!");
        return _PLIVOUA_HOLD_FAILED;
    }

    if (call_info.conf_slot != PJSUA_INVALID_ID){
        status = pjsua_conf_disconnect(0, call_info.conf_slot);
        if (status != PJ_SUCCESS) {
            callbackObj->onDebugMessage("Error holding SIP call, Call Info Couldn't be disconnected!");
            return _PLIVOUA_HOLD_FAILED;
        }

        pjsua_get_snd_dev(&capture_snd_dev, &playback_snd_dev);

        status = pjsua_set_null_snd_dev();
        if (status != PJ_SUCCESS) {
            callbackObj->onDebugMessage("Error holding SIP call, sound device release failure!");
            return _PLIVOUA_HOLD_FAILED;
        }

        return 0;
    }

    return _PLIVOUA_HOLD_FAILED;
}

int UnHold(int pjsuaCallId) {
    pj_status_t status = pjsua_set_snd_dev(capture_snd_dev, playback_snd_dev);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Error unholding SIP call, setting sound device failed!");
        return _PLIVOUA_UNHOLD_FAILED;
    }

    pjsua_call_info call_info;
    status = pjsua_call_get_info(pjsuaCallId, &call_info);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Error unholding SIP call, Call Info Couldn't be fetched!");
        return _PLIVOUA_UNHOLD_FAILED;
    }

    status = pjsua_conf_connect(0, call_info.conf_slot);
    if (status != PJ_SUCCESS) {
        callbackObj->onDebugMessage("Error unholding SIP call, call info couldn't be reconnected!");
        return _PLIVOUA_UNHOLD_FAILED;
    }

    return 0;
}

/**
    Returns
    1 -> Logged In
    0 -> Not Logged In
    -1 -> Account not found
**/
int isRegistered() {
    callbackObj->onDebugMessage("isRegistered");
    //if (is_logged_in == 1) {
    //    return 1;
    //}
    return is_registered(acc_id);
}


void plivoDestroy()
{
    callbackObj->onDebugMessage("plivoDestroy");
    //pjsua_app_destroy();

    /** This is on purpose **/
    //pjsua_app_destroy();
}

int plivoRestart()
{
    callbackObj->onDebugMessage("plivoRestart");
    pj_status_t status;

    plivoDestroy();

    return 0;// initMain(restart_argc, restart_argv);
}

void setCallbackObject(PlivoAppCallback* callback)
{
    callbackObj = callback;
}

void keepAlive()
{
    pjsua_acc_set_registration(acc_id, PJ_TRUE);
}

void resetEndpoint()
{
    pjsua_destroy();
}

void handleIPChange() {
    callbackObj->onDebugMessage("handleIPChange");

    if (is_logged_in != 1) {
        callbackObj->onDebugMessage("Endpoint not registered");
        return;
    }

    unsigned call_cnt = pjsua_call_get_count();

    char numstr[21];
    std::string logtext = "no of active calls ";
    sprintf(numstr, "%d", call_cnt);
    std::string noofcalllog = logtext + numstr;
    callbackObj->onDebugMessage(noofcalllog.c_str());
    if (call_cnt == 0) {
        callbackObj->onDebugMessage("No calls running to handle Ip Change");
        return;
    }

    if (call_cnt == 1) {
        pjsua_call_id call_id;
        pj_status_t status;

        status = pjsua_enum_calls(&call_id, &call_cnt);
        if (status == PJ_SUCCESS) {
            pjsua_call_info call_info;
            pjsua_call_get_info(call_id, &call_info);

            callbackObj->onDebugMessage("on_call_media_state");
            if (call_info.media_status == PJSUA_CALL_MEDIA_ACTIVE) {
                callbackObj->onDebugMessage("media active");

                pjsua_ip_change_param param;
                pjsua_ip_change_param_default(&param);
                pjsua_handle_ip_change(&param);
            } else {
                callbackObj->onDebugMessage("media not active");
            }
        }
    }
}

//Register Deivce token with Plivo.
void registerToken(char *deviceToken)
{
    callbackObj->onDebugMessage("registerToken");
    callbackObj->onDebugMessage(deviceToken);
    pjsua_acc_config acc_cfg;

    struct pjsip_generic_string_hdr CustomHeader;

    char buffer[2048];
    char buffer2[2048];

    pj_str_t contactparam;
    pj_str_t contactparam2;

    pj_bzero(buffer,sizeof(buffer));
    contactparam.ptr = buffer;

    pj_bzero(buffer2,sizeof(buffer2));
    contactparam2.ptr = buffer2;

    pj_str_t name = pj_str("AndroidToken");
    pj_str_t value = pj_str(deviceToken);
    pjsip_generic_string_hdr_init2(&CustomHeader, &name, &value);

    pjsua_acc_get_config(acc_id, app_pool,&acc_cfg);

    pj_list_push_back(&acc_cfg.reg_hdr_list, &CustomHeader);

    pj_strcpy2(&contactparam,";app_id=");
    pj_strcat (&contactparam,&value);

    pj_str_t value2 = pj_str("GCM");
    pj_strcpy2(&contactparam2,";platform_type=");
    pj_strcat (&contactparam2,&value2);

    pj_strcat (&contactparam,&contactparam2);

    acc_cfg.contact_uri_params = contactparam;
    acc_cfg.reg_timeout = 3600*24*30; /* 30 days */
    acc_cfg.ka_interval = 0;

    pj_status_t status = pjsua_acc_modify(acc_id, &acc_cfg);

    if (status != PJ_SUCCESS)
         fprintf(stderr, "Error in Register token funciton");

}

//pushMessage is the string forwarded by the GCM or FCM push notification service.
//Need to split the string to get key values
//PushMessage string will be in this format ("  label:"labelValue", index:"indexValue",  registrar:registrarValue"  ");
void relayVoipPushNotification(char *pushMessage)
{
    callbackObj->onDebugMessage("relayVoipPushNotification ");
    callbackObj->onDebugMessage(pushMessage);	    
    pj_str_t pjLabel; //label
    pj_str_t pjIndex; //index

    char *charRegistrar; //registrar

    //Static string to compare key value in Map
    //key[i] == stdLabel
    std::string stdLabel("label");

    //Static string to compare key value in Map
    //key[i] == stdIndex
    std::string stdIndex("index");

    //Static string to compare key value in Map
    //key[i] == stdRegistrar
    std::string stdRegistrar("registrar");

    //string vector
    //To store keys
    std::vector<std::string> key;

    //string vector
    //To store values
    vector<string> value;

    string str(pushMessage);

    //Split pushMessage string
    //Store Keys in Key Vector
    //Store Values in Value Vector
    vector<string> hdr_vec = split(str, ',');

    for (int i=0; i< hdr_vec.size();i++) {
        vector<string> each_vec = split(hdr_vec[i], ':');
        key.push_back(each_vec[0]);

        //Handling the case when the 'value' field for key-value pair is not defined
        if(each_vec.size()==1){
            value.push_back("");
        }else{
            value.push_back(each_vec[1]);
        }
    }

    //Loop the key vector and compare label, index, registrar values
    for (int i=0; i< key.size();i++) {


        if (key[i] == stdLabel) {

            char *charLabel = new char[value[i].length() + 1];
            strcpy(charLabel, value[i].c_str());
            pjLabel = pj_str(charLabel);

        }else if(key[i] == stdIndex){

            char *charIndex = new char[value[i].length() + 1];
            strcpy(charIndex, value[i].c_str());
            pjIndex = pj_str(charIndex);

        }else if(key[i] == stdRegistrar){

            charRegistrar = new char[value[i].length() + 1];
            strcpy(charRegistrar, value[i].c_str());
            //pjRegistrar = pj_str(charRegistrar);

        }else
        {

        }
    }


    pjsua_acc_config acc_cfg;

    struct pjsip_generic_string_hdr LabelHeader,IndexHeader;

    pj_str_t name1 = pj_str("X-Label");
    pj_str_t value1 = pjLabel;

    pjsip_generic_string_hdr_init2(&LabelHeader, &name1, &value1);

    pj_str_t name2 = pj_str("X-Index");
    pj_str_t value2 = pjIndex;

    pjsip_generic_string_hdr_init2(&IndexHeader, &name2, &value2);

    pjsua_acc_get_config(acc_id, app_pool,&acc_cfg);

    /* remove old X-Label and X-Index from the reg_hdr_list */
    // Iterate list nodes.
    struct pjsip_hdr *it,*tmpIt;

    it = acc_cfg.reg_hdr_list.next;
    while (it != &acc_cfg.reg_hdr_list) {
        if(pj_strcmp(&name1, &it->name)==0 || pj_strcmp(&name2, &it->name)==0){
            tmpIt = it->next;
            pj_list_erase(it);
            it = tmpIt;
        }else{
            it = it->next;
        }
    }

    pj_list_push_back(&acc_cfg.reg_hdr_list, &LabelHeader);
    pj_list_push_back(&acc_cfg.reg_hdr_list, &IndexHeader);

    std::string cReg = std::string(charRegistrar);
    std::string sipReg = "sip:"+cReg+";transport=tls";

    char *sipRegistrar = new char[sipReg.length() + 1];
    strcpy(sipRegistrar, sipReg.c_str());

    pj_status_t status;
    const pj_str_t dst_uri = pj_str(sipRegistrar);
    status = pjsua_verify_sip_url(dst_uri.ptr);

    if (status != PJ_SUCCESS)
    {
        callbackObj->onDebugMessage("Error in SIP URI, Invalid URI");
        return;

    }

    pj_str_t pjProxy = pj_str(sipRegistrar);

    acc_cfg.proxy_cnt = 0;

    pj_strdup_with_null(app_pool, &acc_cfg.proxy[acc_cfg.proxy_cnt++], &pjProxy);

    status = pjsua_acc_modify(acc_id, &acc_cfg);

    if (status != PJ_SUCCESS)
         fprintf(stderr, "Error in relayVoipPushNotification funciton");
}

void updateBasePackets(pjsua_stream_stat streamStats){
    prePacketsReceived = streamStats.rtcp.rx.pkt;
    prePacketsSent = streamStats.rtcp.tx.pkt;
    preRemotePacketsLoss = streamStats.rtcp.rx.loss;
    preLocalPacketsLoss = streamStats.rtcp.tx.loss;
}


float getMOS(pjsua_stream_stat streamStats , string statsType , float fraction_loss){
    int packets_lost, packets_received;
    float jitter = 0.0, R = 0.0, MOS = 0.0;
    if (statsType == "local") {
        jitter = (float)streamStats.rtcp.tx.jitter.last/1000;
        if(jitter<=20) {
            jitter = 0;
        } else {
            jitter = jitter-20;
        }
    } else {
        jitter = (float)streamStats.rtcp.rx.jitter.last/1000;
        packets_lost = streamStats.rtcp.rx.loss - preRemotePacketsLoss;
        packets_received = streamStats.rtcp.rx.pkt - prePacketsReceived + packets_lost;
        fraction_loss = (packets_lost == 0 && packets_received == 0) ? 0 : (packets_received == 0)? 1.0f : (float)packets_lost / packets_received;
    }
    float effectiveLatency = (streamStats.rtcp.rtt.last / 2000.0f) + (jitter * 2) + 10;
    if(effectiveLatency < 160){
        R = 93.2f - (effectiveLatency/40);
    }else{
        R = 93.2f - (effectiveLatency - 120)/10;
    }
    R = R - (100*fraction_loss * 2.5);
    if (R <= 0) {
        MOS = 1;
    } else if (R > 100) {
        MOS = 4.5;
    } else {
        MOS = 1 + 0.035f * R + 7.10f/1000000 * R * (R - 60) * (100 - R);
    }
    return MOS;
}


void updateOpusBitrate(unsigned int maxAverageBitrate) {
    const unsigned audioCodecInfoSize = 64;
    const pj_str_t OPUS_CODEC = {"opus", 4};
    char *present;
    pjsua_codec_info audioCodecInfo[audioCodecInfoSize];
    unsigned audioCodecCount = audioCodecInfoSize;
    pj_status_t status = pjsua_enum_codecs(audioCodecInfo, &audioCodecCount);
    if (status != PJ_SUCCESS) {
        fprintf(stderr, "Error getting list of audio codecs");
    }

    for (int i = 0; i < audioCodecCount; i++) {
        pj_str_t codecId = audioCodecInfo[i].codec_id;
        present = pj_stristr(&codecId, &OPUS_CODEC);
        if (present) {
            pjmedia_codec_param param;
            const pjmedia_codec_info *codecInfo;
            pjmedia_codec_opus_config opus_cfg;
            unsigned count = 1;

            pjmedia_codec_mgr *endpointMgr = pjmedia_endpt_get_codec_mgr(pjsua_get_pjmedia_endpt());
            pjmedia_codec_mgr_find_codecs_by_id(endpointMgr, &codecId, &count, &codecInfo, NULL);
            pjmedia_codec_mgr_get_default_param(endpointMgr, codecInfo, &param);
            pjmedia_codec_opus_get_config(&opus_cfg);
            // Set max average bit rate
            opus_cfg.bit_rate = maxAverageBitrate;
            pjmedia_codec_opus_set_default_param(&opus_cfg, &param);
        }
    }
}


std::string getRemoteStats (){
    pjsua_stream_stat streamStats;
    pjsua_stream_info streamInfo;
    int packets_lost, packets_received;

    float remote_fraction_loss = 0.0;
    pjsua_call_id call_id;
    std::string remoteStatsDict = "";

    call_id = currrent_call_id;

    pj_status_t status = pjsua_call_get_stream_stat(call_id, 0, &streamStats);
    pj_status_t info_status = pjsua_call_get_stream_info(call_id, 0, &streamInfo);

    packets_lost = streamStats.rtcp.rx.loss - preRemotePacketsLoss;
    packets_received = streamStats.rtcp.rx.pkt - prePacketsReceived + packets_lost;
    updateBasePackets(streamStats);

    remote_fraction_loss = (packets_lost == 0 && packets_received == 0 ) ? 0 : (packets_received == 0) ? 1.0f : (float)packets_lost / packets_received;

    remoteStatsDict  = "{ ";
    remoteStatsDict  = remoteStatsDict + "bytesReceived : " + patch::to_string(streamStats.rtcp.rx.bytes) + " , ";
    remoteStatsDict  = remoteStatsDict + "fractionLoss : " + patch::to_string(remote_fraction_loss) + " , ";
    remoteStatsDict  = remoteStatsDict + "jitter : " + patch::to_string(streamStats.rtcp.rx.jitter.last/1000.0f) + " , ";
    remoteStatsDict  = remoteStatsDict + "packetsLost : " + patch::to_string(packets_lost) + " , ";
    remoteStatsDict  = remoteStatsDict + "packetsReceived : " + patch::to_string(packets_received) + " , ";
    remoteStatsDict  = remoteStatsDict + "ssrc : " + patch::to_string(streamInfo.info.aud.ssrc) + " } ";

    callbackObj->onDebugMessage("successfully collected remote stats ");
    return remoteStatsDict;

}

std::string getLocalStats(){
        pjsua_stream_stat streamStats;
        pjsua_stream_info streamInfo;
        double local_fraction_loss = 0.0, MOSLocal = 0.0, MOSRemote = 0.0, MOS = 0.0;
        float jitter = 0.0;
        int packets_lost, packets_sent;
        pjsua_call_id call_id;

        call_id = currrent_call_id;

        pj_status_t status = pjsua_call_get_stream_stat(call_id, 0, &streamStats);
        pj_status_t info_status = pjsua_call_get_stream_info(call_id, 0, &streamInfo);

        packets_lost = streamStats.rtcp.tx.loss - preLocalPacketsLoss;
        packets_sent = streamStats.rtcp.tx.pkt - prePacketsSent;

        local_fraction_loss = (packets_lost == 0 && packets_sent == 0) ? 0 : packets_sent == 0 ? 1.0f : (float) packets_lost / packets_sent ;

        MOSLocal = getMOS(streamStats,"local",local_fraction_loss);
        MOSRemote = getMOS(streamStats, "remote",0.0f);

        MOS = MOSLocal < MOSRemote ? MOSLocal : MOSRemote;

        string localStatsDict="";

        localStatsDict  = localStatsDict + "{ ";
        localStatsDict  = localStatsDict + "bytesSent : " + patch::to_string(streamStats.rtcp.tx.bytes) + " , ";
        localStatsDict  = localStatsDict + "fractionLoss : " + patch::to_string(local_fraction_loss) + " , ";

        //Handling MOS NaN value
        if(MOS != MOS){
            localStatsDict  = localStatsDict + "mos : " + "null" + " , ";
        }else{
            localStatsDict  = localStatsDict + "mos : " + patch::to_string(MOS) + " , ";
        }

        jitter = (float)streamStats.rtcp.tx.jitter.last/1000.0f;
        if(jitter<=20) {
            jitter = 0;
        } else {
            jitter = jitter-20;
        }

        localStatsDict  = localStatsDict + "rtt : " + patch::to_string(streamStats.rtcp.rtt.last/2000.0f) + " , ";
        localStatsDict  = localStatsDict + "jitter : " + patch::to_string(jitter) + " , ";
        localStatsDict  = localStatsDict + "packetsLost : " + patch::to_string(packets_lost) + " , ";
        localStatsDict  = localStatsDict + "packetsSent : " + patch::to_string(packets_sent) + " , ";
        localStatsDict  = localStatsDict + "ssrc : " + patch::to_string(streamInfo.info.aud.ssrc) + " , ";

        try{
            localStatsDict  = localStatsDict + "codec : " + patch::to_string(streamInfo.info.aud.fmt.encoding_name.ptr) + " } ";
        }catch(exception& e) {
            localStatsDict  = localStatsDict + "codec : \"undefined\"  } ";
            callbackObj->onDebugMessage("Could not fetch Codec");
        }

        callbackObj->onDebugMessage("successfully collected local stats ");
        return localStatsDict;

}

std::string _getAudioLevels(){
        pjsua_call_id call_id;

        call_id = currrent_call_id;

        unsigned tx_level, rx_level;
        int slot;
        pjsua_call_info call_info;
        pj_status_t call_info_status = pjsua_call_get_info(call_id, &call_info);
        if (call_info_status != PJ_SUCCESS) {
            callbackObj->onDebugMessage("Unable to get call info, error code");
        }
        pj_status_t signal_level_status = pjsua_conf_get_signal_level(call_info.conf_slot, &tx_level, &rx_level);
        if (signal_level_status != PJ_SUCCESS) {
            callbackObj->onDebugMessage("Unable to get signal level, error code ");
        }

        string audioLevels="";
        audioLevels  = audioLevels + "{ ";
        audioLevels  = audioLevels + "local : " + patch::to_string(tx_level) + " , ";
        audioLevels  = audioLevels + "remote : " + patch::to_string(rx_level) + " } ";
        return audioLevels;
}

//#endif
// #endif


 // Call canceled or timeout from the other side before answering
           //else if (call_info.state == PJSIP_INV_STATE_DISCONNECTED  && (call_info.last_status >= 486 && call_info.last_status <= 489)) {
           //       callbackObj->onDebugMessage("onCallDisconnected or timeout");
           //       callbackObj->onOutgoingCallRejected(call_id, pj_strbuf(&call_info.call_id));
           //}

 // Timeout
          //else if (call_info.state == PJSIP_INV_STATE_DISCONNECTED && call_info.last_status == 408) {
          //     callbackObj->onDebugMessage("onCallTimeout");
          //       callbackObj->onOutgoingCallInvalid(call_id, pj_strbuf(&call_info.call_id));
          //}
