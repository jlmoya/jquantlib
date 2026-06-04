package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * A bootstrapped/interpolated yield term structure sampled across its horizon:
 * discount factors, continuously-compounded zero rates, and instantaneous-ish
 * forward rates, plus the input pillar points.
 */
public record CurveResponse(
        String summary,
        List<TenorPoint> inputs,
        List<Double> timeYears,
        List<Double> discountFactor,
        List<Double> zeroRatePercent,
        List<Double> forwardRatePercent) {

    public record TenorPoint(double years, double ratePercent) {
    }
}
