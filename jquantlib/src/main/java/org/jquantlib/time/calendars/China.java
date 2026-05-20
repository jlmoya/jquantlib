/*
 Copyright (C) 2008 Tim Swetonic, Jia Jia

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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jquantlib.lang.annotation.QualityAssurance;
import org.jquantlib.lang.annotation.QualityAssurance.Quality;
import org.jquantlib.lang.annotation.QualityAssurance.Version;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.April;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.February;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.March;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.October;
import static org.jquantlib.time.Month.September;
import static org.jquantlib.time.Weekday.Saturday;
import static org.jquantlib.time.Weekday.Sunday;

/**
 * Chinese calendar Holidays:
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's day, January 1st (possibly followed by one or two more holidays)</li>
 * <li>Labour Day, first week in May</li>
 * <li>National Day, one week from October 1st</li>
 * </ul>
 *
 * Other holidays for which no rule is given (data available for 2004-2026):
 * <ul>
 * <li>Chinese New Year</li>
 * <li>Ching Ming Festival</li>
 * <li>Tuen Ng Festival</li>
 * <li>Mid-Autumn Festival</li>
 * <li>70th anniversary of the victory of anti-Japaneses war (2015 only)</li>
 * </ul>
 *
 * SSE data from <http://www.sse.com.cn/>
 * IB data from <http://www.chinamoney.com.cn/>
 *
 * <p>Aligned to C++ QuantLib v1.42.1 ({@code ql/time/calendars/china.{hpp,cpp}}).
 * Compared to the legacy 2008-era JQuantLib rules, the holiday tables now
 * extend through 2026 and the {@link Market#IB} (China Inter Bank) market is
 * added with its working-weekend table from 2005-2026.
 *
 * @author Tim Swetonic
 * @author Jia Jia
 * @author Renjith Nair
 * @author Zahid Hussain
 * @author Jose Moya
 * @see <a href="http://www.sse.com.cn/">SSE</a>
 */
@QualityAssurance(quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" })
public class China extends Calendar {

    public China() {
        this(Market.SSE);
    }

    public China(final Market m) {
        switch (m) {
        case SSE:
            impl = new SseImpl();
            break;
        case IB:
            impl = new IbImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /** Shanghai stock exchange */
        SSE,
        /** China Inter Bank market */
        IB
    }

    //
    // package-private impl utilities
    //

    /**
     * Returns true iff the date is an SSE holiday (covers all the tabular
     * rules from v1.42.1 china.cpp:47-209 for years 2004-2026).
     */
    static boolean sseIsBusinessDay(final Date date) {
        final Weekday w = date.weekday();
        final int d = date.dayOfMonth();
        final Month m = date.month();
        final int y = date.year();

        if (w == Saturday || w == Sunday
                // New Year's Day
                || (d == 1 && m == January)
                || (y == 2005 && d == 3 && m == January)
                || (y == 2006 && (d == 2 || d == 3) && m == January)
                || (y == 2007 && d <= 3 && m == January)
                || (y == 2007 && d == 31 && m == December)
                || (y == 2009 && d == 2 && m == January)
                || (y == 2011 && d == 3 && m == January)
                || (y == 2012 && (d == 2 || d == 3) && m == January)
                || (y == 2013 && d <= 3 && m == January)
                || (y == 2014 && d == 1 && m == January)
                || (y == 2015 && d <= 3 && m == January)
                || (y == 2017 && d == 2 && m == January)
                || (y == 2018 && d == 1 && m == January)
                || (y == 2018 && d == 31 && m == December)
                || (y == 2019 && d == 1 && m == January)
                || (y == 2020 && d == 1 && m == January)
                || (y == 2021 && d == 1 && m == January)
                || (y == 2022 && d == 3 && m == January)
                || (y == 2023 && d == 2 && m == January)
                || (y == 2026 && (d == 1 || d == 2) && m == January)
                // Chinese New Year
                || (y == 2004 && d >= 19 && d <= 28 && m == January)
                || (y == 2005 && d >= 7 && d <= 15 && m == February)
                || (y == 2006 && ((d >= 26 && m == January) || (d <= 3 && m == February)))
                || (y == 2007 && d >= 17 && d <= 25 && m == February)
                || (y == 2008 && d >= 6 && d <= 12 && m == February)
                || (y == 2009 && d >= 26 && d <= 30 && m == January)
                || (y == 2010 && d >= 15 && d <= 19 && m == February)
                || (y == 2011 && d >= 2 && d <= 8 && m == February)
                || (y == 2012 && d >= 23 && d <= 28 && m == January)
                || (y == 2013 && d >= 11 && d <= 15 && m == February)
                || (y == 2014 && d >= 31 && m == January)
                || (y == 2014 && d <= 6 && m == February)
                || (y == 2015 && d >= 18 && d <= 24 && m == February)
                || (y == 2016 && d >= 8 && d <= 12 && m == February)
                || (y == 2017 && ((d >= 27 && m == January) || (d <= 2 && m == February)))
                || (y == 2018 && (d >= 15 && d <= 21 && m == February))
                || (y == 2019 && d >= 4 && d <= 8 && m == February)
                || (y == 2020 && (d == 24 || (d >= 27 && d <= 31)) && m == January)
                || (y == 2021 && (d == 11 || d == 12 || d == 15 || d == 16 || d == 17) && m == February)
                || (y == 2022 && ((d == 31 && m == January) || (d <= 4 && m == February)))
                || (y == 2023 && d >= 23 && d <= 27 && m == January)
                || (y == 2024 && (d == 9 || (d >= 12 && d <= 16)) && m == February)
                || (y == 2025 && ((d >= 28 && d <= 31 && m == January) || (d >= 3 && d <= 4 && m == February)))
                || (y == 2026 && ((d >= 16 && d <= 20) || d == 23) && m == February)
                // Ching Ming Festival
                || (y <= 2008 && d == 4 && m == April)
                || (y == 2009 && d == 6 && m == April)
                || (y == 2010 && d == 5 && m == April)
                || (y == 2011 && d >= 3 && d <= 5 && m == April)
                || (y == 2012 && d >= 2 && d <= 4 && m == April)
                || (y == 2013 && d >= 4 && d <= 5 && m == April)
                || (y == 2014 && d == 7 && m == April)
                || (y == 2015 && d >= 5 && d <= 6 && m == April)
                || (y == 2016 && d == 4 && m == April)
                || (y == 2017 && d >= 3 && d <= 4 && m == April)
                || (y == 2018 && d >= 5 && d <= 6 && m == April)
                || (y == 2019 && d == 5 && m == April)
                || (y == 2020 && d == 6 && m == April)
                || (y == 2021 && d == 5 && m == April)
                || (y == 2022 && d >= 4 && d <= 5 && m == April)
                || (y == 2023 && d == 5 && m == April)
                || (y == 2024 && d >= 4 && d <= 5 && m == April)
                || (y == 2025 && d == 4 && m == April)
                || (y == 2026 && d == 6 && m == April)
                // Labor Day
                || (y <= 2007 && d >= 1 && d <= 7 && m == May)
                || (y == 2008 && d >= 1 && d <= 2 && m == May)
                || (y == 2009 && d == 1 && m == May)
                || (y == 2010 && d == 3 && m == May)
                || (y == 2011 && d == 2 && m == May)
                || (y == 2012 && ((d == 30 && m == April) || (d == 1 && m == May)))
                || (y == 2013 && ((d >= 29 && m == April) || (d == 1 && m == May)))
                || (y == 2014 && d >= 1 && d <= 3 && m == May)
                || (y == 2015 && d == 1 && m == May)
                || (y == 2016 && d >= 1 && d <= 2 && m == May)
                || (y == 2017 && d == 1 && m == May)
                || (y == 2018 && ((d == 30 && m == April) || (d == 1 && m == May)))
                || (y == 2019 && d >= 1 && d <= 3 && m == May)
                || (y == 2020 && (d == 1 || d == 4 || d == 5) && m == May)
                || (y == 2021 && (d == 3 || d == 4 || d == 5) && m == May)
                || (y == 2022 && d >= 2 && d <= 4 && m == May)
                || (y == 2023 && d >= 1 && d <= 3 && m == May)
                || (y == 2024 && d >= 1 && d <= 3 && m == May)
                || (y == 2025 && (d == 1 || d == 2 || d == 5) && m == May)
                || (y == 2026 && (d == 1 || d == 4 || d == 5) && m == May)
                // Tuen Ng Festival
                || (y <= 2008 && d == 9 && m == June)
                || (y == 2009 && (d == 28 || d == 29) && m == May)
                || (y == 2010 && d >= 14 && d <= 16 && m == June)
                || (y == 2011 && d >= 4 && d <= 6 && m == June)
                || (y == 2012 && d >= 22 && d <= 24 && m == June)
                || (y == 2013 && d >= 10 && d <= 12 && m == June)
                || (y == 2014 && d == 2 && m == June)
                || (y == 2015 && d == 22 && m == June)
                || (y == 2016 && d >= 9 && d <= 10 && m == June)
                || (y == 2017 && d >= 29 && d <= 30 && m == May)
                || (y == 2018 && d == 18 && m == June)
                || (y == 2019 && d == 7 && m == June)
                || (y == 2020 && d >= 25 && d <= 26 && m == June)
                || (y == 2021 && d == 14 && m == June)
                || (y == 2022 && d == 3 && m == June)
                || (y == 2023 && d >= 22 && d <= 23 && m == June)
                || (y == 2024 && d == 10 && m == June)
                || (y == 2025 && d == 2 && m == June)
                || (y == 2026 && d == 19 && m == June)
                // Mid-Autumn Festival
                || (y <= 2008 && d == 15 && m == September)
                || (y == 2010 && d >= 22 && d <= 24 && m == September)
                || (y == 2011 && d >= 10 && d <= 12 && m == September)
                || (y == 2012 && d == 30 && m == September)
                || (y == 2013 && d >= 19 && d <= 20 && m == September)
                || (y == 2014 && d == 8 && m == September)
                || (y == 2015 && d == 27 && m == September)
                || (y == 2016 && d >= 15 && d <= 16 && m == September)
                || (y == 2018 && d == 24 && m == September)
                || (y == 2019 && d == 13 && m == September)
                || (y == 2021 && (d == 20 || d == 21) && m == September)
                || (y == 2022 && d == 12 && m == September)
                || (y == 2023 && d == 29 && m == September)
                || (y == 2024 && d >= 16 && d <= 17 && m == September)
                || (y == 2026 && d == 25 && m == September)
                // National Day
                || (y <= 2007 && d >= 1 && d <= 7 && m == October)
                || (y == 2008 && ((d >= 29 && m == September) || (d <= 3 && m == October)))
                || (y == 2009 && d >= 1 && d <= 8 && m == October)
                || (y == 2010 && d >= 1 && d <= 7 && m == October)
                || (y == 2011 && d >= 1 && d <= 7 && m == October)
                || (y == 2012 && d >= 1 && d <= 7 && m == October)
                || (y == 2013 && d >= 1 && d <= 7 && m == October)
                || (y == 2014 && d >= 1 && d <= 7 && m == October)
                || (y == 2015 && d >= 1 && d <= 7 && m == October)
                || (y == 2016 && d >= 3 && d <= 7 && m == October)
                || (y == 2017 && d >= 2 && d <= 6 && m == October)
                || (y == 2018 && d >= 1 && d <= 5 && m == October)
                || (y == 2019 && d >= 1 && d <= 7 && m == October)
                || (y == 2020 && d >= 1 && d <= 2 && m == October)
                || (y == 2020 && d >= 5 && d <= 8 && m == October)
                || (y == 2021 && (d == 1 || d == 4 || d == 5 || d == 6 || d == 7) && m == October)
                || (y == 2022 && d >= 3 && d <= 7 && m == October)
                || (y == 2023 && d >= 2 && d <= 6 && m == October)
                || (y == 2024 && ((d >= 1 && d <= 4) || d == 7) && m == October)
                || (y == 2025 && ((d >= 1 && d <= 3) || (d >= 6 && d <= 8)) && m == October)
                || (y == 2026 && ((d >= 1 && d <= 2) || (d >= 5 && d <= 7)) && m == October)
                // 70th anniversary of the victory of anti-Japanese war
                || (y == 2015 && d >= 3 && d <= 4 && m == September)) {
            return false;
        }
        return true;
    }

    //
    // private inner classes
    //

    private final class SseImpl extends Impl {

        @Override
        public String name() {
            return "Shanghai stock exchange";
        }

        @Override
        public boolean isWeekend(final Weekday w) {
            return w == Saturday || w == Sunday;
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            return sseIsBusinessDay(date);
        }
    }

    private static final Set<Date> IB_WORKING_WEEKENDS = buildIbWorkingWeekends();

    private static Set<Date> buildIbWorkingWeekends() {
        return new HashSet<Date>(Arrays.asList(
                // 2005
                new Date(5, February, 2005),
                new Date(6, February, 2005),
                new Date(30, April, 2005),
                new Date(8, May, 2005),
                new Date(8, October, 2005),
                new Date(9, October, 2005),
                new Date(31, December, 2005),
                // 2006
                new Date(28, January, 2006),
                new Date(29, April, 2006),
                new Date(30, April, 2006),
                new Date(30, September, 2006),
                new Date(30, December, 2006),
                new Date(31, December, 2006),
                // 2007
                new Date(17, February, 2007),
                new Date(25, February, 2007),
                new Date(28, April, 2007),
                new Date(29, April, 2007),
                new Date(29, September, 2007),
                new Date(30, September, 2007),
                new Date(29, December, 2007),
                // 2008
                new Date(2, February, 2008),
                new Date(3, February, 2008),
                new Date(4, May, 2008),
                new Date(27, September, 2008),
                new Date(28, September, 2008),
                // 2009
                new Date(4, January, 2009),
                new Date(24, January, 2009),
                new Date(1, February, 2009),
                new Date(31, May, 2009),
                new Date(27, September, 2009),
                new Date(10, October, 2009),
                // 2010
                new Date(20, February, 2010),
                new Date(21, February, 2010),
                new Date(12, June, 2010),
                new Date(13, June, 2010),
                new Date(19, September, 2010),
                new Date(25, September, 2010),
                new Date(26, September, 2010),
                new Date(9, October, 2010),
                // 2011
                new Date(30, January, 2011),
                new Date(12, February, 2011),
                new Date(2, April, 2011),
                new Date(8, October, 2011),
                new Date(9, October, 2011),
                new Date(31, December, 2011),
                // 2012
                new Date(21, January, 2012),
                new Date(29, January, 2012),
                new Date(31, March, 2012),
                new Date(1, April, 2012),
                new Date(28, April, 2012),
                new Date(29, September, 2012),
                // 2013
                new Date(5, January, 2013),
                new Date(6, January, 2013),
                new Date(16, February, 2013),
                new Date(17, February, 2013),
                new Date(7, April, 2013),
                new Date(27, April, 2013),
                new Date(28, April, 2013),
                new Date(8, June, 2013),
                new Date(9, June, 2013),
                new Date(22, September, 2013),
                new Date(29, September, 2013),
                new Date(12, October, 2013),
                // 2014
                new Date(26, January, 2014),
                new Date(8, February, 2014),
                new Date(4, May, 2014),
                new Date(28, September, 2014),
                new Date(11, October, 2014),
                // 2015
                new Date(4, January, 2015),
                new Date(15, February, 2015),
                new Date(28, February, 2015),
                new Date(6, September, 2015),
                new Date(10, October, 2015),
                // 2016
                new Date(6, February, 2016),
                new Date(14, February, 2016),
                new Date(12, June, 2016),
                new Date(18, September, 2016),
                new Date(8, October, 2016),
                new Date(9, October, 2016),
                // 2017
                new Date(22, January, 2017),
                new Date(4, February, 2017),
                new Date(1, April, 2017),
                new Date(27, May, 2017),
                new Date(30, September, 2017),
                // 2018
                new Date(11, February, 2018),
                new Date(24, February, 2018),
                new Date(8, April, 2018),
                new Date(28, April, 2018),
                new Date(29, September, 2018),
                new Date(30, September, 2018),
                new Date(29, December, 2018),
                // 2019
                new Date(2, February, 2019),
                new Date(3, February, 2019),
                new Date(28, April, 2019),
                new Date(5, May, 2019),
                new Date(29, September, 2019),
                new Date(12, October, 2019),
                // 2020
                new Date(19, January, 2020),
                new Date(26, April, 2020),
                new Date(9, May, 2020),
                new Date(28, June, 2020),
                new Date(27, September, 2020),
                new Date(10, October, 2020),
                // 2021
                new Date(7, February, 2021),
                new Date(20, February, 2021),
                new Date(25, April, 2021),
                new Date(8, May, 2021),
                new Date(18, September, 2021),
                new Date(26, September, 2021),
                new Date(9, October, 2021),
                // 2022
                new Date(29, January, 2022),
                new Date(30, January, 2022),
                new Date(2, April, 2022),
                new Date(24, April, 2022),
                new Date(7, May, 2022),
                new Date(8, October, 2022),
                new Date(9, October, 2022),
                // 2023
                new Date(28, January, 2023),
                new Date(29, January, 2023),
                new Date(23, April, 2023),
                new Date(6, May, 2023),
                new Date(25, June, 2023),
                new Date(7, October, 2023),
                new Date(8, October, 2023),
                // 2024
                new Date(4, February, 2024),
                new Date(9, February, 2024),
                new Date(18, February, 2024),
                new Date(7, April, 2024),
                new Date(28, April, 2024),
                new Date(11, May, 2024),
                new Date(14, September, 2024),
                new Date(29, September, 2024),
                new Date(12, October, 2024),
                // 2025
                new Date(26, January, 2025),
                new Date(8, February, 2025),
                new Date(27, April, 2025),
                new Date(28, September, 2025),
                new Date(11, October, 2025),
                // 2026
                new Date(4, January, 2026),
                new Date(14, February, 2026),
                new Date(28, February, 2026),
                new Date(9, May, 2026),
                new Date(20, September, 2026),
                new Date(10, October, 2026)
        ));
    }

    private final class IbImpl extends Impl {

        @Override
        public String name() {
            return "China inter bank market";
        }

        @Override
        public boolean isWeekend(final Weekday w) {
            return w == Saturday || w == Sunday;
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            // If it is already an SSE business day, it must be an IB business day
            return sseIsBusinessDay(date) || IB_WORKING_WEEKENDS.contains(date);
        }
    }
}
