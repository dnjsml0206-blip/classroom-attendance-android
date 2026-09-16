package net.tacademy.attendance;

import java.net.URI;
import java.util.Locale;

/** Observation codes only: an unreadable Android window is never proof of a website block. */
final class ChromeDiagnostics {
    private ChromeDiagnostics() {}
    static String address(String shown) {
        if(shown==null)return "HIDDEN";
        if(shown.trim().isEmpty())return "EMPTY";
        try {
            String value=shown.trim();if(!value.contains("://"))value="https://"+value;
            if(!SiteProtocol.allowedNavigation(value))return "OTHER_ADDRESS";
            String path=URI.create(value).getPath();
            if("/Worknet/Present/Present.asp".equalsIgnoreCase(path))return "ATTENDANCE";
            if("/Worknet/Present/PAuthWay.asp".equalsIgnoreCase(path))return "AUTHENTICATION";
            return "SITE_OTHER";
        }catch(RuntimeException e){return "OTHER_ADDRESS";}
    }
    static boolean siteAddress(String address){return "ATTENDANCE".equals(address)||"AUTHENTICATION".equals(address)||"SITE_OTHER".equals(address);}
    static String pageSignal(String address,String text) {
        if(!siteAddress(address))return "NOT_OBSERVED";
        String s=text==null?"":text.toUpperCase(Locale.ROOT);
        String compact=s.replaceAll("\\s+","");
        if(s.contains("ERR_CERT_")||s.contains("ERR_SSL_")||s.contains("연결이 비공개로 설정되어 있지 않습니다"))return "TLS_ERROR";
        if(s.contains("ERR_CONNECTION_")||s.contains("ERR_NAME_NOT_RESOLVED")||s.contains("ERR_INTERNET_DISCONNECTED")||s.contains("ERR_NETWORK_CHANGED"))return "NETWORK_ERROR";
        if(s.contains("HTTP ERROR 429")||s.contains("429 TOO MANY REQUESTS")||s.contains("TOO MANY REQUESTS")||s.contains("요청이 너무 많"))return "RATE_LIMIT";
        if(s.contains("HTTP ERROR 403")||s.contains("403 FORBIDDEN")||s.contains("ACCESS DENIED")||s.contains("REQUEST BLOCKED")||s.contains("접근이 차단")||s.contains("접속이 차단")||s.contains("접근 권한이 없"))return "ACCESS_DENIED";
        if(s.contains("VERIFY YOU ARE HUMAN")||s.contains("로봇이 아닙니다")||s.contains("사람인지 확인"))return "HUMAN_CHECK";
        if(ChromePage.serverUnavailable(s)||s.contains("500 INTERNAL SERVER ERROR"))return "SERVER_ERROR";
        if(compact.matches(".*(?:진행중인훈련생|진행중인과정)이(?:아니|아닙).*" ))return "TRAINEE_REJECTED";
        if(compact.contains("인증절차를진행한후")||compact.contains("로그인이필요")||compact.contains("세션이만료"))return "AUTH_REQUIRED";
        if(compact.contains("허용된IP가아닙니다")||compact.contains("등록된IP가아닙니다")||compact.contains("지정된와이파이")||compact.contains("교육장내에서만")||compact.contains("교육기관WIFI내에서")||compact.contains("교육기관WIFI에연결"))return "SITE_NETWORK_RESTRICTION";
        return "NONE_RECOGNIZED";
    }
    static String dialogKind(boolean nativeDialog,String text){
        if(!nativeDialog)return "NONE";
        if(ChromePage.checkoutConfirmation(text))return "CHECKOUT_CONFIRMATION";
        String signal=pageSignal("ATTENDANCE",text);
        return "NONE_RECOGNIZED".equals(signal)?"UNKNOWN_DIALOG":signal;
    }
    static String dialogFailure(String kind){
        return switch(kind){
            case "SITE_NETWORK_RESTRICTION"->"SITE_WIFI_REJECTED_DIALOG";
            case "AUTH_REQUIRED","TRAINEE_REJECTED"->"SITE_AUTH_DIALOG";
            case "SERVER_ERROR","NETWORK_ERROR","TLS_ERROR"->"SITE_ERROR_DIALOG";
            case "ACCESS_DENIED","RATE_LIMIT","HUMAN_CHECK"->"SITE_RESTRICTION_DIALOG";
            default->"SITE_UNKNOWN_DIALOG";
        };
    }
    static String diagnosis(String owner,boolean interactive,boolean locked,String readError,String wifi,
                            String address,String signal,int visible,String button,String stage) {
        if("RETURNED_TO_APP_BEFORE_RESULT".equals(stage))return "APP_RETURNED_BEFORE_RECORD";
        if(!"NONE".equals(readError))return "LOCAL_READ_ERROR";
        if(!interactive||locked)return "SCREEN_LOCKED_OR_OFF";
        if(!"ALLOWED".equals(wifi)&&!"NOT_OBSERVED".equals(wifi))return "WIFI_PREREQUISITE";
        if("ROOT_UNAVAILABLE".equals(owner))return "ACCESSIBILITY_ROOT_UNAVAILABLE";
        if(!"CHROME".equals(owner))return "CHROME_NOT_FOREGROUND";
        // Signal must belong to the CURRENT verified site address, not a previously visible tab.
        if(siteAddress(address))switch(signal){
            case "ACCESS_DENIED":case "RATE_LIMIT":case "HUMAN_CHECK":return "SITE_RESTRICTION_SHOWN";
            case "AUTH_REQUIRED":case "TRAINEE_REJECTED":return "SITE_AUTH_MESSAGE_SHOWN";
            case "SITE_NETWORK_RESTRICTION":return "SITE_NETWORK_MESSAGE_SHOWN";
            case "TLS_ERROR":case "NETWORK_ERROR":return "BROWSER_CONNECTION_ERROR_SHOWN";
            case "SERVER_ERROR":return "SERVER_ERROR_SHOWN";
        }
        if(stage.contains("CONFIRM_STILL_VISIBLE")||stage.contains("CONFIRM_REAPPEARED"))return "CONFIRMATION_NOT_DISMISSED";
        if(stage.contains("SITE_UNKNOWN_DIALOG"))return "SITE_DIALOG_UNCLASSIFIED";
        if(stage.contains("CONFIRM_BUTTON_UNAVAILABLE"))return "CONFIRM_BUTTON_NOT_EXPOSED";
        if("BUTTON_ACTION_REJECTED".equals(stage)||"CONFIRM_ACTION_REJECTED".equals(stage))return "UI_ACTION_REJECTED";
        if(!"ATTENDANCE".equals(address))return "ADDRESS_UNVERIFIED";
        if(visible==0)return "PAGE_NODES_UNAVAILABLE";
        if(stage.contains("DATE_")||stage.contains("RECORD_UNREADABLE"))return "PAGE_FORMAT_UNVERIFIED";
        if(stage.contains("BUTTON_UNAVAILABLE")&&!"READY".equals(button))return "BUTTON_NOT_EXPOSED";
        if(stage.contains("RECORD_NOT_UPDATED")||stage.contains("CONFIRMATION_NOT_VISIBLE"))return "CLICK_RESULT_UNCONFIRMED";
        return "UNDETERMINED";
    }
}
