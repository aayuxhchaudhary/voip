package com.test.voip.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SipCallService implements SipListener {

    private static final Logger log = LoggerFactory.getLogger(SipCallService.class);

    // RFC 3261 magic cookie required for SIP transaction branch IDs
    private static final String MAGIC_COOKIE = "z9hG4bK";

    // Local IP and UDP port to listen for incoming SIP packets
    @Value("${sip.local.bind-ip:0.0.0.0}")
    private String bindIp;

    @Value("${sip.local.port:5070}")
    private int localPort;

    // Contact IP advertised in SIP headers so other side knows where to reply
    @Value("${sip.local.contact-ip:127.0.0.1}")
    private String contactIp;

    @Value("${sip.stack.trace-level:32}")
    private String traceLevel;

    // Exception messages loaded from application.properties
    @Value("${sip.exception.bind-failed:Failed to bind UDP Listening Point}")
    private String bindFailedMessage = "Failed to bind UDP Listening Point";

    @Value("${sip.exception.stack-start-failed:Could not start SIP signaling stack}")
    private String stackStartFailedMessage = "Could not start SIP signaling stack";

    // JAIN-SIP stack components
    private SipStack sipStack;
    private SipProvider sipProvider;
    private ListeningPoint listeningPoint;

    // Pre-cached factories and headers for speed
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private MessageFactory messageFactory;
    private ContentTypeHeader sdpContentType;
    private MaxForwardsHeader maxForwardsHeader;

    // Initialize SIP stack on Spring Boot startup
    @PostConstruct
    public void init() {
        try {
            if ("auto".equalsIgnoreCase(contactIp) || contactIp == null || contactIp.isBlank()) {
                contactIp = InetAddress.getLocalHost().getHostAddress();
            }

            SipFactory sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            // Stack configuration: route internal NIST logs into SLF4J
            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "VoipSignalingStack");
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", traceLevel);
            properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "true");
            properties.setProperty("gov.nist.javax.sip.STACK_LOGGER", "com.test.voip.sip.Slf4jStackLogger");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOGGER", "com.test.voip.sip.Slf4jServerLogger");
            properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "false");

            sipStack = sipFactory.createSipStack(properties);

            // Reusable factories
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
            sdpContentType = headerFactory.createContentTypeHeader("application", "sdp");
            maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            // Bind UDP port, auto-fallback to next port if port is busy
            int attempts = 0;
            int portToTry = localPort;
            while (attempts < 5) {
                try {
                    listeningPoint = sipStack.createListeningPoint(bindIp, portToTry, "udp");
                    this.localPort = portToTry;
                    break;
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("Address already in use") || e.getCause() instanceof java.net.BindException) {
                        portToTry = (portToTry == 5060) ? 5070 : portToTry + 1;
                        attempts++;
                    } else {
                        throw e;
                    }
                }
            }

            if (listeningPoint == null) {
                throw new IllegalStateException(bindFailedMessage);
            }

            // Register this class as listener for SIP events
            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);

            log.info("SIP signaling bound to {}:{}", contactIp, this.localPort);

        } catch (Exception e) {
            log.error("Failed to initialize JAIN-SIP stack: {}", e.getMessage(), e);
            throw new IllegalStateException(stackStartFailedMessage, e);
        }
    }

    // Build and send SIP INVITE packet over UDP
    public String makeSingleCall(String caller, String callee, String targetIp, int targetPort) throws Exception {
        // Destination address (Request-URI)
        SipURI requestUri = addressFactory.createSipURI(callee, targetIp);
        requestUri.setPort(targetPort);

        // Caller identity (From header)
        SipURI fromUri = addressFactory.createSipURI(caller, contactIp);
        fromUri.setPort(localPort);
        Address fromAddress = addressFactory.createAddress(fromUri);
        fromAddress.setDisplayName(caller);
        String fromTag = Long.toHexString(ThreadLocalRandom.current().nextLong());
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, fromTag);

        // Callee identity (To header)
        SipURI toUri = addressFactory.createSipURI(callee, targetIp);
        toUri.setPort(targetPort);
        Address toAddress = addressFactory.createAddress(toUri);
        toAddress.setDisplayName(callee);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        // Routing path and transaction branch (Via header)
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>(1);
        String branch = MAGIC_COOKIE + Long.toHexString(ThreadLocalRandom.current().nextLong());
        ViaHeader viaHeader = headerFactory.createViaHeader(contactIp, localPort, "udp", branch);
        viaHeaders.add(viaHeader);

        // Globally unique ID for this call and sequence counter
        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

        // Direct return socket address (Contact header)
        SipURI contactAddressUri = addressFactory.createSipURI(caller, contactIp);
        contactAddressUri.setPort(localPort);
        Address contactAddress = addressFactory.createAddress(contactAddressUri);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);

        // Assemble the SIP INVITE request
        Request inviteRequest = messageFactory.createRequest(
                requestUri,
                Request.INVITE,
                callIdHeader,
                cSeqHeader,
                fromHeader,
                toHeader,
                viaHeaders,
                maxForwardsHeader
        );
        inviteRequest.addHeader(contactHeader);

        // SDP audio payload: G.711u (PCMU) on port 8000
        String sdp = new StringBuilder(180)
                .append("v=0\r\no=").append(caller).append(" 123456 654321 IN IP4 ").append(contactIp)
                .append("\r\ns=Talk\r\nc=IN IP4 ").append(contactIp)
                .append("\r\nt=0 0\r\nm=audio 8000 RTP/AVP 0\r\na=rtpmap:0 PCMU/8000\r\n")
                .toString();

        inviteRequest.setContent(sdp.getBytes(StandardCharsets.UTF_8), sdpContentType);

        // Send UDP packet
        ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(inviteRequest);
        clientTransaction.sendRequest();

        log.info("Dispatched INVITE Call-ID: {} to {}@{}:{}", callIdHeader.getCallId(), callee, targetIp, targetPort);
        return callIdHeader.getCallId();
    }

    // Handle responses (100 Trying, 180 Ringing, 200 OK)
    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();
        CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        String callId = (callIdHeader != null) ? callIdHeader.getCallId() : "";

        if (statusCode == Response.TRYING) {
            log.info("SIP Response: 100 Trying (Server locating callee) | Call-ID: {}", callId);
        } else if (statusCode == Response.RINGING) {
            log.info("SIP Response: 180 Ringing (Remote phone ringing) | Call-ID: {}", callId);
        } else if (statusCode == Response.OK) {
            log.info("SIP Response: 200 OK (Call answered) | Call-ID: {}", callId);
            sendAck(responseEvent, response);
        } else if (statusCode >= 400) {
            log.warn("SIP Response: {} {} (Call rejected or failed) | Call-ID: {}", statusCode, response.getReasonPhrase(), callId);
        } else {
            log.info("SIP Response: {} {} | Call-ID: {}", statusCode, response.getReasonPhrase(), callId);
        }
    }

    // Send ACK to establish active call session
    private void sendAck(ResponseEvent responseEvent, Response response) {
        try {
            Dialog dialog = responseEvent.getDialog();
            if (dialog == null && responseEvent.getClientTransaction() != null) {
                dialog = responseEvent.getClientTransaction().getDialog();
            }

            if (dialog != null) {
                CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                long seqNumber = (cSeqHeader != null) ? cSeqHeader.getSeqNumber() : 1L;
                Request ackRequest = dialog.createAck(seqNumber);
                dialog.sendAck(ackRequest);
                log.info("Sent ACK for Call-ID: {}", dialog.getCallId().getCallId());
            }
        } catch (Exception e) {
            log.error("Failed to send ACK: {}", e.getMessage(), e);
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        log.info("Incoming Request: {} {}", request.getMethod(), request.getRequestURI());
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("SIP transaction timeout");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("SIP IO error on host: {}, port: {}", exceptionEvent.getHost(), exceptionEvent.getPort());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) {
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) {
    }

    // Clean shutdown to release UDP port
    @PreDestroy
    public void stop() {
        try {
            if (sipStack != null) {
                if (sipProvider != null) {
                    sipProvider.removeSipListener(this);
                    if (listeningPoint != null) {
                        sipProvider.removeListeningPoint(listeningPoint);
                        sipStack.deleteListeningPoint(listeningPoint);
                    }
                    sipStack.deleteSipProvider(sipProvider);
                }
                sipStack.stop();
            }
            log.info("SIP stack stopped");
        } catch (Exception e) {
            log.warn("SIP stack stop error: {}", e.getMessage());
        }
    }
}
