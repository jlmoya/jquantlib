package org.jquantlib.showcase.web;

import org.jquantlib.showcase.dto.DividendResponse;
import org.jquantlib.showcase.service.DividendOptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DividendApiController {

    private final DividendOptionService service;

    public DividendApiController(final DividendOptionService service) {
        this.service = service;
    }

    @GetMapping("/api/dividend")
    public DividendResponse price(
            @RequestParam(defaultValue = "Call") final String type,
            @RequestParam(defaultValue = "European") final String style,
            @RequestParam(defaultValue = "100") final double spot,
            @RequestParam(defaultValue = "100") final double strike,
            @RequestParam(defaultValue = "0.05") final double rate,
            @RequestParam(defaultValue = "0.00") final double yield,
            @RequestParam(defaultValue = "0.25") final double vol,
            @RequestParam(defaultValue = "365") final int days,
            @RequestParam(defaultValue = "2.0") final double dividendAmount,
            @RequestParam(defaultValue = "4") final int dividendCount) {
        return service.price(type, style, spot, strike, rate, yield, vol, days, dividendAmount, dividendCount);
    }
}
