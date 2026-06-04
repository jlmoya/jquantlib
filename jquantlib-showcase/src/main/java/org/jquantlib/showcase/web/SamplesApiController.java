package org.jquantlib.showcase.web;

import java.util.List;

import org.jquantlib.showcase.dto.SampleInfo;
import org.jquantlib.showcase.dto.SampleRun;
import org.jquantlib.showcase.service.SamplesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/samples")
public class SamplesApiController {

    private final SamplesService service;

    public SamplesApiController(final SamplesService service) {
        this.service = service;
    }

    @GetMapping
    public List<SampleInfo> catalog() {
        return service.catalog();
    }

    @GetMapping("/{id}/run")
    public SampleRun run(@PathVariable final String id) {
        return service.run(id);
    }
}
