package com.test.voip.exception;

public class SipCallException extends RuntimeException {

    public SipCallException(String message) {
        super(message);
    }

    public SipCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
