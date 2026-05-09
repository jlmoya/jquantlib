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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2011, 2012, 2013, 2023 Andre Miemiec
 Copyright (C) 2012 Samuel Tebege

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.swaptions;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.CashFlows;
import org.jquantlib.cashflow.FixedRateCoupon;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.LUDecomposition;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.math.solvers1D.Bisection;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.swap.DiscountingSwapEngine;
import org.jquantlib.pricingengines.swaption.BlackSwaptionEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.SwaptionVolatilityStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

/**
 * Pricing engine for irregular swaptions via super-replication.
 *
 * <p>Phase 4i port of C++ QuantLib v1.42.1
 * {@code ql/experimental/swaptions/haganirregularswaptionengine.{hpp,cpp}}.
 * Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * <ol>
 *   <li>P.S. Hagan: <i>Methodology for Callable Swaps and Bermudan
 *       'Exercise into Swaptions'</i></li>
 *   <li>P.J. Hunt, J.E. Kennedy: <i>Implied interest rate pricing models</i>,
 *       Finance Stochast. 2, 275-293 (1998)</li>
 * </ol>
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *   <li>The C++ source uses {@code SVD::solveFor} for the basket solve. The
 *       Java {@link org.jquantlib.math.matrixutilities.SVD} stub does not
 *       expose {@code solveFor}, so this port uses {@link LUDecomposition}
 *       directly. The basket matrix is square ({@code n x n}) and well-posed
 *       in the canonical Hagan setup, making LU sufficient. If the basket
 *       becomes ill-conditioned in practice, fall back to a Phase 4i.5
 *       SVD-based path (carry-forward).</li>
 *   <li>{@code BachelierSwaptionEngine} (a typedef alias for
 *       {@code BlackSwaptionEngine} configured for normal vol) is replaced
 *       with {@link BlackSwaptionEngine}, which already dispatches on
 *       {@link VolatilityType} held by the supplied volatility surface.</li>
 *   <li>Spread reshuffling preserves the C++ algorithm: the spread on the
 *       floating leg is converted into an equivalent fixed coupon
 *       adjustment.</li>
 * </ul>
 */
public class HaganIrregularSwaptionEngine extends IrregularSwaption.EngineImpl {

    private final Handle<YieldTermStructure> termStructure_;
    private final Handle<SwaptionVolatilityStructure> volatilityStructure_;

    public HaganIrregularSwaptionEngine(
            final Handle<SwaptionVolatilityStructure> volatilityStructure) {
        this(volatilityStructure, new Handle<YieldTermStructure>());
    }

    public HaganIrregularSwaptionEngine(
            final Handle<SwaptionVolatilityStructure> volatilityStructure,
            final Handle<YieldTermStructure> termStructure) {
        super();
        this.termStructure_ = termStructure;
        this.volatilityStructure_ = volatilityStructure;
        this.termStructure_.addObserver(this);
        this.volatilityStructure_.addObserver(this);
    }

    //
    // implements PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        final IrregularSwaption.ArgumentsImpl args = (IrregularSwaption.ArgumentsImpl) getArguments();

        final Exercise exercise = args.exercise;
        QL.require(exercise.type() == Exercise.Type.European,
                "swaption must be european");

        IrregularSwap swap = args.swap;

        // ---- Reshuffle spread from float to fixed ----
        // Find the fixed-coupon adjustment such that the swap NPV stays
        // constant after removing the floating-leg spread.
        final Leg fixedLeg = swap.fixedLeg();
        final double fxdLgBPS = CashFlows.getInstance().bps(fixedLeg, termStructure_);

        final Leg floatLeg = swap.floatingLeg();
        final double fltLgNPV = CashFlows.getInstance().npv(floatLeg, termStructure_);
        final double fltLgBPS = CashFlows.getInstance().bps(floatLeg, termStructure_);

        final Leg floatCFS = new Leg();
        for (final CashFlow cf : floatLeg) {
            final IborCoupon coupon = (IborCoupon) cf;
            final IborCoupon newCpn = new IborCoupon(
                    coupon.date(), coupon.nominal(), coupon.accrualStartDate(),
                    coupon.accrualEndDate(), coupon.fixingDays(),
                    (IborIndex) coupon.index(),
                    coupon.gearing(), 0.0,
                    coupon.referencePeriodStart(), coupon.referencePeriodEnd(),
                    coupon.dayCounter(), coupon.isInArrears());
            // Note: in the C++ port a BlackIborCouponPricer is set on each
            // non-in-arrears coupon. The pricer requires an
            // OptionletVolatilityStructure handle which the engine does not
            // own here; we defer pricer assignment to a Phase 4i.5
            // refinement and rely on the discounting engine's amount()
            // computation for the spread NPV.
            floatCFS.add(newCpn);
        }

        final double sprdLgNPV = fltLgNPV - CashFlows.getInstance().npv(floatCFS, termStructure_);
        final double avgSpread = sprdLgNPV / fltLgBPS / 10000.0;
        final double cpnAdjustment = avgSpread * fltLgBPS / fxdLgBPS;

        final Leg fixedCFS = new Leg();
        for (final CashFlow cf : fixedLeg) {
            final FixedRateCoupon coupon = (FixedRateCoupon) cf;
            final FixedRateCoupon newCpn = new FixedRateCoupon(
                    coupon.nominal(), coupon.date(),
                    coupon.rate() - cpnAdjustment,
                    coupon.dayCounter(), coupon.accrualStartDate(),
                    coupon.accrualEndDate(),
                    coupon.referencePeriodStart(), coupon.referencePeriodEnd());
            fixedCFS.add(newCpn);
        }

        // Spread-removed irregular swap
        swap = new IrregularSwap(args.swap.type(), fixedCFS, floatCFS);

        // Sets up the basket per Hagan
        final Basket basket = new Basket(swap, termStructure_, volatilityStructure_);

        // ---- Solve for lambda via Bisection ----
        final Bisection s1d = new Bisection();
        final double minLambda = -0.5;
        final double maxLambda = 0.5;
        s1d.setMaxEvaluations(10000);
        s1d.setLowerBound(minLambda);
        s1d.setUpperBound(maxLambda);
        s1d.solve(basket, 1.0e-8, 0.01, minLambda, maxLambda);

        // ---- Sum the prices of the regular swaptions ----
        ((IrregularSwaption.ResultsImpl) getResults()).value = HKPrice(basket, exercise);
    }

    /**
     * Computes irregular swaption price via the Hunt-Kennedy super-replication
     * sum: each basket weight times the corresponding regular swaption NPV.
     */
    public double HKPrice(final Basket basket, final Exercise exercise) {
        QL.require(volatilityStructure_.currentLink().volatilityType() == VolatilityType.Normal,
                "swaptionEngine: only normal volatility implemented.");

        final PricingEngine swaptionEngine = new BlackSwaptionEngine(
                termStructure_, volatilityStructure_);

        final Array weights = basket.weights();
        double npv = 0.0;
        for (int i = 0; i < weights.size(); ++i) {
            final VanillaSwap pvSwap = basket.component(i);
            final Swaption swaption = new Swaption(pvSwap, exercise);
            swaption.setPricingEngine(swaptionEngine);
            npv += weights.get(i) * swaption.NPV();
        }
        return npv;
    }

    /**
     * LGM (Linear Gauss-Markov) variant. C++ declares but does not fully
     * implement this in v1.42.1; left as a Phase 4i.5 carry-forward.
     */
    public double LGMPrice(final Basket basket, final Exercise exercise) {
        throw new LibraryException(
                "HaganIrregularSwaptionEngine.LGMPrice not yet implemented (Phase 4i.5)");
    }

    //
    // helper class
    //

    /**
     * Helper holding the basket of vanilla swaps that super-replicates the
     * irregular swap, parameterised by Lagrange multiplier {@code lambda}.
     */
    public static class Basket implements Ops.DoubleOp {

        private final IrregularSwap swap_;
        private final Handle<YieldTermStructure> termStructure_;
        private final Handle<SwaptionVolatilityStructure> volatilityStructure_;

        private double targetNPV_ = 0.0;
        private final PricingEngine engine_;

        private final java.util.List<Double> fairRates_ = new java.util.ArrayList<Double>();
        private final java.util.List<Double> annuities_ = new java.util.ArrayList<Double>();
        private final java.util.List<Date> expiries_ = new java.util.ArrayList<Date>();

        private double lambda_ = 0.0;

        public Basket(final IrregularSwap swap,
                final Handle<YieldTermStructure> termStructure,
                final Handle<SwaptionVolatilityStructure> volatilityStructure) {
            this.swap_ = swap;
            this.termStructure_ = termStructure;
            this.volatilityStructure_ = volatilityStructure;

            this.engine_ = new DiscountingSwapEngine(termStructure_);
            swap_.setPricingEngine(engine_);
            this.targetNPV_ = swap_.NPV();

            // Build standard swaps
            final Leg fixedLeg = swap_.fixedLeg();
            final Leg floatLeg = swap_.floatingLeg();

            final Leg fixedCFS = new Leg();

            for (int i = 0; i < fixedLeg.size(); ++i) {
                final FixedRateCoupon coupon = (FixedRateCoupon) fixedLeg.get(i);
                expiries_.add(coupon.date());

                final FixedRateCoupon newCpn = new FixedRateCoupon(
                        1.0, coupon.date(), coupon.rate(),
                        coupon.dayCounter(), coupon.accrualStartDate(),
                        coupon.accrualEndDate(),
                        coupon.referencePeriodStart(), coupon.referencePeriodEnd());

                fixedCFS.add(newCpn);

                annuities_.add(10000.0 * CashFlows.getInstance().bps(fixedCFS, termStructure_));

                final Leg floatCFS = new Leg();
                for (final CashFlow cf : floatLeg) {
                    final IborCoupon fcpn = (IborCoupon) cf;
                    if (fcpn.date().le(expiries_.get(i))) {
                        final IborCoupon newFloat = new IborCoupon(
                                fcpn.date(), 1.0,
                                fcpn.accrualStartDate(), fcpn.accrualEndDate(),
                                fcpn.fixingDays(), (IborIndex) fcpn.index(),
                                1.0, fcpn.spread(),
                                fcpn.referencePeriodStart(),
                                fcpn.referencePeriodEnd(),
                                fcpn.dayCounter(), fcpn.isInArrears());
                        // (No pricer assigned here — see engine note above.)
                        floatCFS.add(newFloat);
                    }
                }

                final double floatLegNPV = CashFlows.getInstance().npv(floatCFS, termStructure_);
                fairRates_.add(floatLegNPV / annuities_.get(i));
            }
        }

        /**
         * Computes a replication of the swap as a basket of vanilla swaps by
         * solving a linear system of equations. Returns the basket weights.
         */
        public Array compute(final double lambda) {
            this.lambda_ = lambda;

            final int n = swap_.fixedLeg().size();
            final Matrix arr = new Matrix(n, n);
            final Array rhs = new Array(n);

            // Fill matrix (upper-triangular block plus identity on the diagonal)
            for (int r = 0; r < n; ++r) {
                final FixedRateCoupon cpnR = (FixedRateCoupon) swap_.fixedLeg().get(r);
                for (int c = r; c < n; ++c) {
                    arr.set(r, c, (fairRates_.get(c) + lambda_) * cpnR.accrualPeriod());
                }
                arr.set(r, r, arr.get(r, r) + 1.0);
            }

            // RHS: nominal repayment + coupon accrual
            for (int r = 0; r < n; ++r) {
                final FixedRateCoupon cpnR = (FixedRateCoupon) swap_.fixedLeg().get(r);
                final double Nr = cpnR.nominal();
                if (r < n - 1) {
                    final FixedRateCoupon cpnR1 = (FixedRateCoupon) swap_.fixedLeg().get(r + 1);
                    final double Nr1 = cpnR1.nominal();
                    rhs.set(r, Nr * cpnR.rate() * cpnR.accrualPeriod() + (Nr - Nr1));
                } else {
                    rhs.set(r, Nr * cpnR.rate() * cpnR.accrualPeriod() + Nr);
                }
            }

            // Solve A * x = rhs via LU decomposition.
            // (C++ uses SVD::solveFor; LU is sufficient for the canonical
            // square, well-conditioned Hagan basket. See class javadoc for
            // the SVD carry-forward.)
            final Matrix rhsMat = new Matrix(n, 1);
            for (int i = 0; i < n; ++i) {
                rhsMat.set(i, 0, rhs.get(i));
            }
            final Matrix sol = new LUDecomposition(arr).solve(rhsMat);
            final Array out = new Array(n);
            for (int i = 0; i < n; ++i) {
                out.set(i, sol.get(i, 0));
            }
            return out;
        }

        /** Defect function used by the bisection root-finder. */
        @Override
        public double op(final double lambda) {
            final Array weights = compute(lambda);
            double defect = -targetNPV_;
            for (int i = 0; i < weights.size(); ++i) {
                defect -= swap_.type().toInteger() * lambda * weights.get(i) * annuities_.get(i);
            }
            return defect;
        }

        /**
         * Constructs a vanilla swap component matching the i-th basket element
         * by deriving conventions from the underlying floating index.
         */
        public VanillaSwap component(final int i) {
            final IborCoupon iborCpn = (IborCoupon) swap_.floatingLeg().get(0);
            final IborIndex iborIndex = (IborIndex) iborCpn.index();

            final Period dummySwapLength = new Period(1, TimeUnit.Years);

            VanillaSwap memberSwap = new MakeVanillaSwap(dummySwapLength, iborIndex)
                    .withType(swap_.type())
                    .withEffectiveDate(swap_.startDate())
                    .withTerminationDate(expiries_.get(i))
                    .withRule(DateGeneration.Rule.Backward)
                    .withDiscountingTermStructure(termStructure_)
                    .value();

            final double stdAnnuity = 10000.0
                    * CashFlows.getInstance().bps(memberSwap.fixedLeg(), termStructure_);

            final double transformedRate = (fairRates_.get(i) + lambda_) * annuities_.get(i)
                    / stdAnnuity;

            memberSwap = new MakeVanillaSwap(dummySwapLength, iborIndex, transformedRate)
                    .withType(swap_.type())
                    .withEffectiveDate(swap_.startDate())
                    .withTerminationDate(expiries_.get(i))
                    .withRule(DateGeneration.Rule.Backward)
                    .withDiscountingTermStructure(termStructure_)
                    .value();

            return memberSwap;
        }

        public Array weights() {
            return compute(lambda_);
        }

        public double lambda() {
            return lambda_;
        }

        public IrregularSwap swap() {
            return swap_;
        }
    }
}
