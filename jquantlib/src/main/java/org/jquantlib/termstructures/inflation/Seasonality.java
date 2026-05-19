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
 Copyright (C) 2008 Piero Del Boca
 Copyright (C) 2009 Chris Kenyon
 Copyright (C) 2015 Bernd Lewerenz

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.inflation;

import org.jquantlib.lang.annotation.Rate;
import org.jquantlib.termstructures.InflationTermStructure;
import org.jquantlib.time.Date;

/**
 * A transformation of an existing inflation swap rate.
 *
 * <p>This is an abstract class containing {@code correctXXXRate} methods that
 * return rates with the seasonality correction. Currently only the price multiplicative version is implemented (see
 * {@link MultiplicativePriceSeasonality}); this covers stationary (1-year) and non-stationary (multi-year) seasonality
 * depending on how many years of factors are given. Seasonality is piecewise constant, hence it works with
 * un-interpolated inflation indices.
 *
 * <p>A seasonality assumption can be used to fill in inflation swap curves
 * between maturities that are usually given in integer numbers of years (e.g., 8, 9, 10, 15, 20). Historical
 * seasonality may be observed in reported CPI values, alternatively it may be affected by known future events (e.g.,
 * announced changes in VAT rates). Thus seasonality may be stationary or non-stationary.
 *
 * <p>Mirrors C++ {@code QuantLib::Seasonality} at v1.42.1
 * (termstructures/inflation/seasonality.{hpp,cpp}).
 *
 * @author JQuantLib migration team (Phase 2q C.2)
 */
public abstract class Seasonality {

    /** Apply the seasonality correction to a zero-coupon inflation rate. */
    public abstract /*@Rate*/ double correctZeroRate(final Date d, final @Rate double r,
            final InflationTermStructure iTS);

    /** Apply the seasonality correction to a year-on-year inflation rate. */
    public abstract /*@Rate*/ double correctYoYRate(final Date d, final @Rate double r,
            final InflationTermStructure iTS);

    /**
     * Test whether multi-year seasonality is consistent with a given inflation term structure. The default
     * implementation returns true; subclasses may override (e.g., {@link MultiplicativePriceSeasonality} which checks
     * that factors at whole years from the curve base date are equal).
     *
     * <p>Mirrors C++ {@code Seasonality::isConsistent} default impl
     * (seasonality.cpp:29-31).
     */
    public boolean isConsistent(final InflationTermStructure iTS) {
        return true;
    }
}
