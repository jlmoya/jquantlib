package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * Monte Carlo convergence study: for each sample count, the pseudo-random price
 * with its standard error and 95% confidence band, and the Sobol
 * low-discrepancy price, all relative to the analytic Black–Scholes reference.
 */
public record MonteCarloResponse(
        String summary,
        double analytic,
        List<McRow> rows) {

    public record McRow(
            long samples,
            double pseudoPrice,
            double pseudoStdErr,
            double pseudoCiLow,
            double pseudoCiHigh,
            double pseudoAbsError,
            double sobolPrice,
            double sobolAbsError) {
    }
}
