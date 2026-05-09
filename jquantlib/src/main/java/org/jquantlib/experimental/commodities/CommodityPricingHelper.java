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

import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.ExchangeRate;
import org.jquantlib.currencies.ExchangeRateManager;
import org.jquantlib.time.Date;

/**
 * Static commodity-pricing helpers (FX/UoM conversion and unit cost).
 * <p>
 * Java port of QuantLib v1.42.1 {@code commoditypricinghelpers.{hpp,cpp}}.
 * <p>
 * Note: {@code createPricingPeriods} from the C++ helper is omitted from
 * this commit; it depends on a full Schedule/Calendar arithmetic model
 * that is being addressed in a follow-up commit.
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
}
