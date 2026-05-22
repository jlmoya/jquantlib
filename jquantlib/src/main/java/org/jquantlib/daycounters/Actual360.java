/*
 Copyright (C) 2007 Richard Gomes

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
 Copyright (C) 2004 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.daycounters;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.time.Date;

/**
 * Actual/360 day count convention, also known as "Act/360", or "A/360".
 *
 * @author Richard Gomes
 * @author Srinivas Hasti
 * @category daycounters
 * @see <a href="http://en.wikipedia.org/wiki/Day_count_convention">Day count Convention</a>
 */
@QualityAssurance( quality = Quality.Q4_UNIT, version = Version.V097, reviewers = "Richard Gomes" )
public class Actual360 extends DayCounter {

    public Actual360() {
        super.impl = new Impl(false);
    }

    /**
     * Actual360 with optional inclusion of the last day in the day count — mirror of C++
     * {@code Actual360(bool includeLastDay)} (ql/time/daycounters/actual360.hpp:60-62).
     *
     * <p>When {@code includeLastDay = true} the {@code dayCount} formula
     * adds 1 to the actual difference and {@code yearFraction} divides {@code (daysBetween + 1) / 360.0}; this is the
     * ISDA-CDS-engine-compatible variant flagged as "Actual/360 (inc)".
     *
     * @since Phase 3d L0 A.2
     */
    public Actual360(final boolean includeLastDay) {
        super.impl = new Impl(includeLastDay);
    }

    //
    // private inner classes
    //

    final private class Impl extends DayCounter.Impl {

        private final boolean includeLastDay;

        Impl(final boolean includeLastDay) {
            this.includeLastDay = includeLastDay;
        }

        //
        // implements DayCounter
        //

        @Override
        public String name() /* @ReadOnly */ {
            return includeLastDay ? "Actual/360 (inc)" : "Actual/360";
        }

        @Override
        public long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            return super.dayCount(d1, d2) + (includeLastDay ? 1L : 0L);
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            // Phase 1.3 D5-D-intraday: mirror C++
            // ql/time/daycounters/actual360.hpp:55-58:
            //   return (daysBetween(d1, d2) + (includeLastDay?1:0)) / 360.0;
            // where daysBetween is (d2-d1) + d2.fractionOfDay() - d1.fractionOfDay().
            // For day-only Dates the fractional terms are 0 and the result is
            // identical to the prior integer-only branch.
            final long days = dayCount(dateStart, dateEnd);
            final double fractional = dateEnd.fractionOfDay() - dateStart.fractionOfDay();
            return ((double) days + fractional) / 360.0;
        }

    }

}
