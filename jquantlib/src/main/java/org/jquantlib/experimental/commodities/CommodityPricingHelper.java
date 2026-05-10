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

import java.util.List;

import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.ExchangeRate;
import org.jquantlib.currencies.ExchangeRateManager;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Static commodity-pricing helpers (FX/UoM conversion and unit cost).
 * <p>
 * Java port of QuantLib v1.42.1 {@code commoditypricinghelpers.{hpp,cpp}}.
 */
public final class CommodityPricingHelper {

    private CommodityPricingHelper() {
        // utility
    }

    /**
     * UoM conversion factor for a commodity. Returns 1 if the units match.
     */
    public static double calculateUomConversionFactor(final CommodityType commodityType,
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

    /**
     * FX conversion factor between two currencies as of an evaluation date.
     * Returns 1 if currencies match.
     */
    public static double calculateFxConversionFactor(final Currency fromCurrency,
                                                     final Currency toCurrency,
                                                     final Date evaluationDate) {
        if (!fromCurrency.equals(toCurrency)) {
            final ExchangeRate exchRate = ExchangeRateManager.getInstance()
                    .lookup(fromCurrency, toCurrency, evaluationDate, ExchangeRate.Type.Direct);
            if (!fromCurrency.equals(exchRate.source())) {
                return 1.0 / exchRate.rate();
            }
            return exchRate.rate();
        }
        return 1.0;
    }

    /**
     * Compute a unit cost converted into the base currency and base UoM.
     */
    public static double calculateUnitCost(final CommodityType commodityType,
                                           final CommodityUnitCost unitCost,
                                           final Currency baseCurrency,
                                           final UnitOfMeasure baseUnitOfMeasure,
                                           final Date evaluationDate) {
        if (unitCost.amount().value() != 0) {
            final double uomFactor = calculateUomConversionFactor(
                    commodityType, unitCost.unitOfMeasure(), baseUnitOfMeasure);
            final double fxFactor = calculateFxConversionFactor(
                    unitCost.amount().currency(), baseCurrency, evaluationDate);
            return unitCost.amount().value() * uomFactor * fxFactor;
        }
        return 0.0;
    }

    /**
     * Build {@link PricingPeriod} entries between {@code startDate} and
     * {@code endDate}, partitioning by {@code deliverySchedule}, scaling
     * the period quantity according to {@code qtyPeriodicity}, and
     * computing payment dates via {@code paymentTerm}.
     * <p>
     * Mirrors C++ v1.42.1 {@code CommodityPricingHelper::createPricingPeriods}
     * which currently supports {@code Daily} and {@code Monthly} delivery
     * schedules. Other delivery schedules will throw a
     * {@link LibraryException} (matching {@code QL_FAIL} in C++).
     * <p>
     * The resulting {@link PricingPeriod} instances are appended to
     * {@code pricingPeriods}.
     */
    public static void createPricingPeriods(final Date startDate,
                                            final Date endDate,
                                            final Quantity quantity,
                                            final EnergyCommodity.DeliverySchedule deliverySchedule,
                                            final EnergyCommodity.QuantityPeriodicity qtyPeriodicity,
                                            final PaymentTerm paymentTerm,
                                            final List<PricingPeriod> pricingPeriods) {
        if (deliverySchedule == EnergyCommodity.DeliverySchedule.Monthly) {
            final Quantity periodQuantity;
            if (qtyPeriodicity == EnergyCommodity.QuantityPeriodicity.PerMonth) {
                periodQuantity = quantity;
            } else {
                throw new LibraryException(
                        "Invalid period quantity/pricing period combination.");
            }

            for (Date periodStartDate = startDate; periodStartDate.lt(endDate); ) {
                final Date periodEndDate =
                        periodStartDate.add(new Period(1, TimeUnit.Months)).sub(1);
                final Date paymentDate = paymentTerm.getPaymentDate(periodEndDate);
                pricingPeriods.add(new PricingPeriod(
                        periodStartDate, periodEndDate, paymentDate, periodQuantity));
                periodStartDate = periodEndDate.add(1);
            }
        } else if (deliverySchedule == EnergyCommodity.DeliverySchedule.Daily) {
            if (qtyPeriodicity != EnergyCommodity.QuantityPeriodicity.PerDay) {
                throw new LibraryException(
                        "Invalid period quantity/pricing period combination.");
            }

            for (Date periodStartDate = startDate; periodStartDate.lt(endDate); ) {
                final Date periodEndDate =
                        periodStartDate.add(new Period(1, TimeUnit.Months)).sub(1);
                final long days = periodEndDate.sub(periodStartDate);
                final Quantity periodQuantity = new Quantity(
                        quantity.commodityType(),
                        quantity.unitOfMeasure(),
                        quantity.amount() * days);
                final Date paymentDate = paymentTerm.getPaymentDate(periodEndDate);
                pricingPeriods.add(new PricingPeriod(
                        periodStartDate, periodEndDate, paymentDate, periodQuantity));
                periodStartDate = periodEndDate.add(1);
            }
        }
    }
}
