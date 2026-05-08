/*
 Copyright (C) 2018 Klaus Spanderen
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

import org.jquantlib.math.Constants;
import org.jquantlib.methods.finitedifferences.utilities.CEVRNDCalculator;

/**
 * One-dimensional mesher for the CEV model.
 * <p>
 * Java port of v1.42.1
 * {@code ql/methods/finitedifferences/meshers/fdmcev1dmesher.{hpp,cpp}}.
 *
 * <p>The grid bounds are derived from the CEV risk-neutral density (via
 * {@link CEVRNDCalculator}), and the cell concentration is delegated to
 * {@link Concentrating1dMesher} or {@link Uniform1dMesher} depending on
 * whether a valid critical point was supplied.
 *
 * @author Phase 2m Track C port
 */
public class FdmCEV1dMesher extends Fdm1dMesher {

    /**
     * Build a CEV 1D mesher.
     *
     * @param size         number of grid nodes
     * @param f0           initial forward value
     * @param alpha        CEV volatility parameter
     * @param beta         CEV exponent (must not be 1.0; must be &lt; 1.0 for
     *                     the SABR engine which also calls this)
     * @param maturity     maturity in years
     * @param eps          tail probability cutoff (default 1e-4)
     * @param scaleFactor  scale factor for the upper bound (default 1.5)
     * @param cPointF      critical point to concentrate around (or
     *                     {@link Double#NaN} for no critical point)
     * @param cPointDensity concentration density at the critical point
     */
    public FdmCEV1dMesher(
            final int size,
            final double f0,
            final double alpha,
            final double beta,
            final double maturity,
            final double eps,
            final double scaleFactor,
            final double cPointF,
            final double cPointDensity) {
        super(size);

        final CEVRNDCalculator rnd = new CEVRNDCalculator(f0, alpha, beta);

        final double upperBound = scaleFactor * rnd.invcdf(1.0 - eps, maturity);
        final double massAtZero = rnd.massAtZero(maturity);

        final double lowerBound;
        if (massAtZero > eps) {
            // absorbing boundary: beta < 0 uses QL_EPSILON; otherwise 0
            lowerBound = (beta < 0) ? Constants.QL_EPSILON : 0.0;
        } else {
            lowerBound = rnd.invcdf(eps, maturity) / scaleFactor;
        }

        // Build helper 1D mesher
        final Fdm1dMesher helper;
        final boolean hasCPoint = !Double.isNaN(cPointF)
                && cPointF >= lowerBound && cPointF <= upperBound;

        if (hasCPoint) {
            helper = new Concentrating1dMesher(
                    lowerBound, upperBound, size, cPointF, cPointDensity, false);
        } else {
            helper = new Uniform1dMesher(lowerBound, upperBound, size);
        }

        // copy locations and spacings from the helper
        System.arraycopy(helper.locations, 0, locations, 0, size);
        System.arraycopy(helper.dplus,     0, dplus,     0, size);
        System.arraycopy(helper.dminus,    0, dminus,    0, size);
    }

    /**
     * Convenience constructor with default eps=1e-4, scaleFactor=1.5.
     */
    public FdmCEV1dMesher(
            final int size,
            final double f0,
            final double alpha,
            final double beta,
            final double maturity) {
        this(size, f0, alpha, beta, maturity, 1e-4, 1.5,
                Double.NaN, Double.NaN);
    }
}
