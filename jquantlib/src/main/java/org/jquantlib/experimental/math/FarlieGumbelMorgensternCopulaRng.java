/*
 Copyright (C) 2010 Hachemi Benyahia
 Copyright (C) 2010 DeriveXperts SAS
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

package org.jquantlib.experimental.math;

import org.jquantlib.QL;
import org.jquantlib.math.randomnumbers.RandomNumberGenerator;
import org.jquantlib.methods.montecarlo.Sample;

/**
 * Farlie-Gumbel-Morgenstern copula random-number generator.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/farliegumbelmorgensterncopularng.hpp}.
 */
public final class FarlieGumbelMorgensternCopulaRng {

    private final RandomNumberGenerator uniformGenerator_;
    private final double theta_;

    public FarlieGumbelMorgensternCopulaRng(final RandomNumberGenerator uniformGenerator, final double theta) {
        QL.require(theta >= -1.0 && theta <= 1.0, "theta (" + theta + ") must be in [-1,1]");
        this.uniformGenerator_ = uniformGenerator;
        this.theta_ = theta;
    }

    /** Returns a 2-dim sample drawn from the FGM copula. */
    public Sample< double[] > next() {
        final Sample< Double > v1 = uniformGenerator_.next();
        final Sample< Double > v2 = uniformGenerator_.next();
        final double u1 = v1.value();
        final double a = theta_ * (2.0 * u1 - 1.0);
        final double b = Math.pow(1.0 - theta_ * (2.0 * u1 - 1.0), 2.0) + 4.0 * theta_ * v2.value() * (2.0 * u1 - 1.0);
        final double u2 = (2.0 * v2.value()) / (Math.sqrt(b) - a);
        return new Sample< double[] >(new double[] { u1, u2 }, v1.weight() * v2.weight());
    }
}
