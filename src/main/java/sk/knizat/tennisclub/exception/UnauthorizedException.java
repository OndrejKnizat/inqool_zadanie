package sk.knizat.tennisclub.exception;

/** Authentication failure raised inside the application (invalid refresh token, ...); mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
