/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.methods.finitedifferences;

import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.vanilla.FdSabrVanillaEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5j port of {@code test-suite/fdsabr.cpp} v1.42.1.
 *
 * <p>Java {@code FdSabrVanillaEngine} (Phase 2m Track C) is already covered
 * by {@code FdSabrVanillaEngineTest} (NPV grid vs C++ probe, LOOSE).  This
 * Phase 5j port adds the C++ test cases that exercise the engine in
 * different ways:
 * <ul>
 *   <li>{@code testFdmSabrOp} — put/call parity via PDE engine.  The MC
 *       portion of the C++ test ({@code SobolBrownianBridgeRsg} +
 *       {@code RichardsonExtrapolation}) is deferred — those classes are
 *       not yet ported.</li>
 *   <li>{@code testFdmSabrCevPricing} — degenerate-vol-of-vol SABR
 *       collapses to CEV.  Requires {@code AnalyticCEVEngine} (NOT
 *       ported).</li>
 *   <li>{@code testFdmSabrVsVolApproximation} — PDE vs Hagan formula.
 *       Requires SABR analytic vol formula (Phase 2r).</li>
 *   <li>{@code testOosterleeTestCaseIV}, {@code testBenchOpSabrCase} —
 *       require pre-tabulated Oosterlee/BenchOp reference data.</li>
 * </ul>
 *
 * <p><strong>Tolerance tier</strong>: TIGHT 1e-4 absolute for put/call
 * parity (matches C++ {@code parityTol = 1e-4} verbatim).
 */
public class FdSabrTest {

    /** {@code testFdmSabrOp} — put/call parity portion only.
     * The MC implied-vol comparison is deferred (Phase 5j.5).
     */
    @Test
    public void testFdmSabrOp_putCallParity() {
        final Date today = new Date(22, Month.February, 2018);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(2, TimeUnit.Years));

        final double strike = 1.5;
        final Exercise exercise = new EuropeanExercise(maturityDate);
        final PlainVanillaPayoff putPayoff  = new PlainVanillaPayoff(Option.Type.Put,  strike);
        final PlainVanillaPayoff callPayoff = new PlainVanillaPayoff(Option.Type.Call, strike);

        final VanillaOption optionPut  = new VanillaOption(putPayoff,  exercise);
        final VanillaOption optionCall = new VanillaOption(callPayoff, exercise);

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);

        final double f0    = 1.0;
        final double alpha = 0.35;
        final double nu    = 1.0;
        final double rho   = 0.25;

        final double[] betas = { 0.25, 0.6 };
        final double parityTol = 1e-4;

        for (final double beta : betas) {
            final FdSabrVanillaEngine pdeEngine = new FdSabrVanillaEngine(
                    f0, alpha, beta, nu, rho, rTS,
                    100, 400, 100);  // tGrid, fGrid, xGrid (matches C++)

            optionPut.setPricingEngine(pdeEngine);
            final double pdePut = optionPut.NPV();

            optionCall.setPricingEngine(pdeEngine);
            final double pdeCall = optionCall.NPV();

            final double pdeFwd = pdeCall - pdePut;
            final double parityDiff = Math.abs(pdeFwd - (f0 - strike));

            if (parityDiff > parityTol) {
                fail("call/put parity failed at beta=" + beta
                        + "\n    fwd (call/put) : " + pdeFwd
                        + "\n    fwd (f0-strike): " + (f0 - strike)
                        + "\n    diff           : " + parityDiff
                        + "\n    tol            : " + parityTol);
            }
        }
    }

    // ------------------------------------------------------------------------
    // ----------------- DEFERRED — Phase 5j.5 carry-forward -----------------
    // ------------------------------------------------------------------------

    /** Full {@code testFdmSabrOp} including MC implied-vol comparison —
     * requires {@code SobolBrownianBridgeRsg} + {@code RichardsonExtrapolation}
     * (NOT yet ported).
     */
    @Ignore("Phase 5j.5 — MC arm requires SobolBrownianBridgeRsg + RichardsonExtrapolation")
    @Test
    public void testFdmSabrOp_mcImpliedVol() {
        fail("not implemented");
    }

    /** {@code testFdmSabrCevPricing} — requires {@code AnalyticCEVEngine}
     * (NOT ported — Phase 4n.5 carry-forward).  Engine compares FD-SABR
     * with degenerate vol-of-vol against CEV analytic.
     */
    @Ignore("Phase 5j.5 — requires AnalyticCEVEngine (Phase 4n.5 carry)")
    @Test
    public void testFdmSabrCevPricing() {
        fail("not implemented");
    }

    /** {@code testFdmSabrVsVolApproximation} — requires Hagan SABR
     * analytic vol formula.  Phase 2r SabrInterpolation is partially in
     * place but the full surface comparison wiring is not yet exercised.
     */
    @Ignore("Phase 5j.5 — requires Hagan SABR vol formula full wiring")
    @Test
    public void testFdmSabrVsVolApproximation() {
        fail("not implemented");
    }

    /** {@code testOosterleeTestCaseIV} — requires hard-coded Oosterlee
     * benchmark reference values + RichardsonExtrapolation.
     */
    @Ignore("Phase 5j.5 — requires Oosterlee reference data + RichardsonExtrapolation")
    @Test
    public void testOosterleeTestCaseIV() {
        fail("not implemented");
    }

    /** {@code testBenchOpSabrCase} — BenchOp pricing benchmark.
     * Requires pre-tabulated reference data + bench-op infra.
     */
    @Ignore("Phase 5j.5 — requires BenchOp reference data")
    @Test
    public void testBenchOpSabrCase() {
        fail("not implemented");
    }
}
