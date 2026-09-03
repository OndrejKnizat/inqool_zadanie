package sk.knizat.tennisclub.service.impl;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import sk.knizat.tennisclub.exception.ConflictException;

import java.util.function.Supplier;

/**
 * Business keys (surface name, court number, customer phone) are unique among non-deleted rows. Services
 * check for duplicates before saving, but two concurrent requests can both pass that check; the database
 * unique indexes on the "active" key columns are the backstop, and their violation surfaces on the flush
 * inside {@code save} as a {@link DataIntegrityViolationException}, which this helper turns into a 409.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UniqueKeys {

    /**
     * Runs a save and maps a unique-key violation to a {@link ConflictException}.
     *
     * @param save            the persistence call
     * @param conflictMessage message of the conflict raised on a violation
     * @return the saved entity
     */
    static <T> T saveOrConflict(Supplier<T> save, String conflictMessage) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(conflictMessage);
        }
    }
}
