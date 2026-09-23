package com.test.voip.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "call_details")
public class CallDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String callId;

    private String srcNumber;
    private String dstNumber;
    private LocalDateTime dialTime;
    private LocalDateTime answerTime;
    private LocalDateTime endTime;
    private Long pddMs;
    private Double durationSeconds;
    private String status;
    private String sipStatus;

    @Column(length = 1024)
    private String fullCallAudioPath;

    @Column(length = 1024)
    private String calleeAudioPath;

    public CallDetail() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getSrcNumber() { return srcNumber; }
    public void setSrcNumber(String srcNumber) { this.srcNumber = srcNumber; }

    public String getDstNumber() { return dstNumber; }
    public void setDstNumber(String dstNumber) { this.dstNumber = dstNumber; }

    public LocalDateTime getDialTime() { return dialTime; }
    public void setDialTime(LocalDateTime dialTime) { this.dialTime = dialTime; }

    public LocalDateTime getAnswerTime() { return answerTime; }
    public void setAnswerTime(LocalDateTime answerTime) { this.answerTime = answerTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getPddMs() { return pddMs; }
    public void setPddMs(Long pddMs) { this.pddMs = pddMs; }

    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSipStatus() { return sipStatus; }
    public void setSipStatus(String sipStatus) { this.sipStatus = sipStatus; }

    public String getFullCallAudioPath() { return fullCallAudioPath; }
    public void setFullCallAudioPath(String fullCallAudioPath) { this.fullCallAudioPath = fullCallAudioPath; }

    public String getCalleeAudioPath() { return calleeAudioPath; }
    public void setCalleeAudioPath(String calleeAudioPath) { this.calleeAudioPath = calleeAudioPath; }
}
