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
 Copyright (C) 2006, 2008 Ferdinando Ametrano
 Copyright (C) 2006 François du Vignaud
*/

package org.jquantlib.quotes;

import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.util.LazyObject;

/**
 * Quote for the Eurodollar-future implied standard deviation.
 *
 * <p>Faithful port of {@code ql/quotes/eurodollarfuturesquote.hpp} +
 * {@code ql/quotes/eurodollarfuturesquote.cpp} v1.42.1
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>Given a futures-price forward handle, a call-price handle and a put-price
 * handle (plus a futures {@code strike} quoted in price terms), inverts the
 * Black 1976 formula to recover the implied standard deviation of the
 * corresponding rate. The strike and forward are mapped from price space to
 * rate space as {@code 100 - x}; the option type used in the inversion is the
 * out-of-the-money side: a put price gives a {@code Call} on the rate when
 * {@code strikeRate > forwardRate}, otherwise a call price gives a {@code Put}.
 *
 * <p>Unlike its C++ counterpart, this class does <b>not</b> extend {@link Quote}
 * — Java single inheritance forces a choice between {@link Quote} and
 * {@link LazyObject}. Following the convention used by {@link ImpliedStdDevQuote}
 * (and JQuantLib's broader migration pattern for {@code Quote}+{@code LazyObject}
 * classes), we extend {@link LazyObject} and expose the {@link Quote}-style API
 * ({@code value()}, {@code isValid()}) directly.
 *
 * @see Quote
 * @see LazyObject
 * @see BlackFormula#blackFormulaImpliedStdDev
 */
public class EurodollarFuturesImpliedStdDevQuote extends LazyObject {

    private static final double DISCOUNT = 1.0;
    private static final double DISPLACEMENT = 0.0;

    private double impliedStdev_;
    private final double strike_;
    private final double accuracy_;
    @SuppressWarnings("unused")
    private final int maxIter_;
    private final Handle<? extends Quote> forward_;
    private final Handle<? extends Quote> callPrice_;
    private final Handle<? extends Quote> putPrice_;

    public EurodollarFuturesImpliedStdDevQuote(final Handle<? extends Quote> forward,
                                               final Handle<? extends Quote> callPrice,
                                               final Handle<? extends Quote> putPrice,
                                               final double strike) {
        this(forward, callPrice, putPrice, strike, 0.15, 1.0e-6, 100);
    }

    public EurodollarFuturesImpliedStdDevQuote(final Handle<? extends Quote> forward,
                                               final Handle<? extends Quote> callPrice,
                                               final Handle<? extends Quote> putPrice,
                                               final double strike,
                                               final double guess) {
        this(forward, callPrice, putPrice, strike, guess, 1.0e-6, 100);
    }

    public EurodollarFuturesImpliedStdDevQuote(final Handle<? extends Quote> forward,
                                               final Handle<? extends Quote> callPrice,
                                               final Handle<? extends Quote> putPrice,
                                               final double strike,
                                               final double guess,
                                               final double accuracy) {
        this(forward, callPrice, putPrice, strike, guess, accuracy, 100);
    }

    public EurodollarFuturesImpliedStdDevQuote(final Handle<? extends Quote> forward,
                                               final Handle<? extends Quote> callPrice,
                                               final Handle<? extends Quote> putPrice,
                                               final double strike,
                                               final double guess,
                                               final double accuracy,
                                               final int maxIter) {
        super();
        this.impliedStdev_ = guess;
        this.strike_ = 100.0 - strike;
        this.accuracy_ = accuracy;
        this.maxIter_ = maxIter;
        this.forward_ = forward;
        this.callPrice_ = callPrice;
        this.putPrice_ = putPrice;
        // Mirror C++ registerWith(forward_); registerWith(callPrice_); registerWith(putPrice_).
        forward_.addObserver(this);
        callPrice_.addObserver(this);
        putPrice_.addObserver(this);
    }

    public double value() {
        calculate();
        return impliedStdev_;
    }

    public boolean isValid() {
        if (forward_.empty() || !forward_.currentLink().isValid()) {
            return false;
        }
        final double forwardValue = 100.0 - forward_.currentLink().value();
        if (strike_ > forwardValue) {
            return !putPrice_.empty() && putPrice_.currentLink().isValid();
        } else {
            return !callPrice_.empty() && callPrice_.currentLink().isValid();
        }
    }

    @Override
    protected void performCalculations() {
        final double forwardValue = 100.0 - forward_.currentLink().value();
        // Note: JQuantLib's BlackFormula.blackFormulaImpliedStdDev hard-codes
        // maxIterations to 100 internally (BlackFormula.java:359) and does not
        // expose the maxIter parameter. v1.42.1 C++ default is also 100; only a
        // caller passing a non-default maxIter would observe a divergence.
        // The Java signature order is (optionType, strike, forward, blackPrice,
        // discount, guess, accuracy, displacement) — note displacement is last,
        // after accuracy, unlike the C++ argument order.
        if (strike_ > forwardValue) {
            impliedStdev_ = BlackFormula.blackFormulaImpliedStdDev(
                    Option.Type.Call, strike_, forwardValue, putPrice_.currentLink().value(),
                    DISCOUNT, impliedStdev_, accuracy_, DISPLACEMENT);
        } else {
            impliedStdev_ = BlackFormula.blackFormulaImpliedStdDev(
                    Option.Type.Put, strike_, forwardValue, callPrice_.currentLink().value(),
                    DISCOUNT, impliedStdev_, accuracy_, DISPLACEMENT);
        }
    }
}
