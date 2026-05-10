/*
 Copyright (C) 2026 JQuantLib

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 J. Erik Radmall
*/

package org.jquantlib.experimental.commodities;

import java.util.HashMap;
import java.util.Map;

import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.ExchangeRate;
import org.jquantlib.currencies.ExchangeRateManager;
import org.jquantlib.currencies.Money;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;

/**
 * Abstract energy-commodity instrument base.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energycommodity.{hpp,cpp}}.
 * <p>
 * Subclasses implement {@link #quantity()} and the standard
 * {@code Instrument} hooks (NPV calculation via a pricing engine).
 * The {@code arguments}/{@code results}/{@code engine} nested classes from
 * the C++ implementation are deferred to a follow-up commit.
 */
public abstract class EnergyCommodity extends Commodity {

    public enum DeliverySchedule {
        Constant,
        Window,
        Hourly,
        Daily,
        Weekly,
        Monthly,
        Quarterly,
        Yearly
    }

    public enum QuantityPeriodicity {
        Absolute,
        PerHour,
        PerDay,
        PerWeek,
        PerMonth,
        PerQuarter,
        PerYear
    }

    public enum PaymentSchedule {
        WindowSettlement,
        MonthlySettlement,
        QuarterlySettlement,
        YearlySettlement
    }

    protected final CommodityType commodityType_;

    /**
     * Free-form pricing-engine results, populated by {@link #performCalculations()}.
     * <p>
     * Mirrors the C++ {@code Instrument::additionalResults_} field, which is a
     * protected member of the C++ {@code Instrument} base. Java's
     * {@code Instrument} only carries an additionalResults map on
     * {@code Instrument.ResultsImpl}; energy instruments compute everything
     * themselves (no external engine), so we keep a per-instrument map here
     * (same pattern as {@code FloatFloatSwaption}).
     */
    protected final Map<String, Object> additionalResults_ = new HashMap<>();

    protected EnergyCommodity(final CommodityType commodityType,
                              final SecondaryCosts secondaryCosts) {
        super(secondaryCosts);
        this.commodityType_ = commodityType;
    }

    /** Read-only view of the additional results map. */
    public final Map<String, Object> additionalResults() {
        return additionalResults_;
    }

    /** Look up a single additional result by key (returns null if absent). */
    public final Object additionalResult(final String key) {
        return additionalResults_.get(key);
    }

    public abstract Quantity quantity();

    public final CommodityType commodityType() {
        return commodityType_;
    }

    // ---- helpers (mirror C++ statics) ----

    protected static double calculateUomConversionFactor(final CommodityType commodityType,
                                                         final UnitOfMeasure fromUnitOfMeasure,
                                                         final UnitOfMeasure toUnitOfMeasure) {
        if (!toUnitOfMeasure.equals(fromUnitOfMeasure)) {
            final UnitOfMeasureConversion conv =
                    UnitOfMeasureConversionManager.getInstance()
                            .lookup(commodityType, fromUnitOfMeasure, toUnitOfMeasure);
            return conv.conversionFactor();
        }
        return 1.0;
    }

    protected static double calculateFxConversionFactor(final Currency fromCurrency,
                                                        final Currency toCurrency,
                                                        final Date evaluationDate) {
        if (!fromCurrency.equals(toCurrency)) {
            final ExchangeRate exchRate = ExchangeRateManager.getInstance()
                    .lookup(fromCurrency, toCurrency, evaluationDate);
            // C++ checks fromCurrency == exchRate.target(); if so, invert.
            if (fromCurrency.equals(exchRate.target())) {
                return 1.0 / exchRate.rate();
            }
            return exchRate.rate();
        }
        return 1.0;
    }

    protected double calculateUnitCost(final CommodityType commodityType,
                                       final CommodityUnitCost unitCost,
                                       final Date evaluationDate) {
        if (unitCost.amount().value() != 0) {
            final Currency baseCurrency = CommoditySettings.getInstance().currency();
            final UnitOfMeasure baseUnitOfMeasure = CommoditySettings.getInstance().unitOfMeasure();
            final double uomFactor = calculateUomConversionFactor(
                    commodityType, unitCost.unitOfMeasure(), baseUnitOfMeasure);
            final double fxFactor = calculateFxConversionFactor(
                    unitCost.amount().currency(), baseCurrency, evaluationDate);
            return unitCost.amount().value() * uomFactor * fxFactor;
        }
        return 0.0;
    }

    /**
     * Mirror of C++ {@code calculateSecondaryCostAmounts}: classify each
     * entry in {@link #secondaryCosts_} as either {@link CommodityUnitCost}
     * or {@link Money} and convert into base currency.
     */
    protected void calculateSecondaryCostAmounts(final CommodityType commodityType,
                                                 final double totalQuantityValue,
                                                 final Date evaluationDate) {
        secondaryCostAmounts_.clear();
        if (secondaryCosts_ == null) {
            return;
        }
        final Currency baseCurrency = CommoditySettings.getInstance().currency();
        try {
            for (final Map.Entry<String, Object> entry : secondaryCosts_.entrySet()) {
                final Object value = entry.getValue();
                if (value instanceof CommodityUnitCost) {
                    final double v = calculateUnitCost(commodityType, (CommodityUnitCost) value,
                            evaluationDate) * totalQuantityValue;
                    secondaryCostAmounts_.put(entry.getKey(), new Money(baseCurrency, v));
                } else if (value instanceof Money) {
                    final Money amount = (Money) value;
                    final double fxFactor = calculateFxConversionFactor(
                            amount.currency(), baseCurrency, evaluationDate);
                    secondaryCostAmounts_.put(entry.getKey(),
                            new Money(baseCurrency, amount.value() * fxFactor));
                }
            }
        } catch (final RuntimeException e) {
            throw new LibraryException("error calculating secondary costs: " + e.getMessage(), e);
        }
    }
}
