/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2008 Allen Kuo
 Copyright (C) 2021 Ralf Konrad Eckel

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.experimental.callablebonds;

import org.jquantlib.model.shortrate.ShortRateModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.TimeGrid;

/**
 * Numerical lattice engine for callable zero-coupon bonds.
 * <p>
 * Port of C++ v1.42.1
 * {@code ql/experimental/callablebonds/treecallablebondengine.{hpp,cpp}}
 * (the {@code TreeCallableZeroCouponBondEngine} portion).
 * <p>
 * Behaviorally identical to {@link TreeCallableFixedRateBondEngine}; exists as
 * a separate class for source-level parity with the C++ API.
 */
public class TreeCallableZeroCouponBondEngine extends TreeCallableFixedRateBondEngine {

    public TreeCallableZeroCouponBondEngine(final ShortRateModel model, final int timeSteps,
            final Handle<YieldTermStructure> termStructure) {
        super(model, timeSteps, termStructure);
    }

    public TreeCallableZeroCouponBondEngine(final ShortRateModel model, final TimeGrid timeGrid,
            final Handle<YieldTermStructure> termStructure) {
        super(model, timeGrid, termStructure);
    }
}
