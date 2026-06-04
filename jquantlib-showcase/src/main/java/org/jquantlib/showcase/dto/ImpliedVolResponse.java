package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * Result of backing an implied volatility out of a market option price, plus a
 * price-vs-volatility curve showing the (monotone) relationship the solver
 * inverts.
 */
public record ImpliedVolResponse(
        String summary,
        double impliedVolPercent,
        double marketPrice,
        double recoveredPrice,
        List<Double> volPercent,
        List<Double> price) {
}
