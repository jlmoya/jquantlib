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

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.pricingengines.swaption.gaussian1d;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Settlement;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gaussian1dModel;
import org.jquantlib.time.Date;

/**
 * Swaption engine using Jamshidian's decomposition under a one-factor
 * Gaussian short-rate model ({@link Gaussian1dModel}).
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/swaption/gaussian1djamshidianswaptionengine.{hpp,cpp}}
 * (commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}). Phase 2j WI-3.1.
 *
 * <h3>Algorithm</h3>
 * <p>For European payer (receiver) swaptions on coupon bonds, Jamshidian's
 * decomposition rewrites the swaption payoff as a portfolio of bond options
 * on zero-coupon bonds. The engine:
 * <ol>
 *   <li>Locates the critical short-rate {@code y*} at which the coupon bond
 *       equals the nominal at the exercise date by solving an equation via
 *       Brent's method.</li>
 *   <li>Converts each coupon to a bond-option strike
 *       {@code K_i = P(exercise, T_i; y*) / P(exercise, T_start; y*)}.</li>
 *   <li>Prices each bond option using
 *       {@link Gaussian1dModel#zerobondOption(Option.Type, Date, Date, Date, double)},
 *       which performs a cubic-spline Gaussian quadrature over the model's
 *       state-variable grid.</li>
 *   <li>Sums the weighted bond-option prices.</li>
 * </ol>
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>C++ accesses {@code arguments_.fixedCoupons}, {@code fixedPayDates}, and
 *       {@code fixedResetDates} which are projected into {@code Swaption::arguments}
 *       via its {@code FixedVsFloatingSwap::arguments} base. In Java,
 *       {@link Swaption.ArgumentsImpl} does not extend
 *       {@link VanillaSwap.ArgumentsImpl} (different inheritance branch), so
 *       fixed-leg data is read directly from {@code args.swap.fixedLeg()}.</li>
 *   <li>C++ {@code rStarFinder::operator()(Rate)} is the cost function for the
 *       Brent solver; Java exposes it as the {@link Ops.DoubleOp#op(double)}
 *       contract on the {@link RStarFinder} inner class (Phase 2f WI-2 precedent).</li>
 * </ul>
 *
 * @see Gaussian1dModel#zerobondOption(Option.Type, Date, Date, Date, double)
 * @see JamshidianSwaptionEngine (Phase 1 / Hull-White specialisation)
 */
public class Gaussian1dJamshidianSwaptionEngine extends Swaption.EngineImpl {

    private final Gaussian1dModel model_;

    /**
     * Construct with a Gaussian1d model. The model supplies its own term
     * structure, so no separate {@code Handle<YieldTermStructure>} is needed.
     *
     * @param model non-null Gaussian1dModel (typically a {@link org.jquantlib.model.shortrate.onefactormodels.gaussian1d.Gsr} instance)
     */
    public Gaussian1dJamshidianSwaptionEngine(final Gaussian1dModel model) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.model_.addObserver(this);
    }

    /** @return the underlying Gaussian1d model. */
    public Gaussian1dModel model() {
        return model_;
    }

    // ──────────────────────────────────────────────────────────────────────
    // PricingEngine implementation
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public void calculate() {
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        final Swaption.ResultsImpl results = (Swaption.ResultsImpl) results_;

        // QL_REQUIREs from C++ gaussian1djamshidianswaptionengine.cpp lines 62-76
        QL.require(args.settlementMethod != Settlement.Method.ParYieldCurve,
                "cash settled (ParYieldCurve) swaptions not priced with "
                + "Gaussian1dJamshidianSwaptionEngine");

        final Exercise exercise = args.exercise;
        QL.require(exercise.type() == Exercise.Type.European,
                "cannot use the Jamshidian decomposition on exotic swaptions");

        final VanillaSwap swap = args.swap;
        QL.require(swap.spread() == 0.0,
                "non zero spread (" + swap.spread() + ") not allowed");

        final double nominal = swap.nominal();

        // C++ QL_REQUIRE(arguments_.nominal != Null<Real>(), ...) — Java uses
        // Double.isNaN or NULL_REAL sentinel check.
        QL.require(!Double.isNaN(nominal),
                "non-constant nominals are not supported yet");

        // Build the amounts[] vector = fixed coupons with notional added to last.
        // Mirrors C++ lines 83-85:
        //   std::vector<Real> amounts(arguments_.fixedCoupons);
        //   amounts.back() += arguments_.nominal;
        final Leg fixedLeg = swap.fixedLeg();
        final int n = fixedLeg.size();

        final double[] amounts = new double[n];
        final Date[] fixedPayDates = new Date[n];
        final Date[] fixedResetDates = new Date[n];
        for (int i = 0; i < n; i++) {
            final FixedRateCoupon coupon = (FixedRateCoupon) fixedLeg.get(i);
            amounts[i] = coupon.amount();
            fixedPayDates[i] = coupon.date();
            fixedResetDates[i] = coupon.accrualStartDate();
        }
        amounts[n - 1] += nominal;

        // Find startIndex = upper_bound(fixedResetDates, exercise.date(0) - 1)
        // i.e. first index i where fixedResetDates[i] >= exercise.date(0).
        // C++ lines 86-89:
        //   Size startIndex = std::upper_bound(arguments_.fixedResetDates.begin(),
        //       arguments_.fixedResetDates.end(),
        //       arguments_.exercise->date(0) - 1) -
        //       arguments_.fixedResetDates.begin();
        final Date exerciseDate = exercise.date(0);
        final Date searchKey = exerciseDate.sub(1);  // exerciseDate - 1 day
        int startIndex = n;  // default: all coupons before exercise
        for (int i = 0; i < n; i++) {
            if (fixedResetDates[i].gt(searchKey)) {
                startIndex = i;
                break;
            }
        }

        // Solve for y* (rStar in C++ comment = "yStar") via Brent (lines 92-103)
        final Date valueDate = fixedResetDates[startIndex];
        final RStarFinder finder = new RStarFinder(
                model_, nominal, exerciseDate, valueDate,
                fixedPayDates, amounts, startIndex);
        final Brent solver = new Brent();
        final double minStrike = -8.0;
        final double maxStrike =  8.0;
        solver.setMaxEvaluations(10000);
        solver.setLowerBound(minStrike);
        solver.setUpperBound(maxStrike);
        final double rStar = solver.solve(finder, 1.0e-8, 0.0, minStrike, maxStrike);

        // Payer swaption → option on put on each bond (lower bond price = exercise)
        // C++ lines 105-107:
        //   Option::Type w = (arguments_.type == Swap::Payer)
        //       ? Option::Put : Option::Call;
        final Option.Type w = (swap.type() == VanillaSwap.Type.Payer)
                ? Option.Type.Put : Option.Type.Call;

        // Sum weighted bond-option prices (C++ lines 109-123)
        double value = 0.0;
        for (int i = startIndex; i < n; i++) {
            // K_i = P(T_start, T_i; y*) / P(T_start, T_start; y*)
            //     = zerobond(T_i, exerciseDate, y*) / zerobond(valueDate, exerciseDate, y*)
            final double strike =
                    model_.zerobond(fixedPayDates[i], exerciseDate, rStar)
                    / model_.zerobond(valueDate, exerciseDate, rStar);

            final double dboValue = model_.zerobondOption(
                    w, exerciseDate, valueDate, fixedPayDates[i], strike);

            value += amounts[i] * dboValue;
        }
        results.value = value;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Inner classes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Brent cost function: f(y) = nominal - sum_{i=startIndex}^{n-1}
     *     amounts[i] * zerobond(T_i, exerciseDate, y) / zerobond(valueDate, exerciseDate, y).
     *
     * <p>Root y* is the standardized short rate at which the coupon bond
     * present-valued at the exercise date equals the nominal (strike).
     * Mirrors C++ {@code Gaussian1dJamshidianSwaptionEngine::rStarFinder}.
     */
    static final class RStarFinder implements Ops.DoubleOp {

        private final Gaussian1dModel model_;
        private final double strike_;       // = nominal
        private final Date maturityDate_;   // = exercise date
        private final Date valueDate_;      // = fixedResetDates[startIndex]
        private final int startIndex_;
        private final Date[] times_;        // = fixedPayDates
        private final double[] amounts_;

        RStarFinder(
                final Gaussian1dModel model,
                final double nominal,
                final Date maturityDate,
                final Date valueDate,
                final Date[] fixedPayDates,
                final double[] amounts,
                final int startIndex) {
            this.model_ = model;
            this.strike_ = nominal;
            this.maturityDate_ = maturityDate;
            this.valueDate_ = valueDate;
            this.startIndex_ = startIndex;
            this.times_ = fixedPayDates;
            this.amounts_ = amounts;
        }

        /**
         * C++ operator()(Rate y):
         * <pre>
         *   value = strike
         *   for i in [startIndex, size):
         *       dbValue = zerobond(T_i, maturity, y) / zerobond(valueDate, maturity, y)
         *       value -= amounts[i] * dbValue
         *   return value
         * </pre>
         */
        @Override
        public double op(final double y) {
            double value = strike_;
            final double B = model_.zerobond(valueDate_, maturityDate_, y);
            final int size = times_.length;
            for (int i = startIndex_; i < size; i++) {
                final double dbValue = model_.zerobond(times_[i], maturityDate_, y) / B;
                value -= amounts_[i] * dbValue;
            }
            return value;
        }
    }
}
