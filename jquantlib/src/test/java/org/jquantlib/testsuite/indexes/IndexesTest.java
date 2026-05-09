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
package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.BMAIndex;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.Index;
import org.jquantlib.indexes.InterestRateIndex;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Port of QuantLib v1.42.1 test-suite/indexes.cpp (224 LOC).
 *
 * Phase 5c — calendar/time/indexes test ports.
 *
 * Phase 5c.5 deferrals: tests that exercise classes not yet ported:
 * <ul>
 *   <li>{@code testFixingHasHistoricalFixing} — needs {@code Index.hasHistoricalFixing}
 *       (a v1.42.1 addition); not present in Java {@link Index}.</li>
 *   <li>{@code testCustomIborIndex} — needs {@code BespokeCalendar} and
 *       {@code CustomIborIndex} (both unported).</li>
 *   <li>{@code testCdiIndex} — needs {@code Cdi} index, {@code Brazil} business-252
 *       calendar with settlement variant, and {@code Business252} day counter.</li>
 * </ul>
 *
 * Reference: test-suite/indexes.cpp.
 *
 * @author Jose Moya
 */
public class IndexesTest {

    public IndexesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Verifies that observers registered with an {@link Index} are notified
     * when a fixing is added via {@code addFixing}.
     *
     * Reference: test-suite/indexes.cpp:42-77.
     *
     * Note: Java {@code Observable} uses {@code addObserver} where C++ uses
     * {@code registerWith}; semantics are equivalent.
     */
    @Test
    public void testFixingObservability() {
        QL.info("Testing observability of index fixings...");

        final InterestRateIndex i1 = new Euribor6M();
        final InterestRateIndex i2 = new BMAIndex();

        final Flag f1 = new Flag();
        i1.addObserver(f1);
        f1.lower();

        final Flag f2 = new Flag();
        i2.addObserver(f2);
        f2.lower();

        final Date today = Date.todaysDate();

        final Index euribor = new Euribor6M();

        Date d1 = today.clone();
        while (!euribor.isValidFixingDate(d1)) {
            d1.inc();
        }

        euribor.addFixing(d1, -0.003);
        if (!f1.isUp()) {
            fail("Observer was not notified of added Euribor fixing");
        }

        final Index bma = new BMAIndex();

        Date d2 = today.clone();
        while (!bma.isValidFixingDate(d2)) {
            d2.inc();
        }

        bma.addFixing(d2, 0.01);
        if (!f2.isUp()) {
            fail("Observer was not notified of added BMA fixing");
        }
    }

    /**
     * Verifies that an interest-rate index with tenor {@code 12*Months} and
     * one with tenor {@code 1*Years} produce the same name (canonical
     * normalization), and that 6-day vs 7-day index tenors yield strictly
     * increasing maturity dates.
     *
     * Reference: test-suite/indexes.cpp:120-146.
     */
    @Test
    public void testTenorNormalization() {
        QL.info("Testing that interest-rate index tenor is normalized correctly...");

        final IborIndex i12m = new IborIndex(
                "foo",
                new Period(12, TimeUnit.Months),
                2,
                new Currency(),
                new Target(),
                BusinessDayConvention.Following,
                false,
                new Actual360());
        final IborIndex i1y = new IborIndex(
                "foo",
                new Period(1, TimeUnit.Years),
                2,
                new Currency(),
                new Target(),
                BusinessDayConvention.Following,
                false,
                new Actual360());

        assertEquals("12M index and 1Y index yield different names",
                i12m.name(), i1y.name());

        final IborIndex i6d = new IborIndex(
                "foo",
                new Period(6, TimeUnit.Days),
                2,
                new Currency(),
                new Target(),
                BusinessDayConvention.Following,
                false,
                new Actual360());
        final IborIndex i7d = new IborIndex(
                "foo",
                new Period(7, TimeUnit.Days),
                2,
                new Currency(),
                new Target(),
                BusinessDayConvention.Following,
                false,
                new Actual360());

        final Date testDate = new Date(28, Month.April, 2023);
        final Date maturity6d = i6d.maturityDate(testDate);
        final Date maturity7d = i7d.maturityDate(testDate);

        assertTrue(
                "inconsistent maturity dates and tenors\n  maturity date for 6-days index: "
                        + maturity6d + "\n  maturity date for 7-days index: " + maturity7d,
                maturity6d.lt(maturity7d));
    }

    @Ignore("Phase 5c.5: Index.hasHistoricalFixing (v1.42.1 addition) not yet ported to Java Index")
    @Test
    public void testFixingHasHistoricalFixing() {
        // Tests Index.hasHistoricalFixing across Euribor3M / Euribor6M after
        // addFixing/clearHistories cycles.
        // Reference: test-suite/indexes.cpp:79-118.
    }

    @Ignore("Phase 5c.5: BespokeCalendar and CustomIborIndex not yet ported from v1.42.1")
    @Test
    public void testCustomIborIndex() {
        // Verifies CustomIborIndex with separate fixing / value / maturity
        // calendars; clone behavior; fixingDate / valueDate / maturityDate.
        // Reference: test-suite/indexes.cpp:148-200.
    }

    @Ignore("Phase 5c.5: Brazil CDI index, Business252 day counter, and Brazil(Settlement) calendar variant not yet ported from v1.42.1")
    @Test
    public void testCdiIndex() {
        // Verifies Brazil CDI forecastFixing against the discount-factor
        // approximation (1+r)^252 - 1 with 1e-5 / 1e-6 tolerances.
        // Reference: test-suite/indexes.cpp:202-221.
    }
}
