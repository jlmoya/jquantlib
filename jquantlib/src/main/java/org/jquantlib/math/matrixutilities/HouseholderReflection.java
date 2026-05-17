/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.math.matrixutilities;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;

/**
 * Householder reflection projecting onto the unit vector e.
 * Java port of QuantLib v1.42.1 ql/math/matrixutilities/householder.hpp,cpp.
 */
public class HouseholderReflection {

    private final Array e;

    public HouseholderReflection(final Array e) {
        this.e = e;
    }

    /** C++ reflectionVector(const Array& a): v such that (I-2vv^T)a = ||a||*e. */
    public Array reflectionVector(final Array a) {
        final double na = Math.sqrt(a.dotProduct(a));
        QL.require(na > 0, "vector of length zero given");

        final double aDotE = a.dotProduct(e);
        final Array a1 = e.mul(aDotE);
        final Array a2 = a.sub(a1);

        final double eps = a2.dotProduct(a2) / (aDotE * aDotE);
        if (eps < Constants.QL_EPSILON * Constants.QL_EPSILON) {
            return new Array(a.size()).fill(0.0);
        } else if (eps < 1e-4) {
            final double eps2 = eps * eps;
            final double eps3 = eps * eps2;
            final double eps4 = eps2 * eps2;
            final double coeff = eps / 2.0 - eps2 / 8.0 + eps3 / 16.0
                    - (5.0 / 128.0) * eps4;
            final double denom = aDotE * Math.sqrt(
                    eps + eps2 / 4.0 - eps3 / 8.0 + (5.0 / 64.0) * eps4);
            final Array num = a2.sub(a1.mul(coeff));
            return num.mul(1.0 / denom);
        } else {
            final Array c = a.sub(e.mul(na));
            final double nc = Math.sqrt(c.dotProduct(c));
            return c.mul(1.0 / nc);
        }
    }

    /** C++: operator()(const Array& a) = HouseholderTransformation(v)(a). */
    public Array apply(final Array a) {
        final Array v = reflectionVector(a);
        return new HouseholderTransformation(v).apply(a);
    }
}
