/*
 Copyright (C) 2008 Jia Jia

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
import static org.jquantlib.time.Weekday.Friday;
import static org.jquantlib.time.Weekday.Monday;
import static org.jquantlib.time.Weekday.Saturday;
import static org.jquantlib.time.Weekday.Sunday;

/**
 * South Korean calendars. Public holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, January 1st</li>
 * <li>Independence Day, March 1st (possibly moved to Monday since 2022)</li>
 * <li>Arbour Day, April 5th (until 2005)</li>
 * <li>Labour Day, May 1st</li>
 * <li>Children's Day, May 5th (possibly moved to Monday since 2014)</li>
 * <li>Memorial Day, June 6th</li>
 * <li>Constitution Day, July 17th (until 2007)</li>
 * <li>Liberation Day, August 15th (possibly moved to Monday since 2021)</li>
 * <li>National Foundation Day, October 3rd (possibly moved to Monday since 2021)</li>
 * <li>Hangul Proclamation of Korea, October 9th (since 2013, possibly Monday since 2021)</li>
 * <li>Christmas Day, December 25th (possibly Monday since 2023)</li>
 * </ul>
 *
 * Other holidays for which no rule is given (data available for 2004-2050):
 * <ul>
 * <li>Lunar New Year</li>
 * <li>Election Days</li>
 * <li>Buddha's birthday</li>
 * <li>Harvest Moon Day (Chuseok)</li>
 * </ul>
 *
 * <p>Aligned to C++ QuantLib v1.42.1 ({@code ql/time/calendars/southkorea.{hpp,cpp}}).
 * Compared to the legacy 2008-era JQuantLib rules, the holiday tables now
 * extend through 2050 and post-2013 / post-2020 / post-2022 Monday-shift rules
 * are folded in.
 *
 * @author Jia Jia
 * @author Zahid Hussain
 * @author Jose Moya
 * @category Calendars
 */
@QualityAssurance(quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" })
public class SouthKorea extends Calendar {

    public SouthKorea() {
        this(Market.KRX);
    }

    public SouthKorea(final Market m) {
        switch (m) {
        case Settlement:
            impl = new SettlementImpl();
            break;
        case KRX:
            impl = new KrxImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /** Public holidays */
        Settlement,
        /** Korea Exchange */
        KRX
    }

    //
    // private inner classes
    //

    private class SettlementImpl extends Impl {

        @Override
        public String name() {
            return "South-Korean settlement";
        }

        @Override
        public boolean isWeekend(final Weekday w) {
            return w == Saturday || w == Sunday;
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();

            if (isWeekend(w)
                    // New Year's Day
                    || (d == 1 && m == January)
                    // Independence Day
                    || (d == 1 && m == March)
                    || (w == Monday && (d == 2 || d == 3) && m == March && y > 2021)
                    // Arbour Day
                    || (d == 5 && m == April && y <= 2005)
                    // Labour Day
                    || (d == 1 && m == May)
                    // Children's Day
                    || (d == 5 && m == May)
                    || (w == Monday && (d == 6 || d == 7) && m == May && y > 2013)
                    // Memorial Day
                    || (d == 6 && m == June)
                    // Constitution Day
                    || (d == 17 && m == July && y <= 2007)
                    // Liberation Day
                    || (d == 15 && m == August)
                    || (w == Monday && (d == 16 || d == 17) && m == August && y > 2020)
                    // National Foundation Day
                    || (d == 3 && m == October)
                    || (w == Monday && (d == 4 || d == 5) && m == October && y > 2020)
                    // Christmas Day
                    || (d == 25 && m == December)
                    || (w == Monday && (d == 26 || d == 27) && m == December && y > 2022)) {
                return false;
            }

            // Lunar New Year
            if (((d == 21 || d == 22 || d == 23) && m == January && y == 2004)
                    || ((d == 8 || d == 9 || d == 10) && m == February && y == 2005)
                    || ((d == 28 || d == 29 || d == 30) && m == January && y == 2006)
                    || (d == 19 && m == February && y == 2007)
                    || ((d == 6 || d == 7 || d == 8) && m == February && y == 2008)
                    || ((d == 25 || d == 26 || d == 27) && m == January && y == 2009)
                    || ((d == 13 || d == 14 || d == 15) && m == February && y == 2010)
                    || ((d == 2 || d == 3 || d == 4) && m == February && y == 2011)
                    || ((d == 23 || d == 24) && m == January && y == 2012)
                    || (d == 11 && m == February && y == 2013)
                    || ((d == 30 || d == 31) && m == January && y == 2014)
                    || ((d == 18 || d == 19 || d == 20) && m == February && y == 2015)
                    || ((d >= 7 && d <= 10) && m == February && y == 2016)
                    || ((d >= 27 && d <= 30) && m == January && y == 2017)
                    || ((d == 15 || d == 16 || d == 17) && m == February && y == 2018)
                    || ((d == 4 || d == 5 || d == 6) && m == February && y == 2019)
                    || ((d >= 24 && d <= 27) && m == January && y == 2020)
                    || ((d == 11 || d == 12 || d == 13) && m == February && y == 2021)
                    || (((d == 31 && m == January) || ((d == 1 || d == 2) && m == February)) && y == 2022)
                    || ((d == 23 || d == 24) && m == January && y == 2023)
                    || ((d >= 9 && d <= 12) && m == February && y == 2024)
                    || ((d == 28 || d == 29 || d == 30) && m == January && y == 2025)
                    || ((d == 16 || d == 17 || d == 18) && m == February && y == 2026)
                    || ((d == 8 || d == 9) && m == February && y == 2027)
                    || ((d == 26 || d == 27 || d == 28) && m == January && y == 2028)
                    || ((d == 12 || d == 13 || d == 14) && m == February && y == 2029)
                    || ((d == 4 || d == 5) && m == February && y == 2030)
                    || ((d == 22 || d == 23 || d == 24) && m == January && y == 2031)
                    || ((d == 10 || d == 11 || d == 12) && m == February && y == 2032)
                    || (((d == 31 && m == January) || ((d == 1 || d == 2) && m == February)) && y == 2033)
                    || ((d == 20 || d == 21) && m == February && y == 2034)
                    || ((d == 7 || d == 8 || d == 9) && m == February && y == 2035)
                    || ((d == 28 || d == 29 || d == 30) && m == January && y == 2036)
                    || ((d == 16 || d == 17) && m == February && y == 2037)
                    || ((d == 3 || d == 4 || d == 5) && m == February && y == 2038)
                    || ((d == 24 || d == 25 || d == 26) && m == January && y == 2039)
                    || ((d == 13 || d == 14) && m == February && y == 2040)
                    || (((d == 31 && m == January) || ((d == 1 || d == 2) && m == February)) && y == 2041)
                    || ((d == 21 || d == 22 || d == 23) && m == January && y == 2042)
                    || ((d == 9 || d == 10 || d == 11) && m == February && y == 2043)
                    || ((((d == 29 || d == 30 || d == 31) && m == January) || (d == 1 && m == February)) && y == 2044)
                    || ((d == 16 || d == 17 || d == 18) && m == February && y == 2045)
                    || ((d == 5 || d == 6 || d == 7) && m == February && y == 2046)
                    || ((d >= 25 && d <= 28) && m == January && y == 2047)
                    || ((d == 13 || d == 14 || d == 15) && m == February && y == 2048)
                    || ((d == 1 || d == 2 || d == 3) && m == February && y == 2049)
                    || ((d == 24 || d == 25) && m == January && y == 2050)) {
                return false;
            }

            // Election Days
            if ((d == 15 && m == April    && y == 2004) // National Assembly
                    || (d == 31 && m == May      && y == 2006) // Regional election
                    || (d == 19 && m == December && y == 2007) // Presidency
                    || (d ==  9 && m == April    && y == 2008) // National Assembly
                    || (d ==  2 && m == June     && y == 2010) // Local election
                    || (d == 11 && m == April    && y == 2012) // National Assembly
                    || (d == 19 && m == December && y == 2012) // Presidency
                    || (d ==  4 && m == June     && y == 2014) // Local election
                    || (d == 13 && m == April    && y == 2016) // National Assembly
                    || (d ==  9 && m == May      && y == 2017) // Presidency
                    || (d == 13 && m == June     && y == 2018) // Local election
                    || (d == 15 && m == April    && y == 2020) // National Assembly
                    || (d ==  9 && m == March    && y == 2022) // Presidency
                    || (d ==  1 && m == June     && y == 2022) // Local election
                    || (d == 10 && m == April    && y == 2024)) { // National Assembly
                return false;
            }

            // Buddha's birthday
            if ((d == 26 && m == May   && y == 2004)
                    || (d == 15 && m == May   && y == 2005)
                    || (d ==  5 && m == May   && y == 2006)
                    || (d == 24 && m == May   && y == 2007)
                    || (d == 12 && m == May   && y == 2008)
                    || (d ==  2 && m == May   && y == 2009)
                    || (d == 21 && m == May   && y == 2010)
                    || (d == 10 && m == May   && y == 2011)
                    || (d == 28 && m == May   && y == 2012)
                    || (d == 17 && m == May   && y == 2013)
                    || (d ==  6 && m == May   && y == 2014)
                    || (d == 25 && m == May   && y == 2015)
                    || (d == 14 && m == May   && y == 2016)
                    || (d ==  3 && m == May   && y == 2017)
                    || (d == 22 && m == May   && y == 2018)
                    || (d == 12 && m == May   && y == 2019)
                    || (d == 30 && m == April && y == 2020)
                    || (d == 19 && m == May   && y == 2021)
                    || (d ==  8 && m == May   && y == 2022)
                    || (d == 29 && m == May   && y == 2023)
                    || (d == 15 && m == May   && y == 2024)
                    || (d ==  6 && m == May   && y == 2025)
                    || (d == 25 && m == May   && y == 2026)
                    || (d == 13 && m == May   && y == 2027)
                    || (d ==  2 && m == May   && y == 2028)
                    || (d == 21 && m == May   && y == 2029)
                    || (d ==  9 && m == May   && y == 2030)
                    || (d == 28 && m == May   && y == 2031)
                    || (d == 17 && m == May   && y == 2032)
                    || (d ==  6 && m == May   && y == 2033)
                    || (d == 25 && m == May   && y == 2034)
                    || (d == 15 && m == May   && y == 2035)
                    || (d ==  6 && m == May   && y == 2036)
                    || (d == 22 && m == May   && y == 2037)
                    || (d == 11 && m == May   && y == 2038)
                    || (d ==  2 && m == May   && y == 2039)
                    || (d == 18 && m == May   && y == 2040)
                    || (d ==  7 && m == May   && y == 2041)
                    || (d == 26 && m == May   && y == 2042)
                    || (d == 18 && m == May   && y == 2043)
                    || (d ==  6 && m == May   && y == 2044)
                    || (d == 24 && m == May   && y == 2045)
                    || (d == 14 && m == May   && y == 2046)
                    || (d ==  2 && m == May   && y == 2047)
                    || (d == 20 && m == May   && y == 2048)
                    || (d == 10 && m == May   && y == 2049)
                    || (d == 30 && m == May   && y == 2050)) {
                return false;
            }

            // Special holiday: 70 years from Independence Day
            if (d == 14 && m == August && y == 2015) {
                return false;
            }
            // Special temporary holidays
            if ((d == 17 && m == August && y == 2020)
                    || (d == 2 && m == October && y == 2023)
                    || (d == 1 && m == October && y == 2024)
                    || (d == 27 && m == January && y == 2025)) {
                return false;
            }

            // Harvest Moon Day (Chuseok)
            if (((d == 27 || d == 28 || d == 29) && m == September && y == 2004)
                    || ((d == 17 || d == 18 || d == 19) && m == September && y == 2005)
                    || ((d == 5 || d == 6 || d == 7) && m == October && y == 2006)
                    || ((d == 24 || d == 25 || d == 26) && m == September && y == 2007)
                    || ((d == 13 || d == 14 || d == 15) && m == September && y == 2008)
                    || ((d == 2 || d == 3 || d == 4) && m == October && y == 2009)
                    || ((d == 21 || d == 22 || d == 23) && m == September && y == 2010)
                    || ((d == 12 || d == 13) && m == September && y == 2011)
                    || (d == 1 && m == October && y == 2012)
                    || ((d == 18 || d == 19 || d == 20) && m == September && y == 2013)
                    || ((d == 8 || d == 9 || d == 10) && m == September && y == 2014)
                    || ((d == 28 || d == 29) && m == September && y == 2015)
                    || ((d == 14 || d == 15 || d == 16) && m == September && y == 2016)
                    || ((d >= 3 && d <= 6) && m == October && y == 2017)
                    || ((d >= 23 && d <= 26) && m == September && y == 2018)
                    || ((d == 12 || d == 13 || d == 14) && m == September && y == 2019)
                    || (((d == 30 && m == September) || ((d == 1 || d == 2) && m == October)) && y == 2020)
                    || ((d == 20 || d == 21 || d == 22) && m == September && y == 2021)
                    || ((d == 9 || d == 10 || d == 11) && m == September && y == 2022)
                    || ((d >= 9 && d <= 12) && m == September && y == 2022)
                    || ((d == 28 || d == 29 || d == 30) && m == September && y == 2023)
                    || ((d == 16 || d == 17 || d == 18) && m == September && y == 2024)
                    || ((d == 6 || d == 7 || d == 8) && m == October && y == 2025)
                    || ((d == 24 || d == 25 || d == 26) && m == September && y == 2026)
                    || ((d == 14 || d == 15 || d == 16) && m == September && y == 2027)
                    || ((d >= 2 && d <= 5) && m == October && y == 2028)
                    || ((d >= 21 && d <= 24) && m == September && y == 2029)
                    || ((d == 11 || d == 12 || d == 13) && m == September && y == 2030)
                    || (((d == 30 && m == September) || ((d == 1 || d == 2) && m == October)) && y == 2031)
                    || ((d == 20 || d == 21) && m == September && y == 2032)
                    || ((d == 7 || d == 8 || d == 9) && m == September && y == 2033)
                    || ((d == 26 || d == 27 || d == 28) && m == September && y == 2034)
                    || ((d == 17 || d == 18) && m == September && y == 2035)
                    || ((d >= 3 && d <= 7) && m == October && y == 2036)
                    || ((d == 23 || d == 24 || d == 25) && m == September && y == 2037)
                    || ((d == 13 || d == 14 || d == 15) && m == September && y == 2038)
                    || ((d == 3 || d == 4 || d == 5) && m == October && y == 2039)
                    || ((d == 20 || d == 21 || d == 22) && m == September && y == 2040)
                    || ((d == 9 || d == 10 || d == 11) && m == September && y == 2041)
                    || ((d == 29 || d == 30) && m == September && y == 2042)
                    || ((d == 16 || d == 17 || d == 18) && m == September && y == 2043)
                    || ((d == 4 || d == 5 || d == 6) && m == October && y == 2044)
                    || ((d == 25 || d == 26 || d == 27) && m == September && y == 2045)
                    || ((d >= 14 && d <= 17) && m == September && y == 2046)
                    || ((d == 4 || d == 5 || d == 7) && m == October && y == 2047)
                    || ((d == 21 || d == 22 || d == 23) && m == September && y == 2048)
                    || ((d >= 10 && d <= 13) && m == September && y == 2049)
                    || ((((d == 29 || d == 30) && m == September) || (d == 1 && m == October)) && y == 2050)) {
                return false;
            }

            // Hangul Proclamation of Korea
            if ((d == 9 && m == October && y >= 2013)
                    || (w == Monday && (d == 10 || d == 11) && m == October && y > 2020)) {
                return false;
            }

            return true;
        }
    }

    private class KrxImpl extends SettlementImpl {
        @Override
        public String name() {
            return "South-Korea exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            // public holidays
            if (!super.isBusinessDay(date)) {
                return false;
            }

            final int d = date.dayOfMonth();
            final Weekday w = date.weekday();
            final Month m = date.month();
            final int y = date.year();

            // Year-end closing
            if (((((d == 29 || d == 30) && w == Friday) || d == 31) && m == December)) {
                return false;
            }
            // Occasional closing days (KRX day)
            if ((d == 6 && m == May && y == 2016)
                    || (d == 2 && m == October && y == 2017)) {
                return false;
            }

            return true;
        }
    }
}
