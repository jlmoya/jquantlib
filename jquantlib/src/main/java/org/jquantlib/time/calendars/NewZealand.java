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
import static org.jquantlib.time.Weekday.Monday;
import static org.jquantlib.time.Weekday.Tuesday;

/**
 * New Zealand calendar Holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st (possibly moved to Monday or Tuesday)</li>
 * <li>Day after New Year's Day, January 2nd (possibly moved to Monday or Tuesday)</li>
 * <li>Waitangi Day. February 6th (possibly moved to Monday since 2013)</li>
 * <li>Good Friday</li>
 * <li>Easter Monday</li>
 * <li>ANZAC Day. April 25th (possibly moved to Monday since 2013)</li>
 * <li>Queen's Birthday, first Monday in June</li>
 * <li>Labour Day, fourth Monday in October</li>
 * <li>Christmas, December 25th (possibly moved to Monday or Tuesday)</li>
 * <li>Boxing Day, December 26th (possibly moved to Monday or Tuesday)</li>
 * <li>Matariki, in June or July, official calendar released for years 2022-2052</li>
 * </ul>
 *
 * Additional holidays for {@link Market#Wellington}:
 * <ul><li>Anniversary Day, Monday nearest January 22nd</li></ul>
 *
 * Additional holidays for {@link Market#Auckland}:
 * <ul><li>Anniversary Day, Monday nearest January 29th</li></ul>
 *
 * <p>Aligned to C++ QuantLib v1.42.1 ({@code ql/time/calendars/newzealand.{hpp,cpp}}).
 * Compared to the legacy 2008-era JQuantLib rules:
 * <ul>
 *   <li>{@link Market} enum added (Wellington / Auckland);</li>
 *   <li>Waitangi Day and ANZAC Day are Monday-moved when falling on a weekend
 *       (for years {@code y > 2013}, per the 2013 Holidays Act amendment);</li>
 *   <li>Matariki encoded for 2022-2052;</li>
 *   <li>Queen Elizabeth's funeral (26 Sep 2022) added.</li>
 * </ul>
 *
 * @author Anand Mani
 * @author Zahid Hussain
 * @author Jose Moya
 * @note The holiday rules for New Zealand were documented by David Gilbert for IDB
 * (http://www.jrefinery.com/ibd/). The Matariki holiday calendar has been released by the NZ Government
 * (https://www.legislation.govt.nz/act/public/2022/0014/latest/LMS557893.html).
 * @category calendars
 * @see <a href="http://www.nzx.com">New Zealand Stock Exchange</a>
 */
@QualityAssurance(quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" })
public class NewZealand extends Calendar {

    public NewZealand() {
        this(Market.Wellington);
    }

    public NewZealand(final Market market) {
        switch (market) {
        case Wellington:
            impl = new WellingtonImpl();
            break;
        case Auckland:
            impl = new AucklandImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        Wellington, Auckland
    }

    //
    // private inner classes
    //

    /** Common rules — public holidays observed in both Wellington and Auckland. */
    private class CommonImpl extends WesternImpl {

        @Override
        public String name() {
            return "New Zealand";
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
                    // New Year's Day (possibly moved to Monday or Tuesday)
                    || ((d == 1 || (d == 3 && (w == Monday || w == Tuesday))) && m == January)
                    // Day after New Year's Day (possibly moved to Mon or Tuesday)
                    || ((d == 2 || (d == 4 && (w == Monday || w == Tuesday))) && m == January)
                    // Waitangi Day. February 6th (possibly moved to Monday since 2013)
                    || (d == 6 && m == February)
                    || ((d == 7 || d == 8) && w == Monday && m == February && y > 2013)
                    // Good Friday
                    || (dd == em - 3)
                    // Easter Monday
                    || (dd == em)
                    // ANZAC Day. April 25th (possibly moved to Monday since 2013)
                    || (d == 25 && m == April)
                    || ((d == 26 || d == 27) && w == Monday && m == April && y > 2013)
                    // Queen's Birthday, first Monday in June
                    || (d <= 7 && w == Monday && m == June)
                    // Labour Day, fourth Monday in October
                    || ((d >= 22 && d <= 28) && w == Monday && m == October)
                    // Christmas, December 25th (possibly Monday or Tuesday)
                    || ((d == 25 || (d == 27 && (w == Monday || w == Tuesday))) && m == December)
                    // Boxing Day, December 26th (possibly Monday or Tuesday)
                    || ((d == 26 || (d == 28 && (w == Monday || w == Tuesday))) && m == December)
                    // Matariki, official 30-year calendar released by NZ government
                    || (d == 20 && m == June && y == 2025)
                    || (d == 21 && m == June && (y == 2030 || y == 2052))
                    || (d == 24 && m == June && (y == 2022 || y == 2033 || y == 2044))
                    || (d == 25 && m == June && (y == 2027 || y == 2038 || y == 2049))
                    || (d == 28 && m == June && y == 2024)
                    || (d == 29 && m == June && (y == 2035 || y == 2046))
                    || (d == 30 && m == June && y == 2051)
                    || (d == 2  && m == July && y == 2032)
                    || (d == 3  && m == July && (y == 2043 || y == 2048))
                    || (d == 6  && m == July && (y == 2029 || y == 2040))
                    || (d == 7  && m == July && (y == 2034 || y == 2045))
                    || (d == 10 && m == July && (y == 2026 || y == 2037))
                    || (d == 11 && m == July && (y == 2031 || y == 2042))
                    || (d == 14 && m == July && (y == 2023 || y == 2028))
                    || (d == 15 && m == July && (y == 2039 || y == 2050))
                    || (d == 18 && m == July && y == 2036)
                    || (d == 19 && m == July && (y == 2041 || y == 2047))
                    // Queen Elizabeth's funeral
                    || (d == 26 && m == September && y == 2022)) {
                return false;
            }
            return true;
        }
    }

    private final class WellingtonImpl extends CommonImpl {

        @Override
        public String name() {
            return "New Zealand (Wellington)";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            if (!super.isBusinessDay(date)) {
                return false;
            }
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            // Anniversary Day, Monday nearest January 22nd
            if ((d >= 19 && d <= 25) && w == Monday && m == January) {
                return false;
            }
            return true;
        }
    }

    private final class AucklandImpl extends CommonImpl {

        @Override
        public String name() {
            return "New Zealand (Auckland)";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            if (!super.isBusinessDay(date)) {
                return false;
            }
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            // Anniversary Day, Monday nearest January 29th
            if ((d >= 26 && w == Monday && m == January)
                    || (d == 1 && w == Monday && m == February)) {
                return false;
            }
            return true;
        }
    }
}
