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
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator.ShapePoint;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.instruments.SwingExercise;
import org.jquantlib.instruments.VanillaSwingOption;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.*;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm3DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmSimpleSwingCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmZeroInnerValue;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.termstructures.YieldTermStructure;

import java.util.ArrayList;
import java.util.List;

/**
 * Finite-differences engine for simple swing options driven by the Kluge (OU + exp-jumps) model.
 *
 * <p>Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdsimpleextoujumpswingengine.{hpp,cpp}}.
 *
 * <p>Builds a 3D mesh
 * ({@code xGrid} log-OU points × {@code yGrid} jump points × {@code maxExerciseRights+1} uniform exercise-counter
 * points) with a {@link FdmZeroInnerValue} terminal payoff (cash flows accrue only at exercise times via the swing step
 * condition), wires a Bermudan {@link FdmSimpleSwingCondition} on the exercise dates, and rolls back with a
 * {@link Fdm3DimSolver}.
 *
 * <h3>Defaults (matching C++ v1.42.1)</h3>
 * {@code tGrid = 50, xGrid = 200, yGrid = 50, shape = null, scheme = Hundsdorfer}.
 *
 * <p>The C++ {@code FdSimple3dExtOUJumpSolver} wrapper around the operator
 * is inlined here because the only other call site is the engine itself.
 *
 * @author Phase 5e.5b-CFC-d-211 port
 */
public class FdSimpleExtOUJumpSwingEngine
        extends GenericEngine< VanillaSwingOption.Arguments, VanillaSwingOption.Results > {

    private static final int GAUSS_LAGUERRE_ORDER = 32;

    private final ExtOUWithJumpsProcess process_;
    private final YieldTermStructure rTS_;
    private final int tGrid_;
    private final int xGrid_;
    private final int yGrid_;
    private final List< ShapePoint > shape_;
    private final FdmSchemeDesc schemeDesc_;

    /** Convenience — C++ defaults (tGrid=50, xGrid=200, yGrid=50, no shape, Hundsdorfer). */
    public FdSimpleExtOUJumpSwingEngine(final ExtOUWithJumpsProcess process, final YieldTermStructure rTS) {
        this(process, rTS, 50, 200, 50, null, FdmSchemeDesc.Hundsdorfer());
    }

    /** Grid-size constructor (default no shape, Hundsdorfer scheme). */
    public FdSimpleExtOUJumpSwingEngine(final ExtOUWithJumpsProcess process, final YieldTermStructure rTS,
            final int tGrid, final int xGrid, final int yGrid) {
        this(process, rTS, tGrid, xGrid, yGrid, null, FdmSchemeDesc.Hundsdorfer());
    }

    /** Grid + shape constructor (Hundsdorfer default scheme). */
    public FdSimpleExtOUJumpSwingEngine(final ExtOUWithJumpsProcess process, final YieldTermStructure rTS,
            final int tGrid, final int xGrid, final int yGrid, final List< ShapePoint > shape) {
        this(process, rTS, tGrid, xGrid, yGrid, shape, FdmSchemeDesc.Hundsdorfer());
    }

    /** Full constructor mirroring C++ v1.42.1. */
    public FdSimpleExtOUJumpSwingEngine(final ExtOUWithJumpsProcess process, final YieldTermStructure rTS,
            final int tGrid, final int xGrid, final int yGrid, final List< ShapePoint > shape,
            final FdmSchemeDesc schemeDesc) {
        super(new VanillaSwingOption.ArgumentsImpl(), new VanillaSwingOption.ResultsImpl());
        QL.require(process != null, "null ExtOUWithJumpsProcess");
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

    @Override
    public void calculate() {

        final VanillaSwingOption.ArgumentsImpl a = (VanillaSwingOption.ArgumentsImpl) arguments_;

        // 1. Exercise — swing-only (VanillaSwingOption.ArgumentsImpl field is
        // typed as SwingExercise; null-check mirrors the C++ dynamic_pointer_cast).
        QL.require(a.exercise != null, "Swing exercise supported only");
        final SwingExercise swingExercise = a.exercise;

        // 2. Mesher
        final double[] exTimesArr = swingExercise.exerciseTimes(rTS_.dayCounter(), rTS_.referenceDate());
        final List< Double > exerciseTimes = new ArrayList<>(exTimesArr.length);
        for ( final double t : exTimesArr ) {
            exerciseTimes.add(t);
        }
        final double maturity = exTimesArr[exTimesArr.length - 1];

        final ExtendedOrnsteinUhlenbeckProcess ouProcess = process_.getExtendedOrnsteinUhlenbeckProcess();

        final Fdm1dMesher xMesher = new FdmSimpleProcess1dMesher(xGrid_, ouProcess, maturity);

        final Fdm1dMesher yMesher = new ExponentialJump1dMesher(yGrid_, process_.beta(), process_.jumpIntensity(),
                process_.eta());

        final Fdm1dMesher exerciseMesher = new Uniform1dMesher(0.0, a.maxExerciseRights,
                a.maxExerciseRights + 1);

        final FdmMesher mesher = new FdmMesherComposite(xMesher, yMesher, exerciseMesher);

        // 3. Inner-value calculator — zero terminal payoff; cash flows enter via
        //    the swing step condition only.
        final FdmInnerValueCalculator calculator = new FdmZeroInnerValue();

        // 4. Step conditions — Bermudan-style swing on axis 2.
        final List< List< Double > > stoppingTimes = new ArrayList<>();
        stoppingTimes.add(exerciseTimes);

        final FdmInnerValueCalculator exerciseCalculator = new FdmExtOUJumpModelInnerValue(a.payoff, mesher, shape_);

        final FdmStepConditionComposite.Conditions stepConditions = new FdmStepConditionComposite.Conditions();
        final StepCondition< Array > swingCondition = new FdmSimpleSwingCondition(exerciseTimes, mesher,
                exerciseCalculator, 2, a.minExerciseRights);
        stepConditions.add(swingCondition);

        final FdmStepConditionComposite conditions = new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 5. Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver — inlined C++ FdmSimple3dExtOUJumpSolver: build op, run
        //    Fdm3DimSolver, sample at (x0, y0, 0).
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid_,
                0);

        final FdmLinearOpComposite op = new FdmExtOUJumpOp(mesher, process_, rTS_, boundaries, GAUSS_LAGUERRE_ORDER);

        final Fdm3DimSolver solver = new Fdm3DimSolver(solverDesc, schemeDesc_, op);

        final double x = process_.initialValues().get(0);
        final double y = process_.initialValues().get(1);

        final VanillaSwingOption.ResultsImpl r = (VanillaSwingOption.ResultsImpl) results_;
        r.value = solver.interpolateAt(x, y, 0.0);
    }
}
