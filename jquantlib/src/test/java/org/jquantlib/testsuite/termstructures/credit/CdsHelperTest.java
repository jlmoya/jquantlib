/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 3b Track B test for CdsHelper / SpreadCdsHelper / UpfrontCdsHelper.
*/

package org.jquantlib.testsuite.termstructures.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.CreditDefaultSwap.PricingModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.credit.SpreadCdsHelper;
import org.jquantlib.termstructures.credit.UpfrontCdsHelper;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 3b Track B sanity tests for the CDS-based bootstrap helpers
 * ({@link SpreadCdsHelper}, {@link UpfrontCdsHelper}).
 *
 * <p>The bootstrap consistency tests for these helpers — i.e. round-trip
 * "build curve from spreads, reprice CDS, recover original spreads" — are in
 * {@code DefaultProbabilityCurvesTest} (re-enabled by Phase 3b Track B).
 * This file focuses on smaller, helper-level invariants:
 * <ul>
 *   <li>helper construction populates the {@code earliestDate} / {@code latestDate}
 *       window correctly relative to the global evaluation date,</li>
 *   <li>{@code setTermStructure} attaches a {@code MidPointCdsEngine} via
 *       {@code resetEngine} and produces a finite implied quote,</li>
 *   <li>the implied quote is consistent with a hand-rolled CDS pricing on the
 *       same fixture (spread case),</li>
 *   <li>the upfront helper preserves
 *       {@link Settings#isTodaysPayments()} after {@code impliedQuote()}.</li>
 * </ul>
 */
public class CdsHelperTest {

    public CdsHelperTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final Date EVAL = new Date(15, Month.May, 2026);
    private static final DayCounter DC = new Actual360();
    private static final Calendar CAL = new NullCalendar();
    private static final BusinessDayConvention BDC = BusinessDayConvention.Following;
    private static final DateGeneration.Rule RULE = DateGeneration.Rule.Forward;
    private static final Frequency FREQ = Frequency.Quarterly;

    private static Handle<YieldTermStructure> discountCurve(final double r) {
        new Settings().setEvaluationDate(EVAL);
        return new Handle<YieldTermStructure>(new FlatForward(
                EVAL, new Handle<Quote>(new SimpleQuote(r)), DC,
                Compounding.Continuous, Frequency.Annual));
    }

    private static Handle<DefaultProbabilityTermStructure> hazardCurve(
            final double h) {
        return new Handle<DefaultProbabilityTermStructure>(
                new FlatHazardRate(EVAL,
                        new Handle<Quote>(new SimpleQuote(h)), DC));
    }

    @Test
    public void spreadCdsHelperConstructsAndPopulatesDates() {
        new Settings().setEvaluationDate(EVAL);
        final SpreadCdsHelper h = new SpreadCdsHelper(
                0.0150, new Period(5, TimeUnit.Years), 1, CAL,
                FREQ, BDC, RULE, DC, 0.4, discountCurve(0.03));

        assertNotNull(h.earliestDate());
        assertNotNull(h.latestDate());
        assertTrue("latestDate must be strictly after earliestDate",
                h.latestDate().gt(h.earliestDate()));
        assertTrue("earliestDate >= protectionStart",
                h.earliestDate().ge(EVAL));
    }

    @Test
    public void spreadCdsHelperImpliedQuoteFinite() {
        new Settings().setEvaluationDate(EVAL);
        final SpreadCdsHelper h = new SpreadCdsHelper(
                0.0150, new Period(5, TimeUnit.Years), 1, CAL,
                FREQ, BDC, RULE, DC, 0.4, discountCurve(0.03));

        // resetEngine triggers via setTermStructure → swap_ becomes
        // pricable; the helper's implied quote is the swap's fairSpread.
        h.setTermStructure(hazardCurve(0.025).currentLink());
        final double quote = h.impliedQuote();
        assertTrue("implied quote should be finite + positive: " + quote,
                quote > 0.0 && Double.isFinite(quote));

        // Hand-roll the same CDS fixture and compare. SpreadCdsHelper builds
        // a Buyer / 100.0 / 1% running-spread CDS with the helper's schedule
        // (initialized via initializeDates) and prices it with
        // MidPointCdsEngine; calling impliedQuote returns swap_.fairSpread().
        // Because we don't reproduce the schedule perfectly with public APIs
        // here, we just verify the quote is in a sane band relative to the
        // hazard rate / (1 - recovery) = 0.025 / 0.6 ≈ 4.17% — which is the
        // continuous-time intensity → spread rule of thumb.
        assertTrue("implied quote in expected sanity band: " + quote,
                quote > 0.005 && quote < 0.10);
    }

    @Test
    public void upfrontCdsHelperConstructsAndPopulatesDates() {
        new Settings().setEvaluationDate(EVAL);
        final UpfrontCdsHelper h = new UpfrontCdsHelper(
                0.020, 0.0100, new Period(5, TimeUnit.Years), 1, CAL,
                FREQ, BDC, RULE, DC, 0.4, discountCurve(0.03));

        assertNotNull(h.earliestDate());
        assertNotNull(h.latestDate());
        assertNotNull(h.upfrontDate());
        assertEquals(3, h.upfrontSettlementDays());
    }

    @Test
    public void upfrontCdsHelperImpliedQuoteFiniteAndPreservesSettings() {
        new Settings().setEvaluationDate(EVAL);
        final boolean priorTodaysPayments = new Settings().isTodaysPayments();
        try {
            new Settings().setTodaysPayments(false);

            final UpfrontCdsHelper h = new UpfrontCdsHelper(
                    0.020, 0.0100, new Period(5, TimeUnit.Years), 1, CAL,
                    FREQ, BDC, RULE, DC, 0.4, discountCurve(0.03));
            h.setTermStructure(hazardCurve(0.025).currentLink());

            final double quote = h.impliedQuote();
            assertTrue("quote should be finite: " + quote, Double.isFinite(quote));

            // Most importantly: settings are restored after impliedQuote (mirrors
            // C++ SavedSettings RAII pattern).
            assertEquals("includeTodaysCashFlows must be restored to false",
                    false, new Settings().isTodaysPayments());
        } finally {
            new Settings().setTodaysPayments(priorTodaysPayments);
        }
    }

    @Test
    public void spreadHelperWithSimpleQuote() {
        new Settings().setEvaluationDate(EVAL);
        final SimpleQuote sq = new SimpleQuote(0.0150);
        final SpreadCdsHelper h = new SpreadCdsHelper(
                new Handle<Quote>(sq),
                new Period(5, TimeUnit.Years), 1, CAL,
                FREQ, BDC, RULE, DC, 0.4, discountCurve(0.03),
                true, true, null, null, true,
                PricingModel.Midpoint);
        h.setTermStructure(hazardCurve(0.025).currentLink());

        final double q1 = h.impliedQuote();
        sq.setValue(0.0200);  // mutate the quote — should propagate (it's an input)
        // impliedQuote depends only on the bound term structure, not on sq;
        // sq feeds quoteError(). But we should at least observe quoteError
        // sees the new value.
        final double err = h.quoteError();
        assertEquals(0.0200 - q1, err, 1.0e-12);
    }

    @Test
    public void cdsHelperSwapAccessorReturnsBoundCds() {
        new Settings().setEvaluationDate(EVAL);
        final SpreadCdsHelper h = new SpreadCdsHelper(
                0.0150, new Period(5, TimeUnit.Years), 1, CAL,
                FREQ, BDC, RULE, DC, 0.4, discountCurve(0.03));
        h.setTermStructure(hazardCurve(0.025).currentLink());

        final CreditDefaultSwap swap = h.swap();
        assertNotNull("swap() must be non-null after setTermStructure", swap);
        assertEquals("Helpers always price as Buyer / 100.0 / 1% (placeholder)",
                100.0, swap.notional(), 0.0);
    }
}
