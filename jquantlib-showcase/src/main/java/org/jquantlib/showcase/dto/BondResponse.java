package org.jquantlib.showcase.dto;

import java.util.List;

/**
 * Result of pricing a fixed-rate bond on a flat discount curve: present value,
 * clean/dirty price, accrued interest, yield, the projected cash flows, and a
 * price-vs-yield curve for charting the inverse price/yield relationship.
 */
public record BondResponse(
        String summary,
        double npv,
        double cleanPrice,
        double dirtyPrice,
        double accrued,
        double yieldPercent,
        String settlementDate,
        String maturityDate,
        List<Cashflow> cashflows,
        PriceYield priceYield) {

    public record Cashflow(String date, double amount) {
    }

    public record PriceYield(List<Double> yieldPercent, List<Double> cleanPrice) {
    }
}
