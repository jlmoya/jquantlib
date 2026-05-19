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

import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Money;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;

import java.util.List;
import java.util.Map;

/**
 * Energy basis swap.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energybasisswap.{hpp,cpp}}.
 */
public class EnergyBasisSwap extends EnergySwap {

    private final CommodityIndex spreadIndex_;
    private final CommodityIndex payIndex_;
    private final CommodityIndex receiveIndex_;
    private final boolean spreadToPayLeg_;
    private final CommodityUnitCost basis_;
    private final Handle< YieldTermStructure > payLegTermStructure_;
    private final Handle< YieldTermStructure > receiveLegTermStructure_;
    private final Handle< YieldTermStructure > discountTermStructure_;

    public EnergyBasisSwap(final Calendar calendar, final CommodityIndex spreadIndex, final CommodityIndex payIndex,
            final CommodityIndex receiveIndex, final boolean spreadToPayLeg, final Currency payCurrency,
            final Currency receiveCurrency, final List< PricingPeriod > pricingPeriods, final CommodityUnitCost basis,
            final CommodityType commodityType, final SecondaryCosts secondaryCosts,
            final Handle< YieldTermStructure > payLegTermStructure,
            final Handle< YieldTermStructure > receiveLegTermStructure,
            final Handle< YieldTermStructure > discountTermStructure) {
        super(calendar, payCurrency, receiveCurrency, pricingPeriods, commodityType, secondaryCosts);
        this.spreadIndex_ = spreadIndex;
        this.payIndex_ = payIndex;
        this.receiveIndex_ = receiveIndex;
        this.spreadToPayLeg_ = spreadToPayLeg;
        this.basis_ = basis;
        this.payLegTermStructure_ = payLegTermStructure;
        this.receiveLegTermStructure_ = receiveLegTermStructure;
        this.discountTermStructure_ = discountTermStructure;
    }

    public CommodityIndex spreadIndex() {
        return spreadIndex_;
    }

    public CommodityIndex payIndex() {
        return payIndex_;
    }

    public CommodityIndex receiveIndex() {
        return receiveIndex_;
    }

    public boolean spreadToPayLeg() {
        return spreadToPayLeg_;
    }

    public CommodityUnitCost basis() {
        return basis_;
    }

    public Handle< YieldTermStructure > payLegTermStructure() {
        return payLegTermStructure_;
    }

    public Handle< YieldTermStructure > receiveLegTermStructure() {
        return receiveLegTermStructure_;
    }

    public Handle< YieldTermStructure > discountTermStructure() {
        return discountTermStructure_;
    }

    /**
     * Faithful port of C++ v1.42.1 energybasisswap.cpp performCalculations.
     * <p>
     * Same per-day stepping pattern as {@link EnergyVanillaSwap} but differences:
     * <ul>
     *   <li>Two index quotes per step (pay leg = payIndex.fixing,
     *       receive leg = receiveIndex.fixing) instead of fixed vs floating.</li>
     *   <li>A {@code basis} CommodityUnitCost added to either the pay or
     *       receive leg according to {@code spreadToPayLeg}.</li>
     *   <li>The pay/receive currency selection in the cashflow uses the
     *       sign of {@code dDelta} alone (not multiplied by payReceive).</li>
     * </ul>
     */
    @Override
    protected void performCalculations() {
        try {
            checkIndexQuotes(payIndex_);
            checkIndexQuotes(receiveIndex_);

            this.NPV = 0.0;
            additionalResults_.clear();
            dailyPositions_.clear();
            paymentCashFlows_.clear();

            final Date evaluationDate = new Settings().evaluationDate();
            final Currency baseCurrency = CommoditySettings.getInstance().currency();
            final UnitOfMeasure baseUnitOfMeasure = CommoditySettings.getInstance().unitOfMeasure();

            final Quantity firstPeriodQty = pricingPeriods_.get(0).quantity();
            final double quantityUomConversionFactor = calculateUomConversionFactor(firstPeriodQty.commodityType(),
                    baseUnitOfMeasure, firstPeriodQty.unitOfMeasure());
            final double payIndexUomConversionFactor = calculateUomConversionFactor(payIndex_.commodityType(),
                    payIndex_.unitOfMeasure(), baseUnitOfMeasure);
            final double receiveIndexUomConversionFactor = calculateUomConversionFactor(receiveIndex_.commodityType(),
                    receiveIndex_.unitOfMeasure(), baseUnitOfMeasure);

            final double payIndexFxConversionFactor = calculateFxConversionFactor(payIndex_.currency(), baseCurrency,
                    evaluationDate);
            final double receiveIndexFxConversionFactor = calculateFxConversionFactor(receiveIndex_.currency(),
                    baseCurrency, evaluationDate);
            final double payLegFxConversionFactor = calculateFxConversionFactor(baseCurrency, payCurrency_,
                    evaluationDate);
            final double receiveLegFxConversionFactor = calculateFxConversionFactor(baseCurrency, receiveCurrency_,
                    evaluationDate);

            final double basisUomConversionFactor = calculateUomConversionFactor(firstPeriodQty.commodityType(),
                    basis_.unitOfMeasure(), baseUnitOfMeasure);
            final double basisFxConversionFactor = calculateFxConversionFactor(baseCurrency, basis_.amount().currency(),
                    evaluationDate);

            final double basisValue = basis_.amount().value() * basisUomConversionFactor * basisFxConversionFactor;

            final Date lastPayQuoteDate = payIndex_.lastQuoteDate();
            final Date lastReceiveQuoteDate = receiveIndex_.lastQuoteDate();
            if ( lastPayQuoteDate.lt(evaluationDate.sub(1)) ) {
                addPricingError(PricingError.Level.Warning,
                        "index [" + payIndex_.name() + "] has stale quote date " + lastPayQuoteDate);
            }
            if ( lastReceiveQuoteDate.lt(evaluationDate.sub(1)) ) {
                addPricingError(PricingError.Level.Warning,
                        "index [" + receiveIndex_.name() + "] has stale quote date " + lastReceiveQuoteDate);
            }
            final Date lastQuoteDate = lastPayQuoteDate.le(lastReceiveQuoteDate)
                    ? lastPayQuoteDate
                    : lastReceiveQuoteDate;

            double totalQuantityAmount = 0.0;

            for ( final PricingPeriod pricingPeriod : pricingPeriods_ ) {
                int periodDayCount = 0;
                final Date periodStartDate = calendar_.adjust(pricingPeriod.startDate());
                for ( Date stepDate = periodStartDate; stepDate.le(
                        pricingPeriod.endDate()); stepDate = calendar_.advance(stepDate, 1, TimeUnit.Days) ) {

                    final boolean unrealized = stepDate.gt(evaluationDate);
                    final double payQuoteValue;
                    final double receiveQuoteValue;
                    if ( stepDate.le(lastQuoteDate) ) {
                        payQuoteValue = payIndex_.fixing(stepDate, false);
                        receiveQuoteValue = receiveIndex_.fixing(stepDate, false);
                    } else {
                        payQuoteValue = payIndex_.forwardPrice(stepDate);
                        receiveQuoteValue = receiveIndex_.forwardPrice(stepDate);
                    }
                    if ( payQuoteValue == 0.0 ) {
                        addPricingError(PricingError.Level.Warning,
                                "pay quote value for curve [" + payIndex_.name() + "] is 0 for date " + stepDate);
                    }
                    if ( receiveQuoteValue == 0.0 ) {
                        addPricingError(PricingError.Level.Warning,
                                "receive quote value for curve [" + receiveIndex_.name() + "] is 0 for date "
                                        + stepDate);
                    }
                    if ( Double.isNaN(payQuoteValue) ) {
                        throw new LibraryException(
                                "curve [" + payIndex_.name() + "] missing value for pricing date " + stepDate);
                    }
                    if ( Double.isNaN(receiveQuoteValue) ) {
                        throw new LibraryException(
                                "curve [" + receiveIndex_.name() + "] missing value for pricing date " + stepDate);
                    }

                    double payLegPriceValue = payQuoteValue * payIndexUomConversionFactor * payIndexFxConversionFactor;
                    double receiveLegPriceValue =
                            receiveQuoteValue * receiveIndexUomConversionFactor * receiveIndexFxConversionFactor;
                    if ( spreadToPayLeg_ ) {
                        payLegPriceValue += basisValue;
                    } else {
                        receiveLegPriceValue += basisValue;
                    }

                    dailyPositions_.put(stepDate,
                            new EnergyDailyPosition(stepDate, payLegPriceValue, receiveLegPriceValue, unrealized));
                    periodDayCount++;
                }

                final double periodQuantityAmount = pricingPeriod.quantity().amount() * quantityUomConversionFactor;
                totalQuantityAmount += periodQuantityAmount;

                final double avgDailyQuantityAmount = periodDayCount == 0 ? 0.0 : periodQuantityAmount / periodDayCount;

                double payLegValue = 0.0;
                double receiveLegValue = 0.0;
                for ( final Map.Entry< Date, EnergyDailyPosition > entry : dailyPositions_.subMap(periodStartDate, true,
                        pricingPeriod.endDate(), true).entrySet() ) {
                    final EnergyDailyPosition dp = entry.getValue();
                    dp.quantityAmount = avgDailyQuantityAmount;
                    dp.riskDelta = (-dp.payLegPrice + dp.receiveLegPrice) * avgDailyQuantityAmount;
                    payLegValue += -dp.payLegPrice * avgDailyQuantityAmount;
                    receiveLegValue += dp.receiveLegPrice * avgDailyQuantityAmount;
                }

                double discountFactor = 1.0;
                double payLegDiscountFactor = 1.0;
                double receiveLegDiscountFactor = 1.0;
                if ( pricingPeriod.paymentDate().ge(evaluationDate.add(2)) ) {
                    discountFactor = discountTermStructure_.currentLink().discount(pricingPeriod.paymentDate());
                    payLegDiscountFactor = payLegTermStructure_.currentLink().discount(pricingPeriod.paymentDate());
                    receiveLegDiscountFactor = receiveLegTermStructure_.currentLink()
                            .discount(pricingPeriod.paymentDate());
                }

                final double uDelta = receiveLegValue + payLegValue;
                final double dDelta =
                        (receiveLegValue * receiveLegDiscountFactor) + (payLegValue * payLegDiscountFactor);
                // C++ basis swap uses dDelta sign alone (not dDelta * payReceive).
                final double pmtFxConversionFactor = (dDelta > 0)
                        ? payLegFxConversionFactor
                        : receiveLegFxConversionFactor;
                final Currency pmtCurrency = (dDelta > 0) ? receiveCurrency_ : payCurrency_;
                final double pmtDiscountFactor = (dDelta > 0) ? receiveLegDiscountFactor : payLegDiscountFactor;

                paymentCashFlows_.put(pricingPeriod.paymentDate(), new CommodityCashFlow(pricingPeriod.paymentDate(),
                        new Money(baseCurrency, uDelta * discountFactor), new Money(baseCurrency, uDelta),
                        new Money(pmtCurrency, dDelta * pmtFxConversionFactor),
                        new Money(pmtCurrency, uDelta * pmtFxConversionFactor), discountFactor, pmtDiscountFactor,
                        pricingPeriod.paymentDate().le(evaluationDate)));

                calculateSecondaryCostAmounts(firstPeriodQty.commodityType(), totalQuantityAmount, evaluationDate);

                this.NPV += dDelta;
            }

            if ( paymentCashFlows_.isEmpty() ) {
                throw new LibraryException("no cashflows");
            }

            for ( final Map.Entry< String, Money > entry : secondaryCostAmounts_.entrySet() ) {
                this.NPV -= entry.getValue().value();
            }

            additionalResults_.put("dailyPositions", dailyPositions_);

        } catch ( final LibraryException e ) {
            addPricingError(PricingError.Level.Error, e.getMessage());
            throw e;
        } catch ( final RuntimeException e ) {
            addPricingError(PricingError.Level.Error, e.getMessage());
            throw e;
        }
    }

    private void checkIndexQuotes(final CommodityIndex idx) {
        if ( idx.empty() ) {
            if ( idx.forwardCurveEmpty() ) {
                throw new LibraryException("index [" + idx.name() + "] does not have any quotes or forward prices");
            }
            addPricingError(PricingError.Level.Warning,
                    "index [" + idx.name() + "] does not have any quotes; " + "using forward prices from ["
                            + idx.forwardCurve().name() + "]");
        }
    }
}
