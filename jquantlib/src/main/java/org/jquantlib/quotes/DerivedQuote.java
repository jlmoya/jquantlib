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

/*
 Copyright (C) 2006 Ferdinando Ametrano
 Copyright (C) 2006 François du Vignaud
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
*/

package org.jquantlib.quotes;

import java.util.function.DoubleUnaryOperator;

import org.jquantlib.QL;
import org.jquantlib.util.Observer;

/**
 * Market quote whose value depends on another quote.
 *
 * <p>Faithful port of {@code ql/quotes/derivedquote.hpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The C++ template parameter {@code UnaryFunction} (typically a free function
 * of signature {@code Real(Real)}) maps to {@link DoubleUnaryOperator} in Java,
 * the unary analogue of the {@link DoubleBinaryOperator} used by
 * {@link CompositeQuote}. Construction registers this quote as an observer on the
 * underlying handle; {@link #update()} invalidates the cached value and notifies
 * downstream observers, matching C++ {@code value_ = Null<Real>(); notifyObservers();}.
 *
 * @see Quote
 * @see CompositeQuote — the binary analogue (ql/quotes/compositequote.hpp)
 */
public class DerivedQuote extends Quote implements Observer {

    private final Handle<? extends Quote> element_;
    private final DoubleUnaryOperator f_;
    private double value_ = Double.NaN; // serves as Null<Real> sentinel: NaN means "not computed yet"

    public DerivedQuote(final Handle<? extends Quote> element, final DoubleUnaryOperator f) {
        this.element_ = element;
        this.f_ = f;
        // Mirror C++ registerWith(element_) — the handle's Link forwards notifications.
        element_.addObserver(this);
    }

    @Override
    public double value() {
        if (Double.isNaN(value_)) {
            QL.ensure(isValid(), "invalid DerivedQuote");
            value_ = f_.applyAsDouble(element_.currentLink().value());
        }
        return value_;
    }

    @Override
    public boolean isValid() {
        return !element_.empty() && element_.currentLink().isValid();
    }

    @Override
    public void update() {
        value_ = Double.NaN;
        notifyObservers();
    }
}
