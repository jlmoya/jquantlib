package org.jquantlib.showcase.web;

import org.jquantlib.showcase.dto.ExoticResponse;
import org.jquantlib.showcase.service.ExoticOptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExoticApiController {

    private final ExoticOptionService service;

    public ExoticApiController(final ExoticOptionService service) {
        this.service = service;
    }

    @GetMapping("/api/barrier")
    public ExoticResponse barrier(
            @RequestParam(defaultValue = "Call") final String type,
            @RequestParam(defaultValue = "DownOut") final String barrierType,
            @RequestParam(defaultValue = "100") final double spot,
            @RequestParam(defaultValue = "100") final double strike,
            @RequestParam(defaultValue = "90") final double barrier,
            @RequestParam(defaultValue = "0") final double rebate,
            @RequestParam(defaultValue = "0.25") final double vol,
            @RequestParam(defaultValue = "0.05") final double rate,
            @RequestParam(defaultValue = "0.00") final double div,
            @RequestParam(defaultValue = "365") final int days) {
        return service.barrier(type, barrierType, spot, strike, barrier, rebate, vol, rate, div, days);
    }

    @GetMapping("/api/asian")
    public ExoticResponse asian(
            @RequestParam(defaultValue = "Call") final String type,
            @RequestParam(defaultValue = "100") final double spot,
            @RequestParam(defaultValue = "100") final double strike,
            @RequestParam(defaultValue = "0.25") final double vol,
            @RequestParam(defaultValue = "0.05") final double rate,
            @RequestParam(defaultValue = "0.00") final double div,
            @RequestParam(defaultValue = "365") final int days,
            @RequestParam(defaultValue = "12") final int fixings) {
        return service.asian(type, spot, strike, vol, rate, div, days, fixings);
    }
}
