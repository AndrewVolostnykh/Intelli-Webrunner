package com.non_organic_onion.intelli.webrunner.script;

import java.util.Map;

public class ScriptContext {
    public final VarsStore vars;
    public final ScriptLogger log;
    public final ScriptHelpers helpers;
    public final ScriptRequest request;
    public final ScriptRequest rawRequest;
    public final Object response;
    public final VarsStore globalContext;
    public final VarsStore chainContext;
    public final Map<String, Object> chainRequests;
    public final ScriptFlowControl flowControl;

    public ScriptContext(
        VarsStore vars,
        ScriptLogger log,
        ScriptHelpers helpers,
        ScriptRequest request,
        ScriptRequest rawRequest,
        Object response
    ) {
        this(vars, log, helpers, request, rawRequest, response, new VarsStore());
    }

    public ScriptContext(
        VarsStore vars,
        ScriptLogger log,
        ScriptHelpers helpers,
        ScriptRequest request,
        ScriptRequest rawRequest,
        Object response,
        VarsStore globalContext
    ) {
        this(vars, log, helpers, request, rawRequest, response, globalContext, new VarsStore());
    }

    public ScriptContext(
        VarsStore vars,
        ScriptLogger log,
        ScriptHelpers helpers,
        ScriptRequest request,
        ScriptRequest rawRequest,
        Object response,
        VarsStore globalContext,
        VarsStore chainContext
    ) {
        this(vars, log, helpers, request, rawRequest, response, globalContext, chainContext, Map.of());
    }

    public ScriptContext(
        VarsStore vars,
        ScriptLogger log,
        ScriptHelpers helpers,
        ScriptRequest request,
        ScriptRequest rawRequest,
        Object response,
        VarsStore globalContext,
        VarsStore chainContext,
        Map<String, Object> chainRequests
    ) {
        this(vars, log, helpers, request, rawRequest, response, globalContext, chainContext, chainRequests, new ScriptFlowControl());
    }

    public ScriptContext(
        VarsStore vars,
        ScriptLogger log,
        ScriptHelpers helpers,
        ScriptRequest request,
        ScriptRequest rawRequest,
        Object response,
        VarsStore globalContext,
        VarsStore chainContext,
        Map<String, Object> chainRequests,
        ScriptFlowControl flowControl
    ) {
        this.vars = vars;
        this.log = log;
        this.helpers = helpers;
        this.request = request;
        this.rawRequest = rawRequest;
        this.response = response;
        this.globalContext = globalContext == null ? new VarsStore() : globalContext;
        this.chainContext = chainContext == null ? new VarsStore() : chainContext;
        this.chainRequests = chainRequests == null ? Map.of() : chainRequests;
        this.flowControl = flowControl == null ? new ScriptFlowControl() : flowControl;
    }
}
