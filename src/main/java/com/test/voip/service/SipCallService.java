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
import java.util.UUID;

@Service
public class SipCallService implements SipListener {

    private static final Logger log = LoggerFactory.getLogger(SipCallService.class);

    @Value("${sip.local.bind-ip:0.0.0.0}")
    private String bindIp;

    @Value("${sip.local.port:5060}")
    private int localPort;

    @Value("${sip.local.contact-ip:127.0.0.1}")
    private String contactIp;

    @Value("${sip.stack.trace-level:32}")
    private String traceLevel;

    private SipFactory sipFactory;
    private SipStack sipStack;
    private SipProvider sipProvider;
    private ListeningPoint listeningPoint;

    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private MessageFactory messageFactory;

    @PostConstruct
    public void init() {
        try {
            if ("auto".equalsIgnoreCase(contactIp) || contactIp == null || contactIp.isBlank()) {
                contactIp = InetAddress.getLocalHost().getHostAddress();
            }

            sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "VoipSignalingStack");
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", traceLevel);
            properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "true");
            properties.setProperty("gov.nist.javax.sip.STACK_LOGGER", "com.test.voip.sip.Slf4jStackLogger");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOGGER", "com.test.voip.sip.Slf4jServerLogger");
            properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "false");

            sipStack = sipFactory.createSipStack(properties);

            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();

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
                throw new IllegalStateException("Failed to bind UDP Listening Point after multiple attempts");
            }

            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);

            log.info("Local SIP Contact IP: {}, Bound Port: {}", contactIp, this.localPort);

        } catch (Exception e) {
            log.error("Failed to initialize JAIN-SIP stack: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not start SIP signaling stack", e);
        }
    }

    public String makeSingleCall(String callerAni, String calleeUsername, String calleeIp, int calleePort) throws Exception {
        SipURI requestUri = addressFactory.createSipURI(calleeUsername, calleeIp);
        requestUri.setPort(calleePort);

        SipURI fromUri = addressFactory.createSipURI(callerAni, contactIp);
        fromUri.setPort(localPort);
        Address fromAddress = addressFactory.createAddress(fromUri);
        fromAddress.setDisplayName(callerAni);
        String fromTag = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, fromTag);

        SipURI toUri = addressFactory.createSipURI(calleeUsername, calleeIp);
        toUri.setPort(calleePort);
        Address toAddress = addressFactory.createAddress(toUri);
        toAddress.setDisplayName(calleeUsername);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
        String branch = "z9hG4bK" + UUID.randomUUID().toString().replace("-", "");
        ViaHeader viaHeader = headerFactory.createViaHeader(contactIp, localPort, "udp", branch);
        viaHeaders.add(viaHeader);

        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
        MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

        SipURI contactAddressUri = addressFactory.createSipURI(callerAni, contactIp);
        contactAddressUri.setPort(localPort);
        Address contactAddress = addressFactory.createAddress(contactAddressUri);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);

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

        ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
        String sdpBody = "v=0\r\n" +
                "o=" + callerAni + " 123456 654321 IN IP4 " + contactIp + "\r\n" +
                "s=Talk\r\n" +
                "c=IN IP4 " + contactIp + "\r\n" +
                "t=0 0\r\n" +
                "m=audio 8000 RTP/AVP 0\r\n" +
                "a=rtpmap:0 PCMU/8000\r\n";

        inviteRequest.setContent(sdpBody.getBytes(StandardCharsets.UTF_8), contentTypeHeader);

        ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(inviteRequest);
        clientTransaction.sendRequest();

        return callIdHeader.getCallId();
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();
        CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        String callId = (callIdHeader != null) ? callIdHeader.getCallId() : "UNKNOWN";

        log.info("[SIP INBOUND RESPONSE] Status: {} {} | Call-ID: {}",
                statusCode, response.getReasonPhrase(), callId);

        switch (statusCode) {
            case Response.TRYING:
                log.info(">> [100 Trying] Remote endpoint is processing the INVITE for Call-ID: {}", callId);
                break;

            case Response.RINGING:
                log.info(">> [180 Ringing] Remote user phone is currently ringing! Call-ID: {}", callId);
                break;

            case Response.OK:
                log.info(">> [200 OK] Call answered! Completing handshake by sending SIP ACK...");
                handle200Ok(responseEvent, response);
                break;

            default:
                if (statusCode >= 300) {
                    log.warn(">> [SIP Warning/Error] Received status code {} ({}) for Call-ID: {}",
                            statusCode, response.getReasonPhrase(), callId);
                }
                break;
        }
    }

    private void handle200Ok(ResponseEvent responseEvent, Response response) {
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
                log.info(">> [SIP ACK] Successfully sent ACK on Dialog ID: {} for Call-ID: {}",
                        dialog.getDialogId(), dialog.getCallId().getCallId());
            } else {
                log.warn(">> [SIP ACK] Dialog is null on 200 OK response; unable to send in-dialog ACK directly.");
            }
        } catch (Exception e) {
            log.error(">> [SIP ACK Error] Failed to create or dispatch ACK request: {}", e.getMessage(), e);
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        log.info("[SIP INBOUND REQUEST] Method: {} | RequestURI: {}",
                request.getMethod(), request.getRequestURI());
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("[SIP TIMEOUT] Transaction timed out. IsServerTransaction: {}",
                timeoutEvent.isServerTransaction());
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("[SIP IO ERROR] Network I/O issue on host: {}, port: {}, transport: {}",
                exceptionEvent.getHost(), exceptionEvent.getPort(), exceptionEvent.getTransport());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) {
        log.debug("[SIP TRANSACTION] Transaction terminated.");
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) {
        log.info("[SIP DIALOG] Dialog terminated: {}",
                (event.getDialog() != null) ? event.getDialog().getDialogId() : "N/A");
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping SIP stack and releasing UDP sockets...");
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
            log.info("SIP stack stopped cleanly.");
        } catch (Exception e) {
            log.warn("Error during SIP stack shutdown: {}", e.getMessage());
        }
    }
}
