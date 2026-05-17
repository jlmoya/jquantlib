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
import org.jquantlib.experimental.processes.KlugeExtOUProcess;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
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
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * FD Kluge / extended Ornstein–Uhlenbeck engine for a simple power–gas
 * spread option.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdklugeextouspreadengine.{hpp,cpp}}.</p>
 *
 * <p>The engine builds a 3D FD mesh (gas log-price {@code X}, jump-driven
 * spike component {@code Y}, power log-price {@code U}), composes a
 * {@link FdmSpreadPayoffInnerValue} from gas- and power-side
 * inner-value calculators, and solves the backward PIDE associated with
 * the {@link KlugeExtOUProcess}.</p>
 *
 * <p><strong>Phase 5e.5b-CFC-d-164 status:</strong> the mesh, calculator,
 * step-condition, and boundary-condition setup are faithful Java ports.
 * The terminal {@code valueAt} interpolation, however, requires a
 * 3-dimensional ADI solver ({@code FdmNdimSolver}) and the
 * {@code FdmKlugeExtOUOp} linear operator. Neither is yet ported to
 * Java; {@link #calculate()} therefore throws a {@link LibraryException}
 * with a descriptive message once the setup phase completes. This is
 * sufficient for sibling classes that need to reference the engine type
 * (and for future tests that exercise only the prep phase).</p>
 *
 * <p>The C++ engine is parameterised on N (default 3) for the
 * {@code FdmKlugeExtOUSolver&lt;N&gt;}. Java uses {@code N == 3} only,
 * which is also the C++ static-asserted minimum.</p>
 *
 * @author Phase 5e.5b-CFC-d-164 port
 */
public class FdKlugeExtOUSpreadEngine
        extends GenericEngine<OneAssetOption.Arguments, OneAssetOption.Results> {

    /**
     * Time-shape descriptor for the gas leg. Mirrors C++ typedef
     * {@code GasShape = FdmExtOUJumpModelInnerValue::Shape}.
     */
    public static final class GasShape {
        public final List<ShapePoint> shape;
        public GasShape(final List<ShapePoint> shape) {
            this.shape = shape;
        }
    }

    /**
     * Time-shape descriptor for the power leg. Mirrors C++ typedef
     * {@code PowerShape = FdmExtOUJumpModelInnerValue::Shape}.
     */
    public static final class PowerShape {
        public final List<ShapePoint> shape;
        public PowerShape(final List<ShapePoint> shape) {
            this.shape = shape;
        }
    }

    private final KlugeExtOUProcess klugeOUProcess_;
    private final YieldTermStructure rTS_;
    private final int tGrid_;
    private final int xGrid_;
    private final int yGrid_;
    private final int uGrid_;
    private final GasShape gasShape_;
    private final PowerShape powerShape_;
    private final FdmSchemeDesc schemeDesc_;

    /**
     * Convenience constructor — C++ defaults
     * ({@code tGrid=25, xGrid=50, yGrid=10, uGrid=25}, no shapes,
     * Hundsdorfer scheme).
     */
    public FdKlugeExtOUSpreadEngine(final KlugeExtOUProcess klugeOUProcess,
                                    final YieldTermStructure rTS) {
        this(klugeOUProcess, rTS, 25, 50, 10, 25, null, null,
                FdmSchemeDesc.Hundsdorfer());
    }

    /**
     * Grid-only constructor — no shapes, Hundsdorfer scheme.
     */
    public FdKlugeExtOUSpreadEngine(final KlugeExtOUProcess klugeOUProcess,
                                    final YieldTermStructure rTS,
                                    final int tGrid,
                                    final int xGrid,
                                    final int yGrid,
                                    final int uGrid) {
        this(klugeOUProcess, rTS, tGrid, xGrid, yGrid, uGrid, null, null,
                FdmSchemeDesc.Hundsdorfer());
    }

    /**
     * Full constructor mirroring C++ v1.42.1.
     *
     * @param klugeOUProcess driving 3-factor process
     * @param rTS            risk-free term structure (provides reference
     *                       date + day-counter and is propagated to the
     *                       solver)
     * @param tGrid          number of time steps
     * @param xGrid          number of gas log-price grid points
     * @param yGrid          number of spike-component grid points
     * @param uGrid          number of power log-price grid points
     * @param gasShape       time-shape for the gas leg (may be {@code null})
     * @param powerShape     time-shape for the power leg (may be {@code null})
     * @param schemeDesc     FDM scheme descriptor (default: Hundsdorfer)
     */
    public FdKlugeExtOUSpreadEngine(final KlugeExtOUProcess klugeOUProcess,
                                    final YieldTermStructure rTS,
                                    final int tGrid,
                                    final int xGrid,
                                    final int yGrid,
                                    final int uGrid,
                                    final GasShape gasShape,
                                    final PowerShape powerShape,
                                    final FdmSchemeDesc schemeDesc) {
        super(new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(klugeOUProcess != null, "null Kluge ExtOU process");
        QL.require(rTS != null, "null risk-free term structure");
        QL.require(schemeDesc != null, "null FDM scheme descriptor");
        this.klugeOUProcess_ = klugeOUProcess;
        this.rTS_            = rTS;
        this.tGrid_          = tGrid;
        this.xGrid_          = xGrid;
        this.yGrid_          = yGrid;
        this.uGrid_          = uGrid;
        this.gasShape_       = gasShape;
        this.powerShape_     = powerShape;
        this.schemeDesc_     = schemeDesc;
    }

    /**
     * Build the FD mesh, inner-value calculators, step- and
     * boundary-condition sets, then defer the final 3D solve to
     * {@code FdmKlugeExtOUSolver&lt;3&gt;} (not yet ported to Java).
     *
     * <p>The setup phase is a faithful translation of the C++
     * implementation, so that subclasses or future ports of the 3D
     * solver can pick up exactly the same {@link FdmSolverDesc} as the
     * C++ reference.</p>
     */
    @Override
    public void calculate() {
        // 1. Mesher
        final OneAssetOption.ArgumentsImpl args =
                (OneAssetOption.ArgumentsImpl) arguments_;

        final Date refDate = rTS_.referenceDate();
        final double maturity = rTS_.dayCounter()
                .yearFraction(refDate, args.exercise.lastDate());

        final ExtOUWithJumpsProcess klugeProcess = klugeOUProcess_.getKlugeProcess();
        final StochasticProcess1D ouProcess =
                klugeProcess.getExtendedOrnsteinUhlenbeckProcess();

        final Fdm1dMesher xMesher =
                new FdmSimpleProcess1dMesher(xGrid_, ouProcess, maturity);

        final Fdm1dMesher yMesher = new ExponentialJump1dMesher(
                yGrid_,
                klugeProcess.beta(),
                klugeProcess.jumpIntensity(),
                klugeProcess.eta());

        final Fdm1dMesher uMesher = new FdmSimpleProcess1dMesher(
                uGrid_, klugeOUProcess_.getExtOUProcess(), maturity);

        final FdmMesher mesher = new FdmMesherComposite(xMesher, yMesher, uMesher);

        // 2. Calculator
        QL.require(BasketPayoff.class.isAssignableFrom(args.payoff.getClass()),
                "basket payoff expected");
        final BasketPayoff basketPayoff = (BasketPayoff) args.payoff;

        final Payoff zeroStrikeCall = new PlainVanillaPayoff(Option.Type.Call, 0.0);

        final List<ShapePoint> gasShapePts   = (gasShape_   != null) ? gasShape_.shape   : null;
        final List<ShapePoint> powerShapePts = (powerShape_ != null) ? powerShape_.shape : null;

        final FdmInnerValueCalculator gasPrice =
                new FdmExpExtOUInnerValueCalculator(zeroStrikeCall, mesher,
                                                    gasShapePts, 2);

        final FdmInnerValueCalculator powerPrice =
                new FdmExtOUJumpModelInnerValue(zeroStrikeCall, mesher,
                                                powerShapePts);

        final FdmInnerValueCalculator calculator =
                new FdmSpreadPayoffInnerValue(basketPayoff, powerPrice, gasPrice);

        // 3. Step conditions
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        null, args.exercise, mesher, calculator,
                        rTS_.referenceDate(), rTS_.dayCounter());

        // 4. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver descriptor — ready to hand to FdmKlugeExtOUSolver<3>
        //    once that class (and FdmNdimSolver) are ported to Java.
        @SuppressWarnings("unused")
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, 0);

        throw new LibraryException(
                "FdKlugeExtOUSpreadEngine.calculate(): the 3D ADI solver "
              + "(FdmKlugeExtOUSolver<3> + FdmNdimSolver + FdmKlugeExtOUOp) "
              + "is not yet ported to Java (Phase 5e.5b-CFC-d-164 carry-forward). "
              + "Mesh, calculator, step conditions, and solver descriptor are "
              + "fully prepared; only the terminal valueAt(x) interpolation is "
              + "missing.");
    }

    /** Returns the FDM scheme descriptor used by the engine. */
    public FdmSchemeDesc schemeDesc() {
        return schemeDesc_;
    }

    /** Returns the gas-side time shape (may be {@code null}). */
    public GasShape gasShape() {
        return gasShape_;
    }

    /** Returns the power-side time shape (may be {@code null}). */
    public PowerShape powerShape() {
        return powerShape_;
    }
}
