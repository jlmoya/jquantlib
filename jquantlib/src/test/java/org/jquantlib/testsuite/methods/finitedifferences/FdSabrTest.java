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
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
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
 *       Body-filled in Phase 5e.5b-CFC-d-71 — uses ported
 *       {@code Sabr#sabrVolatility} (Hagan) and
 *       {@code VanillaOption#impliedVolatility} (analytic European)
 *       against {@code FdSabrVanillaEngine} NPVs.</li>
 *   <li>{@code testOosterleeTestCaseIV} — requires
 *       {@code RichardsonExtrapolation} (NOT ported).</li>
 *   <li>{@code testBenchOpSabrCase} — body-filled in Phase 5e.5b-CFC-d-71
 *       using the hard-coded reference table from
 *       BENCHOP-SLV (von Sydow et al., 2018).</li>
 * </ul>
 *
 * <p><strong>Tolerance tier</strong>: TIGHT 1e-4 absolute for put/call
 * parity (matches C++ {@code parityTol = 1e-4} verbatim); 2.5e-3 absolute
 * for Hagan vs FDM implied vols (matches C++); 2e-4 absolute for BenchOp
 * (matches C++).
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

    /**
     * {@code testFdmSabrVsVolApproximation} — verifies that the
     * Black implied vol backed out of {@code FdSabrVanillaEngine}
     * NPVs matches the Hagan-Kumar-Lesniewski-Woodward (2002) SABR
     * lognormal vol approximation to within 2.5e-3 (matches C++).
     *
     * <p>Phase 5e.5b-CFC-d-71 body-fill.  All required classes are now
     * in place: {@link Sabr#sabrVolatility} (Phase 4f.5),
     * {@link VanillaOption#impliedVolatility(double, GeneralizedBlackScholesProcess)}
     * (already in place), and {@link FdSabrVanillaEngine}
     * (Phase 2m Track C).
     */
    @Test
    public void testFdmSabrVsVolApproximation() {
        final Date today = new Date(8, Month.January, 2019);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(6, TimeUnit.Months));
        final double maturityTime = dc.yearFraction(today, maturityDate);

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.05)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);

        final double f0 = 100.0;

        final Handle<BlackVolTermStructure> volH = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(), 0.2, dc));
        final GeneralizedBlackScholesProcess bsProcess = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(new SimpleQuote(f0)), rTS, rTS, volH);

        final double alpha = 0.35;
        final double beta  = 0.85;
        final double nu    = 0.75;
        final double rho   = 0.85;

        final double[] strikes = { 90.0, 100.0, 110.0 };
        final Option.Type[] optionTypes = { Option.Type.Put, Option.Type.Call };

        final double tol = 2.5e-3;
        final Sabr sabr = new Sabr();

        for (final Option.Type optionType : optionTypes) {
            for (final double strike : strikes) {
                final VanillaOption option = new VanillaOption(
                        new PlainVanillaPayoff(optionType, strike),
                        new EuropeanExercise(maturityDate));

                option.setPricingEngine(new FdSabrVanillaEngine(
                        f0, alpha, beta, nu, rho, rTS, 25, 100, 50));

                final double fdmVol =
                        option.impliedVolatility(option.NPV(), bsProcess);

                final double hagenVol = sabr.sabrVolatility(
                        strike, f0, maturityTime, alpha, beta, nu, rho);

                final double diff = Math.abs(fdmVol - hagenVol);

                if (diff > tol) {
                    fail("large difference between Hagen formula and FDM"
                            + "\n    strike          : " + strike
                            + "\n    option type     : " + optionType
                            + "\n    Hagen vol       : " + hagenVol
                            + "\n    pde vol         : " + fdmVol
                            + "\n    vol difference  : " + diff
                            + "\n    tolerance       : " + tol);
                }
            }
        }
    }

    /**
     * {@code testBenchOpSabrCase} — reproduces the BENCHOP-SLV
     * reference SABR call prices (von Sydow et al., 2018) using
     * the FD-SABR engine.
     *
     * <p>Phase 5e.5b-CFC-d-71 body-fill.  Reference values are
     * hard-coded verbatim from C++ {@code fdsabr.cpp:452-455}.
     */
    @Test
    public void testBenchOpSabrCase() {
        final Date today = new Date(8, Month.January, 2019);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);

        final int[] maturityInYears = { 2, 10 };

        final double[] f0s    = { 0.5,  0.07 };
        final double[] alphas = { 0.5,  0.4  };
        final double[] nus    = { 0.4,  0.8  };
        final double[] betas  = { 0.5,  0.5  };
        final double[] rhos   = { 0.0, -0.6  };

        // Reference values copied verbatim from C++ fdsabr.cpp lines 452-455
        final double[][] expected = {
                { 0.221383196830866, 0.193836689413803, 0.166240814653231 },
                { 0.052450313614407, 0.046585753491306, 0.039291470612989 }
        };

        final int gridX = 400;
        final int gridY = 25;
        final int gridT = 10;

        final double factor = 2.0;

        final double tol = 2e-4;

        for (int i = 0; i < f0s.length; ++i) {
            final Date maturity = today.add(new Period(maturityInYears[i] * 365, TimeUnit.Days));
            final double T = dc.yearFraction(today, maturity);

            final double f0    = f0s[i];
            final double alpha = alphas[i];
            final double nu    = nus[i];
            final double beta  = betas[i];
            final double rho   = rhos[i];

            final double[] strikes = {
                    f0 * Math.exp(-0.1 * Math.sqrt(T)),
                    f0,
                    f0 * Math.exp(0.1 * Math.sqrt(T))
            };

            for (int j = 0; j < strikes.length; ++j) {
                final double strike = strikes[j];

                final VanillaOption option = new VanillaOption(
                        new PlainVanillaPayoff(Option.Type.Call, strike),
                        new EuropeanExercise(maturity));

                option.setPricingEngine(new FdSabrVanillaEngine(
                        f0, alpha, beta, nu, rho, rTS,
                        (int) (gridT * factor),
                        (int) (gridX * factor),
                        (int) (gridY * Math.sqrt(factor))));

                final double calculated = option.NPV();
                final double diff = Math.abs(calculated - expected[i][j]);

                if (diff > tol) {
                    fail("failed to reproduce reference values"
                            + "\n    strike     : " + strike
                            + "\n    maturity   : " + maturity
                            + "\n    reference  : " + expected[i][j]
                            + "\n    calculated : " + calculated
                            + "\n    difference : " + diff
                            + "\n    tolerance  : " + tol);
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // ----------------- DEFERRED — Phase 5j.5 carry-forward -----------------
    // ------------------------------------------------------------------------

    /** Full {@code testFdmSabrOp} including MC implied-vol comparison.
     * Java production gap: {@code SobolBrownianBridgeRsg} (C++
     * {@code ql/math/randomnumbers/sobolbrownianbridgersg.{hpp,cpp}}) and
     * {@code RichardsonExtrapolation} (C++
     * {@code ql/math/richardsonextrapolation.{hpp,cpp}}) are not yet
     * ported.  Both are needed to compute the MC implied-vol benchmark
     * that the PDE implied-vol is compared against (C++
     * {@code fdsabr.cpp:208-233}).
     */
    @Ignore("Phase 5j.5 — needs SobolBrownianBridgeRsg + RichardsonExtrapolation Java ports "
            + "(production gap: ql/math/randomnumbers/sobolbrownianbridgersg.{hpp,cpp} + "
            + "ql/math/richardsonextrapolation.{hpp,cpp})")
    @Test
    public void testFdmSabrOp_mcImpliedVol() {
        fail("not implemented");
    }

    /** {@code testFdmSabrCevPricing} — degenerate-vol-of-vol SABR
     * collapses to CEV.  Java production gap:
     * {@code AnalyticCEVEngine} (C++
     * {@code ql/pricingengines/vanilla/analyticcevengine.{hpp,cpp}})
     * is not yet ported.  This is a Phase 4n.5 carry-forward;
     * {@code CEVRNDCalculator} is already in place but the
     * vanilla-option pricing wrapper around it is missing.
     */
    @Ignore("Phase 5j.5 — needs AnalyticCEVEngine Java port "
            + "(production gap: ql/pricingengines/vanilla/analyticcevengine.{hpp,cpp}; "
            + "Phase 4n.5 carry-forward)")
    @Test
    public void testFdmSabrCevPricing() {
        fail("not implemented");
    }

    /** {@code testOosterleeTestCaseIV} — reproduces Chen-Oosterlee-Weide
     * (2017) test case IV.  Java production gap:
     * {@code RichardsonExtrapolation} (C++
     * {@code ql/math/richardsonextrapolation.{hpp,cpp}}) is required
     * to extrapolate the 2-point Monte-Carlo reference table to
     * zero step size.  The 9x2 reference table itself is
     * straightforward to port once the extrapolation utility is
     * available.
     */
    @Ignore("Phase 5j.5 — needs RichardsonExtrapolation Java port "
            + "(production gap: ql/math/richardsonextrapolation.{hpp,cpp}); "
            + "reference table is small and ready to inline once available")
    @Test
    public void testOosterleeTestCaseIV() {
        fail("not implemented");
    }
}
