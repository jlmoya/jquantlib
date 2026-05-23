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
 Copyright (C) 2004 Mike Parker

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.QL;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;

/**
 * Swaption pricing engine for the two-factor additive Gaussian (G2++) model,
 * using the closed-form integral.
 * <p>
 * Port of C++ v1.42.1 {@code ql/pricingengines/swaption/g2swaptionengine.hpp}.
 * Prices a European swaption by numerically integrating
 * {@link G2#swaption(Swaption.ArgumentsImpl, double, double, int)} over the
 * {@code x}-process axis.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code GenericModelEngine<G2, Swaption::arguments,
 *     Swaption::results>} becomes a direct subclass of
 *     {@link Swaption.EngineImpl}; the {@link G2} model is held as a private
 *     final field and observed manually. This mirrors the pattern used by
 *     {@link FdG2SwaptionEngine} / {@link FdHullWhiteSwaptionEngine} — Java
 *     lacks the multiply-templated {@code GenericModelEngine} convenience
 *     base for the swaption argument/result pair.</li>
 * <li>The C++ engine reads {@code arguments_.swap} on a
 *     {@link VanillaSwap} (no OIS variant exists for G2 in v1.42.1); the Java
 *     port enforces this by checking {@code args.swap != null} and rejecting
 *     OIS-backed swaptions with a clear message.</li>
 * <li>{@code Settlement::Cash} is rejected (matches the C++ {@code QL_REQUIRE}
 *     at g2swaptionengine.hpp:51).</li>
 * </ul>
 *
 * <p><strong>Warning</strong> (mirrors C++): the engine assumes the exercise
 * date equals the start date of the passed swap.
 */
public class G2SwaptionEngine extends Swaption.EngineImpl {

    private final G2 model_;
    private final double range_;
    private final int intervals_;

    /**
     * @param model     the G2++ model
     * @param range     number of {@code sigma_x} the integration domain spans
     *                  on either side of the {@code mu_x} centre
     * @param intervals number of trapezoid-rule sub-intervals for the
     *                  inner {@link org.jquantlib.math.integrals.SegmentIntegral}
     */
    public G2SwaptionEngine(final G2 model, final double range, final int intervals) {
        super();
        QL.require(model != null, "no model specified");
        QL.require(range > 0.0, "range must be positive, " + range + " not allowed");
        QL.require(intervals > 0, "intervals must be positive, " + intervals + " not allowed");
        this.model_ = model;
        this.range_ = range;
        this.intervals_ = intervals;
        this.model_.addObserver(this);
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        final Swaption.ResultsImpl results = (Swaption.ResultsImpl) results_;

        QL.require(args.settlementType == Settlement.Type.Physical,
                "cash-settled swaptions not priced with G2 engine");
        QL.require(model_ != null, "no model specified");
        QL.require(args.swap != null,
                "G2SwaptionEngine requires a VanillaSwap underlying; OIS underlyings are not supported");

        // Adjust fixed rate for any floating-leg spread (G2 doesn't model the
        // spread itself); mirrors C++ g2swaptionengine.hpp:55-62.
        final VanillaSwap swap = args.swap;
        swap.setPricingEngine(new DiscountingSwapEngine(model_.termStructure()));
        final double correction = swap.spread() * Math.abs(swap.floatingLegBPS() / swap.fixedLegBPS());
        final double fixedRate = swap.fixedRate() - correction;

        results.value = model_.swaption(args, fixedRate, range_, intervals_);
    }
}
