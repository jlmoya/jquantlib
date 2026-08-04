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

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.integrals;

import org.jquantlib.QL;
import org.jquantlib.math.matrixutilities.Array;

/**
 * Composite Simpson's rule on a non-uniform discrete grid.
 *
 * <p>Java port of v1.42.1
 * {@code ql/math/integrals/discreteintegrals.{hpp,cpp}} (the {@code DiscreteSimpsonIntegral} functor only — the
 * {@code DiscreteSimpsonIntegrator} adaptive variant and the {@code DiscreteTrapezoidIntegral} sibling are not yet
 * required by Java callers).
 *
 * <p>Reference: Levy, D. <i>Numerical Integration</i>
 * (https://www2.math.umd.edu/~dlevy/classes/amsc466/lecture-notes/integration-chap.pdf).
 *
 * <p>Algorithm (per pair of consecutive intervals at indices {@code j, j+1, j+2}):
 * <pre>
 *   dxj   = x[j+1] - x[j]
 *   dxjp1 = x[j+2] - x[j+1]
 *   alpha = dxjp1 * (2*dxj   - dxjp1)
 *   gamma = dxj   * (2*dxjp1 - dxj)
 *   beta  = (dxj + dxjp1)^2
 *   k     = (dxj + dxjp1) / (6 * dxjp1 * dxj)
 *   contrib = k * (alpha*f[j] + beta*f[j+1] + gamma*f[j+2])
 * </pre>
 * Pairs are summed for {@code j = 0, 2, ..., n-3}. When {@code n} is even, a trapezoidal contribution closes the last
 * interval (one cell is left unpaired by the pair-step).
 *
 * <p>For a uniform grid this degenerates to the standard composite Simpson's
 * 1/3 rule: per pair the weight pattern reduces to {@code h/3 * (f0 + 4 f1 + f2)}.
 *
 * @author Phase 5h.5-RND-b port
 */
public class DiscreteSimpsonIntegral {

    /**
     * Evaluate the integral of {@code f} sampled at the abscissae {@code x}.
     *
     * @param x sorted abscissae (size {@code n}, may be non-uniform)
     * @param f function values at {@code x} (size {@code n})
     * @return Simpson's-rule approximation of {@code integral(f) over [x[0], x[n-1]]}
     */
    public double op(final Array x, final Array f) {
        final int n = f.size();
        QL.require(n == x.size(), "inconsistent size");

        // Fewer than two nodes means there is no interval to integrate. Without this, n == 0 falls through to the
        // even-n trailing term below and indexes x[-1]. Mirrors C++ v1.43
        // ({@code ql/math/integrals/discreteintegrals.cpp}), which added the same guard for the unsigned wrap-around.
        if ( n < 2 ) {
            return 0.0;
        }

        double sum = 0.0;

        // Pair-step: each Simpson pair covers indices j, j+1, j+2 on a
        // possibly non-uniform grid. The C++ loop is `for (j=0; j < n-2; j+=2)`
        // — with j being unsigned. Be explicit about n >= 2 to avoid
        // wrap-around in the equivalent Java int.
        for ( int j = 0; j + 2 < n; j += 2 ) {
            final double dxj = x.get(j + 1) - x.get(j);
            final double dxjp1 = x.get(j + 2) - x.get(j + 1);

            final double alpha = dxjp1 * (2.0 * dxj - dxjp1);
            final double dd = dxj + dxjp1;
            final double k = dd / (6.0 * dxjp1 * dxj);
            final double beta = dd * dd;
            final double gamma = dxj * (2.0 * dxjp1 - dxj);

            sum += k * (alpha * f.get(j) + beta * f.get(j + 1) + gamma * f.get(j + 2));
        }

        // For even n, one trailing interval is left unpaired — close with a
        // trapezoidal contribution. Mirrors C++ `(n & 1) == 0U` branch.
        if ( (n & 1) == 0 ) {
            sum += 0.5 * (x.get(n - 1) - x.get(n - 2)) * (f.get(n - 1) + f.get(n - 2));
        }

        return sum;
    }
}
