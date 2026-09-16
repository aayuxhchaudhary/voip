package com.test.voip.exception;

// Custom exception thrown during SIP call validation or transmission failures
public class SipCallException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SipCallException(String message) {
        super(message);
    }

    public SipCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
