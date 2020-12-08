package com.clearstream.hydrogen.messagetransform.converttohydrogendata.beans;

import lombok.Data;

import java.util.Map;

@Data
public class MessageHeaders {
    private String activityName;
    private String instituteCode;
    private String commonCode;
    private String isin;
    private boolean isHome;
    private boolean isNewObj=false;

    public MessageHeaders(Map<String, Object> headers){
        intialise(headers);
    }

    private void  intialise(Map<String, Object> headers){
        activityName = (String)headers.get("ACTIVITY_NM");
        instituteCode = (String)headers.get("INST_SHT_CD");
        commonCode = (String)headers.get("COMMON_CD");
        isin = (String)headers.get("ISIN_CD");
        isNewObj = convertYNtoBoolean((String)headers.get("IS_NEW_OBJ_FLG"));
        isHome = convertYNtoBoolean((String)headers.get("IS_HOME_OR_SINGLE"));
    }

    private boolean convertYNtoBoolean(String value){
        if (value != null && value.equalsIgnoreCase("Y")) {
               return true;
        }

        return false;
    }

}
