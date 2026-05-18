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
import org.jquantlib.indexes.Euribor3M;
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
import org.junit.Test;

/**
 * Port of QuantLib v1.42.1 test-suite/indexes.cpp (224 LOC).
 *
 * Phase 5c — calendar/time/indexes test ports.
 *
 * Phase 5e.5b-CFC-d-14: {@code Index.hasHistoricalFixing}, {@code BespokeCalendar},
 * and {@code CustomIborIndex} ported from C++ v1.42.1 — un-ignores
 * {@code testFixingHasHistoricalFixing} and {@code testCustomIborIndex}.
 *
 * Phase 5e.5b-CFC-d-189: {@code Brlcdi} index ported from C++ v1.42.1 — un-ignores
 * {@code testCdiIndex}.
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

    /**
     * Verifies {@code Index.hasHistoricalFixing(Date)} across freshly
     * constructed and reused index instances, with addFixing /
     * clearHistories cycles.
     *
     * Reference: test-suite/indexes.cpp:79-118.
     */
    @Test
    public void testFixingHasHistoricalFixing() {
        QL.info("Testing if index has historical fixings...");

        final Euribor3M euribor3M = new Euribor3M();
        final Euribor6M euribor6M = new Euribor6M();
        final Euribor6M euribor6MAlt = new Euribor6M();

        Date today = new org.jquantlib.Settings().evaluationDate().clone();
        while (!euribor6M.isValidFixingDate(today)) {
            today.dec();
        }

        euribor6M.addFixing(today, 0.01);

        // Euribor3M never had a fixing — must report false.
        assertEquals("historical fixing erroneously found for " + euribor3M.name(),
                false, euribor3M.hasHistoricalFixing(today));

        // Euribor6M had a fixing — must report true (whether queried via the
        // instance that added it or a different instance with the same name,
        // since IndexManager keys on name()).
        assertEquals("historical fixing not found for " + euribor6M.name(),
                true, euribor6M.hasHistoricalFixing(today));
        assertEquals("historical fixing not found for " + euribor6MAlt.name(),
                true, euribor6MAlt.hasHistoricalFixing(today));

        org.jquantlib.indexes.IndexManager.getInstance().clearHistories();

        assertEquals("historical fixing erroneously found for " + euribor3M.name(),
                false, euribor3M.hasHistoricalFixing(today));
        assertEquals("historical fixing erroneously found for " + euribor6M.name(),
                false, euribor6M.hasHistoricalFixing(today));
        assertEquals("historical fixing erroneously found for " + euribor6MAlt.name(),
                false, euribor6MAlt.hasHistoricalFixing(today));
    }

    /**
     * Verifies {@code CustomIborIndex} with separate fixing / value /
     * maturity calendars; clone behavior; fixingDate / valueDate /
     * maturityDate semantics.
     *
     * Reference: test-suite/indexes.cpp:148-200.
     */
    @Test
    public void testCustomIborIndex() {
        QL.info("Testing CustomIborIndex...");

        final org.jquantlib.time.calendars.BespokeCalendar fixCal =
                new org.jquantlib.time.calendars.BespokeCalendar("Fixings");
        fixCal.addHoliday(new Date(8, Month.January, 2025));

        final org.jquantlib.time.calendars.BespokeCalendar valCal =
                new org.jquantlib.time.calendars.BespokeCalendar("Value");
        valCal.addHoliday(new Date(21, Month.January, 2025));

        final org.jquantlib.time.calendars.BespokeCalendar matCal =
                new org.jquantlib.time.calendars.BespokeCalendar("Maturity");
        matCal.addHoliday(new Date(7, Month.January, 2025));
        matCal.addHoliday(new Date(15, Month.January, 2025));
        matCal.addHoliday(new Date(23, Month.April, 2025));
        matCal.addHoliday(new Date(30, Month.April, 2025));

        final org.jquantlib.indexes.ibor.CustomIborIndex ibor =
                new org.jquantlib.indexes.ibor.CustomIborIndex(
                        "Custom Ibor",
                        new Period(3, TimeUnit.Months),
                        2,
                        new Currency(),
                        fixCal,
                        valCal,
                        matCal,
                        BusinessDayConvention.ModifiedFollowing,
                        true,
                        new Actual360());

        final org.jquantlib.quotes.Handle<IborIndex> iborCloneHandle =
                ibor.clone(new org.jquantlib.quotes.Handle<org.jquantlib.termstructures.YieldTermStructure>());
        final IborIndex iborClone = iborCloneHandle.currentLink();

        // Exercise both the original and the cloned index — both should give
        // identical answers since clone preserves all calendars / convention.
        for (final IborIndex index : new IborIndex[] { ibor, iborClone }) {
            final org.jquantlib.indexes.ibor.CustomIborIndex asCustom =
                    (org.jquantlib.indexes.ibor.CustomIborIndex) index;

            assertEquals("fixingCalendar mismatch",
                    fixCal.name(), index.fixingCalendar().name());
            assertEquals("valueCalendar mismatch",
                    valCal.name(), asCustom.valueCalendar().name());
            assertEquals("maturityCalendar mismatch",
                    matCal.name(), asCustom.maturityCalendar().name());

            // valueDate(8-Jan-2025): 8-Jan is a fixing-calendar holiday →
            // isValidFixingDate fails. The C++ test asserts this throws.
            try {
                index.valueDate(new Date(8, Month.January, 2025));
                fail("expected exception when valueDate called with invalid fixing date 8-Jan-2025");
            } catch (final Exception expected) {
                // Phase 5e.5b-CFC-d-14: matches C++ ExpectedErrorMessage
                // "Fixing date January 8th, 2025 is not valid".
            }

            assertEquals("valueDate(7-Jan-2025) mismatch",
                    new Date(9, Month.January, 2025),
                    index.valueDate(new Date(7, Month.January, 2025)));
            assertEquals("valueDate(13-Jan-2025) mismatch",
                    new Date(16, Month.January, 2025),
                    index.valueDate(new Date(13, Month.January, 2025)));
            assertEquals("valueDate(20-Jan-2025) mismatch",
                    new Date(23, Month.January, 2025),
                    index.valueDate(new Date(20, Month.January, 2025)));

            assertEquals("fixingDate(23-Jan-2025) mismatch",
                    new Date(20, Month.January, 2025),
                    index.fixingDate(new Date(23, Month.January, 2025)));
            assertEquals("fixingDate(16-Jan-2025) mismatch",
                    new Date(14, Month.January, 2025),
                    index.fixingDate(new Date(16, Month.January, 2025)));
            assertEquals("fixingDate(10-Jan-2025) mismatch",
                    new Date(7, Month.January, 2025),
                    index.fixingDate(new Date(10, Month.January, 2025)));

            assertEquals("maturityDate(23-Jan-2025) mismatch",
                    new Date(24, Month.April, 2025),
                    index.maturityDate(new Date(23, Month.January, 2025)));
            assertEquals("maturityDate(30-Jan-2025) mismatch",
                    new Date(29, Month.April, 2025),
                    index.maturityDate(new Date(30, Month.January, 2025)));
            assertEquals("maturityDate(28-Feb-2025) mismatch",
                    new Date(31, Month.May, 2025),
                    index.maturityDate(new Date(28, Month.February, 2025)));
        }
    }

    /**
     * Verifies Brazil CDI forecastFixing against the discount-factor
     * approximation {@code (Df_start / Df_end)^252 - 1} with 1e-5 / 1e-6
     * tolerances.
     * <p>
     * Reference: test-suite/indexes.cpp:202-221.
     */
    @Test
    public void testCdiIndex() {
        QL.info("Testing Brazil CDI forecastFixing...");

        final Date today = new org.jquantlib.Settings().evaluationDate().clone();

        final org.jquantlib.quotes.SimpleQuote flatRate =
                new org.jquantlib.quotes.SimpleQuote(0.05);
        final org.jquantlib.quotes.Handle<org.jquantlib.quotes.Quote> rateHandle =
                new org.jquantlib.quotes.Handle<org.jquantlib.quotes.Quote>(flatRate);

        final org.jquantlib.termstructures.YieldTermStructure flatFwd =
                new org.jquantlib.termstructures.yieldcurves.FlatForward(
                        today, rateHandle,
                        new org.jquantlib.daycounters.Business252());
        final org.jquantlib.quotes.Handle<org.jquantlib.termstructures.YieldTermStructure> ts =
                new org.jquantlib.quotes.Handle<org.jquantlib.termstructures.YieldTermStructure>(flatFwd);

        final org.jquantlib.indexes.ibor.Brlcdi cdi =
                new org.jquantlib.indexes.ibor.Brlcdi(ts);

        final org.jquantlib.time.calendars.Brazil settlement =
                new org.jquantlib.time.calendars.Brazil(
                        org.jquantlib.time.calendars.Brazil.Market.SETTLEMENT);
        final Date testFixingDate = settlement.advance(
                today, new Period(1, TimeUnit.Months));

        final double forecast = cdi.fixing(testFixingDate, true);

        final double discountStart = ts.currentLink().discount(testFixingDate);
        final Date endDate = settlement.advance(
                testFixingDate, new Period(1, TimeUnit.Days));
        final double discountEnd = ts.currentLink().discount(endDate);

        final double approx = Math.pow(discountStart / discountEnd, 252.0) - 1.0;

        assertTrue("discrepancy in fixing forecast computation: |0.05127 - "
                + forecast + "| >= 1e-5",
                Math.abs(0.05127 - forecast) < 1.0e-5);
        assertTrue("discrepancy in fixing forecast computation with approximation: |"
                + approx + " - " + forecast + "| >= 1e-6",
                Math.abs(approx - forecast) < 1.0e-6);
    }
}
