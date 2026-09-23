package com.test.voip.controller;

import com.test.voip.dto.CallRequestDto;
import com.test.voip.dto.CallResponseDto;
import com.test.voip.entity.CallDetail;
import com.test.voip.exception.SipCallException;
import com.test.voip.service.CallDetailService;
import com.test.voip.service.SipCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/call")
public class SipCallController {

    private static final Logger log = LoggerFactory.getLogger(SipCallController.class);

    private final SipCallService sipCallService;
    private final CallDetailService callDetailService;

    @Value("${sip.server.host:127.0.0.1}")
    private String serverHost;

    @Value("${sip.server.port:5060}")
    private int serverPort;

    @Value("${sip.message.success:SIP INVITE dispatched successfully}")
    private String successMessage = "SIP INVITE dispatched successfully";

    @Value("${sip.message.error:SIP call failed}")
    private String errorMessage = "SIP call failed";

    @Value("${sip.message.missing-caller:Missing required parameter: caller}")
    private String missingCallerMessage = "Missing required parameter: caller";

    @Value("${sip.message.missing-callee:Missing required parameter: callee}")
    private String missingCalleeMessage = "Missing required parameter: callee";

    @Value("${sip.message.hangup-success:Call terminated, audio recording saved}")
    private String hangupSuccessMessage = "Call terminated, audio recording saved";

    @Value("${sip.message.hangup-not-found:No active call found}")
    private String hangupNotFoundMessage = "No active call found";

    public SipCallController(SipCallService sipCallService, CallDetailService callDetailService) {
        this.sipCallService = sipCallService;
        this.callDetailService = callDetailService;
    }

    @GetMapping("/details")
    public ResponseEntity<List<CallDetail>> getAllCallDetails() {
        return ResponseEntity.ok(callDetailService.getAllCallDetails());
    }

    @GetMapping("/details/{callId}")
    public ResponseEntity<CallDetail> getCallDetail(@PathVariable String callId) {
        return ResponseEntity.ok(callDetailService.getCallDetailByCallId(callId));
    }

    @PostMapping
    public ResponseEntity<CallResponseDto> initiateCall(@RequestBody CallRequestDto request) {
        if (request == null || request.getCaller() == null || request.getCaller().isBlank()) {
            throw new SipCallException(missingCallerMessage);
        }
        if (request.getCallee() == null || request.getCallee().isBlank()) {
            throw new SipCallException(missingCalleeMessage);
        }

        try {
            log.info("Call request: {} -> {} via {}:{}", request.getCaller(), request.getCallee(), serverHost, serverPort);

            String callId = sipCallService.makeSingleCall(
                    request.getCaller().trim(), request.getCallee().trim(),
                    serverHost.trim(), serverPort);

            return ResponseEntity.ok(CallResponseDto.ok(successMessage, callId, request.getCaller(), request.getCallee()));
        } catch (Exception e) {
            log.error("SIP call failed: {}", e.getMessage(), e);
            throw new SipCallException(errorMessage, e);
        }
    }

    @PostMapping("/hangup")
    public ResponseEntity<Map<String, Object>> hangupCall(@RequestParam String callId) {
        boolean stopped = sipCallService.hangupCall(callId);
        return ResponseEntity.ok(Map.of(
                "callId", callId,
                "status", stopped ? "CALL_TERMINATED" : "CALL_NOT_FOUND",
                "message", stopped ? hangupSuccessMessage : hangupNotFoundMessage
        ));
    }

    @ExceptionHandler(SipCallException.class)
    public ResponseEntity<CallResponseDto> handleSipCallException(SipCallException ex) {
        HttpStatus status = (ex.getCause() != null) ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(CallResponseDto.error(ex.getMessage()));
    }
}
