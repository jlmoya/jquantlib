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
 Copyright (C) 2014 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.experimental.coupons;

import org.jquantlib.cashflow.FloatingRateCouponPricer;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;

/**
 * Base pricer for vanilla CMS-spread coupons.
 * <p>
 * Subclasses are expected to provide a concrete pricing model for the spread
 * (e.g. {@link LognormalCmsSpreadPricer}, which integrates a Brigo-style
 * bivariate-lognormal payoff via Gauss-Hermite quadrature).
 * <p>
 * Port of C++ QuantLib v1.42.1
 * {@code ql/experimental/coupons/cmsspreadcoupon.hpp} (the
 * {@code CmsSpreadCouponPricer} class lines 141-161).
 *
 * @author Peter Caspers (C++ original)
 */
public abstract class CmsSpreadCouponPricer extends FloatingRateCouponPricer {

    private Handle<? extends Quote> correlation_;


    //
    // public constructors
    //

    public CmsSpreadCouponPricer() {
        this(new Handle<Quote>());
    }

    public CmsSpreadCouponPricer(final Handle<? extends Quote> correlation) {
        this.correlation_ = correlation;
        if (correlation_ != null && !correlation_.empty()) {
            correlation_.addObserver(this);
        }
    }


    //
    // public methods
    //

    public Handle<? extends Quote> correlation() {
        return correlation_;
    }

    public void setCorrelation(final Handle<? extends Quote> correlation) {
        if (correlation_ != null && !correlation_.empty()) {
            correlation_.deleteObserver(this);
        }
        this.correlation_ = correlation;
        if (correlation_ != null && !correlation_.empty()) {
            correlation_.addObserver(this);
        }
        update();
    }
}
