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

/**
 * Householder transformation H = I - 2 v v^T / (v^T v).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/math/matrixutilities/householder.hpp,cpp}.
 */
public class HouseholderTransformation {

    private final Array v;

    public HouseholderTransformation(final Array v) {
        this.v = v;
    }

    /** C++: operator()(const Array& x) const: returns x - 2*(v.x)*v. */
    public Array apply(final Array x) {
        final double dot = v.dotProduct(x);
        return x.sub(v.mul(2.0 * dot));
    }

    /** C++: getMatrix(): H = I - 2 y y^T where y = v/||v||. */
    public Matrix getMatrix() {
        final double norm2 = Math.sqrt(v.dotProduct(v));
        final int n = v.size();
        final double[] y = new double[n];
        for (int i = 0; i < n; ++i) {
            y[i] = v.get(i) / norm2;
        }
        final Matrix m = new Matrix(n, n);
        for (int i = 0; i < n; ++i) {
            for (int j = 0; j < n; ++j) {
                m.set(i, j, ((i == j) ? 1.0 : 0.0) - 2.0 * y[i] * y[j]);
            }
        }
        return m;
    }
}
