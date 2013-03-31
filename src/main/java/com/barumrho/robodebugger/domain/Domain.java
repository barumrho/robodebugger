package com.barumrho.robodebugger.domain;

import com.barumrho.robodebugger.Debugger;

import java.util.Map;

abstract public class Domain {
    public static final String DOMAIN = null;

    protected Debugger mDebugger;
    public Domain(Debugger debugger) {
        mDebugger = debugger;
    }

    abstract public Map<String, Object> respond(String method, Map<String, Object> params);
}
