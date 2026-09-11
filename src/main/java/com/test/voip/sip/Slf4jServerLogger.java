package com.test.voip.sip;

import gov.nist.core.ServerLogger;
import gov.nist.javax.sip.message.SIPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.SipStack;
import java.util.Properties;

public class Slf4jServerLogger implements ServerLogger {

    private static final Logger log = LoggerFactory.getLogger("gov.nist.javax.sip.MESSAGES");

    public Slf4jServerLogger() {
    }

    @Override
    public void closeLogFile() {
    }

    @Override
    public void logMessage(SIPMessage message, String from, String to, boolean sender, long time) {
        if (log.isDebugEnabled()) {
            String dir = sender ? "OUTBOUND -> " : "INBOUND <- ";
            log.debug("{} From: {}, To: {}\n{}", dir, from, to, message != null ? message.encode() : "null");
        }
    }

    @Override
    public void logMessage(SIPMessage message, String from, String to, String status, boolean sender, long time) {
        logMessage(message, from, to, sender, time);
    }

    @Override
    public void logMessage(SIPMessage message, String from, String to, String status, boolean sender) {
        logMessage(message, from, to, sender, System.currentTimeMillis());
    }

    @Override
    public void logException(Exception ex) {
        log.error("SIP ServerLogger Exception: {}", ex.getMessage(), ex);
    }

    @Override
    public void setStackProperties(Properties properties) {
    }

    @Override
    public void setSipStack(SipStack sipStack) {
    }
}
