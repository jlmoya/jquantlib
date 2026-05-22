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
 Copyright (C) 2011, 2012 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator.ShapePoint;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.KlugeExtOUProcess;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SwingExercise;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.meshers.ExponentialJump1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmZeroInnerValue;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Finite-Differences engine for simple virtual power plant (VPP) options.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdsimpleklugeextouvppengine.{hpp,cpp}}.</p>
 *
 * <p>The engine assembles a four-dimensional mesh
 * {@code (X, Y, U, exerciseState)} on top of the Kluge + extended OU
 * joint process driving the (power, fuel) pair, builds the spark-spread
 * inner-value calculator from per-leg
 * {@link FdmExtOUJumpModelInnerValue} (power) and
 * {@link FdmExpExtOUInnerValueCalculator} (fuel) terms, attaches a
 * dynamic-programming {@link FdmVPPStepCondition} (built via
 * {@link FdmVPPStepConditionFactory}), and solves the backward PIDE via
 * {@link FdmKlugeExtOUSolver}. After roll-back, the value at the
 * process's initial state {@code (X0, Y0, U0)} is sampled along every
 * point of the discrete exercise-state mesh, and the final reported
 * value is the maximum across the column —
 * {@code stepCondition.maxValue(results)} —
 * which mirrors C++.</p>
 *
 * <p>C++ uses a templated {@code FdmKlugeExtOUSolver<N>} parameterised on
 * the spatial dimension; the Java {@link FdmKlugeExtOUSolver} reads the
 * dimension from the mesher's layout at construction time so no template
 * parameter is needed.</p>
 *
 * <p>Source: {@code test-suite/vpp.cpp::testVPPPricing} at v1.42.1
 * {@code 099987f0ca}.</p>
 *
 * @author Phase 5e.5b-CFC-d-290 port
 */
public class FdSimpleKlugeExtOUVPPEngine
        extends GenericEngine<VanillaVPPOption.ArgumentsImpl,
                              VanillaVPPOption.ResultsImpl> {

    /**
     * Time-shape descriptor. Mirrors C++ typedef
     * {@code Shape = FdmExtOUJumpModelInnerValue::Shape}.
     */
    public static final class Shape {
        public final List<ShapePoint> shape;
        public Shape(final List<ShapePoint> shape) {
            this.shape = shape;
        }
    }

    private final KlugeExtOUProcess process_;
    private final YieldTermStructure rTS_;
    private final double fuelCostAddon_;
    private final Shape fuelShape_;
    private final Shape powerShape_;
    private final int tGrid_;
    private final int xGrid_;
    private final int yGrid_;
    private final int gGrid_;
    private final FdmSchemeDesc schemeDesc_;

    /**
     * Convenience constructor — C++ defaults
     * ({@code tGrid=1, xGrid=50, yGrid=10, gGrid=20},
     * Hundsdorfer scheme).
     */
    public FdSimpleKlugeExtOUVPPEngine(final KlugeExtOUProcess process,
                                       final YieldTermStructure rTS,
                                       final Shape fuelShape,
                                       final Shape powerShape,
                                       final double fuelCostAddon) {
        this(process, rTS, fuelShape, powerShape, fuelCostAddon,
                1, 50, 10, 20, FdmSchemeDesc.Hundsdorfer());
    }

    /**
     * Grid-only constructor — Hundsdorfer scheme.
     */
    public FdSimpleKlugeExtOUVPPEngine(final KlugeExtOUProcess process,
                                       final YieldTermStructure rTS,
                                       final Shape fuelShape,
                                       final Shape powerShape,
                                       final double fuelCostAddon,
                                       final int tGrid,
                                       final int xGrid,
                                       final int yGrid,
                                       final int gGrid) {
        this(process, rTS, fuelShape, powerShape, fuelCostAddon,
                tGrid, xGrid, yGrid, gGrid, FdmSchemeDesc.Hundsdorfer());
    }

    /**
     * Full constructor mirroring C++ v1.42.1.
     *
     * @param process       driving Kluge + extended OU joint process
     * @param rTS           risk-free term structure (reference date +
     *                      day-counter used by the swing exercise)
     * @param fuelShape     fuel-leg time shape (may be {@code null})
     * @param powerShape    power-leg time shape (may be {@code null})
     * @param fuelCostAddon constant fuel-cost surcharge
     * @param tGrid         number of time-grid points
     * @param xGrid         number of OU (power log-spot) grid points
     * @param yGrid         number of exponential-jump grid points
     * @param gGrid         number of OU (gas log-spot) grid points
     * @param schemeDesc    FDM scheme descriptor (default: Hundsdorfer)
     */
    public FdSimpleKlugeExtOUVPPEngine(final KlugeExtOUProcess process,
                                       final YieldTermStructure rTS,
                                       final Shape fuelShape,
                                       final Shape powerShape,
                                       final double fuelCostAddon,
                                       final int tGrid,
                                       final int xGrid,
                                       final int yGrid,
                                       final int gGrid,
                                       final FdmSchemeDesc schemeDesc) {
        super(new VanillaVPPOption.ArgumentsImpl(),
              new VanillaVPPOption.ResultsImpl());
        QL.require(process != null, "null KlugeExtOUProcess");
        QL.require(rTS != null, "null risk-free term structure");
        QL.require(schemeDesc != null, "null FDM scheme descriptor");
        this.process_       = process;
        this.rTS_           = rTS;
        this.fuelCostAddon_ = fuelCostAddon;
        this.fuelShape_     = fuelShape;
        this.powerShape_    = powerShape;
        this.tGrid_         = tGrid;
        this.xGrid_         = xGrid;
        this.yGrid_         = yGrid;
        this.gGrid_         = gGrid;
        this.schemeDesc_    = schemeDesc;
    }

    /**
     * Build the 4D FD mesh, inner-value calculators, step- and
     * boundary-condition sets, then solve the 4D backward PIDE via
     * {@link FdmKlugeExtOUSolver} and interpolate at the process's
     * initial state, sweeping over the discrete exercise-state axis to
     * pick the best maximum.
     */
    @Override
    public void calculate() {
        // 1. Exercise definition (C++ QL_REQUIRE SwingExercise).
        QL.require(SwingExercise.class.isAssignableFrom(
                        arguments_.exercise.getClass()),
                "Swing exercise supported only");
        final SwingExercise swingExercise = (SwingExercise) arguments_.exercise;

        final FdmVPPStepConditionFactory stepConditionFactory =
                new FdmVPPStepConditionFactory(arguments_);

        final double[] exerciseTimesArr =
                swingExercise.exerciseTimes(rTS_.dayCounter(),
                                            rTS_.referenceDate());
        final List<Double> exerciseTimes =
                new ArrayList<>(exerciseTimesArr.length);
        for (final double t : exerciseTimesArr) {
            exerciseTimes.add(t);
        }

        // 2. Mesher set-up.
        final double maturity = exerciseTimesArr[exerciseTimesArr.length - 1];

        final ExtOUWithJumpsProcess klugeProcess = process_.getKlugeProcess();
        final StochasticProcess1D klugeOUProcess =
                klugeProcess.getExtendedOrnsteinUhlenbeckProcess();

        final Fdm1dMesher xMesher =
                new FdmSimpleProcess1dMesher(xGrid_, klugeOUProcess, maturity);

        final Fdm1dMesher yMesher = new ExponentialJump1dMesher(
                yGrid_,
                klugeProcess.beta(),
                klugeProcess.jumpIntensity(),
                klugeProcess.eta(),
                1.0e-3);

        final Fdm1dMesher gMesher = new FdmSimpleProcess1dMesher(
                gGrid_, process_.getExtOUProcess(), maturity);

        final Fdm1dMesher exerciseMesher = stepConditionFactory.stateMesher();

        final FdmMesher mesher = new FdmMesherComposite(
                xMesher, yMesher, gMesher, exerciseMesher);

        // 3. Calculator.
        final FdmInnerValueCalculator zeroInnerValue = new FdmZeroInnerValue();

        final Payoff zeroStrikeCall = new PlainVanillaPayoff(Option.Type.Call, 0.0);

        final List<ShapePoint> fuelShapePts  = (fuelShape_  != null) ? fuelShape_.shape  : null;
        final List<ShapePoint> powerShapePts = (powerShape_ != null) ? powerShape_.shape : null;

        // Fuel grid lives on direction 2 (gas log-spot), wrap zero-strike-call
        // exp(f(t) + U).
        final FdmInnerValueCalculator fuelPrice =
                new FdmExpExtOUInnerValueCalculator(
                        zeroStrikeCall, mesher, fuelShapePts, 2);

        // Power grid uses (X, Y) on directions 0 and 1, wrap
        // zero-strike-call exp(f(t) + X + Y).
        final FdmInnerValueCalculator powerPrice =
                new FdmExtOUJumpModelInnerValue(
                        zeroStrikeCall, mesher, powerShapePts);

        QL.require(BasketPayoff.class.isAssignableFrom(arguments_.payoff.getClass()),
                "basket payoff expected");
        final BasketPayoff basketPayoff = (BasketPayoff) arguments_.payoff;

        // C++ FdmSparkSpreadInnerValue: s[0] = powerPrice, s[1] = fuelPrice.
        final FdmInnerValueCalculator sparkSpread =
                new FdmSparkSpreadInnerValue(basketPayoff, fuelPrice, powerPrice);

        // 4. Step conditions.
        final FdmStepConditionComposite.Conditions stepConditions =
                new FdmStepConditionComposite.Conditions();
        final List<List<Double>> stoppingTimes = new ArrayList<>();

        // 4.1 Bermudan-exercise step condition.
        stoppingTimes.add(exerciseTimes);

        final FdmVPPStepCondition.Mesher mesh =
                new FdmVPPStepCondition.Mesher(3, mesher);

        final FdmVPPStepCondition stepCondition = stepConditionFactory.build(
                mesh, fuelCostAddon_, fuelPrice, sparkSpread);

        stepConditions.add(stepCondition);

        final FdmStepConditionComposite conditions =
                new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 5. Boundary conditions (empty).
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Set up solver.
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, zeroInnerValue,
                maturity, tGrid_, 0);

        final FdmKlugeExtOUSolver solver = new FdmKlugeExtOUSolver(
                process_, rTS_, solverDesc, schemeDesc_);

        // 7. Sample at (X0, Y0, U0, e_i) for every i on the exercise-state
        //    axis, then take the maximum.
        final Array initialValues = process_.initialValues();
        final double[] x = new double[4];
        x[0] = initialValues.get(0);
        x[1] = initialValues.get(1);
        x[2] = initialValues.get(2);

        final double tol = 1.0e-8;
        final double[] exerciseLocs = exerciseMesher.locations();
        final double minExerciseValue = exerciseLocs[0];
        final double maxExerciseValue = exerciseLocs[exerciseLocs.length - 1];

        final int n = exerciseMesher.size();
        final Array results = new Array(n);
        for (int i = 0; i < n; ++i) {
            // Clamp the exercise-axis location away from the open ends so the
            // multi-cubic-spline interpolation stays inside the grid envelope.
            x[3] = Math.max(minExerciseValue + tol,
                            Math.min(exerciseMesher.location(i),
                                     maxExerciseValue - tol));
            results.set(i, solver.valueAt(x));
        }
        results_.value = stepCondition.maxValue(results);
    }

    /** Returns the FDM scheme descriptor used by the engine. */
    public FdmSchemeDesc schemeDesc() {
        return schemeDesc_;
    }

    /** Returns the fuel-side time shape (may be {@code null}). */
    public Shape fuelShape() {
        return fuelShape_;
    }

    /** Returns the power-side time shape (may be {@code null}). */
    public Shape powerShape() {
        return powerShape_;
    }

    /**
     * Inner-value calculator implementing the C++ anonymous
     * {@code FdmSparkSpreadInnerValue}: stacks power and fuel single-leg
     * inner-value calculators and applies a {@link BasketPayoff} on the
     * pair {@code (power, fuel)}.
     */
    private static final class FdmSparkSpreadInnerValue
            implements FdmInnerValueCalculator {

        private final BasketPayoff basketPayoff_;
        private final FdmInnerValueCalculator fuelPrice_;
        private final FdmInnerValueCalculator powerPrice_;

        FdmSparkSpreadInnerValue(final BasketPayoff basketPayoff,
                                 final FdmInnerValueCalculator fuelPrice,
                                 final FdmInnerValueCalculator powerPrice) {
            this.basketPayoff_ = basketPayoff;
            this.fuelPrice_    = fuelPrice;
            this.powerPrice_   = powerPrice;
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            final double[] s = new double[2];
            s[0] = powerPrice_.innerValue(iter, t);
            s[1] = fuelPrice_.innerValue(iter, t);
            return basketPayoff_.get(s);
        }

        @Override
        public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
            return innerValue(iter, t);
        }
    }
}
