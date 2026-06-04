package org.jquantlib.showcase.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Thymeleaf pages. Each demo is a thin HTML shell whose JavaScript
 * calls the matching JSON API and renders the results and charts.
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index(final Model model) {
        model.addAttribute("active", "home");
        return "index";
    }

    @GetMapping("/options")
    public String options(final Model model) {
        model.addAttribute("active", "options");
        return "options";
    }

    @GetMapping("/american")
    public String american(final Model model) {
        model.addAttribute("active", "american");
        return "american";
    }

    @GetMapping("/monte-carlo")
    public String monteCarlo(final Model model) {
        model.addAttribute("active", "monteCarlo");
        return "monte-carlo";
    }

    @GetMapping("/implied-vol")
    public String impliedVol(final Model model) {
        model.addAttribute("active", "impliedVol");
        return "implied-vol";
    }

    @GetMapping("/bonds")
    public String bonds(final Model model) {
        model.addAttribute("active", "bonds");
        return "bonds";
    }

    @GetMapping("/curve")
    public String curve(final Model model) {
        model.addAttribute("active", "curve");
        return "curve";
    }

    @GetMapping("/calendar")
    public String calendar(final Model model) {
        model.addAttribute("active", "calendar");
        return "calendar";
    }

    @GetMapping("/samples")
    public String samples(final Model model) {
        model.addAttribute("active", "samples");
        return "samples";
    }

    @GetMapping("/barrier")
    public String barrier(final Model model) {
        model.addAttribute("active", "barrier");
        return "barrier";
    }

    @GetMapping("/asian")
    public String asian(final Model model) {
        model.addAttribute("active", "asian");
        return "asian";
    }

    @GetMapping("/dividends")
    public String dividends(final Model model) {
        model.addAttribute("active", "dividends");
        return "dividends";
    }
}
