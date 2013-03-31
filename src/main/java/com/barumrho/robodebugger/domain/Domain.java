package com.barumrho.robodebugger.domain;

import com.barumrho.robodebugger.Debugger;

import java.util.Map;

abstract public class Domain {
    protected Debugger mDebugger;
    public Domain(Debugger debugger) {
        mDebugger = debugger;
    }

    abstract public String getDomainName();
    abstract public Map<String, Object> respond(String method, Map<String, Object> params);
}
