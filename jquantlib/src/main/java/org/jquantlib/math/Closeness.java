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

package org.jquantlib.math;

import org.jquantlib.lang.annotation.NonNegative;

/**
 * Follows somewhat the advice of Knuth on checking for floating-point equality. The closeness relationship is:
 * <p>{@latex[
 * \mathrm{close}(x,y,n) \equiv |x-y| \leq \varepsilon |x| \wedge |x-y| \leq \varepsilon |y| }
 * <p>where {@latex$ \varepsilon} is {@latex$ n} times the machine accuracy;
 * <p>{@latex$ n} equals 42 if not given.
 *
 * @author Richard Gomes
 */
final public class Closeness {

    //
    // Static public final methods
    //

    static public boolean isClose(final double x, final double y) {
        return isClose(x, y, 42);
    }

    static public boolean isClose(final double x, final double y, @NonNegative final int n) {
        // Deals with +infinity and -infinity representations etc.
        // (C++ v1.43 ql/math/comparison.hpp:47-49)
        if (x == y) {
            return true;
        }

        final double diff = Math.abs(x - y);
        final double tolerance = n * Constants.QL_EPSILON;

        // A relative tolerance is meaningless against zero: C++ falls back to
        // an absolute one (ql/math/comparison.hpp:53-54). Without this branch
        // the two clauses below reduce to `diff <= 0` on the zero side, so
        // nothing but exact zero can ever be close to zero.
        if (x == 0.0 || y == 0.0) {
            return diff < (tolerance * tolerance);
        }

        return diff <= tolerance * Math.abs(x) && diff <= tolerance * Math.abs(y);
    }

    static public boolean isCloseEnough(final double x, final double y) {
        return isCloseEnough(x, y, 42);
    }

    static public boolean isCloseEnough(final double x, final double y, @NonNegative final int n) {
        // Deals with +infinity and -infinity representations etc.
        // (C++ v1.43 ql/math/comparison.hpp:63-65)
        if (x == y) {
            return true;
        }

        final double diff = Math.abs(x - y);
        final double tolerance = n * Constants.QL_EPSILON;

        // See isClose: relative tolerance against zero degenerates.
        // (C++ v1.43 ql/math/comparison.hpp:69-70)
        if (x == 0.0 || y == 0.0) {
            return diff < (tolerance * tolerance);
        }

        return diff <= tolerance * Math.abs(x) || diff <= tolerance * Math.abs(y);
    }

}
