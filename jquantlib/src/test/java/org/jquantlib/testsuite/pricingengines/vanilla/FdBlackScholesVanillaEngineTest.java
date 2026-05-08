/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.pricingengines.vanilla;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.pricingengines.vanilla.FdBlackScholesVanillaEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Phase 2m Track A fingerprint test for {@link FdBlackScholesVanillaEngine}.
 *
 * <p>Cross-validates NPV, delta, and gamma across a 33-case grid of
 * (strike, maturity, vol, type, exercise, scheme, grid) combinations
 * against the C++ v1.42.1 probe
 * {@code migration-harness/cpp/probes/pricingengines/vanilla/fd_black_scholes_vanilla_engine_probe.cpp}.
 *
 * <p><strong>Tolerance tier</strong> — {@link Tolerance#loose} (1e-8 relative).
 * FD engines accumulate numerical noise across 100 time steps × 100 mesh
 * points. Greeks (delta, gamma) may show slightly higher residuals due to
 * finite-difference differentiation of the interpolated solution.
 */
public class FdBlackScholesVanillaEngineTest {

    /** Base process parameters — must mirror the C++ probe. */
    private static final double S     = 100.0;
    private static final double R     = 0.05;
    private static final double Q     = 0.02;

    @Test
    public void npvMatchesCpp() {
        final ReferenceReader reader = ReferenceReader.load(
                "pricingengines/vanilla/fd_black_scholes_vanilla_engine");

        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final DayCounter dc = new Actual365Fixed();
        final NullCalendar cal = new NullCalendar();

        // Build processes for vol = 20%, 30%, 10%, 50%
        final GeneralizedBlackScholesProcess p20 = makeGBS(eval, S, R, Q, 0.20, dc, cal);
        final GeneralizedBlackScholesProcess p30 = makeGBS(eval, S, R, Q, 0.30, dc, cal);
        final GeneralizedBlackScholesProcess p10 = makeGBS(eval, S, R, Q, 0.10, dc, cal);
        final GeneralizedBlackScholesProcess p50 = makeGBS(eval, S, R, Q, 0.50, dc, cal);

        final StringBuilder failures = new StringBuilder();

        // ---- iterate over all reference cases ----
        for (final String caseName : reader.caseNames()) {
            final Case ref = reader.getCase(caseName);
            final JSONObject in  = ref.inputs();
            final JSONObject exp = (JSONObject) ref.expectedRaw();

            final double strike       = in.getDouble("strike");
            final double matYears     = in.getDouble("maturity_years");
            final String typeStr      = in.getString("option_type");
            final String exTypeStr    = in.getString("exercise_type");
            final int tGrid           = in.getInt("t_grid");
            final int xGrid           = in.getInt("x_grid");
            final int dampingSteps    = in.getInt("damping_steps");

            final double cppNpv   = exp.getDouble("npv");
            final double cppDelta = exp.getDouble("delta");
            final double cppGamma = exp.getDouble("gamma");

            // Pick process by vol embedded in case name
            final GeneralizedBlackScholesProcess proc;
            if (caseName.endsWith("_v30")) {
                proc = p30;
            } else if (caseName.endsWith("_v10")) {
                proc = p10;
            } else if (caseName.endsWith("_v50")) {
                proc = p50;
            } else {
                proc = p20;
            }

            // Scheme
            final FdmSchemeDesc scheme;
            if (caseName.endsWith("_ie")) {
                scheme = FdmSchemeDesc.ImplicitEuler();
            } else {
                scheme = FdmSchemeDesc.Douglas();
            }

            // Build exercise date: eval + round(matYears * 365) calendar days
            final Date exerciseDate = eval.add((int) Math.round(matYears * 365));

            final Exercise exercise;
            if ("European".equals(exTypeStr)) {
                exercise = new EuropeanExercise(exerciseDate);
            } else {
                exercise = new AmericanExercise(eval, exerciseDate);
            }

            final Option.Type type = "Call".equals(typeStr) ? Option.Type.Call : Option.Type.Put;
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
            final VanillaOption option = new VanillaOption(payoff, exercise);

            final FdBlackScholesVanillaEngine engine =
                    new FdBlackScholesVanillaEngine(proc, tGrid, xGrid, dampingSteps, scheme);
            option.setPricingEngine(engine);

            try {
                final double jNpv   = option.NPV();
                final double jDelta = option.delta();
                final double jGamma = option.gamma();

                if (!Tolerance.loose(jNpv, cppNpv)) {
                    failures.append(String.format(
                            "[%s] NPV: java=%.10f cpp=%.10f diff=%.3e%n",
                            caseName, jNpv, cppNpv, Math.abs(jNpv - cppNpv)));
                }
                // Delta tolerance: greeks are differentiated — allow loose
                if (!Tolerance.loose(jDelta, cppDelta)) {
                    failures.append(String.format(
                            "[%s] delta: java=%.10f cpp=%.10f diff=%.3e%n",
                            caseName, jDelta, cppDelta, Math.abs(jDelta - cppDelta)));
                }
                // Gamma tolerance: second derivative — allow 10x loose
                if (!Tolerance.within(jGamma, cppGamma, 1e-7,
                        "FD gamma: second derivative of interpolated 1D solution; 10x loose justified")) {
                    failures.append(String.format(
                            "[%s] gamma: java=%.10f cpp=%.10f diff=%.3e%n",
                            caseName, jGamma, cppGamma, Math.abs(jGamma - cppGamma)));
                }
            } catch (final Exception ex) {
                failures.append(String.format(
                        "[%s] EXCEPTION: %s%n", caseName, ex.getMessage()));
            }
        }

        if (failures.length() > 0) {
            fail("FdBlackScholesVanillaEngine failures:\n" + failures);
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static GeneralizedBlackScholesProcess makeGBS(
            final Date eval, final double spot, final double r,
            final double q, final double vol,
            final DayCounter dc, final NullCalendar cal) {

        final Handle<Quote> spotH = new Handle<>(new SimpleQuote(spot));
        final Handle<YieldTermStructure> rTS = new Handle<>(
                new FlatForward(eval, new Handle<>(new SimpleQuote(r)), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<YieldTermStructure> qTS = new Handle<>(
                new FlatForward(eval, new Handle<>(new SimpleQuote(q)), dc,
                        Compounding.Continuous, Frequency.Annual));
        final Handle<org.jquantlib.termstructures.BlackVolTermStructure> volTS =
                new Handle<>(new BlackConstantVol(eval, cal, vol, dc));

        return new GeneralizedBlackScholesProcess(spotH, qTS, rTS, volTS);
    }
}
