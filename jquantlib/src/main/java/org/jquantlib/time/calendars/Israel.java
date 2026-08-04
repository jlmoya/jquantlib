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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Weekday;

import static org.jquantlib.time.Month.April;
import static org.jquantlib.time.Month.August;
import static org.jquantlib.time.Month.December;
import static org.jquantlib.time.Month.February;
import static org.jquantlib.time.Month.January;
import static org.jquantlib.time.Month.July;
import static org.jquantlib.time.Month.June;
import static org.jquantlib.time.Month.March;
import static org.jquantlib.time.Month.May;
import static org.jquantlib.time.Month.November;
import static org.jquantlib.time.Month.October;
import static org.jquantlib.time.Month.September;

/**
 * Israel calendar.
 * <p>
 * Faithful port of {@code ql/time/calendars/israel.{hpp,cpp}} from QuantLib
 * v1.42.1.
 *
 * <p>Due to the lack of reliable sources, the settlement calendar has the same
 * holidays as the Tel Aviv stock exchange (TASE).
 *
 * <p>Tabular holidays for TASE — Purim, Passover, Memorial Day, Independence
 * Day, Shavuot, Fast Day, Jewish New Year, Yom Kippur, Sukkoth, Simchat Torah
 * — are encoded for the years 2000-2050 (data from <http://www.tase.co.il>).
 *
 * @author Jose Moya
 */
public class Israel extends Calendar {

    /**
     * v1.43 changeover from the Friday+Saturday weekend to Saturday+Sunday for
     * TASE. C++ pins this exact date, not a year boundary.
     */
    private static final Date TASE_WEEKEND_SWITCH = new Date(5, Month.January, 2026);

    public Israel() {
        this(Market.TASE);
    }

    public Israel(final Market market) {
        switch (market) {
        case Settlement:
        case TASE:
            impl = new TelAvivImpl();
            break;
        case Telbor:
            impl = new TelborImpl();
            break;
        case SHIR:
            impl = new ShirImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    public enum Market {
        /**
         * Generic settlement calendar.
         *
         * @deprecated C++ QuantLib v1.43 removed {@code Settlement} from
         *             {@code Israel::Market} (the default is now {@link #TASE}).
         *             Retained for source compatibility and still behaving as
         *             {@code TASE}; use {@link #TASE} instead.
         */
        @Deprecated(forRemoval = true)
        Settlement,
        /** Tel-Aviv stock exchange calendar (Fri/Sat weekends pre-2026, Sat/Sun afterwards) */
        TASE,
        /** SHIR fixing calendar (Sat/Sun weekends) */
        SHIR,
        /** Telbor fixing calendar (Sat/Sun weekends). New in C++ QuantLib v1.43. */
        Telbor
    }

    //
    // Jewish holiday tables (2000-2050) shared by TASE and SHIR impls.
    //

    private static final Set<Date> PURIM = buildSet(
            new Date(21, March,    2000),
            new Date(9,  March,    2001),
            new Date(26, February, 2002),
            new Date(18, March,    2003),
            new Date(7,  March,    2004),
            new Date(25, March,    2005),
            new Date(14, March,    2006),
            new Date(4,  March,    2007),
            new Date(21, March,    2008),
            new Date(10, March,    2009),
            new Date(28, February, 2010),
            new Date(20, March,    2011),
            new Date(8,  March,    2012),
            new Date(24, February, 2013),
            new Date(16, March,    2014),
            new Date(5,  March,    2015),
            new Date(24, March,    2016),
            new Date(12, March,    2017),
            new Date(1,  March,    2018),
            new Date(21, March,    2019),
            new Date(10, March,    2020),
            new Date(26, February, 2021),
            new Date(17, March,    2022),
            new Date(7,  March,    2023),
            new Date(24, March,    2024),
            new Date(14, March,    2025),
            new Date(3,  March,    2026),
            new Date(23, March,    2027),
            new Date(12, March,    2028),
            new Date(1,  March,    2029),
            new Date(19, March,    2030),
            new Date(9,  March,    2031),
            new Date(26, February, 2032),
            new Date(15, March,    2033),
            new Date(5,  March,    2034),
            new Date(25, March,    2035),
            new Date(13, March,    2036),
            new Date(1,  March,    2037),
            new Date(21, March,    2038),
            new Date(10, March,    2039),
            new Date(28, February, 2040),
            new Date(17, March,    2041),
            new Date(6,  March,    2042),
            new Date(26, March,    2043),
            new Date(13, March,    2044),
            new Date(3,  March,    2045),
            new Date(22, March,    2046),
            new Date(12, March,    2047),
            new Date(28, February, 2048),
            new Date(18, March,    2049),
            new Date(8,  March,    2050)
    );

    private static final Set<Date> PASSOVER_1ST = buildSet(
            new Date(20, April, 2000),
            new Date(8,  April, 2001),
            new Date(28, March, 2002),
            new Date(17, April, 2003),
            new Date(6,  April, 2004),
            new Date(24, April, 2005),
            new Date(13, April, 2006),
            new Date(3,  April, 2007),
            new Date(20, April, 2008),
            new Date(9,  April, 2009),
            new Date(30, March, 2010),
            new Date(19, April, 2011),
            new Date(7,  April, 2012),
            new Date(26, March, 2013),
            new Date(15, April, 2014),
            new Date(4,  April, 2015),
            new Date(23, April, 2016),
            new Date(11, April, 2017),
            new Date(31, March, 2018),
            new Date(20, April, 2019),
            new Date(9,  April, 2020),
            new Date(28, March, 2021),
            new Date(16, April, 2022),
            new Date(6,  April, 2023),
            new Date(23, April, 2024),
            new Date(13, April, 2025),
            new Date(2,  April, 2026),
            new Date(22, April, 2027),
            new Date(11, April, 2028),
            new Date(31, March, 2029),
            new Date(18, April, 2030),
            new Date(8,  April, 2031),
            new Date(27, March, 2032),
            new Date(14, April, 2033),
            new Date(4,  April, 2034),
            new Date(24, April, 2035),
            new Date(12, April, 2036),
            new Date(31, March, 2037),
            new Date(20, April, 2038),
            new Date(9,  April, 2039),
            new Date(29, March, 2040),
            new Date(16, April, 2041),
            new Date(5,  April, 2042),
            new Date(25, April, 2043),
            new Date(12, April, 2044),
            new Date(2,  April, 2045),
            new Date(21, April, 2046),
            new Date(11, April, 2047),
            new Date(29, March, 2048),
            new Date(17, April, 2049),
            new Date(7,  April, 2050)
    );

    private static final Set<Date> INDEPENDENCE_DAY = buildSet(
            new Date(10, May,   2000),
            new Date(26, April, 2001),
            new Date(17, April, 2002),
            new Date(7,  May,   2003),
            new Date(27, April, 2004),
            new Date(12, May,   2005),
            new Date(3,  May,   2006),
            new Date(24, April, 2007),
            new Date(8,  May,   2008),
            new Date(29, April, 2009),
            new Date(20, April, 2010),
            new Date(10, May,   2011),
            new Date(26, April, 2012),
            new Date(16, April, 2013),
            new Date(6,  May,   2014),
            new Date(23, April, 2015),
            new Date(12, May,   2016),
            new Date(2,  May,   2017),
            new Date(19, April, 2018),
            new Date(9,  May,   2019),
            new Date(29, April, 2020),
            new Date(15, April, 2021),
            new Date(5,  May,   2022),
            new Date(26, April, 2023),
            new Date(14, May,   2024),
            new Date(1,  May,   2025),
            new Date(22, April, 2026),
            new Date(12, May,   2027),
            new Date(2,  May,   2028),
            new Date(19, April, 2029),
            new Date(8,  May,   2030),
            new Date(29, April, 2031),
            new Date(15, April, 2032),
            new Date(4,  May,   2033),
            new Date(25, April, 2034),
            new Date(15, May,   2035),
            new Date(1,  May,   2036),
            new Date(21, April, 2037),
            new Date(10, May,   2038),
            new Date(28, April, 2039),
            new Date(18, April, 2040),
            new Date(7,  May,   2041),
            new Date(24, April, 2042),
            new Date(14, May,   2043),
            new Date(3,  May,   2044),
            new Date(20, April, 2045),
            new Date(10, May,   2046),
            new Date(1,  May,   2047),
            new Date(16, April, 2048),
            new Date(6,  May,   2049),
            new Date(27, April, 2050)
    );

    private static final Set<Date> SHAVUOT = buildSet(
            new Date(9,  June, 2000),
            new Date(28, May,  2001),
            new Date(17, May,  2002),
            new Date(6,  June, 2003),
            new Date(26, May,  2004),
            new Date(13, June, 2005),
            new Date(2,  June, 2006),
            new Date(23, May,  2007),
            new Date(9,  June, 2008),
            new Date(29, May,  2009),
            new Date(19, May,  2010),
            new Date(8,  June, 2011),
            new Date(27, May,  2012),
            new Date(15, May,  2013),
            new Date(4,  June, 2014),
            new Date(24, May,  2015),
            new Date(12, June, 2016),
            new Date(31, May,  2017),
            new Date(20, May,  2018),
            new Date(9,  June, 2019),
            new Date(29, May,  2020),
            new Date(17, May,  2021),
            new Date(5,  June, 2022),
            new Date(26, May,  2023),
            new Date(12, June, 2024),
            new Date(2,  June, 2025),
            new Date(22, May,  2026),
            new Date(11, June, 2027),
            new Date(31, May,  2028),
            new Date(20, May,  2029),
            new Date(7,  June, 2030),
            new Date(28, May,  2031),
            new Date(16, May,  2032),
            new Date(3,  June, 2033),
            new Date(24, May,  2034),
            new Date(13, June, 2035),
            new Date(1,  June, 2036),
            new Date(20, May,  2037),
            new Date(9,  June, 2038),
            new Date(29, May,  2039),
            new Date(18, May,  2040),
            new Date(5,  June, 2041),
            new Date(25, May,  2042),
            new Date(14, June, 2043),
            new Date(1,  June, 2044),
            new Date(22, May,  2045),
            new Date(10, June, 2046),
            new Date(31, May,  2047),
            new Date(18, May,  2048),
            new Date(6,  June, 2049),
            new Date(27, May,  2050)
    );

    private static final Set<Date> FAST_DAY = buildSet(
            new Date(10, August, 2000),
            new Date(29, July,   2001),
            new Date(18, July,   2002),
            new Date(7,  August, 2003),
            new Date(27, July,   2004),
            new Date(14, August, 2005),
            new Date(3,  August, 2006),
            new Date(24, July,   2007),
            new Date(10, August, 2008),
            new Date(30, July,   2009),
            new Date(20, July,   2010),
            new Date(9,  August, 2011),
            new Date(29, July,   2012),
            new Date(16, July,   2013),
            new Date(5,  August, 2014),
            new Date(26, July,   2015),
            new Date(14, August, 2016),
            new Date(1,  August, 2017),
            new Date(22, July,   2018),
            new Date(11, August, 2019),
            new Date(30, July,   2020),
            new Date(18, July,   2021),
            new Date(7,  August, 2022),
            new Date(27, July,   2023),
            new Date(13, August, 2024),
            new Date(3,  August, 2025),
            new Date(23, July,   2026),
            new Date(12, August, 2027),
            new Date(1,  August, 2028),
            new Date(22, July,   2029),
            new Date(8,  August, 2030),
            new Date(29, July,   2031),
            new Date(18, July,   2032),
            new Date(4,  August, 2033),
            new Date(25, July,   2034),
            new Date(14, August, 2035),
            new Date(3,  August, 2036),
            new Date(21, July,   2037),
            new Date(10, August, 2038),
            new Date(31, July,   2039),
            new Date(19, July,   2040),
            new Date(6,  August, 2041),
            new Date(27, July,   2042),
            new Date(16, August, 2043),
            new Date(2,  August, 2044),
            new Date(23, July,   2045),
            new Date(12, August, 2046),
            new Date(1,  August, 2047),
            new Date(19, July,   2048),
            new Date(8,  August, 2049),
            new Date(28, July,   2050)
    );

    private static final Set<Date> NEW_YEARS_DAY = buildSet(
            new Date(30, September, 2000),
            new Date(17, September, 2001),
            new Date(7,  September, 2002),
            new Date(27, September, 2003),
            new Date(16, September, 2004),
            new Date(4,  October,   2005),
            new Date(23, September, 2006),
            new Date(13, September, 2007),
            new Date(30, September, 2008),
            new Date(19, September, 2009),
            new Date(9,  September, 2010),
            new Date(29, September, 2011),
            new Date(17, September, 2012),
            new Date(5,  September, 2013),
            new Date(25, September, 2014),
            new Date(14, September, 2015),
            new Date(3,  October,   2016),
            new Date(21, September, 2017),
            new Date(10, September, 2018),
            new Date(30, September, 2019),
            new Date(19, September, 2020),
            new Date(7,  September, 2021),
            new Date(26, September, 2022),
            new Date(16, September, 2023),
            new Date(3,  October,   2024),
            new Date(23, September, 2025),
            new Date(12, September, 2026),
            new Date(2,  October,   2027),
            new Date(21, September, 2028),
            new Date(10, September, 2029),
            new Date(28, September, 2030),
            new Date(18, September, 2031),
            new Date(6,  September, 2032),
            new Date(24, September, 2033),
            new Date(14, September, 2034),
            new Date(4,  October,   2035),
            new Date(22, September, 2036),
            new Date(10, September, 2037),
            new Date(30, September, 2038),
            new Date(19, September, 2039),
            new Date(8,  September, 2040),
            new Date(26, September, 2041),
            new Date(15, September, 2042),
            new Date(5,  October,   2043),
            new Date(22, September, 2044),
            new Date(12, September, 2045),
            new Date(1,  October,   2046),
            new Date(21, September, 2047),
            new Date(8,  September, 2048),
            new Date(27, September, 2049),
            new Date(17, September, 2050)
    );

    private static Set<Date> buildSet(final Date... dates) {
        return new HashSet<>(Arrays.asList(dates));
    }

    private static boolean isPurim(final Date d)            { return PURIM.contains(d); }
    private static boolean isPassover1st(final Date d)      { return PASSOVER_1ST.contains(d); }
    private static boolean isIndependenceDay(final Date d)  { return INDEPENDENCE_DAY.contains(d); }
    private static boolean isMemorialDay(final Date d)      { return isIndependenceDay(d.add(1)); }
    private static boolean isShavuot(final Date d)          { return SHAVUOT.contains(d); }
    private static boolean isFastDay(final Date d)          { return FAST_DAY.contains(d); }
    private static boolean isNewYearsDay(final Date d)      { return NEW_YEARS_DAY.contains(d); }
    private static boolean isYomKippur(final Date d)        { return isNewYearsDay(d.sub(9)); }
    private static boolean isSukkot(final Date d)           { return isYomKippur(d.sub(5)); }
    private static boolean isSimchatTorah(final Date d)     { return isSukkot(d.sub(7)); }

    //
    // private final inner classes
    //

    private final class TelAvivImpl extends Impl {

        @Override
        public String name() {
            return "Tel Aviv stock exchange";
        }

        /**
         * v1.43: TASE reports the post-switch Saturday+Sunday weekend from the
         * weekday-only query; the date-dependent rule lives in
         * {@link #isBusinessDay(Date)} below, mirroring C++
         * {@code TelAvivImpl::isWeekend}.
         */
        @Override
        public boolean isWeekend(final Weekday w) {
            return w == Weekday.Saturday || w == Weekday.Sunday;
        }

        /**
         * v1.43 moved the Tel-Aviv weekend from Friday+Saturday to
         * Saturday+Sunday, pinned to an exact changeover date rather than a
         * year boundary (C++ {@code TelAvivImpl::isBusinessDay}).
         */
        private boolean isWeekendOn(final Date date) {
            final Weekday w = date.weekday();
            if (date.compareTo(TASE_WEEKEND_SWITCH) >= 0) {
                return w == Weekday.Saturday || w == Weekday.Sunday;
            }
            return w == Weekday.Friday || w == Weekday.Saturday;
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final int y = date.year();

            return !(isWeekendOn(date)
                    || isPurim(date)
                    || (y <= 2020 && isPassover1st(date.add(1))) // Eve of Passover, until 2020
                    || isPassover1st(date)
                    || isPassover1st(date.sub(5)) // Eve of Passover VII, until 2020
                    || isPassover1st(date.sub(6)) // Passover VII
                    || isMemorialDay(date)
                    || isIndependenceDay(date)
                    || (y <= 2020 && isShavuot(date.add(1))) // Eve of Shavuot, until 2020
                    || isShavuot(date)
                    || isFastDay(date)
                    || (y <= 2019 && isNewYearsDay(date.add(1))) // Eve of new year, until 2019
                    || isNewYearsDay(date)
                    || isNewYearsDay(date.sub(1)) // 2nd day of new year
                    || isYomKippur(date.add(1)) // Eve of Yom Kippur
                    || isYomKippur(date)
                    || isSukkot(date.add(1)) // Eve of Sukkot
                    || isSukkot(date)
                    || isSimchatTorah(date.add(1)) // Eve of Simchat Torah
                    || isSimchatTorah(date));
        }
    }

    private final class ShirImpl extends WesternImpl {

        @Override
        public String name() {
            return "SHIR fixing calendar";
        }

        /**
         * SHIR uses a Saturday+Sunday weekend: C++ declares
         * {@code class Israel::ShirImpl final : public Calendar::WesternImpl}
         * in both v1.42.1 and v1.43, and {@code WesternImpl::isWeekend} is
         * Saturday+Sunday. This previously returned Friday+Saturday, which was
         * wrong in both versions.
         */
        @Override
        public boolean isWeekend(final Weekday w) {
            return w == Weekday.Saturday || w == Weekday.Sunday;
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final int dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();

            if (isWeekend(w)
                    || isPurim(date)
                    || isPurim(date.sub(1)) // Purim (Jerusalem)
                    || isPassover1st(date.add(1)) // Eve of Passover
                    || isPassover1st(date)
                    || isPassover1st(date.sub(6)) // Last day of Passover
                    || isIndependenceDay(date)
                    || isShavuot(date)
                    || isFastDay(date)
                    || isNewYearsDay(date.add(1)) // Eve of new year
                    || isNewYearsDay(date)
                    || isNewYearsDay(date.sub(1)) // 2nd day of new year
                    || isYomKippur(date.add(1)) // Eve of Yom Kippur
                    || isYomKippur(date)
                    || isSukkot(date)
                    || isSimchatTorah(date)
                    // one-off closings
                    || (d == 27 && m == February && y == 2024) // Municipal elections
                    // holidays abroad
                    || (d == 1 && m == January) // Western New Year's day
                    || dd == easterMonday(y) - 3 // Good Friday
                    || (d >= 25 && w == Weekday.Monday && m == May && y != 2022) // Spring Bank Holiday
                    || (d == 3 && m == June && y == 2022)
                    || (d == 25 && m == December) // Christmas
                    || (d == 26 && m == December) // Boxing day
                    // other days when fixings were not published
                    || (d == 1 && m == November && y == 2022)
                    || (d == 2 && m == January && y == 2023)
                    || (d == 10 && m == April && y == 2023)) {
                return false;
            }
            return true;
        }
    }
    /**
     * Telbor fixing calendar, new in C++ QuantLib v1.43
     * ({@code ql/time/calendars/israel.cpp}, {@code Israel::TelborImpl}).
     * <p>
     * Distinct from both siblings: it keeps a Saturday+Sunday weekend like SHIR, but observes a different holiday set
     * — notably Shushan Purim, Passover VII, Simchat Torah and a set of one-off election and abroad-holiday closings
     * that SHIR does not share.
     */
    private final class TelborImpl extends Impl {

        @Override
        public String name() {
            return "Telbor fixing calendar";
        }

        @Override
        public boolean isWeekend(final Weekday w) {
            return w == Weekday.Saturday || w == Weekday.Sunday;
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();

            if (isWeekend(w)
                    // New Year's Day
                    || (d == 1 && m == Month.January)
                    // General Elections
                    || (((d == 9 && m == Month.April) || (d == 17 && m == Month.September)) && y == 2019)
                    || (d == 2 && m == Month.March && y == 2020)
                    // Holiday abroad
                    || (((d == 22 && m == Month.April) || (d == 27 && m == Month.May)) && y == 2019)
                    || ((((d == 10 || d == 13) && m == Month.April)
                            || ((d == 8 || d == 25) && m == Month.May)) && y == 2020)
                    // Purim
                    || isPurim(date)
                    || isPurim(date.sub(1)) // Shushan Purim
                    // Passover I and Passover VII
                    || isPassover1st(date.add(1)) // Eve of Passover
                    || isPassover1st(date)
                    || isPassover1st(date.sub(6)) // Passover VII
                    // Israel Independence Day
                    || isIndependenceDay(date)
                    // Feast of Shavuot (Pentecost)
                    || isShavuot(date)
                    // Fast of Ninth of Av
                    || isFastDay(date)
                    // Jewish New Year (Rosh Hashanah)
                    || isNewYearsDay(date)
                    || isNewYearsDay(date.sub(1)) // 2nd day of new year
                    // Day of Atonement (Yom Kippur)
                    || isYomKippur(date)
                    // First Day of Sukkot (Tabernacles)
                    || isSukkot(date)
                    // Rejoicing of the Law Festival (Simchat Torah)
                    || isSimchatTorah(date)
                    // last Monday of May (Spring Bank Holiday)
                    || (d >= 25 && w == Weekday.Monday && m == Month.May && y != 2002 && y != 2012)
                    // Christmas
                    || (d == 25 && m == Month.December)
                    // Day of Goodwill (Boxing Day)
                    || (d == 26 && m == Month.December && y >= 2000 && y != 2020)) {
                return false;
            }
            return true;
        }
    }
}
