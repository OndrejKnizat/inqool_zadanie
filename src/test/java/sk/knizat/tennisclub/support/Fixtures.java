package sk.knizat.tennisclub.support;

import jakarta.persistence.EntityManager;
import sk.knizat.tennisclub.entity.BaseEntity;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.GameType;
import sk.knizat.tennisclub.entity.Reservation;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.entity.User;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Builders of entity fixtures. The static methods create transient objects; the instance methods persist
 * them through the supplied {@link EntityManager} and return the managed instance.
 */
public final class Fixtures {

    public static final Instant T10 = Instant.parse("2026-06-01T10:00:00Z");
    public static final Instant T11 = Instant.parse("2026-06-01T11:00:00Z");
    public static final Instant T12 = Instant.parse("2026-06-01T12:00:00Z");
    public static final Instant T13 = Instant.parse("2026-06-01T13:00:00Z");

    private final EntityManager em;

    public Fixtures(EntityManager em) {
        this.em = em;
    }

    public static SurfaceType surfaceType(String name, String pricePerMinute) {
        SurfaceType surface = new SurfaceType();
        surface.setName(name);
        surface.setPricePerMinute(new BigDecimal(pricePerMinute));
        return surface;
    }

    public static Court court(Integer courtNumber, SurfaceType surfaceType) {
        Court court = new Court();
        court.setCourtNumber(courtNumber);
        court.setName("Court " + courtNumber);
        court.setSurfaceType(surfaceType);
        return court;
    }

    public static User user(String phoneNumber, String name) {
        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setName(name);
        user.setRole(Role.USER);
        return user;
    }

    public static Reservation reservation(Court court, User user, Instant start, Instant end) {
        Reservation reservation = new Reservation();
        reservation.setCourt(court);
        reservation.setUser(user);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setGameType(GameType.SINGLES);
        reservation.setPrice(new BigDecimal("100.00"));
        return reservation;
    }

    public SurfaceType persistSurfaceType(String name) {
        return persist(surfaceType(name, "2.00"));
    }

    public Court persistCourt(Integer courtNumber, SurfaceType surfaceType) {
        return persist(court(courtNumber, surfaceType));
    }

    /** Persists a court on a fresh surface type named after the court. */
    public Court persistCourt(Integer courtNumber) {
        return persistCourt(courtNumber, persistSurfaceType("Surface of court " + courtNumber));
    }

    public User persistUser(String phoneNumber) {
        return persist(user(phoneNumber, "User " + phoneNumber));
    }

    public Reservation persistReservation(Court court, User user, Instant start, Instant end) {
        return persist(reservation(court, user, start, end));
    }

    /** Persists an entity that is already soft-deleted (deleted at a fixed time in the past). */
    public <E extends BaseEntity> E persistDeleted(E entity) {
        entity.markDeleted(Instant.parse("2026-01-01T00:00:00Z"));
        return persist(entity);
    }

    public <E extends BaseEntity> E persist(E entity) {
        em.persist(entity);
        em.flush();
        return entity;
    }

    /** Flushes pending changes and clears the persistence context so subsequent reads hit the database. */
    public void flushAndClear() {
        em.flush();
        em.clear();
    }
}
