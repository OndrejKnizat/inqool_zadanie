package sk.knizat.tennisclub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the tennis club reservation system.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TennisClubApplication {

    public static void main(String[] args) {
        SpringApplication.run(TennisClubApplication.class, args);
    }
}
