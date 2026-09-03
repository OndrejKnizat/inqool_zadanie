package sk.knizat.tennisclub.dao.impl;

import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import sk.knizat.tennisclub.dao.AbstractDao;
import sk.knizat.tennisclub.dao.CourtDao;
import sk.knizat.tennisclub.entity.Court;

import java.util.List;
import java.util.Optional;

/** JPQL implementation of {@link CourtDao}. */
@Repository
public class CourtDaoImpl extends AbstractDao<Court> implements CourtDao {

    public CourtDaoImpl() {
        super(Court.class);
    }

    /** {@inheritDoc} Overridden to {@code JOIN FETCH} the lazy surface type so mappers do not trigger N+1 selects. */
    @Override
    public List<Court> findAll() {
        return em.createQuery(
                        "SELECT c FROM Court c JOIN FETCH c.surfaceType WHERE c.deleted = false ORDER BY c.id",
                        Court.class)
                .getResultList();
    }

    @Override
    public Optional<Court> findByCourtNumber(Integer courtNumber) {
        return singleResult(em.createQuery(
                        "SELECT c FROM Court c WHERE c.courtNumber = :courtNumber AND c.deleted = false", Court.class)
                .setParameter("courtNumber", courtNumber));
    }

    @Override
    public boolean existsByCourtNumber(Integer courtNumber) {
        return exists(em.createQuery(
                        "SELECT COUNT(c) FROM Court c WHERE c.courtNumber = :courtNumber AND c.deleted = false",
                        Long.class)
                .setParameter("courtNumber", courtNumber));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Uses {@code em.find} with a lock mode (JPQL {@code setLockMode} on a filtered query is not portable),
     * then applies the soft-delete filter in memory. Locking a deleted row is harmless: it is released with
     * the transaction.
     */
    @Override
    public Optional<Court> findByIdForUpdate(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Court.class, id, LockModeType.PESSIMISTIC_WRITE))
                .filter(court -> !court.isDeleted());
    }
}
