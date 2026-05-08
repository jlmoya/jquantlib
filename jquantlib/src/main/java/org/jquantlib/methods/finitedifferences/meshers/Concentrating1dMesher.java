/*
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2014 Johannes Göttker-Schnetmann
 Copyright (C) 2014 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the license for more details.
*/

package org.jquantlib.methods.finitedifferences.meshers;

import org.jquantlib.QL;
import org.jquantlib.math.interpolations.LinearInterpolation;
import org.jquantlib.math.matrixutilities.Array;

/**
 * One-dimensional grid mesher concentrating grid points around one critical
 * point using a sinh transformation.
 * <p>
 * Java port of v1.42.1
 * ql/methods/finitedifferences/meshers/concentrating1dmesher.{hpp,cpp}
 * (simple pair-based constructor only — sufficient for AndreasenHuge use case).
 *
 * @author Phase 2m Track D port
 */
public class Concentrating1dMesher extends Fdm1dMesher {

    /**
     * Constructs a 1D mesh of {@code size} points on [{@code start},{@code end}]
     * concentrating points near {@code cPoint.first} with density scale
     * {@code cPoint.second}. If either component is {@link Double#NaN}, falls
     * back to uniform spacing. Mirrors C++ constructor taking
     * {@code std::pair<Real,Real>}.
     *
     * @param start         left boundary (exclusive: actual grid boundary)
     * @param end           right boundary
     * @param size          total number of grid points (including endpoints)
     * @param cPointLoc     concentration point location (NaN = no concentration)
     * @param cPointDensity relative density parameter (NaN = uniform)
     * @param requireCPoint if true the concentration point must appear exactly;
     *                      ignored when cPoint is NaN
     */
    public Concentrating1dMesher(final double start, final double end,
            final int size,
            final double cPointLoc, final double cPointDensity,
            final boolean requireCPoint) {
        super(size);

        QL.require(end > start, "end must be larger than start");

        final boolean hasCPoint = !Double.isNaN(cPointLoc);
        final double density = Double.isNaN(cPointDensity)
                ? Double.NaN : cPointDensity * (end - start);

        if (hasCPoint) {
            QL.require(cPointLoc >= start && cPointLoc <= end,
                    "cPoint must be between start and end");
            QL.require(!Double.isNaN(density) && density > 0.0,
                    "density > 0 required");
        }
        if (requireCPoint) {
            QL.require(hasCPoint, "cPoint is required in grid but not given");
        }

        final double dx = 1.0 / (size - 1);

        if (hasCPoint) {
            final double c1 = asinh((start - cPointLoc) / density);
            final double c2 = asinh((end - cPointLoc) / density);

            if (requireCPoint) {
                // Build a piecewise-linear transform so the concentration point
                // lands exactly on a grid node.
                final double[] u;
                final double[] z;

                final boolean atBoundary = (Math.abs(cPointLoc - start) < 1e-15 * (end - start))
                        || (Math.abs(cPointLoc - end) < 1e-15 * (end - start));

                if (!atBoundary) {
                    final double z0 = -c1 / (c2 - c1);
                    final long nodeIdx = Math.max(1L, Math.min((long) Math.round(z0 * (size - 1)),
                            (long) size - 2));
                    final double u0 = nodeIdx / (double) (size - 1);
                    u = new double[]{0.0, u0, 1.0};
                    z = new double[]{0.0, z0, 1.0};
                } else {
                    u = new double[]{0.0, 1.0};
                    z = new double[]{0.0, 1.0};
                }

                final LinearInterpolation transform = new LinearInterpolation(new Array(u), new Array(z));
                transform.enableExtrapolation();

                for (int i = 1; i < size - 1; ++i) {
                    final double li = transform.op(i * dx);
                    locations[i] = cPointLoc + density * Math.sinh(c1 * (1.0 - li) + c2 * li);
                }
            } else {
                for (int i = 1; i < size - 1; ++i) {
                    final double li = i * dx;
                    locations[i] = cPointLoc + density * Math.sinh(c1 * (1.0 - li) + c2 * li);
                }
            }
        } else {
            // Uniform spacing
            for (int i = 1; i < size - 1; ++i) {
                locations[i] = start + i * dx * (end - start);
            }
        }

        locations[0] = start;
        locations[size - 1] = end;

        for (int i = 0; i < size - 1; ++i) {
            dplus[i] = dminus[i + 1] = locations[i + 1] - locations[i];
        }
        dplus[size - 1] = Double.NaN;
        dminus[0] = Double.NaN;
    }

    /** Inverse hyperbolic sine: asinh(x) = ln(x + sqrt(x^2 + 1)). */
    private static double asinh(final double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }

    /**
     * Convenience constructor: no requireCPoint flag (defaults to false).
     */
    public Concentrating1dMesher(final double start, final double end,
            final int size, final double cPointLoc, final double cPointDensity) {
        this(start, end, size, cPointLoc, cPointDensity, false);
    }

    /**
     * Convenience constructor: no concentration point (uniform grid).
     */
    public Concentrating1dMesher(final double start, final double end, final int size) {
        this(start, end, size, Double.NaN, Double.NaN, false);
    }
}
