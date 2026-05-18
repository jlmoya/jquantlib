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

import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.finitedifferences.FdmExpExtOUInnerValueCalculator.ShapePoint;
import org.jquantlib.experimental.processes.ExtOUWithJumpsProcess;
import org.jquantlib.experimental.processes.ExtendedOrnsteinUhlenbeckProcess;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.finitedifferences.meshers.ExponentialJump1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Finite-differences vanilla option engine for the Kluge (OU + exp-jumps)
 * model.
 *
 * <p>Java port of v1.42.1
 * {@code ql/experimental/finitedifferences/fdextoujumpvanillaengine.{hpp,cpp}}.
 *
 * <p>Builds a 2D mesh ({@code xGrid} log-OU points × {@code yGrid} jump
 * points), wires an {@link FdmExtOUJumpModelInnerValue} terminal payoff
 * over {@code S = exp(f(t) + X + Y)} where {@code f} is the optional
 * piecewise-constant time shape, and rolls back with the
 * {@link FdmExtOUJumpSolver} (Hundsdorfer scheme by default).
 *
 * <h3>Defaults (matching C++ v1.42.1)</h3>
 * {@code tGrid = 50, xGrid = 200, yGrid = 50,
 *  shape = null, scheme = Hundsdorfer}.
 *
 * <p>The engine is typed against {@link OneAssetOption} arguments/results
 * (which {@link VanillaOption} extends), matching the C++ engine which is
 * a {@code GenericEngine<VanillaOption::arguments, VanillaOption::results>}.
 *
 * @author Phase 5e.5b-CFC-d-211 port
 */
public class FdExtOUJumpVanillaEngine
        extends GenericEngine<OneAssetOption.Arguments, OneAssetOption.Results> {

    private final ExtOUWithJumpsProcess process_;
    private final YieldTermStructure rTS_;
    private final int tGrid_;
    private final int xGrid_;
    private final int yGrid_;
    private final List<ShapePoint> shape_;
    private final FdmSchemeDesc schemeDesc_;

    /** Convenience — C++ defaults (tGrid=50, xGrid=200, yGrid=50, no shape, Hundsdorfer). */
    public FdExtOUJumpVanillaEngine(final ExtOUWithJumpsProcess process,
                                    final YieldTermStructure rTS) {
        this(process, rTS, 50, 200, 50, null, FdmSchemeDesc.Hundsdorfer());
    }

    /** Grid-size constructor (default no shape, Hundsdorfer scheme). */
    public FdExtOUJumpVanillaEngine(final ExtOUWithJumpsProcess process,
                                    final YieldTermStructure rTS,
                                    final int tGrid,
                                    final int xGrid,
                                    final int yGrid) {
        this(process, rTS, tGrid, xGrid, yGrid, null, FdmSchemeDesc.Hundsdorfer());
    }

    /** Grid + shape constructor (Hundsdorfer default scheme). */
    public FdExtOUJumpVanillaEngine(final ExtOUWithJumpsProcess process,
                                    final YieldTermStructure rTS,
                                    final int tGrid,
                                    final int xGrid,
                                    final int yGrid,
                                    final List<ShapePoint> shape) {
        this(process, rTS, tGrid, xGrid, yGrid, shape, FdmSchemeDesc.Hundsdorfer());
    }

    /** Full constructor mirroring C++ v1.42.1. */
    public FdExtOUJumpVanillaEngine(final ExtOUWithJumpsProcess process,
                                    final YieldTermStructure rTS,
                                    final int tGrid,
                                    final int xGrid,
                                    final int yGrid,
                                    final List<ShapePoint> shape,
                                    final FdmSchemeDesc schemeDesc) {
        super(new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(process != null, "null ExtOUWithJumpsProcess");
        QL.require(rTS != null, "null risk-free term structure");
        QL.require(schemeDesc != null, "null FDM scheme descriptor");
        this.process_    = process;
        this.rTS_        = rTS;
        this.tGrid_      = tGrid;
        this.xGrid_      = xGrid;
        this.yGrid_      = yGrid;
        this.shape_      = shape;
        this.schemeDesc_ = schemeDesc;
    }

    @Override
    public void calculate() {

        final OneAssetOption.ArgumentsImpl a =
                (OneAssetOption.ArgumentsImpl) arguments_;

        // 1. Mesher
        final double maturity = rTS_.dayCounter().yearFraction(
                rTS_.referenceDate(), a.exercise.lastDate());

        final ExtendedOrnsteinUhlenbeckProcess ouProcess =
                process_.getExtendedOrnsteinUhlenbeckProcess();

        final Fdm1dMesher xMesher = new FdmSimpleProcess1dMesher(
                xGrid_, ouProcess, maturity);

        final Fdm1dMesher yMesher = new ExponentialJump1dMesher(
                yGrid_, process_.beta(), process_.jumpIntensity(), process_.eta());

        final FdmMesher mesher = new FdmMesherComposite(xMesher, yMesher);

        // 2. Inner-value calculator
        final FdmInnerValueCalculator calculator =
                new FdmExtOUJumpModelInnerValue(
                        (StrikedTypePayoff) a.payoff, mesher, shape_);

        // 3. Step conditions (Bermudan / American / European exercise + no divs)
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        new DividendSchedule(), a.exercise,
                        mesher, calculator,
                        rTS_.referenceDate(), rTS_.dayCounter());

        // 4. Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, 0);

        final FdmExtOUJumpSolver solver = new FdmExtOUJumpSolver(
                process_, rTS_, solverDesc, schemeDesc_);

        final double x = process_.initialValues().get(0);
        final double y = process_.initialValues().get(1);

        final OneAssetOption.ResultsImpl r =
                (OneAssetOption.ResultsImpl) results_;
        r.value = solver.valueAt(x, y);
    }
}
