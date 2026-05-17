/*
 Copyright (C) 2009 Dimitri Reiswich
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

package org.jquantlib.math;

/**
 * Kernel function in the statistical sense: a non-negative, real-valued
 * function which integrates to one and is symmetric.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/math/kernelfunctions.hpp}
 * (class {@code KernelFunction}).
 *
 * <p>This is a functional interface so a Java lambda can be used wherever
 * the C++ code accepts a free function such as {@code epanechnikovKernel}.
 *
 * @author Phase 5e.5b-CFC-d-59 port
 */
@FunctionalInterface
public interface KernelFunction {

    /**
     * Evaluate the kernel at point {@code x}.
     *
     * @param x the input value
     * @return the kernel weight K(x)
     */
    double op(double x);
}
