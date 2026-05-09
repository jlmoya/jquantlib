/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.FactorSpreadedHazardRateCurve;
import org.jquantlib.experimental.credit.SpreadedHazardRateCurve;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 4m foundation tests for {@link SpreadedHazardRateCurve} and
 * {@link FactorSpreadedHazardRateCurve}.
 *
 * <p>Cross-validation: both classes are pure inline wrappers in C++:
 * <ul>
 *   <li>SpreadedHazardRateCurve: {@code original.hazardRate(t,true) + spread.value()}</li>
 *   <li>FactorSpreadedHazardRateCurve: {@code original.hazardRate(t,true) * (1+spread)}</li>
 * </ul>
 * Source: {@code ql/experimental/credit/{spreadedhazardratecurve,
 * factorspreadedhazardratecurve}.hpp} v1.42.1.
 *
 * <p>Reference values are derived analytically from a {@link FlatHazardRate}
 * base curve — no probe binary needed.
 */
public class SpreadedHazardRateCurveTest {

    private Date savedEvalDate;

    @Before
    public void setUp() {
        savedEvalDate = new Settings().evaluationDate();
        new Settings().setEvaluationDate(new Date(15, Month.June, 2010));
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvalDate);
    }

    @Test
    public void spreadedHazardAdditive() {
        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.June, 2010);
        final FlatHazardRate base = new FlatHazardRate(today, 0.02, dc);
        final Handle<DefaultProbabilityTermStructure> baseH = new Handle<DefaultProbabilityTermStructure>(base);
        final SimpleQuote spreadQ = new SimpleQuote(0.005);
        final Handle<Quote> spread = new Handle<Quote>(spreadQ);

        final SpreadedHazardRateCurve curve = new SpreadedHazardRateCurve(baseH, spread);
        assertNotNull(curve);
        // hazardRate(t) ≡ base.hazardRate(t) + spread = 0.02 + 0.005 = 0.025
        final double h = curve.hazardRate(0.5);
        assertEquals(0.025, h, 1.0e-12);
    }

    @Test
    public void spreadedHazardZeroSpreadEqualsBase() {
        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.June, 2010);
        final FlatHazardRate base = new FlatHazardRate(today, 0.02, dc);
        final Handle<DefaultProbabilityTermStructure> baseH = new Handle<DefaultProbabilityTermStructure>(base);
        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.0));
        final SpreadedHazardRateCurve curve = new SpreadedHazardRateCurve(baseH, spread);
        assertEquals(base.hazardRate(0.5), curve.hazardRate(0.5), 1.0e-15);
    }

    @Test
    public void spreadedHazardDelegatesDayCounterAndCalendar() {
        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.June, 2010);
        final FlatHazardRate base = new FlatHazardRate(today, 0.02, dc);
        final Handle<DefaultProbabilityTermStructure> baseH = new Handle<DefaultProbabilityTermStructure>(base);
        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.01));
        final SpreadedHazardRateCurve curve = new SpreadedHazardRateCurve(baseH, spread);
        // Day-count and ref-date should be the original's.
        assertEquals(dc.name(), curve.dayCounter().name());
        assertEquals(today, curve.referenceDate());
        // maxDate: FlatHazardRate uses Date.maxDate.
        assertTrue(curve.maxDate().compareTo(today) > 0);
    }

    @Test
    public void factorSpreadedHazardMultiplicative() {
        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.June, 2010);
        final FlatHazardRate base = new FlatHazardRate(today, 0.02, dc);
        final Handle<DefaultProbabilityTermStructure> baseH = new Handle<DefaultProbabilityTermStructure>(base);
        final SimpleQuote spreadQ = new SimpleQuote(0.5); // 50% factor uplift
        final Handle<Quote> spread = new Handle<Quote>(spreadQ);

        final FactorSpreadedHazardRateCurve curve = new FactorSpreadedHazardRateCurve(baseH, spread);
        // hazardRate ≡ base.hazardRate * (1 + 0.5) = 0.02 * 1.5 = 0.03
        assertEquals(0.03, curve.hazardRate(0.5), 1.0e-12);
    }

    @Test
    public void factorSpreadedHazardZeroFactorEqualsBase() {
        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.June, 2010);
        final FlatHazardRate base = new FlatHazardRate(today, 0.02, dc);
        final Handle<DefaultProbabilityTermStructure> baseH = new Handle<DefaultProbabilityTermStructure>(base);
        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.0));
        final FactorSpreadedHazardRateCurve curve = new FactorSpreadedHazardRateCurve(baseH, spread);
        assertEquals(base.hazardRate(0.5), curve.hazardRate(0.5), 1.0e-15);
    }

    @Test
    public void spreadedHazardLiveLinkAfterQuoteUpdate() {
        // Verify the curve reflects spread changes (no caching).
        final DayCounter dc = new Actual360();
        final Date today = new Date(15, Month.June, 2010);
        final FlatHazardRate base = new FlatHazardRate(today, 0.02, dc);
        final Handle<DefaultProbabilityTermStructure> baseH = new Handle<DefaultProbabilityTermStructure>(base);
        final SimpleQuote spreadQ = new SimpleQuote(0.005);
        final Handle<Quote> spread = new Handle<Quote>(spreadQ);
        final SpreadedHazardRateCurve curve = new SpreadedHazardRateCurve(baseH, spread);
        assertEquals(0.025, curve.hazardRate(0.5), 1.0e-12);
        // Push a new spread.
        spreadQ.setValue(0.015);
        assertEquals(0.035, curve.hazardRate(0.5), 1.0e-12);
    }
}
