package com.barumrho.robodebugger.domain;

import com.barumrho.robodebugger.Debugger;

import java.util.HashMap;
import java.util.Map;

public class PageDomain extends Domain {
    public PageDomain(Debugger debugger) {
        super(debugger);
    }

    @Override
    public String getDomainName() {
        return "Page";
    }

    @Override
    public Map<String, Object> respond(String method, Map<String, Object> params) {
        HashMap<String, Object> response = new HashMap<String, Object>();
        String error = null;
        if (method.equals("getResourceTree")) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            HashMap<String, Object> frameTree = new HashMap<String, Object>();
            HashMap<String, Object> frame = new HashMap<String, Object>();
            frame.put("id", "0");
            frame.put("loaderId", "0");
            frame.put("name", "Root");
            frame.put("securityOrigin", "com.freshbooks.android");
            frame.put("url", "/data/data/com.freshbooks.android/databases/freshbooks.db");

            frameTree.put("frame", frame);
            result.put("frameTree", frameTree);
            response.put("result", result);
        }
        response.put("error", error);
        return response;
    }
}
