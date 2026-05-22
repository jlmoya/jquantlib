/*
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2014 Johannes Göttker-Schnetmann
 Copyright (C) 2014 Klaus Spanderen
 Copyright (C) 2015 Peter Caspers

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

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.ode.AdaptiveRungeKutta;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.math.transcendental.JQuantMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One-dimensional grid mesher concentrating around one or more critical points.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/meshers/concentrating1dmesher.{hpp,cpp}}.
 *
 * <p>Two construction modes are supported:
 * <ul>
 *   <li><b>Single-critical-point (asinh transform).</b> Mirrors the C++
 *       {@code std::pair<Real, Real>} constructor. The grid is generated via
 *       an inverse-sinh transform that concentrates cells around {@code cPoint}
 *       with density {@code density}. When {@code requireCPoint} is
 *       {@code true} a piecewise-linear remap ensures the critical point is a
 *       grid node.</li>
 *   <li><b>Multi-critical-point (adaptive ODE).</b> Mirrors the C++
 *       {@code std::vector<std::tuple<Real, Real, bool>>} constructor. Solves
 *       {@code dy/dx = a/sqrt(sum_i 1/(beta_i + (y - p_i)^2))} via adaptive
 *       Runge-Kutta with the scaling factor {@code a} determined by a Brent
 *       solve so that {@code y(1) = end}. Required critical points are pinned
 *       to grid nodes via a piecewise-linear transform on the parameter axis.
 *       See {@link CPointSpec} for the per-point tuple type.</li>
 * </ul>
 *
 * @author Phase 2m Track C port (single-cPoint)
 * @author Phase 5e.5b-CFC-d-216 (multi-cPoint)
 */
public class Concentrating1dMesher extends Fdm1dMesher {

    /**
     * Sentinel value for "no critical point" — mirrors C++ {@code Null<Real>()}. {@link Double#NaN} is used because it
     * propagates comparisons cleanly.
     */
    public static final double NULL_REAL = Double.NaN;

    /**
     * Build a concentrating 1D mesh from {@code start} to {@code end} with {@code size} nodes around a single critical
     * point.
     *
     * @param start         left boundary
     * @param end           right boundary
     * @param size          number of grid nodes
     * @param cPoint        critical point to concentrate around (or {@link #NULL_REAL} for uniform)
     * @param density       concentration density at {@code cPoint}; the actual bandwidth used is
     *                      {@code density * (end - start)}. Ignored (pass {@code 0}) when {@code cPoint} is
     *                      {@link #NULL_REAL}.
     * @param requireCPoint if {@code true}, ensure {@code cPoint} is an exact grid node via piecewise-linear remap
     */
    public Concentrating1dMesher(final double start, final double end, final int size, final double cPoint,
            final double density, final boolean requireCPoint) {
        super(size);

        QL.require(end > start, "end must be larger than start");

        final boolean hasCPoint = !Double.isNaN(cPoint);
        final double scaledDensity = Double.isNaN(density) ? Double.NaN : density * (end - start);

        QL.require(!hasCPoint || (cPoint >= start && cPoint <= end), "cPoint must be between start and end");
        QL.require(!hasCPoint || (!Double.isNaN(scaledDensity) && scaledDensity > 0.0),
                "density must be positive when cPoint is given");
        QL.require(!requireCPoint || hasCPoint, "cPoint is required in grid but not given");

        final double dx = 1.0 / (size - 1);

        if ( hasCPoint ) {
            final double c1 = asinh((start - cPoint) / scaledDensity);
            final double c2 = asinh((end - cPoint) / scaledDensity);

            // piecewise-linear remap u -> z when requireCPoint is set
            double[] u = null, z = null;
            if ( requireCPoint ) {
                // build 2-3 knot piecewise linear transform
                final boolean atStart = Math.abs(cPoint - start) < 1e-15;
                final boolean atEnd = Math.abs(cPoint - end) < 1e-15;
                if ( !atStart && !atEnd ) {
                    final double z0 = -c1 / (c2 - c1);
                    final long i0 = Math.max(1L, Math.min(Math.round(z0 * (size - 1)), size - 2));
                    final double u0 = i0 / (double) (size - 1);
                    u = new double[] { 0.0, u0, 1.0 };
                    z = new double[] { 0.0, z0, 1.0 };
                } else {
                    u = new double[] { 0.0, 1.0 };
                    z = new double[] { 0.0, 1.0 };
                }
            }

            for ( int i = 1; i < size - 1; ++i ) {
                final double li;
                if ( requireCPoint && u != null ) {
                    li = linearInterp(u, z, i * dx);
                } else {
                    li = i * dx;
                }
                locations[i] = cPoint + scaledDensity * Math.sinh(c1 * (1.0 - li) + c2 * li);
            }

        } else {
            // uniform mesh
            for ( int i = 1; i < size - 1; ++i ) {
                locations[i] = start + i * dx * (end - start);
            }
        }

        locations[0] = start;
        locations[size - 1] = end;

        for ( int i = 0; i < size - 1; ++i ) {
            dplus[i] = locations[i + 1] - locations[i];
            dminus[i + 1] = dplus[i];
        }
        dplus[size - 1] = Double.NaN;
        dminus[0] = Double.NaN;
    }

    /**
     * Convenience: uniform mesh (no critical point).
     */
    public Concentrating1dMesher(final double start, final double end, final int size) {
        this(start, end, size, NULL_REAL, NULL_REAL, false);
    }

    /**
     * Convenience: critical point without requireCPoint flag.
     */
    public Concentrating1dMesher(final double start, final double end, final int size, final double cPoint,
            final double density) {
        this(start, end, size, cPoint, density, false);
    }

    /**
     * Convenience: multi-cPoint constructor with default tolerance {@code 1e-8}.
     */
    public Concentrating1dMesher(final double start, final double end, final int size,
            final List< CPointSpec > cPoints) {
        this(start, end, size, cPoints, 1.0e-8);
    }

    /**
     * Build a concentrating 1D mesh from {@code start} to {@code end} with {@code size} nodes concentrating around
     * multiple critical points.
     * <p>
     * Mirrors C++ v1.42.1
     * {@code Concentrating1dMesher(Real, Real, Size, const std::vector<std::tuple<Real,Real,bool>>&, Real)}.
     *
     * @param start   left boundary
     * @param end     right boundary
     * @param size    number of grid nodes
     * @param cPoints list of {@link CPointSpec} tuples (location, density, requireCPoint). Density is multiplied by
     *                {@code end-start} internally (matches C++).
     * @param tol     tolerance for the ODE integrator and Brent solvers
     */
    public Concentrating1dMesher(final double start, final double end, final int size, final List< CPointSpec > cPoints,
            final double tol) {
        super(size);

        QL.require(end > start, "end must be larger than start");
        QL.require(cPoints != null && !cPoints.isEmpty(), "cPoints must be non-empty");

        final int n = cPoints.size();
        final double[] points = new double[n];
        final double[] betas = new double[n];
        for ( int i = 0; i < n; ++i ) {
            final CPointSpec sp = cPoints.get(i);
            points[i] = sp.location;
            final double d = sp.density * (end - start);
            betas[i] = d * d;
        }

        // get scaling factor a so that y(1) = end
        double aInit = 0.0;
        for ( int i = 0; i < n; ++i ) {
            final double c1 = asinh((start - points[i]) / betas[i]);
            final double c2 = asinh((end - points[i]) / betas[i]);
            aInit += (c2 - c1) / n;
        }

        final OdeIntegrationFct fct = new OdeIntegrationFct(points, betas, tol);
        final Brent brent = new Brent();
        final double startCapture = start;
        final double endCapture = end;
        final Ops.DoubleOp aResidual = new Ops.DoubleOp() {
            @Override
            public double op(final double x) {
                return fct.solve(x, startCapture, 0.0, 1.0) - endCapture;
            }
        };
        final double a = brent.solve(aResidual, tol, aInit, 0.1 * aInit);

        // solve ODE for all grid points
        final double[] x = new double[size];
        final double[] y = new double[size];
        x[0] = 0.0;
        y[0] = start;
        final double dx = 1.0 / (size - 1);
        for ( int i = 1; i < size; ++i ) {
            x[i] = i * dx;
            y[i] = fct.solve(a, y[i - 1], x[i - 1], x[i]);
        }

        // eliminate numerical noise and ensure y(1) = end
        final double dy = y[size - 1] - end;
        for ( int i = 1; i < size; ++i ) {
            y[i] -= i * dx * dy;
        }

        final LinearInterpolation odeSolution = new LinearInterpolation(new Array(x), new Array(y));
        odeSolution.enableExtrapolation();

        // ensure required points are part of the grid
        final List< double[] > w = new ArrayList<>();
        w.add(new double[] { 0.0, 0.0 });

        for ( int i = 0; i < n; ++i ) {
            final CPointSpec sp = cPoints.get(i);
            if ( sp.requireCPoint && points[i] > start && points[i] < end ) {

                // std::lower_bound on y (sorted ascending) for points[i]
                int j = lowerBound(y, points[i]);

                final double pi = points[i];
                final Ops.DoubleOp residual = new Ops.DoubleOp() {
                    @Override
                    public double op(final double xx) {
                        return odeSolution.op(xx, true) - pi;
                    }
                };
                final double e = brent.solve(residual, Constants.QL_EPSILON, x[j], 0.5 / size);

                w.add(new double[] { Math.min(x[size - 2], x[j]), e });
            }
        }
        w.add(new double[] { 1.0, 1.0 });

        // sort by first coordinate; dedupe by close_enough on first coordinate (n=1000)
        w.sort(new Comparator< double[] >() {
            @Override
            public int compare(final double[] a, final double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });
        final List< double[] > wUnique = new ArrayList<>();
        wUnique.add(w.get(0));
        for ( int i = 1; i < w.size(); ++i ) {
            // C++ std::unique with binary predicate close_enough(p1.first, p2.first, 1000)
            // removes the SECOND element of any adjacent pair where the predicate
            // returns true — i.e. keeps the first occurrence in a run.
            if ( !Closeness.isCloseEnough(wUnique.get(wUnique.size() - 1)[0], w.get(i)[0], 1000) ) {
                wUnique.add(w.get(i));
            }
        }

        final int wn = wUnique.size();
        final double[] uArr = new double[wn];
        final double[] zArr = new double[wn];
        for ( int i = 0; i < wn; ++i ) {
            uArr[i] = wUnique.get(i)[0];
            zArr[i] = wUnique.get(i)[1];
        }
        final LinearInterpolation transform = new LinearInterpolation(new Array(uArr), new Array(zArr));

        for ( int i = 0; i < size; ++i ) {
            locations[i] = odeSolution.op(transform.op(i * dx));
        }

        for ( int i = 0; i < size - 1; ++i ) {
            dplus[i] = dminus[i + 1] = locations[i + 1] - locations[i];
        }
        dplus[size - 1] = Double.NaN;
        dminus[0] = Double.NaN;
    }

    /** Inverse hyperbolic sine using JQuantMath.log. */
    private static double asinh(final double x) {
        return JQuantMath.log(x + Math.sqrt(x * x + 1.0));
    }

    // --- helpers ---

    /** Linear interpolation on a sorted node array. */
    private static double linearInterp(final double[] u, final double[] z, final double x) {
        // locate interval
        int j = u.length - 2;
        for ( int i = 0; i < u.length - 1; ++i ) {
            if ( x <= u[i + 1] ) {
                j = i;
                break;
            }
        }
        final double t = (x - u[j]) / (u[j + 1] - u[j]);
        return z[j] + t * (z[j + 1] - z[j]);
    }

    /**
     * Equivalent of C++ {@code std::lower_bound(v.begin(), v.end(), target)}: the first index {@code i} for which
     * {@code v[i] >= target}, or {@code v.length} if no such index exists.
     */
    private static int lowerBound(final double[] v, final double target) {
        int lo = 0;
        int hi = v.length;
        while ( lo < hi ) {
            final int mid = (lo + hi) >>> 1;
            if ( v[mid] < target ) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * Per-critical-point specification for the multi-cPoint constructor.
     * <p>Mirrors C++ {@code std::tuple<Real, Real, bool>}: location, density,
     * and a flag requesting that the location is pinned to an exact grid node.
     */
    public static final class CPointSpec {
        public final double location;
        public final double density;
        public final boolean requireCPoint;

        public CPointSpec(final double location, final double density, final boolean requireCPoint) {
            this.location = location;
            this.density = density;
            this.requireCPoint = requireCPoint;
        }
    }

    /**
     * ODE right-hand side {@code dy/dx = a / sqrt(sum_i 1/(beta_i + (y - p_i)^2))}. Wraps an {@link AdaptiveRungeKutta}
     * integrator. Mirrors the C++ anonymous {@code OdeIntegrationFct} class.
     */
    private static final class OdeIntegrationFct {
        private final AdaptiveRungeKutta rk;
        private final double[] points;
        private final double[] betas;

        OdeIntegrationFct(final double[] points, final double[] betas, final double tol) {
            // C++ AdaptiveRungeKutta<>::AdaptiveRungeKutta(Real eps = 1e-6,
            //     Real relInitStep = 1e-4, Real hmin = 0). The
            //     OdeIntegrationFct ctor passes (tol) — eps = tol, default rest.
            this.rk = new AdaptiveRungeKutta(tol, 1.0e-4, 0.0);
            this.points = points;
            this.betas = betas;
        }

        /** Integrate from {@code (x0, y0)} to {@code x1} with scaling factor {@code a}. */
        double solve(final double a, final double y0, final double x0, final double x1) {
            final AdaptiveRungeKutta.OdeFct1d odeFct = new AdaptiveRungeKutta.OdeFct1d() {
                @Override
                public double apply(final double t, final double y) {
                    return jac(a, y);
                }
            };
            return rk.solve(odeFct, y0, x0, x1);
        }

        private double jac(final double a, final double y) {
            double s = 0.0;
            for ( int i = 0; i < points.length; ++i ) {
                final double dyi = y - points[i];
                s += 1.0 / (betas[i] + dyi * dyi);
            }
            return a / Math.sqrt(s);
        }
    }
}
