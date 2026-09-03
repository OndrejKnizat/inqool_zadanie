package sk.knizat.tennisclub.dao;

import sk.knizat.tennisclub.entity.BaseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Generic persistence operations shared by every DAO. All read methods see only non-deleted rows;
 * deletion is always a soft delete (see {@link BaseEntity#markDeleted(Instant)}).
 *
 * @param <T> entity type
 */
public interface GenericDao<T extends BaseEntity> {

    /**
     * Finds a non-deleted entity by id.
     *
     * @return the entity, or empty when it does not exist or is soft-deleted
     */
    Optional<T> findById(Long id);

    /** Returns all non-deleted entities ordered by id. */
    List<T> findAll();

    /**
     * Persists a new entity ({@code id == null}) or merges a detached/updated one.
     * <p>
     * Updates must follow load-then-modify: load the managed entity via {@link #findById(Long)}, change it and
     * pass it here. Never build a fresh object with an id and merge it: merge copies every field, so
     * {@code createdAt} would be lost on the returned instance and a soft-deleted row would be silently revived.
     *
     * The change is flushed before returning, so lifecycle callbacks (audit timestamps, generated id) have
     * already run on the returned instance.
     *
     * @return the managed instance (same object for persist, possibly a different one for merge)
     */
    T save(T entity);

    /**
     * Soft-deletes the entity: marks it deleted at {@code now} and merges the change.
     * Idempotent for an already deleted entity. Flushed before returning.
     *
     * @param now deletion time supplied by the caller from the application {@code Clock}
     */
    void softDelete(T entity, Instant now);

    /** Tells whether a non-deleted entity with the given id exists. */
    boolean existsById(Long id);
}
