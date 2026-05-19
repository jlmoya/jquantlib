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

import org.jquantlib.cashflow.FloatingRateCoupon;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * CMS-spread coupon.
 * <p>
 * Coupon paying {@latex$ g_1 R_1 + g_2 R_2 } where {@latex$ R_1, R_2 } are the swap-rate fixings of the two underlying
 * CMS indices (referenced via {@link SwapSpreadIndex}).
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/experimental/coupons/cmsspreadcoupon.hpp/cpp}.
 *
 * <p><b>Warning:</b> this class does not perform any date adjustment, i.e.,
 * the start and end date passed upon construction should be already rolled to a business day.
 *
 * @author Peter Caspers (C++ original)
 */
public class CmsSpreadCoupon extends FloatingRateCoupon {

    private final SwapSpreadIndex index_;

    //
    // public constructors
    //

    /** Convenience: gearing=1, spread=0, ref=now, dc=empty, isInArrears=false. */
    public CmsSpreadCoupon(final Date paymentDate, final double nominal, final Date startDate, final Date endDate,
            final int fixingDays, final SwapSpreadIndex index) {
        this(paymentDate, nominal, startDate, endDate, fixingDays, index, 1.0, 0.0, new Date(), new Date(),
                new DayCounter(), false);
    }

    /** Full ctor (matches C++ CmsSpreadCoupon constructor signature). */
    public CmsSpreadCoupon(final Date paymentDate, final double nominal, final Date startDate, final Date endDate,
            final int fixingDays, final SwapSpreadIndex index, final double gearing, final double spread,
            final Date refPeriodStart, final Date refPeriodEnd, final DayCounter dayCounter,
            final boolean isInArrears) {
        super(paymentDate, nominal, startDate, endDate, fixingDays, index, gearing, spread, refPeriodStart,
                refPeriodEnd, dayCounter, isInArrears);
        this.index_ = index;
    }

    //
    // public inspectors
    //

    public SwapSpreadIndex swapSpreadIndex() {
        return index_;
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< CmsSpreadCoupon > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
