package com.test.voip.controller;

import com.test.voip.dto.CallRequestDto;
import com.test.voip.dto.CallResponseDto;
import com.test.voip.service.SipCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/call", "/api/v1/call"})
public class SipCallController {

    private static final Logger log = LoggerFactory.getLogger(SipCallController.class);

    private final SipCallService sipCallService;

    public SipCallController(SipCallService sipCallService) {
        this.sipCallService = sipCallService;
    }

    @PostMapping("/start")
    public ResponseEntity<CallResponseDto> startCall(
            @RequestBody(required = false) CallRequestDto bodyRequest,
            @RequestParam(name = "ani", required = false) String paramAni,
            @RequestParam(name = "calleeUser", required = false) String paramCalleeUser,
            @RequestParam(name = "targetIp", required = false) String paramTargetIp,
            @RequestParam(name = "targetPort", required = false) Integer paramTargetPort) {

        String ani = bodyRequest != null && bodyRequest.getAni() != null ? bodyRequest.getAni() : paramAni;
        String calleeUser = bodyRequest != null && bodyRequest.getCalleeUser() != null ? bodyRequest.getCalleeUser() : paramCalleeUser;
        String targetIp = bodyRequest != null && bodyRequest.getTargetIp() != null ? bodyRequest.getTargetIp() : paramTargetIp;
        Integer targetPort = bodyRequest != null && bodyRequest.getTargetPort() != null ? bodyRequest.getTargetPort() : paramTargetPort;

        if (targetPort == null || targetPort <= 0) {
            targetPort = 5060;
        }

        if (ani == null || ani.isBlank()) {
            return ResponseEntity.badRequest().body(CallResponseDto.error("Missing required parameter: 'ani' (Caller ID)"));
        }
        if (calleeUser == null || calleeUser.isBlank()) {
            return ResponseEntity.badRequest().body(CallResponseDto.error("Missing required parameter: 'calleeUser' (Destination User)"));
        }
        if (targetIp == null || targetIp.isBlank()) {
            return ResponseEntity.badRequest().body(CallResponseDto.error("Missing required parameter: 'targetIp' (Destination IP)"));
        }

        try {
            log.info("Received request to start SIP call to {}@{}:{} from ANI {}", calleeUser, targetIp, targetPort, ani);
            String callId = sipCallService.makeSingleCall(ani.trim(), calleeUser.trim(), targetIp.trim(), targetPort);

            String successMsg = String.format("SIP INVITE dispatched successfully to %s@%s:%d", calleeUser, targetIp, targetPort);
            return ResponseEntity.ok(CallResponseDto.ok(successMsg, callId, ani, calleeUser));

        } catch (Exception e) {
            log.error("Failed to start SIP call: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CallResponseDto.error("Failed to initiate call: " + e.getMessage()));
        }
    }
}
