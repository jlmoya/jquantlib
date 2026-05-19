/*
 Copyright (C) 2008 Richard Gomes

 This source code is release under the BSD License.

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

package org.jquantlib.math.distributions;

import org.jquantlib.QL;

/**
 * In mathematics, the Gamma function is an extension of the factorial function to real and complex numbers. The Gamma
 * function is a component in various probability-distribution functions, and as such it is applicable in the fields of
 * probability and statistics, as well as combinatorics.
 *
 * @author Richard Gomes
 * @author Dominik Holenstein
 * @see <a href="http://en.wikipedia.org/wiki/Gamma_function">Gamma Function</a>
 */
public class GammaFunction {

    //
    // private static fields (constants)
    //

    private static final double c1_ = 76.18009172947146;
    private static final double c2_ = -86.50532032941677;
    private static final double c3_ = 24.01409824083091;
    private static final double c4_ = -1.231739572450155;
    private static final double c5_ = 0.1208650973866179e-2;
    private static final double c6_ = -0.5395239384953e-5;

    //
    // computes the logValue
    //

    /**
     * The Gamma function value itself. Mirrors C++ v1.42.1 {@code QuantLib::GammaFunction::value(Real)}: uses
     * {@link #logValue} for {@code x >= 1}, the recurrence {@code Γ(x) = Γ(x+1)/x} for {@code x ∈ (-20, 1)}, and the
     * reflection formula {@code Γ(-x) = -π / (Γ(x)·x·sin(πx))} for very negative arguments.
     *
     * <p>Phase 2f WI-3 alignment: dependency of the
     * {@link org.jquantlib.math.ModifiedBesselFunction} port used by the Heston BroadieKaya Fourier-inversion harness.
     */
    public double value(final double x) {
        if ( x >= 1.0 ) {
            return Math.exp(logValue(x));
        } else if ( x > -20.0 ) {
            return value(x + 1.0) / x;
        } else {
            return -Math.PI / (value(-x) * x * Math.sin(Math.PI * x));
        }
    }

    /**
     * Computes the log of the Gamma.
     *
     * @param x
     * @return <code>-temp+Math.log(2.5066282746310005*ser/x)</code>
     */
    public double logValue(final double x) /* Read-only */ {
        QL.require(x > 0.0, "positive argument required"); // TODO: message
        double temp = x + 5.5;
        temp -= (x + 0.5) * Math.log(temp);
        double ser = 1.000000000190015;

        ser += c1_ / (x + 1.0);
        ser += c2_ / (x + 2.0);
        ser += c3_ / (x + 3.0);
        ser += c4_ / (x + 4.0);
        ser += c5_ / (x + 5.0);
        ser += c6_ / (x + 6.0);

        return -temp + Math.log(2.5066282746310005 * ser / x);
    }

}
