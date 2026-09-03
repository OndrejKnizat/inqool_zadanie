package sk.knizat.tennisclub.dao;

import sk.knizat.tennisclub.entity.SurfaceType;

import java.util.Optional;

/** DAO of the {@link SurfaceType} code list. */
public interface SurfaceTypeDao extends GenericDao<SurfaceType> {

    /** Finds a non-deleted surface type by its exact name. */
    Optional<SurfaceType> findByName(String name);

    /** Counts non-deleted courts that reference the given surface type. */
    long countCourtsUsing(Long surfaceTypeId);
}
