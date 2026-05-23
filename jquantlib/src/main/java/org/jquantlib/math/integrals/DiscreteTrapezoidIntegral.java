/*
 Copyright (C) 2014 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.math.integrals;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Trapezoidal rule on a non-uniform discrete grid (functor form).
 *
 * <p>Faithful Java port of {@code QuantLib::DiscreteTrapezoidIntegral}
 * (v1.42.1 {@code ql/math/integrals/discreteintegrals.{hpp,cpp}}, pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Sibling of {@link DiscreteSimpsonIntegral}, complementing the existing
 * {@link DiscreteTrapezoidIntegrator} integrator form.
 *
 * <p>Reference: Levy, D. <i>Numerical Integration</i>
 * (https://www2.math.umd.edu/~dlevy/classes/amsc466/lecture-notes/integration-chap.pdf).
 *
 * <p>Phase 2 L1-D port.
 */
public class DiscreteTrapezoidIntegral {

    /**
     * Trapezoidal integration of {@code f} over a non-uniform grid {@code x}.
     * Requires {@code x.size() == f.size()}.
     */
    public double evaluate(final Array x, final Array f) {
        final int n = f.size();
        QL.require(n == x.size(), "inconsistent size");

        double sum = 0.0;
        for ( int i = 0; i < n - 1; i++ ) {
            sum += (x.get(i + 1) - x.get(i)) * (f.get(i) + f.get(i + 1));
        }
        return 0.5 * sum;
    }
}
