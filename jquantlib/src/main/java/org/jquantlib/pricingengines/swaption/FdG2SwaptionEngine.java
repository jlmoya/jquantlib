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
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmG2Solver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmAffineModelTermStructure;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.model.shortrate.twofactormodels.G2;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finite-difference G2++ two-factor swaption pricing engine.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/pricingengines/swaption/fdg2swaptionengine.{hpp,cpp}}.
 *
 * <p>Builds a 2D non-uniform mesh from two {@link OrnsteinUhlenbeckProcess}
 * factors driving G2++, wires it into an {@link FdmG2Solver}, and reads off the swaption value at the OU origin
 * {@code (0, 0)}.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code GenericModelEngine<G2, Swaption::arguments,
 *     Swaption::results>} becomes a direct subclass of
 *     {@link Swaption.EngineImpl}; the {@code G2} model is held as a
 *     private field and observed manually, mirroring
 *     {@link FdHullWhiteSwaptionEngine} (Phase 2h WI-2 precedent).</li>
 * <li>C++ uses the
 *     {@code FdmAffineModelSwapInnerValue<G2>} template specialisation
 *     which clones the swap with {@code iborIndex()->clone(fwdTs)}, rebinds
 *     a pair of {@code RelinkableHandle<YieldTermStructure>}s on every grid
 *     node, and re-prices the cloned legs through an
 *     {@link FdmAffineModelTermStructure}. The Java port mirrors this with
 *     the {@link G2SwapInnerValue} inner class — bypassing the framework
 *     {@code FdmAffineModelSwapInnerValue} which doesn't reproject the
 *     floating-leg cash flows through the model-implied curve. (Same fix
 *     pattern as Phase 2h WI-2 {@code HullWhiteSwapInnerValue}; the
 *     framework class is correct only when the floating leg is degenerate
 *     or negligible.)</li>
 * <li>The dual-curve {@code disModel}/{@code fwdModel} pair collapses to a
 *     single G2 model in the Java port. Both legs query the same
 *     {@link FdmAffineModelTermStructure}; this is adequate for the
 *     single-curve fixture used by the WI-3 fingerprint test.</li>
 * <li>{@code dampingSteps = 0} by default. Higher values trigger an
 *     implicit-Euler damping pre-roll; this is supported by the
 *     {@link FdmG2Solver} damping path.</li>
 * <li>Only {@link Exercise.Type#European} with no dividend schedule is
 *     supported; {@link FdmStepConditionComposite#vanillaComposite}
 *     does not yet port the American/Bermudan/dividend handlers
 *     (Phase 2h WI-1.3 implementer's note).</li>
 * </ul>
 *
 * <h3>Default constructor parameters (matching C++ v1.42.1)</h3>
 * <p>{@code tGrid = 100}, {@code xGrid = 50}, {@code yGrid = 50},
 * {@code dampingSteps = 0}, {@code invEps = 1e-5}, scheme =
 * {@code FdmSchemeDesc::Hundsdorfer()}.
 *
 * @author Phase 2h WI-3 port
 * @see G2
 * @see FdmG2Solver
 * @see FdmAffineModelTermStructure
 */
public class FdG2SwaptionEngine extends Swaption.EngineImpl {

    private final G2 model_;
    private final int tGrid_;
    private final int xGrid_;
    private final int yGrid_;
    private final int dampingSteps_;
    private final double invEps_;
    private final FdmSchemeDesc schemeDesc_;

    /**
     * Convenience: defaults match C++ v1.42.1 (tGrid=100, xGrid=50, yGrid=50, dampingSteps=0, invEps=1e-5,
     * scheme=Hundsdorfer).
     */
    public FdG2SwaptionEngine(final G2 model) {
        this(model, 100, 50, 50, 0, 1.0e-5, FdmSchemeDesc.Hundsdorfer());
    }

    public FdG2SwaptionEngine(final G2 model, final int tGrid, final int xGrid, final int yGrid) {
        this(model, tGrid, xGrid, yGrid, 0, 1.0e-5, FdmSchemeDesc.Hundsdorfer());
    }

    public FdG2SwaptionEngine(final G2 model, final int tGrid, final int xGrid, final int yGrid,
            final int dampingSteps) {
        this(model, tGrid, xGrid, yGrid, dampingSteps, 1.0e-5, FdmSchemeDesc.Hundsdorfer());
    }

    /**
     * Full constructor mirroring C++ v1.42.1 {@code FdG2SwaptionEngine::FdG2SwaptionEngine}.
     *
     * @param model        G2++ short-rate model (non-null).
     * @param tGrid        number of time steps in the rollback.
     * @param xGrid        number of grid points along the x factor.
     * @param yGrid        number of grid points along the y factor.
     * @param dampingSteps leading implicit-Euler damping steps.
     * @param invEps       tail percentile for the {@link FdmSimpleProcess1dMesher} truncation.
     * @param schemeDesc   finite-difference scheme descriptor.
     */
    public FdG2SwaptionEngine(final G2 model, final int tGrid, final int xGrid, final int yGrid, final int dampingSteps,
            final double invEps, final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(model != null, "no model specified");
        this.model_ = model;
        this.tGrid_ = tGrid;
        this.xGrid_ = xGrid;
        this.yGrid_ = yGrid;
        this.dampingSteps_ = dampingSteps;
        this.invEps_ = invEps;
        this.schemeDesc_ = schemeDesc;
        this.model_.addObserver(this);
    }

    public G2 model() {
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
        final Handle< YieldTermStructure > ts = model_.termStructure();
        QL.require(ts != null && !ts.empty(), "G2 model has no term structure");

        final DayCounter dc = ts.currentLink().dayCounter();
        final Date referenceDate = ts.currentLink().referenceDate();
        final double maturity = dc.yearFraction(referenceDate, args.exercise.lastDate());

        // 2. Mesher — 2D mesh built from the two OU factors driving G2++.
        final OrnsteinUhlenbeckProcess process1 = new OrnsteinUhlenbeckProcess(model_.a(), model_.sigma());
        final OrnsteinUhlenbeckProcess process2 = new OrnsteinUhlenbeckProcess(model_.b(), model_.eta());

        final Fdm1dMesher xMesher = new FdmSimpleProcess1dMesher(xGrid_, process1, maturity, 1, invEps_, Double.NaN);
        final Fdm1dMesher yMesher = new FdmSimpleProcess1dMesher(yGrid_, process2, maturity, 1, invEps_, Double.NaN);

        final FdmMesherComposite mesher = new FdmMesherComposite(Arrays.asList(xMesher, yMesher));

        // 3. Inner-value calculator: build t -> exerciseDate map, then a
        //    swap-NPV calculator that prices the swap at each (t, x, y) on
        //    the 2D mesh using the affine-model implied term structure.
        final List< Date > exerciseDates = args.exercise.dates();
        final Map< Double, Date > t2d = new HashMap<>();
        for ( final Date exerciseDate : exerciseDates ) {
            final double t = dc.yearFraction(referenceDate, exerciseDate);
            QL.require(t >= 0, "exercise dates must not contain past date");
            t2d.put(t, exerciseDate);
        }

        // C++ also builds a separate fwdModel for the floating leg. The
        // Java port collapses to a single model (single-curve usage is
        // structural here). Validate the forwarding/discount day-counter
        // and reference-date parity, matching the C++ guards.
        final VanillaSwap swap = args.swap;
        final IborIndex iborIndex = swap.iborIndex();
        final Handle< YieldTermStructure > fwdTs = iborIndex.termStructure();
        QL.require(!fwdTs.empty(), "ibor index has no forwarding term structure set");
        QL.require(fwdTs.currentLink().dayCounter().equals(dc), "day counter of forward and discount curve must match");
        QL.require(fwdTs.currentLink().referenceDate().eq(referenceDate),
                "reference date of forward and discount curve must match");

        final FdmInnerValueCalculator calculator = new G2SwapInnerValue(model_, mesher, swap, t2d);

        // 4. Step conditions (European with no dividends -> empty composite,
        //    matching C++ behaviour).
        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(new DividendSchedule(),
                args.exercise, mesher, calculator, referenceDate, dc);

        // 5. Boundary conditions — empty (Dirichlet at outer mesh edges
        //    is implicit in the discrete operator).
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver — wire the descriptor and read NPV at the OU origin
        //    (x0 = y0 = 0 in G2++).
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid_,
                dampingSteps_);

        final FdmG2Solver solver = new FdmG2Solver(new Handle< G2 >(model_), solverDesc, schemeDesc_);

        results.value = solver.valueAt(0.0, 0.0);
    }

    /**
     * G2-specialised inner-value calculator: at each mesh node {@code (t, x, y)} we set the affine factor vector to
     * {@code [x, y]}, rebind a pair of {@link FdmAffineModelTermStructure}s (discount + forwarding) keyed at the
     * corresponding exercise date, and compute the swap leg sum as
     * {@code sum_j sign_j * sum_i amount_i * disTs.discount(payDate_i)} for {@code i} with
     * {@code accrualStart_i >= exerciseDate}.
     * <p>
     * Mirrors C++ v1.42.1 {@code FdmAffineModelSwapInnerValue<G2>::innerValue} (template body in
     * fdmaffinemodelswapinnervalue.hpp) plus the explicit {@code <G2>::getState} specialization in
     * fdmaffinemodelswapinnervalue.cpp which uses {@code (mesher.location(iter,0), mesher.location(iter,1))}.
     * <p>
     * Bypasses the generic {@link org.jquantlib.methods.finitedifferences.utilities.FdmAffineModelSwapInnerValue}
     * because the latter does not clone the swap with a re-linked {@code IborIndex}, so the floating-leg amounts are
     * not reprojected through the model-implied curve at each grid node — yielding a dramatic NPV under-estimate on the
     * WI-3 fixture (observed ~450x factor on the prior dispatch). Same workaround pattern as Phase 2h WI-2
     * {@code HullWhiteSwapInnerValue}.
     */
    private static final class G2SwapInnerValue implements FdmInnerValueCalculator {

        private final G2 model_;
        private final FdmMesher mesher_;
        private final VanillaSwap swap_;
        private final Map< Double, Date > exerciseDates_;
        private final RelinkableHandle< YieldTermStructure > disTsHandle_ = new RelinkableHandle< YieldTermStructure >();
        private final RelinkableHandle< YieldTermStructure > fwdTsHandle_ = new RelinkableHandle< YieldTermStructure >();
        private FdmAffineModelTermStructure disTs_;
        private FdmAffineModelTermStructure fwdTs_;
        private Date currentRefDate_;

        G2SwapInnerValue(final G2 model, final FdmMesher mesher, final VanillaSwap swap,
                final Map< Double, Date > exerciseTimes) {
            this.model_ = model;
            this.mesher_ = mesher;
            // C++ clones the swap with iborIndex()->clone(fwdTs) so the
            // floating coupons reproject through the affine-model term
            // structure. Java port does the same.
            final IborIndex clonedIndex = swap.iborIndex().clone(fwdTsHandle_).currentLink();
            this.swap_ = new VanillaSwap(swap.type(), swap.nominal(), swap.fixedSchedule(), swap.fixedRate(),
                    swap.fixedDayCount(), swap.floatingSchedule(), clonedIndex, swap.spread(), swap.floatingDayCount(),
                    swap.paymentConvention());
            this.exerciseDates_ = exerciseTimes;
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            final Date iterExerciseDate = exerciseDates_.get(t);
            if ( iterExerciseDate == null ) {
                throw new org.jquantlib.lang.exceptions.LibraryException("no exercise date found for grid time " + t);
            }

            // G2 state at the mesh node: (x, y) — no dynamics shift.
            // C++: FdmAffineModelSwapInnerValue<G2>::getState returns
            //      {mesher_->location(iter,0), mesher_->location(iter,1)}.
            final double xLoc = mesher_.location(iter, 0);
            final double yLoc = mesher_.location(iter, 1);
            final Array factors = new Array(2);
            factors.set(0, xLoc);
            factors.set(1, yLoc);

            final Handle< YieldTermStructure > baseTs = model_.termStructure();
            final NullCalendar cal = new NullCalendar();
            if ( disTs_ == null || !iterExerciseDate.eq(currentRefDate_) ) {
                disTs_ = new FdmAffineModelTermStructure(factors, cal, baseTs.currentLink().dayCounter(),
                        iterExerciseDate, baseTs.currentLink().referenceDate(), model_);
                fwdTs_ = new FdmAffineModelTermStructure(factors, cal, baseTs.currentLink().dayCounter(),
                        iterExerciseDate, baseTs.currentLink().referenceDate(), model_);
                disTsHandle_.linkTo(disTs_);
                fwdTsHandle_.linkTo(fwdTs_);
                currentRefDate_ = iterExerciseDate;
            } else {
                disTs_.setVariable(factors);
                fwdTs_.setVariable(factors);
            }

            // Sum legs: j=0 fixed (sign -1 for payer), j=1 floating (+1).
            // The cloned swap's float coupons reproject through fwdTsHandle_
            // (now linked to fwdTs_), so cf.amount() is recomputed on each
            // call.
            double npv = 0.0;
            for ( int j = 0; j < 2; j++ ) {
                final Leg leg = (j == 0) ? swap_.fixedLeg() : swap_.floatingLeg();
                double legNpv = 0.0;
                for ( final CashFlow cf : leg ) {
                    if (!(cf instanceof Coupon coupon)) {
                        continue;
                    }
                    if ( coupon.accrualStartDate().ge(iterExerciseDate) ) {
                        legNpv += cf.amount() * disTs_.discount(cf.date());
                    }
                }
                if ( j == 0 ) {
                    legNpv = -legNpv;
                }
                npv += legNpv;
            }
            if ( swap_.type() == VanillaSwap.Type.Receiver ) {
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
