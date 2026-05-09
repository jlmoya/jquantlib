/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.experimental.coupons;

import org.jquantlib.cashflow.CappedFlooredCoupon;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Constants;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Capped and/or floored CMS-spread coupon.
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/coupons/cmsspreadcoupon.hpp} (the
 * {@code CappedFlooredCmsSpreadCoupon} class lines 71-103).
 *
 * @author Peter Caspers (C++ original)
 */
public class CappedFlooredCmsSpreadCoupon extends CappedFlooredCoupon {

    //
    // public constructors
    //

    /** Convenience: gearing=1, spread=0, no cap/floor, ref=now, dc=empty, isInArrears=false. */
    public CappedFlooredCmsSpreadCoupon(final Date paymentDate,
                                        final double nominal,
                                        final Date startDate,
                                        final Date endDate,
                                        final int fixingDays,
                                        final SwapSpreadIndex index) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index,
                1.0, 0.0, Constants.NULL_REAL, Constants.NULL_REAL,
                new Date(), new Date(), new DayCounter(), false);
    }

    /** Full ctor (matches C++ CappedFlooredCmsSpreadCoupon constructor). */
    public CappedFlooredCmsSpreadCoupon(final Date paymentDate,
                                        final double nominal,
                                        final Date startDate,
                                        final Date endDate,
                                        final int fixingDays,
                                        final SwapSpreadIndex index,
                                        final double gearing,
                                        final double spread,
                                        final double cap,
                                        final double floor,
                                        final Date refPeriodStart,
                                        final Date refPeriodEnd,
                                        final DayCounter dayCounter,
                                        final boolean isInArrears) {
        super(new CmsSpreadCoupon(paymentDate, nominal, startDate, endDate,
                fixingDays, index, gearing, spread,
                refPeriodStart, refPeriodEnd, dayCounter, isInArrears),
              cap, floor);
    }


    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<CappedFlooredCmsSpreadCoupon> v =
                (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
