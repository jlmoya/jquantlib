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

/**
 * Energy futures contract.
 * <p>
 * Java port of QuantLib v1.42.1 {@code energyfuture.{hpp,cpp}}.
 * <p>
 * The C++ {@code performCalculations} computes the mark-to-market via
 * {@link CommodityIndex#fixing} (or {@link CommodityIndex#forwardPrice})
 * and a chain of UoM/FX conversions; this is deferred to Phase 4o.5.
 * The instrument constructor / accessors / {@link #isExpired()} are
 * provided here.
 */
public class EnergyFuture extends EnergyCommodity {

    private final int buySell_;
    private final Quantity quantity_;
    private final CommodityUnitCost tradePrice_;
    private final CommodityIndex index_;

    public EnergyFuture(final int buySell,
                        final Quantity quantity,
                        final CommodityUnitCost tradePrice,
                        final CommodityIndex index,
                        final CommodityType commodityType,
                        final SecondaryCosts secondaryCosts) {
        super(commodityType, secondaryCosts);
        this.buySell_ = buySell;
        this.quantity_ = quantity;
        this.tradePrice_ = tradePrice;
        this.index_ = index;
    }

    /** Mirrors the C++ default of {@code false}. */
    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public Quantity quantity() {
        return quantity_;
    }

    public int buySell() {
        return buySell_;
    }

    public CommodityUnitCost tradePrice() {
        return tradePrice_;
    }

    public CommodityIndex index() {
        return index_;
    }

    @Override
    protected void performCalculations() {
        // Pricing implementation deferred to Phase 4o.5 (TODO):
        // mirrors C++ EnergyFuture::performCalculations which combines
        // tradePrice + index fixing/forwardPrice via UoM/FX conversion
        // factors and lot quantity, then subtracts secondary cost amounts.
    }
}
