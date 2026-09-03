package sk.knizat.tennisclub.dao;

import sk.knizat.tennisclub.entity.Court;

import java.util.Optional;

/** DAO of {@link Court}. */
public interface CourtDao extends GenericDao<Court> {

    /** Finds a non-deleted court by its business court number. */
    Optional<Court> findByCourtNumber(Integer courtNumber);

    /** Tells whether a non-deleted court with the given number exists. */
    boolean existsByCourtNumber(Integer courtNumber);

    /**
     * Finds a non-deleted court by id and acquires a {@code PESSIMISTIC_WRITE} row lock on it, serialising
     * concurrent reservation attempts for the same court. Must be called inside a transaction.
     */
    Optional<Court> findByIdForUpdate(Long id);
}
