package sk.knizat.tennisclub.entity;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

/**
 * JPA lifecycle listener that stamps {@link BaseEntity#getCreatedAt()} / {@link BaseEntity#getUpdatedAt()}
 * from the application {@link Clock}. Hibernate instantiates it through Spring's bean container, so the
 * {@code Clock} bean is injected and time stays deterministic in tests.
 */
@RequiredArgsConstructor
public class AuditListener {

    private final Clock clock;

    @PrePersist
    public void onCreate(BaseEntity entity) {
        entity.initTimestamps(clock.instant());
    }

    @PreUpdate
    public void onUpdate(BaseEntity entity) {
        entity.touch(clock.instant());
    }
}
