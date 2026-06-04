package org.jquantlib.showcase.web;

import org.jquantlib.showcase.dto.BondResponse;
import org.jquantlib.showcase.service.BondService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bond")
public class BondApiController {

    private final BondService service;

    public BondApiController(final BondService service) {
        this.service = service;
    }

    @GetMapping
    public BondResponse price(
            @RequestParam(defaultValue = "1000") final double face,
            @RequestParam(defaultValue = "5.0") final double coupon,
            @RequestParam(defaultValue = "2") final int couponsPerYear,
            @RequestParam(defaultValue = "10") final int years,
            @RequestParam(defaultValue = "4.0") final double rate,
            @RequestParam(defaultValue = "3") final int settlementDays,
            @RequestParam(defaultValue = "TARGET") final String calendar) {
        return service.price(face, coupon, couponsPerYear, years, rate, settlementDays, calendar);
    }
}
