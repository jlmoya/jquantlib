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
 Copyright (C) 2006, 2007, 2008 Ferdinando Ametrano
 Copyright (C) 2006 François du Vignaud
*/

package org.jquantlib.quotes;

import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.util.LazyObject;

/**
 * Quote for the implied standard deviation of an underlying.
 *
 * <p>Faithful port of {@code ql/quotes/impliedstddevquote.hpp} +
 * {@code ql/quotes/impliedstddevquote.cpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>A {@link LazyObject} subclass that solves the Black 1976 implied stdev
 * problem via {@link BlackFormula#blackFormulaImpliedStdDev}. Per the C++
 * implementation, if the solver throws the cached value falls back to 0.0
 * (no propagation to the caller).
 *
 * <p>Unlike its C++ counterpart, this class does <b>not</b> extend {@link Quote}
 * — Java single inheritance forces a choice. Following the convention used
 * by other lazy-object quotes (and by JQuantLib's broader migration pattern),
 * we extend {@link LazyObject} and expose the {@link Quote}-style API
 * ({@code value()}, {@code isValid()}) directly. Code that needs to register
 * this as a {@link Quote} can wrap it.
 *
 * @see Quote
 * @see LazyObject
 * @see BlackFormula#blackFormulaImpliedStdDev
 */
public class ImpliedStdDevQuote extends LazyObject {

    private static final double DISCOUNT = 1.0;
    private static final double DISPLACEMENT = 0.0;

    private final Option.Type optionType_;
    private final double strike_;
    private final double accuracy_;
    @SuppressWarnings("unused")
    private final int maxIter_;
    private final Handle<? extends Quote> forward_;
    private final Handle<? extends Quote> price_;

    private double impliedStdev_;

    public ImpliedStdDevQuote(final Option.Type optionType,
                              final Handle<? extends Quote> forward,
                              final Handle<? extends Quote> price,
                              final double strike,
                              final double guess) {
        this(optionType, forward, price, strike, guess, 1.0e-6, 100);
    }

    public ImpliedStdDevQuote(final Option.Type optionType,
                              final Handle<? extends Quote> forward,
                              final Handle<? extends Quote> price,
                              final double strike,
                              final double guess,
                              final double accuracy) {
        this(optionType, forward, price, strike, guess, accuracy, 100);
    }

    public ImpliedStdDevQuote(final Option.Type optionType,
                              final Handle<? extends Quote> forward,
                              final Handle<? extends Quote> price,
                              final double strike,
                              final double guess,
                              final double accuracy,
                              final int maxIter) {
        super();
        this.impliedStdev_ = guess;
        this.optionType_ = optionType;
        this.strike_ = strike;
        this.accuracy_ = accuracy;
        this.maxIter_ = maxIter;
        this.forward_ = forward;
        this.price_ = price;
        forward_.addObserver(this);
        price_.addObserver(this);
    }

    public double value() {
        calculate();
        return impliedStdev_;
    }

    public boolean isValid() {
        return !price_.empty() && !forward_.empty()
                && price_.currentLink().isValid() && forward_.currentLink().isValid();
    }

    @Override
    protected void performCalculations() {
        final double blackPrice = price_.currentLink().value();
        try {
            // Note: JQuantLib's BlackFormula.blackFormulaImpliedStdDev does not
            // expose the maxIter parameter (hard-coded to 100 inside, see
            // BlackFormula.java:386). v1.42.1 C++ default is also 100; only
            // tests passing a non-default maxIter would observe a divergence.
            impliedStdev_ = BlackFormula.blackFormulaImpliedStdDev(
                    optionType_,
                    strike_,
                    forward_.currentLink().value(),
                    blackPrice,
                    DISCOUNT,
                    impliedStdev_,
                    accuracy_,
                    DISPLACEMENT);
        } catch (final RuntimeException e) {
            impliedStdev_ = 0.0;
        }
    }
}
