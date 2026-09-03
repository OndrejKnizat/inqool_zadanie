package sk.knizat.tennisclub.service;

import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;

import java.util.List;

/**
 * Court CRUD. Court numbers are unique among non-deleted courts. A request that references a surface type
 * which does not exist or is soft-deleted fails with {@link sk.knizat.tennisclub.exception.ValidationException}
 * (400), not 404: the id is part of the request payload, not the addressed resource.
 */
public interface CourtService {

    /** Returns all non-deleted courts ordered by id. */
    List<CourtResponse> findAll();

    /**
     * @throws sk.knizat.tennisclub.exception.NotFoundException when missing or deleted
     */
    CourtResponse findById(Long id);

    /**
     * @throws sk.knizat.tennisclub.exception.ConflictException   when a non-deleted court with the same number exists
     * @throws sk.knizat.tennisclub.exception.ValidationException when the surface type does not exist or is deleted
     */
    CourtResponse create(CourtRequest request);

    /**
     * @throws sk.knizat.tennisclub.exception.NotFoundException   when the court is missing or deleted
     * @throws sk.knizat.tennisclub.exception.ConflictException   when the new number belongs to a different court
     * @throws sk.knizat.tennisclub.exception.ValidationException when the surface type does not exist or is deleted
     */
    CourtResponse update(Long id, CourtRequest request);

    /**
     * Soft-deletes the court. Past reservations stay as history (see ARCHITECTURE O-9).
     *
     * @throws sk.knizat.tennisclub.exception.NotFoundException when missing or deleted
     * @throws sk.knizat.tennisclub.exception.ConflictException when the court still has an unfinished reservation
     */
    void delete(Long id);
}
