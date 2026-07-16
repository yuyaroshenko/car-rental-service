package org.crd.carrental.exception;

/**
 * Thrown when a caller-supplied argument is not {@code null} but still
 * violates a business rule the method expects — for example, a blank
 * {@code carId} or a non-positive number of days. Distinct from
 * {@link NullPointerException}, which this codebase reserves for callers
 * passing {@code null} where the method contract doesn't allow it.
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * @param message a human-readable description of what was invalid
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
