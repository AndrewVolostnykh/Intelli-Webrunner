package com.intelli.webrunner.script;

import com.intelli.webrunner.state.HeaderEntryState;
import com.intelli.webrunner.state.FormEntryState;

import java.util.List;

public class ScriptRequest {
    private String body;
    private List<HeaderEntryState> headers;
    private List<HeaderEntryState> params;
    private List<FormEntryState> formData;
    private String binaryFilePath;

    public ScriptRequest(String body, List<HeaderEntryState> headers) {
        this.body = body;
        this.headers = headers;
        this.params = List.of();
        this.formData = List.of();
        this.binaryFilePath = "";
    }

    public ScriptRequest(String body, List<HeaderEntryState> headers, List<HeaderEntryState> params) {
        this.body = body;
        this.headers = headers;
        this.params = params;
        this.formData = List.of();
        this.binaryFilePath = "";
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<HeaderEntryState> getHeaders() {
        return headers;
    }

    public void setHeaders(List<HeaderEntryState> headers) {
        this.headers = headers;
    }

    public List<HeaderEntryState> getParams() {
        return params;
    }

    public void setParams(List<HeaderEntryState> params) {
        this.params = params;
    }

    public List<FormEntryState> getFormData() {
        return formData;
    }

    public void setFormData(List<FormEntryState> formData) {
        this.formData = formData;
    }

    public String getBinaryFilePath() {
        return binaryFilePath;
    }

    public void setBinaryFilePath(String binaryFilePath) {
        this.binaryFilePath = binaryFilePath;
    }
}
