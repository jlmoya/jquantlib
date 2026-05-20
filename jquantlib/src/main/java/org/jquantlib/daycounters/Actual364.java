/*
 Copyright (C) 2025 JQuantLib Migration Project

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */

/*
 Copyright (C) 2014 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.daycounters;

import org.jquantlib.time.Date;

/**
 * Actual/364 day count convention. Port of v1.42.1
 * {@code ql/time/daycounters/actual364.hpp}.
 */
public class Actual364 extends DayCounter {

    public Actual364() {
        super.impl = new Impl();
    }

    private final class Impl extends DayCounter.Impl {

        @Override
        public String name() /* @ReadOnly */ {
            return "Actual/364";
        }

        @Override
        public /*@Time*/ double yearFraction(final Date d1, final Date d2,
                final Date refPeriodStart, final Date refPeriodEnd) /* @ReadOnly */ {
            return dayCount(d1, d2) / 364.0;
        }
    }
}
