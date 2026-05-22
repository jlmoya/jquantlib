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

/*
 Copyright (C) 2003 RiskMap srl
 Copyright (C) 2006 Piter Dias

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.testsuite.daycounters;

import static java.lang.Math.abs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual364;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Actual366;
import org.jquantlib.daycounters.Actual36525;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.Business252;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.daycounters.Thirty365;
import org.jquantlib.math.Closeness;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Brazil;
import org.jquantlib.time.calendars.Canada;
import org.jquantlib.time.calendars.China;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Test Day Counters
 *
 * @author Richard Gomes
 * @author Daniel Kong
 *
 * <h2>Phase 1 cert D5-A-R4 status of v1.42.1 {@code test-suite/daycounters.cpp} tests</h2>
 *
 * The C++ file ({@code migration-harness/cpp/quantlib/test-suite/daycounters.cpp},
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}) contains 21 cases.
 * R2/R3 ported the four already covered above
 * ({@code testActualActual}, {@code testSimple}, {@code testOne},
 * {@code testBusiness252}, {@code testThirty360_BondBasis},
 * {@code testThirty360_EurobondBasis}, plus the {@code testActual360IncludeLastDay}
 * smoke test for the {@code Actual360(includeLastDay)} variant).
 * R4 evaluated the remaining 15 cases and finds <b>all BLOCKED</b> because
 * Java QuantLib lacks one or more underlying production classes
 * (Phase 2 A4 triggers per design §7.3 — additions outside the 61 existing
 * packages). Specifically:
 *
 * <ul>
 *  <li>{@code testActualActualIsma} / {@code testActualActualWithSemiannualSchedule} /
 *      {@code testActualActualWithAnnualSchedule} / {@code testActualActualWithSchedule} /
 *      {@code testActualActualOutOfScheduleRange} — require
 *      {@code ActualActual(Convention, Schedule)} constructor and a
 *      schedule-aware {@code ImplISMA}; absent from
 *      {@link org.jquantlib.daycounters.ActualActual} (which only supports
 *      reference-period overloads).</li>
 *  <li>{@code testThirty365} — requires {@code org.jquantlib.daycounters.Thirty365}
 *      class (absent).</li>
 *  <li>{@code testThirty360_USA} — Java's
 *      {@link org.jquantlib.daycounters.Thirty360.Convention#USA} routes to
 *      {@code Impl_US}, which is wired identically to
 *      {@link org.jquantlib.daycounters.Thirty360.Convention#BondBasis}
 *      (no end-of-February rule). The C++ {@code US_Impl::dayCount} (see
 *      {@code ql/time/daycounters/thirty360.cpp:57-73}) applies an end-of-Feb
 *      adjustment that {@code BondBasis (ISMA_Impl)} omits, producing different
 *      numbers (e.g. {@code 28-Feb-2006} to {@code 3-Mar-2006} = 3 days under
 *      USA, 5 days under BondBasis). Porting against the current Java enum
 *      mapping would assert against the wrong numbers; a separate
 *      {@code align(daycounters.Thirty360)} commit to split USA/BondBasis impls
 *      is required first.</li>
 *  <li>{@code testThirty360_ISDA} — requires
 *      {@link org.jquantlib.daycounters.Thirty360.Convention#ISDA} (absent;
 *      Java enum has only USA/BondBasis/European/EurobondBasis/Italian) plus a
 *      termination-date constructor.</li>
 *  <li>{@code testActual365_Canadian} — requires
 *      {@code Actual365Fixed::Canadian} enum branch (absent).</li>
 *  <li>{@code testIntraday} — <b>ACTIVATED (Phase 1.3 D5-D-intraday)</b>:
 *      ported to {@link #testIntraday()}; depends on the intraday-aware
 *      Date constructor (CFC-d-304) plus the new sub-day TimeUnits, and
 *      requires {@code Actual360} / {@code ActualActual.ISDA} to honour
 *      {@code Date.fractionOfDay()} (now done — both mirror C++
 *      {@code daysBetween} which adds the intraday term).</li>
 *  <li>{@code testAct366} / {@code testAct36525} — require
 *      {@code org.jquantlib.daycounters.Actual366} and
 *      {@code Actual36525} classes (absent).</li>
 *  <li>{@code testActualConsistency} — requires {@code Actual366}, {@code Actual364},
 *      {@code Actual36525}, plus {@code Actual360(true)} (the includeLastDay
 *      variant is present, but the rest are not).</li>
 *  <li>{@code testYearFraction2DateBulk} / {@code testYearFraction2DateRounding} —
 *      require the {@code yearFractionToDate(DayCounter,Date,Time)} helper
 *      (absent from any Java day-counter or utility class) plus every variant
 *      listed above plus {@code Thirty360::German/ISMA/NASD}.</li>
 * </ul>
 *
 * Activating these tests is deferred to a future phase that introduces the
 * required production classes; this header records the BLOCKED reason so the
 * cert audit can locate them without re-mining C++.
 */
public class DayCountersTest {

    public DayCountersTest() {
        QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
    }

    private static class SingleCase {
        private final ActualActual.Convention convention;
        private final Date start;
        private final Date end;
        private final Date refStart;
        private final Date refEnd;
        private final /*@Time*/ double  result;

        public SingleCase(
                final ActualActual.Convention convention,
                final Date start,
                final Date end,
                final /*@Time*/ double result) {
            this(convention, start, end, new Date(), new Date(), result);
        }

        public SingleCase(
                final ActualActual.Convention convention,
                final Date start,
                final Date end,
                final Date refStart,
                final Date refEnd,
                final /*@Time*/ double result) {
            this.convention = convention;
            this.start = start;
            this.end = end;
            this.refStart = refStart;
            this.refEnd = refEnd;
            this.result = result;
        }

        private String dumpDate(final Date date) {
            if (date == null || date.isNull())
                return "null";
            else
                return date.isoDate().toString();
        }


        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("[ ");
            sb.append(convention).append(", ");
            sb.append(dumpDate(start)).append(", ");
            sb.append(dumpDate(end)).append(", ");
            sb.append(dumpDate(refStart)).append(", ");
            sb.append(dumpDate(refEnd));
            sb.append(" ]");
            return sb.toString();
        }
    }


    @Test
    public void testActualActual() {

        QL.info("Testing actual/actual day counters...");

        final SingleCase testCases[] = new SingleCase[] {
                // first example
                new SingleCase(ActualActual.Convention.ISDA,
                        new Date(1,Month.November,2003), new Date(1,Month.May,2004),
                        0.497724380567),
                        new SingleCase(ActualActual.Convention.ISMA,
                                new Date(1,Month.November,2003), new Date(1,Month.May,2004),
                                new Date(1,Month.November,2003), new Date(1,Month.May,2004),
                                0.500000000000),
                                new SingleCase(ActualActual.Convention.AFB,
                                        new Date(1,Month.November,2003), new Date(1,Month.May,2004),
                                        0.497267759563),
                                        // short first calculation period (first period)
                                        new SingleCase(ActualActual.Convention.ISDA,
                                                new Date(1,Month.February,1999), new Date(1,Month.July,1999),
                                                0.410958904110),
                                                new SingleCase(ActualActual.Convention.ISMA,
                                                        new Date(1,Month.February,1999), new Date(1,Month.July,1999),
                                                        new Date(1,Month.July,1998), new Date(1,Month.July,1999),
                                                        0.410958904110),
                                                        new SingleCase(ActualActual.Convention.AFB,
                                                                new Date(1,Month.February,1999), new Date(1,Month.July,1999),
                                                                0.410958904110),
                                                                // short first calculation period (second period)
                                                                new SingleCase(ActualActual.Convention.ISDA,
                                                                        new Date(1,Month.July,1999), new Date(1,Month.July,2000),
                                                                        1.001377348600),
                                                                        new SingleCase(ActualActual.Convention.ISMA,
                                                                                new Date(1,Month.July,1999), new Date(1,Month.July,2000),
                                                                                new Date(1,Month.July,1999), new Date(1,Month.July,2000),
                                                                                1.000000000000),
                                                                                new SingleCase(ActualActual.Convention.AFB,
                                                                                        new Date(1,Month.July,1999), new Date(1,Month.July,2000),
                                                                                        1.000000000000),
                                                                                        // long first calculation period (first period)
                                                                                        new SingleCase(ActualActual.Convention.ISDA,
                                                                                                new Date(15,Month.August,2002), new Date(15,Month.July,2003),
                                                                                                0.915068493151),
                                                                                                new SingleCase(ActualActual.Convention.ISMA,
                                                                                                        new Date(15,Month.August,2002), new Date(15,Month.July,2003),
                                                                                                        new Date(15,Month.January,2003), new Date(15,Month.July,2003),
                                                                                                        0.915760869565),
                                                                                                        new SingleCase(ActualActual.Convention.AFB,
                                                                                                                new Date(15,Month.August,2002), new Date(15,Month.July,2003),
                                                                                                                0.915068493151),
                                                                                                                // long first calculation period (second period)
                                                                                                                /* Warning: the ISDA case is in disagreement with mktc1198.pdf */
                                                                                                                new SingleCase(ActualActual.Convention.ISDA,
                                                                                                                        new Date(15,Month.July,2003), new Date(15,Month.January,2004),
                                                                                                                        0.504004790778),
                                                                                                                        new SingleCase(ActualActual.Convention.ISMA,
                                                                                                                                new Date(15,Month.July,2003), new Date(15,Month.January,2004),
                                                                                                                                new Date(15,Month.July,2003), new Date(15,Month.January,2004),
                                                                                                                                0.500000000000),
                                                                                                                                new SingleCase(ActualActual.Convention.AFB,
                                                                                                                                        new Date(15,Month.July,2003), new Date(15,Month.January,2004),
                                                                                                                                        0.504109589041),
                                                                                                                                        // short final calculation period (penultimate period)
                                                                                                                                        new SingleCase(ActualActual.Convention.ISDA,
                                                                                                                                                new Date(30,Month.July,1999), new Date(30,Month.January,2000),
                                                                                                                                                0.503892506924),
                                                                                                                                                new SingleCase(ActualActual.Convention.ISMA,
                                                                                                                                                        new Date(30,Month.July,1999), new Date(30,Month.January,2000),
                                                                                                                                                        new Date(30,Month.July,1999), new Date(30,Month.January,2000),
                                                                                                                                                        0.500000000000),
                                                                                                                                                        new SingleCase(ActualActual.Convention.AFB,
                                                                                                                                                                new Date(30,Month.July,1999), new Date(30,Month.January,2000),
                                                                                                                                                                0.504109589041),
                                                                                                                                                                // short final calculation period (final period)
                                                                                                                                                                new SingleCase(ActualActual.Convention.ISDA,
                                                                                                                                                                        new Date(30,Month.January,2000), new Date(30,Month.June,2000),
                                                                                                                                                                        0.415300546448),
                                                                                                                                                                        new SingleCase(ActualActual.Convention.ISMA,
                                                                                                                                                                                new Date(30,Month.January,2000), new Date(30,Month.June,2000),
                                                                                                                                                                                new Date(30,Month.January,2000), new Date(30,Month.July,2000),
                                                                                                                                                                                0.417582417582),
                                                                                                                                                                                new SingleCase(ActualActual.Convention.AFB,
                                                                                                                                                                                        new Date(30,Month.January,2000), new Date(30,Month.June,2000),
                                                                                                                                                                                        0.41530054644)
        };

        for (int i=0; i<testCases.length-1; i++) {
            final ActualActual dayCounter =  new ActualActual(testCases[i].convention);
            final Date d1 = testCases[i].start;
            final Date d2 = testCases[i].end;
            final Date rd1 = testCases[i].refStart;
            final Date rd2 = testCases[i].refEnd;

            QL.info(testCases[i].toString());

            /*@Time*/ final double  calculated = dayCounter.yearFraction(d1, d2, rd1, rd2);

            if (abs(calculated-testCases[i].result) > 1.0e-10) {
                final String period = "period: " + d1 + " to " + d2;
                String refPeriod = "";
                if (testCases[i].convention == ActualActual.Convention.ISMA) {
                    refPeriod = "referencePeriod: " + rd1 + " to " + rd2;
                }
                fail(dayCounter.name() + ":\n"
                        + period + "\n"
                        + refPeriod + "\n"
                        + "    calculated: " + calculated + "\n"
                        + "    expected:   " + testCases[i].result);
            }
        }
    }


    @Test
    public void testSimple() {

        QL.info("Testing simple day counter...");

        final Period p[] = new Period[] { new Period(3, TimeUnit.Months), new Period(6, TimeUnit.Months), new Period(1, TimeUnit.Years) };
        /*@Time*/ final double expected[] = { 0.25, 0.5, 1.0 };

        // 4 years should be enough
        final Date first = new Date(1,Month.January,2002);
        final Date last  = new Date(31,Month.December,2005);
        final DayCounter dayCounter = new SimpleDayCounter();

        for (final Date start = first; start.le(last); start.inc()) {
            for (int i=0; i<expected.length-1; i++) {
                final Date end = start.add(p[i]);
                /*@Time*/ final double  calculated = dayCounter.yearFraction(start, end);

                if (abs(calculated-expected[i]) > 1.0e-12) {
                    fail("from " + start + " to " + end + ":\n"
                            + "    calculated: " + calculated + "\n"
                            + "    expected:   " + expected[i]);
                }
            }
        }
    }

    @Test
    public void testOne() {

        QL.info("Testing 1/1 day counter...");

        final Period p[] = new Period[]{ new Period(3, TimeUnit.Months), new Period(6, TimeUnit.Months), new Period(1, TimeUnit.Years) };
        /*@Time*/ final double expected[] = new double[] { 1.0, 1.0, 1.0 };

        // 1 years should be enough
        final Date first = new Date(1,Month.January,2004);
        final Date last  = new Date(31,Month.December,2004);
        final DayCounter dayCounter = new SimpleDayCounter();

        for (final Date start = first; start.le(last); start.inc()) {
            for (int i=0; i<expected.length-1; i++) {
                final Date end = start.add(p[i]);
                /*@Time*/ final double  calculated = dayCounter.yearFraction(start, end);

                if (abs(calculated-expected[i]) <= 1.0e-12) {
                    fail("from " + start + " to " + end + ":\n"
                            + "    calculated: " + calculated + "\n"
                            + "    expected:   " + expected[i]);
                }
            }
        }
    }

    //TODO: Sounds like this test method from the C++ codes actually test nothing!
    //abs(calculated - expected[i]) <= 1.0e-12? making sense? could always pass. Daniel
    @Test
    public void testBusiness252() {

        QL.info("Testing business/252 day counter...");

        final Date testDates[] = {
                new Date(1,Month.February,2002),
                new Date(4,Month.February,2002),
                new Date(16,Month.May,2003),
                new Date(17,Month.December,2003),
                new Date(17,Month.December,2004),
                new Date(19,Month.December,2005),
                new Date(2,Month.January,2006),
                new Date(13,Month.March,2006),
                new Date(15,Month.May,2006),
                new Date(17,Month.March,2006),
                new Date(15,Month.May,2006),
                new Date(26,Month.July,2006) };

        /*@Time*/ final double expected[] = {
                0.0039682539683,
                1.2738095238095,
                0.6031746031746,
                0.9960317460317,
                1.0000000000000,
                0.0396825396825,
                0.1904761904762,
                0.1666666666667,
                -0.1507936507937,
                0.1507936507937,
                0.2023809523810
        };

        final DayCounter dayCounter = new Business252(new Brazil(Brazil.Market.SETTLEMENT));

        for (int i=1; i<testDates.length-1; i++) {
            final Date start = testDates[i-1];
            final Date end = testDates[i];
            /*@Time*/ final double  calculated = dayCounter.yearFraction(start, end);
            // System.out.println(calculated);
            assertFalse(dayCounter.getClass().getName()
                    +"\n from "+start
                    +"\n to "+end
                    +"\n calculated: "+calculated
                    +"\n expected:   "+expected[i],
                    abs(calculated - expected[i]) <= 1.0e-12);
        }
    }
    
    @Test
    public void testEqualityHashCode() {

        QL.info("Testing Equality and HashCode ...");
        final DayCounter business252Brazil = new Business252(new Brazil(Brazil.Market.SETTLEMENT));
        final DayCounter business252Brazil1 = new Business252(new Brazil(Brazil.Market.SETTLEMENT));

        
        final DayCounter business252China = new Business252(new China(China.Market.SSE));
        final DayCounter simpleDayCounter = new SimpleDayCounter();      
        final DayCounter actual360 = new Actual360();        
        final DayCounter actual365Fixed = new Actual365Fixed();        
        final DayCounter actualActual = new ActualActual();        
        final DayCounter thirty360 = new Thirty360();        
        final DayCounter thirty360_2 = new Thirty360(); 
        
        assertFalse(thirty360.equals(null));
        assertEquals(thirty360, thirty360);
        assertEquals(thirty360, thirty360_2);
        
        assertFalse(simpleDayCounter.equals(business252Brazil));
        assertFalse(business252Brazil.equals(simpleDayCounter));
        assertFalse(actual360.equals(actual365Fixed));
        assertFalse(actual365Fixed.equals(actual360));
        assertFalse(actualActual.equals(thirty360));
        assertFalse(thirty360.equals(actualActual));
        assertFalse(business252Brazil.equals(business252China));
        assertFalse(business252China.equals(business252Brazil));
        assertTrue(business252Brazil.equals(business252Brazil1));
        
        assertTrue(business252Brazil.eq(business252Brazil1));
        assertFalse(business252Brazil.ne(business252Brazil1));
        
        HashSet<DayCounter> testSet = new HashSet<DayCounter>();
        testSet.add(thirty360);
        
        assertTrue(testSet.contains(thirty360));
        assertFalse(testSet.contains(actualActual));

    }

    /**
     * Smoke test for the {@code Actual360(includeLastDay)} variant added in
     * Phase 3d L0 A.2 — mirror of C++ {@code Actual360(bool includeLastDay)}
     * (ql/time/daycounters/actual360.hpp:60-62). Verifies (a) the alternate
     * name "Actual/360 (inc)", (b) the +1-day offset on dayCount, and
     * (c) the proportional yearFraction shift.
     */
    @Test
    public void testActual360IncludeLastDay() {
        QL.info("Testing Actual360(includeLastDay=true) variant ...");
        final DayCounter dc = new Actual360();
        final DayCounter dcInc = new Actual360(true);

        assertEquals("name (default)", "Actual/360", dc.name());
        assertEquals("name (inc)", "Actual/360 (inc)", dcInc.name());

        // 2026-04-01 -> 2026-05-01 = 30 days actual.
        final org.jquantlib.time.Date d1 =
                new org.jquantlib.time.Date(1, org.jquantlib.time.Month.April, 2026);
        final org.jquantlib.time.Date d2 =
                new org.jquantlib.time.Date(1, org.jquantlib.time.Month.May, 2026);
        assertEquals("dayCount default", 30L, dc.dayCount(d1, d2));
        assertEquals("dayCount inc",     31L, dcInc.dayCount(d1, d2));
        assertEquals("yearFraction default", 30.0 / 360.0, dc.yearFraction(d1, d2),    1.0e-12);
        assertEquals("yearFraction inc",     31.0 / 360.0, dcInc.yearFraction(d1, d2), 1.0e-12);
    }

    /**
     * Helper container for {@link #testThirty360_BondBasis} /
     * {@link #testThirty360_EurobondBasis}, mirroring C++
     * {@code Thirty360Case { Date start; Date end; long expected; }}.
     */
    private static final class Thirty360Case {
        final Date start;
        final Date end;
        final long expected;
        Thirty360Case(final Date start, final Date end, final long expected) {
            this.start = start; this.end = end; this.expected = expected;
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testThirty360_BondBasis
     * (lines 805-857). Validates {@link Thirty360.Convention#BondBasis}
     * dayCount against the ISDA reference table at
     * https://www.isda.org/2008/12/22/30-360-day-count-conventions/.
     *
     * NOTE: Java's {@code Thirty360.Impl_US} (used for both BondBasis and
     * USA in this codebase) yields the same numbers as C++ {@code ISMA_Impl}
     * (the BondBasis backend), so BondBasis comparisons are exact.
     */
    @Test
    public void testThirty360_BondBasis() {
        QL.info("Testing 30/360 day counter (Bond Basis)...");

        final DayCounter dayCounter = new Thirty360(Thirty360.Convention.BondBasis);

        final Thirty360Case[] data = new Thirty360Case[] {
            // Example 1: End dates do not involve the last day of February
            new Thirty360Case(new Date(20, Month.August,   2006), new Date(20, Month.February, 2007), 180L),
            new Thirty360Case(new Date(20, Month.February, 2007), new Date(20, Month.August,   2007), 180L),
            new Thirty360Case(new Date(20, Month.August,   2007), new Date(20, Month.February, 2008), 180L),
            new Thirty360Case(new Date(20, Month.February, 2008), new Date(20, Month.August,   2008), 180L),
            new Thirty360Case(new Date(20, Month.August,   2008), new Date(20, Month.February, 2009), 180L),
            new Thirty360Case(new Date(20, Month.February, 2009), new Date(20, Month.August,   2009), 180L),

            // Example 2: End dates include some end-February dates
            new Thirty360Case(new Date(31, Month.August,   2006), new Date(28, Month.February, 2007), 178L),
            new Thirty360Case(new Date(28, Month.February, 2007), new Date(31, Month.August,   2007), 183L),
            new Thirty360Case(new Date(31, Month.August,   2007), new Date(29, Month.February, 2008), 179L),
            new Thirty360Case(new Date(29, Month.February, 2008), new Date(31, Month.August,   2008), 182L),
            new Thirty360Case(new Date(31, Month.August,   2008), new Date(28, Month.February, 2009), 178L),
            new Thirty360Case(new Date(28, Month.February, 2009), new Date(31, Month.August,   2009), 183L),

            // Example 3: Miscellaneous calculations
            new Thirty360Case(new Date(31, Month.January,   2006), new Date(28, Month.February, 2006),  28L),
            new Thirty360Case(new Date(30, Month.January,   2006), new Date(28, Month.February, 2006),  28L),
            new Thirty360Case(new Date(28, Month.February,  2006), new Date( 3, Month.March,    2006),   5L),
            new Thirty360Case(new Date(14, Month.February,  2006), new Date(28, Month.February, 2006),  14L),
            new Thirty360Case(new Date(30, Month.September, 2006), new Date(31, Month.October,  2006),  30L),
            new Thirty360Case(new Date(31, Month.October,   2006), new Date(28, Month.November, 2006),  28L),
            new Thirty360Case(new Date(31, Month.August,    2007), new Date(28, Month.February, 2008), 178L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(28, Month.August,   2008), 180L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(30, Month.August,   2008), 182L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(31, Month.August,   2008), 183L),
            new Thirty360Case(new Date(26, Month.February,  2007), new Date(28, Month.February, 2008), 362L),
            new Thirty360Case(new Date(26, Month.February,  2007), new Date(29, Month.February, 2008), 363L),
            new Thirty360Case(new Date(29, Month.February,  2008), new Date(28, Month.February, 2009), 359L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(30, Month.March,    2008),  32L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(31, Month.March,    2008),  33L)
        };

        for (final Thirty360Case x : data) {
            final long calculated = dayCounter.dayCount(x.start, x.end);
            if (calculated != x.expected) {
                fail("from " + x.start + " to " + x.end + ":\n"
                        + "    calculated: " + calculated + "\n"
                        + "    expected:   " + x.expected);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testThirty360_EurobondBasis
     * (lines 859-917). Validates {@link Thirty360.Convention#EurobondBasis}
     * (also known as 30E/360) day counts against the ISDA reference table.
     */
    @Test
    public void testThirty360_EurobondBasis() {
        QL.info("Testing 30/360 day counter (Eurobond Basis)...");

        final DayCounter dayCounter = new Thirty360(Thirty360.Convention.EurobondBasis);

        final Thirty360Case[] data = new Thirty360Case[] {
            // Example 1: End dates do not involve the last day of February
            new Thirty360Case(new Date(20, Month.August,   2006), new Date(20, Month.February, 2007), 180L),
            new Thirty360Case(new Date(20, Month.February, 2007), new Date(20, Month.August,   2007), 180L),
            new Thirty360Case(new Date(20, Month.August,   2007), new Date(20, Month.February, 2008), 180L),
            new Thirty360Case(new Date(20, Month.February, 2008), new Date(20, Month.August,   2008), 180L),
            new Thirty360Case(new Date(20, Month.August,   2008), new Date(20, Month.February, 2009), 180L),
            new Thirty360Case(new Date(20, Month.February, 2009), new Date(20, Month.August,   2009), 180L),

            // Example 2: End dates include some end-February dates
            new Thirty360Case(new Date(28, Month.February, 2006), new Date(31, Month.August,   2006), 182L),
            new Thirty360Case(new Date(31, Month.August,   2006), new Date(28, Month.February, 2007), 178L),
            new Thirty360Case(new Date(28, Month.February, 2007), new Date(31, Month.August,   2007), 182L),
            new Thirty360Case(new Date(31, Month.August,   2007), new Date(29, Month.February, 2008), 179L),
            new Thirty360Case(new Date(29, Month.February, 2008), new Date(31, Month.August,   2008), 181L),
            new Thirty360Case(new Date(31, Month.August,   2008), new Date(28, Month.February, 2009), 178L),
            new Thirty360Case(new Date(28, Month.February, 2009), new Date(31, Month.August,   2009), 182L),
            new Thirty360Case(new Date(31, Month.August,   2009), new Date(28, Month.February, 2010), 178L),
            new Thirty360Case(new Date(28, Month.February, 2010), new Date(31, Month.August,   2010), 182L),
            new Thirty360Case(new Date(31, Month.August,   2010), new Date(28, Month.February, 2011), 178L),
            new Thirty360Case(new Date(28, Month.February, 2011), new Date(31, Month.August,   2011), 182L),
            new Thirty360Case(new Date(31, Month.August,   2011), new Date(29, Month.February, 2012), 179L),

            // Example 3: Miscellaneous calculations
            new Thirty360Case(new Date(31, Month.January,   2006), new Date(28, Month.February, 2006),  28L),
            new Thirty360Case(new Date(30, Month.January,   2006), new Date(28, Month.February, 2006),  28L),
            new Thirty360Case(new Date(28, Month.February,  2006), new Date( 3, Month.March,    2006),   5L),
            new Thirty360Case(new Date(14, Month.February,  2006), new Date(28, Month.February, 2006),  14L),
            new Thirty360Case(new Date(30, Month.September, 2006), new Date(31, Month.October,  2006),  30L),
            new Thirty360Case(new Date(31, Month.October,   2006), new Date(28, Month.November, 2006),  28L),
            new Thirty360Case(new Date(31, Month.August,    2007), new Date(28, Month.February, 2008), 178L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(28, Month.August,   2008), 180L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(30, Month.August,   2008), 182L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(31, Month.August,   2008), 182L),
            new Thirty360Case(new Date(26, Month.February,  2007), new Date(28, Month.February, 2008), 362L),
            new Thirty360Case(new Date(26, Month.February,  2007), new Date(29, Month.February, 2008), 363L),
            new Thirty360Case(new Date(29, Month.February,  2008), new Date(28, Month.February, 2009), 359L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(30, Month.March,    2008),  32L),
            new Thirty360Case(new Date(28, Month.February,  2008), new Date(31, Month.March,    2008),  32L)
        };

        for (final Thirty360Case x : data) {
            final long calculated = dayCounter.dayCount(x.start, x.end);
            if (calculated != x.expected) {
                fail("from " + x.start + " to " + x.end + ":\n"
                        + "    calculated: " + calculated + "\n"
                        + "    expected:   " + x.expected);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testThirty365 (lines 763-803).
     */
    @Test
    public void testThirty365() {
        QL.info("Testing 30/365 day counter...");

        final long[][] testCases = {
                // {d1.serial, d2.serial — encoded via new Date(d,m,y), expected}
                // Date(17, June, 2011) - Date(30, December, 2012) = 553 days
                // Date(31, March, 2025) - Date(30, April, 2025) = 30 days
                // Date(30, September, 2024) - Date(31, March, 2025) = 180 days
                // Date(30, March, 2025) - Date(31, March, 2025) = 0 days
        };
        final Date[][] dates = {
                { new Date(17, Month.June, 2011), new Date(30, Month.December, 2012) },
                { new Date(31, Month.March, 2025), new Date(30, Month.April, 2025) },
                { new Date(30, Month.September, 2024), new Date(31, Month.March, 2025) },
                { new Date(30, Month.March, 2025), new Date(31, Month.March, 2025) }
        };
        final long[] expected = { 553L, 30L, 180L, 0L };

        final DayCounter dayCounter = new Thirty365();
        for (int i = 0; i < dates.length; i++) {
            final Date d1 = dates[i][0];
            final Date d2 = dates[i][1];
            final long days = dayCounter.dayCount(d1, d2);
            if (days != expected[i]) {
                fail("from " + d1 + " to " + d2 + ":\n"
                        + "    calculated: " + days + "\n"
                        + "    expected:   " + expected[i]);
            }
            final double t = dayCounter.yearFraction(d1, d2);
            final double expT = expected[i] / 365.0;
            if (abs(t - expT) > 1.0e-12) {
                fail("from " + d1 + " to " + d2 + ":\n"
                        + "    calculated: " + t + "\n"
                        + "    expected:   " + expT);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testThirty360_USA (lines 919-971).
     */
    @Test
    public void testThirty360_USA() {
        QL.info("Testing 30/360 day counter (USA)...");

        final DayCounter dayCounter = new Thirty360(Thirty360.Convention.USA);
        final Thirty360Case[] data = new Thirty360Case[] {
                // Example 1
                new Thirty360Case(new Date(20, Month.August, 2006),    new Date(20, Month.February, 2007), 180L),
                new Thirty360Case(new Date(20, Month.February, 2007),  new Date(20, Month.August, 2007),   180L),
                new Thirty360Case(new Date(20, Month.August, 2007),    new Date(20, Month.February, 2008), 180L),
                new Thirty360Case(new Date(20, Month.February, 2008),  new Date(20, Month.August, 2008),   180L),
                new Thirty360Case(new Date(20, Month.August, 2008),    new Date(20, Month.February, 2009), 180L),
                new Thirty360Case(new Date(20, Month.February, 2009),  new Date(20, Month.August, 2009),   180L),
                // Example 2
                new Thirty360Case(new Date(31, Month.August, 2006),    new Date(28, Month.February, 2007), 178L),
                new Thirty360Case(new Date(28, Month.February, 2007),  new Date(31, Month.August, 2007),   180L),
                new Thirty360Case(new Date(31, Month.August, 2007),    new Date(29, Month.February, 2008), 179L),
                new Thirty360Case(new Date(29, Month.February, 2008),  new Date(31, Month.August, 2008),   180L),
                new Thirty360Case(new Date(31, Month.August, 2008),    new Date(28, Month.February, 2009), 178L),
                new Thirty360Case(new Date(28, Month.February, 2009),  new Date(31, Month.August, 2009),   180L),
                // Example 3
                new Thirty360Case(new Date(31, Month.January, 2006),   new Date(28, Month.February, 2006),  28L),
                new Thirty360Case(new Date(30, Month.January, 2006),   new Date(28, Month.February, 2006),  28L),
                new Thirty360Case(new Date(28, Month.February, 2006),  new Date( 3, Month.March, 2006),      3L),
                new Thirty360Case(new Date(14, Month.February, 2006),  new Date(28, Month.February, 2006),  14L),
                new Thirty360Case(new Date(30, Month.September, 2006), new Date(31, Month.October, 2006),   30L),
                new Thirty360Case(new Date(31, Month.October, 2006),   new Date(28, Month.November, 2006),  28L),
                new Thirty360Case(new Date(31, Month.August, 2007),    new Date(28, Month.February, 2008), 178L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(28, Month.August, 2008),   180L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(30, Month.August, 2008),   182L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(31, Month.August, 2008),   183L),
                new Thirty360Case(new Date(26, Month.February, 2007),  new Date(28, Month.February, 2008), 362L),
                new Thirty360Case(new Date(26, Month.February, 2007),  new Date(29, Month.February, 2008), 363L),
                new Thirty360Case(new Date(29, Month.February, 2008),  new Date(28, Month.February, 2009), 360L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(30, Month.March, 2008),     32L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(31, Month.March, 2008),     33L)
        };
        for (final Thirty360Case x : data) {
            final long calculated = dayCounter.dayCount(x.start, x.end);
            if (calculated != x.expected) {
                fail("from " + x.start + " to " + x.end + ":\n"
                        + "    calculated: " + calculated + "\n"
                        + "    expected:   " + x.expected);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testThirty360_ISDA (lines 973-1062).
     */
    @Test
    public void testThirty360_ISDA() {
        QL.info("Testing 30/360 day counter (ISDA)...");

        // Group 1: termination = 20-Aug-2009
        final Thirty360Case[] data1 = {
                new Thirty360Case(new Date(20, Month.August, 2006),    new Date(20, Month.February, 2007), 180L),
                new Thirty360Case(new Date(20, Month.February, 2007),  new Date(20, Month.August, 2007),   180L),
                new Thirty360Case(new Date(20, Month.August, 2007),    new Date(20, Month.February, 2008), 180L),
                new Thirty360Case(new Date(20, Month.February, 2008),  new Date(20, Month.August, 2008),   180L),
                new Thirty360Case(new Date(20, Month.August, 2008),    new Date(20, Month.February, 2009), 180L),
                new Thirty360Case(new Date(20, Month.February, 2009),  new Date(20, Month.August, 2009),   180L)
        };
        Date terminationDate = new Date(20, Month.August, 2009);
        DayCounter dayCounter = new Thirty360(Thirty360.Convention.ISDA, terminationDate);
        for (final Thirty360Case x : data1) {
            final long calculated = dayCounter.dayCount(x.start, x.end);
            if (calculated != x.expected) {
                fail("ISDA grp1: from " + x.start + " to " + x.end + " calc=" + calculated + " exp=" + x.expected);
            }
        }

        // Group 2: termination = 29-Feb-2012
        final Thirty360Case[] data2 = {
                new Thirty360Case(new Date(28, Month.February, 2006),  new Date(31, Month.August, 2006),   180L),
                new Thirty360Case(new Date(31, Month.August, 2006),    new Date(28, Month.February, 2007), 180L),
                new Thirty360Case(new Date(28, Month.February, 2007),  new Date(31, Month.August, 2007),   180L),
                new Thirty360Case(new Date(31, Month.August, 2007),    new Date(29, Month.February, 2008), 180L),
                new Thirty360Case(new Date(29, Month.February, 2008),  new Date(31, Month.August, 2008),   180L),
                new Thirty360Case(new Date(31, Month.August, 2008),    new Date(28, Month.February, 2009), 180L),
                new Thirty360Case(new Date(28, Month.February, 2009),  new Date(31, Month.August, 2009),   180L),
                new Thirty360Case(new Date(31, Month.August, 2009),    new Date(28, Month.February, 2010), 180L),
                new Thirty360Case(new Date(28, Month.February, 2010),  new Date(31, Month.August, 2010),   180L),
                new Thirty360Case(new Date(31, Month.August, 2010),    new Date(28, Month.February, 2011), 180L),
                new Thirty360Case(new Date(28, Month.February, 2011),  new Date(31, Month.August, 2011),   180L),
                new Thirty360Case(new Date(31, Month.August, 2011),    new Date(29, Month.February, 2012), 179L)
        };
        terminationDate = new Date(29, Month.February, 2012);
        dayCounter = new Thirty360(Thirty360.Convention.ISDA, terminationDate);
        for (final Thirty360Case x : data2) {
            final long calculated = dayCounter.dayCount(x.start, x.end);
            if (calculated != x.expected) {
                fail("ISDA grp2: from " + x.start + " to " + x.end + " calc=" + calculated + " exp=" + x.expected);
            }
        }

        // Group 3: termination = 29-Feb-2008
        final Thirty360Case[] data3 = {
                new Thirty360Case(new Date(31, Month.January, 2006),   new Date(28, Month.February, 2006),  30L),
                new Thirty360Case(new Date(30, Month.January, 2006),   new Date(28, Month.February, 2006),  30L),
                new Thirty360Case(new Date(28, Month.February, 2006),  new Date( 3, Month.March, 2006),      3L),
                new Thirty360Case(new Date(14, Month.February, 2006),  new Date(28, Month.February, 2006),  16L),
                new Thirty360Case(new Date(30, Month.September, 2006), new Date(31, Month.October, 2006),   30L),
                new Thirty360Case(new Date(31, Month.October, 2006),   new Date(28, Month.November, 2006),  28L),
                new Thirty360Case(new Date(31, Month.August, 2007),    new Date(28, Month.February, 2008), 178L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(28, Month.August, 2008),   180L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(30, Month.August, 2008),   182L),
                new Thirty360Case(new Date(28, Month.February, 2008),  new Date(31, Month.August, 2008),   182L),
                new Thirty360Case(new Date(28, Month.February, 2007),  new Date(28, Month.February, 2008), 358L),
                new Thirty360Case(new Date(28, Month.February, 2007),  new Date(29, Month.February, 2008), 359L),
                new Thirty360Case(new Date(29, Month.February, 2008),  new Date(28, Month.February, 2009), 360L),
                new Thirty360Case(new Date(29, Month.February, 2008),  new Date(30, Month.March, 2008),     30L),
                new Thirty360Case(new Date(29, Month.February, 2008),  new Date(31, Month.March, 2008),     30L)
        };
        terminationDate = new Date(29, Month.February, 2008);
        dayCounter = new Thirty360(Thirty360.Convention.ISDA, terminationDate);
        for (final Thirty360Case x : data3) {
            final long calculated = dayCounter.dayCount(x.start, x.end);
            if (calculated != x.expected) {
                fail("ISDA grp3: from " + x.start + " to " + x.end + " calc=" + calculated + " exp=" + x.expected);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testActual365_Canadian (lines 1064-1092).
     */
    @Test
    public void testActual365_Canadian() {
        QL.info("Testing that Actual/365 (Canadian) throws when needed...");

        final Actual365Fixed dayCounter = new Actual365Fixed(Actual365Fixed.Convention.Canadian);

        // no reference period
        try {
            dayCounter.yearFraction(new Date(10, Month.September, 2018),
                                    new Date(10, Month.September, 2019));
            fail("Expected exception for missing reference period");
        } catch (final RuntimeException expected) { /* ok */ }

        // reference period shorter than a month
        try {
            dayCounter.yearFraction(new Date(10, Month.September, 2018),
                                    new Date(12, Month.September, 2018),
                                    new Date(10, Month.September, 2018),
                                    new Date(15, Month.September, 2018));
            fail("Expected exception for reference period shorter than a month");
        } catch (final RuntimeException expected) { /* ok */ }

        // reference period longer than a year
        try {
            dayCounter.yearFraction(new Date( 8, Month.January, 2025),
                                    new Date( 8, Month.January, 2027),
                                    new Date( 8, Month.January, 2025),
                                    new Date( 8, Month.January, 2027));
            fail("Expected exception for reference period longer than a year");
        } catch (final RuntimeException expected) { /* ok */ }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testAct366 (lines 1157-1210).
     */
    @Test
    public void testAct366() {
        QL.info("Testing Act/366 day counter...");

        final Date[] testDates = {
                new Date( 1, Month.February, 2002),
                new Date( 4, Month.February, 2002),
                new Date(16, Month.May, 2003),
                new Date(17, Month.December, 2003),
                new Date(17, Month.December, 2004),
                new Date(19, Month.December, 2005),
                new Date( 2, Month.January, 2006),
                new Date(13, Month.March, 2006),
                new Date(15, Month.May, 2006),
                new Date(17, Month.March, 2006),
                new Date(15, Month.May, 2006),
                new Date(26, Month.July, 2006),
                new Date(28, Month.June, 2007),
                new Date(16, Month.September, 2009),
                new Date(26, Month.July, 2016)
        };
        final double[] expected = {
                0.00819672131147541,
                1.27322404371585,
                0.587431693989071,
                1.0,
                1.00273224043716,
                0.0382513661202186,
                0.191256830601093,
                0.172131147540984,
                -0.16120218579235,
                0.16120218579235,
                0.19672131147541,
                0.920765027322404,
                2.21584699453552,
                6.84426229508197
        };
        final DayCounter dayCounter = new Actual366();
        for (int i = 1; i < testDates.length; i++) {
            final double calculated = dayCounter.yearFraction(testDates[i - 1], testDates[i]);
            if (abs(calculated - expected[i - 1]) > 1.0e-12) {
                fail("from " + testDates[i - 1] + " to " + testDates[i]
                        + " calc=" + calculated + " exp=" + expected[i - 1]);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testAct36525 (lines 1212-1265).
     */
    @Test
    public void testAct36525() {
        QL.info("Testing Act/365.25 day counter...");

        final Date[] testDates = {
                new Date( 1, Month.February, 2002),
                new Date( 4, Month.February, 2002),
                new Date(16, Month.May, 2003),
                new Date(17, Month.December, 2003),
                new Date(17, Month.December, 2004),
                new Date(19, Month.December, 2005),
                new Date( 2, Month.January, 2006),
                new Date(13, Month.March, 2006),
                new Date(15, Month.May, 2006),
                new Date(17, Month.March, 2006),
                new Date(15, Month.May, 2006),
                new Date(26, Month.July, 2006),
                new Date(28, Month.June, 2007),
                new Date(16, Month.September, 2009),
                new Date(26, Month.July, 2016)
        };
        final double[] expected = {
                0.0082135523613963,
                1.27583846680356,
                0.588637919233402,
                1.00205338809035,
                1.00479123887748,
                0.0383299110198494,
                0.191649555099247,
                0.172484599589322,
                -0.161533196440794,
                0.161533196440794,
                0.197125256673511,
                0.922655715263518,
                2.22039698836413,
                6.85831622176591
        };
        final DayCounter dayCounter = new Actual36525();
        for (int i = 1; i < testDates.length; i++) {
            final double calculated = dayCounter.yearFraction(testDates[i - 1], testDates[i]);
            if (abs(calculated - expected[i - 1]) > 1.0e-12) {
                fail("from " + testDates[i - 1] + " to " + testDates[i]
                        + " calc=" + calculated + " exp=" + expected[i - 1]);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testActualConsistency (lines 1267-1313).
     */
    @Test
    public void testActualConsistency() {
        QL.info("Testing consistency between different actual day-counters...");

        final Date[] todayDates = { new Date(12, Month.January, 2022) };
        final Date[] testDates = {
                new Date( 1, Month.February, 2023),
                new Date( 4, Month.February, 2023),
                new Date(16, Month.May, 2024),
                new Date(17, Month.December, 2024),
                new Date(17, Month.December, 2025),
                new Date(19, Month.December, 2026),
                new Date( 2, Month.January, 2027),
                new Date(13, Month.March, 2028),
                new Date(15, Month.May, 2028),
                new Date(26, Month.July, 2036)
        };
        final DayCounter actual365 = new Actual365Fixed();
        final DayCounter actual366 = new Actual366();
        final DayCounter actual364 = new Actual364();
        final DayCounter actual36525 = new Actual36525();
        final DayCounter actual360 = new Actual360();
        final DayCounter actual360incl = new Actual360(true);

        for (final Date today : todayDates) {
            for (final Date d : testDates) {
                final double t365 = actual365.yearFraction(today, d);
                final double t366 = actual366.yearFraction(today, d);
                final double t364 = actual364.yearFraction(today, d);
                final double t360 = actual360.yearFraction(today, d);
                final double t360incl = actual360incl.yearFraction(today, d);
                final double t36525 = actual36525.yearFraction(today, d);

                assertTrue(abs(t365 * 365 / 366.0 - t366) < 1e-14);
                assertTrue(abs(t365 * 365 / 364.0 - t364) < 1e-14);
                assertTrue(abs(t365 * 365 / 360.0 - t360) < 1e-14);
                assertTrue(abs(t365 * 365 / 365.25 - t36525) < 1e-14);
                assertTrue(abs(t365 * 365 / 360.0 - (t360incl * 360 - 1) / 360.0) < 1e-14);
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testActualActualIsma (lines 223-336).
     */
    @Test
    public void testActualActualIsma() {
        QL.info("Testing actual/actual (ISMA) with odd last period...");

        // Group 1: not endOfMonth, semiannual
        Schedule schedule = new MakeSchedule()
                .from(new Date(30, Month.January, 1999))
                .to(new Date(30, Month.June, 2000))
                .withFrequency(Frequency.Semiannual)
                .withFirstDate(new Date(30, Month.July, 1999))
                .withNextToLastDate(new Date(30, Month.January, 2000))
                .endOfMonth(false)
                .schedule();

        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        double expected = 152.0 / (182.0 * 2);
        double calculated = dayCounter.yearFraction(new Date(30, Month.January, 2000),
                                                    new Date(30, Month.June, 2000));
        if (abs(calculated - expected) > 1.0e-10) {
            fail(dayCounter.name() + ": grp1 calc=" + calculated + " exp=" + expected);
        }

        // Group 2: endOfMonth=true, quarterly
        schedule = new MakeSchedule()
                .from(new Date(31, Month.May, 1999))
                .to(new Date(30, Month.April, 2000))
                .withFrequency(Frequency.Quarterly)
                .withFirstDate(new Date(31, Month.August, 1999))
                .withNextToLastDate(new Date(30, Month.November, 1999))
                .endOfMonth(true)
                .schedule();
        dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        expected = 91.0 / (91.0 * 4) + 61.0 / (92.0 * 4);
        calculated = dayCounter.yearFraction(new Date(30, Month.November, 1999),
                                             new Date(30, Month.April, 2000));
        if (abs(calculated - expected) > 1.0e-10) {
            fail(dayCounter.name() + ": grp2 calc=" + calculated + " exp=" + expected);
        }

        // Group 3: endOfMonth=false, quarterly
        schedule = new MakeSchedule()
                .from(new Date(31, Month.May, 1999))
                .to(new Date(30, Month.April, 2000))
                .withFrequency(Frequency.Quarterly)
                .withFirstDate(new Date(31, Month.August, 1999))
                .withNextToLastDate(new Date(30, Month.November, 1999))
                .endOfMonth(false)
                .schedule();
        dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        expected = 91.0 / (91.0 * 4) + 61.0 / (90.0 * 4);
        calculated = dayCounter.yearFraction(new Date(30, Month.November, 1999),
                                             new Date(30, Month.April, 2000));
        if (abs(calculated - expected) > 1.0e-10) {
            fail(dayCounter.name() + ": grp3 calc=" + calculated + " exp=" + expected);
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testActualActualOutOfScheduleRange (lines 1123-1155).
     *
     * <p>Uses a {@code Schedule} that ends at 21-May-2029 and then asks for a
     * year fraction past that endpoint — should throw because of the
     * {@code Dates out of range of schedule} guard in
     * {@link ActualActual.SchedISMA_Impl#yearFraction}.
     */
    @Test
    public void testActualActualOutOfScheduleRange() {
        QL.info("Testing usage of actual/actual out of schedule...");

        final Date effective = new Date(21, Month.May, 2019);
        final Date termination = new Date(21, Month.May, 2029);
        final Period tenor = new Period(1, TimeUnit.Years);
        final Calendar calendar = new China(); // SSE is the only available variant in JQuantLib
        final BusinessDayConvention convention = BusinessDayConvention.Unadjusted;
        final DateGeneration.Rule rule = DateGeneration.Rule.Backward;
        final boolean endOfMonth = false;

        final Schedule schedule = new Schedule(
                effective, termination, tenor, calendar, convention, convention,
                rule, endOfMonth, new Date(), new Date());
        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.Bond, schedule);

        // Beyond the schedule's end date — should throw.
        final Date today = new Date(10, Month.November, 2020);
        final Date beyond = today.add(new Period(9, TimeUnit.Years));
        try {
            dayCounter.yearFraction(today, beyond);
            fail("Expected exception when yearFraction exceeds schedule range");
        } catch (final RuntimeException expected) { /* ok */ }
    }

    /**
     * Port of free function {@code ISMAYearFractionWithReferenceDates} from
     * v1.42.1 {@code test-suite/daycounters.cpp:83-91}. Computes the ISMA
     * year fraction for a sub-interval inside a reference period using only
     * the day-count primitive, mirroring the C++ helper one-for-one. Used by
     * {@link #testActualActualWithAnnualSchedule()} and
     * {@link #testActualActualWithSchedule()}.
     */
    private static double ismaYearFractionWithReferenceDates(
            final DayCounter dayCounter,
            final Date start, final Date end,
            final Date refStart, final Date refEnd) {
        final double referenceDayCount = dayCounter.dayCount(refStart, refEnd);
        final int couponsPerYear = (int) Math.round(365.0 / referenceDayCount);
        return ((double) dayCounter.dayCount(start, end))
                / (referenceDayCount * couponsPerYear);
    }

    /**
     * Port of free function {@code actualActualDaycountComputation} from
     * v1.42.1 {@code test-suite/daycounters.cpp:93-112}. Sums the ISMA year
     * fraction across reference periods of a given schedule, used as a
     * reference value in {@link #testActualActualWithSemiannualSchedule()}.
     */
    private static double actualActualDaycountComputation(
            final Schedule schedule, final Date start, final Date end) {
        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        double yearFraction = 0.0;
        for (int i = 1; i < schedule.size() - 1; i++) {
            final Date referenceStart = schedule.date(i);
            final Date referenceEnd = schedule.date(i + 1);
            if (start.lt(referenceEnd) && end.gt(referenceStart)) {
                final Date subStart = start.gt(referenceStart) ? start : referenceStart;
                final Date subEnd = end.lt(referenceEnd) ? end : referenceEnd;
                yearFraction += ismaYearFractionWithReferenceDates(
                        dayCounter, subStart, subEnd, referenceStart, referenceEnd);
            }
        }
        return yearFraction;
    }

    /**
     * Direct port of v1.42.1 {@code test-suite/daycounters.cpp::
     * testActualActualWithSemiannualSchedule} (lines 338-451). Validates the
     * schedule-aware ISMA Act/Act day counter under a semiannual coupon
     * schedule against a from-scratch ISMA computation that walks the
     * reference periods one at a time. Uses {@link UnitedStates.Market#GovernmentBond}.
     * Tolerance 1e-10 / 1e-8.
     */
    @Test
    public void testActualActualWithSemiannualSchedule() {
        QL.info("Testing actual/actual with schedule for undefined semiannual reference periods...");

        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Date fromDate = new Date(10, Month.January, 2017);
        final Date firstCoupon = new Date(31, Month.August, 2017);
        final Date quasiCoupon = new Date(28, Month.February, 2017);
        final Date quasiCoupon2 = new Date(31, Month.August, 2016);

        Schedule schedule = new MakeSchedule()
                .from(fromDate)
                .withFirstDate(firstCoupon)
                .to(new Date(28, Month.February, 2026))
                .withFrequency(Frequency.Semiannual)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards().endOfMonth(true)
                .schedule();

        Date testDate = schedule.date(1);
        DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);
        final DayCounter dayCounterNoSchedule = new ActualActual(ActualActual.Convention.ISMA);

        final Date referencePeriodStart = schedule.date(1);
        final Date referencePeriodEnd = schedule.date(2);

        // Zero-length intervals must produce 0.
        if (dayCounter.yearFraction(referencePeriodStart, referencePeriodStart) != 0.0) {
            fail("Expected 0 year fraction for identical dates (schedule-aware)");
        }
        if (dayCounterNoSchedule.yearFraction(referencePeriodStart, referencePeriodStart) != 0.0) {
            fail("Expected 0 year fraction for identical dates (no schedule)");
        }
        if (dayCounterNoSchedule.yearFraction(referencePeriodStart, referencePeriodStart,
                                              referencePeriodStart, referencePeriodStart) != 0.0) {
            fail("Expected 0 year fraction for identical dates with explicit refs");
        }
        if (dayCounter.yearFraction(referencePeriodStart, referencePeriodEnd) != 0.5) {
            fail("Expected exactly 0.5 for full reference period (schedule-aware), got "
                    + dayCounter.yearFraction(referencePeriodStart, referencePeriodEnd));
        }
        if (dayCounterNoSchedule.yearFraction(referencePeriodStart, referencePeriodEnd,
                                              referencePeriodStart, referencePeriodEnd) != 0.5) {
            fail("Expected exactly 0.5 for full reference period (no schedule, explicit refs)");
        }

        // Walk test date across the period — schedule-aware and explicit-refs forms must agree.
        while (testDate.lt(referencePeriodEnd)) {
            final double difference =
                    dayCounter.yearFraction(testDate, referencePeriodEnd,
                                            referencePeriodStart, referencePeriodEnd)
                    - dayCounter.yearFraction(testDate, referencePeriodEnd);
            if (Math.abs(difference) > 1.0e-10) {
                fail("Schedule did not pick the right reference period: "
                        + testDate + " to " + referencePeriodEnd
                        + " diff=" + difference);
            }
            testDate = calendar.advance(testDate, new Period(1, TimeUnit.Days),
                                        BusinessDayConvention.Following, false);
        }

        // Long first coupon
        final double calculatedYearFraction = dayCounter.yearFraction(fromDate, firstCoupon);
        final double expectedYearFraction = 0.5
                + ((double) dayCounter.dayCount(fromDate, quasiCoupon))
                  / (2.0 * dayCounter.dayCount(quasiCoupon2, quasiCoupon));
        if (Math.abs(calculatedYearFraction - expectedYearFraction) > 1.0e-10) {
            fail("Long first coupon year fraction mismatch: expected=" + expectedYearFraction
                    + " calculated=" + calculatedYearFraction);
        }

        // Multi-period sweep with endOfMonth=false.
        schedule = new MakeSchedule()
                .from(new Date(10, Month.January, 2017))
                .withFirstDate(new Date(31, Month.August, 2017))
                .to(new Date(28, Month.February, 2026))
                .withFrequency(Frequency.Semiannual)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards().endOfMonth(false)
                .schedule();

        Date periodStartDate = schedule.date(1);
        Date periodEndDate = schedule.date(2);
        dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);

        while (periodEndDate.lt(schedule.date(schedule.size() - 2))) {
            final double expected = actualActualDaycountComputation(
                    schedule, periodStartDate, periodEndDate);
            final double calculated = dayCounter.yearFraction(periodStartDate, periodEndDate);
            if (Math.abs(expected - calculated) > 1.0e-8) {
                fail("Schedule-aware Act/Act mismatch: " + periodStartDate
                        + " to " + periodEndDate
                        + " expected=" + expected + " calculated=" + calculated);
            }
            periodEndDate = calendar.advance(periodEndDate, new Period(1, TimeUnit.Days),
                                             BusinessDayConvention.Following, false);
        }
    }

    /**
     * Direct port of v1.42.1 {@code test-suite/daycounters.cpp::
     * testActualActualWithAnnualSchedule} (lines 453-491). Validates that
     * the schedule-aware ISMA Act/Act day counter agrees with the
     * {@link #ismaYearFractionWithReferenceDates} reference computation when
     * fed an annual schedule. Tolerance 1e-10.
     */
    @Test
    public void testActualActualWithAnnualSchedule() {
        QL.info("Testing actual/actual with schedule for undefined annual reference periods...");

        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Schedule schedule = new MakeSchedule()
                .from(new Date(10, Month.January, 2017))
                .withFirstDate(new Date(31, Month.August, 2017))
                .to(new Date(28, Month.February, 2026))
                .withFrequency(Frequency.Annual)
                .withCalendar(calendar)
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards().endOfMonth(false)
                .schedule();

        final Date referencePeriodStart = schedule.date(1);
        final Date referencePeriodEnd = schedule.date(2);

        Date testDate = schedule.date(1);
        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);

        while (testDate.lt(referencePeriodEnd)) {
            final double difference =
                    ismaYearFractionWithReferenceDates(dayCounter,
                                                       testDate, referencePeriodEnd,
                                                       referencePeriodStart, referencePeriodEnd)
                    - dayCounter.yearFraction(testDate, referencePeriodEnd);
            if (Math.abs(difference) > 1.0e-10) {
                fail("Schedule did not pick the right annual reference period: "
                        + testDate + " to " + referencePeriodEnd
                        + " (ref " + referencePeriodStart + " to " + referencePeriodEnd + ")"
                        + " diff=" + difference);
            }
            testDate = calendar.advance(testDate, new Period(1, TimeUnit.Days),
                                        BusinessDayConvention.Following, false);
        }
    }

    /**
     * Direct port of v1.42.1 {@code test-suite/daycounters.cpp::
     * testActualActualWithSchedule} (lines 493-641). Validates the
     * schedule-aware ISMA Act/Act day counter against a {@link Canada}
     * semiannual schedule with a long first coupon, exercising both reference
     * and no-reference paths, splitting the long first coupon across two
     * quasi-periods, and checking sum-consistency. Tolerance 1e-10.
     */
    @Test
    public void testActualActualWithSchedule() {
        QL.info("Testing actual/actual day counter with schedule...");

        // Long first coupon
        final Date issueDateExpected = new Date(17, Month.January, 2017);
        final Date firstCouponDateExpected = new Date(31, Month.August, 2017);

        final Schedule schedule = new MakeSchedule()
                .from(issueDateExpected)
                .withFirstDate(firstCouponDateExpected)
                .to(new Date(28, Month.February, 2026))
                .withFrequency(Frequency.Semiannual)
                .withCalendar(new Canada())
                .withConvention(BusinessDayConvention.Unadjusted)
                .backwards()
                .endOfMonth()
                .schedule();

        final Date issueDate = schedule.date(0);
        if (!issueDate.equals(issueDateExpected)) {
            fail("Issue date mismatch: " + issueDate + " expected " + issueDateExpected);
        }
        final Date firstCouponDate = schedule.date(1);
        if (!firstCouponDate.equals(firstCouponDateExpected)) {
            fail("First coupon date mismatch: " + firstCouponDate
                    + " expected " + firstCouponDateExpected);
        }

        // Build quasi coupon dates by stepping back two tenors from the first coupon.
        final Date quasiCouponDate2 = schedule.calendar().advance(
                firstCouponDate,
                schedule.tenor().negative(),
                schedule.businessDayConvention(),
                schedule.endOfMonth());
        final Date quasiCouponDate1 = schedule.calendar().advance(
                quasiCouponDate2,
                schedule.tenor().negative(),
                schedule.businessDayConvention(),
                schedule.endOfMonth());

        final Date quasiCouponDate1Expected = new Date(31, Month.August, 2016);
        final Date quasiCouponDate2Expected = new Date(28, Month.February, 2017);

        if (!quasiCouponDate2.equals(quasiCouponDate2Expected)) {
            fail("Later quasi coupon date mismatch: " + quasiCouponDate2
                    + " expected " + quasiCouponDate2Expected);
        }
        if (!quasiCouponDate1.equals(quasiCouponDate1Expected)) {
            fail("Earlier quasi coupon date mismatch: " + quasiCouponDate1
                    + " expected " + quasiCouponDate1Expected);
        }

        final DayCounter dayCounter = new ActualActual(ActualActual.Convention.ISMA, schedule);

        // Full coupon — schedule-aware vs explicit-refs vs split.
        double tWithReference = dayCounter.yearFraction(
                issueDate, firstCouponDate, quasiCouponDate2, firstCouponDate);
        double tNoReference = dayCounter.yearFraction(issueDate, firstCouponDate);
        final double tTotal = ismaYearFractionWithReferenceDates(
                dayCounter, issueDate, quasiCouponDate2,
                quasiCouponDate1, quasiCouponDate2) + 0.5;
        final double expected = 0.6160220994;

        if (Math.abs(tTotal - expected) > 1.0e-10) {
            fail("tTotal mismatch: calc=" + tTotal + " expected=" + expected);
        }
        if (Math.abs(tWithReference - expected) > 1.0e-10) {
            fail("tWithReference mismatch: calc=" + tWithReference + " expected=" + expected);
        }
        if (Math.abs(tNoReference - tWithReference) > 1.0e-10) {
            fail("Reference-vs-no-reference disagreement on full coupon");
        }

        // Settlement date in the first quasi-period.
        Date settlementDate = new Date(29, Month.January, 2017);
        tWithReference = ismaYearFractionWithReferenceDates(
                dayCounter, issueDate, settlementDate,
                quasiCouponDate1, quasiCouponDate2);
        tNoReference = dayCounter.yearFraction(issueDate, settlementDate);
        final double tExpectedFirstQp = 0.03314917127071823;  // 12.0/362
        if (Math.abs(tWithReference - tExpectedFirstQp) > 1.0e-10) {
            fail("First-qp year fraction (with ref) mismatch: calc=" + tNoReference
                    + " expected=" + tExpectedFirstQp);
        }
        if (Math.abs(tNoReference - tWithReference) > 1.0e-10) {
            fail("Reference-vs-no-reference disagreement on first quasi-period");
        }
        double t2 = dayCounter.yearFraction(settlementDate, firstCouponDate);
        if (Math.abs(tExpectedFirstQp + t2 - expected) > 1.0e-10) {
            fail("First-qp split sum inconsistent: "
                    + (tExpectedFirstQp + t2) + " vs " + expected);
        }

        // Settlement date in the second quasi-period.
        settlementDate = new Date(29, Month.July, 2017);
        tNoReference = dayCounter.yearFraction(issueDate, settlementDate);
        tWithReference = ismaYearFractionWithReferenceDates(
                dayCounter, issueDate, quasiCouponDate2,
                quasiCouponDate1, quasiCouponDate2)
                + ismaYearFractionWithReferenceDates(
                dayCounter, quasiCouponDate2, settlementDate,
                quasiCouponDate2, firstCouponDate);
        if (Math.abs(tNoReference - tWithReference) > 1.0e-10) {
            fail("Second-qp two-segment vs single-call disagree: "
                    + tNoReference + " vs " + tWithReference);
        }
        t2 = dayCounter.yearFraction(settlementDate, firstCouponDate);
        if (Math.abs(tTotal - (tNoReference + t2)) > 1.0e-10) {
            fail("tTotal vs split sum inconsistent: "
                    + tTotal + " vs " + (tNoReference + t2));
        }
    }

    /**
     * Faithful port of {@code test-suite/daycounters.cpp::testYearFraction2DateBulk} (lines 1315-1365).
     *
     * <p>Round-trips {@code dc.yearFraction(d1, d2)} through {@link DayCounter#yearFractionToDate} and
     * asserts the recovered yearFraction matches the original via {@link Closeness#isCloseEnough} — sweep
     * runs over a 1090-day window and the full canonical day-counter portfolio.
     *
     * <p>Tolerance tier: exact ({@code Closeness.isCloseEnough}, ~42 ULP).
     */
    @Test
    public void testYearFraction2DateBulk() {
        QL.info("Testing bulk dates for YearFractionToDate ...");

        final DayCounter[] dayCounters = {
                new Actual365Fixed(),
                new Actual365Fixed(Actual365Fixed.Convention.NoLeap),
                new Actual360(),
                new Actual360(true),
                new Actual36525(),
                new Actual36525(true),
                new Actual364(),
                new Actual366(),
                new Actual366(true),
                new ActualActual(ActualActual.Convention.ISDA),
                new ActualActual(ActualActual.Convention.ISMA),
                new ActualActual(ActualActual.Convention.Bond),
                new ActualActual(ActualActual.Convention.Historical),
                new ActualActual(ActualActual.Convention.Actual365),
                new ActualActual(ActualActual.Convention.AFB),
                new ActualActual(ActualActual.Convention.Euro),
                new Business252(),
                new Thirty360(Thirty360.Convention.USA),
                new Thirty360(Thirty360.Convention.BondBasis),
                new Thirty360(Thirty360.Convention.European),
                new Thirty360(Thirty360.Convention.EurobondBasis),
                new Thirty360(Thirty360.Convention.Italian),
                new Thirty360(Thirty360.Convention.German),
                new Thirty360(Thirty360.Convention.ISMA),
                new Thirty360(Thirty360.Convention.ISDA),
                new Thirty360(Thirty360.Convention.NASD),
                new Thirty365(),
                new SimpleDayCounter()
        };

        final Date base = new Date(1, Month.January, 2020);
        for (final DayCounter dc : dayCounters) {
            for (int i = -360; i < 730; ++i) {
                final Date today = base.add(new Period(i, TimeUnit.Days));
                final Date target = today.add(new Period(i, TimeUnit.Days));

                final double t = dc.yearFraction(today, target);
                final Date time2Date = DayCounter.yearFractionToDate(dc, today, t);
                final double tNew = dc.yearFraction(today, time2Date);

                if (!Closeness.isCloseEnough(t, tNew)) {
                    fail("\ntoday      : " + today
                            + "\ntarget     : " + target
                            + "\ninverse    : " + time2Date
                            + "\ntime diff  : " + (t - tNew)
                            + "\nday counter: " + dc.name());
                }
            }
        }
    }

    /**
     * Faithful port of {@code test-suite/daycounters.cpp::testYearFraction2DateRounding} (lines 1367-1384).
     *
     * <p>For each offset in {@code [0, 0.4999]} the inverse must round DOWN to {@code d2}; for offsets in
     * {@code [0.5, 1.0]} it must round UP to {@code d2 + 1 day}.
     */
    @Test
    public void testYearFraction2DateRounding() {
        QL.info("Testing YearFractionToDate rounding to closer date...");

        final DayCounter[] dayCounters = {
                new Thirty360(Thirty360.Convention.USA),
                new Actual360()
        };
        final Date d1 = new Date(1, Month.February, 2023);
        final Date d2 = new Date(17, Month.February, 2124);

        for (final DayCounter dc : dayCounters) {
            final double t = dc.yearFraction(d1, d2);
            for (double offset = 0.0; offset < 1.0 + 1e-10; offset += 0.05) {
                final Date inv = DayCounter.yearFractionToDate(dc, d1, t + offset / 360.0);
                if (offset < 0.4999) {
                    if (!inv.equals(d2)) {
                        fail("rounding mismatch dc=" + dc.name() + " offset=" + offset
                                + " inv=" + inv + " expected=" + d2);
                    }
                } else {
                    final Date d2Plus = d2.add(new Period(1, TimeUnit.Days));
                    if (!inv.equals(d2Plus)) {
                        fail("rounding mismatch dc=" + dc.name() + " offset=" + offset
                                + " inv=" + inv + " expected=" + d2Plus);
                    }
                }
            }
        }
    }

    /**
     * Port of v1.42.1 test-suite/daycounters.cpp::testIntraday (lines 1094-1120).
     *
     * <p>Phase 1.3 D5-D-intraday: exercises that ActualActual(ISDA),
     * Actual365Fixed, and Actual360 honour the intraday {@code fractionOfDay()}
     * component of a Date. The C++ test expects the formula:
     * <pre>
     *   yf(d1, d2) = ((12*60+34)*60 + 17 + 0.231298)
     *                * yf(d1, d1+1) / 86400
     *                + yf(d1, d1+2)
     * </pre>
     * which equals {@code yf(d1, d1+2) + fractionOfDay(d2) * yf(d1, d1+1)}.
     * Both d1+1 and d1+2 are pure day-only Dates, so {@code yf(d1, d1+1)/86400}
     * is the per-second fraction; the test verifies that adding two days of
     * day-resolution plus the intraday fraction reproduces yf(d1, d2).
     */
    @Test
    public void testIntraday() {
        QL.info("Testing intraday behavior of day counter...");

        final Date d1 = new Date(12, Month.February, 2015);
        final Date d2 = new Date(14, Month.February, 2015, 12, 34, 17, 1, 230298);

        final double tol = 100.0 * 1e-16; // 100 * QL_EPSILON

        final DayCounter[] dayCounters = {
                new ActualActual(ActualActual.Convention.ISDA),
                new Actual365Fixed(),
                new Actual360()
        };

        for (final DayCounter dc : dayCounters) {
            final double expected = ((12 * 60 + 34) * 60 + 17 + 0.231298)
                                  * dc.yearFraction(d1, d1.add(new Period(1, TimeUnit.Days))) / 86400.0
                                  + dc.yearFraction(d1, d1.add(new Period(2, TimeUnit.Days)));

            final double calc = dc.yearFraction(d1, d2);
            if (Math.abs(calc - expected) > tol) {
                fail("can not reproduce result for day counter " + dc.name()
                        + "  calculated=" + calc + "  expected=" + expected);
            }

            final double calcReverse = dc.yearFraction(d2, d1);
            if (Math.abs(calcReverse + expected) > tol) {
                fail("can not reproduce reverse result for day counter " + dc.name()
                        + "  calculated=" + calcReverse + "  expected=" + (-expected));
            }
        }
    }
}
