package org.jquantlib.showcase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the JQuantLib showcase web application.
 *
 * <p>Run with {@code mvn spring-boot:run} (or {@code java -jar}) and open
 * {@code http://localhost:8080} to explore the demos.
 */
@SpringBootApplication
public class JQuantLibShowcaseApplication {

    public static void main(final String[] args) {
        SpringApplication.run(JQuantLibShowcaseApplication.class, args);
    }
}
