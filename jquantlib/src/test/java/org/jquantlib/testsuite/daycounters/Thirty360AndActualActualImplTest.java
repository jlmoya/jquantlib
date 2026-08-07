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

package org.jquantlib.testsuite.daycounters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates every {@code DayCounter::Impl} that C++ v1.43 hides inside
 * {@link Thirty360} and {@link ActualActual} against the
 * {@code time/daycounters/thirty360_actualactual} probe reference.
 * <p>
 * The C++ classes {@code Thirty360::{US,ISMA,EU,IT,ISDA,NASD}_Impl} and
 * {@code ActualActual::{ISMA,Old_ISMA,ISDA,AFB}_Impl} are private nested
 * classes, so JQuantLib realises them as private inner classes of the same two
 * public day counters, selected by the same {@code Convention} enum. That is
 * only a legitimate mapping if every convention's arithmetic matches C++, and
 * before this test the Italian and NASD conventions were exercised solely by
 * {@code DayCountersTest#testYearFraction2DateBulk} — a
 * {@code yearFraction}/{@code yearFractionToDate} round trip, which is
 * self-consistent for any monotone day count and so pinned nothing.
 * <p>
 * Tolerance tiers: {@code dayCount} is an integer and compared exactly;
 * {@code yearFraction} is compared at the tight tier (1e-14 absolute, which
 * for these values — all below ~5 in magnitude — is well inside 1e-12
 * relative). Both sides evaluate {@code n/360.0} or a short sum of such terms
 * from the same integers, so anything looser would hide a real divergence.
 *
 * @author Jose Moya
 */
public class Thirty360AndActualActualImplTest {

    private static final String GROUP = "time/daycounters/thirty360_actualactual";

    /** Tight tier: absolute, because every expected value here is O(1). */
    private static final double TIGHT_ABS = 1.0e-14;

    public Thirty360AndActualActualImplTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static Date parseDate(final String iso) {
        final int y = Integer.parseInt(iso.substring(0, 4));
        final int m = Integer.parseInt(iso.substring(5, 7));
        final int d = Integer.parseInt(iso.substring(8, 10));
        return new Date(d, Month.valueOf(m), y);
    }

    /** Replays every {(d1,d2) -> dayCount, yearFraction} row of one probe case. */
    private static void checkGrid(final String caseName, final DayCounter dc) {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();

        assertEquals(caseName + ": day-counter name", expected.getString("name"), dc.name());

        final JSONArray rows = expected.getJSONArray("rows");
        assertTrue(caseName + ": probe produced no rows", rows.length() > 0);
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.getJSONObject(i);
            final Date d1 = parseDate(row.getString("d1"));
            final Date d2 = parseDate(row.getString("d2"));
            final String where = caseName + " [" + row.getString("d1") + " -> " + row.getString("d2") + "]";

            assertEquals(where + " dayCount", row.getLong("dayCount"), dc.dayCount(d1, d2));
            assertEquals(where + " yearFraction",
                    row.getDouble("yearFraction"), dc.yearFraction(d1, d2), TIGHT_ABS);
        }
    }

    /**
     * An alias enumerator must route to the same C++ Impl as its canonical
     * sibling. The probe records the alias relation; here we assert the name
     * matches C++ and that the alias reproduces the canonical convention's
     * values over the whole grid.
     */
    private static void checkAlias(final String aliasCase, final DayCounter alias, final DayCounter canonical) {
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final JSONObject aliasExpected = (JSONObject) ref.getCase(aliasCase).expectedRaw();
        assertEquals(aliasCase + ": day-counter name", aliasExpected.getString("name"), alias.name());

        final String canonicalCase = aliasExpected.getString("aliasOf");
        final JSONObject canonicalExpected = (JSONObject) ref.getCase(canonicalCase).expectedRaw();
        final JSONArray rows = canonicalExpected.getJSONArray("rows");
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.getJSONObject(i);
            final Date d1 = parseDate(row.getString("d1"));
            final Date d2 = parseDate(row.getString("d2"));
            final String where = aliasCase + " [" + row.getString("d1") + " -> " + row.getString("d2") + "]";
            assertEquals(where + " dayCount", row.getLong("dayCount"), alias.dayCount(d1, d2));
            assertEquals(where + " yearFraction",
                    row.getDouble("yearFraction"), alias.yearFraction(d1, d2), TIGHT_ABS);
            // and the alias must agree with the canonical Java instance too
            assertEquals(where + " alias vs canonical",
                    canonical.yearFraction(d1, d2), alias.yearFraction(d1, d2), 0.0);
        }
    }

    // ------------------------------------------------------------------
    // Thirty360
    // ------------------------------------------------------------------

    /** C++ {@code Thirty360::US_Impl} (thirty360.hpp:97). */
    @Test
    public void testThirty360UsImpl() {
        QL.info("Testing Thirty360 USA (C++ US_Impl) against C++ v1.43...");
        checkGrid("thirty360_usa", new Thirty360(Thirty360.Convention.USA));
    }

    /** C++ {@code Thirty360::ISMA_Impl} (thirty360.hpp:102), reached via BondBasis. */
    @Test
    public void testThirty360IsmaImpl() {
        QL.info("Testing Thirty360 BondBasis/ISMA (C++ ISMA_Impl) against C++ v1.43...");
        checkGrid("thirty360_bond_basis", new Thirty360(Thirty360.Convention.BondBasis));
        checkAlias("thirty360_isma",
                new Thirty360(Thirty360.Convention.ISMA),
                new Thirty360(Thirty360.Convention.BondBasis));
    }

    /** C++ {@code Thirty360::EU_Impl} (thirty360.hpp:107). */
    @Test
    public void testThirty360EuImpl() {
        QL.info("Testing Thirty360 European (C++ EU_Impl) against C++ v1.43...");
        checkGrid("thirty360_european", new Thirty360(Thirty360.Convention.European));
        checkAlias("thirty360_eurobond_basis",
                new Thirty360(Thirty360.Convention.EurobondBasis),
                new Thirty360(Thirty360.Convention.European));
    }

    /**
     * C++ {@code Thirty360::IT_Impl} (thirty360.hpp:112). The Italian
     * convention had no dedicated test before this one: its only appearance
     * was in a round-trip sweep.
     */
    @Test
    public void testThirty360ItImpl() {
        QL.info("Testing Thirty360 Italian (C++ IT_Impl) against C++ v1.43...");
        checkGrid("thirty360_italian", new Thirty360(Thirty360.Convention.Italian));
    }

    /**
     * C++ {@code Thirty360::ISDA_Impl} (thirty360.hpp:117), both with and
     * without a termination date — the termination date suppresses the
     * last-of-February adjustment on {@code d2} for that one date only, so the
     * two constructions must be pinned separately.
     */
    @Test
    public void testThirty360IsdaImpl() {
        QL.info("Testing Thirty360 ISDA (C++ ISDA_Impl) against C++ v1.43...");
        checkGrid("thirty360_isda_no_termination", new Thirty360(Thirty360.Convention.ISDA));
        checkGrid("thirty360_isda_termination_20201231",
                new Thirty360(Thirty360.Convention.ISDA, new Date(31, Month.December, 2020)));
        checkAlias("thirty360_german_no_termination",
                new Thirty360(Thirty360.Convention.German),
                new Thirty360(Thirty360.Convention.ISDA));
    }

    /**
     * C++ {@code Thirty360::NASD_Impl} (thirty360.hpp:126). Like Italian, NASD
     * had no dedicated test before this one; its distinguishing branch is
     * {@code d2 == 31 && d1 < 30}, which rolls {@code d2} to the 1st of the
     * following month.
     */
    @Test
    public void testThirty360NasdImpl() {
        QL.info("Testing Thirty360 NASD (C++ NASD_Impl) against C++ v1.43...");
        checkGrid("thirty360_nasd", new Thirty360(Thirty360.Convention.NASD));
    }

    // ------------------------------------------------------------------
    // ActualActual
    // ------------------------------------------------------------------

    /** C++ {@code ActualActual::ISDA_Impl} (actualactual.hpp:78). */
    @Test
    public void testActualActualIsdaImpl() {
        QL.info("Testing ActualActual ISDA (C++ ISDA_Impl) against C++ v1.43...");
        checkGrid("actualactual_isda", new ActualActual(ActualActual.Convention.ISDA));
        checkAlias("actualactual_historical",
                new ActualActual(ActualActual.Convention.Historical),
                new ActualActual(ActualActual.Convention.ISDA));
        checkAlias("actualactual_actual365",
                new ActualActual(ActualActual.Convention.Actual365),
                new ActualActual(ActualActual.Convention.ISDA));
    }

    /** C++ {@code ActualActual::AFB_Impl} (actualactual.hpp:84). */
    @Test
    public void testActualActualAfbImpl() {
        QL.info("Testing ActualActual AFB (C++ AFB_Impl) against C++ v1.43...");
        checkGrid("actualactual_afb", new ActualActual(ActualActual.Convention.AFB));
        checkAlias("actualactual_euro",
                new ActualActual(ActualActual.Convention.Euro),
                new ActualActual(ActualActual.Convention.AFB));
    }

    /**
     * C++ {@code ActualActual::Old_ISMA_Impl} (actualactual.hpp:70) — the
     * reference-period implementation selected when no schedule is supplied.
     */
    @Test
    public void testActualActualOldIsmaImpl() {
        QL.info("Testing ActualActual ISMA without a schedule (C++ Old_ISMA_Impl) against C++ v1.43...");
        checkGrid("actualactual_isma_no_schedule", new ActualActual(ActualActual.Convention.ISMA));
        checkAlias("actualactual_bond_no_schedule",
                new ActualActual(ActualActual.Convention.Bond),
                new ActualActual(ActualActual.Convention.ISMA));
    }

    /**
     * C++ {@code Old_ISMA_Impl} again, this time with explicit reference
     * periods so the short-first-coupon, long-first-coupon and
     * accumulate-past-the-reference-end branches are reached — the plain grid
     * leaves all three unvisited because it passes a null reference period.
     */
    @Test
    public void testActualActualOldIsmaReferencePeriods() {
        QL.info("Testing ActualActual ISMA reference-period branches against C++ v1.43...");
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISMA);
        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final JSONObject expected =
                (JSONObject) ref.getCase("actualactual_isma_reference_periods").expectedRaw();
        assertEquals(expected.getString("name"), dc.name());

        final JSONArray rows = expected.getJSONArray("rows");
        assertTrue("probe produced no rows", rows.length() > 0);
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.getJSONObject(i);
            final Date d1 = parseDate(row.getString("d1"));
            final Date d2 = parseDate(row.getString("d2"));
            final Date refStart = row.isNull("refStart") ? new Date() : parseDate(row.getString("refStart"));
            final Date refEnd = row.isNull("refEnd") ? new Date() : parseDate(row.getString("refEnd"));
            assertEquals("act/act ISMA ref-period row " + i,
                    row.getDouble("yearFraction"),
                    dc.yearFraction(d1, d2, refStart, refEnd), TIGHT_ABS);
        }
    }

    /**
     * C++ {@code ActualActual::ISMA_Impl} (actualactual.hpp:57) — the
     * schedule-aware implementation, a different class from
     * {@code Old_ISMA_Impl} behind the same {@code ISMA} enumerator.
     */
    @Test
    public void testActualActualSchedIsmaImpl() {
        QL.info("Testing ActualActual ISMA with a schedule (C++ ISMA_Impl) against C++ v1.43...");
        checkSchedule("actualactual_isma_semiannual_schedule",
                new Date(1, Month.January, 2020), new Date(1, Month.January, 2023),
                new Period(6, TimeUnit.Months));
        checkSchedule("actualactual_isma_annual_schedule",
                new Date(15, Month.March, 2019), new Date(15, Month.March, 2024),
                new Period(1, TimeUnit.Years));
    }

    private static void checkSchedule(final String caseName, final Date start, final Date end,
            final Period tenor) {
        final Schedule schedule = new Schedule(start, end, tenor, new NullCalendar(),
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                org.jquantlib.time.DateGeneration.Rule.Backward, false,
                new Date(), new Date());
        final DayCounter dc = new ActualActual(ActualActual.Convention.ISMA, schedule);

        final ReferenceReader ref = ReferenceReader.load(GROUP);
        final JSONObject expected = (JSONObject) ref.getCase(caseName).expectedRaw();
        assertEquals(caseName + ": day-counter name", expected.getString("name"), dc.name());

        // The schedule the Java side built must be the schedule C++ built,
        // otherwise the yearFraction comparison below would be meaningless.
        final JSONArray scheduleDates = expected.getJSONArray("scheduleDates");
        assertEquals(caseName + ": schedule size", scheduleDates.length(), schedule.size());
        for (int i = 0; i < scheduleDates.length(); i++) {
            assertEquals(caseName + ": schedule date " + i,
                    parseDate(scheduleDates.getString(i)), schedule.date(i));
        }

        final JSONArray rows = expected.getJSONArray("rows");
        assertTrue(caseName + ": probe produced no rows", rows.length() > 0);
        for (int i = 0; i < rows.length(); i++) {
            final JSONObject row = rows.getJSONObject(i);
            final Date d1 = parseDate(row.getString("d1"));
            final Date d2 = parseDate(row.getString("d2"));
            assertEquals(caseName + " [" + row.getString("d1") + " -> " + row.getString("d2") + "]",
                    row.getDouble("yearFraction"), dc.yearFraction(d1, d2), TIGHT_ABS);
        }
    }
}
