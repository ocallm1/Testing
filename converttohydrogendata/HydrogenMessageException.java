package com.clearstream.hydrogen.messagetransform.converttohydrogendata;

public class HydrogenMessageException extends RuntimeException {
    public HydrogenMessageException() {
        super();
    }

    public HydrogenMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public HydrogenMessageException(String message) {
        super(message);
    }

    public HydrogenMessageException(Throwable cause) {
        super(cause);
    }
}

