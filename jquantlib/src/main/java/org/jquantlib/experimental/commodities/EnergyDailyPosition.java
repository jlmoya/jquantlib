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

import org.jquantlib.time.Date;

/**
 * Daily position of an energy commodity instrument.
 * <p>
 * Java port of QuantLib v1.42.1 {@code EnergyDailyPosition} from
 * {@code energycommodity.hpp}.
 * <p>
 * Public mutable fields preserve the C++ struct semantics (used by engines
 * to populate positions per evaluation date).
 */
public class EnergyDailyPosition {

    public Date date;
    public double quantityAmount;
    public double payLegPrice;
    public double receiveLegPrice;
    public double riskDelta;
    public boolean unrealized;

    public EnergyDailyPosition() {
        this.date = new Date();
        this.quantityAmount = 0.0;
        this.payLegPrice = 0.0;
        this.receiveLegPrice = 0.0;
        this.riskDelta = 0.0;
        this.unrealized = false;
    }

    public EnergyDailyPosition(final Date date,
                               final double payLegPrice,
                               final double receiveLegPrice,
                               final boolean unrealized) {
        this.date = date;
        this.quantityAmount = 0.0;
        this.payLegPrice = payLegPrice;
        this.receiveLegPrice = receiveLegPrice;
        this.riskDelta = 0.0;
        this.unrealized = unrealized;
    }
}
