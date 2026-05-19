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
 Copyright (C) 2014 Ralph Schreyer
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator.ShapePoint;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.instruments.*;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.*;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmSimpleStorageCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Finite-differences engine for a simple gas-storage option driven by the extended Ornstein-Uhlenbeck process.
 *
 * <p>Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdsimpleextoustorageengine.{hpp,cpp}}.</p>
 *
 * <p>Pricing path (mirrors the C++ {@code calculate()} body):</p>
 * <ol>
 *   <li>build a 2D mesh: {@code FdmSimpleProcess1dMesher} along the OU
 *       log-spot axis x, and a storage-level y-mesh that is either uniform
 *       on {@code [0, capacity]} (when {@code yGrid} is supplied) or a C++
 *       "elevator" {@code Predefined1dMesher} built from
 *       {@code {capacity, 0, capacity, dy, capacity-dy, 2dy, ...}} with
 *       duplicates removed by a close-enough tolerance;</li>
 *   <li>terminal inner-value calculator returns {@code exp(x) * y} (the C++
 *       anonymous {@code FdmStorageValue}) at maturity;</li>
 *   <li>Bermudan {@link FdmSimpleStorageCondition} on the exercise dates
 *       evaluates inject/withdraw/wait + every intermediate storage-grid
 *       point within the per-step {@code changeRate} window;</li>
 *   <li>solve with {@link FdmSimple2dExtOUSolver} (Douglas scheme by default);</li>
 *   <li>sample the rolled-back surface at {@code (x = process.x0(),
 *       y = arguments.load)}.</li>
 * </ol>
 *
 * @author Phase 5e.5b-CFC-d-215 port (calculate body)
 */
public class FdSimpleExtOUStorageEngine
        extends GenericEngine< VanillaStorageOption.ArgumentsImpl, OneAssetOption.ResultsImpl > {

    private final ExtendedOrnsteinUhlenbeckProcess process_;
    private final YieldTermStructure rTS_;
    private final int tGrid_;
    private final int xGrid_;
    private final Integer yGrid_;
    private final List< ShapePoint > shape_;
    private final FdmSchemeDesc schemeDesc_;

    /**
     * Convenience constructor — C++ defaults
     * ({@code tGrid=50, xGrid=100, yGrid=null (elevator mesh), no shape, Douglas scheme}).
     */
    public FdSimpleExtOUStorageEngine(final ExtendedOrnsteinUhlenbeckProcess process, final YieldTermStructure rTS) {
        this(process, rTS, 50, 100, null, null, FdmSchemeDesc.Douglas());
    }

    public FdSimpleExtOUStorageEngine(final ExtendedOrnsteinUhlenbeckProcess process, final YieldTermStructure rTS,
            final int tGrid, final int xGrid) {
        this(process, rTS, tGrid, xGrid, null, null, FdmSchemeDesc.Douglas());
    }

    public FdSimpleExtOUStorageEngine(final ExtendedOrnsteinUhlenbeckProcess process, final YieldTermStructure rTS,
            final int tGrid, final int xGrid, final Integer yGrid, final List< ShapePoint > shape,
            final FdmSchemeDesc schemeDesc) {
        super(new VanillaStorageOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(process != null, "null ExtendedOrnsteinUhlenbeckProcess");
        QL.require(rTS != null, "null risk-free term structure");
        QL.require(schemeDesc != null, "null FDM scheme descriptor");
        this.process_ = process;
        this.rTS_ = rTS;
        this.tGrid_ = tGrid;
        this.xGrid_ = xGrid;
        this.yGrid_ = yGrid;
        this.shape_ = shape;
        this.schemeDesc_ = schemeDesc;
    }

    /** Returns the driving process. */
    public ExtendedOrnsteinUhlenbeckProcess process() {
        return process_;
    }

    /** Returns the risk-free term structure. */
    public YieldTermStructure rTS() {
        return rTS_;
    }

    /** Returns the number of time-grid points. */
    public int tGrid() {
        return tGrid_;
    }

    /** Returns the number of x-grid (log-spot) points. */
    public int xGrid() {
        return xGrid_;
    }

    /**
     * Returns the y-grid size (number of storage levels for the uniform mesher, or {@code null} for the C++ "elevator"
     * mesher built from the capacity/change-rate of the storage option).
     */
    public Integer yGrid() {
        return yGrid_;
    }

    /** Returns the time-shape descriptor (may be {@code null}). */
    public List< ShapePoint > shape() {
        return shape_;
    }

    /** Returns the FDM scheme descriptor. */
    public FdmSchemeDesc schemeDesc() {
        return schemeDesc_;
    }

    @Override
    public void calculate() {
        // 1. Exercise — C++ QL_REQUIRE(arguments_.exercise->type() == Bermudan).
        //    BermudanExercise with a single date silently downgrades to
        //    European in jquantlib (see exercise/BermudanExercise.java:96),
        //    so accept European here too to match that behaviour.
        QL.require(arguments_.exercise.type() == Exercise.Type.Bermudan
                || arguments_.exercise.type() == Exercise.Type.European, "Bermudan exercise supported only");

        // 2. Mesher
        final Date refDate = rTS_.referenceDate();
        final double maturity = rTS_.dayCounter().yearFraction(refDate, arguments_.exercise.lastDate());

        final Fdm1dMesher xMesher = new FdmSimpleProcess1dMesher(xGrid_, process_, maturity);

        final Fdm1dMesher storageMesher;
        if ( yGrid_ == null ) {
            // Elevator mesher: build the C++ set
            //   { capacity }
            //   union { level, capacity - level | level = 0, dy, 2dy, ..., <= capacity }
            // then sort + dedupe by "close-enough" tolerance.
            final List< Double > storageValues = new ArrayList< Double >();
            storageValues.add(arguments_.capacity);
            for ( double level = 0.0; level <= arguments_.capacity; level += arguments_.changeRate ) {
                storageValues.add(level);
                storageValues.add(arguments_.capacity - level);
            }
            // Close-enough-aware ordered set (C++ LessButNotCloseEnough).
            final TreeSet< Double > ordered = new TreeSet< Double >(new Comparator< Double >() {
                @Override
                public int compare(final Double a, final Double b) {
                    if ( Closeness.isCloseEnough(a.doubleValue(), b.doubleValue(), 100) ) {
                        return 0;
                    }
                    return Double.compare(a, b);
                }
            });
            ordered.addAll(storageValues);
            final double[] meshPts = new double[ordered.size()];
            int k = 0;
            for ( final Double v : ordered ) {
                meshPts[k++] = v.doubleValue();
            }
            storageMesher = new Predefined1dMesher(meshPts);
        } else {
            // Uniform mesher on [0, capacity].
            storageMesher = new Uniform1dMesher(0.0, arguments_.capacity, yGrid_.intValue());
        }

        final FdmMesher mesher = new FdmMesherComposite(xMesher, storageMesher);

        // 3. Storage value calculator: innerValue = exp(x) * y.
        final FdmInnerValueCalculator storageCalculator = new FdmStorageValue(mesher);

        // 4. Step conditions
        final FdmStepConditionComposite.Conditions stepConditions = new FdmStepConditionComposite.Conditions();
        final List< List< Double > > stoppingTimes = new ArrayList< List< Double > >();

        // 4.1 Bermudan exercise times
        final List< Double > exerciseTimes = new ArrayList< Double >();
        for ( final Date d : arguments_.exercise.dates() ) {
            final double t = rTS_.dayCounter().yearFraction(refDate, d);
            QL.require(t >= 0.0, "exercise dates must not contain past date");
            exerciseTimes.add(t);
        }
        stoppingTimes.add(exerciseTimes);

        // Underlying-price calculator (for buy/sell pricing inside the step
        // condition): zero-strike call on exp(x) with optional shape.
        final Payoff payoff = new PlainVanillaPayoff(Option.Type.Call, 0.0);
        final FdmInnerValueCalculator underlyingCalculator = new FdmExpExtOUInnerValueCalculator(payoff, mesher, shape_,
                0);

        final StepCondition< Array > storageCond = new FdmSimpleStorageCondition(exerciseTimes, mesher,
                underlyingCalculator, arguments_.changeRate);
        stepConditions.add(storageCond);

        final FdmStepConditionComposite conditions = new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 5. Boundary conditions (empty).
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver.
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, storageCalculator, maturity,
                tGrid_, 0);

        final FdmSimple2dExtOUSolver solver = new FdmSimple2dExtOUSolver(process_, rTS_, solverDesc, schemeDesc_);

        final double x = process_.x0();
        final double y = arguments_.load;

        results_.value = solver.valueAt(x, y);
    }

    /**
     * Mirror of C++ anonymous {@code FdmStorageValue}: returns
     * {@code exp(mesher.location(iter, 0)) * mesher.location(iter, 1)}.
     */
    private static final class FdmStorageValue implements FdmInnerValueCalculator {
        private final FdmMesher mesher_;

        FdmStorageValue(final FdmMesher mesher) {
            this.mesher_ = mesher;
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            final double s = Math.exp(mesher_.location(iter, 0));
            final double v = mesher_.location(iter, 1);
            return s * v;
        }

        @Override
        public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
            return innerValue(iter, t);
        }
    }
}
