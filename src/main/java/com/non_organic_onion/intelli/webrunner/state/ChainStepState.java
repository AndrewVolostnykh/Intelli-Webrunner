package com.non_organic_onion.intelli.webrunner.state;

public class ChainStepState {
    public String requestId;
    public String successCodes = "200";
    public boolean runBasicBeforeRequest;
    public boolean runBasicAfterRequest;
    public boolean runBasicStress;
    public boolean runBasicTests;
    public String runIfScript = "";
    public String beforeRequestScript = "";
    public String afterRequestScript = "";
    public String interruptIfScript = "";
    public String rawRequestSnapshot = "";
    public String sentRequestSnapshot = "";
    public String responseSnapshot = "";
    public String resultBody = "";
    public String resultResponse = "";
    public String resultHeaders = "";
    public String resultCookies = "";
    public String resultBodySnapshot = "";
}
