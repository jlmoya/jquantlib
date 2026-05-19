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
 Copyright (C) 2010 Klaus Spanderen
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.operators.FdmBatesOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm2DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.model.equity.BatesModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.BatesProcess;
import org.jquantlib.processes.HestonProcess;

/**
 * Partial integro finite-differences Bates vanilla option engine.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/pricingengines/vanilla/fdbatesvanillaengine.{hpp,cpp}} (Phase 5h.5-Bates-b).
 *
 * <p>Solves the 2-factor Heston PDE on a {@code (log-spot, variance)}
 * grid with an additional jump-integro term (the partial-integro-differential equation for Bates). Reuses
 * {@link FdHestonVanillaEngine#getSolverDesc} for meshing / step-conditions / boundary set, then wires
 * {@link FdmBatesOp} (instead of {@code FdmHestonOp}) into a {@link Fdm2DimSolver}.
 *
 * <p>Limitations vs. C++ v1.42.1:
 * <ul>
 *   <li>The Java {@link BatesModel} does not expose {@code .process()},
 *       so the engine takes the {@link BatesProcess} as a separate
 *       constructor argument (matches {@link FdHestonVanillaEngine}).</li>
 *   <li>Greek queries ({@link OneAssetOption.Results#greeks().theta})
 *       inherit the same finite-difference accuracy notes as
 *       {@link FdHestonVanillaEngine}.</li>
 *   <li>{@code FdmBatesSolver} is not exposed as a separate type — its
 *       behaviour is inlined here for simplicity (matches the rest of
 *       the JQuantLib FD pricing-engine layer).</li>
 * </ul>
 *
 * @author JQuantLib
 * @see FdmBatesOp
 * @see FdHestonVanillaEngine
 */
public class FdBatesVanillaEngine
        extends GenericModelEngine< BatesModel, OneAssetOption.Arguments, OneAssetOption.Results > {

    private final BatesProcess batesProcess;
    private final DividendSchedule dividends;
    private final int tGrid, xGrid, vGrid, dampingSteps;
    private final FdmSchemeDesc schemeDesc;

    /** Convenience constructor — all C++ defaults, no dividends. */
    public FdBatesVanillaEngine(final BatesModel model, final BatesProcess process) {
        this(model, process, null, 100, 100, 50, 0, FdmSchemeDesc.Hundsdorfer());
    }

    /** Convenience constructor — explicit grid + scheme, no dividends. */
    public FdBatesVanillaEngine(final BatesModel model, final BatesProcess process, final int tGrid, final int xGrid,
            final int vGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(model, process, null, tGrid, xGrid, vGrid, dampingSteps, schemeDesc);
    }

    /** Full constructor — explicit grid + dividends. */
    public FdBatesVanillaEngine(final BatesModel model, final BatesProcess process, final DividendSchedule dividends,
            final int tGrid, final int xGrid, final int vGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        super(model, new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(model != null, "null Bates model");
        QL.require(process != null, "null Bates process");
        QL.require(schemeDesc != null, "null scheme descriptor");
        this.batesProcess = process;
        this.dividends = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid = tGrid;
        this.xGrid = xGrid;
        this.vGrid = vGrid;
        this.dampingSteps = dampingSteps;
        this.schemeDesc = schemeDesc;
    }

    @Override
    public void calculate() {
        // Reuse FdHestonVanillaEngine for meshing / step-conditions /
        // boundary set — mirrors C++
        // FdHestonVanillaEngine helperEngine(model_->process(), ...);
        // *helperEngine.getArguments() = arguments_;
        // FdmSolverDesc solverDesc = helperEngine.getSolverDesc(2.0);
        // The Java HestonModel does not expose process(); we instead reuse
        // the engine constructor's BatesProcess (which IS-A HestonProcess
        // — the Heston grid spec stays valid for the Bates PDE).
        final HestonProcess hestonView = batesProcess;
        final FdHestonVanillaEngine helperEngine = new FdHestonVanillaEngine(
                /* hestonModel */  model,
                /* hestonProcess */ hestonView, dividends, tGrid, xGrid, vGrid, dampingSteps,
                schemeDesc, /* mixingFactor */ 1.0);
        // Mirror C++: copy the option arguments into the helper so its
        // getSolverDesc() picks up the right payoff / exercise.
        copyArgumentsTo(helperEngine);

        final FdmSolverDesc solverDesc = helperEngine.getSolverDesc();

        final FdmBatesOp op = new FdmBatesOp(solverDesc.mesher, batesProcess, solverDesc.bcSet);
        final Fdm2DimSolver solver = new Fdm2DimSolver(solverDesc, schemeDesc, op);

        final double spot = batesProcess.s0().currentLink().value();
        final double v0 = batesProcess.v0().currentLink().value();

        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;
        final double logSpot = JQuantMath.log(spot);
        r.value = solver.interpolateAt(logSpot, v0);

        // Greeks via finite differences, matching FdHestonVanillaEngine
        // conventions (1% spot bump for delta / gamma; analytic-theta via
        // Fdm2DimSolver.thetaAt in log-spot space).
        final double eps = spot * 0.01;
        final double vUp = solver.interpolateAt(JQuantMath.log(spot + eps), v0);
        final double vDown = solver.interpolateAt(JQuantMath.log(spot - eps), v0);
        r.greeks().delta = (vUp - vDown) / (2.0 * eps);
        r.greeks().gamma = (vUp - 2.0 * r.value + vDown) / (eps * eps);
        r.greeks().theta = solver.thetaAt(logSpot, v0);
    }

    /**
     * Copy {@link #arguments_} into {@code helper.arguments_}. Mirrors C++
     * {@code *dynamic_cast<VanillaOption::arguments*>(helperEngine.getArguments()) = arguments_}.
     */
    private void copyArgumentsTo(final FdHestonVanillaEngine helper) {
        final OneAssetOption.ArgumentsImpl src = (OneAssetOption.ArgumentsImpl) this.arguments_;
        final OneAssetOption.ArgumentsImpl dst = (OneAssetOption.ArgumentsImpl) helper.getArguments();
        dst.payoff = src.payoff;
        dst.exercise = src.exercise;
    }
}
