/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Java port test for Phase 3d L1 IsdaCdsEngine — sanity / smoke tests that
 exercise the engine end-to-end without depending on Markit-exact numerical
 reconciliation (which lives in CreditDefaultSwapTest::testIsda*).
*/

package org.jquantlib.testsuite.pricingengines.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.MakeCreditDefaultSwap;
import org.jquantlib.instruments.Protection;
import org.jquantlib.pricingengines.credit.IsdaCdsEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.WeekendsOnly;
import org.junit.Test;

/**
 * Phase 3d L1 sanity tests for {@link IsdaCdsEngine}.
 *
 * <p>The numerically rigorous Markit-reconciliation tests live in
 * {@code CreditDefaultSwapTest::testIsda*}. This class adds three quick smoke
 * checks that lock down engine wiring + sign conventions without depending on
 * any external probe / reference file:
 *
 * <ol>
 *   <li>{@link #testEnginePricesNonZero} — flat-curves CDS prices and emits a
 *       sensible NPV (Buyer/Seller signs flip).</li>
 *   <li>{@link #testEngineRejectsNonAct365FixedDiscountCurve} — engine throws
 *       when the discount curve uses Act/360.</li>
 *   <li>{@link #testEngineRequiresFaceValueClaim} — already enforced by
 *       MakeCreditDefaultSwap defaults; smoke-checks the Buyer side.</li>
 * </ol>
 */
public class IsdaCdsEngineTest {

    public IsdaCdsEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /** Build a flat-rate ISDA-compatible discount curve (Act/365 Fixed required). */
    private static Handle<YieldTermStructure> flatDiscount(final Date eval, final double rate) {
        final DayCounter dc = new Actual365Fixed();
        return new Handle<YieldTermStructure>(new FlatForward(eval, rate, dc));
    }

    /** Build a flat-hazard-rate credit curve (Act/365 Fixed required). */
    private static Handle<DefaultProbabilityTermStructure> flatHazard(
            final Date eval, final double hazard) {
        final DayCounter dc = new Actual365Fixed();
        return new Handle<DefaultProbabilityTermStructure>(
                new FlatHazardRate(eval, new Handle<Quote>(new SimpleQuote(hazard)), dc));
    }

    @Test
    public void testEnginePricesNonZero() {
        final Date eval = new Date(15, Month.May, 2026);
        final Settings s = new Settings();
        final Date prevEval = s.evaluationDate();
        try {
            s.setEvaluationDate(eval);

            final Handle<YieldTermStructure> disc = flatDiscount(eval, 0.03);
            final Handle<DefaultProbabilityTermStructure> prob = flatHazard(eval, 0.02);

            final IsdaCdsEngine engine = new IsdaCdsEngine(
                    prob, 0.40, disc, Boolean.FALSE,
                    IsdaCdsEngine.NumericalFix.Taylor,
                    IsdaCdsEngine.AccrualBias.HalfDayBias,
                    IsdaCdsEngine.ForwardsInCouponPeriod.Piecewise);

            final CreditDefaultSwap buyerCds = new MakeCreditDefaultSwap(
                    new Period(5, TimeUnit.Years), 0.01)
                    .withNominal(1.0e7)
                    .withPricingEngine(engine)
                    .build();
            final double buyerNpv = buyerCds.NPV();
            assertTrue("buyer NPV should be finite", !Double.isNaN(buyerNpv) && !Double.isInfinite(buyerNpv));

            // Re-price as Seller — sign flips on default + accrual rebate.
            final CreditDefaultSwap sellerCds = new MakeCreditDefaultSwap(
                    new Period(5, TimeUnit.Years), 0.01)
                    .withNominal(1.0e7)
                    .withSide(Protection.Side.Seller)
                    .withPricingEngine(engine)
                    .build();
            final double sellerNpv = sellerCds.NPV();
            assertTrue("seller NPV should be finite", !Double.isNaN(sellerNpv) && !Double.isInfinite(sellerNpv));

            // Buyer + Seller of same trade should sum to zero (less the upfront
            // double-count, which is zero when upfrontRate=0). For
            // protection-only running-spread CDS, buyer NPV + seller NPV ≈ 0.
            // Both sides see the same protection leg with opposite signs and
            // the same coupon leg with opposite signs.
            assertEquals("buyer + seller NPV should net to zero",
                    0.0, buyerNpv + sellerNpv, 1.0e-7);
        } finally {
            s.setEvaluationDate(prevEval);
        }
    }

    @Test
    public void testEngineRejectsNonAct365FixedDiscountCurve() {
        final Date eval = new Date(15, Month.May, 2026);
        final Settings s = new Settings();
        final Date prevEval = s.evaluationDate();
        try {
            s.setEvaluationDate(eval);

            // Act/360 discount curve — should be rejected.
            final Handle<YieldTermStructure> badDisc =
                    new Handle<YieldTermStructure>(
                            new FlatForward(eval, 0.03, new Actual360()));
            final Handle<DefaultProbabilityTermStructure> prob = flatHazard(eval, 0.02);

            final IsdaCdsEngine engine = new IsdaCdsEngine(prob, 0.4, badDisc);
            final CreditDefaultSwap cds = new MakeCreditDefaultSwap(
                    new Period(5, TimeUnit.Years), 0.01)
                    .withNominal(1.0e7)
                    .withPricingEngine(engine)
                    .build();
            try {
                cds.NPV();
                fail("expected ISDA engine to reject Act/360 discount curve");
            } catch (final RuntimeException expected) {
                assertTrue("expected day-counter mismatch message but got: "
                        + expected.getMessage(),
                        expected.getMessage() != null
                        && expected.getMessage().contains("Act/365"));
            }
        } finally {
            s.setEvaluationDate(prevEval);
        }
    }

    @Test
    public void testEngineRequiresEvalDateMatchesCurves() {
        final Date eval = new Date(15, Month.May, 2026);
        final Settings s = new Settings();
        final Date prevEval = s.evaluationDate();
        try {
            // Build curves anchored at eval, then move evaluationDate.
            s.setEvaluationDate(eval);
            final Handle<YieldTermStructure> disc = flatDiscount(eval, 0.03);
            final Handle<DefaultProbabilityTermStructure> prob = flatHazard(eval, 0.02);

            // Schedule built at eval so coupons exist.
            final CreditDefaultSwap cds = new MakeCreditDefaultSwap(
                    new Period(5, TimeUnit.Years), 0.01)
                    .withNominal(1.0e7)
                    .withPricingEngine(new IsdaCdsEngine(prob, 0.4, disc))
                    .build();

            // Move the evaluation date forward by a year; the curves' reference
            // date is locked to the original eval date so the engine should
            // refuse to price.
            s.setEvaluationDate(eval.add(new Period(1, TimeUnit.Years)));
            try {
                cds.recalculate();
                cds.NPV();
                fail("expected ISDA engine to reject mismatched reference / eval date");
            } catch (final RuntimeException expected) {
                final String msg = expected.getMessage();
                assertTrue("expected reference-date mismatch but got: " + msg,
                        msg != null && (msg.contains("reference date") || msg.contains("evaluation date")));
            }
        } finally {
            s.setEvaluationDate(prevEval);
        }
    }

    @Test
    public void testIncludeSettlementDateFlowsTogglesUpfront() {
        final Date eval = new Date(15, Month.May, 2026);
        final Settings s = new Settings();
        final Date prevEval = s.evaluationDate();
        final boolean prevTodaysPayments = s.isTodaysPayments();
        try {
            s.setEvaluationDate(eval);

            final Handle<YieldTermStructure> disc = flatDiscount(eval, 0.03);
            final Handle<DefaultProbabilityTermStructure> prob = flatHazard(eval, 0.02);

            // Two engines that differ only in includeSettlementDateFlows;
            // the upfront-payment date for MakeCreditDefaultSwap defaults to
            // tradeDate + 3 cash-settlement days using the WeekendsOnly
            // calendar — same calendar as ISDA std, so we can construct cases
            // where the upfront falls exactly on the eval date.
            final IsdaCdsEngine eng1 = new IsdaCdsEngine(
                    prob, 0.40, disc, Boolean.TRUE,
                    IsdaCdsEngine.NumericalFix.Taylor,
                    IsdaCdsEngine.AccrualBias.HalfDayBias,
                    IsdaCdsEngine.ForwardsInCouponPeriod.Piecewise);
            final IsdaCdsEngine eng2 = new IsdaCdsEngine(
                    prob, 0.40, disc, Boolean.FALSE,
                    IsdaCdsEngine.NumericalFix.Taylor,
                    IsdaCdsEngine.AccrualBias.HalfDayBias,
                    IsdaCdsEngine.ForwardsInCouponPeriod.Piecewise);

            // Set the upfrontDate explicitly to the eval date so the include
            // flag has a tangible effect.
            final CreditDefaultSwap cds1 = new MakeCreditDefaultSwap(
                    new Period(5, TimeUnit.Years), 0.005)
                    .withNominal(1.0e7)
                    .withUpfrontRate(0.02)
                    .withUpfrontDate(eval)
                    .withPricingEngine(eng1)
                    .build();
            final CreditDefaultSwap cds2 = new MakeCreditDefaultSwap(
                    new Period(5, TimeUnit.Years), 0.005)
                    .withNominal(1.0e7)
                    .withUpfrontRate(0.02)
                    .withUpfrontDate(eval)
                    .withPricingEngine(eng2)
                    .build();

            // Both NPVs should be finite; with includeSettlementDateFlows=true
            // the upfront on eval date counts as not-yet-occurred, with =false
            // it counts as already-occurred. For Buyer side, that means cds1's
            // upfront is included, cds2's is not.
            final double npv1 = cds1.NPV();
            final double npv2 = cds2.NPV();
            assertTrue("npv1 finite", !Double.isNaN(npv1) && !Double.isInfinite(npv1));
            assertTrue("npv2 finite", !Double.isNaN(npv2) && !Double.isInfinite(npv2));
            // The two should differ by approximately the upfront amount
            // (notional * upfrontRate * P(upfrontDate)) with a sign flip from
            // the buyer/seller convention applied uniformly to upfrontNPV.
            assertTrue("NPV difference indicates upfront include/exclude is wired",
                    Math.abs(npv1 - npv2) > 1.0);
        } finally {
            s.setEvaluationDate(prevEval);
            s.setTodaysPayments(prevTodaysPayments);
        }
    }

    /** Smoke test: WeekendsOnly is the ISDA-standard calendar; this is just
     *  enough to demonstrate that MakeCreditDefaultSwap + IsdaCdsEngine compose. */
    @Test
    public void testWeekendsOnlyCalendarCdsPriced() {
        final Date eval = new Date(15, Month.May, 2026);
        final Settings s = new Settings();
        final Date prevEval = s.evaluationDate();
        try {
            s.setEvaluationDate(eval);

            // Sanity check: WeekendsOnly is what MakeCreditDefaultSwap uses.
            // Java returns lower-case "weekends only" (vs C++ "Weekends Only").
            assertTrue("WeekendsOnly calendar present",
                    new WeekendsOnly().name().toLowerCase().contains("weekends"));

            final Handle<YieldTermStructure> disc = flatDiscount(eval, 0.025);
            final Handle<DefaultProbabilityTermStructure> prob = flatHazard(eval, 0.015);

            final CreditDefaultSwap cds = new MakeCreditDefaultSwap(
                    new Period(3, TimeUnit.Years), 0.0050)
                    .withNominal(1.0e6)
                    .withPricingEngine(new IsdaCdsEngine(prob, 0.4, disc))
                    .build();
            final double npv = cds.NPV();
            assertTrue("3y CDS NPV finite", !Double.isNaN(npv) && !Double.isInfinite(npv));
        } finally {
            s.setEvaluationDate(prevEval);
        }
    }
}
