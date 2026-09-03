package sk.knizat.tennisclub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TennisClubApplicationTests {

    @Test
    void should_loadContext_when_testProfileActive() {
        // context startup is the assertion
    }
}
