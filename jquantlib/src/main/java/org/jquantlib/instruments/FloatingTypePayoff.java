/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2003, 2006 Ferdinando Ametrano
 Copyright (C) 2006 Warren Chou
 Copyright (C) 2006 StatPro Italia srl
 Copyright (C) 2006 Chiara Fornarola

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.instruments;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Floating-strike payoff (used by floating-strike lookback options).
 *
 * <p>The single-argument {@link #get(double)} form is not supported because
 * floating-strike payoffs require both the underlying price and a separately
 * tracked strike (typically the running min/max of the underlying). Use
 * {@link #get(double, double)} instead.
 *
 * <p>Port of QuantLib v1.42.1 {@code QuantLib::FloatingTypePayoff}
 * ({@code ql/instruments/payoffs.hpp}).
 */
public class FloatingTypePayoff extends TypePayoff {

    public FloatingTypePayoff(final Option.Type type) {
        super(type);
    }

    @Override
    public String name() {
        return "FloatingType";
    }

    /**
     * Floating-strike payoffs cannot be evaluated with a single price; the
     * strike depends on the running extremum of the path. C++ throws
     * {@code QL_FAIL("floating payoff not handled")}; we mirror that.
     */
    @Override
    public double get(final double price) {
        throw new LibraryException("floating payoff not handled");
    }

    /**
     * Two-argument form: {@code max(price - strike, 0)} for calls, {@code
     * max(strike - price, 0)} for puts.
     */
    public double get(final double price, final double strike) {
        if (type == Option.Type.Call) {
            return Math.max(price - strike, 0.0);
        } else if (type == Option.Type.Put) {
            return Math.max(strike - price, 0.0);
        } else {
            throw new LibraryException(UNKNOWN_OPTION_TYPE);
        }
    }

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor<FloatingTypePayoff> v =
            (pv != null) ? pv.visitor(this.getClass()) : null;
        if (v != null) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }
}
