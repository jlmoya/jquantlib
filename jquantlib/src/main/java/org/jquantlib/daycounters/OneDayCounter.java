/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.daycounters;

import org.jquantlib.time.Date;

/**
 * 1/1 day count convention.
 * <p>
 * Faithful port of {@code ql/time/daycounters/one.hpp} from QuantLib v1.42.1
 * @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The day count is the sign between the two dates (+1 or -1) and the year
 * fraction equals the day count.
 *
 * @author Jose Moya
 */
public class OneDayCounter extends DayCounter {

    public OneDayCounter() {
        super.impl = new Impl();
    }

    private final class Impl extends DayCounter.Impl {

        @Override
        protected String name() {
            return "1/1";
        }

        @Override
        protected long dayCount(final Date dateStart, final Date dateEnd) {
            // The sign is all we need.
            return dateEnd.ge(dateStart) ? 1L : -1L;
        }

        @Override
        protected double yearFraction(final Date dateStart, final Date dateEnd,
                final Date refPeriodStart, final Date refPeriodEnd) {
            return dayCount(dateStart, dateEnd);
        }
    }
}
