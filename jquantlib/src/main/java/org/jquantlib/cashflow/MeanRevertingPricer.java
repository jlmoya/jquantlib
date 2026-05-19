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

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;

/**
 * Mix-in for CMS coupon pricers parameterised by a mean-reversion quote.
 * <p>
 * Mirrors C++ QuantLib v1.42.1 {@code MeanRevertingPricer} in {@code ql/cashflows/couponpricer.hpp}:
 * <pre>{@code
 * class MeanRevertingPricer {
 *   public:
 *     virtual Real meanReversion() const = 0;
 *     virtual void setMeanReversion(const Handle<Quote>&) = 0;
 *     virtual ~MeanRevertingPricer() = default;
 * };
 * }</pre>
 * <p>
 * Implemented by Hagan-style CMS pricers (e.g. {@code AnalyticHaganPricer}, {@code NumericHaganPricer},
 * {@code LinearTsrPricer}) that calibrate to CMS market quotes via a mean-reversion parameter.
 */
public interface MeanRevertingPricer {

    /** @return current mean-reversion value. */
    double meanReversion();

    /** Replace the mean-reversion quote handle. */
    void setMeanReversion(Handle< Quote > meanReversion);
}
