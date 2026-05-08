/*
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2014 Johannes Göttker-Schnetmann
 Copyright (C) 2014 Klaus Spanderen

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
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * One-dimensional grid mesher concentrating around a critical point.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/meshers/concentrating1dmesher.{hpp,cpp}}.
 *
 * <p>Implements the single-critical-point constructor (with optional
 * {@code requireCPoint} flag). The grid is generated via an inverse-sinh
 * transform that concentrates cells around {@code cPoint} with density
 * {@code density}. When {@code requireCPoint} is {@code true} a
 * piecewise-linear remap ensures the critical point is a grid node.
 *
 * <p>The multi-point ODE-integration constructor is not ported here as it
 * is not required by {@link FdmCEV1dMesher}.
 *
 * @author Phase 2m Track C port
 */
public class Concentrating1dMesher extends Fdm1dMesher {

    /**
     * Sentinel value for "no critical point" — mirrors C++ {@code Null<Real>()}.
     * {@link Double#NaN} is used because it propagates comparisons cleanly.
     */
    public static final double NULL_REAL = Double.NaN;

    /**
     * Build a concentrating 1D mesh from {@code start} to {@code end} with
     * {@code size} nodes.
     *
     * @param start        left boundary
     * @param end          right boundary
     * @param size         number of grid nodes
     * @param cPoint       critical point to concentrate around (or
     *                     {@link #NULL_REAL} for uniform)
     * @param density      concentration density at {@code cPoint}; the actual
     *                     bandwidth used is {@code density * (end - start)}.
     *                     Ignored (pass {@code 0}) when {@code cPoint} is
     *                     {@link #NULL_REAL}.
     * @param requireCPoint if {@code true}, ensure {@code cPoint} is an exact
     *                     grid node via piecewise-linear remap
     */
    public Concentrating1dMesher(
            final double start,
            final double end,
            final int size,
            final double cPoint,
            final double density,
            final boolean requireCPoint) {
        super(size);

        QL.require(end > start, "end must be larger than start");

        final boolean hasCPoint = !Double.isNaN(cPoint);
        final double scaledDensity = Double.isNaN(density) ?
                Double.NaN : density * (end - start);

        QL.require(!hasCPoint || (cPoint >= start && cPoint <= end),
                "cPoint must be between start and end");
        QL.require(!hasCPoint || (!Double.isNaN(scaledDensity) && scaledDensity > 0.0),
                "density must be positive when cPoint is given");
        QL.require(!requireCPoint || hasCPoint,
                "cPoint is required in grid but not given");

        final double dx = 1.0 / (size - 1);

        if (hasCPoint) {
            final double c1 = asinh((start - cPoint) / scaledDensity);
            final double c2 = asinh((end   - cPoint) / scaledDensity);

            // piecewise-linear remap u -> z when requireCPoint is set
            double[] u = null, z = null;
            if (requireCPoint) {
                // build 2-3 knot piecewise linear transform
                final boolean atStart = Math.abs(cPoint - start) < 1e-15;
                final boolean atEnd   = Math.abs(cPoint - end)   < 1e-15;
                if (!atStart && !atEnd) {
                    final double z0 = -c1 / (c2 - c1);
                    final long i0 = Math.max(1L, Math.min((long) Math.round(z0 * (size - 1)), (long)(size - 2)));
                    final double u0 = i0 / (double)(size - 1);
                    u = new double[]{0.0, u0, 1.0};
                    z = new double[]{0.0, z0, 1.0};
                } else {
                    u = new double[]{0.0, 1.0};
                    z = new double[]{0.0, 1.0};
                }
            }

            for (int i = 1; i < size - 1; ++i) {
                final double li;
                if (requireCPoint && u != null) {
                    li = linearInterp(u, z, i * dx);
                } else {
                    li = i * dx;
                }
                locations[i] = cPoint + scaledDensity * Math.sinh(c1 * (1.0 - li) + c2 * li);
            }

        } else {
            // uniform mesh
            for (int i = 1; i < size - 1; ++i) {
                locations[i] = start + i * dx * (end - start);
            }
        }

        locations[0]        = start;
        locations[size - 1] = end;

        for (int i = 0; i < size - 1; ++i) {
            dplus[i]     = locations[i + 1] - locations[i];
            dminus[i + 1] = dplus[i];
        }
        dplus[size - 1]  = Double.NaN;
        dminus[0]        = Double.NaN;
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
    public Concentrating1dMesher(
            final double start, final double end, final int size,
            final double cPoint, final double density) {
        this(start, end, size, cPoint, density, false);
    }

    // --- helpers ---

    /** Inverse hyperbolic sine using JQuantMath.log. */
    private static double asinh(final double x) {
        return JQuantMath.log(x + Math.sqrt(x * x + 1.0));
    }

    /** Linear interpolation on a sorted node array. */
    private static double linearInterp(final double[] u, final double[] z, final double x) {
        // locate interval
        int j = u.length - 2;
        for (int i = 0; i < u.length - 1; ++i) {
            if (x <= u[i + 1]) {
                j = i;
                break;
            }
        }
        final double t = (x - u[j]) / (u[j + 1] - u[j]);
        return z[j] + t * (z[j + 1] - z[j]);
    }
}
