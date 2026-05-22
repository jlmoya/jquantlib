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

/*
 Copyright (C) 2003 Neil Firth
 Copyright (C) 2007 StatPro Italia srl
 Copyright (C) 2007 Joseph Wang

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
 */

package org.jquantlib.instruments;

/**
 * Base class for basket option payoffs.
 *
 * <p>A {@code BasketPayoff} wraps a base single-asset {@link Payoff} (typically
 * a {@link PlainVanillaPayoff}) and defines how the values of multiple underlying assets are combined into a single
 * scalar before being fed to the base payoff. Concrete subclasses ({@link MinBasketPayoff}, {@link MaxBasketPayoff},
 * {@link AverageBasketPayoff}, {@link SpreadBasketPayoff}) implement {@link #accumulate(double[])}.</p>
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/instruments/basketoption.hpp::BasketPayoff}.</p>
 *
 * <p>JDK 25 sealed (JEP 409): permits the four v1.42.1 basket combinators
 * (min, max, average, spread). All sub-types live in this package.</p>
 *
 * @author Jose Moya
 */
public abstract sealed class BasketPayoff extends Payoff
        permits MinBasketPayoff, MaxBasketPayoff, AverageBasketPayoff, SpreadBasketPayoff {

    //
    // protected fields
    //

    private final Payoff basePayoff;

    //
    // public constructors
    //

    public BasketPayoff(final Payoff basePayoff) {
        this.basePayoff = basePayoff;
    }

    //
    // public methods
    //

    public Payoff basePayoff() {
        return basePayoff;
    }

    /**
     * Combine the array of underlying values into a single scalar.
     *
     * <p>Subclasses define the basket function (min/max/avg/spread).</p>
     *
     * @param a array of underlying spot values at exercise
     * @return scalar to feed into the base payoff
     */
    public abstract double accumulate(double[] a);

    /**
     * Evaluate the payoff against an array of underlying values.
     *
     * <p>Equivalent to {@code basePayoff(accumulate(a))} in the C++
     * {@code Real operator()(const Array &a) const} overload.</p>
     */
    public double get(final double[] a) {
        return basePayoff.get(accumulate(a));
    }

    //
    // overrides Payoff
    //

    @Override
    public String name() /* @ReadOnly */ {
        return basePayoff.name();
    }

    @Override
    public String description() /* @ReadOnly */ {
        return basePayoff.description();
    }

    /**
     * Single-scalar form: delegates to the base payoff.
     */
    @Override
    public double get(final double price) /* @ReadOnly */ {
        return basePayoff.get(price);
    }
}
