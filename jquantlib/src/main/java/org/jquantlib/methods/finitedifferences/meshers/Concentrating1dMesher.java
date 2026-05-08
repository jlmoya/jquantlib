/*
 Copyright (C) 2009 Ralph Schreyer
 Copyright (C) 2014 Johannes Goettker-Schnetmann
 Copyright (C) 2014 Klaus Spanderen

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

import org.jquantlib.QL;
import org.jquantlib.math.transcendental.JQuantMath;

/**
 * One-dimensional grid mesher that concentrates points around a critical
 * point (e.g., a strike) using a sinh mapping.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/meshers/concentrating1dmesher.{hpp,cpp}}.
 * Only the single-cPoint constructor is ported (the multi-cPoint ODE variant
 * is not required by {@link FdmBlackScholesMesher}).
 *
 * <p>When {@code cPoint} is {@link Double#NaN} the mesher degenerates to a
 * uniform grid (matches C++ {@code cPoint == Null<Real>()}).
 *
 * @author Phase 2m Track A port
 */
public class Concentrating1dMesher extends Fdm1dMesher {

    /**
     * Construct a 1-D mesh on {@code [start, end]} with {@code size} points,
     * concentrating around {@code cPoint} with normalised density
     * {@code density} (0 = no concentration, higher = tighter).
     *
     * <p>Mirrors C++ v1.42.1
     * {@code Concentrating1dMesher(start, end, size, cPoints, requireCPoint=false)}.
     *
     * @param start     lower bound of the grid
     * @param end       upper bound (must be &gt; {@code start})
     * @param size      number of grid points (&ge; 2)
     * @param cPoint    concentration point (NaN = uniform)
     * @param density   normalised density parameter (NaN = ignored when
     *                  cPoint is NaN). C++ multiplies by {@code (end - start)}
     *                  internally before use.
     */
    // -----------------------------------------------------------------------
    // private helpers
    // -----------------------------------------------------------------------

    /**
     * Inverse hyperbolic sine: asinh(x) = log(x + sqrt(x^2 + 1)).
     * Replaces {@code Math.asinh} which is only available from Java 25+;
     * this project targets Java 11.
     */
    private static double asinh(final double x) {
        return JQuantMath.log(x + Math.sqrt(x * x + 1.0));
    }

    // -----------------------------------------------------------------------
    // constructor
    // -----------------------------------------------------------------------

    public Concentrating1dMesher(final double start,
                                 final double end,
                                 final int size,
                                 final double cPoint,
                                 final double density) {
        super(size);
        QL.require(end > start, "end must be larger than start");

        final double dx = 1.0 / (size - 1);

        if (!Double.isNaN(cPoint)) {
            // C++ multiplies the user-supplied density by (end - start)
            final double dens = density * (end - start);

            // sinh-transform parameters
            // asinh(x) = log(x + sqrt(x^2 + 1))
            final double c1 = asinh((start - cPoint) / dens);
            final double c2 = asinh((end   - cPoint) / dens);

            for (int i = 1; i < size - 1; ++i) {
                final double li = i * dx;
                locations[i] = cPoint + dens * Math.sinh(c1 * (1.0 - li) + c2 * li);
            }
        } else {
            // uniform fallback
            for (int i = 1; i < size - 1; ++i) {
                locations[i] = start + i * dx * (end - start);
            }
        }

        locations[0]        = start;
        locations[size - 1] = end;

        for (int i = 0; i < size - 1; ++i) {
            dplus[i]    = locations[i + 1] - locations[i];
            dminus[i + 1] = dplus[i];
        }
        dplus[size - 1] = Double.NaN;   // Null<Real>()
        dminus[0]       = Double.NaN;   // Null<Real>()
    }
}
