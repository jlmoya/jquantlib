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
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.Ops;
import org.jquantlib.math.RichardsonExtrapolation;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.randomnumbers.SobolBrownianBridgeRsg;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.math.statistics.GeneralStatistics;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;
import org.jquantlib.pricingengines.vanilla.AnalyticCEVEngine;
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
import org.junit.Test;

/**
 * Phase 5j port of {@code test-suite/fdsabr.cpp} v1.42.1.
 *
 * <p>Java {@code FdSabrVanillaEngine} (Phase 2m Track C) is already covered
 * by {@code FdSabrVanillaEngineTest} (NPV grid vs C++ probe, LOOSE).  This
 * Phase 5j port adds the C++ test cases that exercise the engine in
 * different ways:
 * <ul>
 *   <li>{@code testFdmSabrOp_putCallParity} — put/call parity via PDE
 *       engine (matches the parity portion of C++ {@code testFdmSabrOp}).</li>
 *   <li>{@code testFdmSabrOp_mcImpliedVol} — MC implied-vol comparison
 *       portion of C++ {@code testFdmSabrOp}.  Body-filled in Phase
 *       5e.5b-CFC-d-178 once {@code SobolBrownianBridgeRsg} and
 *       {@code RichardsonExtrapolation} were ported.</li>
 *   <li>{@code testFdmSabrCevPricing} — degenerate-vol-of-vol SABR
 *       collapses to CEV.  Body-filled in Phase 5e.5b-CFC-d-178 using
 *       the ported {@link AnalyticCEVEngine}.</li>
 *   <li>{@code testFdmSabrVsVolApproximation} — PDE vs Hagan formula.
 *       Body-filled in Phase 5e.5b-CFC-d-71 — uses ported
 *       {@code Sabr#sabrVolatility} (Hagan) and
 *       {@code VanillaOption#impliedVolatility} (analytic European)
 *       against {@code FdSabrVanillaEngine} NPVs.</li>
 *   <li>{@code testOosterleeTestCaseIV} — Chen-Oosterlee-Weide test case
 *       IV.  Body-filled in Phase 5e.5b-CFC-d-178 using ported
 *       {@link RichardsonExtrapolation} against the 9x2 reference
 *       table from the paper.</li>
 *   <li>{@code testBenchOpSabrCase} — body-filled in Phase 5e.5b-CFC-d-71
 *       using the hard-coded reference table from
 *       BENCHOP-SLV (von Sydow et al., 2018).</li>
 * </ul>
 *
 * <p><strong>Tolerance tier</strong>: TIGHT 1e-4 absolute for put/call
 * parity (matches C++ {@code parityTol = 1e-4} verbatim); 2.5e-3 absolute
 * for Hagan vs FDM implied vols (matches C++); 2e-4 absolute for BenchOp
 * (matches C++); 5e-3 absolute for PDE-vs-MC implied vol (matches C++);
 * 5e-5 absolute for SABR-collapses-to-CEV (matches C++); 0.00035 absolute
 * for the Oosterlee reference (matches C++).
 */
public class FdSabrTest {

    /**
     * Name-alias for the split-by-two {@link #testFdmSabrOp_putCallParity()}
     * and {@link #testFdmSabrOp_mcImpliedVol()} so the audit script's
     * @Test-name match against {@code test-suite/fdsabr.cpp testFdmSabrOp}
     * passes (Round A8-E). The C++ test combines both portions; the Java
     * port splits them for clarity (header above documents the split).
     */
    @Test
    public void testFdmSabrOp() {
        testFdmSabrOp_putCallParity();
        testFdmSabrOp_mcImpliedVol();
    }

    /** {@code testFdmSabrOp} — put/call parity portion only.
     * The MC implied-vol comparison is body-filled separately in
     * {@link #testFdmSabrOp_mcImpliedVol()}.
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
     * {@code testFdmSabrOp} — MC implied-vol comparison portion
     * (C++ {@code fdsabr.cpp:208-233}).
     *
     * <p>Phase 5e.5b-CFC-d-178 body-fill: now that
     * {@link SobolBrownianBridgeRsg} (Phase 4o.5 carry-forward) and
     * {@link RichardsonExtrapolation} (Phase 5e.5b-CFC-d-91) are in
     * place, we reproduce the C++ MC reference and compare the
     * resulting Black implied vol against the FDM implied vol to
     * within 5e-3 (matches C++ {@code volTol = 5e-3}).
     */
    @Test
    public void testFdmSabrOp_mcImpliedVol() {
        final Date today = new Date(22, Month.February, 2018);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(2, TimeUnit.Years));
        final double maturityTime = dc.yearFraction(today, maturityDate);

        final double strike = 1.5;
        final Exercise exercise = new EuropeanExercise(maturityDate);
        final PlainVanillaPayoff putPayoff = new PlainVanillaPayoff(Option.Type.Put, strike);

        final VanillaOption optionPut = new VanillaOption(putPayoff, exercise);

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);

        final double f0    = 1.0;
        final double alpha = 0.35;
        final double nu    = 1.0;
        final double rho   = 0.25;

        final double[] betas = { 0.25, 0.6 };

        final Handle<BlackVolTermStructure> volH = new Handle<BlackVolTermStructure>(
                new BlackConstantVol(today, new NullCalendar(), 0.2, dc));
        final GeneralizedBlackScholesProcess bsProcess = new GeneralizedBlackScholesProcess(
                new Handle<Quote>(new SimpleQuote(f0)), rTS, rTS, volH);

        final double volTol = 5e-3;

        for (final double beta : betas) {
            final FdSabrVanillaEngine pdeEngine = new FdSabrVanillaEngine(
                    f0, alpha, beta, nu, rho, rTS, 100, 400, 100);
            optionPut.setPricingEngine(pdeEngine);

            final double putPdeImplVol =
                    optionPut.impliedVolatility(optionPut.NPV(), bsProcess, 1e-6);

            final SabrMonteCarloPricer mcSabr = new SabrMonteCarloPricer(
                    f0, maturityTime, putPayoff, alpha, beta, nu, rho);

            final double mcNPV = new RichardsonExtrapolation(mcSabr, 1.0 / 4.0)
                    .valueAt(4.0, 2.0);

            final double putMcImplVol =
                    optionPut.impliedVolatility(mcNPV, bsProcess, 1e-6);

            final double volDiff = Math.abs(putPdeImplVol - putMcImplVol);

            if (volDiff > volTol) {
                fail("failed to validate PDE against MC implied volatility"
                        + "\n    beta         : " + beta
                        + "\n    strike       : " + strike
                        + "\n    PDE impl vol : " + putPdeImplVol
                        + "\n    MC  impl vol : " + putMcImplVol
                        + "\n    diff         : " + volDiff
                        + "\n    tol          : " + volTol);
            }
        }
    }

    /**
     * {@code testFdmSabrCevPricing} — degenerate-vol-of-vol SABR
     * collapses to CEV (C++ {@code fdsabr.cpp:237-298}).
     *
     * <p>Phase 5e.5b-CFC-d-178 body-fill: the FD-SABR PDE engine is
     * compared against the analytic CEV engine ({@link AnalyticCEVEngine},
     * Phase 4n.5) with {@code nu = 1e-3} (effectively zero vol-of-vol).
     * Tolerance 5e-5 matches C++ verbatim.
     */
    @Test
    public void testFdmSabrCevPricing() {
        final Date today = new Date(3, Month.January, 2019);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(12, TimeUnit.Months));

        final double[] betas   = { 0.1, 0.9 };
        final double[] strikes = { 0.9, 1.5 };

        final double f0    = 1.2;
        final double alpha = 0.35;
        final double nu    = 1e-3;
        final double rho   = 0.25;

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.05)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);

        final Exercise exercise = new EuropeanExercise(maturityDate);

        final Option.Type[] optionTypes = { Option.Type.Put, Option.Type.Call };

        final double tol = 5e-5;

        for (final Option.Type optionType : optionTypes) {
            for (final double strike : strikes) {
                final PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(optionType, strike);
                final VanillaOption option = new VanillaOption(payoff, exercise);

                for (final double beta : betas) {
                    option.setPricingEngine(new FdSabrVanillaEngine(
                            f0, alpha, beta, nu, rho, rTS, 100, 400, 3));
                    final double calculated = option.NPV();

                    option.setPricingEngine(new AnalyticCEVEngine(
                            f0, alpha, beta, rTS));
                    final double expected = option.NPV();

                    final double diff = Math.abs(expected - calculated);
                    if (diff > tol) {
                        fail("failed to calculate vanilla CEV option prices"
                                + "\n    beta            : " + beta
                                + "\n    strike          : " + strike
                                + "\n    option type     : "
                                + (optionType == Option.Type.Call ? "Call" : "Put")
                                + "\n    analytic npv    : " + expected
                                + "\n    pde npv         : " + calculated
                                + "\n    npv difference  : " + diff
                                + "\n    tolerance       : " + tol);
                    }
                }
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
     * {@code testOosterleeTestCaseIV} — reproduces Chen-Oosterlee-Weide
     * (2017) test case IV (C++ {@code fdsabr.cpp:360-423}).
     *
     * <p>Phase 5e.5b-CFC-d-178 body-fill: uses the ported
     * {@link RichardsonExtrapolation} with the unknown-order overload
     * ({@code valueAt(t)} with explicit {@code n = 1}) to extrapolate
     * the 2-point MC reference table from the paper down to zero step
     * size.  Tolerance 0.00035 matches C++ verbatim.
     */
    @Test
    public void testOosterleeTestCaseIV() {
        final Date today = new Date(8, Month.January, 2019);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final YieldTermStructure flatR = new FlatForward(today,
                new Handle<Quote>(new SimpleQuote(0.0)),
                dc, Compounding.Continuous, Frequency.Annual);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(flatR);

        final double f0    =  0.07;
        final double alpha =  0.4;
        final double nu    =  0.8;
        final double beta  =  0.4;
        final double rho   = -0.6;

        final Period[] maturities = {
                new Period(2, TimeUnit.Years),
                new Period(5, TimeUnit.Years),
                new Period(10, TimeUnit.Years)
        };

        final double[] strikes = { 0.4 * f0, f0, 1.6 * f0 };

        final double tol = 0.00035;
        for (int i = 0; i < maturities.length; ++i) {
            final Date maturityDate = today.add(maturities[i]);
            final double maturityTime = dc.yearFraction(today, maturityDate);

            final int timeSteps = (int) (5 * maturityTime);

            final FdSabrVanillaEngine engine = new FdSabrVanillaEngine(
                    f0, alpha, beta, nu, rho, rTS, timeSteps, 200, 21);

            final Exercise exercise = new EuropeanExercise(maturityDate);

            for (int j = 0; j < strikes.length; ++j) {
                final PlainVanillaPayoff payoff =
                        new PlainVanillaPayoff(Option.Type.Call, strikes[j]);

                final VanillaOption option = new VanillaOption(payoff, exercise);
                option.setPricingEngine(engine);

                final double calculated = option.NPV();

                final OosterleeReferenceResults referenceResults =
                        new OosterleeReferenceResults(i * 3 + j);

                // C++: RichardsonExtrapolation(fn, 1/16., 1)(2.)
                //  -> known-order overload with n=1, then valueAt(t=2)
                final double expected =
                        new RichardsonExtrapolation(referenceResults, 1.0 / 16.0, 1.0)
                                .valueAt(2.0);

                final double diff = Math.abs(calculated - expected);
                if (diff > tol) {
                    fail("can not reproduce reference values from Monte-Carlo"
                            + "\n    strike     : " + strikes[j]
                            + "\n    maturity   : " + maturityDate
                            + "\n    reference  : " + expected
                            + "\n    calculated : " + calculated
                            + "\n    difference : " + diff
                            + "\n    tolerance  : " + tol);
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
    // ---------------- helpers (Java port of C++ private classes) ------------
    // ------------------------------------------------------------------------

    /**
     * Java port of the anonymous-namespace {@code SabrMonteCarloPricer}
     * functor in {@code test-suite/fdsabr.cpp:46-106}.  Implements
     * {@link Ops.DoubleOp} so it can be passed to
     * {@link RichardsonExtrapolation} as a function of the time-step
     * size.  Mirrors the simple-Euler MC scheme verbatim, including
     * the {@code SobolBrownianBridgeRsg(2, timeSteps, Diagonal, 12345)}
     * seeding.
     */
    private static final class SabrMonteCarloPricer implements Ops.DoubleOp {
        private final double f0_;
        private final double maturity_;
        private final Payoff payoff_;
        private final double alpha_;
        private final double beta_;
        private final double nu_;
        private final double rho_;

        SabrMonteCarloPricer(final double f0,
                             final double maturity,
                             final Payoff payoff,
                             final double alpha,
                             final double beta,
                             final double nu,
                             final double rho) {
            this.f0_ = f0;
            this.maturity_ = maturity;
            this.payoff_ = payoff;
            this.alpha_ = alpha;
            this.beta_ = beta;
            this.nu_ = nu;
            this.rho_ = rho;
        }

        @Override
        public double op(final double dt) {
            final int nSims = 64 * 1024;

            final double timeStepsPerYear = 1.0 / dt;
            final int timeSteps = (int) (maturity_ * timeStepsPerYear + 1e-8);

            final double sqrtDt = Math.sqrt(dt);
            final double w = Math.sqrt(1.0 - rho_ * rho_);

            final double logAlpha = Math.log(alpha_);

            final SobolBrownianBridgeRsg rsg = new SobolBrownianBridgeRsg(
                    2, timeSteps,
                    SobolBrownianGenerator.Ordering.Diagonal,
                    12345L);

            final GeneralStatistics stats = new GeneralStatistics();

            for (int i = 0; i < nSims; ++i) {
                double f = f0_;
                double a = logAlpha;

                final Sample<double[]> sample = rsg.nextSequence();
                final double[] n = sample.value();

                for (int j = 0; j < timeSteps && f > 0.0; ++j) {
                    final double r1 = n[j];
                    final double r2 = rho_ * r1 + n[j + timeSteps] * w;

                    // simple Euler method
                    f += Math.exp(a) * Math.pow(f, beta_) * r1 * sqrtDt;
                    a += -0.5 * nu_ * nu_ * dt + nu_ * r2 * sqrtDt;
                }
                f = Math.max(0.0, f);
                stats.add(payoff_.get(f));
            }

            return stats.mean();
        }
    }

    /**
     * Java port of the anonymous-namespace
     * {@code OsterleeReferenceResults} functor in
     * {@code test-suite/fdsabr.cpp:115-140}.  Looks up the 9x2
     * reference table from Chen-Oosterlee-Weide (2017) Table IV by
     * time-step.  Implements {@link Ops.DoubleOp} so it can be passed
     * to {@link RichardsonExtrapolation}.
     */
    private static final class OosterleeReferenceResults implements Ops.DoubleOp {
        // C++ data_[9][3] in fdsabr.cpp:136-140 (third column is unused;
        // the C++ lookup only ever accesses columns 0 and 1).
        private static final double[][] DATA = {
                { 0.0610, 0.0604 }, { 0.0468, 0.0463 }, { 0.0347, 0.0343 },
                { 0.0632, 0.0625 }, { 0.0512, 0.0506 }, { 0.0406, 0.0400 },
                { 0.0635, 0.0630 }, { 0.0523, 0.0520 }, { 0.0422, 0.0421 }
        };

        private final int i_;

        OosterleeReferenceResults(final int i) {
            this.i_ = i;
        }

        @Override
        public double op(final double t) {
            final int j;
            if (Closeness.isCloseEnough(t, 1.0 / 16.0)) {
                j = 0;
            } else if (Closeness.isCloseEnough(t, 1.0 / 32.0)) {
                j = 1;
            } else {
                throw new IllegalArgumentException(
                        "unmatched reference result lookup: t=" + t);
            }
            return DATA[i_][j];
        }
    }
}
