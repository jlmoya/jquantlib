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

import org.jquantlib.instruments.Option;

/**
 * Functional interface for evaluating a vanilla swaption-style option price
 * given strike, type, and an arbitrary deflator (annuity / discount).
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code VanillaOptionPricer} in
 * {@code ql/cashflows/conundrumpricer.hpp}:
 * <pre>{@code
 * class VanillaOptionPricer {
 *   public:
 *     virtual ~VanillaOptionPricer() = default;
 *     virtual Real operator()(Real strike,
 *                             Option::Type optionType,
 *                             Real deflator) const = 0;
 * };
 * }</pre>
 * <p>
 * The standard concrete implementation is
 * {@link MarketQuotedOptionPricer}, which deflates the
 * Black/Bachelier formula by the supplied factor.
 */
@FunctionalInterface
public interface VanillaOptionPricer {

    /**
     * Evaluate the deflated option price.
     *
     * @param strike     option strike
     * @param optionType {@link Option.Type#Call} or {@link Option.Type#Put}
     * @param deflator   multiplicative scale (annuity, discount, etc.)
     * @return deflated option price
     */
    double evaluate(double strike, Option.Type optionType, double deflator);
}
