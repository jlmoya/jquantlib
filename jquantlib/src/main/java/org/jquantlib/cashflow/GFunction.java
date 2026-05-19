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
package org.jquantlib.cashflow;

/**
 * G-function used in CMS conundrum/replication pricing (Hagan 2003).
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code GFunction} in {@code ql/cashflows/conundrumpricer.hpp}:
 * <pre>{@code
 * class GFunction {
 *   public:
 *     virtual ~GFunction() = default;
 *     virtual Real operator()(Real x) = 0;
 *     virtual Real firstDerivative(Real x) = 0;
 *     virtual Real secondDerivative(Real x) = 0;
 * };
 * }</pre>
 * <p>
 * Concrete implementations are provided by {@link GFunctionFactory}:
 * <ul>
 *   <li>{@code Standard} — closed-form yield-curve approximation.</li>
 *   <li>{@code ExactYield} — exact-yield model based on swap accruals.</li>
 *   <li>{@code WithShifts} — parallel/non-parallel shifts model with
 *       Newton-calibrated shift parameter.</li>
 * </ul>
 */
public interface GFunction {

    /** Evaluate G(x). */
    double evaluate(double x);

    /** First derivative G'(x). */
    double firstDerivative(double x);

    /** Second derivative G''(x). */
    double secondDerivative(double x);
}
