package org.jquantlib.showcase.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trivial liveness endpoint used to smoke-test that the application boots.
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "jquantlib-showcase up";
    }
}
