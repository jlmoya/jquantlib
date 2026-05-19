/*
 Copyright (C) 2008 Srinivas Hasti
 Copyright (C) 2008 Dominik Holenstein

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

/**
 * United States calendars <br> Public holidays (see: http://www.opm.gov/fedhol/):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, JANUARY 1st (possibly moved to Monday if actually on
 * Sunday, or to Friday if on Saturday)</li>
 * <li>Martin Luther King's birthday, third Monday in JANUARY</li>
 * <li>Presidents' Day (a.k.a. Washington's birthday), third Monday in February</li>
 * <li>Memorial Day, last Monday in May</li>
 * <li>Independence Day, July 4th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * <li>Labor Day, first Monday in September</li>
 * <li>Columbus Day, second Monday in October</li>
 * <li>Veterans' Day, November 11th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * <li>Thanksgiving Day, fourth Thursday in November</li>
 * <li>Christmas, December 25th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * </ul>
 *
 * Holidays for the stock exchange (data from http://www.nyse.com):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, JANUARY 1st (possibly moved to Monday if actually on
 * Sunday)</li>
 * <li>Martin Luther King's birthday, third Monday in JANUARY (since 1998)</li>
 * <li>Presidents' Day (a.k.a. Washington's birthday), third Monday in February</li>
 * <li>Good Friday</li>
 * <li>Memorial Day, last Monday in May</li>
 * <li>Independence Day, July 4th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * <li>Labor Day, first Monday in September</li>
 * <li>Thanksgiving Day, fourth Thursday in November</li>
 * <li>Presidential election day, first Tuesday in November of election years
 * (until 1980)</li>
 * <li>Christmas, December 25th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * <li>Special historic closings (see http://www.nyse.com/pdfs/closings.pdf)</li>
 * </ul>
 *
 * Holidays for the government bond market (data from
 * http://www.bondmarkets.com):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, JANUARY 1st (possibly moved to Monday if actually on
 * Sunday)</li>
 * <li>Martin Luther King's birthday, third Monday in JANUARY</li>
 * <li>Presidents' Day (a.k.a. Washington's birthday), third Monday in February</li>
 * <li>Good Friday</li>
 * <li>Memorial Day, last Monday in May</li>
 * <li>Independence Day, July 4th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * <li>Labor Day, first Monday in September</li>
 * <li>Columbus Day, second Monday in October</li>
 * <li>Veterans' Day, November 11th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * <li>Thanksgiving Day, fourth Thursday in November</li>
 * <li>Christmas, December 25th (moved to Monday if Sunday or Friday if
 * Saturday)</li>
 * </ul>
 *
 * Holidays for the North American Energy Reliability Council (data from
 * http://www.nerc.com/~oc/offpeaks.html):
 * <ul>
 * <li>Saturdays</li>
 * <li>Sundays</li>
 * <li>New Year's Day, JANUARY 1st (possibly moved to Monday if actually on
 * Sunday)</li>
 * <li>Memorial Day, last Monday in May</li>
 * <li>Independence Day, July 4th (moved to Monday if Sunday)</li>
 * <li>Labor Day, first Monday in September</li>
 * <li>Thanksgiving Day, fourth Thursday in November</li>
 * <li>Christmas, December 25th (moved to Monday if Sunday)</li>
 * </ul>
 *
 * @author Srinivas Hasti
 * @author Zahid Hussain
 * @category calendars
 */

@QualityAssurance( quality = Quality.Q3_DOCUMENTATION, version = Version.V097, reviewers = { "Zahid Hussain" } )

public class UnitedStates extends Calendar {

    public UnitedStates() {
        this(Market.SETTLEMENT);
    }

    //
    // public constructors
    //

    public UnitedStates(final Market market) {
        switch ( market ) {
        case SETTLEMENT:
            impl = new SettlementImpl();
            break;
        case NYSE:
            impl = new NyseImpl();
            break;
        case GOVERNMENTBOND:
            impl = new GovernmentBondImpl();
            break;
        case NERC:
            impl = new NercImpl();
            break;
        case FederalReserve:
            impl = new FederalReserveImpl();
            break;
        case LiborImpact:
            impl = new LiborImpactImpl();
            break;
        case SOFR:
            impl = new SofrImpl();
            break;
        default:
            throw new LibraryException(UNKNOWN_MARKET);
        }
    }

    private static boolean isWashingtonBirthday(final int d, final Month m, final int y, final Weekday w) {
        if ( y >= 1971 ) {
            // third Monday in February
            return (d >= 15 && d <= 21) && w == Weekday.Monday && m == Month.February;
        } else {
            // February 22nd, possibly adjusted
            return (d == 22 || (d == 23 && w == Weekday.Monday) || (d == 21 && w == Weekday.Friday))
                    && m == Month.February;
        }
    }

    //
    // private final inner classes
    //

    private static boolean isMemorialDay(final int d, final Month m, final int y, final Weekday w) {
        if ( y >= 1971 ) {
            // last Monday in May
            return d >= 25 && w == Weekday.Monday && m == Month.May;
        } else {
            // May 30th, possibly adjusted
            return (d == 30 || (d == 31 && w == Weekday.Monday) || (d == 29 && w == Weekday.Friday)) && m == Month.May;
        }
    }

    private static boolean isVeteransDayNoSaturday(final int d, final Month m, final int y, final Weekday w) {
        if ( y <= 1970 || y >= 1978 ) {
            // November 11th, adjusted, but no Saturday->Friday
            return (d == 11 || (d == 12 && w == Weekday.Monday)) && m == Month.November;
        } else {
            // fourth Monday in October (1971-1977)
            return (d >= 22 && d <= 28) && w == Weekday.Monday && m == Month.October;
        }
    }

    /**
     * Juneteenth, declared 2021 and observed by exchanges from 2022 onward.
     *
     * @param moveToFriday when true (default), Saturday->Friday rollback is honored (matches C++ default param).
     *                     Federal Reserve passes false.
     */
    private static boolean isJuneteenth(final int d, final Month m, final int y, final Weekday w,
            final boolean moveToFriday) {
        return (d == 19 || (d == 20 && w == Weekday.Monday) || ((d == 18 && w == Weekday.Friday) && moveToFriday))
                && m == Month.June && y >= 2022;
    }

    /**
     * US calendars
     */
    public enum Market {
        SETTLEMENT,     // generic settlement calendar
        NYSE,           // New York stock exchange calendar
        GOVERNMENTBOND, // government-bond calendar
        NERC,           // off-peak days for NERC
        FederalReserve, // Federal Reserve Bankwire System (Phase 5g.5d)
        LiborImpact,    // Libor impact calendar (Phase Bug-Fix-5)
        SOFR            // SOFR fixing calendar (extends GovernmentBond with Good Friday closure; Phase 5e.5b-CFC-d)
    }

    private final class SettlementImpl extends WesternImpl {

        @Override
        public String name() {
            return "US settlement";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth(), dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            //                // New Year's Day (possibly moved to Monday if on Sunday)
            //                || ((d == 1 || (d == 2 && w == Weekday.Monday)) && m == Month.January)
            //                // Martin Luther King's birthday (third Monday in JANUARY)
            //                || ((d >= 15 && d <= 21) && w == Weekday.Monday && m == Month.January)
            //                // Washington's birthday (third Monday in Month.FEBRUARY)
            //                || ((d >= 15 && d <= 21) && w == Weekday.Monday && m == Month.February)
            //                // Good Weekday.FRIDAY
            //                || (dd == em-3)
            //                // Memorial Day (last Monday in Month.MAY)
            //                || (d >= 25 && w == Weekday.Monday && m == Month.May)
            //                // Independence Day (Monday if Sunday or Weekday.FRIDAY if Saturday)
            //                || ((d == 4 || (d == 5 && w == Weekday.Monday) ||
            //                     (d == 3 && w == Weekday.Friday)) && m == Month.July)
            //                // Labor Day (first Monday in Month.SEPTEMBER)
            //                || (d <= 7 && w == Weekday.Monday && m == Month.September)
            //                // Columbus Day (second Monday in October)
            //                || ((d >= 8 && d <= 14) && w == Weekday.Monday && m == Month.October)
            //                // Veteran's Day (Monday if Sunday or Weekday.FRIDAY if Saturday)
            //                || ((d == 11 || (d == 12 && w == Weekday.Monday) ||
            //                     (d == 10 && w == Weekday.Friday)) && m == Month.November)
            //                // Thanksgiving Day (fourth Weekday.THURSDAY in Month.NOVEMBER)
            //                || ((d >= 22 && d <= 28) && w == Weekday.Thursday && m == Month.November)
            //                // Christmas (Monday if Sunday or Weekday.FRIDAY if Saturday)
            //                || ((d == 25 || (d == 26 && w == Weekday.Monday) ||
            //                     (d == 24 && w == Weekday.Friday)) && m == Month.December))
            return !isWeekend(w)
                    // New Year's Day (possibly moved to Monday if on Sunday)
                    && ((d != 1 && (d != 2 || w != Weekday.Monday)) || m != Month.January)
                    // (or to Friday if on Saturday)
                    && (d != 31 || w != Weekday.Friday || m != Month.December)
                    // Martin Luther King's birthday (third Monday in January)
                    && ((d < 15 || d > 21) || w != Weekday.Monday || m != Month.January)
                    // Washington's birthday (third Monday in February)
                    && ((d < 15 || d > 21) || w != Weekday.Monday || m != Month.February)
                    // Memorial Day (last Monday in May)
                    && (d < 25 || w != Weekday.Monday || m != Month.May)
                    // Juneteenth (Monday if Sunday or Friday if Saturday) — observed
                    // since 2022 by federal holiday and US settlement market. Mirrors
                    // C++ unitedstates.cpp:151-152.
                    && !isJuneteenth(d, m, y, w, true)
                    // Independence Day (Monday if Sunday or Friday if Saturday)
                    && ((d != 4 && (d != 5 || w != Weekday.Monday) && (d != 3 || w != Weekday.Friday))
                    || m != Month.July)
                    // Labor Day (first Monday in September)
                    && (d > 7 || w != Weekday.Monday || m != Month.September)
                    // Columbus Day (second Monday in October)
                    && ((d < 8 || d > 14) || w != Weekday.Monday || m != Month.October)
                    // Veteran's Day (Monday if Sunday or Friday if Saturday)
                    && ((d != 11 && (d != 12 || w != Weekday.Monday) && (d != 10 || w != Weekday.Friday))
                    || m != Month.November)
                    // Thanksgiving Day (fourth Thursday in November)
                    && ((d < 22 || d > 28) || w != Weekday.Thursday || m != Month.November)
                    // Christmas (Monday if Sunday or Friday if Saturday)
                    && ((d != 25 && (d != 26 || w != Weekday.Monday) && (d != 24 || w != Weekday.Friday))
                    || m != Month.December);
        }
    }

    /**
     * US calendar with the Independence Day "Libor impact" exemption.
     *
     * <p>Mirrors C++ v1.42.1 ql/time/calendars/unitedstates.cpp
     * {@code UnitedStates::LiborImpactImpl::isBusinessDay}.
     *
     * <p>Per ICE LIBOR holiday calendars
     * (<https://www.theice.com/iba/libor>, <https://www.theice.com/marketdata/reports/170>), since 2015 a July 4
     * (Independence Day) observance falls back to the regular settlement rule only when it lands on a weekday. When it
     * would have been moved to Monday (Jul 5) or Friday (Jul 3), the day remains a Libor business day.
     *
     * <p>Phase Bug-Fix-5.
     */
    private final class LiborImpactImpl extends WesternImpl {

        private final SettlementImpl settlement = new SettlementImpl();

        @Override
        public String name() {
            return "US with Libor impact";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();
            // Since 2015 Independence Day only impacts Libor if it falls
            // on a weekday — i.e. the Saturday->Friday and Sunday->Monday
            // moves are NOT observed. Return true (business day) for those
            // moved-observance dates.
            if ( ((d == 5 && w == Weekday.Monday) || (d == 3 && w == Weekday.Friday)) && m == Month.July
                    && y >= 2015 ) {
                return true;
            }
            return settlement.isBusinessDay(date);
        }
    }

    private final class NyseImpl extends WesternImpl {

        @Override
        public String name() {
            return "New York stock exchange";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth(), dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            if ( isWeekend(w)
                    // New Year's Day (possibly moved to Monday if on Sunday)
                    || ((d == 1 || (d == 2 && w == Weekday.Monday)) && m == Month.January)
                    // Washington's birthday (third Monday in Month.FEBRUARY)
                    || ((d >= 15 && d <= 21) && w == Weekday.Monday && m == Month.February)
                    // Good Weekday.FRIDAY
                    || (dd == em - 3)
                    // Memorial Day (last Weekday.MONDAY in Month.MAY)
                    || (d >= 25 && w == Weekday.Monday && m == Month.May)
                    // Juneteenth (Monday if Sunday or Friday if Saturday) — observed
                    // since 2022 by NYSE. Mirrors C++ unitedstates.cpp:199-200.
                    || isJuneteenth(d, m, y, w, true)
                    // Independence Day (Weekday.MONDAY if Sunday or Weekday.FRIDAY if Saturday)
                    || ((d == 4 || (d == 5 && w == Weekday.Monday) || (d == 3 && w == Weekday.Friday))
                    && m == Month.July)
                    // Labor Day (first Weekday.MONDAY in Month.SEPTEMBER)
                    || (d <= 7 && w == Weekday.Monday && m == Month.September)
                    // Thanksgiving Day (fourth Weekday.THURSDAY in Month.NOVEMBER)
                    || ((d >= 22 && d <= 28) && w == Weekday.Thursday && m == Month.November)
                    // Christmas (Weekday.MONDAY if Sunday or Weekday.FRIDAY if Saturday)
                    || ((d == 25 || (d == 26 && w == Weekday.Monday) || (d == 24 && w == Weekday.Friday))
                    && m == Month.December) )
                return false;

            if ( y >= 1998 ) {
                // Martin Luther King's birthday (third Weekday.MONDAY in JANUARY)
                return ((d < 15 || d > 21) || w != Weekday.Monday || m != Month.January)
                        // President Reagan's funeral
                        && (y != 2004 || m != Month.June || d != 11)
                        // Month.SEPTEMBER 11, 2001
                        && (y != 2001 || m != Month.September || (11 > d || d > 14))
                        // President Ford's funeral
                        && (y != 2007 || m != Month.January || d != 2);
            } else // Nixon's funeral
                if ( y <= 1980 ) {
                    // Presidential election days
                    return ((y % 4 != 0) || m != Month.November || d > 7 || w != Weekday.Tuesday)
                            // 1977 Blackout
                            && (y != 1977 || m != Month.July || d != 14)
                            // Funeral of former President Lyndon B. Johnson.
                            && (y != 1973 || m != Month.January || d != 25)
                            // Funeral of former President Harry S. Truman
                            && (y != 1972 || m != Month.December || d != 28)
                            // National Day of Participation for the lunar exploration.
                            && (y != 1969 || m != Month.July || d != 21)
                            // Funeral of former President Eisenhower.
                            && (y != 1969 || m != Month.March || d != 31)
                            // Closed all day - heavy snow.
                            && (y != 1969 || m != Month.February || d != 10)
                            // Day after Independence Day.
                            && (y != 1968 || m != Month.July || d != 5)
                            // Month.JUNE 12-Dec. 31, 1968
                            // Four day week (closed on Wednesdays) - Paperwork Crisis
                            && (y != 1968 || dd < 163 || w != Weekday.Wednesday);
            } else
                    return y != 1994 || m != Month.April || d != 27;
        }
    }

    //
    // private helpers — shared rules used by the FederalReserve calendar.
    //
    // Mirrors the anonymous-namespace helpers in C++ v1.42.1
    // ql/time/calendars/unitedstates.cpp.
    //

    private class GovernmentBondImpl extends WesternImpl {

        @Override
        public String name() {
            return "US government bond market";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth(), dd = date.dayOfYear();
            final Month m = date.month();
            final int y = date.year();
            final int em = easterMonday(y);
            return !isWeekend(w)
                    // New Year's Day (possibly moved to Weekday.MONDAY if on Sunday)
                    && ((d != 1 && (d != 2 || w != Weekday.Monday)) || m != Month.January)
                    // Martin Luther King's birthday (third Weekday.MONDAY in Month.JANUARY)
                    && ((d < 15 || d > 21) || w != Weekday.Monday || m != Month.January)
                    // Washington's birthday (third Weekday.MONDAY in Month.FEBRUARY)
                    && ((d < 15 || d > 21) || w != Weekday.Monday || m != Month.February)
                    // Good Friday — full closure for SOFR (used by the Sofr index)
                    // matches this rule. The C++ GovernmentBond market actually
                    // applies an NFP-release-date carve-out (early close, not full
                    // close, when Good Friday's d <= 7); see C++
                    // unitedstates.cpp:287-298. Java does NOT yet model that
                    // distinction — both calendars treat Good Friday as a full
                    // market close. This is consistent with Java's Sofr index using
                    // GovernmentBond as its fixing calendar (a documented divergence
                    // from C++ SOFR's Good Friday-always-closed semantics, but
                    // bit-exact on the date set tested in v1.42.1's overnight
                    // pricing test fixtures).
                    && (dd != em - 3)
                    // Memorial Day (last Monday in Month.MAY)
                    && (d < 25 || w != Weekday.Monday || m != Month.May)
                    // Juneteenth (Monday if Sunday or Friday if Saturday) — observed
                    // since 2022 by the US government bond market. Mirrors C++
                    // unitedstates.cpp:301-302.
                    && !isJuneteenth(d, m, y, w, true)
                    // Independence Day (Monday if Sunday or Weekday.FRIDAY if Saturday)
                    && ((d != 4 && (d != 5 || w != Weekday.Monday) && (d != 3 || w != Weekday.Friday))
                    || m != Month.July)
                    // Labor Day (first Monday in Month.SEPTEMBER)
                    && (d > 7 || w != Weekday.Monday || m != Month.September)
                    // Columbus Day (second Monday in October)
                    && ((d < 8 || d > 14) || w != Weekday.Monday || m != Month.October)
                    // Veteran's Day (Monday if Sunday or Weekday.FRIDAY if Saturday)
                    && ((d != 11 && (d != 12 || w != Weekday.Monday) && (d != 10 || w != Weekday.Friday))
                    || m != Month.November)
                    // Thanksgiving Day (fourth Weekday.THURSDAY in Month.NOVEMBER)
                    && ((d < 22 || d > 28) || w != Weekday.Thursday || m != Month.November)
                    // Christmas (Monday if Sunday or Weekday.FRIDAY if Saturday)
                    && ((d != 25 && (d != 26 || w != Weekday.Monday) && (d != 24 || w != Weekday.Friday))
                    || m != Month.December);
        }
    }

    /**
     * SOFR fixing calendar — extends {@link GovernmentBondImpl} with full Good Friday closure (no NFP exception).
     * Mirrors C++ v1.42.1 {@code UnitedStates::SofrImpl::isBusinessDay} (unitedstates.cpp:332-344).
     *
     * <p>From the C++ comment block: "so far (that is, up to 2023 at the
     * time of this change) SOFR never fixed on Good Friday. We're extrapolating that pattern. This might change if a
     * fixing on Good Friday occurs in future years."
     *
     * <p>Phase 5e.5b-CFC-d.
     */
    private final class SofrImpl extends GovernmentBondImpl {

        @Override
        public String name() {
            return "SOFR fixing calendar";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final int dY = date.dayOfYear();
            final int y = date.year();
            // Good Friday — full closure (no NFP exception); GovernmentBond's
            // NFP carve-out doesn't apply for SOFR fixings.
            if ( dY == easterMonday(y) - 3 ) {
                return false;
            }
            return super.isBusinessDay(date);
        }
    }

    private final class NercImpl extends WesternImpl {

        @Override
        public String name() {
            return "North American Energy Reliability Council";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            return !isWeekend(w)
                    // New Year's Day (possibly moved to Monday if on Sunday)
                    && ((d != 1 && (d != 2 || w != Weekday.Monday)) || m != Month.January)
                    // Memorial Day (last Monday in Month.MAY)
                    && (d < 25 || w != Weekday.Monday || m != Month.May)
                    // Independence Day (Monday if Sunday)
                    && ((d != 4 && (d != 5 || w != Weekday.Monday)) || m != Month.July)
                    // Labor Day (first Monday in Month.SEPTEMBER)
                    && (d > 7 || w != Weekday.Monday || m != Month.September)
                    // Thanksgiving Day (fourth Weekday.THURSDAY in Month.NOVEMBER)
                    && ((d < 22 || d > 28) || w != Weekday.Thursday || m != Month.November)
                    // Christmas (Monday if Sunday)
                    && ((d != 25 && (d != 26 || w != Weekday.Monday)) || m != Month.December);
        }
    }

    /**
     * Federal Reserve Bankwire System holidays.
     *
     * <p>Mirrors C++ v1.42.1 ql/time/calendars/unitedstates.cpp
     * {@code UnitedStates::FederalReserveImpl::isBusinessDay}. Holiday set per
     * <https://www.frbservices.org/about/holiday-schedules>:
     * <ul>
     *   <li>Saturdays</li>
     *   <li>Sundays</li>
     *   <li>New Year's Day, January 1st (moved to Monday if Sunday)</li>
     *   <li>Martin Luther King's birthday, third Monday in January (since 1983)</li>
     *   <li>Washington's birthday (third Monday in February since 1971; Feb 22nd otherwise)</li>
     *   <li>Memorial Day (last Monday in May since 1971; May 30th otherwise)</li>
     *   <li>Juneteenth (Monday if Sunday) — observed since 2022, no Saturday->Friday move</li>
     *   <li>Independence Day, July 4th (moved to Monday if Sunday only — no Saturday->Friday)</li>
     *   <li>Labor Day, first Monday in September</li>
     *   <li>Columbus Day, second Monday in October (since 1971)</li>
     *   <li>Veterans' Day (Monday if Sunday only — no Saturday->Friday)</li>
     *   <li>Thanksgiving, fourth Thursday in November</li>
     *   <li>Christmas, December 25th (moved to Monday if Sunday only)</li>
     * </ul>
     *
     * <p>Phase 5g.5d.
     */
    private final class FederalReserveImpl extends WesternImpl {

        @Override
        public String name() {
            return "Federal Reserve Bankwire System";
        }

        @Override
        public boolean isBusinessDay(final Date date) {
            final Weekday w = date.weekday();
            final int d = date.dayOfMonth();
            final Month m = date.month();
            final int y = date.year();
            return !isWeekend(w)
                    // New Year's Day (possibly moved to Monday if on Sunday)
                    && ((d != 1 && (d != 2 || w != Weekday.Monday)) || m != Month.January)
                    // Martin Luther King's birthday (third Monday in January, since 1983)
                    && ((d < 15 || d > 21) || w != Weekday.Monday || m != Month.January || y < 1983)
                    // Washington's birthday (third Monday in February since 1971; Feb 22 adjusted otherwise)
                    && !isWashingtonBirthday(d, m, y, w)
                    // Memorial Day (last Monday in May since 1971; May 30 adjusted otherwise)
                    && !isMemorialDay(d, m, y, w)
                    // Juneteenth (Monday if Sunday) — moveToFriday=false for Federal Reserve
                    && !isJuneteenth(d, m, y, w, false)
                    // Independence Day (Monday if Sunday) — no Saturday->Friday move
                    && ((d != 4 && (d != 5 || w != Weekday.Monday)) || m != Month.July)
                    // Labor Day (first Monday in September)
                    && (d > 7 || w != Weekday.Monday || m != Month.September)
                    // Columbus Day (second Monday in October, since 1971)
                    && ((d < 8 || d > 14) || w != Weekday.Monday || m != Month.October || y < 1971)
                    // Veterans' Day (Monday if Sunday) — no Saturday->Friday move
                    && !isVeteransDayNoSaturday(d, m, y, w)
                    // Thanksgiving Day (fourth Thursday in November)
                    && ((d < 22 || d > 28) || w != Weekday.Thursday || m != Month.November)
                    // Christmas (Monday if Sunday) — no Saturday->Friday move
                    && ((d != 25 && (d != 26 || w != Weekday.Monday)) || m != Month.December);
        }
    }
}
