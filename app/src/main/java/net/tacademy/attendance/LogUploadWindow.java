package net.tacademy.attendance;

import java.util.*;
import org.json.*;

/** A time cursor for changes observed when a report is prepared; original test dates remain untouched. */
final class LogUploadWindow {
    record Selection(JSONArray records, JSONObject observations, long through) {}
    private LogUploadWindow() {}
    static String canonical(Object value) throws JSONException {
        if (value instanceof JSONObject object) {
            List<String> keys=new ArrayList<>();object.keys().forEachRemaining(keys::add);Collections.sort(keys);
            List<String> fields=new ArrayList<>();
            for(String key:keys)fields.add(JSONObject.quote(key)+":"+canonical(object.get(key)));
            return "{"+String.join(",",fields)+"}";
        }
        if(value instanceof JSONArray array){List<String> values=new ArrayList<>();for(int i=0;i<array.length();i++)values.add(canonical(array.get(i)));return "["+String.join(",",values)+"]";}
        return value instanceof String ? JSONObject.quote((String)value) : String.valueOf(value);
    }
    private static String key(JSONObject record) throws JSONException {
        String id=record.optString("test_id","");
        return !id.isEmpty()&&!record.isNull("test_id") ? "test:"+id : "legacy:"+LockLogExport.digest(canonical(record));
    }
    static JSONObject remember(JSONArray records,long at) throws JSONException {
        JSONObject result=new JSONObject();
        for(int i=0;i<records.length();i++){
            JSONObject record=records.getJSONObject(i);
            result.put(key(record),new JSONObject().put("hash",LockLogExport.digest(canonical(record))).put("at_ms",at));
        }
        return result;
    }
    static Selection select(JSONArray records,JSONObject previous,long after,long now) throws JSONException {
        long newest=after;
        for(Iterator<String> keys=previous.keys();keys.hasNext();)newest=Math.max(newest,previous.getJSONObject(keys.next()).optLong("at_ms",0));
        // Keep successive observations ordered, even within one millisecond or after a clock correction.
        long changedAt=Math.max(now,newest+1),through=Math.max(now,after);
        JSONObject observed=new JSONObject();JSONArray selected=new JSONArray();
        for(int i=0;i<records.length();i++){
            JSONObject record=records.getJSONObject(i);String key=key(record),hash=LockLogExport.digest(canonical(record));
            JSONObject old=previous.optJSONObject(key);
            long at=old!=null&&hash.equals(old.optString("hash")) ? old.optLong("at_ms",changedAt) : changedAt;
            observed.put(key,new JSONObject().put("hash",hash).put("at_ms",at));
            if(at>after){selected.put(record);through=Math.max(through,at);}
        }
        return new Selection(selected,observed,through);
    }
}
