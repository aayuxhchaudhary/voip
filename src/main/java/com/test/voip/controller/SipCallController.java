package com.test.voip.controller;

import com.test.voip.dto.CallRequestDto;
import com.test.voip.dto.CallResponseDto;
import com.test.voip.exception.SipCallException;
import com.test.voip.service.SipCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/call")
public class SipCallController {

    private static final Logger log = LoggerFactory.getLogger(SipCallController.class);

    // SIP service engine
    private final SipCallService sipCallService;

    // Target SIP server IP and port
    @Value("${sip.server.host:127.0.0.1}")
    private String serverHost;

    @Value("${sip.server.port:5060}")
    private int serverPort;

    // Response and error messages from application.properties
    @Value("${sip.message.success:SIP INVITE dispatched successfully}")
    private String successMessage = "SIP INVITE dispatched successfully";

    @Value("${sip.message.error:SIP call failed}")
    private String errorMessage = "SIP call failed";

    @Value("${sip.message.missing-caller:Missing required parameter: caller}")
    private String missingCallerMessage = "Missing required parameter: caller";

    @Value("${sip.message.missing-callee:Missing required parameter: callee}")
    private String missingCalleeMessage = "Missing required parameter: callee";

    public SipCallController(SipCallService sipCallService) {
        this.sipCallService = sipCallService;
    }

    // REST endpoint to trigger call from JSON body (supports POST and PUT)
    @RequestMapping(method = {RequestMethod.POST})
    public ResponseEntity<CallResponseDto> initiateCall(@RequestBody CallRequestDto request) {
        // Validate required caller and callee fields
        if (request == null || request.getCaller() == null || request.getCaller().isBlank()) {
            throw new SipCallException(missingCallerMessage);
        }
        if (request.getCallee() == null || request.getCallee().isBlank()) {
            throw new SipCallException(missingCalleeMessage);
        }

        try {
            log.info("REST request to start call: caller='{}' -> callee='{}' via target {}:{}",
                    request.getCaller(), request.getCallee(), serverHost, serverPort);

            // Send SIP INVITE to remote PBX/phone
            String callId = sipCallService.makeSingleCall(
                    request.getCaller().trim(),
                    request.getCallee().trim(),
                    serverHost.trim(),
                    serverPort
            );

            // Return success with call details
            return ResponseEntity.ok(CallResponseDto.ok(successMessage, callId, request.getCaller(), request.getCallee()));

        } catch (Exception e) {
            log.error("Error making SIP call: {}", e.getMessage(), e);
            throw new SipCallException(errorMessage, e);
        }
    }

    // Handles validation (400) and call errors (500)
    @ExceptionHandler(SipCallException.class)
    public ResponseEntity<CallResponseDto> handleSipCallException(SipCallException ex) {
        HttpStatus status = (ex.getCause() != null) ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(CallResponseDto.error(ex.getMessage()));
    }
}
