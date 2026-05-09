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
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Calendar;

/**
 * Energy basis swap.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energybasisswap.{hpp,cpp}}.
 * <p>
 * Like {@link EnergyVanillaSwap}, the {@code performCalculations} pricing
 * implementation is deferred to Phase 4o.5; the API is otherwise
 * complete.
 */
public class EnergyBasisSwap extends EnergySwap {

    private final CommodityIndex spreadIndex_;
    private final CommodityIndex payIndex_;
    private final CommodityIndex receiveIndex_;
    private final boolean spreadToPayLeg_;
    private final CommodityUnitCost basis_;
    private final Handle<YieldTermStructure> payLegTermStructure_;
    private final Handle<YieldTermStructure> receiveLegTermStructure_;
    private final Handle<YieldTermStructure> discountTermStructure_;

    public EnergyBasisSwap(final Calendar calendar,
                           final CommodityIndex spreadIndex,
                           final CommodityIndex payIndex,
                           final CommodityIndex receiveIndex,
                           final boolean spreadToPayLeg,
                           final Currency payCurrency,
                           final Currency receiveCurrency,
                           final List<PricingPeriod> pricingPeriods,
                           final CommodityUnitCost basis,
                           final CommodityType commodityType,
                           final SecondaryCosts secondaryCosts,
                           final Handle<YieldTermStructure> payLegTermStructure,
                           final Handle<YieldTermStructure> receiveLegTermStructure,
                           final Handle<YieldTermStructure> discountTermStructure) {
        super(calendar, payCurrency, receiveCurrency, pricingPeriods,
                commodityType, secondaryCosts);
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
