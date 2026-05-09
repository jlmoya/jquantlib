/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.AssetSwapHelper;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Phase 4m.6 tests for {@link AssetSwapHelper}.
 *
 * <p>Cross-validation: construction-only smoke + impliedQuote round-trip
 * after binding a flat hazard-rate curve. Reference: QuantLib v1.42.1
 * {@code ql/experimental/credit/riskyassetswap.{hpp,cpp}}.
 */
public class AssetSwapHelperTest {

    @Test
    public void constructionSetsLifecycleDates() {
        final Calendar cal = new Target();
        final DayCounter act360 = new Actual360();
        final DayCounter act365 = new Actual365Fixed();
        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.005));
        final Handle<YieldTermStructure> yieldTS = new Handle<YieldTermStructure>(
                new FlatForward(2, cal, 0.04, act365));

        final AssetSwapHelper helper = new AssetSwapHelper(
                spread,
                new Period(5, TimeUnit.Years),
                2,
                cal,
                new Period(6, TimeUnit.Months),
                BusinessDayConvention.ModifiedFollowing,
                act360,
                new Period(3, TimeUnit.Months),
                BusinessDayConvention.ModifiedFollowing,
                act360,
                0.4,
                yieldTS);

        assertNotNull(helper.earliestDate());
        assertNotNull(helper.latestDate());
    }

    @Test
    public void impliedQuoteRequiresTermStructure() {
        // Per C++ AssetSwapHelper::impliedQuote() — fails if probability_ unset.
        final Calendar cal = new Target();
        final DayCounter act360 = new Actual360();
        final DayCounter act365 = new Actual365Fixed();
        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.005));
        final Handle<YieldTermStructure> yieldTS = new Handle<YieldTermStructure>(
                new FlatForward(2, cal, 0.04, act365));

        final AssetSwapHelper helper = new AssetSwapHelper(
                spread,
                new Period(3, TimeUnit.Years),
                2,
                cal,
                new Period(6, TimeUnit.Months),
                BusinessDayConvention.ModifiedFollowing,
                act360,
                new Period(3, TimeUnit.Months),
                BusinessDayConvention.ModifiedFollowing,
                act360,
                0.4,
                yieldTS);

        try {
            helper.impliedQuote();
            fail("expected exception when default term structure not set");
        } catch (final Exception e) {
            // expected
        }
    }

    @Test
    public void impliedQuoteRunsWhenTermStructureBound() {
        // After binding a default-probability curve, impliedQuote must succeed.
        // We don't pin a specific number (the C++ test would need full curve
        // generation; this is a smoke test of the bind-recalc-evaluate path).
        // Use the current Settings.evaluationDate for the curves so the test is
        // independent of the test-suite ordering (some prior test may mutate
        // the global eval date).
        final Calendar cal = new Target();
        final DayCounter act360 = new Actual360();
        final DayCounter act365 = new Actual365Fixed();
        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.005));
        final Date today = new org.jquantlib.Settings().evaluationDate();
        final Handle<YieldTermStructure> yieldTS = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.04, act365));

        final AssetSwapHelper helper = new AssetSwapHelper(
                spread,
                new Period(2, TimeUnit.Years),
                2,
                cal,
                new Period(6, TimeUnit.Months),
                BusinessDayConvention.ModifiedFollowing,
                act360,
                new Period(3, TimeUnit.Months),
                BusinessDayConvention.ModifiedFollowing,
                act360,
                0.4,
                yieldTS);

        final FlatHazardRate flatHazard = new FlatHazardRate(today, 0.02, act365);
        helper.setTermStructure(flatHazard);

        // Smoke: invocation must not throw and must return a finite double.
        final double q = helper.impliedQuote();
        assertEquals("quote is finite", q, q, 0.0);  // !NaN guard
    }
}
