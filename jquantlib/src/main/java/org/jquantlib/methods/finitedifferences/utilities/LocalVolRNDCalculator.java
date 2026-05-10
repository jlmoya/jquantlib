/*
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
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
package org.jquantlib.methods.finitedifferences.utilities;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.integrals.DiscreteSimpsonIntegral;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.math.interpolations.NaturalCubicInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Predefined1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLocalVolFwdOp;
import org.jquantlib.methods.finitedifferences.schemes.DouglasScheme;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.LocalVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.TimeGrid;

/**
 * Local-volatility risk-neutral terminal density calculator via Fokker-Planck PDE.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/localvolrndcalculator.{hpp,cpp}}.
 *
 * <p>The terminal density is computed by forward-evolving a Gaussian initial
 * condition through the local-vol Fokker-Planck operator
 * ({@link FdmLocalVolFwdOp}) using a Douglas/Crank-Nicolson scheme on a
 * concentrating 1D log-spot mesh. The mesh is rebuilt whenever probability
 * mass leaks past the boundaries (controlled by {@code localVolProbEps}).
 *
 * <p>Differences from the C++ port:
 * <ul>
 *   <li>Java cannot multiply-inherit; we extend
 *       {@link RiskNeutralDensityCalculator} only and embed a
 *       lazy-evaluation flag manually rather than extending {@code LazyObject}.
 *       Callers that need observer-style invalidation should construct a
 *       fresh instance.</li>
 *   <li>Cubic interpolation uses {@link NaturalCubicInterpolation}
 *       (Java equivalent of C++ {@code CubicNaturalSpline}).</li>
 * </ul>
 *
 * @author Phase 5h.5-RND-b port
 */
public class LocalVolRNDCalculator extends RiskNeutralDensityCalculator {

    private final int xGrid;
    private final int tGrid;
    private final double x0Density;
    private final double localVolProbEps;
    private final int maxIter;
    private final double gaussianStepSize;
    private final Quote spot;
    private final LocalVolTermStructure localVol;
    private final YieldTermStructure rTS;
    private final YieldTermStructure qTS;
    private final TimeGrid timeGrid;
    private final List<Fdm1dMesher> xm;
    private final Matrix pm;
    private final List<Integer> rescaleTimeSteps;
    private final List<NaturalCubicInterpolation> pFct;

    private boolean calculated;

    /**
     * Sentinel for the "no override" {@code gaussianStepSize} flag — mirrors
     * C++ {@code -Null<Time>()}. Negative or zero values trigger the default
     * heuristic in {@code performCalculations()}.
     */
    public static final double NULL_TIME = Double.NaN;

    /**
     * Primary constructor — derives the time grid internally.
     *
     * @param spot              spot quote (S0 = spot.value())
     * @param rTS               domestic risk-free curve
     * @param qTS               dividend / foreign curve
     * @param localVol          local volatility surface
     * @param xGrid             log-spot grid size (default 101 in C++)
     * @param tGrid             time grid size (default 51 in C++)
     * @param x0Density         density at the central log-spot (default 0.1)
     * @param localVolProbEps   leakage tolerance for adaptive rescaling (default 1e-6)
     * @param maxIter           Lobatto-integration iteration cap (default 10000)
     * @param gaussianStepSize  override for the first Gaussian step (default {@link #NULL_TIME})
     */
    public LocalVolRNDCalculator(final Quote spot,
                                 final YieldTermStructure rTS,
                                 final YieldTermStructure qTS,
                                 final LocalVolTermStructure localVol,
                                 final int xGrid,
                                 final int tGrid,
                                 final double x0Density,
                                 final double localVolProbEps,
                                 final int maxIter,
                                 final double gaussianStepSize) {
        this.xGrid = xGrid;
        this.tGrid = tGrid;
        this.x0Density = x0Density;
        this.localVolProbEps = localVolProbEps;
        this.maxIter = maxIter;
        this.gaussianStepSize = gaussianStepSize;
        this.spot = spot;
        this.localVol = localVol;
        this.rTS = rTS;
        this.qTS = qTS;
        // C++: TimeGrid(localVol->maxTime(), tGrid) — uniform [0, T] in tGrid steps.
        this.timeGrid = new TimeGrid(localVol.maxTime(), tGrid);
        this.xm = new ArrayList<Fdm1dMesher>(tGrid);
        for (int i = 0; i < tGrid; ++i) {
            this.xm.add(null);
        }
        this.pm = new Matrix(tGrid, xGrid);
        this.rescaleTimeSteps = new ArrayList<Integer>();
        this.pFct = new ArrayList<NaturalCubicInterpolation>(tGrid);
        for (int i = 0; i < tGrid; ++i) {
            this.pFct.add(null);
        }
        this.calculated = false;
    }

    /** Convenience constructor matching the C++ default arguments. */
    public LocalVolRNDCalculator(final Quote spot,
                                 final YieldTermStructure rTS,
                                 final YieldTermStructure qTS,
                                 final LocalVolTermStructure localVol) {
        this(spot, rTS, qTS, localVol, 101, 51, 0.1, 1e-6, 10000, NULL_TIME);
    }

    /** Convenience constructor with custom grid sizes; defaults for the rest. */
    public LocalVolRNDCalculator(final Quote spot,
                                 final YieldTermStructure rTS,
                                 final YieldTermStructure qTS,
                                 final LocalVolTermStructure localVol,
                                 final int xGrid,
                                 final int tGrid) {
        this(spot, rTS, qTS, localVol, xGrid, tGrid, 0.1, 1e-6, 10000, NULL_TIME);
    }

    @Override
    public double pdf(final double x, final double t) {
        calculate();

        QL.require(t > 0, "positive time expected");
        QL.require(t <= timeGrid.back(), "given time exceeds local vol time grid");

        final double tMin = Math.min(timeGrid.at(1), 1.0 / 365);

        if (t <= tMin) {
            final double vol = localVol.localVol(0.0, spot.value());
            final double stdDev = vol * Math.sqrt(t);
            final double xm0 = -0.5 * stdDev * stdDev
                    + Math.log(spot.value() * qTS.discount(t) / rTS.discount(t));
            return new NormalDistribution(xm0, stdDev).op(x);
        } else if (t <= timeGrid.at(1)) {
            final double vol = localVol.localVol(0.0, spot.value());
            final double stdDev = vol * Math.sqrt(tMin);
            final double xm0 = -0.5 * stdDev * stdDev
                    + Math.log(spot.value() * qTS.discount(tMin) / rTS.discount(tMin));
            final NormalDistribution g = new NormalDistribution(xm0, stdDev);

            final double deltaT = timeGrid.at(1) - tMin;
            return g.op(x) * (timeGrid.at(1) - t) / deltaT
                    + probabilityInterpolation(0, x) * (t - tMin) / deltaT;
        } else {
            // lower_bound: smallest tg-index with tg.at(idx) >= t
            int lbIdx = lowerBound(t);
            // C++: idx = std::distance(begin, lb) - 1; then deltaT = *lb - *llb
            //      ret = pInt(idx-1, x) * (*lb - t)/deltaT + pInt(idx, x) * (t - *llb)/deltaT
            // Note: idx here is a *zero-based grid index* of *lb*; the calculator
            // pFct slot is at (gridIndex - 1). To preserve C++ semantics, idx-1
            // corresponds to pFct index (lbIdx - 2), and idx to pFct index (lbIdx - 1).
            final int idx = lbIdx - 1;
            final double lbT  = timeGrid.at(lbIdx);
            final double llbT = timeGrid.at(lbIdx - 1);
            final double deltaT = lbT - llbT;
            return probabilityInterpolation(idx - 1, x) * (lbT - t) / deltaT
                    + probabilityInterpolation(idx, x) * (t - llbT) / deltaT;
        }
    }

    @Override
    public double cdf(final double x, final double t) {
        calculate();

        // Locate the time grid neighbour for boundary mesh access.
        final double tc = timeGrid.closestTime(t);
        final int idx = (tc > t) ? timeGrid.index(tc) - 1
                : Math.min(xm.size() - 1, timeGrid.index(tc));

        double xl = xm.get(idx).locations()[0];
        double xr = xm.get(idx).locations()[xm.get(idx).size() - 1];

        if (x < xl) {
            return 0.0;
        } else if (x > xr) {
            return 1.0;
        }

        double addition = 0.1 * (xr - xl);

        // Right-tail integration when x is past the midpoint.
        if (x > 0.5 * (xr + xl)) {
            while (pdf(xr, t) > 0.01 * localVolProbEps) {
                addition *= 1.1;
                xr += addition;
            }
            final double upperX = xr;
            return 1.0 - new GaussLobattoIntegral(maxIter, 0.1 * localVolProbEps)
                    .op(new Ops.DoubleOp() {
                        @Override
                        public double op(final double y) { return pdf(y, t); }
                    }, x, upperX);
        } else {
            while (pdf(xl, t) > 0.01 * localVolProbEps) {
                addition *= 1.1;
                xl -= addition;
            }
            final double lowerX = xl;
            return new GaussLobattoIntegral(maxIter, 0.1 * localVolProbEps)
                    .op(new Ops.DoubleOp() {
                        @Override
                        public double op(final double y) { return pdf(y, t); }
                    }, lowerX, x);
        }
    }

    @Override
    public double invcdf(final double p, final double t) {
        calculate();

        final double closeGridTime = timeGrid.closestTime(t);

        if (closeGridTime == 0.0) {
            final double[] locs0 = xm.get(0).locations();
            final double stepSize = 0.02 * (locs0[locs0.length - 1] - locs0[0]);
            return new InvCDFHelper(this, Math.log(spot.value()),
                    0.1 * localVolProbEps, maxIter, stepSize).inverseCDF(p, t);
        } else {
            final int idx = timeGrid.index(closeGridTime) - 1;
            final double[] locs = xm.get(idx).locations();
            final int n = locs.length;
            final double stepSize = 0.005 * (locs[n - 1] - locs[0]);

            // xm = sum(x[j] * p[j]) (Simpson) — initial guess location.
            final Array x = new Array(n);
            final Array xp = new Array(n);
            for (int j = 0; j < n; ++j) {
                x.set(j, locs[j]);
                xp.set(j, locs[j] * pm.get(idx, j));
            }
            final double xmGuess = new DiscreteSimpsonIntegral().op(x, xp);
            return new InvCDFHelper(this, xmGuess,
                    0.1 * localVolProbEps, maxIter, stepSize).inverseCDF(p, t);
        }
    }

    /**
     * Returns the 1D mesher associated with time {@code t} (matched against
     * the internal time grid). For {@code idx == 0} returns a constant-spot
     * predefined mesher (mirrors C++ behaviour).
     */
    public Fdm1dMesher mesher(final double t) {
        calculate();

        final int idx = timeGrid.index(t);
        QL.require(idx <= xm.size(), "inconsistent time " + t + " given");

        if (idx > 0) {
            return xm.get(idx - 1);
        } else {
            final double[] flat = new double[xGrid];
            final double xLog = Math.log(spot.value());
            for (int i = 0; i < xGrid; ++i) {
                flat[i] = xLog;
            }
            return new Predefined1dMesher(flat);
        }
    }

    /** Internal time grid used for the Fokker-Planck evolution. */
    public TimeGrid timeGrid() {
        return timeGrid;
    }

    /** Indices of the time steps where the mesh was rescaled (debugging hook). */
    public List<Integer> rescaleTimeSteps() {
        calculate();
        return new ArrayList<Integer>(rescaleTimeSteps);
    }

    /** Force re-evaluation on next access. */
    public void invalidate() { this.calculated = false; }

    private void calculate() {
        if (!calculated) {
            performCalculations();
            calculated = true;
        }
    }

    private void performCalculations() {
        rescaleTimeSteps.clear();

        final double sT = timeGrid.at(1);
        double t = Math.min(sT,
                (!Double.isNaN(gaussianStepSize) && gaussianStepSize > 0.0)
                        ? gaussianStepSize : 0.5 * sT);

        final double vol = localVol.localVol(0.0, spot.value());
        final double stdDev = vol * Math.sqrt(t);
        double xm0 = -0.5 * stdDev * stdDev
                + Math.log(spot.value() * qTS.discount(t) / rTS.discount(t));

        final double stdDevOfFirstStep = vol * Math.sqrt(sT);
        final double normInvEps = new InverseCumulativeNormal().op(1.0 - localVolProbEps);

        double sLowerBound = xm0 - normInvEps * stdDevOfFirstStep;
        double sUpperBound = xm0 + normInvEps * stdDevOfFirstStep;

        Concentrating1dMesher mesher1 = new Concentrating1dMesher(
                sLowerBound, sUpperBound, xGrid, xm0, x0Density, true);
        Fdm1dMesher mesher = mesher1;

        Array p = new Array(mesher.size());
        Array x = new Array(mesher.size());
        for (int i = 0; i < mesher.size(); ++i) {
            x.set(i, mesher.locations()[i]);
        }
        final NormalDistribution gaussianPDF = new NormalDistribution(xm0, vol * Math.sqrt(t));
        for (int i = 0; i < p.size(); ++i) {
            p.set(i, gaussianPDF.op(x.get(i)));
        }
        p = rescalePDF(x, p);

        QL.require(x.size() > 10, "x grid is too small. Minimum size is greater than 10");

        final int b = Math.max(1, (int) (x.size() * 0.04));

        DouglasScheme evolver = new DouglasScheme(0.5,
                new FdmLocalVolFwdOp(new FdmMesherComposite(mesher), spot, rTS, qTS, localVol));

        for (int i = 1; i <= tGrid; ++i) {
            final double dt = timeGrid.at(i) - t;

            // Probability-leak detection on the boundary cells.
            double maxLeftValue = 0.0;
            double maxRightValue = 0.0;
            for (int k = 0; k < b; ++k) {
                final double vL = Math.abs(p.get(k));
                final double vR = Math.abs(p.get(p.size() - 1 - k));
                if (vL > maxLeftValue)  maxLeftValue  = vL;
                if (vR > maxRightValue) maxRightValue = vR;
            }

            if (Math.max(maxLeftValue, maxRightValue) > localVolProbEps) {
                rescaleTimeSteps.add(Integer.valueOf(i));

                final double oldLowerBound = sLowerBound;
                final double oldUpperBound = sUpperBound;

                // xm = Simpson( x * p ) over current mesh
                final Array xp = new Array(x.size());
                for (int j = 0; j < x.size(); ++j) {
                    xp.set(j, x.get(j) * p.get(j));
                }
                xm0 = new DiscreteSimpsonIntegral().op(x, xp);

                // Mean local-vol on current support
                final Array vols = new Array(x.size());
                for (int j = 0; j < vols.size(); ++j) {
                    vols.set(j, localVol.localVol(t + dt, Math.exp(x.get(j))));
                }
                final double vm = new DiscreteSimpsonIntegral().op(x, vols)
                        / (x.get(x.size() - 1) - x.get(0));

                final double scalingFactor = vm * Math.sqrt(0.5 * timeGrid.back());

                if (maxLeftValue > localVolProbEps) {
                    sLowerBound -= scalingFactor * (oldUpperBound - oldLowerBound);
                }
                if (maxRightValue > localVolProbEps) {
                    sUpperBound += scalingFactor * (oldUpperBound - oldLowerBound);
                }

                mesher = new Concentrating1dMesher(sLowerBound, sUpperBound, xGrid,
                        xm0, 0.1, false);

                // Cubic-spline re-interpolation of the existing density onto the new mesh.
                final NaturalCubicInterpolation pSpline = new NaturalCubicInterpolation(x, p);
                pSpline.update();

                final Array xn = new Array(mesher.size());
                for (int j = 0; j < xn.size(); ++j) {
                    xn.set(j, mesher.locations()[j]);
                }
                final Array pn = new Array(xn.size()).fill(0.0);
                for (int j = 0; j < xn.size(); ++j) {
                    if (xn.get(j) >= oldLowerBound && xn.get(j) <= oldUpperBound) {
                        pn.set(j, pSpline.op(xn.get(j)));
                    }
                }

                x = xn;
                p = rescalePDF(xn, pn);

                evolver = new DouglasScheme(0.5,
                        new FdmLocalVolFwdOp(new FdmMesherComposite(mesher),
                                spot, rTS, qTS, localVol));
            }

            evolver.setStep(dt);
            t += dt;

            if (dt > org.jquantlib.math.Constants.QL_EPSILON) {
                evolver.step(p, t);
                p = rescalePDF(x, p);
            }

            xm.set(i - 1, mesher);
            for (int j = 0; j < p.size(); ++j) {
                pm.set(i - 1, j, p.get(j));
            }
            // Build cubic-natural-spline interpolant on this row of pm.
            final Array xRow = new Array(p.size());
            final Array pRow = new Array(p.size());
            for (int j = 0; j < p.size(); ++j) {
                xRow.set(j, mesher.locations()[j]);
                pRow.set(j, p.get(j));
            }
            final NaturalCubicInterpolation interp = new NaturalCubicInterpolation(xRow, pRow);
            interp.update();
            pFct.set(i - 1, interp);
        }
    }

    private double probabilityInterpolation(final int idx, final double x) {
        // Bounds-clip: outside the supporting mesh, return 0.
        final double[] locs = xm.get(idx).locations();
        if (x < locs[0] || x > locs[locs.length - 1]) {
            return 0.0;
        }
        return pFct.get(idx).op(x);
    }

    private static Array rescalePDF(final Array x, final Array p) {
        final double mass = new DiscreteSimpsonIntegral().op(x, p);
        if (mass == 0.0) {
            return p;
        }
        final Array out = new Array(p.size());
        for (int i = 0; i < p.size(); ++i) {
            out.set(i, p.get(i) / mass);
        }
        return out;
    }

    /**
     * Walk the time grid and return the smallest index {@code k} with
     * {@code timeGrid.at(k) >= t}. Mirrors C++ {@code std::lower_bound}.
     * Caller guarantees {@code t > timeGrid.at(0)}.
     */
    private int lowerBound(final double t) {
        // Linear scan — TimeGrid is short (<= 100 typical).
        for (int k = 0; k < timeGrid.size(); ++k) {
            if (timeGrid.at(k) >= t) {
                return k;
            }
        }
        return timeGrid.size() - 1;
    }
}
