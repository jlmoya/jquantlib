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
import org.jquantlib.math.Constants;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.statistics.GeneralStatistics;
import org.jquantlib.methods.finitedifferences.utilities.CEVRNDCalculator;
import org.jquantlib.pricingengines.vanilla.AnalyticCEVEngine;
import org.jquantlib.pricingengines.vanilla.FdCEVVanillaEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-112 port of {@code test-suite/fdcev.cpp} v1.42.1.
 *
 * <p>Both tests body-filled — exercises:
 * <ul>
 *   <li>{@code testLocalMartingale} — CEV process martingale property
 *       via PDF integral (Gauss-Lobatto) and Monte-Carlo Euler sim.</li>
 *   <li>{@code testFdmCevOp} — FdmCEVOp + FdCEVVanillaEngine convergence
 *       vs AnalyticCEVEngine for NPV and finite-difference delta.</li>
 * </ul>
 *
 * <p>Source: {@code test-suite/fdcev.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class FdCevTest {

    /**
     * Helper: martingale-integrand f * pdf(f, t) for {@link GaussLobattoIntegral}.
     * Mirrors C++ {@code ExpectationFct}.
     */
    private static final class ExpectationFct
            implements org.jquantlib.math.Ops.DoubleOp {
        private final CEVRNDCalculator calc_;
        private final double t_;
        ExpectationFct(final CEVRNDCalculator calc, final double t) {
            this.calc_ = calc; this.t_ = t;
        }
        @Override
        public double op(final double f) {
            return f * calc_.pdf(f, t_);
        }
    }

    @Test
    public void testLocalMartingale() {
        // Direct port of C++ test-suite/fdcev.cpp:testLocalMartingale.
        final double t     = 1.0;
        final double f0    = 2.1;
        final double alpha = 1.75;
        final double[] betas = { -2.4, 0.23, 0.9, 1.1, 1.5 };

        for (final double beta : betas) {
            final CEVRNDCalculator rnd = new CEVRNDCalculator(f0, alpha, beta);

            final double eps = 1e-10;
            final double tol = 100 * eps;

            final double upperBound = 10.0 * rnd.invcdf(1.0 - eps, t);

            final double expectationValue = new GaussLobattoIntegral(10000, eps)
                    .op(new ExpectationFct(rnd, t),
                            Constants.QL_EPSILON, upperBound);

            final double diff = expectationValue - f0;

            if (beta < 1.0 && Math.abs(diff) > tol) {
                fail("CEV process should be a martingale for beta < 1.0"
                        + "; beta=" + beta
                        + ", expected " + f0
                        + ", diff=" + diff
                        + ", tol=" + tol);
            }

            if (beta > 1.0 && diff > -tol) {
                fail("CEV process should only be a local martingale for beta > 1.0"
                        + "; beta=" + beta
                        + ", E[F_t]=" + expectationValue
                        + ", F_0=" + f0);
            }

            // Monte-Carlo Euler simulation for beta > 1.2 (heavy regime)
            if (beta > 1.2) {
                final int nSims  = 5000;
                final int nSteps = 2000;
                final double dt  = t / nSteps;
                final double sqrtDt = Math.sqrt(dt);

                final GeneralStatistics stat = new GeneralStatistics();
                final MersenneTwisterUniformRng mt = new MersenneTwisterUniformRng(42);
                final InverseCumulativeNormal icn = new InverseCumulativeNormal();

                for (int i = 0; i < nSims; ++i) {
                    double f = f0;
                    for (int j = 0; j < nSteps; ++j) {
                        // C++ PseudoRandom::rng_type wraps MT through
                        // InverseCumulativeNormal: each next().value is a
                        // standard normal deviate built from a uniform.
                        final double u = mt.next().value();
                        final double z = icn.op(u);
                        f += alpha * Math.pow(f, beta) * z * sqrtDt;
                        if (f < 0.0) f = 0.0;
                        if (f == 0.0) break; // absorbing boundary
                    }
                    stat.add(f - f0);
                }

                final double calculated = stat.mean();
                final double error      = stat.errorEstimate();

                if (Math.abs(calculated - diff) > 2.35 * error) {
                    fail("MC local-martingale failed for beta=" + beta
                            + "; E[F_t|F_0]=" + expectationValue
                            + ", E_MC[F_t|F_0]=" + (calculated + f0)
                            + ", error_MC=" + error
                            + ", diff=" + Math.abs(calculated - diff)
                            + ", tol=" + (2.35 * error));
                }
            }
        }
    }

    @Test
    public void testFdmCevOp() {
        // Direct port of C++ test-suite/fdcev.cpp:testFdmCevOp.
        final Date today = new Date(22, Month.February, 2018);
        final DayCounter dc = new Actual365Fixed();
        new Settings().setEvaluationDate(today);

        final Date maturityDate = today.add(new Period(12, TimeUnit.Months));
        final double strike = 2.3;

        final Option.Type[] optionTypes = { Option.Type.Call, Option.Type.Put };

        final Exercise exercise = new EuropeanExercise(maturityDate);

        for (final Option.Type optionType : optionTypes) {
            final PlainVanillaPayoff payoff =
                    new PlainVanillaPayoff(optionType, strike);

            final YieldTermStructure rTS = Utilities.flatRate(today, 0.15, dc);

            final double f0    = 2.1;
            final double alpha = 0.75;

            final double[] betas = { -2.0, -0.5, 0.45, 0.6, 0.9, 1.45 };

            for (final double beta : betas) {
                final VanillaOption option = new VanillaOption(payoff, exercise);
                option.setPricingEngine(new AnalyticCEVEngine(
                        f0, alpha, beta,
                        new Handle<YieldTermStructure>(rTS)));
                final double analyticNPV = option.NPV();

                final double eps = 1e-3;

                option.setPricingEngine(new AnalyticCEVEngine(
                        f0 * (1.0 + eps), alpha, beta,
                        new Handle<YieldTermStructure>(rTS)));
                final double analyticUpNPV = option.NPV();

                option.setPricingEngine(new AnalyticCEVEngine(
                        f0 * (1.0 - eps), alpha, beta,
                        new Handle<YieldTermStructure>(rTS)));
                final double analyticDownNPV = option.NPV();

                final double analyticDelta = (analyticUpNPV - analyticDownNPV)
                        / (2.0 * eps * f0);

                option.setPricingEngine(new FdCEVVanillaEngine(
                        f0, alpha, beta,
                        new Handle<YieldTermStructure>(rTS),
                        100, 1000, 1, 1.0, 1e-6));

                final double calculatedNPV   = option.NPV();
                final double calculatedDelta = option.delta();

                final double tol = 0.01;
                if (Math.abs(calculatedNPV - analyticNPV) > tol
                        || Math.abs(calculatedDelta - analyticDelta) > tol) {
                    fail("FdCEV vs AnalyticCEV mismatch"
                            + "; beta=" + beta
                            + ", type=" + optionType
                            + ", analyticNPV=" + analyticNPV
                            + ", pdeNPV=" + calculatedNPV
                            + ", npvDiff=" + Math.abs(calculatedNPV - analyticNPV)
                            + ", analyticDelta=" + analyticDelta
                            + ", pdeDelta=" + calculatedDelta
                            + ", deltaDiff=" + Math.abs(calculatedDelta - analyticDelta)
                            + ", tol=" + tol);
                }
            }
        }
    }
}
