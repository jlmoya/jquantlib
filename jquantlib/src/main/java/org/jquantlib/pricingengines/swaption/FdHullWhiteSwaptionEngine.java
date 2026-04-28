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
 Copyright (C) 2011 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swaption;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jquantlib.QL;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.Coupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmHullWhiteSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmAffineModelTermStructure;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Finite-difference Hull-White swaption engine.
 * <p>
 * Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/swaption/fdhullwhiteswaptionengine.{hpp,cpp}}.
 * Discretises the Hull-White short-rate PDE on a 1-D mesh in the
 * Ornstein-Uhlenbeck state variable {@code x = r - phi(t)} and rolls back the
 * intrinsic value of the underlying vanilla swap from the exercise date to
 * the evaluation date with an ADI scheme (default
 * {@link FdmSchemeDesc#Douglas()} matching C++).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code GenericModelEngine<HullWhite, Swaption::arguments,
 *     Swaption::results>} becomes a direct subclass of
 *     {@link Swaption.EngineImpl} (which itself extends
 *     {@code GenericEngine<Swaption.Arguments, Swaption.Results>}). The model
 *     reference is held as a private field and observed manually, mirroring
 *     {@link JamshidianSwaptionEngine}'s shape (Phase 2e/2f precedent for
 *     swaption engines that are conceptually
 *     {@code GenericModelEngine<HullWhite,...>}).
 * <li>The dual-curve discount/forward split that the C++ engine threads via
 *     a separate {@code fwdModel = HullWhite(fwdTs, a, sigma)} collapses to
 *     a single model in the Java port; both legs query the same
 *     {@link FdmAffineModelTermStructure}. This is adequate for single-curve
 *     fixtures (which is what the WI-2 fingerprint test uses).
 * <li>C++ uses the
 *     {@code FdmAffineModelSwapInnerValue<HullWhite>} template specialisation
 *     which clones the swap with {@code iborIndex()->clone(fwdTs)}, rebinds
 *     a pair of {@code RelinkableHandle<YieldTermStructure>}s on every grid
 *     node, and re-prices the cloned legs. The Java port mirrors this by
 *     cloning the {@link VanillaSwap} with the original
 *     {@link IborIndex#clone(Handle) cloned} ibor index pointing at a
 *     {@link RelinkableHandle} into a {@link FdmAffineModelTermStructure}.
 *     The {@link HullWhiteSwapInnerValue} inner class then reads each
 *     coupon's {@code amount()} (which dynamically reprojects through the
 *     mesh-state-driven term structure) and discounts it via the rebound
 *     handle. Match to C++ is at FD discretisation tolerance (see test).
 * <li>The exercise dates -&gt; {@code t2d} mapping uses Java {@code Double}
 *     keys (year-fraction times). C++ uses {@code std::map<Time, Date>}
 *     with the same key type.
 * <li>Only {@link Exercise.Type#European} with no dividend schedule is
 *     supported because {@link FdmStepConditionComposite#vanillaComposite}
 *     does not yet port the {@code FdmAmericanStepCondition} /
 *     {@code FdmBermudanStepCondition} / {@code FdmDividendHandler}
 *     classes (Phase 2h WI-1.3 implementer's note).
 * </ul>
 *
 * <h3>Default constructor parameters (matching C++ v1.42.1)</h3>
 * <p>{@code tGrid = 100}, {@code xGrid = 100}, {@code dampingSteps = 0},
 * {@code invEps = 1e-5}, scheme = {@code FdmSchemeDesc::Douglas()}.
 *
 * @see HullWhite
 * @see FdmHullWhiteSolver
 * @see FdmAffineModelTermStructure
 *
 * @author Phase 2h WI-2 port
 */
public class FdHullWhiteSwaptionEngine extends Swaption.EngineImpl {

    private final HullWhite model_;
    private final int tGrid_;
    private final int xGrid_;
    private final int dampingSteps_;
    private final double invEps_;
    private final FdmSchemeDesc schemeDesc_;

    /** Convenience: defaults match C++ v1.42.1 (tGrid=100, xGrid=100,
     *  dampingSteps=0, invEps=1e-5, scheme=Douglas). */
    public FdHullWhiteSwaptionEngine(final HullWhite model) {
        this(model, 100, 100, 0, 1.0e-5, FdmSchemeDesc.Douglas());
    }

    public FdHullWhiteSwaptionEngine(final HullWhite model,
                                     final int tGrid,
                                     final int xGrid) {
        this(model, tGrid, xGrid, 0, 1.0e-5, FdmSchemeDesc.Douglas());
    }

    public FdHullWhiteSwaptionEngine(final HullWhite model,
                                     final int tGrid,
                                     final int xGrid,
                                     final int dampingSteps) {
        this(model, tGrid, xGrid, dampingSteps, 1.0e-5, FdmSchemeDesc.Douglas());
    }

    /**
     * Full constructor mirroring C++ v1.42.1
     * {@code FdHullWhiteSwaptionEngine::FdHullWhiteSwaptionEngine}.
     *
     * @param model         Hull-White short-rate model (non-null).
     * @param tGrid         number of time steps in the rollback.
     * @param xGrid         number of state-space grid points.
     * @param dampingSteps  number of leading implicit-Euler damping
     *                      steps applied before the main scheme.
     * @param invEps        tail percentile for the
     *                      {@link FdmSimpleProcess1dMesher} truncation.
     * @param schemeDesc    finite-difference scheme descriptor.
     */
    public FdHullWhiteSwaptionEngine(final HullWhite model,
                                     final int tGrid,
                                     final int xGrid,
                                     final int dampingSteps,
                                     final double invEps,
                                     final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.tGrid_ = tGrid;
        this.xGrid_ = xGrid;
        this.dampingSteps_ = dampingSteps;
        this.invEps_ = invEps;
        this.schemeDesc_ = schemeDesc;
        this.model_.addObserver(this);
    }

    public HullWhite model() {
        return model_;
    }

    //
    // implements PricingEngine
    //

    @Override
    public void calculate() /* @ReadOnly */ {
        final Swaption.ArgumentsImpl args = (Swaption.ArgumentsImpl) arguments_;
        final Swaption.ResultsImpl results = (Swaption.ResultsImpl) results_;

        // 1. Term structure
        final Handle<YieldTermStructure> ts = model_.termStructure();
        QL.require(ts != null && !ts.empty(),
                "Hull-White model has no term structure");

        final DayCounter dc = ts.currentLink().dayCounter();
        final Date referenceDate = ts.currentLink().referenceDate();
        final double maturity = dc.yearFraction(referenceDate,
                args.exercise.lastDate());

        // 2. Mesher — 1-D OU mesher in x = r - phi(t).
        final OrnsteinUhlenbeckProcess process =
                new OrnsteinUhlenbeckProcess(model_.a(), model_.sigma());
        final FdmSimpleProcess1dMesher shortRateMesher =
                new FdmSimpleProcess1dMesher(xGrid_, process, maturity, 1, invEps_,
                        Double.NaN);
        final FdmMesherComposite mesher = new FdmMesherComposite(shortRateMesher);

        // 3. Inner-value calculator: build t -> exerciseDate map, then a
        //    swap-NPV calculator that prices the swap at each (t, x) on the
        //    mesh using the affine discountBond formula.
        final List<Date> exerciseDates = args.exercise.dates();
        final Map<Double, Date> t2d = new HashMap<>();
        for (final Date exerciseDate : exerciseDates) {
            final double t = dc.yearFraction(referenceDate, exerciseDate);
            QL.require(t >= 0, "exercise dates must not contain past date");
            t2d.put(t, exerciseDate);
        }

        // C++ also asserts that the forward and discount day-counters /
        // reference dates match. The Java port collapses to a single
        // model (one curve) so the parity is structural — skip the runtime
        // check.
        final VanillaSwap swap = args.swap;
        final FdmInnerValueCalculator calculator =
                new HullWhiteSwapInnerValue(model_, mesher, swap, t2d);

        // 4. Step conditions (European with no dividends -> empty composite,
        //    matching C++ behaviour).
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        new DividendSchedule(), args.exercise,
                        mesher, calculator, referenceDate, dc);

        // 5. Boundary conditions — C++ uses an empty FdmBoundaryConditionSet.
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator, maturity,
                tGrid_, dampingSteps_);

        final FdmHullWhiteSolver solver = new FdmHullWhiteSolver(
                new Handle<HullWhite>(model_), solverDesc, schemeDesc_);

        // C++: results_.value = solver.valueAt(0.0)
        // The 1-D mesh state variable is x = r - phi(t); at t = 0 the OU
        // process starts at x0 = 0, so we sample the solution at r = 0.
        results.value = solver.valueAt(0.0);
    }

    /**
     * HullWhite-specialised inner-value: at each mesh node {@code (t, x)}
     * we set {@code r = dynamics().shortRate(t, x)}, rebind a pair of
     * {@link FdmAffineModelTermStructure}s (discount + forwarding) keyed at
     * the corresponding exercise date with factor {@code [r]}, and compute
     * the swap leg sum as
     * {@code sum_j sign_j * sum_i amount_i * disTs.discount(payDate_i)}
     * for {@code i} with {@code accrualStart_i >= exerciseDate}.
     * <p>
     * Mirrors C++ v1.42.1
     * {@code FdmAffineModelSwapInnerValue<HullWhite>::innerValue}.
     */
    private static final class HullWhiteSwapInnerValue
            implements FdmInnerValueCalculator {

        private final HullWhite model_;
        private final FdmMesher mesher_;
        private final VanillaSwap swap_;
        private final Map<Double, Date> exerciseDates_;
        private final RelinkableHandle<YieldTermStructure> disTsHandle_ =
                new RelinkableHandle<YieldTermStructure>();
        private final RelinkableHandle<YieldTermStructure> fwdTsHandle_ =
                new RelinkableHandle<YieldTermStructure>();
        private FdmAffineModelTermStructure disTs_;
        private FdmAffineModelTermStructure fwdTs_;
        private Date currentRefDate_;

        HullWhiteSwapInnerValue(final HullWhite model,
                                final FdmMesher mesher,
                                final VanillaSwap swap,
                                final Map<Double, Date> exerciseTimes) {
            this.model_ = model;
            this.mesher_ = mesher;
            // C++ clones the swap with iborIndex()->clone(fwdTs) so the
            // float coupons reproject through the affine-model term
            // structure. Java port does the same: build a cloned
            // VanillaSwap whose IborIndex has its own forwarding handle
            // linked to {@code fwdTsHandle_}. The cloned swap's float
            // coupons therefore re-fix at every grid node.
            final IborIndex clonedIndex =
                    swap.iborIndex().clone(fwdTsHandle_).currentLink();
            this.swap_ = new VanillaSwap(
                    swap.type(), swap.nominal(),
                    swap.fixedSchedule(), swap.fixedRate(), swap.fixedDayCount(),
                    swap.floatingSchedule(), clonedIndex,
                    swap.spread(), swap.floatingDayCount(),
                    swap.paymentConvention());
            this.exerciseDates_ = exerciseTimes;
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            final Date iterExerciseDate = exerciseDates_.get(t);
            if (iterExerciseDate == null) {
                throw new org.jquantlib.lang.exceptions.LibraryException(
                        "no exercise date found for grid time " + t);
            }

            // OU state -> short rate via HW dynamics.
            final double x = mesher_.location(iter, 0);
            final double r = model_.dynamics().shortRate(t, x);
            final Array factors = new Array(1);
            factors.set(0, r);

            final Handle<YieldTermStructure> baseTs = model_.termStructure();
            final NullCalendar cal = new NullCalendar();
            if (disTs_ == null || !iterExerciseDate.eq(currentRefDate_)) {
                disTs_ = new FdmAffineModelTermStructure(
                        factors, cal, baseTs.currentLink().dayCounter(),
                        iterExerciseDate, baseTs.currentLink().referenceDate(),
                        model_);
                fwdTs_ = new FdmAffineModelTermStructure(
                        factors, cal, baseTs.currentLink().dayCounter(),
                        iterExerciseDate, baseTs.currentLink().referenceDate(),
                        model_);
                disTsHandle_.linkTo(disTs_);
                fwdTsHandle_.linkTo(fwdTs_);
                currentRefDate_ = iterExerciseDate;
            } else {
                disTs_.setVariable(factors);
                fwdTs_.setVariable(factors);
            }

            // Sum legs: j=0 fixed (sign -1), j=1 floating (sign +1) for a
            // payer; receiver flips the final sign at the end.
            double npv = 0.0;
            for (int j = 0; j < 2; j++) {
                final Leg leg = (j == 0) ? swap_.fixedLeg() : swap_.floatingLeg();
                double legNpv = 0.0;
                for (final CashFlow cf : leg) {
                    if (!(cf instanceof Coupon)) {
                        continue;
                    }
                    final Coupon coupon = (Coupon) cf;
                    if (coupon.accrualStartDate().ge(iterExerciseDate)) {
                        legNpv += cf.amount() * disTs_.discount(cf.date());
                    }
                }
                if (j == 0) {
                    legNpv = -legNpv;
                }
                npv += legNpv;
            }
            if (swap_.type() == VanillaSwap.Type.Receiver) {
                npv = -npv;
            }
            return Math.max(0.0, npv);
        }

        @Override
        public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
            return innerValue(iter, t);
        }
    }
}
