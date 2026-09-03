package sk.knizat.tennisclub.exception;

/** State conflict (duplicate name, deleting an entity still in use, ...); mapped to HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
