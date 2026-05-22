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
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2013 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.model.shortrate.onefactormodels.OneFactorAffineModel;
import org.jquantlib.model.shortrate.onefactormodels.TermStructureConsistentModel;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Jamshidian decomposition swaption engine for one-factor affine short-rate models.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/pricingengines/swaption/jamshidianswaptionengine.{hpp,cpp}}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code rStarFinder::operator()(Rate)} is exposed in Java as the
 *     {@link Ops.DoubleOp#op(double)} contract on the {@link RStarFinder}
 *     inner class (Phase 2c precedent).
 * <li>The Java {@link Swaption.ArgumentsImpl} stores the underlying
 *     {@link VanillaSwap} reference directly (see Swaption.java). Fixed-leg
 *     coupons / pay dates / nominal therefore come from
 *     {@code args.swap.fixedLeg()} and {@code args.swap.nominal()} rather than
 *     from the {@code fixedCoupons}/{@code fixedPayDates}/{@code fixedResetDates}
 *     projections that {@link VanillaSwap.ArgumentsImpl} would otherwise
 *     populate. This avoids depending on
 *     {@link VanillaSwap#setupArguments(org.jquantlib.pricingengines.PricingEngine.Arguments)}
 *     (whose projection chain the Phase 2e WI-3 retro-note flagged as still
 *     having an inverted {@code isAssignableFrom} check).
 * <li>Only {@link OneFactorAffineModel}s that also implement
 *     {@link TermStructureConsistentModel} (e.g. {@link HullWhite}) are
 *     supported by the {@code referenceDate}/{@code dayCounter} discovery
 *     path. For models that do not, an explicit
 *     {@code Handle<YieldTermStructure>} must be supplied via the two-arg
 *     constructor (mirrors the C++ fallback branch).
 * </ul>
 *
 * <p><strong>Settlement contract.</strong> Mirrors C++: rejects
 * {@link Settlement.Method#ParYieldCurve} (cash-settled with par-yield-curve
 * NPV cannot be Jamshidian-priced). Accepts {@link Settlement.Type#Physical}
 * and {@link Settlement.Type#Cash} with
 * {@link Settlement.Method#CollateralizedCashPrice}.
 *
 * <p><strong>Caveat.</strong> The engine assumes that the exercise date
 * equals the start date of the passed swap unless the model provides its own
 * {@code discountBondOption} with start-delay support (e.g. Hull-White's
 * 5-argument overload).
 *
 * @see Swaption
 * @see HullWhite
 */
public class JamshidianSwaptionEngine extends Swaption.EngineImpl {

    private final OneFactorAffineModel model_;
    private final Handle< YieldTermStructure > termStructure_;

    /**
     * Build with model only; the model must implement {@link TermStructureConsistentModel} so a reference date / day
     * counter can be discovered.
     */
    public JamshidianSwaptionEngine(final OneFactorAffineModel model) {
        this(model, new Handle< YieldTermStructure >());
    }

    /**
     * Build with explicit term structure. Required when the model is not {@link TermStructureConsistentModel}.
     */
    public JamshidianSwaptionEngine(final OneFactorAffineModel model,
            final Handle< YieldTermStructure > termStructure) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.termStructure_ = termStructure;
        this.model_.addObserver(this);
        if ( this.termStructure_ != null && !this.termStructure_.empty() ) {
            this.termStructure_.addObserver(this);
        }
    }

    public OneFactorAffineModel model() {
        return model_;
    }

    public Handle< YieldTermStructure > termStructure() {
        return termStructure_;
    }

    //
    // implements PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        final Swaption.ResultsImpl results = (Swaption.ResultsImpl) results_;

        // C++ QL_REQUIREs (jamshidianswaptionengine.cpp lines 59-72).
        QL.require(args.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with " + "JamshidianSwaptionEngine");

        final Exercise exercise = args.exercise;
        QL.require(exercise.type() == Exercise.Type.European,
                "cannot use the Jamshidian decomposition on exotic swaptions");

        final VanillaSwap swap = args.swap;
        QL.require(swap.spread() == 0.0, "non zero spread (" + swap.spread() + ") not allowed");

        final double nominal = swap.nominal();
        QL.require(!Double.isNaN(nominal) && nominal != Constants.NULL_REAL,
                "non-constant nominals are not supported yet");

        // Reference date + day counter. C++ casts model to
        // TermStructureConsistentModel; Java mirrors via instanceof.
        final Date referenceDate;
        final DayCounter dayCounter;
        if (model_ instanceof TermStructureConsistentModel tsm) {
            referenceDate = tsm.termStructure().currentLink().referenceDate();
            dayCounter = tsm.termStructure().currentLink().dayCounter();
        } else {
            QL.require(termStructure_ != null && !termStructure_.empty(),
                    "no term structure available — model is not " + "TermStructureConsistentModel and no fallback "
                            + "Handle<YieldTermStructure> was provided");
            referenceDate = termStructure_.currentLink().referenceDate();
            dayCounter = termStructure_.currentLink().dayCounter();
        }

        // Collect fixed-leg coupon amounts and pay times directly from the
        // underlying swap (see class-level deviation note).
        final Leg fixedLeg = swap.fixedLeg();
        final int n = fixedLeg.size();
        final double[] amounts = new double[n];
        final double[] fixedPayTimes = new double[n];
        for ( int i = 0; i < n; i++ ) {
            final FixedRateCoupon coupon = (FixedRateCoupon) fixedLeg.get(i);
            amounts[i] = coupon.amount();
            fixedPayTimes[i] = dayCounter.yearFraction(referenceDate, coupon.date());
        }
        // C++: amounts.back() += arguments_.nominal — the terminal exchange
        // of notional is implied by the swap leg in QL but added explicitly
        // here for the Jamshidian decomposition (each fixed cashflow becomes
        // an option on a discount bond, and the final one carries the
        // notional repayment).
        amounts[n - 1] += nominal;

        // Maturity (= exercise date) and value-time (= first fixed reset).
        final Date exerciseDate = exercise.date(0);
        final double maturity = dayCounter.yearFraction(referenceDate, exerciseDate);
        final FixedRateCoupon firstCoupon = (FixedRateCoupon) fixedLeg.get(0);
        final double valueTime = dayCounter.yearFraction(referenceDate, firstCoupon.accrualStartDate());

        // Solve for r* such that the sum of forward bond prices equals the
        // strike (= nominal). C++ uses Brent on [-10, +10] with guess 0.05.
        final RStarFinder finder = new RStarFinder(model_, nominal, maturity, valueTime, fixedPayTimes, amounts);
        final Brent solver = new Brent();
        final double minStrike = -10.0;
        final double maxStrike = 10.0;
        solver.setMaxEvaluations(10000);
        solver.setLowerBound(minStrike);
        solver.setUpperBound(maxStrike);
        final double rStar = solver.solve(finder, 1.0e-8, 0.05, minStrike, maxStrike);

        // C++: payer -> Option::Put on the bond, receiver -> Option::Call.
        final Option.Type w = (swap.type() == VanillaSwap.Type.Payer) ? Option.Type.Put : Option.Type.Call;

        double value = 0.0;
        final double B = model_.discountBond(maturity, valueTime, rStar);
        for ( int i = 0; i < n; i++ ) {
            final double fixedPayTime = fixedPayTimes[i];
            final double strike = model_.discountBond(maturity, fixedPayTime, rStar) / B;
            // Prefer the start-delay (5-arg) overload when the model exposes
            // one (Hull-White does); fall back to the 4-arg AffineModel
            // contract otherwise.
            final double dboValue = discountBondOptionWithDelay(w, strike, maturity, valueTime, fixedPayTime);
            value += amounts[i] * dboValue;
        }
        results.value = value;
    }

    /**
     * Dispatches to the 5-argument start-delay
     * {@code discountBondOption(type, strike, maturity, bondStart, bondMaturity)} when the concrete model provides one
     * (e.g. {@link HullWhite}); falls back to the 4-argument
     * {@code AffineModel.discountBondOption(type, strike, maturity, bondMaturity)} contract otherwise.
     */
    private double discountBondOptionWithDelay(final Option.Type type, final double strike, final double maturity,
            final double bondStart, final double bondMaturity) {
        if ( model_ instanceof HullWhite ) {
            return ((HullWhite) model_).discountBondOption(type, strike, maturity, bondStart, bondMaturity);
        }
        // Fallback: 4-arg AffineModel contract (no start delay).
        return model_.discountBondOption(type, strike, maturity, bondMaturity);
    }

    //
    // inner classes
    //

    /**
     * Brent cost function: returns {@code strike - sum(amounts[i] * discountBond(maturity, times[i], x) / B)} where
     * {@code B = discountBond(maturity, valueTime, x)}. The root r* is the short rate at which the underlying coupon
     * bond equals the strike at exercise. Mirrors C++ {@code JamshidianSwaptionEngine::rStarFinder} (cpp lines 27-55).
     */
    static final class RStarFinder implements Ops.DoubleOp {

        private final OneFactorAffineModel model_;
        private final double strike_;
        private final double maturity_;
        private final double valueTime_;
        private final double[] times_;
        private final double[] amounts_;

        RStarFinder(final OneFactorAffineModel model, final double nominal, final double maturity,
                final double valueTime, final double[] fixedPayTimes, final double[] amounts) {
            this.model_ = model;
            this.strike_ = nominal;
            this.maturity_ = maturity;
            this.valueTime_ = valueTime;
            this.times_ = fixedPayTimes;
            this.amounts_ = amounts;
        }

        @Override
        public double op(final double x) {
            double value = strike_;
            final double B = model_.discountBond(maturity_, valueTime_, x);
            for ( int i = 0; i < times_.length; i++ ) {
                final double dbValue = model_.discountBond(maturity_, times_[i], x) / B;
                value -= amounts_[i] * dbValue;
            }
            return value;
        }
    }
}
