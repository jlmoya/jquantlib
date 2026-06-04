package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * Result of pricing a path-dependent (barrier or Asian) option, with a vanilla
 * reference value and a spot-sweep that contrasts the exotic against the vanilla
 * payoff.
 */
public record ExoticResponse(
        String summary,
        double npv,
        double vanilla,
        Double barrierLevel,
        Sweep sweep) {

    /** Parallel arrays over a grid of spot prices. */
    public record Sweep(
            List<Double> spot,
            List<Double> exotic,
            List<Double> vanilla,
            List<Double> intrinsic) {
    }
}
