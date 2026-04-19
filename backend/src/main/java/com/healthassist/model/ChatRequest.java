package com.healthassist.model;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {

    @NotBlank(message = "Please provide a valid message")
    private String message;

    @NotBlank(message = "Please provide a valid sessionId")
    private String sessionId;

    public ChatRequest() {}

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
