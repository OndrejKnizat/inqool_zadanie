package sk.knizat.tennisclub.exception;

/** Thrown when a requested entity does not exist or is soft-deleted; mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    /**
     * Builds the standard "Type with id X not found" message.
     *
     * @param entityName human-readable entity name, e.g. {@code "SurfaceType"}
     * @param id         identifier that was looked up
     */
    public static NotFoundException of(String entityName, Object id) {
        return new NotFoundException(entityName + " with id " + id + " not found");
    }
}
