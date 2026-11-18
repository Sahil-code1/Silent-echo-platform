package com.silentecho.silentecho_backend.exception;

/**
 * Framework-independent API error. The status is an HTTP status code so that
 * pure domain/security classes can raise it without depending on Spring.
 * Messages are user-facing and must never contain secrets or report content.
 */
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
