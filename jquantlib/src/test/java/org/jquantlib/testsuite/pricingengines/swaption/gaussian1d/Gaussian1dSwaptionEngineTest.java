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
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dSwaptionEngine;
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
 * Phase 2j WI-2.1 fingerprint test for {@link Gaussian1dSwaptionEngine}.
 *
 * <p>Cross-validates {@code Swaption.NPV()} for a 144-cell grid of European
 * swaptions priced under a vol-step Gsr Gaussian1d model against
 * {@code migration-harness/cpp/probes/pricingengines/swaption/gaussian1d_swaption_engine_probe.cpp}
 * (oracle: C++ QuantLib v1.42.1).
 *
 * <p>The grid covers four engine variants (default, high-density, narrow
 * std-dev / no-extrapolation, flat-extrapolation), three exercise dates (1Y,
 * 3Y, 5Y), two swap tenors (2Y, 5Y), three strikes (2%/3%/4%) and two swap
 * directions (payer / receiver). Tier: {@link Tolerance#loose} (abs 1e-8 +
 * rel 1e-8) — A19 justification: this engine performs numerical Gaussian
 * quadrature across a 2N+1 state grid (N=64 default), with accumulated
 * {@code gaussianShiftedPolynomialIntegral} calls that invoke {@code erfc}.
 * These integration errors dominate at ~1e-9, well within LOOSE but above
 * TIGHT. Single {@code @Test} with collect-all-failures pattern.
 */
public class Gaussian1dSwaptionEngineTest {

    private static final Date EVAL = new Date(15, Month.January, 2026);
    private static final double FLAT_RATE = 0.03;
    private static final double REVERSION = 0.01;
    private static final double NOMINAL = 100.0;

    @Test
    public void gaussian1dSwaptionEngine_npvMatchesCpp() {
        new Settings().setEvaluationDate(EVAL);

        // ── Fixture (mirrors gaussian1d_swaption_engine_probe.cpp) ──
        final DayCounter dc = new Actual365Fixed();
        final Calendar cal = new Target();
        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);

        final YieldTermStructure flat = new FlatForward(
                EVAL, new Handle<Quote>(new SimpleQuote(FLAT_RATE)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);

        final List<Date> volStepDates = new ArrayList<>();
        volStepDates.add(EVAL.add(new Period(1, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(2, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(5, TimeUnit.Years)));
        final double[] vols = new double[]{0.01, 0.012, 0.014, 0.016};
        final Gsr gsr = new Gsr(ts, volStepDates, vols, REVERSION);

        // ── Walk reference cases ──
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/gaussian1d_swaption_engine");

        final List<String> failures = new ArrayList<>();

        for (final String name : reader.caseNames()) {
            final Case c = reader.getCase(name);
            final JSONObject in = c.inputs();
            final JSONObject exp = (JSONObject) c.expectedRaw();
            try {
                if (name.startsWith("e0_") || name.startsWith("e1_")
                        || name.startsWith("e2_") || name.startsWith("e3_")) {
                    final double npv = priceCase(cal, idx, dc, fixedDc, gsr, in);
                    final double cpp = exp.getDouble("npv");
                    // A19: numerical Gaussian quadrature integration engine;
                    // accumulated erfc errors reach ~1e-9 — LOOSE tier required.
                    if (!Tolerance.loose(npv, cpp)) {
                        failures.add(name + ": LOOSE mismatch java=" + npv
                                + " cpp=" + cpp + " (diff="
                                + Math.abs(npv - cpp) + ")");
                    }
                } else {
                    failures.add(name + ": no dispatcher branch");
                }
            } catch (final RuntimeException e) {
                failures.add(name + ": exception "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Gaussian1dSwaptionEngineTest: " + failures.size()
                    + " mismatch(es)\n  "
                    + String.join("\n  ", failures.subList(0,
                            Math.min(30, failures.size())))
                    + (failures.size() > 30
                            ? "\n  ... (" + (failures.size() - 30) + " more)" : ""));
        }
    }

    private static double priceCase(
            final Calendar cal,
            final Euribor3M idx,
            final DayCounter floatDc,
            final DayCounter fixedDc,
            final Gsr gsr,
            final JSONObject in) {

        final int integrationPoints = in.getInt("integration_points");
        final double stddevs = in.getDouble("stddevs");
        final boolean extrapolatePayoff = in.getBoolean("extrapolate_payoff");
        final boolean flatPayoffExtrapolation =
                in.getBoolean("flat_payoff_extrapolation");
        final int exerciseYears = in.getInt("exercise_years");
        final int swapYears = in.getInt("swap_years");
        final double strike = in.getDouble("strike");
        final String typeStr = in.getString("type");
        final VanillaSwap.Type type = "Payer".equals(typeStr)
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

        final Schedule fixedSchedule = new Schedule(
                startDate, maturity, new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
        final Schedule floatSchedule = new Schedule(
                startDate, maturity, new Period(3, TimeUnit.Months), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        final VanillaSwap swap = new VanillaSwap(
                type, NOMINAL, fixedSchedule, strike, fixedDc,
                floatSchedule, idx, 0.0, floatDc);

        final Swaption swaption = new Swaption(swap, exercise);
        swaption.setPricingEngine(new Gaussian1dSwaptionEngine(
                gsr, integrationPoints, stddevs,
                extrapolatePayoff, flatPayoffExtrapolation,
                new Handle<YieldTermStructure>(),
                Gaussian1dSwaptionEngine.Probabilities.None));
        return swaption.NPV();
    }
}
