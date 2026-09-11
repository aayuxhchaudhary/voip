package com.test.voip.dto;

public class CallRequestDto {

    private String ani;
    private String calleeUser;
    private String targetIp;
    private Integer targetPort = 5060;

    public CallRequestDto() {
    }

    public CallRequestDto(String ani, String calleeUser, String targetIp, Integer targetPort) {
        this.ani = ani;
        this.calleeUser = calleeUser;
        this.targetIp = targetIp;
        this.targetPort = (targetPort != null && targetPort > 0) ? targetPort : 5060;
    }

    public String getAni() {
        return ani;
    }

    public void setAni(String ani) {
        this.ani = ani;
    }

    public String getCalleeUser() {
        return calleeUser;
    }

    public void setCalleeUser(String calleeUser) {
        this.calleeUser = calleeUser;
    }

    public String getTargetIp() {
        return targetIp;
    }

    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }

    public Integer getTargetPort() {
        return targetPort != null && targetPort > 0 ? targetPort : 5060;
    }

    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }

    @Override
    public String toString() {
        return "CallRequestDto{" +
                "ani='" + ani + '\'' +
                ", calleeUser='" + calleeUser + '\'' +
                ", targetIp='" + targetIp + '\'' +
                ", targetPort=" + targetPort +
                '}';
    }
}
