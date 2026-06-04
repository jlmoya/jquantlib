package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * Result of pricing a vanilla option, including analytic Greeks, a cross-engine
 * price comparison, and a spot-sweep used to draw the payoff / price / Greek
 * charts on the client.
 */
public record OptionResponse(
        String summary,
        double npv,
        GreeksDto greeks,
        List<EngineQuote> engines,
        SweepDto sweep) {

    /** The five core Black–Scholes Greeks (theta expressed per calendar day). */
    public record GreeksDto(
            double delta,
            double gamma,
            double vega,
            double thetaPerDay,
            double rho,
            double dividendRho) {
    }

    /** One row of the "price the same option with every engine" comparison. */
    public record EngineQuote(
            String engine,
            String category,
            Double npv,
            String note) {
    }

    /** Parallel arrays over a grid of spot prices, for charting. */
    public record SweepDto(
            List<Double> spot,
            List<Double> price,
            List<Double> delta,
            List<Double> gamma,
            List<Double> intrinsic) {
    }
}
