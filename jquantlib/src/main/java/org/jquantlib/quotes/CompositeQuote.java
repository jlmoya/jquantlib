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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
*/

package org.jquantlib.quotes;

import java.util.function.DoubleBinaryOperator;

import org.jquantlib.QL;
import org.jquantlib.util.Observer;

/**
 * Market element whose value depends on two other market elements.
 *
 * <p>Faithful port of {@code ql/quotes/compositequote.hpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The C++ template parameter {@code BinaryFunction} (typically a free
 * function of signature {@code Real(Real,Real)}) maps to {@link DoubleBinaryOperator} in Java.
 * Construction registers this quote as an observer on both underlying
 * handles; {@link #update()} invalidates the cached value and notifies
 * downstream observers, matching C++ {@code value_ = Null<Real>(); notifyObservers();}.
 *
 * @see Quote
 * @see MultiCompositeQuote — the N-ary equivalent (ql/quotes/multicompositequote.hpp)
 */
public class CompositeQuote extends Quote implements Observer {

    private final Handle<? extends Quote> element1_;
    private final Handle<? extends Quote> element2_;
    private final DoubleBinaryOperator f_;
    private double value_ = Double.NaN; // serves as Null<Real> sentinel: NaN means "not computed yet"

    public CompositeQuote(final Handle<? extends Quote> element1,
                          final Handle<? extends Quote> element2,
                          final DoubleBinaryOperator f) {
        this.element1_ = element1;
        this.element2_ = element2;
        this.f_ = f;
        element1_.addObserver(this);
        element2_.addObserver(this);
    }

    /** Inspector — first underlying quote's current value. Mirrors C++ {@code value1()}. */
    public double value1() {
        return element1_.currentLink().value();
    }

    /** Inspector — second underlying quote's current value. Mirrors C++ {@code value2()}. */
    public double value2() {
        return element2_.currentLink().value();
    }

    @Override
    public double value() {
        if (Double.isNaN(value_)) {
            QL.ensure(isValid(), "invalid CompositeQuote");
            value_ = f_.applyAsDouble(element1_.currentLink().value(), element2_.currentLink().value());
        }
        return value_;
    }

    @Override
    public boolean isValid() {
        return !element1_.empty() && !element2_.empty()
                && element1_.currentLink().isValid() && element2_.currentLink().isValid();
    }

    @Override
    public void update() {
        value_ = Double.NaN;
        notifyObservers();
    }
}
