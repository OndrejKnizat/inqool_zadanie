package sk.knizat.tennisclub.dao.impl;

import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import sk.knizat.tennisclub.dao.AbstractDao;
import sk.knizat.tennisclub.dao.ReservationDao;
import sk.knizat.tennisclub.entity.Reservation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * JPQL implementation of {@link ReservationDao}. List queries {@code JOIN FETCH} the court, its surface type
 * and the user so that mappers can read the whole graph without N+1 selects.
 */
@Repository
public class ReservationDaoImpl extends AbstractDao<Reservation> implements ReservationDao {

    private static final String SELECT_GRAPH =
            "SELECT r FROM Reservation r JOIN FETCH r.court c JOIN FETCH c.surfaceType JOIN FETCH r.user u ";

    public ReservationDaoImpl() {
        super(Reservation.class);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Only {@code r.deleted} is filtered on purpose: the court is matched by number regardless of its own
     * deleted flag, so reservations made on a since-removed court remain listable.
     */
    @Override
    public List<Reservation> findByCourtNumberOrderByCreatedAt(Integer courtNumber) {
        return em.createQuery(SELECT_GRAPH
                        + "WHERE c.courtNumber = :courtNumber AND r.deleted = false "
                        + "ORDER BY r.createdAt ASC, r.id ASC", Reservation.class)
                .setParameter("courtNumber", courtNumber)
                .getResultList();
    }

    /** {@inheritDoc} Overridden so that the lazy court/user graph is fetched like in the other list queries. */
    @Override
    public List<Reservation> findAll() {
        return em.createQuery(SELECT_GRAPH + "WHERE r.deleted = false ORDER BY r.id", Reservation.class)
                .getResultList();
    }

    @Override
    public List<Reservation> findByPhoneNumber(String phoneNumber, boolean futureOnly, Instant now) {
        Objects.requireNonNull(phoneNumber, "phoneNumber");
        if (futureOnly) {
            Objects.requireNonNull(now, "now");
        }
        StringBuilder jpql = new StringBuilder(SELECT_GRAPH)
                .append("WHERE u.phoneNumber = :phoneNumber AND u.deleted = false AND r.deleted = false ");
        if (futureOnly) {
            jpql.append("AND r.startTime > :now ");
        }
        jpql.append("ORDER BY r.startTime ASC, r.id ASC");
        TypedQuery<Reservation> query = em.createQuery(jpql.toString(), Reservation.class)
                .setParameter("phoneNumber", phoneNumber);
        if (futureOnly) {
            query.setParameter("now", now);
        }
        return query.getResultList();
    }

    @Override
    public List<Reservation> findAllOrderByStartTime() {
        return em.createQuery(SELECT_GRAPH
                        + "WHERE r.deleted = false ORDER BY r.startTime ASC, r.id ASC", Reservation.class)
                .getResultList();
    }

    @Override
    public boolean existsOverlapping(Long courtId, Instant start, Instant end, Long excludeReservationId) {
        Objects.requireNonNull(courtId, "courtId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(r) FROM Reservation r WHERE r.court.id = :courtId AND r.deleted = false "
                        + "AND r.startTime < :end AND r.endTime > :start ");
        if (excludeReservationId != null) {
            jpql.append("AND r.id <> :excludeId");
        }
        TypedQuery<Long> query = em.createQuery(jpql.toString(), Long.class)
                .setParameter("courtId", courtId)
                .setParameter("start", start)
                .setParameter("end", end);
        if (excludeReservationId != null) {
            query.setParameter("excludeId", excludeReservationId);
        }
        return exists(query);
    }

    @Override
    public boolean existsUnfinishedByCourt(Long courtId, Instant now) {
        Objects.requireNonNull(courtId, "courtId");
        Objects.requireNonNull(now, "now");
        return exists(em.createQuery(
                        "SELECT COUNT(r) FROM Reservation r WHERE r.court.id = :courtId AND r.deleted = false "
                                + "AND r.endTime > :now", Long.class)
                .setParameter("courtId", courtId)
                .setParameter("now", now));
    }

    @Override
    public boolean existsUnfinishedByUser(Long userId, Instant now) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(now, "now");
        return exists(em.createQuery(
                        "SELECT COUNT(r) FROM Reservation r WHERE r.user.id = :userId AND r.deleted = false "
                                + "AND r.endTime > :now", Long.class)
                .setParameter("userId", userId)
                .setParameter("now", now));
    }
}
