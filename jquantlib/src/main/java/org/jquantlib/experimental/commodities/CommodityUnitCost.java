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

import org.jquantlib.currencies.Money;

/**
 * Money amount per unit of measure.
 * <p>
 * Java port of QuantLib v1.42.1 {@code commodityunitcost.{hpp,cpp}}.
 */
public class CommodityUnitCost {

    private final Money amount_;
    private final UnitOfMeasure unitOfMeasure_;

    public CommodityUnitCost() {
        this.amount_ = new Money();
        this.unitOfMeasure_ = new UnitOfMeasure();
    }

    public CommodityUnitCost(final Money amount, final UnitOfMeasure unitOfMeasure) {
        this.amount_ = amount;
        this.unitOfMeasure_ = unitOfMeasure;
    }

    public Money amount() {
        return amount_;
    }

    public UnitOfMeasure unitOfMeasure() {
        return unitOfMeasure_;
    }

    @Override
    public String toString() {
        return amount_.value() + " " + amount_.currency().code() + "/" + unitOfMeasure_.code();
    }
}
