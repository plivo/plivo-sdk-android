package com.plivo.endpoint;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;

public class Options {
    Boolean enableTracking;
    HashMap setupOptions;

    public Options( HashMap setupOptions){
        this.setupOptions = setupOptions;
        if (setupOptions.containsKey("enableTracking") && setupOptions.get(enableTracking)!=null) {
            this.enableTracking = (Boolean) setupOptions.get(enableTracking);
        } else {
            this.enableTracking = true;
        }
    }

    public Boolean isEnableTacking(){
        return this.enableTracking;
    }

    public JSONObject getOptions (){
        JSONObject options = new JSONObject(this.setupOptions);
        try{
            options.put("enableTracking",this.enableTracking);
        }catch (JSONException exception){
            exception.printStackTrace();
        }
        return options;
    }
}
