/*
 Copyright (C) 2026 JQuantLib migration

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

/*
 Copyright (C) 2008 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;

/**
 * One-factor Gaussian copula.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::OneFactorGaussianCopula}
 * ({@code ql/experimental/credit/onefactorgaussiancopula.{hpp,cpp}}).
 *
 * <p>Specifies the densities of M, Z, and Y to the standard normal
 * distribution {@code phi(x) = exp(-x^2/2) / sqrt(2 pi)} and overrides the table-based cumulative-Y / inverse-Y of the
 * base class with direct CDF calls.
 *
 * <p>Phase 4m.5.
 */
public class OneFactorGaussianCopula extends OneFactorCopula {

    private final NormalDistribution density = new NormalDistribution();
    private final CumulativeNormalDistribution cumulative = new CumulativeNormalDistribution();
    private final InverseCumulativeNormal inverseCumulative = new InverseCumulativeNormal();

    public OneFactorGaussianCopula(final Handle< Quote > correlation, final double maximum,
            final int integrationSteps) {
        super(correlation, maximum, integrationSteps, -maximum);
        correlation.addObserver(this);
    }

    public OneFactorGaussianCopula(final Handle< Quote > correlation) {
        this(correlation, 5.0, 50);
    }

    @Override
    public double density(final double m) {
        return density.op(m);
    }

    @Override
    public double cumulativeZ(final double z) {
        return cumulative.op(z);
    }

    @Override
    public double cumulativeY(final double y) {
        return cumulative.op(y);
    }

    @Override
    public double inverseCumulativeY(final double p) {
        return inverseCumulative.op(p);
    }

    /** Test cumulative-Y via direct double-Euler integration; mirrors C++ {@code testCumulativeY}. */
    public double testCumulativeY(final double y) {
        final double c = correlation.currentLink().value();
        if ( c == 0 || c == 1 ) {
            return new CumulativeNormalDistribution().op(y);
        }
        final NormalDistribution dz = new NormalDistribution();
        final NormalDistribution dm = new NormalDistribution();
        final double minimum = -10;
        final double maximum = +10;
        final int steps = 200;
        final double delta = (maximum - minimum) / steps;
        double cumulated = 0.0;
        if ( c < 0.5 ) {
            for ( double m = minimum; m < maximum; m += delta ) {
                final double zMax = (y - Math.sqrt(c) * m) / Math.sqrt(1.0 - c);
                for ( double z = minimum; z < zMax; z += delta ) {
                    cumulated += dm.op(m) * dz.op(z);
                }
            }
        } else {
            for ( double z = minimum; z < maximum; z += delta ) {
                final double mMax = (y - Math.sqrt(1.0 - c) * z) / Math.sqrt(c);
                for ( double m = minimum; m < mMax; m += delta ) {
                    cumulated += dm.op(m) * dz.op(z);
                }
            }
        }
        cumulated *= (delta * delta);
        return cumulated;
    }

    /** Nothing to do when correlation changes — table is not used. */
    @Override
    protected void performCalculations() {
        // empty
    }
}
