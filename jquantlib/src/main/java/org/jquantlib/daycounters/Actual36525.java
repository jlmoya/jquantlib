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
 Copyright (C) 2022 Ignacio Anguita

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
 * Actual/365.25 day count convention. Port of v1.42.1
 * {@code ql/time/daycounters/actual36525.hpp}.
 */
public class Actual36525 extends DayCounter {

    public Actual36525() {
        this(false);
    }

    public Actual36525(final boolean includeLastDay) {
        super.impl = new Impl(includeLastDay);
    }

    private final class Impl extends DayCounter.Impl {

        private final boolean includeLastDay;

        Impl(final boolean includeLastDay) {
            this.includeLastDay = includeLastDay;
        }

        @Override
        public String name() /* @ReadOnly */ {
            return includeLastDay ? "Actual/365.25 (inc)" : "Actual/365.25";
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            return super.dayCount(d1, d2) + (includeLastDay ? 1L : 0L);
        }

        @Override
        public /*@Time*/ double yearFraction(final Date d1, final Date d2,
                final Date refPeriodStart, final Date refPeriodEnd) /* @ReadOnly */ {
            return dayCount(d1, d2) / 365.25;
        }
    }
}
