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

package org.jquantlib.time.calendars;

import static org.jquantlib.time.Month.August;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

/**
 * French calendars.
 * <p>
 * Faithful port of {@code ql/time/calendars/france.{hpp,cpp}} from QuantLib
 * v1.42.1 @ {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Public holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Easter Monday</li>
 * <li>Labour Day, May 1st</li>
 * <li>Armistice 1945, May 8th</li>
 * <li>Ascension, May 10th</li>
 * <li>Pentecôte, May 21st</li>
 * <li>Fête nationale, July 14th</li>
 * <li>Assumption, August 15th</li>
 * <li>All Saint's Day, November 1st</li>
 * <li>Armistice 1918, November 11th</li>
 * <li>Christmas Day, December 25th</li>
 * </ul>
 *
 * <p>Holidays for the Paris stock exchange (Euronext Paris):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Good Friday</li>
 * <li>Easter Monday</li>
 * <li>Labour Day, May 1st</li>
 * <li>Christmas Eve, December 24th</li>
 * <li>Christmas Day, December 25th</li>
 * <li>Boxing Day, December 26th</li>
 * <li>New Year's Eve, December 31st</li>
 * </ul>
 *
 * @author Jose Moya
 */
public class France extends Calendar {

    public France() {
        this(Market.Settlement);
    }

    public France(final Market market) {
        impl = switch (market) {
            case Settlement -> new SettlementImpl();
            case Exchange -> new ExchangeImpl();
        };
        if (impl == null) {
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    /**
     * French calendars.
     */
    public enum Market {
        /** generic settlement calendar */
        Settlement,
        /** Paris stock-exchange calendar */
        Exchange
    }

    private final class SettlementImpl extends WesternImpl {
        @Override
        public String name() {
            return "French settlement";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final int dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            if (isWeekend(w)
                    // Jour de l'An
                    || (d == 1 && m == January)
                    // Lundi de Pâques
                    || (dd == em)
                    // Fête du Travail
                    || (d == 1 && m == May)
                    // Victoire 1945
                    || (d == 8 && m == May)
                    // Ascension
                    || (d == 10 && m == May)
                    // Pentecôte
                    || (d == 21 && m == May)
                    // Fête nationale
                    || (d == 14 && m == July)
                    // Assomption
                    || (d == 15 && m == August)
                    // Toussaint
                    || (d == 1 && m == November)
                    // Armistice 1918
                    || (d == 11 && m == November)
                    // Noël
                    || (d == 25 && m == December)) {
                return false;
            }
            return true;
        }
    }

    private final class ExchangeImpl extends WesternImpl {
        @Override
        public String name() {
            return "Paris stock exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final int dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            if (isWeekend(w)
                    // Jour de l'An
                    || (d == 1 && m == January)
                    // Good Friday
                    || (dd == em - 3)
                    // Easter Monday
                    || (dd == em)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Christmas Eve
                    || (d == 24 && m == December)
                    // Christmas Day
                    || (d == 25 && m == December)
                    // Boxing Day
                    || (d == 26 && m == December)
                    // New Year's Eve
                    || (d == 31 && m == December)) {
                return false;
            }
            return true;
        }
    }
}
