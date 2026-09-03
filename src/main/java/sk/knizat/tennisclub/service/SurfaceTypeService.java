package sk.knizat.tennisclub.service;

import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;

import java.util.List;

/**
 * Surface type code list. Names are unique among non-deleted surface types; the comparison is exact
 * (case-sensitive) after trimming.
 */
public interface SurfaceTypeService {

    /** Returns all non-deleted surface types ordered by id. */
    List<SurfaceTypeResponse> findAll();

    /**
     * @throws sk.knizat.tennisclub.exception.NotFoundException when missing or deleted
     */
    SurfaceTypeResponse findById(Long id);

    /**
     * @throws sk.knizat.tennisclub.exception.ConflictException when a non-deleted surface type with the same name exists
     */
    SurfaceTypeResponse create(SurfaceTypeRequest request);

    /**
     * @throws sk.knizat.tennisclub.exception.NotFoundException when missing or deleted
     * @throws sk.knizat.tennisclub.exception.ConflictException when the new name belongs to a different surface type
     */
    SurfaceTypeResponse update(Long id, SurfaceTypeRequest request);

    /**
     * Soft-deletes the surface type.
     *
     * @throws sk.knizat.tennisclub.exception.NotFoundException when missing or deleted
     * @throws sk.knizat.tennisclub.exception.ConflictException when a non-deleted court still uses it
     */
    void delete(Long id);
}
