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

import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Money;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Vanilla energy swap.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energyvanillaswap.{hpp,cpp}}.
 * <p>
 * The {@code performCalculations} pricing implementation is deferred to
 * Phase 4o.5; constructor / accessors / {@link #isExpired()} are
 * provided so the API is callable today.
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
        // C++ stores payReceive_ as an Integer (-1 for payer, +1 for receiver
        // in the standard convention).  Mirror that here.
        this.payReceive_ = payer ? -1 : 1;
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
}
