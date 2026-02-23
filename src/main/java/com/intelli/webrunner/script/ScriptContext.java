package com.intelli.webrunner.script;

public class ScriptContext {
    public final VarsStore vars;
    public final ScriptLogger log;
    public final ScriptHelpers helpers;
    public final ScriptRequest request;
    public final ScriptRequest rawRequest;
    public final Object response;

    public ScriptContext(
        VarsStore vars,
        ScriptLogger log,
        ScriptHelpers helpers,
        ScriptRequest request,
        ScriptRequest rawRequest,
        Object response
    ) {
        this.vars = vars;
        this.log = log;
        this.helpers = helpers;
        this.request = request;
        this.rawRequest = rawRequest;
        this.response = response;
    }
}
