package com.test.voip.service;

import com.test.voip.entity.CallDetail;

import java.util.List;

public interface CallDetailService {

    CallDetail recordDial(String callId, String srcNumber, String dstNumber);

    void recordRinging(String callId, int statusCode, String reasonPhrase);

    void recordAnswer(String callId);

    void recordHangup(String callId, String sipStatus);

    void recordFailure(String callId, int statusCode, String reasonPhrase);

    void recordTimeout(String callId);

    void recordMediaPaths(String callId, String fullCallPath, String calleePath);

    List<CallDetail> getAllCallDetails();

    CallDetail getCallDetailByCallId(String callId);
}
