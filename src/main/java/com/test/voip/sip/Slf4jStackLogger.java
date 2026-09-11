package com.test.voip.sip;

import gov.nist.core.StackLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class Slf4jStackLogger implements StackLogger {

    private static final Logger log = LoggerFactory.getLogger("gov.nist.javax.sip.STACK");
    private boolean enabled = true;
    private int lineCount = 0;

    public Slf4jStackLogger() {
    }

    @Override
    public void logStackTrace() {
        if (log.isDebugEnabled()) {
            log.debug("SIP Stack Trace", new Exception("SIP Stack Trace"));
        }
    }

    @Override
    public void logStackTrace(int traceLevel) {
        if (isLoggingEnabled(traceLevel)) {
            log.debug("SIP Stack Trace (level: {})", traceLevel, new Exception("SIP Stack Trace"));
        }
    }

    @Override
    public int getLineCount() {
        return lineCount;
    }

    @Override
    public void logException(Throwable ex) {
        log.error("SIP Stack Exception: {}", ex.getMessage(), ex);
    }

    @Override
    public void logDebug(String message) {
        lineCount++;
        log.debug(message);
    }

    @Override
    public void logDebug(String message, Exception ex) {
        lineCount++;
        log.debug(message, ex);
    }

    @Override
    public void logTrace(String message) {
        lineCount++;
        log.trace(message);
    }

    @Override
    public void logFatalError(String message) {
        lineCount++;
        log.error("FATAL: {}", message);
    }

    @Override
    public void logError(String message) {
        lineCount++;
        log.error(message);
    }

    @Override
    public boolean isLoggingEnabled() {
        return enabled && (log.isDebugEnabled() || log.isTraceEnabled() || log.isInfoEnabled());
    }

    @Override
    public boolean isLoggingEnabled(int traceLevel) {
        return isLoggingEnabled();
    }

    @Override
    public void logError(String message, Exception ex) {
        lineCount++;
        log.error(message, ex);
    }

    @Override
    public void logWarning(String message) {
        lineCount++;
        log.warn(message);
    }

    @Override
    public void logInfo(String message) {
        lineCount++;
        log.info(message);
    }

    @Override
    public void disableLogging() {
        this.enabled = false;
    }

    @Override
    public void enableLogging() {
        this.enabled = true;
    }

    @Override
    public void setBuildTimeStamp(String timeStamp) {
    }

    @Override
    public void setStackProperties(Properties properties) {
    }

    @Override
    public String getLoggerName() {
        return log.getName();
    }
}
