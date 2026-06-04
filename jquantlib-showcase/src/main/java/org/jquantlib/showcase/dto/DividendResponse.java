package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * Result of pricing an option that pays discrete cash dividends, via the
 * jquantlib-helpers finite-difference dividend engines, contrasted with the
 * otherwise-identical option that pays no dividends.
 */
public record DividendResponse(
        String summary,
        double npv,
        double npvNoDividends,
        Greeks greeks,
        List<DividendPayment> schedule,
        Sweep sweep) {

    public record Greeks(double delta, double gamma, double theta, double vega, double rho) {
    }

    public record DividendPayment(String date, double amount) {
    }

    public record Sweep(List<Double> spot, List<Double> withDividends, List<Double> noDividends) {
    }
}
