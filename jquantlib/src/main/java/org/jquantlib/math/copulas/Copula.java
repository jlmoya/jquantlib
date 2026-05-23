/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.math.copulas;

/**
 * Java SAM (single-abstract-method) interface for a bivariate copula C(u, v)
 * where {@code u, v} are marginal CDF values in {@code [0, 1]}.
 *
 * <p>Mirrors the C++ QuantLib v1.42.1 copula function-object idiom (each copula
 * header declares {@code Real operator()(Real, Real) const}). Java uses an
 * explicit SAM so call sites can pass copulas as lambdas or method references.
 *
 * <p>Implementations are expected to be stateless after construction and
 * thread-safe for read-only invocation.
 */
@FunctionalInterface
public interface Copula {
    /**
     * Evaluate the copula at {@code (u, v)}.
     *
     * @param u first marginal CDF value, must be in {@code [0, 1]}
     * @param v second marginal CDF value, must be in {@code [0, 1]}
     * @return joint CDF value {@code C(u, v)} in {@code [0, 1]}
     */
    double apply(double u, double v);
}
