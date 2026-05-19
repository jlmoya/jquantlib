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

import java.util.List;

/**
 * Multiple-reset pricer that averages sub-period fixings (simple convention).
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::AveragingMultipleResetsPricer}
 * in {@code ql/cashflows/multipleresetscoupon.cpp}:
 *
 * <pre>
 *   aggregate = sum_i  fixing_i * dt_i
 *   rate      = aggregate / accrualPeriod
 *   coupon    = gearing * rate + spread
 * </pre>
 *
 * <p>Phase 5d.5-MR.
 */
public class AveragingMultipleResetsPricer extends MultipleResetsPricer {

    @Override
    public double swapletRate() {
        // past or future fixing is managed in InterestRateIndex::fixing()
        final int nCount = subPeriodFixings_.size();
        final List< Double > subPeriodFractions = coupon_.dt();
        double aggregateFactor = 0.0;
        for ( int i = 0; i < nCount; i++ ) {
            aggregateFactor += subPeriodFixings_.get(i) * subPeriodFractions.get(i);
        }

        final double rate = aggregateFactor / coupon_.accrualPeriod();
        return coupon_.gearing() * rate + coupon_.spread();
    }
}
