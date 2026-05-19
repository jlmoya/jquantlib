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
 * A {@link DateInterval} carrying a payment date and a quantity.
 * <p>
 * Java port of QuantLib v1.42.1 {@code pricingperiod.hpp}.
 */
public class PricingPeriod extends DateInterval {

    private final Date paymentDate_;
    private final Quantity quantity_;

    public PricingPeriod(final Date startDate, final Date endDate, final Date paymentDate, final Quantity quantity) {
        super(startDate, endDate);
        this.paymentDate_ = paymentDate;
        this.quantity_ = quantity;
    }

    public Date paymentDate() {
        return paymentDate_;
    }

    public Quantity quantity() {
        return quantity_;
    }
}
