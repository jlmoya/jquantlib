/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Java port test for Phase 3c Track B IntegralCdsEngine — cross-validates a
 lattice of CDS NPVs / fair-spreads against the C++ probe.
*/

package org.jquantlib.testsuite.pricingengines.credit;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.Protection;
import org.jquantlib.pricingengines.credit.IntegralCdsEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
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
import org.jquantlib.time.calendars.NullCalendar;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 3c Track B fingerprint test for {@link IntegralCdsEngine}.
 *
 * <p>Cross-validates CDS NPV / leg breakdown / fair spread / fair upfront
 * for an 8-case lattice (Buyer/Seller, running-only vs upfront+running,
 * varied recovery / hazard / integration step) against the C++
 * {@code integral_cds_engine_probe} reference output.
 *
 * <p><strong>Tolerance tier</strong> — tight (1e-12 rel + 1e-14 abs).
 * IntegralCdsEngine performs a deterministic per-period numerical
 * integration; arithmetic order is identical between Java and C++ so
 * agreement is bit-exact modulo trailing-ULP floating-point noise.
 */
public class IntegralCdsEngineTest {

    public IntegralCdsEngineTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static void runCase(final Case ref) {
        final JSONObject in = ref.inputs();
        final JSONObject exp = (JSONObject) ref.expectedRaw();

        final Date eval = new Date(15, Month.May, 2026);
        new Settings().setEvaluationDate(eval);
        final DayCounter dc = new Actual360();
        final Calendar cal = new NullCalendar();
        final BusinessDayConvention bdc = BusinessDayConvention.Following;

        final double flatRate = in.getDouble("flat_rate");
        final YieldTermStructure flat = new FlatForward(
                eval, new Handle<Quote>(new SimpleQuote(flatRate)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts =
                new Handle<YieldTermStructure>(flat);

        final double hazardRate = in.getDouble("hazard_rate");
        final DefaultProbabilityTermStructure dpts = new FlatHazardRate(
                eval,
                new Handle<Quote>(new SimpleQuote(hazardRate)),
                dc);
        final Handle<DefaultProbabilityTermStructure> probability =
                new Handle<DefaultProbabilityTermStructure>(dpts);

        final int years = in.getInt("years");
        final Date start = eval;
        final Date end = eval.add(new Period(years, TimeUnit.Years));
        final Schedule schedule = new Schedule(
                start, end,
                new Period(Frequency.Quarterly),
                cal, bdc, bdc,
                DateGeneration.Rule.Forward, false);

        final Protection.Side side = "Buyer".equals(in.getString("side"))
                ? Protection.Side.Buyer : Protection.Side.Seller;
        final double notional = in.getDouble("notional");
        final double spread = in.getDouble("spread");
        final double recoveryRate = in.getDouble("recovery_rate");
        final boolean hasUpfront = in.getBoolean("has_upfront");
        final double upfront = in.getDouble("upfront");
        final int stepDays = in.getInt("step_days");

        final CreditDefaultSwap cds;
        if (hasUpfront) {
            cds = new CreditDefaultSwap(
                    side, notional, upfront, spread, schedule, bdc, dc,
                    true, true, start, null,
                    null, null, true, eval, 3);
        } else {
            cds = new CreditDefaultSwap(
                    side, notional, spread, schedule, bdc, dc,
                    true, true, start);
        }
        cds.setPricingEngine(new IntegralCdsEngine(
                new Period(stepDays, TimeUnit.Days),
                probability, recoveryRate, ts));

        // -------- assertions --------
        check(exp, "npv", cds.NPV());
        check(exp, "default_leg_npv", cds.defaultLegNPV());
        check(exp, "coupon_leg_npv", cds.couponLegNPV());
        check(exp, "upfront_npv", cds.upfrontNPV());
        check(exp, "accrual_rebate_npv", cds.accrualRebateNPV());

        if (exp.getBoolean("fair_spread_valid")) {
            check(exp, "fair_spread", cds.fairSpread());
        }

        if (exp.getBoolean("fair_upfront_valid")) {
            check(exp, "fair_upfront", cds.fairUpfront());
        }
    }

    private static void check(final JSONObject exp, final String key,
                              final double javaValue) {
        final double expected = exp.getDouble(key);
        if (!Tolerance.tight(javaValue, expected)) {
            fail(key + ": expected=" + expected + " got=" + javaValue
                    + " absDiff=" + Math.abs(javaValue - expected));
        }
    }

    private static Case load(final String name) {
        return ReferenceReader.load(
                "pricingengines/credit/integral_cds_engine").getCase(name);
    }

    @Test
    public void buyer_5y_running_only_at_par_step1d() {
        runCase(load("buyer_5y_running_only_at_par_step1d"));
    }

    @Test
    public void buyer_5y_running_only_at_par_step7d() {
        runCase(load("buyer_5y_running_only_at_par_step7d"));
    }

    @Test
    public void seller_5y_running_only_at_par_step1d() {
        runCase(load("seller_5y_running_only_at_par_step1d"));
    }

    @Test
    public void buyer_3y_running_only_offmarket_step7d() {
        runCase(load("buyer_3y_running_only_offmarket_step7d"));
    }

    @Test
    public void buyer_5y_upfront_plus_running_step1d() {
        runCase(load("buyer_5y_upfront_plus_running_step1d"));
    }

    @Test
    public void seller_2y_upfront_plus_running_step7d() {
        runCase(load("seller_2y_upfront_plus_running_step7d"));
    }

    @Test
    public void buyer_5y_high_recovery_step1d() {
        runCase(load("buyer_5y_high_recovery_step1d"));
    }

    @Test
    public void buyer_5y_low_hazard_step7d() {
        runCase(load("buyer_5y_low_hazard_step7d"));
    }
}
