/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Black-formula callable zero-coupon bond engine.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/experimental/callablebonds/blackcallablebondengine.{hpp,cpp}}
 * (the {@code BlackCallableZeroCouponBondEngine} portion).
 * <p>
 * Behaviorally identical to {@link BlackCallableFixedRateBondEngine}; exists
 * as a separate class for source-level parity with the C++ API.
 */
public class BlackCallableZeroCouponBondEngine extends BlackCallableFixedRateBondEngine {

    public BlackCallableZeroCouponBondEngine(final Handle<Quote> fwdYieldVol,
            final Handle<YieldTermStructure> discountCurve) {
        super(fwdYieldVol, discountCurve);
    }

    /**
     * Java port: factory matching the C++ ctor that takes a
     * {@code Handle<CallableBondVolatilityStructure>}. Because Java type
     * erasure forbids two ctors differing only in the {@link Handle} type
     * parameter, we expose this as a static factory.
     */
    public static BlackCallableZeroCouponBondEngine fromVolStructure(
            final Handle<CallableBondVolatilityStructure> yieldVolStructure,
            final Handle<YieldTermStructure> discountCurve) {
        return new BlackCallableZeroCouponBondEngine(yieldVolStructure, discountCurve, true);
    }

    private BlackCallableZeroCouponBondEngine(
            final Handle<CallableBondVolatilityStructure> yieldVolStructure,
            final Handle<YieldTermStructure> discountCurve, final boolean marker) {
        super(yieldVolStructure, discountCurve, marker);
    }
}
