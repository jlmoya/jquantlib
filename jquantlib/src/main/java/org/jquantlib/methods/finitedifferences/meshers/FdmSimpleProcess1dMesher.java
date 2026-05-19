/*
 Copyright (C) 2009, 2011 Klaus Spanderen

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

import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.processes.StochasticProcess1D;

/**
 * One-dimensional grid mesher driven by a {@link StochasticProcess1D}.
 * <p>
 * Java port of v1.42.1 ql/methods/finitedifferences/meshers/fdmsimpleprocess1dmesher.{hpp,cpp}.
 * <p>
 * Builds a non-uniform mesh by averaging, over {@code tAvgSteps} sub-times of {@code [0, maturity]}, the
 * percentile-evolved values of the process started at {@code x0}. The averaging guarantees that the grid spans a
 * sensible range for every intermediate solver step. Used by the Hull-White and G2 PDE solvers planned in later
 * sub-layers.
 *
 * @author Phase 2h WI-1 port
 */
public class FdmSimpleProcess1dMesher extends Fdm1dMesher {

    /**
     * Convenience overload — delegates to the full constructor with {@code tAvgSteps = 10}, {@code epsilon = 0.0001},
     * and {@code mandatoryPoint = Null<Real>() (Double.NaN)}.
     */
    public FdmSimpleProcess1dMesher(final int size, final StochasticProcess1D process, final double maturity) {
        this(size, process, maturity, 10, 0.0001, Double.NaN);
    }

    /**
     * Full constructor matching C++ v1.42.1 {@code FdmSimpleProcess1dMesher::FdmSimpleProcess1dMesher}.
     *
     * @param size           number of grid cells
     * @param process        driving stochastic process
     * @param maturity       terminal time the mesh must span
     * @param tAvgSteps      number of sub-times averaged into each location
     * @param eps            tail percentile (e.g. {@code 1e-4} keeps {@code 1 - 2*eps} of the distribution)
     * @param mandatoryPoint point that must lie inside the mesh range (typically a barrier / strike). Use
     *                       {@link Double#NaN} for "none" — matches C++ {@code Null<Real>()}.
     */
    public FdmSimpleProcess1dMesher(final int size, final StochasticProcess1D process, final double maturity,
            final int tAvgSteps, final double eps, final double mandatoryPoint) {
        super(size);

        // locations_ default-initialises to zero in Java; matches C++ std::fill.
        final InverseCumulativeNormal invCumNorm = new InverseCumulativeNormal();
        final double x0 = process.x0();

        for ( int l = 1; l <= tAvgSteps; ++l ) {
            final double t = (maturity * l) / tAvgSteps;

            final double mp = Double.isNaN(mandatoryPoint) ? x0 : mandatoryPoint;

            final double evolveLow = process.evolve(0.0, x0, t, invCumNorm.op(eps));
            final double evolveHigh = process.evolve(0.0, x0, t, invCumNorm.op(1.0 - eps));

            final double qMin = Math.min(Math.min(mp, x0), evolveLow);
            final double qMax = Math.max(Math.max(mp, x0), evolveHigh);

            final double dp = (1.0 - 2.0 * eps) / (size - 1);
            double p = eps;
            locations[0] += qMin;

            for ( int i = 1; i < size - 1; ++i ) {
                p += dp;
                locations[i] += process.evolve(0.0, x0, t, invCumNorm.op(p));
            }
            locations[size - 1] += qMax;
        }

        for ( int i = 0; i < size; ++i ) {
            locations[i] /= tAvgSteps;
        }

        for ( int i = 0; i < size - 1; ++i ) {
            dplus[i] = locations[i + 1] - locations[i];
            dminus[i + 1] = dplus[i];
        }
        // C++ leaves dplus_.back() and dminus_.front() as Null<Real>().
        dplus[size - 1] = Double.NaN;
        dminus[0] = Double.NaN;
    }
}
