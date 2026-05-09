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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;

/**
 * Energy swap.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energyswap.{hpp,cpp}}.
 * <p>
 * The pricing implementation ({@code performCalculations}) is delegated
 * to subclasses ({@link EnergyVanillaSwap}, {@link EnergyBasisSwap}); the
 * inherited {@link EnergyCommodity#calculateSecondaryCostAmounts} helper
 * remains available.
 */
public class EnergySwap extends EnergyCommodity {

    protected final Calendar calendar_;
    protected final Currency payCurrency_;
    protected final Currency receiveCurrency_;
    protected final List<PricingPeriod> pricingPeriods_;
    /** Map<Date, EnergyDailyPosition> populated by performCalculations(). */
    protected final TreeMap<Date, EnergyDailyPosition> dailyPositions_ = new TreeMap<>();
    /** Map<Date, CommodityCashFlow> populated by performCalculations(). */
    protected final TreeMap<Date, CommodityCashFlow> paymentCashFlows_ = new TreeMap<>();

    public EnergySwap(final Calendar calendar,
                      final Currency payCurrency,
                      final Currency receiveCurrency,
                      final List<PricingPeriod> pricingPeriods,
                      final CommodityType commodityType,
                      final SecondaryCosts secondaryCosts) {
        super(commodityType, secondaryCosts);
        this.calendar_ = calendar;
        this.payCurrency_ = payCurrency;
        this.receiveCurrency_ = receiveCurrency;
        this.pricingPeriods_ = new ArrayList<>(pricingPeriods);
    }

    @Override
    public boolean isExpired() {
        if (pricingPeriods_.isEmpty()) {
            return true;
        }
        final Date paymentDate = pricingPeriods_.get(pricingPeriods_.size() - 1).paymentDate();
        return paymentDate.le(new Settings().evaluationDate());
    }

    public Calendar calendar() {
        return calendar_;
    }

    public Currency payCurrency() {
        return payCurrency_;
    }

    public Currency receiveCurrency() {
        return receiveCurrency_;
    }

    public List<PricingPeriod> pricingPeriods() {
        return pricingPeriods_;
    }

    public Map<Date, EnergyDailyPosition> dailyPositions() {
        return dailyPositions_;
    }

    public Map<Date, CommodityCashFlow> paymentCashFlows() {
        return paymentCashFlows_;
    }

    /** Returns the commodity type from the first pricing period. */
    @Override
    public Quantity quantity() {
        if (pricingPeriods_.isEmpty()) {
            throw new LibraryException("no pricing periods");
        }
        double totalQuantityAmount = 0;
        for (final PricingPeriod pp : pricingPeriods_) {
            totalQuantityAmount += pp.quantity().amount();
        }
        return new Quantity(pricingPeriods_.get(0).quantity().commodityType(),
                            pricingPeriods_.get(0).quantity().unitOfMeasure(),
                            totalQuantityAmount);
    }

    @Override
    protected void performCalculations() {
        // Pricing implementation deferred to Phase 4o.5 (TODO):
        // mirrors C++ EnergyVanillaSwap/EnergyBasisSwap::performCalculations
        // which iterate pricing periods, fetch the index forward price /
        // basis, populate dailyPositions_ and paymentCashFlows_, and
        // accumulate NPV via discount factors from the YieldTermStructure
        // handles.
    }
}
