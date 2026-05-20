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

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.April;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.February;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.March;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;
import static org.jquantlib.time.Weekday.Monday;

/**
 * Russian calendars.
 * <p>
 * Faithful port of {@code ql/time/calendars/russia.{hpp,cpp}} from QuantLib
 * v1.42.1.
 *
 * <p>Settlement public holidays (see <http://www.cbr.ru/eng/>):
 * <ul>
 *   <li>Saturdays</li>
 *   <li>Sundays</li>
 *   <li>New Year holidays, January 1st to 5th (only 1st and 2nd until 2005)</li>
 *   <li>Christmas, January 7th (possibly moved to Monday)</li>
 *   <li>Defender of the Fatherland Day, February 23rd (possibly moved to Monday)</li>
 *   <li>International Women's Day, March 8th (possibly moved to Monday)</li>
 *   <li>Labour Day, May 1st (possibly moved to Monday)</li>
 *   <li>Victory Day, May 9th (possibly moved to Monday)</li>
 *   <li>Russia Day, June 12th (possibly moved to Monday)</li>
 *   <li>Unity Day, November 4th (possibly moved to Monday)</li>
 * </ul>
 *
 * <p>Holidays for the Moscow Exchange (MOEX) taken from <http://moex.com/s726>
 * and related pages. These holidays are <em>not</em> consistent year-to-year,
 * may or may not correlate to public holidays, and are only available for dates
 * since the introduction of the MOEX brand (a merger of the stock and futures
 * markets) in 2011.
 *
 * @author Jose Moya
 */
public class Russia extends Calendar {

    public Russia() {
        this(Market.Settlement);
    }

    public Russia(final Market market) {
        switch (market) {
        case Settlement:
            impl = new SettlementImpl();
            break;
        case MOEX:
            impl = new ExchangeImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /** Generic settlement calendar */
        Settlement,
        /** Moscow Exchange calendar */
        MOEX
    }

    //
    // private final inner classes
    //

    private final class SettlementImpl extends OrthodoxImpl {

        @Override
        public String name() {
            return "Russian settlement";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();

            if (isWeekend(w)
                    // New Year's holidays
                    || (y <= 2005 && d <= 2 && m == January)
                    || (y >= 2005 && d <= 5 && m == January)
                    // in 2012, the 6th was also a holiday
                    || (y == 2012 && d == 6 && m == January)
                    // Christmas (possibly moved to Monday)
                    || ((d == 7 || ((d == 8 || d == 9) && w == Monday)) && m == January)
                    // Defender of the Fatherland Day (possibly moved to Monday)
                    || ((d == 23 || ((d == 24 || d == 25) && w == Monday)) && m == February)
                    // International Women's Day (possibly moved to Monday)
                    || ((d == 8 || ((d == 9 || d == 10) && w == Monday)) && m == March)
                    // Labour Day (possibly moved to Monday)
                    || ((d == 1 || ((d == 2 || d == 3) && w == Monday)) && m == May)
                    // Victory Day (possibly moved to Monday)
                    || ((d == 9 || ((d == 10 || d == 11) && w == Monday)) && m == May)
                    // Russia Day (possibly moved to Monday)
                    || ((d == 12 || ((d == 13 || d == 14) && w == Monday)) && m == June)
                    // Unity Day (possibly moved to Monday)
                    || ((d == 4 || ((d == 5 || d == 6) && w == Monday)) && m == November)) {
                return false;
            }

            if (isExtraHolidaySettlementImpl(d, m, y)) {
                return false;
            }

            return true;
        }

        private boolean isExtraHolidaySettlementImpl(final int d, final Month month, final int year) {
            switch (year) {
            case 2017:
                switch (month) {
                case February: return d == 24;
                case May:      return d == 8;
                case November: return d == 6;
                default:       return false;
                }
            case 2018:
                switch (month) {
                case March:    return d == 9;
                case April:    return d == 30;
                case May:      return d == 2;
                case June:     return d == 11;
                case December: return d == 31;
                default:       return false;
                }
            case 2019:
                if (month == May) {
                    return d == 2 || d == 3 || d == 10;
                }
                return false;
            case 2020:
                switch (month) {
                case March: return d == 30 || d == 31;
                case April: return d == 1 || d == 2 || d == 3;
                case May:   return d == 4 || d == 5;
                default:    return false;
                }
            default:
                return false;
            }
        }
    }

    private final class ExchangeImpl extends OrthodoxImpl {

        @Override
        public String name() {
            return "Moscow exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();

            // The exchange was formally established in 2011, so data are
            // only available from 2012 to present
            if (y < 2012) {
                throw new LibraryException("MOEX calendar for the year " + y + " does not exist.");
            }

            if (isWorkingWeekend(d, m, y)) {
                return true;
            }

            // Known holidays
            if (isWeekend(w)
                    // Defender of the Fatherland Day
                    || (d == 23 && m == February)
                    // International Women's Day (possibly moved to Monday)
                    || ((d == 8 || ((d == 9 || d == 10) && w == Monday)) && m == March)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Victory Day (possibly moved to Monday)
                    || ((d == 9 || ((d == 10 || d == 11) && w == Monday)) && m == May)
                    // Russia Day
                    || (d == 12 && m == June)
                    // Unity Day (possibly moved to Monday)
                    || ((d == 4 || ((d == 5 || d == 6) && w == Monday)) && m == November)
                    // New Year's Eve
                    || (d == 31 && m == December)) {
                return false;
            }

            if (isExtraHolidayExchangeImpl(d, m, y)) {
                return false;
            }

            return true;
        }

        private boolean isWorkingWeekend(final int d, final Month month, final int year) {
            switch (year) {
            case 2012:
                switch (month) {
                case March: return d == 11;
                case April: return d == 28;
                case May:   return d == 5 || d == 12;
                case June:  return d == 9;
                default:    return false;
                }
            case 2016:
                if (month == February) {
                    return d == 20;
                }
                return false;
            case 2018:
                switch (month) {
                case April:    return d == 28;
                case June:     return d == 9;
                case December: return d == 29;
                default:       return false;
                }
            default:
                return false;
            }
        }

        private boolean isExtraHolidayExchangeImpl(final int d, final Month month, final int year) {
            switch (year) {
            case 2012:
                switch (month) {
                case January: return d == 2;
                case March:   return d == 9;
                case April:   return d == 30;
                case June:    return d == 11;
                default:      return false;
                }
            case 2013:
                if (month == January) {
                    return d == 1 || d == 2 || d == 3 || d == 4 || d == 7;
                }
                return false;
            case 2014:
                if (month == January) {
                    return d == 1 || d == 2 || d == 3 || d == 7;
                }
                return false;
            case 2015:
                if (month == January) {
                    return d == 1 || d == 2 || d == 7;
                }
                return false;
            case 2016:
                switch (month) {
                case January:  return d == 1 || d == 7 || d == 8;
                case May:      return d == 2 || d == 3;
                case June:     return d == 13;
                case December: return d == 30;
                default:       return false;
                }
            case 2017:
                switch (month) {
                case January: return d == 2;
                case May:     return d == 8;
                default:      return false;
                }
            case 2018:
                switch (month) {
                case January:  return d == 1 || d == 2 || d == 8;
                case December: return d == 31;
                default:       return false;
                }
            case 2019:
                switch (month) {
                case January:  return d == 1 || d == 2 || d == 7;
                case December: return d == 31;
                default:       return false;
                }
            case 2020:
                switch (month) {
                case January:  return d == 1 || d == 2 || d == 7;
                case February: return d == 24;
                case June:     return d == 24;
                case July:     return d == 1;
                default:       return false;
                }
            default:
                return false;
            }
        }
    }
}
