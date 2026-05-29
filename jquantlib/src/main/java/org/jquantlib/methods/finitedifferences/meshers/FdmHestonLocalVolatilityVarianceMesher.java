/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008, 2019 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.methods.finitedifferences.meshers;

import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * One-dimensional grid mesher for the variance part of the Heston stochastic-local-volatility (SLV) model.
 * <p>
 * Java port of v1.42.1 {@code FdmHestonLocalVolatilityVarianceMesher} in
 * {@code ql/methods/finitedifferences/meshers/fdmhestonvariancemesher.{hpp,cpp}}.
 *
 * <p>This is a leverage-aware variant of {@link FdmHestonVarianceMesher}: it first builds the plain Heston variance
 * mesh (copying its {@code locations}, {@code dplus}, {@code dminus}) and then, if a {@code leverageFct} is supplied,
 * rescales the volatility estimate by the time-and-spot-averaged leverage function (a running mean over
 * {@code tAvgSteps} maturity slices, each integrated over {@code sAvgSteps=50} log-moneyness samples).
 *
 * @author JQuantLib gap-fdm port
 */
public class FdmHestonLocalVolatilityVarianceMesher extends Fdm1dMesher {

    private final double volaEstimate;

    public FdmHestonLocalVolatilityVarianceMesher(final int size,
            final HestonProcess process,
            final LocalVolTermStructure leverageFct,
            final double maturity) {
        this(size, process, leverageFct, maturity, 10, 0.0001, 1.0);
    }

    public FdmHestonLocalVolatilityVarianceMesher(final int size,
            final HestonProcess process,
            final LocalVolTermStructure leverageFct,
            final double maturity,
            final int tAvgSteps,
            final double epsilon) {
        this(size, process, leverageFct, maturity, tAvgSteps, epsilon, 1.0);
    }

    public FdmHestonLocalVolatilityVarianceMesher(final int size,
            final HestonProcess process,
            final LocalVolTermStructure leverageFct,
            final double maturity,
            final int tAvgSteps,
            final double epsilon,
            final double mixingFactor) {
        super(size);

        // C++: build the plain Heston variance mesh and copy its arrays.
        final FdmHestonVarianceMesher mesher =
                new FdmHestonVarianceMesher(size, process, maturity, tAvgSteps, epsilon, mixingFactor);

        for ( int i = 0; i < size; ++i ) {
            dplus[i] = mesher.dplus(i);
            dminus[i] = mesher.dminus(i);
            locations[i] = mesher.location(i);
        }

        double vola = mesher.volaEstimate();

        if ( leverageFct != null ) {
            // boost::accumulators running arithmetic mean of all fed samples.
            final RunningMean acc = new RunningMean();

            final double s0 = process.s0().currentLink().value();

            acc.add(leverageFct.localVol(0.0, s0, true));

            final YieldTermStructure rTS = process.riskFreeRate().currentLink();
            final YieldTermStructure qTS = process.dividendYield().currentLink();

            final int sAvgSteps = 50;
            final GaussLobattoIntegral integrator = new GaussLobattoIntegral(10000, 1e-4);
            final InverseCumulativeNormal invNormal = new InverseCumulativeNormal();

            for ( int l = 1; l <= tAvgSteps; ++l ) {
                final double t = (maturity * l) / tAvgSteps;
                final double vol = vola * acc.mean();

                final double fwd = s0 * qTS.discount(t) / rTS.discount(t);

                final double[] u = new double[sAvgSteps];
                final double[] sig = new double[sAvgSteps];

                for ( int i = 0; i < sAvgSteps; ++i ) {
                    u[i] = epsilon + ((1.0 - 2.0 * epsilon) / (sAvgSteps - 1)) * i;
                    final double x = invNormal.op(u[i]);

                    final double gf = x * vol * Math.sqrt(t);
                    final double f = fwd * Math.exp(gf);

                    final double lv = leverageFct.localVol(t, f, true);
                    sig[i] = lv * lv;
                }

                // interpolated_volatility(u, sig): sqrt(linterp(u, true)).
                final Array uArr = new Array(sAvgSteps);
                final Array sigArr = new Array(sAvgSteps);
                for ( int i = 0; i < sAvgSteps; ++i ) {
                    uArr.set(i, u[i]);
                    sigArr.set(i, sig[i]);
                }
                final LinearInterpolation linterp = new LinearInterpolation(uArr, sigArr);
                linterp.enableExtrapolation();

                final double leverageAvg =
                        integrator.op(new Ops.DoubleOp() {
                            @Override
                            public double op(final double xVal) {
                                return Math.sqrt(Math.max(0.0, linterp.op(xVal, true)));
                            }
                        }, u[0], u[sAvgSteps - 1]) / (1.0 - 2.0 * epsilon);

                acc.add(leverageAvg);
            }
            vola *= acc.mean();
        }

        this.volaEstimate = vola;
    }

    /** Estimated average volatility (sqrt of variance), scaled by the averaged leverage. */
    public double volaEstimate() {
        return volaEstimate;
    }

    /** Running arithmetic mean — Java equivalent of boost accumulator_set&lt;stats&lt;tag::mean&gt;&gt;. */
    private static final class RunningMean {
        private double sum = 0.0;
        private long count = 0;

        void add(final double x) {
            sum += x;
            ++count;
        }

        double mean() {
            return sum / count;
        }
    }
}
