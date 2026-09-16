package com.test.voip.dto;

// Request body payload to initiate a SIP call
public class CallRequestDto {

    // Caller number / ANI (Caller ID)
    private String caller;

    // Callee number / extension to ring
    private String callee;

    public CallRequestDto() {
    }

    public CallRequestDto(String caller, String callee) {
        this.caller = caller;
        this.callee = callee;
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
