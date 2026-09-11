package com.test.voip.dto;

public class CallResponseDto {

    private boolean success;
    private String message;
    private String callId;
    private String ani;
    private String callee;

    public CallResponseDto() {
    }

    public CallResponseDto(boolean success, String message, String callId, String ani, String callee) {
        this.success = success;
        this.message = message;
        this.callId = callId;
        this.ani = ani;
        this.callee = callee;
    }

    public static CallResponseDto ok(String message, String callId, String ani, String callee) {
        return new CallResponseDto(true, message, callId, ani, callee);
    }

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

    public String getAni() {
        return ani;
    }

    public void setAni(String ani) {
        this.ani = ani;
    }

    public String getCallee() {
        return callee;
    }

    public void setCallee(String callee) {
        this.callee = callee;
    }
}
