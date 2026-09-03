package sk.knizat.tennisclub.dao.impl;

import org.springframework.stereotype.Repository;
import sk.knizat.tennisclub.dao.AbstractDao;
import sk.knizat.tennisclub.dao.SurfaceTypeDao;
import sk.knizat.tennisclub.entity.SurfaceType;

import java.util.Optional;

/** JPQL implementation of {@link SurfaceTypeDao}. */
@Repository
public class SurfaceTypeDaoImpl extends AbstractDao<SurfaceType> implements SurfaceTypeDao {

    public SurfaceTypeDaoImpl() {
        super(SurfaceType.class);
    }

    @Override
    public Optional<SurfaceType> findByName(String name) {
        return singleResult(em.createQuery(
                        "SELECT s FROM SurfaceType s WHERE s.name = :name AND s.deleted = false", SurfaceType.class)
                .setParameter("name", name));
    }

    @Override
    public long countCourtsUsing(Long surfaceTypeId) {
        return em.createQuery(
                        "SELECT COUNT(c) FROM Court c WHERE c.surfaceType.id = :surfaceTypeId AND c.deleted = false",
                        Long.class)
                .setParameter("surfaceTypeId", surfaceTypeId)
                .getSingleResult();
    }
}
