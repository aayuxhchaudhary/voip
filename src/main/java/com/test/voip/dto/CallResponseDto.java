package com.test.voip.dto;

public class CallResponseDto {

    private boolean success;
    private String message;
    private String callId;
    private String caller;
    private String callee;

    public CallResponseDto() { }

    public CallResponseDto(boolean success, String message, String callId, String caller, String callee) {
        this.success = success;
        this.message = message;
        this.callId = callId;
        this.caller = caller;
        this.callee = callee;
    }

    public static CallResponseDto ok(String message, String callId, String caller, String callee) {
        return new CallResponseDto(true, message, callId, caller, callee);
    }

    public static CallResponseDto error(String message) {
        return new CallResponseDto(false, message, null, null, null);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }

    public String getCallee() { return callee; }
    public void setCallee(String callee) { this.callee = callee; }
}
