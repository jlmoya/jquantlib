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
 Copyright (C) 2026 Eugene Toder
*/

package org.jquantlib.quotes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import org.jquantlib.QL;
import org.jquantlib.util.Observer;

/**
 * Multi-input composite quote — applies a user-supplied array function to the values of a list of underlying quotes.
 *
 * <p>Faithful port of {@code ql/quotes/multicompositequote.hpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>In Java the C++ template parameter {@code ArrayFunction} is realised as a
 * {@link ToDoubleFunction}{@code <double[]>}. Construction registers this quote as an observer on each underlying
 * handle; {@link #update()} invalidates the cached value and notifies downstream observers.
 *
 * @see Quote
 * @see CompositeQuote (would be the binary equivalent — not yet ported)
 */
public class MultiCompositeQuote extends Quote implements Observer {

    private final List<Handle<? extends Quote>> elements_;
    private final ToDoubleFunction<double[]> f_;
    private double value_ = Double.NaN; // serves as Null<Real> sentinel: NaN means "not computed yet"

    public MultiCompositeQuote(final List<Handle<? extends Quote>> elements,
                               final ToDoubleFunction<double[]> f) {
        this.elements_ = new ArrayList<Handle<? extends Quote>>(elements);
        this.f_ = f;
        for (final Handle<? extends Quote> elem : elements_) {
            elem.addObserver(this);
        }
    }

    /** Inspector — exposes the i-th underlying quote's current value. */
    public double inputValue(final int i) {
        return elements_.get(i).currentLink().value();
    }

    @Override
    public double value() {
        if (Double.isNaN(value_)) {
            QL.ensure(isValid(), "invalid MultiCompositeQuote");
            final double[] args = new double[elements_.size()];
            for (int i = 0; i < args.length; ++i) {
                args[i] = elements_.get(i).currentLink().value();
            }
            value_ = f_.applyAsDouble(args);
        }
        return value_;
    }

    @Override
    public boolean isValid() {
        for (final Handle<? extends Quote> elem : elements_) {
            if (elem.empty() || !elem.currentLink().isValid()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void update() {
        value_ = Double.NaN;
        notifyObservers();
    }
}
