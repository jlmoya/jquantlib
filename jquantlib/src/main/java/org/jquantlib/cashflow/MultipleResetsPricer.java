/*
 Copyright (C) 2026 JQuantLib team

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
 Copyright (C) 2008 Toyin Akin
 Copyright (C) 2021 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.cashflow;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;

/**
 * Base pricer for {@link MultipleResetsCoupon}.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::MultipleResetsPricer} in
 * {@code ql/cashflows/multipleresetscoupon.{hpp,cpp}}.
 *
 * <p>Phase 5d.5-MR.
 */
public abstract class MultipleResetsPricer extends FloatingRateCouponPricer {

    protected MultipleResetsCoupon coupon_;
    protected List<Double> subPeriodFixings_;

    @Override
    public void initialize(final FloatingRateCoupon coupon) {
        QL.require(coupon instanceof MultipleResetsCoupon, "sub-periods coupon required");
        this.coupon_ = (MultipleResetsCoupon) coupon;

        QL.require(coupon_.index() instanceof IborIndex, "IborIndex required");
        final IborIndex index = (IborIndex) coupon_.index();

        QL.require(coupon_.accrualPeriod() != 0.0, "null accrual period");

        final List<Date> fixingDates = coupon_.fixingDates();
        final int n = fixingDates.size();
        subPeriodFixings_ = new ArrayList<Double>(n);
        for (int i = 0; i < n; i++) {
            subPeriodFixings_.add(index.fixing(fixingDates.get(i)) + coupon_.rateSpread());
        }
    }

    @Override
    public double swapletPrice() {
        throw new LibraryException("MultipleResetsPricer::swapletPrice not implemented");
    }

    @Override
    public double capletPrice(final double effectiveCap) {
        throw new LibraryException("MultipleResetsPricer::capletPrice not implemented");
    }

    @Override
    public double capletRate(final double effectiveCap) {
        throw new LibraryException("MultipleResetsPricer::capletRate not implemented");
    }

    @Override
    public double floorletPrice(final double effectiveFloor) {
        throw new LibraryException("MultipleResetsPricer::floorletPrice not implemented");
    }

    @Override
    public double floorletRate(final double effectiveFloor) {
        throw new LibraryException("MultipleResetsPricer::floorletRate not implemented");
    }
}
