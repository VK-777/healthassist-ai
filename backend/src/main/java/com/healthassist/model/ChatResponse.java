package com.healthassist.model;

import java.util.List;

public class ChatResponse {

    private String response;
    private List<String> citations;
    private String workflowId;
    private String disclaimer;

    public ChatResponse() {}

    public ChatResponse(String response, List<String> citations, String workflowId, String disclaimer) {
        this.response = response;
        this.citations = citations;
        this.workflowId = workflowId;
        this.disclaimer = disclaimer;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public List<String> getCitations() {
        return citations;
    }

    public void setCitations(List<String> citations) {
        this.citations = citations;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
