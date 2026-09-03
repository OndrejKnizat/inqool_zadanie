package sk.knizat.tennisclub.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

/** Removes all rows in FK-safe order; for tests that commit real data into a shared context database. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DatabaseCleaner {

    public static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM reservation");
        jdbcTemplate.update("DELETE FROM court");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update("DELETE FROM surface_type");
    }
}
