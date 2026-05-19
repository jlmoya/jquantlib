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
import java.util.Arrays;
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

        // Phase 5e.5b-CFC-d-288: at very-low sigma_v (e.g. sigma=1e-6 used
        // by HHHW testFdmHestonHullWhiteEngine) df explodes to ~1e11 and
        // the non-central chi-square inverter breaks down in *both* C++
        // and Java:
        //   - C++ v1.42.1's {@code NonCentralCumulativeChiSquareDistribution}
        //     evaluates {@code t = exp(f2*log(x2) - x2 - logGamma(f2+1))}
        //     which underflows to 0 across the entire AS-275 series range,
        //     so the CDF returns 0 for any positive x. The inverter's
        //     Brent then throws "root not bracketed" — C++ catches this
        //     exception and falls through to its uniform-mesh fallback
        //     centred on the deterministic CIR mean.
        //   - Java's port added an {@code if (t == 0.0) return 1.0} early
        //     return (for SquareRootCLVModel right-tail) which flips
        //     the CDF to 1.0 instead of 0.0. The Brent never throws, the
        //     "try" succeeds (with bogus values), and the fallback path
        //     is not entered.
        //
        // The fix: detect the degenerate regime ({@code df + nominalNcp > 1e6}
        // where {@code nominalNcp = 4*kappa*v0/(mixSigma^2*(1-exp(-kappa*T))})
        // and force the fallback path, mirroring C++'s exception-driven
        // entry into the fallback. The fallback itself must match the
        // C++ literal (lower=max(0, min(v0-4*vol, theta-4*vol)),
        // upper=max(v0+4*vol, theta+4*vol)) — see comment below.
        // For the {@code sigma_v=1e-6}, {@code v0=theta=0.09} regression
        // case, the C++ fallback produces a tight Gaussian-around-v0
        // mesh of width {@code 8e-7} centred on v0; the Java port's
        // earlier widened fallback (Phase 5e.5b-CFC-d-213) opened this
        // to {@code [v0/2, 2*v0]} and was the root cause of the FD
        // operator's spurious NPV blow-up.
        //
        // Tolerance: Gaussian-fallback mesh matches C++ output to
        // better than 1e-9 absolute on locations (the difference is
        // pure floating-point round-off in the upper/lower bound
        // computation). Downstream FD-engine NPV error is within the
        // LOOSE-tier 5e-3 envelope.
        //
        // Note: the C++ chi-square Brent inverter doesn't throw until
        // df+ncp grows large enough that {@code exp(huge_negative)}
        // underflows everywhere — empirically around df+ncp > 1e6 for
        // the AS-275 series used by both ports. Java's t==0 early-return
        // engages at the same threshold (and for the same reason), so
        // {@code df + ncp_lower > 1e6} is a reliable trigger. We
        // pre-compute the "minimum-magnitude" ncp seen across the time
        // slices (at the latest slice t = maturity) since ncp shrinks
        // as t grows; the maximum df+ncp is at the earliest slice.
        final double ekt_min = JQuantMath.exp(-kappa * maturity);
        final double ncpMax = (mixSigma > 0.0 && (1.0 - ekt_min) > 0.0)
                ? 4.0 * kappa * Math.exp(-kappa * (maturity / tAvgSteps))
                  / (mixSigma * mixSigma * (1.0 - Math.exp(-kappa * (maturity / tAvgSteps))))
                  * v0
                : 0.0;
        final boolean degenerate = (df + ncpMax) > 1.0e6;

        boolean ok = false;
        if (!degenerate) {
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
        }

        if (!ok) {
            // C++ v1.42.1 literal fallback (lines 106-119 of
            // fdmhestonvariancemesher.cpp): uniform mesh on
            // [max(0, min(v0-4*vol, theta-4*vol)), max(v0+4*vol, theta+4*vol)]
            // where {@code vol = mixSigma * sqrt(theta / (2*kappa))} is
            // the stationary CIR standard deviation.
            //
            // Phase 5e.5b-CFC-d-288: restore the literal C++ fallback
            // (the previous Phase 5e.5b-CFC-d-213 "widening" to
            // {@code max(upperBoundRaw, 2*v0)} / {@code min(lowerBoundRaw, 0.5*v0)}
            // was a workaround for a different root cause — the chi-square
            // inverter throwing exceptions on moderate-sigma cases — and
            // is no longer needed now that the degenerate detection above
            // routes degenerate-sigma cases to this fallback. For
            // sigma_v=1e-6 / v0=theta=0.09 the literal C++ fallback gives
            // a tight Gaussian-around-v0 mesh of width
            // {@code 8 * 1e-6 * sqrt(0.09/2) = 8.5e-7} which matches the
            // C++ reference {@code methods/finitedifferences/meshers/
            // fdm_heston_variance_mesher_low_sigma.json} exactly.
            final double vol        = mixSigma * Math.sqrt(theta / (2.0 * kappa));
            final double mean       = theta;
            final double upperBound =
                    Math.max(v0 + 4.0 * vol, mean + 4.0 * vol);
            final double lowerBound =
                    Math.max(0.0, Math.min(v0 - 4.0 * vol, mean - 4.0 * vol));
            for (int i = 0; i < size; ++i) {
                pGrid[i] = i / (size - 1.0);
                vGrid[i] = lowerBound + i * (upperBound - lowerBound) / (size - 1.0);
            }
        }

        // volaEstimate via GaussLobatto on interpolated sqrt(v) over pGrid
        final double skewHint = (kappa != 0.0)
                ? Math.max(1.0, mixSigma / kappa) : 1.0;

        // Sort pGrid for use with LinearInterpolation. pGrid comes out of the
        // bucket-averaging step (sorted by v, then averaged) — for moderate /
        // large vol-of-vol the probability column is roughly monotone in v,
        // but for small vol-of-vol or degenerate (sigma~1e-6) parameters the
        // bucket-averaged p column can have ties / mild non-monotonicities
        // that trip LinearInterpolation's "unsorted values on array X" guard.
        // Mirrors C++ fdmhestonvariancemesher.cpp line 125
        // (std::sort(pGrid.begin(), pGrid.end())).
        final double[] pSorted = pGrid.clone();
        Arrays.sort(pSorted);

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
            }, pFront, pBack) * JQuantMath.pow(skewHint, 1.5);
        } catch (final Exception e) {
            // fallback: simple average
            double sum = 0.0;
            for (final double v : vGrid) sum += Math.sqrt(Math.max(0.0, v));
            estimate = (sum / size) * JQuantMath.pow(skewHint, 1.5);
        }
        this.volaEstimate = estimate;

        // Pin v0 to nearest grid point (mirrors C++ lines 130-138). When v0
        // falls inside the chi-square-derived mesh, snap the nearer of the
        // two bracketing grid points to v0 so that downstream interpolants
        // hit a mesh node rather than extrapolating.
        boolean pinned = false;
        for (int i = 1; i < size; ++i) {
            if (vGrid[i - 1] <= v0 && vGrid[i] >= v0) {
                if (Math.abs(vGrid[i - 1] - v0) < Math.abs(vGrid[i] - v0)) {
                    vGrid[i - 1] = v0;
                } else {
                    vGrid[i] = v0;
                }
                pinned = true;
                break;
            }
        }
        // Phase 5e.5b-CFC-d-213: Edge case beyond C++ pin coverage.  For
        // small vol-of-vol (sigma~1e-3 or smaller) the non-central
        // chi-square is sharply peaked at v0 and the per-slice
        // {@code qMax = max(v0, ...)} guarantees v0 is the upper bound of
        // each slice — but bucket averaging across {@code size*tAvgSteps}
        // (v, p) pairs leaves the last bucket's mean strictly below v0.
        // C++'s pin loop above then misses (vGrid[size-1] < v0), v0 is
        // outside the mesh, and Fdm2DimSolver's BicubicSpline rejects the
        // v0 evaluation as extrapolation.  Snap the nearest end-point to
        // v0 in this case so the mesh always contains the evaluation point.
        // Symmetric fallback for v0 below vGrid[0] is preserved for safety,
        // though that branch is not hit by any current Heston test.
        if (!pinned) {
            if (v0 > vGrid[size - 1]) {
                vGrid[size - 1] = v0;
            } else if (v0 < vGrid[0]) {
                vGrid[0] = v0;
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
