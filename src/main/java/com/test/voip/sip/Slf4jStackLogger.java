package com.test.voip.sip;

import gov.nist.core.StackLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class Slf4jStackLogger implements StackLogger {

    private static final Logger log = LoggerFactory.getLogger("gov.nist.javax.sip.STACK");
    private boolean enabled = true;

    @Override public void logStackTrace() {
        if (log.isDebugEnabled()) log.debug("SIP Stack Trace", new Exception());
    }

    @Override public void logStackTrace(int traceLevel) {
        if (isLoggingEnabled(traceLevel)) log.debug("SIP Stack Trace (level: {})", traceLevel, new Exception());
    }

    @Override public int getLineCount() { return 0; }
    @Override public void logException(Throwable ex) { log.error("SIP exception: {}", ex.getMessage(), ex); }
    @Override public void logDebug(String message) { log.debug(message); }
    @Override public void logDebug(String message, Exception ex) { log.debug(message, ex); }
    @Override public void logTrace(String message) { log.trace(message); }
    @Override public void logFatalError(String message) { log.error("FATAL: {}", message); }
    @Override public void logError(String message) { log.error(message); }
    @Override public void logError(String message, Exception ex) { log.error(message, ex); }
    @Override public void logWarning(String message) { log.warn(message); }
    @Override public void logInfo(String message) { log.info(message); }

    @Override public boolean isLoggingEnabled() {
        return enabled && (log.isDebugEnabled() || log.isTraceEnabled() || log.isInfoEnabled());
    }

    @Override public boolean isLoggingEnabled(int traceLevel) { return isLoggingEnabled(); }
    @Override public void disableLogging() { this.enabled = false; }
    @Override public void enableLogging() { this.enabled = true; }
    @Override public void setBuildTimeStamp(String timeStamp) { }
    @Override public void setStackProperties(Properties properties) { }
    @Override public String getLoggerName() { return log.getName(); }
}
