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

import org.jquantlib.QL;
import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;

/**
 * "Actual/365 (Fixed)" day count convention, also know as "Act/365 (Fixed)", "A/365 (Fixed)", or "A/365F".
 *
 * @author Srinivas Hasti
 * @author Richard Gomes
 * @note According to ISDA, "Actual/365" (without "Fixed") is an alias for "Actual/Actual (ISDA)"DayCounter (see
 * ActualActual.)  If Actual/365 is not explicitly specified as fixed in an instrument specification, you might want to
 * double-check its meaning.
 */
@QualityAssurance( quality = Quality.Q4_UNIT, version = Version.V097, reviewers = "Richard Gomes" )
public class Actual365Fixed extends DayCounter {

    /**
     * Convention enum aligned with v1.42.1 ql/time/daycounters/actual365fixed.hpp.
     */
    public enum Convention {
        Standard, Canadian, NoLeap
    }

    public Actual365Fixed() {
        this(Convention.Standard);
    }

    public Actual365Fixed(final Convention c) {
        switch (c) {
        case Standard:
            super.impl = new Impl();
            break;
        case Canadian:
            super.impl = new CA_Impl();
            break;
        case NoLeap:
            super.impl = new NL_Impl();
            break;
        default:
            throw new LibraryException("unknown Actual/365 (Fixed) convention");
        }
    }

    //
    // private inner classes
    //

    final private class Impl extends DayCounter.Impl {

        //
        // implements DayCounter
        //

        @Override
        public String name() /* @ReadOnly */ {
            // Phase 3d L1 — align to C++ ql/time/daycounters/actual365fixed.hpp
            // ("Actual/365 (Fixed)"); was lowercase "(fixed)" historically.
            return "Actual/365 (Fixed)";
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            // Phase 5e.5b-CFC-d-314: mirror C++
            // ql/time/daycounters/actual365fixed.hpp:55-58 verbatim:
            //   return daysBetween(d1, d2) / 365.0;
            // where the free function daysBetween(d1, d2) is
            // (d2-d1) + d2.fractionOfDay() - d1.fractionOfDay()
            // (ql/time/date.cpp:719-722). For day-only Dates the fraction
            // terms are both 0 and the result is identical to the prior
            // integer-only branch; for intraday Dates this preserves the
            // sub-day component as required by C++ test-suite
            // testFdmHestonIntradayPricing.
            final long days = dayCount(dateStart, dateEnd);
            final double fractional = dateEnd.fractionOfDay() - dateStart.fractionOfDay();
            return ((double) days + fractional) / 365.0;
        }

    }

    /**
     * Actual/365 (Fixed) Canadian Bond — port of v1.42.1
     * ql/time/daycounters/actual365fixed.cpp:40-67.
     *
     * <p>Requires non-null refPeriodStart/refPeriodEnd. Backbone:
     *   months   = round(12 * dcc/365)
     *   frequency = 12/months
     *   if dcs &lt; 365/frequency  -> dcs/365
     *   else                       -> 1/frequency - (dcc-dcs)/365
     */
    final private class CA_Impl extends DayCounter.Impl {

        @Override
        public String name() /* @ReadOnly */ {
            return "Actual/365 (Fixed) Canadian Bond";
        }

        @Override
        public /*@Time*/ double yearFraction(final Date d1, final Date d2,
                final Date refPeriodStart, final Date refPeriodEnd) /* @ReadOnly */ {
            if (d1.equals(d2)) {
                return 0.0;
            }
            QL.require(!refPeriodStart.isNull(), "invalid refPeriodStart");
            QL.require(!refPeriodEnd.isNull(), "invalid refPeriodEnd");

            final double dcs = dayCount(d1, d2);
            final double dcc = dayCount(refPeriodStart, refPeriodEnd);
            final int months = (int) Math.round(12.0 * dcc / 365.0);
            QL.require(months != 0, "invalid reference period for Act/365 Canadian; must be longer than a month");
            final int frequency = 12 / months;
            QL.require(frequency != 0, "invalid reference period for Act/365 Canadian; must not be longer than a year");

            if (dcs < (365 / frequency)) {
                return dcs / 365.0;
            }
            return 1.0 / frequency - (dcc - dcs) / 365.0;
        }
    }

    /**
     * Actual/365 (No Leap) — port of v1.42.1
     * ql/time/daycounters/actual365fixed.cpp:69-98.
     *
     * Feb 29 is treated as the same day-of-year as Feb 28.
     */
    final private class NL_Impl extends DayCounter.Impl {

        private final int[] MONTH_OFFSET = {
            0,  31,  59,  90, 120, 151, // Jan - Jun
            181, 212, 243, 273, 304, 334  // Jul - Dec
        };

        @Override
        public String name() /* @ReadOnly */ {
            return "Actual/365 (No Leap)";
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            long s1 = (long) d1.dayOfMonth() + MONTH_OFFSET[d1.month().value() - 1] + ((long) d1.year() * 365L);
            long s2 = (long) d2.dayOfMonth() + MONTH_OFFSET[d2.month().value() - 1] + ((long) d2.year() * 365L);

            if (d1.month().value() == 2 && d1.dayOfMonth() == 29) {
                --s1;
            }
            if (d2.month().value() == 2 && d2.dayOfMonth() == 29) {
                --s2;
            }
            return s2 - s1;
        }

        @Override
        public /*@Time*/ double yearFraction(final Date d1, final Date d2,
                final Date refPeriodStart, final Date refPeriodEnd) /* @ReadOnly */ {
            return dayCount(d1, d2) / 365.0;
        }
    }

}
