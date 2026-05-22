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
import org.jquantlib.instruments.NonstandardSwap;
import org.jquantlib.instruments.NonstandardSwaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr;
import org.jquantlib.pricingengines.swaption.gaussian1d.Gaussian1dNonstandardSwaptionEngine;
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
 * Phase 2j.5 Track A.3 fingerprint test for {@link Gaussian1dNonstandardSwaptionEngine}.
 *
 * <p>Cross-validates {@code NonstandardSwaption.NPV()} across a 92-cell grid of
 * European non-standard swaptions priced under a vol-step Gsr Gaussian1d model
 * against {@code migration-harness/cpp/probes/pricingengines/swaption/
 * gaussian1d_nonstandard_swaption_engine_probe.cpp} (oracle: C++ QuantLib v1.42.1).
 *
 * <p>The grid covers two engine variants (default 64-pt/7sd, high-density 128-pt/7sd),
 * five exercise-tenor combinations (1Y/3Y, 1Y/5Y, 2Y/3Y, 2Y/5Y, 5Y/3Y, 5Y/5Y),
 * two swap types (payer/receiver), and three notional profiles (flat, amortizing,
 * accreting). Fixed rates span ITM/ATM/OTM bands.
 *
 * <p>Tier: {@link Tolerance#loose} (abs 1e-8 + rel 1e-8) — A19 justification:
 * same Gaussian quadrature integration engine as {@link Gaussian1dSwaptionEngine};
 * accumulated {@code gaussianShiftedPolynomialIntegral} errors dominate at ~1e-9.
 * Single {@code @Test} with collect-all-failures pattern.
 */
public class Gaussian1dNonstandardSwaptionEngineTest {

    private static final Date EVAL = new Date(15, Month.January, 2026);
    private static final double FLAT_RATE  = 0.03;
    private static final double REVERSION  = 0.01;
    private static final double NOMINAL    = 100.0;

    @Test
    public void gaussian1dNonstandardSwaptionEngine_npvMatchesCpp() {
        new Settings().setEvaluationDate(EVAL);

        // ── Fixture (mirrors gaussian1d_nonstandard_swaption_engine_probe.cpp) ──
        final DayCounter dc      = new Actual365Fixed();
        final DayCounter fixedDc = new Thirty360(Thirty360.Convention.European);
        final Calendar   cal     = new Target();

        final YieldTermStructure flat = new FlatForward(
                EVAL, new Handle<Quote>(new SimpleQuote(FLAT_RATE)), dc,
                Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(flat);

        final Euribor3M idx = new Euribor3M(ts);

        final List<Date> volStepDates = new ArrayList<>();
        volStepDates.add(EVAL.add(new Period(1, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(2, TimeUnit.Years)));
        volStepDates.add(EVAL.add(new Period(5, TimeUnit.Years)));
        final double[] vols = {0.01, 0.012, 0.014, 0.016};
        final Gsr gsr = new Gsr(ts, volStepDates, vols, REVERSION);

        // ── Walk reference cases ──
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/swaption/gaussian1d_nonstandard_swaption_engine");

        final List<String> failures = new ArrayList<>();

        for (final String name : reader.caseNames()) {
            final Case c = reader.getCase(name);
            final JSONObject in  = c.inputs();
            final JSONObject exp = (JSONObject) c.expectedRaw();
            try {
                final double npv = priceCase(cal, idx, dc, fixedDc, gsr, in);
                final double cpp = exp.getDouble("npv");
                // A19: Gaussian quadrature integration engine — LOOSE tier required
                if (!Tolerance.loose(npv, cpp)) {
                    failures.add(name + ": LOOSE mismatch java=" + npv
                            + " cpp=" + cpp
                            + " (diff=" + Math.abs(npv - cpp) + ")");
                }
            } catch (final RuntimeException e) {
                failures.add(name + ": exception "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Gaussian1dNonstandardSwaptionEngineTest: " + failures.size()
                    + " mismatch(es)\n  "
                    + String.join("\n  ", failures.subList(0,
                            Math.min(30, failures.size())))
                    + (failures.size() > 30
                            ? "\n  ... (" + (failures.size() - 30) + " more)" : ""));
        }
    }

    // ── Pricing helper ────────────────────────────────────────────────────────

    private static double priceCase(
            final Calendar cal,
            final Euribor3M idx,
            final DayCounter floatDc,
            final DayCounter fixedDc,
            final Gsr gsr,
            final JSONObject in) {

        final int     integrationPoints      = in.getInt("integration_points");
        final double  stddevs                = in.getDouble("stddevs");
        final boolean extrapolatePayoff      = in.getBoolean("extrapolate_payoff");
        final boolean flatPayoffExtrapolation = in.getBoolean("flat_payoff_extrapolation");
        final int     exerciseYears          = in.getInt("exercise_years");
        final int     swapYears              = in.getInt("swap_years");
        final String  swapTypeStr            = in.getString("swap_type");
        final String  nominalProfile         = in.getString("nominal_profile");
        final double  fixedRate              = in.getDouble("fixed_rate");
        final int     nFixed                 = in.getInt("n_fixed");
        final int     nFloat                 = in.getInt("n_float");

        final VanillaSwap.Type swapType = "Payer".equals(swapTypeStr)
                ? VanillaSwap.Type.Payer : VanillaSwap.Type.Receiver;

        final Date exerciseDate = cal.advance(EVAL,
                new Period(exerciseYears, TimeUnit.Years),
                BusinessDayConvention.Following);
        final Exercise exercise = new EuropeanExercise(exerciseDate);
        final Date startDate = cal.advance(exerciseDate, 2, TimeUnit.Days,
                BusinessDayConvention.Following, false);
        final Date maturity  = cal.advance(startDate,
                new Period(swapYears, TimeUnit.Years),
                BusinessDayConvention.Following);

        final Schedule fixedSch = new Schedule(
                startDate, maturity, new Period(1, TimeUnit.Years), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);
        final Schedule floatSch = new Schedule(
                startDate, maturity, new Period(3, TimeUnit.Months), cal,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Forward, false);

        // Build nominal arrays matching the probe logic
        final double[] fixedNoms  = buildNominals(nominalProfile, nFixed,  NOMINAL);
        final double[] floatNoms  = buildNominals(nominalProfile, nFixed, nFloat, NOMINAL);
        final double[] fixedRates = fill(nFixed, fixedRate);

        final NonstandardSwap swap = new NonstandardSwap(
                swapType,
                fixedNoms, floatNoms,
                fixedSch, fixedRates, fixedDc,
                floatSch, idx,
                1.0, 0.0, floatDc, false, false);

        final NonstandardSwaption swaption = new NonstandardSwaption(swap, exercise);
        swaption.setPricingEngine(new Gaussian1dNonstandardSwaptionEngine(
                gsr,
                integrationPoints, stddevs,
                extrapolatePayoff, flatPayoffExtrapolation,
                new Handle<Quote>(),
                new Handle<YieldTermStructure>(),
                Gaussian1dNonstandardSwaptionEngine.Probabilities.None));

        return swaption.NPV();
    }

    // ── Notional-profile builders (mirror the C++ probe logic) ───────────────

    /** Build fixed-leg nominals. */
    private static double[] buildNominals(
            final String profile, final int nFixed, final double nom) {
        final double[] a = new double[nFixed];
        for (int i = 0; i < nFixed; i++) {
            if ("flat".equals(profile)) {
                a[i] = nom;
            } else if ("amort".equals(profile)) {
                a[i] = nom * (1.0 - i * 0.1);
            } else { // accret
                a[i] = nom * (1.0 + i * 0.05);
            }
        }
        return a;
    }

    /**
     * Build floating-leg nominals by replicating each fixed-period nominal
     * across 4 quarterly sub-periods (mirrors the C++ probe for non-flat
     * profiles), then truncating / padding to nFloat.
     */
    private static double[] buildNominals(
            final String profile, final int nFixed, final int nFloat, final double nom) {
        if ("flat".equals(profile)) {
            return fill(nFloat, nom);
        }
        final double[] fixedNoms = buildNominals(profile, nFixed, nom);
        final double[] a = new double[nFloat];
        int pos = 0;
        for (int i = 0; i < nFixed && pos < nFloat; i++) {
            for (int q = 0; q < 4 && pos < nFloat; q++) {
                a[pos++] = fixedNoms[i];
            }
        }
        // If nFloat > 4*nFixed (can't happen with 3-month quarters) fill remainder
        while (pos < nFloat) {
            a[pos++] = fixedNoms[nFixed - 1];
        }
        return a;
    }

    private static double[] fill(final int n, final double v) {
        final double[] a = new double[n];
        java.util.Arrays.fill(a, v);
        return a;
    }
}
