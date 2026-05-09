/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.AveragingMultipleResetsPricer;
import org.jquantlib.cashflow.CompoundingMultipleResetsPricer;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.MultipleResetsCoupon;
import org.jquantlib.cashflow.MultipleResetsLeg;
import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.instruments.MultipleResetsSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.quotes.Handle;
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
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.Test;

/**
 * Phase 5d.5-MR cross-validation smoke tests for the multiple-resets coupon
 * family.
 *
 * <p>Drives every public knob of {@link MultipleResetsCoupon},
 * {@link CompoundingMultipleResetsPricer}, {@link AveragingMultipleResetsPricer},
 * {@link MultipleResetsLeg}, and {@link MultipleResetsSwap} against
 * reference values produced by
 * {@code migration-harness/cpp/probes/cashflows/multiple_resets_coupon_probe.cpp}
 * (QuantLib v1.42.1).
 *
 * <p>The five test-suite cases ported as skeletons in
 * {@link MultipleResetsCouponsTest} (Phase 5d) remain {@code @Ignore}'d —
 * full un-ignore is Phase 5d.5-MRb (needs ex-coupon date plumbing through
 * {@code FloatingRateCoupon} ctor).
 */
public class MultipleResetsCouponSmokeTest {

    private static final String REF_GROUP = "cashflows/multiple_resets_coupon";

    /**
     * Builds the same setup as the C++ probe and exercises the single-coupon,
     * leg, and swap cases via the JSON reference.
     */
    @Test
    public void multipleResetsCoupon_matchesCpp() {
        final ReferenceReader ref = ReferenceReader.load(REF_GROUP);

        // ---------- Probe-matching setup ----------
        final Date evalDate = new Date(1, Month.April, 2026);
        new Settings().setEvaluationDate(evalDate);

        final Calendar calendar = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final DayCounter dc = new Actual360();

        final FlatForward flatTs = new FlatForward(
                evalDate, 0.03, dc,
                Compounding.Compounded, Frequency.Annual);
        final Handle<YieldTermStructure> ytsHandle =
                new Handle<YieldTermStructure>(flatTs);

        final IborIndex idx = new USDLibor(new Period(1, TimeUnit.Months), ytsHandle);
        // Match probe: provide the historical fixing for March 30, 2026.
        idx.addFixing(new Date(30, Month.March, 2026), 0.025);

        final Schedule resetSchedule = new Schedule(
                new Date(1, Month.April,   2026),
                new Date(1, Month.October, 2026),
                new Period(1, TimeUnit.Months), calendar,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        // ---------- Case 1: single MultipleResetsCoupon ----------
        final Case c1 = ref.getCase("single_coupon_compound_vs_average");
        final double nominal = c1.inputs().getDouble("nominal");
        final Date paymentDate = calendar.adjust(
                resetSchedule.dates().get(resetSchedule.size() - 1),
                BusinessDayConvention.ModifiedFollowing);

        final MultipleResetsCoupon coupon = new MultipleResetsCoupon(
                paymentDate, nominal, resetSchedule, /* fixingDays */ 2, idx,
                /* gearing */ 1.0, /* couponSpread */ 0.0, /* rateSpread */ 0.0,
                new Date(), new Date(), new DayCounter());

        coupon.setPricer(new CompoundingMultipleResetsPricer());
        final double compoundRate = coupon.rate();
        final double compoundAmount = coupon.amount();

        coupon.setPricer(new AveragingMultipleResetsPricer());
        final double averageRate = coupon.rate();
        final double averageAmount = coupon.amount();

        final org.json.JSONObject e1 = (org.json.JSONObject) c1.expectedRaw();

        check("compoundRate",  compoundRate,  e1.getDouble("compoundRate"));
        check("compoundAmount", compoundAmount, e1.getDouble("compoundAmount"));
        check("averageRate",  averageRate,  e1.getDouble("averageRate"));
        check("averageAmount", averageAmount, e1.getDouble("averageAmount"));
        check("accrualPeriod", coupon.accrualPeriod(), e1.getDouble("accrualPeriod"));

        // value dates / fixing dates / dt arrays — bit-exact serial / TIGHT
        final org.json.JSONArray vd = e1.getJSONArray("valueDates");
        for (int i = 0; i < vd.length(); i++) {
            if (coupon.valueDates().get(i).serialNumber() != vd.getInt(i)) {
                fail("valueDate[" + i + "] mismatch: java="
                        + coupon.valueDates().get(i).serialNumber()
                        + " cpp=" + vd.getInt(i));
            }
        }
        final org.json.JSONArray fd = e1.getJSONArray("fixingDates");
        for (int i = 0; i < fd.length(); i++) {
            if (coupon.fixingDates().get(i).serialNumber() != fd.getInt(i)) {
                fail("fixingDate[" + i + "] mismatch: java="
                        + coupon.fixingDates().get(i).serialNumber()
                        + " cpp=" + fd.getInt(i));
            }
        }
        final org.json.JSONArray dts = e1.getJSONArray("dt");
        for (int i = 0; i < dts.length(); i++) {
            check("dt[" + i + "]", coupon.dt().get(i), dts.getDouble(i));
        }
        if (coupon.fixingDate().serialNumber() != e1.getInt("fixingDate")) {
            fail("coupon.fixingDate() mismatch");
        }

        // ---------- Case 2: 2-coupon MultipleResetsLeg, resetsPerCoupon=3 ----------
        final Case c2 = ref.getCase("leg_two_coupons_resetsPerCoupon3");
        final org.json.JSONObject e2 = (org.json.JSONObject) c2.expectedRaw();

        final Leg legCompound = new MultipleResetsLeg(resetSchedule, idx, 3)
                .withNotionals(nominal)
                .withAveragingMethod(RateAveraging.Type.Compound)
                .Leg();
        final Leg legAverage = new MultipleResetsLeg(resetSchedule, idx, 3)
                .withNotionals(nominal)
                .withAveragingMethod(RateAveraging.Type.Simple)
                .Leg();

        if (legCompound.size() != e2.getInt("legSize")) {
            fail("legSize mismatch java=" + legCompound.size()
                 + " cpp=" + e2.getInt("legSize"));
        }
        final MultipleResetsCoupon cmp0 = (MultipleResetsCoupon) legCompound.get(0);
        final MultipleResetsCoupon cmp1 = (MultipleResetsCoupon) legCompound.get(1);
        final MultipleResetsCoupon avg0 = (MultipleResetsCoupon) legAverage.get(0);
        final MultipleResetsCoupon avg1 = (MultipleResetsCoupon) legAverage.get(1);

        check("compound_cpn0_rate",   cmp0.rate(),   e2.getDouble("compound_cpn0_rate"));
        check("compound_cpn0_amount", cmp0.amount(), e2.getDouble("compound_cpn0_amount"));
        check("compound_cpn1_rate",   cmp1.rate(),   e2.getDouble("compound_cpn1_rate"));
        check("compound_cpn1_amount", cmp1.amount(), e2.getDouble("compound_cpn1_amount"));
        check("average_cpn0_rate",    avg0.rate(),   e2.getDouble("average_cpn0_rate"));
        check("average_cpn0_amount",  avg0.amount(), e2.getDouble("average_cpn0_amount"));
        check("average_cpn1_rate",    avg1.rate(),   e2.getDouble("average_cpn1_rate"));
        check("average_cpn1_amount",  avg1.amount(), e2.getDouble("average_cpn1_amount"));

        if (cmp0.fixingDates().size() != e2.getInt("compound_cpn0_fixings_count")) {
            fail("cpn0 fixings count mismatch java=" + cmp0.fixingDates().size()
                 + " cpp=" + e2.getInt("compound_cpn0_fixings_count"));
        }

        // ---------- Case 3: MultipleResetsSwap NPV ----------
        final Case c3 = ref.getCase("swap_npv_compound");
        final org.json.JSONObject e3 = (org.json.JSONObject) c3.expectedRaw();

        final Schedule fixedSchedule = new Schedule(
                new Date(1, Month.April,   2026),
                new Date(1, Month.October, 2026),
                new Period(6, TimeUnit.Months), calendar,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        final MultipleResetsSwap swap = new MultipleResetsSwap(
                VanillaSwap.Type.Payer, nominal,
                fixedSchedule, /* fixedRate */ 0.025, dc,
                resetSchedule, idx, /* resetsPerCoupon */ 3,
                /* spread */ 0.0,
                RateAveraging.Type.Compound,
                /* paymentConvention default */ null,
                /* paymentLag */ 0,
                new org.jquantlib.time.calendars.NullCalendar());

        swap.setPricingEngine(new DiscountingSwapEngine(ytsHandle));

        check("swap.NPV", swap.NPV(), e3.getDouble("NPV"));
        check("swap.fixedLegNPV",    swap.fixedLegNPV(),    e3.getDouble("fixedLegNPV"));
        check("swap.floatingLegNPV", swap.floatingLegNPV(), e3.getDouble("floatingLegNPV"));
        check("swap.fairRate",       swap.fairRate(),       e3.getDouble("fairRate"));
    }

    /**
     * TIGHT-tier comparison; widened slightly for the absolute term to handle
     * mid-large notional amounts (1e6 nominal × 0.03 rate → ~15 000 amount
     * carries an absolute scale of ~1e-11 for tight relative).
     */
    private static void check(final String label, final double java, final double cpp) {
        // |a-b| < 1e-9 + 1e-9*|cpp| handles both rate-scale (~0.03) and
        // amount-scale (~1.5e4) values uniformly.
        if (!Tolerance.within(java, cpp, 1.0e-9, "TIGHT for cashflow rates and amounts")) {
            fail(label + ": java=" + java + " cpp=" + cpp
                 + " diff=" + (java - cpp));
        }
    }
}
