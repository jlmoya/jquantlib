package org.jquantlib.showcase.web;

import org.jquantlib.showcase.dto.CalendarResponse;
import org.jquantlib.showcase.service.CalendarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
public class CalendarApiController {

    private final CalendarService service;

    public CalendarApiController(final CalendarService service) {
        this.service = service;
    }

    @GetMapping
    public CalendarResponse explore(
            @RequestParam(defaultValue = "US-Settlement") final String calendar,
            @RequestParam(defaultValue = "12") final int horizonMonths,
            @RequestParam(defaultValue = "6") final int scheduleTenorMonths,
            @RequestParam(defaultValue = "3") final int scheduleYears) {
        return service.explore(calendar, horizonMonths, scheduleTenorMonths, scheduleYears);
    }
}
