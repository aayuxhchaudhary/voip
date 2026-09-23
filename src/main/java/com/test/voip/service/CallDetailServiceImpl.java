package com.test.voip.service;

import com.test.voip.entity.CallDetail;
import com.test.voip.repository.CallDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CallDetailServiceImpl implements CallDetailService {

    private static final Logger log = LoggerFactory.getLogger(CallDetailServiceImpl.class);

    private final CallDetailRepository callDetailRepository;

    @Value("${call.error.not-found:Call detail not found}")
    private String notFoundMessage = "Call detail not found";

    private final Map<String, Long> dialTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Long> answerTimestamps = new ConcurrentHashMap<>();

    public CallDetailServiceImpl(CallDetailRepository callDetailRepository) {
        this.callDetailRepository = callDetailRepository;
    }

    @Override
    public CallDetail recordDial(String callId, String srcNumber, String dstNumber) {
        dialTimestamps.put(callId, System.currentTimeMillis());

        CallDetail detail = new CallDetail();
        detail.setCallId(callId);
        detail.setSrcNumber(srcNumber);
        detail.setDstNumber(dstNumber);
        detail.setDialTime(LocalDateTime.now());
        detail.setStatus("CALLING");
        detail.setSipStatus("INVITE");
        return callDetailRepository.save(detail);
    }

    @Override
    public void recordRinging(String callId, int statusCode, String reasonPhrase) {
        callDetailRepository.findByCallId(callId).ifPresent(detail -> {
            if (detail.getPddMs() == null) {
                Long dialTs = dialTimestamps.get(callId);
                long pdd = (dialTs != null) ? System.currentTimeMillis() - dialTs : 0L;
                detail.setPddMs(pdd);
            }
            detail.setStatus("RINGING");
            detail.setSipStatus(statusCode + " " + reasonPhrase);
            callDetailRepository.save(detail);
        });
    }

    @Override
    public void recordAnswer(String callId) {
        long now = System.currentTimeMillis();
        answerTimestamps.put(callId, now);

        callDetailRepository.findByCallId(callId).ifPresent(detail -> {
            if (detail.getPddMs() == null) {
                Long dialTs = dialTimestamps.get(callId);
                long pdd = (dialTs != null) ? now - dialTs : 0L;
                detail.setPddMs(pdd);
            }
            detail.setAnswerTime(LocalDateTime.now());
            detail.setStatus("ANSWERED");
            detail.setSipStatus("200 OK");
            callDetailRepository.save(detail);
        });
    }

    @Override
    public void recordHangup(String callId, String sipStatus) {
        LocalDateTime now = LocalDateTime.now();
        Long answerTs = answerTimestamps.remove(callId);
        dialTimestamps.remove(callId);

        callDetailRepository.findByCallId(callId).ifPresent(detail -> {
            detail.setEndTime(now);
            if (answerTs != null) {
                detail.setDurationSeconds(Math.max(0.0, (System.currentTimeMillis() - answerTs) / 1000.0));
                detail.setStatus("ANSWERED");
            } else {
                detail.setDurationSeconds(0.0);
                if ("CALLING".equalsIgnoreCase(detail.getStatus()) || "RINGING".equalsIgnoreCase(detail.getStatus())) {
                    detail.setStatus("CANCELLED");
                }
            }
            detail.setSipStatus(sipStatus);
            callDetailRepository.save(detail);
        });
    }

    @Override
    public void recordFailure(String callId, int statusCode, String reasonPhrase) {
        LocalDateTime now = LocalDateTime.now();
        dialTimestamps.remove(callId);
        answerTimestamps.remove(callId);

        callDetailRepository.findByCallId(callId).ifPresent(detail -> {
            detail.setEndTime(now);
            detail.setDurationSeconds(0.0);
            detail.setStatus(mapStatus(statusCode));
            detail.setSipStatus(statusCode + " " + reasonPhrase);
            callDetailRepository.save(detail);
        });
    }

    @Override
    public void recordTimeout(String callId) {
        LocalDateTime now = LocalDateTime.now();
        dialTimestamps.remove(callId);
        answerTimestamps.remove(callId);

        callDetailRepository.findByCallId(callId).ifPresent(detail -> {
            detail.setEndTime(now);
            detail.setDurationSeconds(0.0);
            detail.setStatus("TIMEOUT");
            detail.setSipStatus("408 Request Timeout");
            callDetailRepository.save(detail);
        });
    }

    @Override
    public void recordMediaPaths(String callId, String fullCallPath, String calleePath) {
        callDetailRepository.findByCallId(callId).ifPresent(detail -> {
            if (fullCallPath != null) detail.setFullCallAudioPath(fullCallPath);
            if (calleePath != null) detail.setCalleeAudioPath(calleePath);
            callDetailRepository.save(detail);
        });
    }

    @Override
    public List<CallDetail> getAllCallDetails() {
        return callDetailRepository.findAllByOrderByDialTimeDesc();
    }

    @Override
    public CallDetail getCallDetailByCallId(String callId) {
        return callDetailRepository.findByCallId(callId)
                .orElseThrow(() -> new RuntimeException(notFoundMessage + ": " + callId));
    }

    private String mapStatus(int statusCode) {
        return switch (statusCode) {
            case 486 -> "BUSY";
            case 487 -> "CANCELLED";
            case 408 -> "TIMEOUT";
            default -> (statusCode >= 400) ? "FAILED" : "COMPLETED";
        };
    }
}
