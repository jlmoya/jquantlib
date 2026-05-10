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
import java.util.Map;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Money;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeUnit;

/**
 * Vanilla energy swap.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energyvanillaswap.{hpp,cpp}}.
 */
public class EnergyVanillaSwap extends EnergySwap {

    private final int payReceive_;
    private final Money fixedPrice_;
    private final UnitOfMeasure fixedPriceUnitOfMeasure_;
    private final CommodityIndex index_;
    private final Handle<YieldTermStructure> payLegTermStructure_;
    private final Handle<YieldTermStructure> receiveLegTermStructure_;
    private final Handle<YieldTermStructure> discountTermStructure_;

    public EnergyVanillaSwap(final boolean payer,
                             final Calendar calendar,
                             final Money fixedPrice,
                             final UnitOfMeasure fixedPriceUnitOfMeasure,
                             final CommodityIndex index,
                             final Currency payCurrency,
                             final Currency receiveCurrency,
                             final List<PricingPeriod> pricingPeriods,
                             final CommodityType commodityType,
                             final SecondaryCosts secondaryCosts,
                             final Handle<YieldTermStructure> payLegTermStructure,
                             final Handle<YieldTermStructure> receiveLegTermStructure,
                             final Handle<YieldTermStructure> discountTermStructure) {
        super(calendar, payCurrency, receiveCurrency, pricingPeriods,
                commodityType, secondaryCosts);
        // Match C++ v1.42.1 energyvanillaswap.cpp: payReceive_(payer ? 1 : 0).
        // Used as a sign predicate (payReceive_ > 0) inside performCalculations.
        this.payReceive_ = payer ? 1 : 0;
        this.fixedPrice_ = fixedPrice;
        this.fixedPriceUnitOfMeasure_ = fixedPriceUnitOfMeasure;
        this.index_ = index;
        this.payLegTermStructure_ = payLegTermStructure;
        this.receiveLegTermStructure_ = receiveLegTermStructure;
        this.discountTermStructure_ = discountTermStructure;
    }

    @Override
    public boolean isExpired() {
        // Mirrors the C++ override which short-circuits on the absence of
        // pricing periods first.
        if (pricingPeriods_.isEmpty()) {
            return true;
        }
        final Date paymentDate = pricingPeriods_.get(pricingPeriods_.size() - 1).paymentDate();
        return paymentDate.le(new Settings().evaluationDate());
    }

    public int payReceive() {
        return payReceive_;
    }

    public Money fixedPrice() {
        return fixedPrice_;
    }

    public UnitOfMeasure fixedPriceUnitOfMeasure() {
        return fixedPriceUnitOfMeasure_;
    }

    public CommodityIndex index() {
        return index_;
    }

    public Handle<YieldTermStructure> payLegTermStructure() {
        return payLegTermStructure_;
    }

    public Handle<YieldTermStructure> receiveLegTermStructure() {
        return receiveLegTermStructure_;
    }

    public Handle<YieldTermStructure> discountTermStructure() {
        return discountTermStructure_;
    }

    /**
     * Faithful port of C++ v1.42.1 energyvanillaswap.cpp performCalculations.
     * <p>
     * Iterates each pricing period day by day, populates dailyPositions_
     * with the fixed-vs-floating leg prices in (baseCurrency, baseUom),
     * accumulates payLegValue / receiveLegValue scaled by the per-day
     * average period quantity, then writes a CommodityCashFlow per
     * payment date (undiscounted + discounted in baseCurrency, plus a
     * payment-currency conversion). NPV is the sum of discounted leg
     * deltas across periods minus the secondary cost amounts.
     * <p>
     * The discount factors come from the three YieldTermStructure handles
     * (discount, payLeg, receiveLeg) only when paymentDate >= eval+2;
     * otherwise they default to 1.0. This mirrors the C++ short-circuit
     * for already-due cashflows.
     */
    @Override
    protected void performCalculations() {
        try {
            if (index_.empty()) {
                if (index_.forwardCurveEmpty()) {
                    throw new LibraryException("index [" + index_.name()
                            + "] does not have any quotes");
                }
                addPricingError(PricingError.Level.Warning,
                        "index [" + index_.name() + "] does not have any quotes; "
                                + "using forward prices from ["
                                + index_.forwardCurve().name() + "]");
            }

            this.NPV = 0.0;
            additionalResults_.clear();
            dailyPositions_.clear();
            paymentCashFlows_.clear();

            final Date evaluationDate = new Settings().evaluationDate();
            final Currency baseCurrency = CommoditySettings.getInstance().currency();
            final UnitOfMeasure baseUnitOfMeasure =
                    CommoditySettings.getInstance().unitOfMeasure();

            final Quantity firstPeriodQty = pricingPeriods_.get(0).quantity();
            final double quantityUomConversionFactor =
                    calculateUomConversionFactor(firstPeriodQty.commodityType(),
                            baseUnitOfMeasure, firstPeriodQty.unitOfMeasure());
            final double fixedPriceUomConversionFactor =
                    calculateUomConversionFactor(firstPeriodQty.commodityType(),
                            fixedPriceUnitOfMeasure_, baseUnitOfMeasure);
            final double indexUomConversionFactor =
                    calculateUomConversionFactor(index_.commodityType(),
                            index_.unitOfMeasure(), baseUnitOfMeasure);

            final double fixedPriceFxConversionFactor =
                    calculateFxConversionFactor(fixedPrice_.currency(),
                            baseCurrency, evaluationDate);
            final double indexPriceFxConversionFactor =
                    calculateFxConversionFactor(index_.currency(),
                            baseCurrency, evaluationDate);
            // Pay-leg pays in payCurrency_ when payer (payReceive_>0), else receiveCurrency_.
            final double payLegFxConversionFactor =
                    calculateFxConversionFactor(baseCurrency,
                            payReceive_ > 0 ? payCurrency_ : receiveCurrency_,
                            evaluationDate);
            final double receiveLegFxConversionFactor =
                    calculateFxConversionFactor(baseCurrency,
                            payReceive_ > 0 ? receiveCurrency_ : payCurrency_,
                            evaluationDate);

            final Date lastQuoteDate = index_.lastQuoteDate();
            if (lastQuoteDate.lt(evaluationDate.sub(1))) {
                addPricingError(PricingError.Level.Warning,
                        "index [" + index_.name() + "] has stale quote date "
                                + lastQuoteDate);
            }

            double totalQuantityAmount = 0.0;

            for (final PricingPeriod pricingPeriod : pricingPeriods_) {
                if (pricingPeriod.quantity().amount() == 0.0) {
                    throw new LibraryException("quantity is zero");
                }

                int periodDayCount = 0;
                final Date periodStartDate = calendar_.adjust(pricingPeriod.startDate());
                for (Date stepDate = periodStartDate;
                     stepDate.le(pricingPeriod.endDate());
                     stepDate = calendar_.advance(stepDate, 1, TimeUnit.Days)) {

                    final boolean unrealized = stepDate.gt(evaluationDate);
                    final double quoteValue;
                    if (stepDate.le(lastQuoteDate)) {
                        quoteValue = index_.fixing(stepDate, false);
                    } else {
                        quoteValue = index_.forwardPrice(stepDate);
                    }

                    if (quoteValue == 0.0) {
                        addPricingError(PricingError.Level.Warning,
                                "pay quote value for curve [" + index_.name()
                                        + "] is 0 for date " + stepDate);
                    }
                    if (Double.isNaN(quoteValue)) {
                        throw new LibraryException("curve [" + index_.name()
                                + "] missing value for pricing date " + stepDate);
                    }

                    final double fixedLegPriceValue =
                            fixedPrice_.value() * fixedPriceUomConversionFactor
                                    * fixedPriceFxConversionFactor;
                    final double floatingLegPriceValue =
                            quoteValue * indexUomConversionFactor
                                    * indexPriceFxConversionFactor;
                    final double payLegPriceValue = payReceive_ > 0
                            ? fixedLegPriceValue : floatingLegPriceValue;
                    final double receiveLegPriceValue = payReceive_ > 0
                            ? floatingLegPriceValue : fixedLegPriceValue;

                    dailyPositions_.put(stepDate,
                            new EnergyDailyPosition(stepDate, payLegPriceValue,
                                    receiveLegPriceValue, unrealized));
                    periodDayCount++;
                }

                final double periodQuantityAmount =
                        pricingPeriod.quantity().amount() * quantityUomConversionFactor;
                totalQuantityAmount += periodQuantityAmount;

                final double avgDailyQuantityAmount = periodDayCount == 0
                        ? 0.0 : periodQuantityAmount / periodDayCount;

                double payLegValue = 0.0;
                double receiveLegValue = 0.0;
                for (final Map.Entry<Date, EnergyDailyPosition> entry :
                        dailyPositions_.subMap(periodStartDate, true,
                                pricingPeriod.endDate(), true).entrySet()) {
                    final EnergyDailyPosition dp = entry.getValue();
                    dp.quantityAmount = avgDailyQuantityAmount;
                    dp.riskDelta = (-dp.payLegPrice + dp.receiveLegPrice)
                            * avgDailyQuantityAmount;
                    payLegValue += -dp.payLegPrice * avgDailyQuantityAmount;
                    receiveLegValue += dp.receiveLegPrice * avgDailyQuantityAmount;
                }

                double discountFactor = 1.0;
                double payLegDiscountFactor = 1.0;
                double receiveLegDiscountFactor = 1.0;
                if (pricingPeriod.paymentDate().ge(evaluationDate.add(2))) {
                    discountFactor = discountTermStructure_.currentLink()
                            .discount(pricingPeriod.paymentDate());
                    payLegDiscountFactor = payLegTermStructure_.currentLink()
                            .discount(pricingPeriod.paymentDate());
                    receiveLegDiscountFactor = receiveLegTermStructure_.currentLink()
                            .discount(pricingPeriod.paymentDate());
                }

                final double uDelta = receiveLegValue + payLegValue;
                final double dDelta = (receiveLegValue * receiveLegDiscountFactor)
                        + (payLegValue * payLegDiscountFactor);
                // C++: pmtFxFactor & pmtCurrency depend on (dDelta * payReceive_) > 0.
                // Note: pmtDiscountFactor uses dDelta>0 only (no payReceive_ factor).
                final double pmtFxConversionFactor = ((dDelta * payReceive_) > 0)
                        ? payLegFxConversionFactor : receiveLegFxConversionFactor;
                final Currency pmtCurrency = ((dDelta * payReceive_) > 0)
                        ? receiveCurrency_ : payCurrency_;
                final double pmtDiscountFactor = (dDelta > 0)
                        ? receiveLegDiscountFactor : payLegDiscountFactor;

                paymentCashFlows_.put(pricingPeriod.paymentDate(),
                        new CommodityCashFlow(pricingPeriod.paymentDate(),
                                new Money(baseCurrency, uDelta * discountFactor),
                                new Money(baseCurrency, uDelta),
                                new Money(pmtCurrency, dDelta * pmtFxConversionFactor),
                                new Money(pmtCurrency, uDelta * pmtFxConversionFactor),
                                discountFactor,
                                pmtDiscountFactor,
                                pricingPeriod.paymentDate().le(evaluationDate)));

                calculateSecondaryCostAmounts(firstPeriodQty.commodityType(),
                        totalQuantityAmount, evaluationDate);

                this.NPV += dDelta;
            }

            if (paymentCashFlows_.isEmpty()) {
                throw new LibraryException("no cashflows");
            }

            for (final Map.Entry<String, Money> entry : secondaryCostAmounts_.entrySet()) {
                this.NPV -= entry.getValue().value();
            }

            additionalResults_.put("dailyPositions", dailyPositions_);

        } catch (final LibraryException e) {
            addPricingError(PricingError.Level.Error, e.getMessage());
            throw e;
        } catch (final RuntimeException e) {
            addPricingError(PricingError.Level.Error, e.getMessage());
            throw e;
        }
    }
}
