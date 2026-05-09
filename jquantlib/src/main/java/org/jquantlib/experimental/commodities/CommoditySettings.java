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

import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Currency;

/**
 * Global repository for commodity-pricing run-time settings.
 * <p>
 * Java port of QuantLib v1.42.1 {@code commoditysettings.{hpp,cpp}}.
 * <p>
 * Defaults: USD currency, barrels UoM (mirrors C++).
 */
public final class CommoditySettings {

    private static final CommoditySettings INSTANCE = new CommoditySettings();

    private Currency currency_;
    private UnitOfMeasure unitOfMeasure_;

    private CommoditySettings() {
        this.currency_ = new America.USDCurrency();
        this.unitOfMeasure_ = new BarrelUnitOfMeasure();
    }

    public static CommoditySettings getInstance() {
        return INSTANCE;
    }

    public Currency currency() {
        return currency_;
    }

    public void setCurrency(final Currency currency) {
        this.currency_ = currency;
    }

    public UnitOfMeasure unitOfMeasure() {
        return unitOfMeasure_;
    }

    public void setUnitOfMeasure(final UnitOfMeasure unitOfMeasure) {
        this.unitOfMeasure_ = unitOfMeasure;
    }
}
