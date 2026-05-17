/*
 Copyright (C) 2018 Klaus Spanderen
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
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmCEV1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.operators.FdmCEVOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm1DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.BoundaryCondition;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmCellAveragingInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmDiscountDirichletBoundary;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmTimeDepDirichletBoundary;
import org.jquantlib.pricingengines.vanilla.AnalyticCEVEngine.CEVCalculator;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Finite-difference pricing engine for European vanilla options under the
 * constant elasticity of variance (CEV) process.
 * <p>
 * Process:
 * <pre>  df_t = alpha * f_t^beta * dW_t</pre>
 *
 * <p>The engine uses {@link FdmCEV1dMesher} for the forward grid (its
 * concentration is anchored on the option strike, mirroring C++) and
 * {@link FdmCEVOp} for the PDE operator. The {@link Fdm1DimSolver} then
 * rolls the payoff back to {@code t = 0} and reads off value, delta, gamma,
 * and theta at {@code f0}.
 *
 * <p>The upper boundary uses an {@link FdmTimeDepDirichletBoundary} whose
 * value comes from {@link CEVCalculator} (essentially the analytic CEV
 * option price at the boundary mesh node). The lower boundary uses an
 * {@link FdmDiscountDirichletBoundary} only when {@code delta < 2.0}
 * (i.e. when the absorbing-at-zero boundary is relevant); for
 * {@code delta >= 2.0} no lower BC is needed because zero is not
 * reachable.
 *
 * <p>Java port of v1.42.1
 * {@code ql/pricingengines/vanilla/fdcevvanillaengine.{hpp,cpp}}.
 *
 * @author Phase 5e.5b-CFC-d-112 port
 */
public class FdCEVVanillaEngine extends VanillaOption.EngineImpl {

    private final double f0_;
    private final double alpha_;
    private final double beta_;
    private final Handle<YieldTermStructure> discountCurve_;
    private final int tGrid_;
    private final int xGrid_;
    private final int dampingSteps_;
    private final double scalingFactor_;
    private final double eps_;
    private final FdmSchemeDesc schemeDesc_;

    private final Option.ArgumentsImpl     arguments;
    private final Instrument.ResultsImpl   results;

    // ----------------------------------------------------------------
    // constructors
    // ----------------------------------------------------------------

    /** Convenience constructor mirroring C++ defaults. */
    public FdCEVVanillaEngine(final double f0,
                              final double alpha,
                              final double beta,
                              final Handle<YieldTermStructure> discountCurve) {
        this(f0, alpha, beta, discountCurve,
                50, 400, 0, 1.0, 1e-4, FdmSchemeDesc.Douglas());
    }

    /** Convenience constructor — grid sizes only (other defaults from C++). */
    public FdCEVVanillaEngine(final double f0,
                              final double alpha,
                              final double beta,
                              final Handle<YieldTermStructure> discountCurve,
                              final int tGrid,
                              final int xGrid,
                              final int dampingSteps,
                              final double scalingFactor,
                              final double eps) {
        this(f0, alpha, beta, discountCurve,
                tGrid, xGrid, dampingSteps, scalingFactor, eps,
                FdmSchemeDesc.Douglas());
    }

    /** Full constructor mirroring C++ v1.42.1. */
    public FdCEVVanillaEngine(final double f0,
                              final double alpha,
                              final double beta,
                              final Handle<YieldTermStructure> discountCurve,
                              final int tGrid,
                              final int xGrid,
                              final int dampingSteps,
                              final double scalingFactor,
                              final double eps,
                              final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(discountCurve != null, "null discount curve");
        QL.require(schemeDesc    != null, "null scheme descriptor");

        this.f0_            = f0;
        this.alpha_         = alpha;
        this.beta_          = beta;
        this.discountCurve_ = discountCurve;
        this.tGrid_         = tGrid;
        this.xGrid_         = xGrid;
        this.dampingSteps_  = dampingSteps;
        this.scalingFactor_ = scalingFactor;
        this.eps_           = eps;
        this.schemeDesc_    = schemeDesc;

        this.arguments = (Option.ArgumentsImpl)     arguments_;
        this.results   = (Instrument.ResultsImpl)   results_;
    }

    // ----------------------------------------------------------------
    // PricingEngine
    // ----------------------------------------------------------------

    @Override
    public void calculate() {
        // 1. Mesher
        QL.require(arguments.payoff instanceof StrikedTypePayoff,
                "non-striked payoff given");
        final StrikedTypePayoff payoff = (StrikedTypePayoff) arguments.payoff;

        final YieldTermStructure rTS = discountCurve_.currentLink();
        final DayCounter dc          = rTS.dayCounter();
        final Date referenceDate     = rTS.referenceDate();
        final Date maturityDate      = arguments.exercise.lastDate();
        final double maturityTime    = dc.yearFraction(referenceDate, maturityDate);

        final Fdm1dMesher cevMesher = new FdmCEV1dMesher(
                xGrid_, f0_, alpha_, beta_,
                maturityTime, eps_, scalingFactor_,
                payoff.strike(), 0.1);

        final double lowerBound = cevMesher.location(0);
        final double upperBound = cevMesher.location(xGrid_ - 1);

        final FdmMesher mesher = new FdmMesherComposite(cevMesher);

        // 2. Calculator
        final FdmInnerValueCalculator calculator =
                new FdmCellAveragingInnerValue(payoff, mesher, 0);

        // 3. Step conditions (European or American)
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        new DividendSchedule(), arguments.exercise,
                        mesher, calculator, referenceDate, dc);

        // 4. Boundary conditions
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // Upper bound: time-dependent Dirichlet from CEVCalculator value
        // C++ namespace-private PriceAtBoundary lambda:
        //   time2Expiry = max(1/365, maturityTime - t)
        //   df          = rTS.discount(maturityTime) / rTS.discount(t)
        //   value       = df * calculator.value(optionType, strike, time2Expiry)
        final CEVCalculator upperCalc =
                new CEVCalculator(upperBound, alpha_, beta_);
        final double dfMaturity = rTS.discount(maturityTime);

        boundaries.add(new FdmTimeDepDirichletBoundary(
                mesher,
                t -> {
                    final double time2Expiry = Math.max(1.0 / 365.0, maturityTime - t);
                    final double df = dfMaturity / rTS.discount(t);
                    return df * upperCalc.value(
                            payoff.optionType(), payoff.strike(), time2Expiry);
                },
                0, BoundaryCondition.Side.Upper));

        // Lower bound: only when delta < 2.0 (zero is reachable)
        final double delta = (1.0 - 2.0 * beta_) / (1.0 - beta_);
        if (delta < 2.0) {
            final double terminalCashFlow = payoff.get(lowerBound);
            boundaries.add(new FdmDiscountDirichletBoundary(
                    mesher, rTS, maturityTime, terminalCashFlow,
                    0, BoundaryCondition.Side.Lower));
        }

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturityTime, tGrid_, dampingSteps_);

        final FdmCEVOp op = new FdmCEVOp(
                mesher, rTS, f0_, alpha_, beta_, 0);

        final Fdm1DimSolver solver = new Fdm1DimSolver(
                solverDesc, schemeDesc_, op);

        results.value = solver.interpolateAt(f0_);
        // Greeks: results is Instrument.ResultsImpl; cast to access greeks
        // via the OneAssetOption results path.
        final org.jquantlib.instruments.OneAssetOption.ResultsImpl r =
                (org.jquantlib.instruments.OneAssetOption.ResultsImpl) results_;
        r.greeks().delta = solver.derivativeX(f0_);
        r.greeks().gamma = solver.derivativeXX(f0_);
        r.greeks().theta = solver.thetaAt(f0_);
    }
}
