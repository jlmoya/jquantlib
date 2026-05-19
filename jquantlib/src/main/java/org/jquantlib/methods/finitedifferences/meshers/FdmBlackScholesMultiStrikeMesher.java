/*
 Copyright (C) 2009 Klaus Spanderen

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
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * One-dimensional FDM mesher for the Black-Scholes process (log-spot) that spans a strip wide enough to cover the
 * densities of a vector of strikes.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/meshers/fdmblackscholesmultistrikemesher.{hpp,cpp}}.
 * <p>
 * Given a set of strikes {@code K_i}, the mesher computes a single log-spot grid whose boundaries are wide enough to
 * cover the {@code ±sigma*sqrt(T)* normInv(1-eps)*scaleFactor} envelopes evaluated at the extremal strikes (smallest
 * and largest). The forward used to set the boundary is {@code F_min = S^2/K_max * d} (left side) and
 * {@code F_max = S^2/K_min * d} (right side), where {@code d} is the discount-factor ratio {@code q/r}. The boundary is
 * then taken as the wider of
 * <ul>
 *   <li>the "tail" boundary {@code log(F) ± sigmaSqrtT * normInvEps * scaleFactor
 *       - sigmaSqrtT^2/2}</li>
 *   <li>a generous coverage boundary {@code 0.8 * log(0.8 * S^2/K_max)}
 *       (or {@code 1.2 * log(0.8 * S^2/K_min)}).</li>
 * </ul>
 * <p>
 * If an optional concentration point {@code cPoint} falls inside the
 * computed boundary, a {@link Concentrating1dMesher} is used; otherwise a
 * {@link Uniform1dMesher} is used.
 *
 * @author Phase 5e.5b-CFC-d-279 port
 */
public class FdmBlackScholesMultiStrikeMesher extends Fdm1dMesher {

    /**
     * Full constructor mirroring C++ v1.42.1.
     *
     * @param size          number of grid points
     * @param process       Black-Scholes process (used for spot, rate, dividend, and vol surface)
     * @param maturity      option maturity in years
     * @param strikes       vector of strikes whose densities must be covered
     * @param eps           tail percentile (e.g. {@code 0.0001})
     * @param scaleFactor   boundary scaling factor (e.g. {@code 1.5})
     * @param cPointValue   optional concentration point (NaN = no concentration)
     * @param cPointDensity concentration density (NaN if no concentration)
     */
    public FdmBlackScholesMultiStrikeMesher(final int size, final GeneralizedBlackScholesProcess process,
            final double maturity, final double[] strikes, final double eps, final double scaleFactor,
            final double cPointValue, final double cPointDensity) {

        super(size);

        final double spot = process.x0();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double d = process.dividendYield().currentLink().discount(maturity) / process.riskFreeRate().currentLink()
                .discount(maturity);

        double minStrike = Double.POSITIVE_INFINITY;
        double maxStrike = Double.NEGATIVE_INFINITY;
        for ( final double K : strikes ) {
            if ( K < minStrike ) {
                minStrike = K;
            }
            if ( K > maxStrike ) {
                maxStrike = K;
            }
        }

        final double Fmin = spot * spot / maxStrike * d;
        final double Fmax = spot * spot / minStrike * d;

        QL.require(Fmin > 0.0, "negative forward given");

        // Boundary computation (log-space).
        final InverseCumulativeNormal icn = new InverseCumulativeNormal();
        final double normInvEps = icn.op(1.0 - eps);

        final double sigmaSqrtTmin =
                process.blackVolatility().currentLink().blackVol(maturity, minStrike, true) * Math.sqrt(maturity);
        final double sigmaSqrtTmax =
                process.blackVolatility().currentLink().blackVol(maturity, maxStrike, true) * Math.sqrt(maturity);

        final double xMin = Math.min(0.8 * JQuantMath.log(0.8 * spot * spot / maxStrike),
                JQuantMath.log(Fmin) - sigmaSqrtTmin * normInvEps * scaleFactor - sigmaSqrtTmin * sigmaSqrtTmin / 2.0);

        final double xMax = Math.max(1.2 * JQuantMath.log(0.8 * spot * spot / minStrike),
                JQuantMath.log(Fmax) + sigmaSqrtTmax * normInvEps * scaleFactor - sigmaSqrtTmax * sigmaSqrtTmax / 2.0);

        // Helper mesher (concentrating or uniform).
        final Fdm1dMesher helper;
        final double logCPoint = Double.isNaN(cPointValue) ? Double.NaN : JQuantMath.log(cPointValue);

        if ( !Double.isNaN(logCPoint) && logCPoint >= xMin && logCPoint <= xMax ) {
            helper = new Concentrating1dMesher(xMin, xMax, size, logCPoint, cPointDensity);
        } else {
            helper = new Uniform1dMesher(xMin, xMax, size);
        }

        System.arraycopy(helper.locations, 0, locations, 0, size);
        System.arraycopy(helper.dplus, 0, dplus, 0, size);
        System.arraycopy(helper.dminus, 0, dminus, 0, size);
    }

    /**
     * Convenience constructor without concentration point.
     */
    public FdmBlackScholesMultiStrikeMesher(final int size, final GeneralizedBlackScholesProcess process,
            final double maturity, final double[] strikes, final double eps, final double scaleFactor) {
        this(size, process, maturity, strikes, eps, scaleFactor, Double.NaN, Double.NaN);
    }

    /**
     * Convenience constructor with C++ defaults ({@code eps=0.0001, scaleFactor=1.5}).
     */
    public FdmBlackScholesMultiStrikeMesher(final int size, final GeneralizedBlackScholesProcess process,
            final double maturity, final double[] strikes) {
        this(size, process, maturity, strikes, 0.0001, 1.5, Double.NaN, Double.NaN);
    }
}
