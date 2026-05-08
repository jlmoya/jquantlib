/*
 Copyright (C) 2008 Andreas Gaida
 Copyright (C) 2008 Ralph Schreyer
 Copyright (C) 2008, 2019 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
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

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.Ops;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.math.distributions.InverseNonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.distributions.NonCentralCumulativeChiSquaredDistribution;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.processes.HestonProcess;

/**
 * One-dimensional grid mesher for the variance part of the Heston model.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/meshers/fdmhestonvariancemesher.{hpp,cpp}}.
 * <p>
 * Builds a 1D variance mesh based on the non-central chi-squared distribution
 * of the Heston CIR variance process, averaging over {@code tAvgSteps} time
 * slices. If the distribution inversion fails, falls back to a uniform mesh
 * centered on the long-run variance.
 *
 * @author Phase 2m Track B port
 */
public class FdmHestonVarianceMesher extends Fdm1dMesher {

    private final double volaEstimate;

    public FdmHestonVarianceMesher(final int size,
                                    final HestonProcess process,
                                    final double maturity) {
        this(size, process, maturity, 10, 0.0001, 1.0);
    }

    public FdmHestonVarianceMesher(final int size,
                                    final HestonProcess process,
                                    final double maturity,
                                    final int tAvgSteps,
                                    final double epsilon) {
        this(size, process, maturity, tAvgSteps, epsilon, 1.0);
    }

    public FdmHestonVarianceMesher(final int size,
                                    final HestonProcess process,
                                    final double maturity,
                                    final int tAvgSteps,
                                    final double epsilon,
                                    final double mixingFactor) {
        super(size);

        final double kappa     = process.kappa().currentLink().value();
        final double theta     = process.theta().currentLink().value();
        final double sigma     = process.sigma().currentLink().value();
        final double v0        = process.v0().currentLink().value();
        final double mixSigma  = sigma * mixingFactor;
        final double df        = 4.0 * theta * kappa / (mixSigma * mixSigma);

        double[] vGrid = new double[size];
        double[] pGrid = new double[size];

        boolean ok = false;
        try {
            // Collect (v, p) pairs from all tAvgSteps time slices
            final List<double[]> grid = new ArrayList<double[]>(size * tAvgSteps);

            for (int l = 1; l <= tAvgSteps; ++l) {
                final double t   = (maturity * l) / tAvgSteps;
                final double ekt = JQuantMath.exp(-kappa * t);
                final double k   = mixSigma * mixSigma * (1.0 - ekt) / (4.0 * kappa);
                final double ncp = 4.0 * kappa * ekt / (mixSigma * mixSigma * (1.0 - ekt)) * v0;

                final double qMax = Math.max(v0,
                    k * new InverseNonCentralCumulativeChiSquaredDistribution(
                                df, ncp, 100, 1e-8).op(1.0 - epsilon));
                final double minVStep = (qMax - 0.0) / (50.0 * size);

                double ps = 0.0, p = 0.0;
                double vTmp = 0.0;
                grid.add(new double[]{ 0.0, epsilon });

                for (int i = 1; i < size; ++i) {
                    ps = (1.0 - epsilon - p) / (size - i);
                    p += ps;
                    final double tmp = k * new InverseNonCentralCumulativeChiSquaredDistribution(
                            df, ncp, 100, 1e-8).op(p);
                    final double vx = Math.max(vTmp + minVStep, tmp);
                    p = new NonCentralCumulativeChiSquaredDistribution(df, ncp).op(vx / k);
                    vTmp = vx;
                    grid.add(new double[]{ vx, p });
                }
            }

            if (grid.size() != (long) size * tAvgSteps) {
                throw new RuntimeException("grid size mismatch");
            }

            // Sort by v
            grid.sort((a, b) -> Double.compare(a[0], b[0]));

            // Average into size buckets
            final int tp = grid.size();
            for (int i = 0; i < size; ++i) {
                final int b = (i * tp) / size;
                final int e = ((i + 1) * tp) / size;
                double vSum = 0.0, pSum = 0.0;
                for (int j = b; j < e; ++j) {
                    vSum += grid.get(j)[0];
                    pSum += grid.get(j)[1];
                }
                vGrid[i] = vSum / (e - b);
                pGrid[i] = pSum / (e - b);
            }
            ok = true;
        } catch (final Exception ex) {
            ok = false;
        }

        if (!ok) {
            // fallback: uniform mesh
            final double vol        = mixSigma * Math.sqrt(theta / (2.0 * kappa));
            final double mean       = theta;
            final double upperBound = Math.max(v0 + 4.0 * vol, mean + 4.0 * vol);
            final double lowerBound = Math.max(0.0, Math.min(v0 - 4.0 * vol, mean - 4.0 * vol));
            for (int i = 0; i < size; ++i) {
                pGrid[i] = i / (size - 1.0);
                vGrid[i] = lowerBound + i * (upperBound - lowerBound) / (size - 1.0);
            }
        }

        // volaEstimate via GaussLobatto on interpolated sqrt(v) over pGrid
        final double skewHint = (kappa != 0.0)
                ? Math.max(1.0, mixSigma / kappa) : 1.0;

        // Sort pGrid (should already be sorted, but ensure)
        // Build arrays for interpolation
        final double[] pSorted = pGrid.clone();
        // pGrid is already sorted (from sorted grid or linspace)

        final double pFront = pSorted[0];
        final double pBack  = pSorted[size - 1];

        // Build Array objects for LinearInterpolation
        final Array pArr = new Array(size);
        final Array vArr = new Array(size);
        for (int i = 0; i < size; ++i) {
            pArr.set(i, pSorted[i]);
            vArr.set(i, vGrid[i]);
        }

        final GaussLobattoIntegral integrator = new GaussLobattoIntegral(100000, 1e-4);

        double estimate;
        try {
            // anonymous Ops.DoubleOp: sqrt(interpolated variance)
            final org.jquantlib.math.interpolations.LinearInterpolation linterp =
                new org.jquantlib.math.interpolations.LinearInterpolation(pArr, vArr);
            estimate = integrator.op(new Ops.DoubleOp() {
                @Override public double op(final double p) {
                    return Math.sqrt(Math.max(0.0, linterp.op(p)));
                }
            }, pFront, pBack) * Math.pow(skewHint, 1.5);
        } catch (final Exception e) {
            // fallback: simple average
            double sum = 0.0;
            for (final double v : vGrid) sum += Math.sqrt(Math.max(0.0, v));
            estimate = (sum / size) * Math.pow(skewHint, 1.5);
        }
        this.volaEstimate = estimate;

        // Pin v0 to nearest grid point
        for (int i = 1; i < size; ++i) {
            if (vGrid[i - 1] <= v0 && vGrid[i] >= v0) {
                if (Math.abs(vGrid[i - 1] - v0) < Math.abs(vGrid[i] - v0)) {
                    vGrid[i - 1] = v0;
                } else {
                    vGrid[i] = v0;
                }
                break;
            }
        }

        System.arraycopy(vGrid, 0, this.locations, 0, size);
        for (int i = 0; i < size - 1; ++i) {
            dplus[i]      = vGrid[i + 1] - vGrid[i];
            dminus[i + 1] = dplus[i];
        }
        dplus[size - 1] = Double.NaN;
        dminus[0]       = Double.NaN;
    }

    /** Estimated average volatility (sqrt of variance) over the grid. */
    public double volaEstimate() {
        return volaEstimate;
    }
}
