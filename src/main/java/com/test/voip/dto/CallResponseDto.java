package com.test.voip.dto;

// Standard JSON response returned by the API
public class CallResponseDto {

    // True if INVITE was sent successfully
    private boolean success;

    // Status or error message
    private String message;

    // Unique SIP Call-ID header
    private String callId;

    // Caller ANI
    private String caller;

    // Destination user/extension
    private String callee;

    public CallResponseDto() {
    }

    public CallResponseDto(boolean success, String message, String callId, String caller, String callee) {
        this.success = success;
        this.message = message;
        this.callId = callId;
        this.caller = caller;
        this.callee = callee;
    }

    // Success response helper
    public static CallResponseDto ok(String message, String callId, String caller, String callee) {
        return new CallResponseDto(true, message, callId, caller, callee);
    }

    // Error response helper
    public static CallResponseDto error(String message) {
        return new CallResponseDto(false, message, null, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getCallee() {
        return callee;
    }

    public void setCallee(String callee) {
        this.callee = callee;
    }
}
