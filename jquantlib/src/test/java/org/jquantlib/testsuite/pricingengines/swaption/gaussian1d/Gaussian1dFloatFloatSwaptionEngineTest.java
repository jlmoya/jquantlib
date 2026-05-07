/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.swaption.gaussian1d;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.instruments.FloatFloatSwap;
import org.jquantlib.instruments.FloatFloatSwaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dFloatFloatSwaptionEngine;
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
 * Phase 2j.5 Track B.3 fingerprint test for
 * {@link Gaussian1dFloatFloatSwaptionEngine}.
 *
 * <p>Cross-validates {@code FloatFloatSwaption.NPV()} across a 72-cell grid of
 * European float-float swaptions priced under a vol-step Gsr Gaussian1d model
 * against
 * {@code migration-harness/cpp/probes/pricingengines/swaption/
 * gaussian1d_float_float_swaption_engine_probe.cpp} (oracle: C++ QuantLib
 * v1.42.1).
 *
 * <p>The grid covers two engine variants (default 64-pt/7sd, high-density
 * 128-pt/7sd), six exercise/tenor combinations (1Y/3Y, 1Y/5Y, 2Y/3Y, 2Y/5Y,
 * 5Y/3Y, 5Y/5Y), two swap types (payer/receiver), two notional shapes (flat
 * and amortising), and four cap/floor variants on leg 2.
 *
 * <p>Tier: {@link Tolerance#loose} (abs 1e-8 + rel 1e-8) — A19 justification:
 * deeply-nested Gaussian-quadrature convolution (two parallel splines for
 * option NPV and underlying NPV) plus per-coupon capped/floored payoffs at
 * each event date. Same precedent as
 * {@link org.jquantlib.testsuite.pricingengines.swaption.gaussian1d.Gaussian1dNonstandardSwaptionEngineTest}.
 *
 * <p>Single {@code @Test} with collect-all-failures pattern.
 */
public class Gaussian1dFloatFloatSwaptionEngineTest {

    private static final Date EVAL = new Date(15, Month.January, 2026);
    private static final double FLAT_RATE = 0.03;
    private static final double REVERSION = 0.01;
    private static final double NOMINAL = 100.0;

    @Test
    public void gaussian1dFloatFloatSwaptionEngine_npvMatchesCpp() {
        new Settings().setEvaluationDate(EVAL);

        // ── Fixture ──────────────────────────────────────────────────────────
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();

        final YieldTermStructure flat = new FlatForward(
                EVAL, new Handle<Quote>(new SimpleQuote(FLAT_RATE)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx1 = new Euribor3M(ts);
        final Euribor6M idx2 = new Euribor6M(ts);

        final List<Date> volStepDates = new ArrayList<Date>();
        volStepDates.add(EVAL.add(new Period(1, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(2, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(5, TimeUnit.Years)));
        final double[] vols = {0.01, 0.012, 0.014, 0.016};
        final Gsr gsr = new Gsr(ts, volStepDates, vols, REVERSION);

        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/gaussian1d_float_float_swaption_engine");

        final List<String> failures = new ArrayList<String>();

        for (final String name : reader.caseNames()) {
            final Case c = reader.getCase(name);
            final JSONObject in  = c.inputs();
            final JSONObject exp = (JSONObject) c.expectedRaw();
            try {
                final double[] result = priceCase(cal, idx1, idx2, dc, gsr, in);
                final double javaNpv  = result[0];
                final double javaUnd  = result[1];
                final double cppNpv   = exp.getDouble("npv");
                final double cppUnd   = exp.getDouble("underlyingValue");

                // A19: Gaussian quadrature convolution dominates error budget.
                if (!Tolerance.loose(javaNpv, cppNpv)) {
                    failures.add(name + ": LOOSE NPV mismatch java=" + javaNpv
                            + " cpp=" + cppNpv
                            + " (diff=" + Math.abs(javaNpv - cppNpv) + ")");
                }
                if (!Tolerance.loose(javaUnd, cppUnd)) {
                    failures.add(name + ": LOOSE underlying mismatch java=" + javaUnd
                            + " cpp=" + cppUnd
                            + " (diff=" + Math.abs(javaUnd - cppUnd) + ")");
                }
            } catch (final RuntimeException e) {
                failures.add(name + ": exception "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Gaussian1dFloatFloatSwaptionEngineTest: " + failures.size()
                    + " mismatch(es)\n  "
                    + String.join("\n  ", failures.subList(0,
                            Math.min(40, failures.size())))
                    + (failures.size() > 40
                            ? "\n  ... (" + (failures.size() - 40) + " more)" : ""));
        }
    }

    // ── Pricing helper ────────────────────────────────────────────────────────

    private static double[] priceCase(
            final Calendar cal,
            final Euribor3M idx1,
            final Euribor6M idx2,
            final DayCounter dc,
            final Gsr gsr,
            final JSONObject in) {

        final int     integrationPoints      = in.getInt("integration_points");
        final double  stddevs                = in.getDouble("stddevs");
        final boolean extrapolatePayoff      = in.getBoolean("extrapolate_payoff");
        final boolean flatPayoffExtrapolation = in.getBoolean("flat_payoff_extrapolation");
        final int     exerciseYears          = in.getInt("exercise_years");
        final int     swapYears              = in.getInt("swap_years");
        final String  swapTypeStr            = in.getString("swap_type");
        final String  shape                  = in.getString("shape");
        final String  capFloor               = in.getString("cap_floor");
        final double  spread2                = in.getDouble("spread2");
        final double  gearing2               = in.getDouble("gearing2");

        final VanillaSwap.Type swapType = "Payer".equals(swapTypeStr)
                ? VanillaSwap.Type.Payer : VanillaSwap.Type.Receiver;

        final Date exerciseDate = cal.advance(EVAL,
                new Period(exerciseYears, TimeUnit.Years),
                BusinessDayConvention.Following);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Date startDate = cal.advance(exerciseDate, 2, TimeUnit.Days,
                BusinessDayConvention.Following, false);
        final Date maturity = cal.advance(startDate,
                new Period(swapYears, TimeUnit.Years),
                BusinessDayConvention.Following);

        final Schedule sch1 = new Schedule(
                startDate, maturity, new Period(3, TimeUnit.Months), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
        final Schedule sch2 = new Schedule(
                startDate, maturity, new Period(6, TimeUnit.Months), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        final int n1 = sch1.size() - 1;
        final int n2 = sch2.size() - 1;

        final double[] nom1 = buildNominals(shape, n1, swapYears, /*perYear=*/4, NOMINAL);
        final double[] nom2 = buildNominals(shape, n2, swapYears, /*perYear=*/2, NOMINAL);

        final double[] gear1 = fill(n1, 1.0);
        final double[] spr1  = fill(n1, 0.0);
        final double[] cap1  = fill(n1, FloatFloatSwap.NULL_REAL);
        final double[] flr1  = fill(n1, FloatFloatSwap.NULL_REAL);

        final double[] gear2 = fill(n2, gearing2);
        final double[] spr2  = fill(n2, spread2);
        final double[] cap2  = fill(n2, FloatFloatSwap.NULL_REAL);
        final double[] flr2  = fill(n2, FloatFloatSwap.NULL_REAL);

        if ("cap".equals(capFloor) || "both".equals(capFloor)) {
            for (int i = 0; i < n2; i++) cap2[i] = 0.04;
        }
        if ("floor".equals(capFloor) || "both".equals(capFloor)) {
            for (int i = 0; i < n2; i++) flr2[i] = 0.02;
        }

        final FloatFloatSwap swap = new FloatFloatSwap(
                swapType,
                nom1, nom2,
                sch1, idx1, dc,
                sch2, idx2, dc,
                false, false,
                gear1, spr1, cap1, flr1,
                gear2, spr2, cap2, flr2);

        final FloatFloatSwaption swaption = new FloatFloatSwaption(swap, exercise);
        swaption.setPricingEngine(new Gaussian1dFloatFloatSwaptionEngine(
                gsr,
                integrationPoints, stddevs,
                extrapolatePayoff, flatPayoffExtrapolation,
                new Handle<Quote>(),
                new Handle<YieldTermStructure>(),
                false,
                Gaussian1dFloatFloatSwaptionEngine.Probabilities.None));

        final double npv = swaption.NPV();
        final Object und = swaption.result("underlyingValue");
        final double underlying = (und instanceof Double)
                ? ((Double) und).doubleValue() : 0.0;
        return new double[] { npv, underlying };
    }

    /**
     * Build per-coupon nominal array. For "flat" returns a constant fill.
     * For "amort" replicates an annual amortising profile (1.0, 0.9, 0.8, …)
     * across {@code perYear} sub-periods per year. Mirrors the C++ probe.
     */
    private static double[] buildNominals(
            final String shape, final int n, final int swapYears,
            final int perYear, final double nom) {
        final double[] a = new double[n];
        if ("flat".equals(shape)) {
            java.util.Arrays.fill(a, nom);
            return a;
        }
        // amort
        final double[] perYr = new double[swapYears];
        for (int y = 0; y < swapYears; y++) perYr[y] = nom * (1.0 - y * 0.1);
        for (int i = 0; i < n; i++) {
            final int yr = Math.min(i / perYear, swapYears - 1);
            a[i] = perYr[yr];
        }
        return a;
    }

    private static double[] fill(final int n, final double v) {
        final double[] a = new double[n];
        java.util.Arrays.fill(a, v);
        return a;
    }
}
