package com.budgetguardian.network;

/**
 * Failure of an HTTP API call: transport error, timeout, or a non-2xx
 * response. Carries the HTTP status (0 when the request never reached the
 * server) so callers can distinguish "not found" from "backend down".
 */
public class ApiException extends Exception {

    private final int status;

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** @return HTTP status of the failed response, or 0 for transport failures */
    public int status() {
        return status;
    }
}
