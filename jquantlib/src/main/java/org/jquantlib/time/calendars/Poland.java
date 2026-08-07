/*
 Copyright (C) 2008 Anand Mani

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

package org.jquantlib.time.calendars;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.*;

/**
 * Polish calendar
 * <p>
 * Faithful port of {@code ql/time/calendars/poland.{hpp,cpp}} from C++
 * QuantLib v1.43.
 * <p>
 * Holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>Easter Monday</li>
 * <li>Corpus Christi</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Epiphany, January 6th (since 2011)</li>
 * <li>May Day, May 1st</li>
 * <li>Constitution Day, May 3rd</li>
 * <li>Assumption of the Blessed Virgin Mary, August 15th</li>
 * <li>All Saints Day, November 1st</li>
 * <li>Independence Day, November 11th</li>
 * <li>Christmas, December 25th</li>
 * <li>2nd Day of Christmas, December 26th</li>
 * </ul>
 *
 * @author Anand Mani
 * @author Renjith Nair
 * @author Richard Gomes
 * @author Jose Moya
 * @category calendars
 * @see <a href="http://www.gpw.pl/">Warsaw Stock Exchange</a>
 */

@QualityAssurance( quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" } )

public class Poland extends Calendar {

    //
    // public constructors
    //

    /** C++ v1.43 defaults {@code Poland::Market} to {@link Market#Settlement}. */
    public Poland() {
        this(Market.Settlement);
    }

    public Poland(final Market market) {
        switch ( market ) {
        case Settlement:
            impl = new SettlementImpl();
            break;
        case WSE:
            impl = new WseImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    //
    // public enums
    //

    /**
     * Polish calendars, mirroring {@code Poland::Market}
     * (ql/time/calendars/poland.hpp:65-68).
     */
    public enum Market {
        /** Generic settlement calendar. */
        Settlement,

        /** Warsaw stock exchange calendar. */
        WSE
    }

    //
    // private inner classes
    //

    /**
     * Port of C++ {@code Poland::SettlementImpl}
     * (ql/time/calendars/poland.cpp:41-70).
     */
    private class SettlementImpl extends WesternImpl {
        @Override
        public String name() {
            return "Poland Settlement";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth(), dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            return !isWeekend(w)
                    // Easter Monday
                    && (dd != em)
                    // Corpus Christi
                    && (dd != em + 59)
                    // New Year's Day
                    && (d != 1 || m != January)
                    // Epiphany
                    && (d != 6 || m != January || y < 2011)
                    // May Day
                    && (d != 1 || m != May)
                    // Constitution Day
                    && (d != 3 || m != May)
                    // Assumption of the Blessed Virgin Mary
                    && (d != 15 || m != August)
                    // All Saints Day
                    && (d != 1 || m != November)
                    // Independence Day
                    && (d != 11 || m != November)
                    // Christmas
                    && (d != 25 || m != December)
                    // 2nd Day of Christmas
                    && (d != 26 || m != December);
        }
    }

    /**
     * Port of C++ {@code Poland::WseImpl}
     * (ql/time/calendars/poland.cpp:73-85). The Warsaw stock exchange adds
     * Christmas Eve and New Year's Eve to the settlement holidays; see
     * <a href="https://www.gpw.pl/session-details">gpw.pl session details</a>.
     */
    private final class WseImpl extends SettlementImpl {
        @Override
        public String name() {
            return "Warsaw stock exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final int d = date.dayOfMonth();
            final Month m = date.month();
            if ((d == 24 && m == December) || (d == 31 && m == December)) {
                return false;
            }
            return super.isBusinessDay(date);
        }
    }
}
