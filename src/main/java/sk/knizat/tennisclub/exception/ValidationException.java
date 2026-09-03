package sk.knizat.tennisclub.exception;

/** Business validation failure (invalid interval, overlapping reservation, ...); mapped to HTTP 400. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
