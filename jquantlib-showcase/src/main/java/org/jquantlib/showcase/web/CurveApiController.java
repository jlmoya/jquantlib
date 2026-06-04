package org.jquantlib.showcase.web;

import org.jquantlib.showcase.dto.CurveResponse;
import org.jquantlib.showcase.service.YieldCurveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curve")
public class CurveApiController {

    private final YieldCurveService service;

    public CurveApiController(final YieldCurveService service) {
        this.service = service;
    }

    @GetMapping
    public CurveResponse build(
            @RequestParam(defaultValue = "3.0") final double front,
            @RequestParam(defaultValue = "3.3") final double y1,
            @RequestParam(defaultValue = "3.6") final double y2,
            @RequestParam(defaultValue = "4.0") final double y5,
            @RequestParam(defaultValue = "4.4") final double y10,
            @RequestParam(defaultValue = "4.7") final double y30) {
        return service.build(front, y1, y2, y5, y10, y30);
    }
}
