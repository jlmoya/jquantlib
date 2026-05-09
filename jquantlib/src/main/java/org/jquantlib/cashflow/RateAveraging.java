/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2017 Ferdinando Ametrano

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.cashflow;

/**
 * Rate-averaging method for multi-fixing coupons (e.g., overnight
 * compounded vs simple-averaged coupons).
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/rateaveraging.hpp}
 * {@code RateAveraging::Type}.
 */
public final class RateAveraging {

    private RateAveraging() {}

    public enum Type {
        /**
         * Simple averaging: amount of interest is the sum of sub-rates
         * applied to the principal.
         */
        Simple,
        /**
         * Compound averaging: each sub-rate is applied to principal plus
         * accumulated unpaid interest.
         */
        Compound
    }
}
