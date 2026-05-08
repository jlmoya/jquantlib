/*
 Copyright (C) 2009 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmHestonVarianceMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmHestonHullWhiteSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.processes.HullWhiteProcess;

/**
 * Finite-differences Heston Hull-White vanilla option engine.
 * <p>
 * Java port of v1.42.1
 * {@code ql/pricingengines/vanilla/fdhestonhullwhitevanillaengine.{hpp,cpp}}.
 * <p>
 * Solves the 3-factor Heston–Hull-White PDE on a (log-S, v, r) grid using
 * the Hundsdorfer alternating-direction-implicit (ADI) scheme.
 * <p>
 * <strong>Control-variate correction</strong> is not implemented in this port
 * (it requires {@code AnalyticHestonEngine} and {@code FdHestonVanillaEngine}
 * which are deferred to a follow-up sub-task). Pass {@code controlVariate=false}.
 *
 * @author Phase 2m Track B port
 */
public class FdHestonHullWhiteVanillaEngine
        extends GenericModelEngine<HestonModel,
                                   OneAssetOption.Arguments,
                                   OneAssetOption.Results> {

    private final HestonProcess  hestonProcess;
    private final HullWhiteProcess hwProcess;
    private final double corrEquityShortRate;
    private final int    tGrid, xGrid, vGrid, rGrid, dampingSteps;
    private final FdmSchemeDesc schemeDesc;

    /**
     * Construct a Heston–Hull-White FD engine.
     *
     * @param hestonModel        calibrated Heston model (provides process)
     * @param hestonProcess      the Heston process (model parameter source)
     * @param hwProcess          Hull-White short-rate process
     * @param corrEquityShortRate correlation between equity and short rate
     * @param tGrid              number of time steps
     * @param xGrid              number of log-spot grid points
     * @param vGrid              number of variance grid points
     * @param rGrid              number of short-rate grid points
     * @param dampingSteps       number of implicit-Euler damping steps
     * @param schemeDesc         ADI scheme descriptor (default: Hundsdorfer)
     */
    public FdHestonHullWhiteVanillaEngine(
            final HestonModel   hestonModel,
            final HestonProcess hestonProcess,
            final HullWhiteProcess hwProcess,
            final double corrEquityShortRate,
            final int    tGrid,
            final int    xGrid,
            final int    vGrid,
            final int    rGrid,
            final int    dampingSteps,
            final FdmSchemeDesc schemeDesc) {
        super(hestonModel,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        this.hestonProcess       = hestonProcess;
        this.hwProcess           = hwProcess;
        this.corrEquityShortRate = corrEquityShortRate;
        this.tGrid               = tGrid;
        this.xGrid               = xGrid;
        this.vGrid               = vGrid;
        this.rGrid               = rGrid;
        this.dampingSteps        = dampingSteps;
        this.schemeDesc          = schemeDesc;
    }

    /** Convenience constructor with default grids and Hundsdorfer scheme. */
    public FdHestonHullWhiteVanillaEngine(
            final HestonModel   hestonModel,
            final HestonProcess hestonProcess,
            final HullWhiteProcess hwProcess,
            final double corrEquityShortRate) {
        this(hestonModel, hestonProcess, hwProcess, corrEquityShortRate,
             50, 100, 40, 20, 0, FdmSchemeDesc.Hundsdorfer());
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args =
            (OneAssetOption.ArgumentsImpl) arguments_;

        // 2. Mesher
        final double maturity = hestonProcess.time(
            args.exercise.lastDate());

        // 2.1 Variance mesher (Heston CIR v-process)
        final int tGridMin = 5;
        final FdmHestonVarianceMesher varianceMesher = new FdmHestonVarianceMesher(
            vGrid, hestonProcess, maturity,
            Math.max(tGridMin, tGrid / 50), 0.0001);

        // 2.2 Equity mesher (log-spot)
        final StrikedTypePayoff payoff =
            (StrikedTypePayoff) args.payoff;
        QL.require(payoff != null, "wrong payoff type given");

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
            xGrid,
            FdmBlackScholesMesher.processHelper(
                hestonProcess.s0(),
                hestonProcess.dividendYield(),
                hestonProcess.riskFreeRate(),
                varianceMesher.volaEstimate()),
            maturity, payoff.strike(),
            new org.jquantlib.instruments.DividendSchedule(),
            0.0);

        // 2.3 Short-rate mesher (Hull-White OU process)
        final OrnsteinUhlenbeckProcess ouProcess =
            new OrnsteinUhlenbeckProcess(hwProcess.a(), hwProcess.sigma());
        final Fdm1dMesher shortRateMesher =
            new FdmSimpleProcess1dMesher(rGrid, ouProcess, maturity);

        final FdmMesher mesher = new FdmMesherComposite(
            equityMesher, varianceMesher, shortRateMesher);

        // 3. Calculator
        final FdmLogInnerValue calculator =
            new FdmLogInnerValue(args.payoff, mesher, 0);

        // 4. Step conditions
        final FdmStepConditionComposite conditions =
            FdmStepConditionComposite.vanillaComposite(
                new org.jquantlib.instruments.DividendSchedule(),
                args.exercise,
                mesher, calculator,
                hestonProcess.riskFreeRate().currentLink().referenceDate(),
                hestonProcess.riskFreeRate().currentLink().dayCounter());

        // 5. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
            mesher, boundaries, conditions, calculator, maturity, tGrid, dampingSteps);

        final FdmHestonHullWhiteSolver solver = new FdmHestonHullWhiteSolver(
            hestonProcess, hwProcess, corrEquityShortRate, solverDesc, schemeDesc);

        final double spot = hestonProcess.s0().currentLink().value();
        final double v0   = hestonProcess.v0().currentLink().value();
        final double r0   = 0.0; // short-rate starts at OU mean = 0

        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;
        res.value = solver.valueAt(spot, v0, r0);
        res.greeks().delta = solver.deltaAt(spot, v0, r0, spot * 0.01);
        res.greeks().gamma = solver.gammaAt(spot, v0, r0, spot * 0.01);
        res.greeks().theta = solver.thetaAt(spot, v0, r0);
    }
}
