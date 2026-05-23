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

package org.jquantlib.daycounters;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;

/**
 * 30/360 day count convention
 * <p>
 * The 30/360 day count can be calculated according to US, European, or Italian conventions.
 * <p>
 * US (NASD) convention: if the starting date is the 31st of a month, it becomes equal to the 30th of the same month. If
 * the ending date is the 31st of a month and the starting date is earlier than the 30th of a month, the ending date
 * becomes equal to the 1st of the next month, otherwise the ending date becomes equal to the 30th of the same month.
 * Also known as "30/360", "360/360", or "Bond Basis"
 * <p>
 * European convention: starting dates or ending dates that occur on the 31st of a month become equal to the 30th of the
 * same month. Also known as "30E/360", or "Eurobond Basis"
 * <p>
 * Italian convention: starting dates or ending dates that occur on February and are grater than 27 become equal to 30
 * for computational sake.
 *
 * @author Srinivas Hasti
 * @author Richard Gomes
 * @see <a href="http://en.wikipedia.org/wiki/Day_count_convention">Day count convention</a>
 */
@QualityAssurance( quality = Quality.Q4_UNIT, version = Version.V097, reviewers = "Richard Gomes" )
public class Thirty360 extends DayCounter {

    public Thirty360() {
        this(Convention.BondBasis, new Date());
    }

    //
    // public constructors
    //

    public Thirty360(final Thirty360.Convention c) {
        this(c, new Date());
    }

    /**
     * Termination-date-aware ctor (required by ISDA convention per
     * v1.42.1 ql/time/daycounters/thirty360.{hpp,cpp}).
     */
    public Thirty360(final Thirty360.Convention c, final Date terminationDate) {
        switch ( c ) {
        case USA:
            super.impl = new Impl_US();
            break;
        case ISMA:
        case BondBasis:
            super.impl = new Impl_ISMA();
            break;
        case European:
        case EurobondBasis:
            super.impl = new Impl_EU();
            break;
        case Italian:
            super.impl = new Impl_IT();
            break;
        case ISDA:
        case German:
            super.impl = new Impl_ISDA(terminationDate);
            break;
        case NASD:
            super.impl = new Impl_NASD();
            break;
        default:
            throw new LibraryException("unknown 30/360 convention");
        }
    }

    /**
     * 30/360 Calendar Conventions
     *
     * <p>Aligned to v1.42.1 (ql/time/daycounters/thirty360.hpp:78-88):
     * {USA, BondBasis, European, EurobondBasis, Italian, German, ISMA,
     *  ISDA, NASD}.
     */
    public enum Convention {
        USA, BondBasis, European, EurobondBasis, Italian, German, ISMA, ISDA, NASD
    }

    //
    // private static helpers
    //

    private static boolean isLastOfFebruary(final int d, final int m, final int y) {
        return m == 2 && d == 28 + (Date.isLeap(y) ? 1 : 0);
    }

    //
    // private inner classes
    //

    /**
     * Implementation of Thirty360 class abstraction according to the v1.42.1
     * US convention.
     *
     * <p>Distinct from {@link Impl_ISMA} (BondBasis): the US rule applies
     * end-of-February adjustments (last-of-Feb on either side collapses to
     * the 30th). See ql/time/daycounters/thirty360.cpp:57-73.
     */
    private final class Impl_US extends DayCounter.Impl {

        @Override
        public String name() /* @ReadOnly */ {
            return "30/360 (US)";
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            int dd1 = d1.dayOfMonth();
            int dd2 = d2.dayOfMonth();
            final int mm1 = d1.month().value();
            final int mm2 = d2.month().value();
            final int yy1 = d1.year();
            final int yy2 = d2.year();

            // See https://en.wikipedia.org/wiki/Day_count_convention#30/360_US
            // NOTE: the order of checks is important
            if (isLastOfFebruary(dd1, mm1, yy1)) {
                if (isLastOfFebruary(dd2, mm2, yy2)) { dd2 = 30; }
                dd1 = 30;
            }
            if (dd2 == 31 && dd1 >= 30) { dd2 = 30; }
            if (dd1 == 31) { dd1 = 30; }

            return 360L * (yy2 - yy1) + 30L * (mm2 - mm1) + (dd2 - dd1);
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            return /*@Time*/ dayCount(dateStart, dateEnd) / 360.0;
        }
    }

    /**
     * Implementation of Thirty360 class abstraction according to v1.42.1
     * Bond Basis / ISMA convention (no end-of-Feb adjustment).
     */
    private final class Impl_ISMA extends DayCounter.Impl {

        @Override
        public String name() /* @ReadOnly */ {
            return "30/360 (Bond Basis)";
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            int dd1 = d1.dayOfMonth();
            int dd2 = d2.dayOfMonth();
            final int mm1 = d1.month().value();
            final int mm2 = d2.month().value();
            final int yy1 = d1.year();
            final int yy2 = d2.year();

            if (dd1 == 31) { dd1 = 30; }
            if (dd2 == 31 && dd1 == 30) { dd2 = 30; }

            return 360L * (yy2 - yy1) + 30L * (mm2 - mm1) + (dd2 - dd1);
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            return /*@Time*/ dayCount(dateStart, dateEnd) / 360.0;
        }
    }

    /**
     * ISDA / German "30E/360 (ISDA)" implementation. End-of-Feb collapses
     * to 30 on both sides except when the end date is the termination date.
     */
    private final class Impl_ISDA extends DayCounter.Impl {

        private final Date terminationDate;

        Impl_ISDA(final Date terminationDate) {
            this.terminationDate = terminationDate;
        }

        @Override
        public String name() /* @ReadOnly */ {
            return "30E/360 (ISDA)";
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            int dd1 = d1.dayOfMonth();
            int dd2 = d2.dayOfMonth();
            final int mm1 = d1.month().value();
            final int mm2 = d2.month().value();
            final int yy1 = d1.year();
            final int yy2 = d2.year();

            if (dd1 == 31) { dd1 = 30; }
            if (dd2 == 31) { dd2 = 30; }

            if (isLastOfFebruary(dd1, mm1, yy1)) { dd1 = 30; }

            final boolean d2IsTerm = !terminationDate.isNull() && d2.eq(terminationDate);
            if (!d2IsTerm && isLastOfFebruary(dd2, mm2, yy2)) { dd2 = 30; }

            return 360L * (yy2 - yy1) + 30L * (mm2 - mm1) + (dd2 - dd1);
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            return /*@Time*/ dayCount(dateStart, dateEnd) / 360.0;
        }
    }

    /**
     * NASD convention (ql/time/daycounters/thirty360.cpp:130-141).
     */
    private final class Impl_NASD extends DayCounter.Impl {

        @Override
        public String name() /* @ReadOnly */ {
            return "30/360 (NASD)";
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            int dd1 = d1.dayOfMonth();
            int dd2 = d2.dayOfMonth();
            final int mm1 = d1.month().value();
            int mm2 = d2.month().value();
            final int yy1 = d1.year();
            final int yy2 = d2.year();

            if (dd1 == 31) { dd1 = 30; }
            if (dd2 == 31 && dd1 >= 30) { dd2 = 30; }
            if (dd2 == 31 && dd1 < 30) { dd2 = 1; mm2++; }

            return 360L * (yy2 - yy1) + 30L * (mm2 - mm1) + (dd2 - dd1);
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            return /*@Time*/ dayCount(dateStart, dateEnd) / 360.0;
        }
    }

    /**
     * Implementation of Thirty360 class abstraction according to European convention
     *
     * @author Richard Gomes
     * @see <a href="http://en.wikipedia.org/wiki/Bridge_pattern">Bridge pattern</a>
     */
    private final class Impl_EU extends DayCounter.Impl {

        @Override
        public String name() /* @ReadOnly */ {
            return "30E/360 (Eurobond Basis)";
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            return /*@Time*/ dayCount(dateStart, dateEnd) / 360.0;
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            final int dd1 = d1.dayOfMonth();
            final int dd2 = d2.dayOfMonth();
            final int mm1 = d1.month().value();
            final int mm2 = d2.month().value();
            final int yy1 = d1.year();
            final int yy2 = d2.year();

            return 360L * (yy2 - yy1) + 30L * (mm2 - mm1 - 1) + Math.max(0, 30 - dd1) + Math.min(30, dd2);
        }

    }

    /**
     * Implementation of Thirty360 class abstraction according to Italian convention
     *
     * @author Richard Gomes
     * @see <a href="http://en.wikipedia.org/wiki/Bridge_pattern">Bridge pattern</a>
     */
    private final class Impl_IT extends DayCounter.Impl {

        @Override
        protected String name() /* @ReadOnly */ {
            return "30/360 (Italian)";
        }

        @Override
        public /*@Time*/ double yearFraction(final Date dateStart, final Date dateEnd, final Date refPeriodStart,
                final Date refPeriodEnd) /* @ReadOnly */ {
            return /*@Time*/ dayCount(dateStart, dateEnd) / 360.0;
        }

        @Override
        protected long dayCount(final Date d1, final Date d2) /* @ReadOnly */ {
            int dd1 = d1.dayOfMonth();
            int dd2 = d2.dayOfMonth();
            final int mm1 = d1.month().value();
            final int mm2 = d2.month().value();
            final int yy1 = d1.year();
            final int yy2 = d2.year();

            if ( mm1 == 2 && dd1 > 27 ) {
                dd1 = 30;
            }
            if ( mm2 == 2 && dd2 > 27 ) {
                dd2 = 30;
            }

            return 360L * (yy2 - yy1) + 30L * (mm2 - mm1 - 1) + Math.max(0, 30 - dd1) + Math.min(30, dd2);
        }

    }

}
