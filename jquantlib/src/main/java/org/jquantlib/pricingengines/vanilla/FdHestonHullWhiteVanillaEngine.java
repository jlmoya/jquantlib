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
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.methods.finitedifferences.meshers.*;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmHestonHullWhiteSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.processes.HullWhiteProcess;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;

import java.util.ArrayList;
import java.util.List;

/**
 * Finite-differences Heston Hull-White vanilla option engine.
 * <p>
 * Java port of v1.42.1 {@code ql/pricingengines/vanilla/fdhestonhullwhitevanillaengine.{hpp,cpp}}.
 * <p>
 * Solves the 3-factor Heston-Hull-White PDE on a (log-S, v, r) grid using the Hundsdorfer
 * alternating-direction-implicit (ADI) scheme.
 * <p>
 * <strong>Control-variate correction.</strong> When
 * {@code controlVariate=true} (the C++ default), the engine subtracts a pure-Heston FD NPV
 * ({@link FdHestonVanillaEngine} on the same {@code (tGrid, xGrid, vGrid, dampingSteps, schemeDesc)}) and adds back the
 * analytic Heston NPV ({@link AnalyticHestonEngine} order 164). In the deterministic-vol Heston limit (e.g.
 * {@code sigma=QL_EPSILON}), the FD-HHW and FD-Heston discretization biases cancel almost exactly, recovering the
 * Brigo-Mercurio BSM-HW closed form at FD speed.
 * <p>
 * <strong>Multiple-strikes caching.</strong>
 * {@link #enableMultipleStrikesCaching(double[])} mirrors the C++ accelerator: a single FD-solver run is shared across
 * many strikes by evaluating the solver at {@code spot * (K_ref / K_i)} and rescaling the value/delta/gamma/theta
 * accordingly (C++ {@code fdhestonhullwhitevanillaengine.cpp:183-196}). Subsequent {@link #calculate()} calls whose
 * option matches a cached strike short-circuit to the cached results. The Java port uses the single-strike
 * {@link FdmBlackScholesMesher} on the reference strike (the C++ {@code FdmBlackScholesMultiStrikeMesher} is not yet
 * ported); this widens the equity mesh's effective coverage but keeps the value/delta/gamma rescaling identity intact.
 *
 * @author Phase 2m Track B port (initial)
 * @author Phase 5e.5b-CFC-d-258 (controlVariate + enableMultipleStrikesCaching)
 */
public class FdHestonHullWhiteVanillaEngine
        extends GenericModelEngine< HestonModel, OneAssetOption.Arguments, OneAssetOption.Results > {

    private final HestonProcess hestonProcess;
    private final HullWhiteProcess hwProcess;
    private final double corrEquityShortRate;
    private final int tGrid, xGrid, vGrid, rGrid, dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final boolean controlVariate;
    /** Cached results per strike, populated on first calculate() after caching is enabled. */
    private final List< CachedResult > cachedResults = new ArrayList< CachedResult >();
    /** Cached strikes (multi-strike acceleration). Empty = single-strike mode. */
    private double[] strikes = new double[0];

    /**
     * Construct a Heston-Hull-White FD engine with explicit control-variate choice. Mirrors v1.42.1
     * {@code FdHestonHullWhiteVanillaEngine(model, hwProcess, corrEquityShortRate, tGrid, xGrid, vGrid, rGrid,
     * dampingSteps, controlVariate, schemeDesc)}.
     *
     * @param hestonModel         calibrated Heston model (provides process)
     * @param hestonProcess       the Heston process (model parameter source)
     * @param hwProcess           Hull-White short-rate process
     * @param corrEquityShortRate correlation between equity and short rate
     * @param tGrid               number of time steps
     * @param xGrid               number of log-spot grid points
     * @param vGrid               number of variance grid points
     * @param rGrid               number of short-rate grid points
     * @param dampingSteps        number of implicit-Euler damping steps
     * @param controlVariate      enable analytic-Heston control-variate correction
     * @param schemeDesc          ADI scheme descriptor (default: Hundsdorfer)
     */
    public FdHestonHullWhiteVanillaEngine(final HestonModel hestonModel, final HestonProcess hestonProcess,
            final HullWhiteProcess hwProcess, final double corrEquityShortRate, final int tGrid, final int xGrid,
            final int vGrid, final int rGrid, final int dampingSteps, final boolean controlVariate,
            final FdmSchemeDesc schemeDesc) {
        super(hestonModel, new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        this.hestonProcess = hestonProcess;
        this.hwProcess = hwProcess;
        this.corrEquityShortRate = corrEquityShortRate;
        this.tGrid = tGrid;
        this.xGrid = xGrid;
        this.vGrid = vGrid;
        this.rGrid = rGrid;
        this.dampingSteps = dampingSteps;
        this.controlVariate = controlVariate;
        this.schemeDesc = schemeDesc;
    }

    /**
     * Convenience constructor preserving the pre-Phase 5e.5b-CFC-d-258 signature (no controlVariate parameter);
     * defaults {@code controlVariate=false} to keep callers that built against the Phase 2m signature
     * binary-identical.
     */
    public FdHestonHullWhiteVanillaEngine(final HestonModel hestonModel, final HestonProcess hestonProcess,
            final HullWhiteProcess hwProcess, final double corrEquityShortRate, final int tGrid, final int xGrid,
            final int vGrid, final int rGrid, final int dampingSteps, final FdmSchemeDesc schemeDesc) {
        this(hestonModel, hestonProcess, hwProcess, corrEquityShortRate, tGrid, xGrid, vGrid, rGrid, dampingSteps,
                /* controlVariate */ false, schemeDesc);
    }

    /** Convenience constructor with default grids and Hundsdorfer scheme. */
    public FdHestonHullWhiteVanillaEngine(final HestonModel hestonModel, final HestonProcess hestonProcess,
            final HullWhiteProcess hwProcess, final double corrEquityShortRate) {
        this(hestonModel, hestonProcess, hwProcess, corrEquityShortRate, 50, 100, 40, 20, 0, /* controlVariate */ false,
                FdmSchemeDesc.Hundsdorfer());
    }

    /**
     * Enable the multi-strike accelerator: a single FD-solver run is shared across all supplied strikes, with
     * per-strike value/delta/ gamma/theta derived by spot-rescaling (C++
     * {@code fdhestonhullwhitevanillaengine.cpp:183-196}). Caching only applies on subsequent {@link #calculate()}
     * calls whose payoff is a {@link PlainVanillaPayoff} matching a strike in this list.
     */
    public void enableMultipleStrikesCaching(final double[] strikes) {
        this.strikes = strikes.clone();
        this.cachedResults.clear();
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args = (OneAssetOption.ArgumentsImpl) arguments_;
        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;

        // 1. Cache lookup: short-circuit when the requesting option's
        // (exercise dates, option type, strike) was previously computed.
        if ( args.payoff instanceof PlainVanillaPayoff ) {
            final PlainVanillaPayoff p1 = (PlainVanillaPayoff) args.payoff;
            for ( final CachedResult c : cachedResults ) {
                final PlainVanillaPayoff p2 = (PlainVanillaPayoff) c.payoff;
                if ( c.exercise.lastDate().equals(args.exercise.lastDate()) && p1.optionType() == p2.optionType()
                        && p1.strike() == p2.strike() ) {
                    res.value = c.value;
                    res.greeks().delta = c.delta;
                    res.greeks().gamma = c.gamma;
                    res.greeks().theta = c.theta;
                    return;
                }
            }
        }

        // 2. Mesher
        final double maturity = hestonProcess.time(args.exercise.lastDate());

        // 2.1 Variance mesher (Heston CIR v-process)
        final int tGridMin = 5;
        final FdmHestonVarianceMesher varianceMesher = new FdmHestonVarianceMesher(vGrid, hestonProcess, maturity,
                Math.max(tGridMin, tGrid / 50), 0.0001);

        // 2.2 Equity mesher (log-spot). The Java port lacks
        // FdmBlackScholesMultiStrikeMesher so even when strikes_ is populated
        // we use the single-strike mesher centred on the requesting payoff
        // strike; the FD-solver value rescaling identity used in the
        // multi-strike cache fill (C++:184-196) holds independently of mesh
        // breadth.
        final StrikedTypePayoff payoff = (StrikedTypePayoff) args.payoff;
        QL.require(payoff != null, "wrong payoff type given");

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(xGrid,
                FdmBlackScholesMesher.processHelper(hestonProcess.s0(), hestonProcess.dividendYield(),
                        hestonProcess.riskFreeRate(), varianceMesher.volaEstimate()), maturity, payoff.strike(),
                new org.jquantlib.instruments.DividendSchedule(), 0.0);

        // 2.3 Short-rate mesher (Hull-White OU process)
        final OrnsteinUhlenbeckProcess ouProcess = new OrnsteinUhlenbeckProcess(hwProcess.a(), hwProcess.sigma());
        final Fdm1dMesher shortRateMesher = new FdmSimpleProcess1dMesher(rGrid, ouProcess, maturity);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher, varianceMesher, shortRateMesher);

        // 3. Calculator
        final FdmLogInnerValue calculator = new FdmLogInnerValue(args.payoff, mesher, 0);

        // 4. Step conditions
        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(
                new org.jquantlib.instruments.DividendSchedule(), args.exercise, mesher, calculator,
                hestonProcess.riskFreeRate().currentLink().referenceDate(),
                hestonProcess.riskFreeRate().currentLink().dayCounter());

        // 5. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid,
                dampingSteps);

        final FdmHestonHullWhiteSolver solver = new FdmHestonHullWhiteSolver(hestonProcess, hwProcess,
                corrEquityShortRate, solverDesc, schemeDesc);

        final double spot = hestonProcess.s0().currentLink().value();
        final double v0 = hestonProcess.v0().currentLink().value();
        final double r0 = 0.0; // short-rate starts at OU mean = 0

        res.value = solver.valueAt(spot, v0, r0);
        res.greeks().delta = solver.deltaAt(spot, v0, r0, spot * 0.01);
        res.greeks().gamma = solver.gammaAt(spot, v0, r0, spot * 0.01);
        res.greeks().theta = solver.thetaAt(spot, v0, r0);

        // 7. Populate the per-strike cache (multi-strike acceleration).
        // C++:184-196 - reuse the FD solver by evaluating at
        // spot * (Kref/Ki) and rescaling.
        cachedResults.clear();
        for ( final double strike : strikes ) {
            final double d = payoff.strike() / strike;
            final CachedResult c = new CachedResult();
            c.exercise = args.exercise;
            c.payoff = new PlainVanillaPayoff(payoff.optionType(), strike);
            c.value = solver.valueAt(spot * d, v0, r0) / d;
            c.delta = solver.deltaAt(spot * d, v0, r0, spot * d * 0.01);
            c.gamma = solver.gammaAt(spot * d, v0, r0, spot * d * 0.01) * d;
            c.theta = solver.thetaAt(spot * d, v0, r0) / d;
            cachedResults.add(c);
        }

        // 8. Control-variate correction.
        //
        // Replaces the FD-HHW NPV's discretization bias by the bias of a
        // pure-Heston FD NPV (same grids/scheme), then adds back the
        // analytic Heston NPV. In the deterministic-vol Heston limit
        // sigma -> 0 (used by testBsmHullWhitePricing) FD-HHW and
        // FD-Heston share the same v-direction degeneracy, so the bias
        // cancels and the engine recovers the BSM-HW closed form.
        // Mirrors C++ fdhestonhullwhitevanillaengine.cpp:198-229.
        if ( controlVariate ) {
            final HestonModel hModel = this.model;
            final PricingEngine analyticEngine = new AnalyticHestonEngine(hModel, hestonProcess, 164);
            final Exercise europeanLast = new EuropeanExercise(args.exercise.lastDate());

            final EuropeanOption option = new EuropeanOption(
                    new PlainVanillaPayoff(payoff.optionType(), payoff.strike()), europeanLast);
            option.setPricingEngine(analyticEngine);
            double analyticNPV = option.NPV();

            final FdHestonVanillaEngine fdEngine = new FdHestonVanillaEngine(hModel, hestonProcess, tGrid, xGrid, vGrid,
                    dampingSteps, schemeDesc);
            option.setPricingEngine(fdEngine);
            double fdNPV = option.NPV();

            res.value += analyticNPV - fdNPV;

            for ( final CachedResult c : cachedResults ) {
                final PlainVanillaPayoff cp = (PlainVanillaPayoff) c.payoff;
                final EuropeanOption cvOption = new EuropeanOption(new PlainVanillaPayoff(cp.optionType(), cp.strike()),
                        europeanLast);
                cvOption.setPricingEngine(analyticEngine);
                analyticNPV = cvOption.NPV();
                cvOption.setPricingEngine(fdEngine);
                fdNPV = cvOption.NPV();
                c.value += analyticNPV - fdNPV;
            }
        }
    }

    /** Per-strike cache entry (value + Greeks, post-CV correction). */
    private static final class CachedResult {
        Exercise exercise;
        StrikedTypePayoff payoff;
        double value, delta, gamma, theta;
    }
}
