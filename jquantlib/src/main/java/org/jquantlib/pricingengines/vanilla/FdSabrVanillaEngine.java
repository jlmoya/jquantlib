/*
 Copyright (C) 2019 Klaus Spanderen
 Copyright (C) 2026 JQuantLib migration contributors.

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

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.Concentrating1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmCEV1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmSabrOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm2DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmCellAveragingInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmDiscountDirichletBoundary;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOp;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.Sabr;
import org.jquantlib.time.Date;

/**
 * Finite-differences pricing engine for the SABR model.
 *
 * <p>Prices European vanilla options under the SABR dynamics:
 * <pre>
 *   df_t = alpha_t * f_t^beta * dW_t
 *   d(alpha_t) = nu * alpha_t * dZ_t
 *   corr(dW_t, dZ_t) = rho * dt
 * </pre>
 *
 * <p>The 2D grid uses direction 0 for {@code f} (built by
 * {@link FdmCEV1dMesher}) and direction 1 for {@code x = log(alpha)}
 * (built by {@link Concentrating1dMesher} around {@code log(alpha)}).
 *
 * <p>Java port of v1.42.1
 * {@code ql/pricingengines/vanilla/fdsabrvanillaengine.{hpp,cpp}}.
 *
 * @author Phase 2m Track C port
 */
public class FdSabrVanillaEngine extends VanillaOption.EngineImpl {

    private final double f0_;
    private final double alpha_;
    private final double beta_;
    private final double nu_;
    private final double rho_;
    private final Handle<YieldTermStructure> rTS_;
    private final int tGrid_;
    private final int fGrid_;
    private final int xGrid_;
    private final int dampingSteps_;
    private final double scalingFactor_;
    private final double eps_;
    private final FdmSchemeDesc schemeDesc_;

    // arguments/results aliases (set in ctor via cast)
    private final Option.ArgumentsImpl arguments;
    private final Instrument.ResultsImpl results;

    /**
     * Full constructor.
     *
     * @param f0            initial forward value (f_0 &gt; 0)
     * @param alpha         initial SABR volatility (alpha &gt; 0)
     * @param beta          CEV exponent (0 &le; beta &lt; 1)
     * @param nu            vol-of-vol (nu &ge; 0)
     * @param rho           correlation (rho^2 &lt; 1)
     * @param rTS           risk-free yield curve
     * @param tGrid         time steps (default 50)
     * @param fGrid         forward mesh points (default 400)
     * @param xGrid         log-alpha mesh points (default 50)
     * @param dampingSteps  damping steps for initial condition smoothing
     * @param scalingFactor scale applied to the log-alpha grid range
     * @param eps           tail probability cutoff for mesher bounds
     * @param schemeDesc    FD scheme (default {@link FdmSchemeDesc#Hundsdorfer()})
     */
    public FdSabrVanillaEngine(
            final double f0,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final Handle<YieldTermStructure> rTS,
            final int tGrid,
            final int fGrid,
            final int xGrid,
            final int dampingSteps,
            final double scalingFactor,
            final double eps,
            final FdmSchemeDesc schemeDesc) {

        // Validate parameters (mirrors C++ validateSabrParameters then beta check)
        new Sabr().validateSabrParameters(alpha, 0.5 /* dummy beta */, nu, rho);
        QL.require(beta < 1.0,
                "beta must be smaller than 1.0: " + beta + " not allowed");

        this.f0_           = f0;
        this.alpha_        = alpha;
        this.beta_         = beta;
        this.nu_           = nu;
        this.rho_          = rho;
        this.rTS_          = rTS;
        this.tGrid_        = tGrid;
        this.fGrid_        = fGrid;
        this.xGrid_        = xGrid;
        this.dampingSteps_ = dampingSteps;
        this.scalingFactor_ = scalingFactor;
        this.eps_          = eps;
        this.schemeDesc_   = schemeDesc;

        this.arguments = (Option.ArgumentsImpl) arguments_;
        this.results   = (Instrument.ResultsImpl) results_;
    }

    /** Constructor with default scheme (Hundsdorfer). */
    public FdSabrVanillaEngine(
            final double f0,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final Handle<YieldTermStructure> rTS,
            final int tGrid,
            final int fGrid,
            final int xGrid) {
        this(f0, alpha, beta, nu, rho, rTS,
                tGrid, fGrid, xGrid, 0, 1.0, 1e-4,
                FdmSchemeDesc.Hundsdorfer());
    }

    /** Constructor with defaults for all grid parameters. */
    public FdSabrVanillaEngine(
            final double f0,
            final double alpha,
            final double beta,
            final double nu,
            final double rho,
            final Handle<YieldTermStructure> rTS) {
        this(f0, alpha, beta, nu, rho, rTS,
                50, 400, 50, 0, 1.0, 1e-4,
                FdmSchemeDesc.Hundsdorfer());
    }

    @Override
    public void calculate() {
        // 1. Extract payoff and maturity
        QL.require(arguments.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments.payoff;

        final YieldTermStructure rts = rTS_.currentLink();
        final DayCounter dc          = rts.dayCounter();
        final Date refDate           = rts.referenceDate();
        final Date maturityDate      = arguments.exercise.lastDate();
        final double maturityTime    = dc.yearFraction(refDate, maturityDate);

        // 2. Build the 2D mesh

        // direction 0: f (forward) using CEV-calibrated mesher
        final double upperAlpha = alpha_
                * JQuantMath.exp(nu_ * Math.sqrt(maturityTime)
                        * new InverseCumulativeNormal().op(0.75));

        final Fdm1dMesher cevMesher = new FdmCEV1dMesher(
                fGrid_, f0_, upperAlpha, beta_,
                maturityTime, eps_, scalingFactor_,
                payoff.strike(), 0.025);

        // direction 1: x = log(alpha) using a concentrating mesher
        final double normInvEps = new InverseCumulativeNormal().op(1.0 - eps_);
        final double logDrift   = -0.5 * nu_ * nu_ * maturityTime;
        final double volRange   = nu_ * Math.sqrt(maturityTime)
                * normInvEps * scalingFactor_;

        final double xMin = JQuantMath.log(alpha_) + logDrift - volRange;
        final double xMax = JQuantMath.log(alpha_) + logDrift + volRange;

        final Fdm1dMesher xMesher = new Concentrating1dMesher(
                xMin, xMax, xGrid_,
                JQuantMath.log(alpha_), 0.1, false);

        final FdmMesher mesher = new FdmMesherComposite(cevMesher, xMesher);

        // 3. Inner-value calculator
        final FdmInnerValueCalculator calculator =
                new FdmCellAveragingInnerValue(payoff, mesher, 0);

        // 4. Step conditions (European: just the vanilla composite with no dividends)
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        new DividendSchedule(), arguments.exercise,
                        mesher, calculator, refDate, dc);

        // 5. Boundary conditions along direction 0 (f-axis)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        final double lowerBound = cevMesher.location(0);
        final double upperBound = cevMesher.location(fGrid_ - 1);

        boundaries.add(new FdmDiscountDirichletBoundary(
                mesher, rts, maturityTime,
                payoff.get(upperBound),
                0, BoundaryCondition.Side.Upper));

        boundaries.add(new FdmDiscountDirichletBoundary(
                mesher, rts, maturityTime,
                payoff.get(lowerBound),
                0, BoundaryCondition.Side.Lower));

        // 6. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions,
                calculator, maturityTime, tGrid_, dampingSteps_);

        final FdmLinearOpComposite op = new FdmSabrOp(
                mesher, rts, f0_, alpha_, beta_, nu_, rho_);

        final Fdm2DimSolver solver = new Fdm2DimSolver(
                solverDesc, schemeDesc_, op);

        // 7. Read off value at initial point (f0, log(alpha))
        results.value = solver.interpolateAt(f0_, JQuantMath.log(alpha_));
    }
}
