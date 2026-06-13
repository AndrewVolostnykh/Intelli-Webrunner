package com.intelli.webrunner.script;

public class ScriptContext {
    public final VarsStore vars;
    public final ScriptLogger log;
    public final ScriptHelpers helpers;
    public final ScriptRequest request;
    public final ScriptRequest rawRequest;
    public final Object response;
    public final VarsStore globalContext;

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
        this.vars = vars;
        this.log = log;
        this.helpers = helpers;
        this.request = request;
        this.rawRequest = rawRequest;
        this.response = response;
        this.globalContext = globalContext == null ? new VarsStore() : globalContext;
    }
}
