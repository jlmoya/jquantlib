package org.jquantlib.showcase.web;

import org.jquantlib.showcase.dto.ImpliedVolResponse;
import org.jquantlib.showcase.dto.MonteCarloResponse;
import org.jquantlib.showcase.dto.OptionResponse;
import org.jquantlib.showcase.service.OptionPricingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API behind the option-pricing demos. The Thymeleaf pages call these with
 * {@code fetch()} and render the results + charts client-side.
 */
@RestController
@RequestMapping("/api/option")
public class OptionApiController {

    private final OptionPricingService service;

    public OptionApiController(final OptionPricingService service) {
        this.service = service;
    }

    @GetMapping("/european")
    public OptionResponse european(
            @RequestParam(defaultValue = "Call") final String type,
            @RequestParam(defaultValue = "100") final double spot,
            @RequestParam(defaultValue = "100") final double strike,
            @RequestParam(defaultValue = "0.20") final double vol,
            @RequestParam(defaultValue = "0.05") final double rate,
            @RequestParam(defaultValue = "0.00") final double div,
            @RequestParam(defaultValue = "180") final int days) {
        return service.priceEuropean(type, spot, strike, vol, rate, div, days);
    }

    @GetMapping("/american")
    public OptionResponse american(
            @RequestParam(defaultValue = "Put") final String type,
            @RequestParam(defaultValue = "100") final double spot,
            @RequestParam(defaultValue = "100") final double strike,
            @RequestParam(defaultValue = "0.20") final double vol,
            @RequestParam(defaultValue = "0.05") final double rate,
            @RequestParam(defaultValue = "0.04") final double div,
            @RequestParam(defaultValue = "180") final int days) {
        return service.priceAmerican(type, spot, strike, vol, rate, div, days);
    }

    @GetMapping("/montecarlo")
    public MonteCarloResponse monteCarlo(
            @RequestParam(defaultValue = "Call") final String type,
            @RequestParam(defaultValue = "100") final double spot,
            @RequestParam(defaultValue = "100") final double strike,
            @RequestParam(defaultValue = "0.20") final double vol,
            @RequestParam(defaultValue = "0.05") final double rate,
            @RequestParam(defaultValue = "0.00") final double div,
            @RequestParam(defaultValue = "365") final int days) {
        return service.monteCarloConvergence(type, spot, strike, vol, rate, div, days);
    }

    @GetMapping("/implied-vol")
    public ImpliedVolResponse impliedVol(
            @RequestParam(defaultValue = "Call") final String type,
            @RequestParam(defaultValue = "100") final double spot,
            @RequestParam(defaultValue = "100") final double strike,
            @RequestParam(defaultValue = "0.05") final double rate,
            @RequestParam(defaultValue = "0.00") final double div,
            @RequestParam(defaultValue = "180") final int days,
            @RequestParam(defaultValue = "7.0") final double price) {
        return service.impliedVolatility(type, spot, strike, rate, div, days, price);
    }
}
