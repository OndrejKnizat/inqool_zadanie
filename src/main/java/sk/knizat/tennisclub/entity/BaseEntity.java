package sk.knizat.tennisclub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.time.Instant;

/**
 * Common base of all entities: identity primary key, audit timestamps and the soft-delete flag.
 * <p>
 * Rows are never physically removed; {@link #markDeleted(Instant)} flips {@code deleted} and every DAO query
 * filters on {@code deleted = false}. Audit timestamps are filled by {@link AuditListener} from the injected
 * {@link java.time.Clock}; they have no public setters.
 * <p>
 * Identity: two entities are equal when they are of the same persistent class (Hibernate proxies are unwrapped)
 * and share a non-null id. {@code hashCode} is stable across persist (it does not depend on the id), so entities
 * may be stored in hash-based collections before they are saved.
 */
@MappedSuperclass
@EntityListeners(AuditListener.class)
@Getter
@Setter
@NoArgsConstructor
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Setter(AccessLevel.NONE)
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Fills the audit timestamps on first persist; values already present are kept. */
    void initTimestamps(Instant now) {
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /** Refreshes {@code updatedAt}. */
    void touch(Instant now) {
        updatedAt = now;
    }

    /**
     * Soft-deletes this entity. Idempotent: a second call keeps the original deletion time.
     *
     * @param now the deletion time, supplied by the caller from the injected {@code Clock}
     */
    public void markDeleted(Instant now) {
        if (deleted) {
            return;
        }
        this.deleted = true;
        this.deletedAt = now;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }
        Long otherId = ((BaseEntity) o).getId();
        return id != null && id.equals(otherId);
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return Hibernate.getClass(this).getSimpleName() + "{id=" + getId() + "}";
    }
}
