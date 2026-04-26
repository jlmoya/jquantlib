/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Tests for CapHelper performCalculations / fairRate path (Phase 2d WI-1).
 */
package org.jquantlib.testsuite.model.shortrate.calibrationhelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.Swap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.model.shortrate.calibrationhelpers.CapHelper;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 2d WI-1 (scoped) fingerprint test for CapHelper.
 *
 * <p>Cross-validates the swap-implied {@code fairRate} intermediate that
 * {@link CapHelper#performCalculations()} computes in lines 91-144 of
 * caphelper.cpp v1.42.1 against a C++ probe-generated reference. Also
 * asserts that the helper constructs and {@code calculate()} runs
 * without throwing (the only structural invariants reachable in this
 * commit).
 *
 * <p><strong>Out of scope</strong> — {@code modelValue()} and
 * {@code blackPrice(volatility)}: these depend on
 * {@code BlackCapFloorEngine} and {@code CapFloor.NPV()}, both of
 * which remain commented-out stubs in jquantlib. They are documented
 * Phase 2e seams and will be cross-validated once those classes are
 * ported. The probe reference also intentionally captures only the
 * fairRate path so the two sides stay aligned.
 *
 * <p><strong>Tolerance tier</strong> — tight (1e-12 rel + 1e-14 abs).
 * Closed-form arithmetic on swap NPV (DiscountingSwapEngine accumulates
 * discounted cashflows, no solver/iteration) and legBPS. Java and C++
 * both walk the same Schedule with the same calendar/BDC, build the
 * same legs, discount on a flat curve. Should be effectively bit-equal.
 */
public class CapHelperTest {

    @Test
    public void fairRateFingerprint_matchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "model/shortrate/calibrationhelpers/caphelper");
        final Case ref = reader.getCase("fair_rate_intermediate");
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        // ---- Fixture (must match caphelper_probe.cpp exactly) ----
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final BusinessDayConvention bdc = BusinessDayConvention.ModifiedFollowing;
        final Currency ccy = new EURCurrency();

        final double flatRate = in.getDouble("flat_rate");
        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(flatRate)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Period idxTenor = new Period(in.getInt("index_tenor_months"), TimeUnit.Months);
        final int fixingDays = 0;
        final boolean eom = false;
        final IborIndex idx = new IborIndex(
                "TestIbor3M", idxTenor, fixingDays, ccy, cal, bdc, eom, dc, ts);

        // ---- Replicate CapHelper.performCalculations setup ----
        final Period length = new Period(in.getInt("length_years"), TimeUnit.Years);
        final Frequency fixedLegFrequency = Frequency.Annual;
        final DayCounter fixedLegDayCounter = new Thirty360(Thirty360.Convention.European);
        final boolean includeFirstSwaplet = in.getBoolean("include_first_swaplet");
        final double fixedRate = in.getDouble("fixed_rate_dummy");

        final Date startDate = includeFirstSwaplet
                ? ts.currentLink().referenceDate()
                : ts.currentLink().referenceDate().add(idxTenor);
        final Date maturity = ts.currentLink().referenceDate().add(length);

        final Array nominals = new Array(new double[] { 1.0 });

        final Schedule floatSchedule = new Schedule(
                startDate, maturity, idxTenor, cal, bdc, bdc,
                DateGeneration.Rule.Forward, false);
        final Leg floatingLeg = new IborLeg(floatSchedule, idx)
                .withNotionals(nominals)
                .withPaymentAdjustment(bdc)
                .withFixingDays(0)
                .Leg();

        final Schedule fixedSchedule = new Schedule(
                startDate, maturity, new Period(fixedLegFrequency), cal,
                BusinessDayConvention.Unadjusted, BusinessDayConvention.Unadjusted,
                DateGeneration.Rule.Forward, false);
        final Leg fixedLeg = new FixedRateLeg(fixedSchedule, fixedLegDayCounter)
                .withNotionals(new double[] { 1.0 })
                .withCouponRates(fixedRate)
                .withPaymentAdjustment(bdc)
                .Leg();

        final Swap swap = new Swap(floatingLeg, fixedLeg);
        swap.setPricingEngine(new DiscountingSwapEngine(ts));
        final double swapNPV  = swap.NPV();
        final double legBPS1  = swap.legBPS(1);
        final double fairRate = fixedRate - swapNPV / (legBPS1 / 1.0e-4);

        // ---- Cross-validate against C++ probe ----
        final double expSwapNPV  = exp.getDouble("swap_npv");
        final double expLegBPS1  = exp.getDouble("leg_bps_1");
        final double expFairRate = exp.getDouble("fair_rate");

        if (!Tolerance.tight(swapNPV, expSwapNPV)) {
            fail("swap.NPV(): exp=" + expSwapNPV + " got=" + swapNPV);
        }
        if (!Tolerance.tight(legBPS1, expLegBPS1)) {
            fail("swap.legBPS(1): exp=" + expLegBPS1 + " got=" + legBPS1);
        }
        if (!Tolerance.tight(fairRate, expFairRate)) {
            fail("fairRate: exp=" + expFairRate + " got=" + fairRate);
        }
        assertEquals("floating leg period count must match C++",
                exp.getInt("n_floating_periods"), floatingLeg.size());
        assertEquals("fixed leg period count must match C++",
                exp.getInt("n_fixed_periods"), fixedLeg.size());

        // ---- Structural assertions on CapHelper itself ----
        // Same fixture; CapHelper is expected to construct and run
        // performCalculations() without throwing (the WI-1 unstub
        // promise). The internal fairRate is private, so we assert it
        // indirectly via the parallel swap above. marketValue() is the
        // shortest public path that triggers calculate() ->
        // performCalculations(). It returns blackPrice(vol) which is a
        // documented Phase 2e seam returning 0.0 — we deliberately do
        // NOT assert on its value, only that the trigger does not throw.
        final Handle<Quote> vol = new Handle<Quote>(new SimpleQuote(0.20));
        final CapHelper helper = new CapHelper(
                length, vol, idx, fixedLegFrequency, fixedLegDayCounter,
                includeFirstSwaplet, ts);
        assertNotNull("CapHelper must construct", helper);
        helper.marketValue();
    }
}
