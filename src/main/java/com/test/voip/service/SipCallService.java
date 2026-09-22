	package com.test.voip.service;

import com.test.voip.repository.CountryRepository;
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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SipCallService implements SipListener {

    private static final Logger log = LoggerFactory.getLogger(SipCallService.class);
    private static final String MAGIC_COOKIE = "z9hG4bK";

    @Value("${sip.local.bind-ip:0.0.0.0}")
    private String bindIp;

    @Value("${sip.local.port:5070}")
    private int localPort;

    @Value("${sip.local.contact-ip:127.0.0.1}")
    private String contactIp;

    @Value("${sip.stack.trace-level:0}")
    private String traceLevel;

    @Value("${sip.exception.bind-failed:Failed to bind UDP Listening Point}")
    private String bindFailedMessage;

    @Value("${sip.exception.stack-start-failed:Could not start SIP signaling stack}")
    private String stackStartFailedMessage;

    @Value("${rbt.enabled:true}")
    private boolean rbtEnabled;

    @Value("${rtp.local.port:8000}")
    private int rtpLocalPort;

    private final RtpService rtpService;
    private final ResourceService resourceService;
    private final AudioService audioService;
    private final CountryRepository countryRepository;

    private final Map<String, CallContext> activeCalls = new ConcurrentHashMap<>();

    private SipStack sipStack;
    private SipProvider sipProvider;
    private ListeningPoint listeningPoint;
    private HeaderFactory headerFactory;
    private AddressFactory addressFactory;
    private MessageFactory messageFactory;
    private ContentTypeHeader sdpContentType;
    private MaxForwardsHeader maxForwardsHeader;

    public SipCallService(
            RtpService rtpService,
            ResourceService resourceService,
            AudioService audioService,
            CountryRepository countryRepository) {
        this.rtpService = rtpService;
        this.resourceService = resourceService;
        this.audioService = audioService;
        this.countryRepository = countryRepository;
    }

    @PostConstruct
    public void init() {
        try {
            if ("auto".equalsIgnoreCase(contactIp) || contactIp == null || contactIp.isBlank()) {
                contactIp = InetAddress.getLocalHost().getHostAddress();
            }

            SipFactory sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist");

            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "VoipSignalingStack");
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", traceLevel);
            properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "false");
            properties.setProperty("gov.nist.javax.sip.STACK_LOGGER", "com.test.voip.sip.Slf4jStackLogger");
            properties.setProperty("gov.nist.javax.sip.SERVER_LOGGER", "com.test.voip.sip.Slf4jServerLogger");
            properties.setProperty("gov.nist.javax.sip.CACHE_CLIENT_CONNECTIONS", "false");

            sipStack = sipFactory.createSipStack(properties);
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            messageFactory = sipFactory.createMessageFactory();
            sdpContentType = headerFactory.createContentTypeHeader("application", "sdp");
            maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);

            int attempts = 0;
            int portToTry = localPort;
            while (attempts < 5) {
                try {
                    listeningPoint = sipStack.createListeningPoint(bindIp, portToTry, "udp");
                    this.localPort = portToTry;
                    break;
                } catch (Exception e) {
                    if (e.getMessage() != null && (e.getMessage().contains("Address already in use") || e.getCause() instanceof java.net.BindException)) {
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

            sipProvider = sipStack.createSipProvider(listeningPoint);
            sipProvider.addSipListener(this);
            log.info("SIP signaling bound to {}:{}", contactIp, this.localPort);

        } catch (Exception e) {
            log.error("Failed to initialize JAIN-SIP stack: {}", e.getMessage(), e);
            throw new IllegalStateException(stackStartFailedMessage, e);
        }
    }

    public String makeSingleCall(String caller, String callee, String targetIp, int targetPort) throws Exception {
        SipURI requestUri = addressFactory.createSipURI(callee, targetIp);
        requestUri.setPort(targetPort);

        SipURI fromUri = addressFactory.createSipURI(caller, contactIp);
        fromUri.setPort(localPort);
        Address fromAddress = addressFactory.createAddress(fromUri);
        fromAddress.setDisplayName(caller);
        String fromTag = Long.toHexString(ThreadLocalRandom.current().nextLong());
        FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, fromTag);

        SipURI toUri = addressFactory.createSipURI(callee, targetIp);
        toUri.setPort(targetPort);
        Address toAddress = addressFactory.createAddress(toUri);
        toAddress.setDisplayName(callee);
        ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

        String branch = MAGIC_COOKIE + Long.toHexString(ThreadLocalRandom.current().nextLong());
        ViaHeader viaHeader = headerFactory.createViaHeader(contactIp, localPort, "udp", branch);
        ArrayList<ViaHeader> viaHeaders = new ArrayList<>(1);
        viaHeaders.add(viaHeader);

        CallIdHeader callIdHeader = sipProvider.getNewCallId();
        String callId = callIdHeader.getCallId();
        CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);

        SipURI contactAddressUri = addressFactory.createSipURI(caller, contactIp);
        contactAddressUri.setPort(localPort);
        Address contactAddress = addressFactory.createAddress(contactAddressUri);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);

        Request inviteRequest = messageFactory.createRequest(
                requestUri, Request.INVITE, callIdHeader, cSeqHeader,
                fromHeader, toHeader, viaHeaders, maxForwardsHeader
        );
        inviteRequest.addHeader(contactHeader);

        String sdp = "v=0\r\no=" + caller + " 123456 654321 IN IP4 " + contactIp
                + "\r\ns=Talk\r\nc=IN IP4 " + contactIp
                + "\r\nt=0 0\r\nm=audio " + rtpLocalPort + " RTP/AVP 0\r\na=rtpmap:0 PCMU/8000\r\n";
        inviteRequest.setContent(sdp.getBytes(StandardCharsets.UTF_8), sdpContentType);

        String dialCode = countryRepository.resolveDialCode(caller, callee);
        activeCalls.put(callId, new CallContext(callId, targetIp, targetPort, dialCode));

        ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(inviteRequest);
        clientTransaction.sendRequest();

        log.info("Dispatched INVITE Call-ID: {} to {}@{}:{} [dialCode={}]", callId, callee, targetIp, targetPort, dialCode);
        return callId;
    }

    public boolean hangupCall(String callId) {
        CallContext context = activeCalls.get(callId);
        if (context == null) {
            log.warn("No active call found to hangup for Call-ID: {}", callId);
            return false;
        }

        try {
            cleanupCallMedia(callId);

            if (context.dialog != null && context.dialog.getState() == DialogState.CONFIRMED) {
                Request byeRequest = context.dialog.createRequest(Request.BYE);
                ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(byeRequest);
                context.dialog.sendRequest(clientTransaction);
                log.info("Sent BYE request for Call-ID: {}", callId);
            }

            activeCalls.remove(callId);
            return true;
        } catch (Exception e) {
            log.error("Error hanging up call {}: {}", callId, e.getMessage(), e);
            activeCalls.remove(callId);
            return false;
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();
        CallIdHeader callIdHeader = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        String callId = (callIdHeader != null) ? callIdHeader.getCallId() : "";

        CallContext context = activeCalls.get(callId);
        if (context != null && responseEvent.getDialog() != null) {
            context.dialog = responseEvent.getDialog();
        }

        if (statusCode == Response.TRYING) {
            log.info("100 Trying | Call-ID: {}", callId);

        } else if (statusCode == Response.RINGING) {
            log.info("180 Ringing | Call-ID: {}", callId);
            if (rbtEnabled && context != null) {
                resourceService.playResourceSong(callId, context.dialCode);
            }

        } else if (statusCode == Response.OK) {
            log.info("200 OK (answered) | Call-ID: {}", callId);
            resourceService.stopSong(callId);

            RemoteMediaEndpoint endpoint = parseRemoteSdp(response.getRawContent(),
                    (context != null ? context.targetIp : "127.0.0.1"), rtpLocalPort);
            log.info("Remote RTP endpoint: {}:{} | Call-ID: {}", endpoint.ip, endpoint.port, callId);

            audioService.startRecording(callId, "FULL_CALL", 8000, 1, 8, false, false);

            if (context != null) {
                rtpService.startRtpStream(callId, context.dialCode, endpoint.ip, endpoint.port);
                rtpService.startRtpReceiver(callId, rtpLocalPort);
            }

            sendAck(responseEvent, response);

        } else if (statusCode >= 400) {
            log.warn("{} {} | Call-ID: {}", statusCode, response.getReasonPhrase(), callId);
            cleanupCallMedia(callId);
            activeCalls.remove(callId);

        } else {
            log.info("{} {} | Call-ID: {}", statusCode, response.getReasonPhrase(), callId);
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        String method = request.getMethod();
        CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        String callId = (callIdHeader != null) ? callIdHeader.getCallId() : "";

        log.info("Incoming SIP {} {} | Call-ID: {}", method, request.getRequestURI(), callId);

        if (Request.BYE.equalsIgnoreCase(method)) {
            cleanupCallMedia(callId);
            activeCalls.remove(callId);

            try {
                ServerTransaction serverTransaction = requestEvent.getServerTransaction();
                if (serverTransaction == null) {
                    serverTransaction = sipProvider.getNewServerTransaction(request);
                }
                serverTransaction.sendResponse(messageFactory.createResponse(Response.OK, request));
                log.info("200 OK to BYE | Call-ID: {}", callId);
            } catch (Exception e) {
                log.error("Failed to respond to BYE: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.warn("SIP transaction timeout");
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("SIP IO error on {}:{}", exceptionEvent.getHost(), exceptionEvent.getPort());
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent event) { }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent event) { }

    @PreDestroy
    public void stop() {
        try {
            resourceService.stopAll();
            rtpService.stopAll();

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

    private void cleanupCallMedia(String callId) {
        resourceService.stopSong(callId);
        rtpService.stopRtp(callId);
        audioService.stopRecording(callId, "FULL_CALL");
    }

    private void sendAck(ResponseEvent responseEvent, Response response) {
        try {
            Dialog dialog = responseEvent.getDialog();
            if (dialog == null && responseEvent.getClientTransaction() != null) {
                dialog = responseEvent.getClientTransaction().getDialog();
            }
            if (dialog != null) {
                CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                long seqNumber = (cSeqHeader != null) ? cSeqHeader.getSeqNumber() : 1L;
                dialog.sendAck(dialog.createAck(seqNumber));
                log.info("ACK sent | Call-ID: {}", dialog.getCallId().getCallId());
            }
        } catch (Exception e) {
            log.error("Failed to send ACK: {}", e.getMessage(), e);
        }
    }

    private RemoteMediaEndpoint parseRemoteSdp(byte[] rawContent, String fallbackIp, int fallbackPort) {
        if (rawContent == null || rawContent.length == 0) {
            return new RemoteMediaEndpoint(fallbackIp, fallbackPort);
        }
        try {
            String sdp = new String(rawContent, StandardCharsets.UTF_8);
            String ip = fallbackIp;
            int port = fallbackPort;

            for (String line : sdp.split("\r?\n")) {
                line = line.trim();
                if (line.startsWith("c=IN IP4 ")) {
                    String parsed = line.substring(9).trim();
                    if (!parsed.equals("0.0.0.0") && !parsed.isBlank()) {
                        ip = parsed;
                    }
                } else if (line.startsWith("m=audio ")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        try {
                            int parsed = Integer.parseInt(parts[1]);
                            if (parsed > 0) port = parsed;
                        } catch (NumberFormatException ignored) { }
                    }
                }
            }
            return new RemoteMediaEndpoint(ip, port);
        } catch (Exception e) {
            log.warn("Failed to parse remote SDP: {}", e.getMessage());
            return new RemoteMediaEndpoint(fallbackIp, fallbackPort);
        }
    }

    private record RemoteMediaEndpoint(String ip, int port) { }

    private static class CallContext {
        final String callId;
        final String targetIp;
        final int targetPort;
        final String dialCode;
        volatile Dialog dialog;

        CallContext(String callId, String targetIp, int targetPort, String dialCode) {
            this.callId = callId;
            this.targetIp = targetIp;
            this.targetPort = targetPort;
            this.dialCode = dialCode;
        }
    }
}
