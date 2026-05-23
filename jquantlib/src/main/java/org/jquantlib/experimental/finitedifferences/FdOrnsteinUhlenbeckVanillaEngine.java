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
 Copyright (C) 2016 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.FdmSimpleProcess1dMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.operators.FdmOrnsteinUhlenbeckOp;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm1DimSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Finite-Differences pricing engine for vanilla options on an Ornstein-Uhlenbeck
 * underlying.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdornsteinuhlenbeckvanillaengine.{hpp,cpp}}.</p>
 *
 * <p>The pricing PDE is solved on a single-dimension mesh built from
 * {@link FdmSimpleProcess1dMesher} (process-driven) using
 * {@link FdmOrnsteinUhlenbeckOp} as the operator, an inner-value calculator
 * that evaluates the payoff at the mesh location (no log-transform), and the
 * 1-D solver {@link Fdm1DimSolver}.</p>
 *
 * @author Phase 2 L5-B port
 */
public class FdOrnsteinUhlenbeckVanillaEngine extends VanillaOption.EngineImpl {

    private final OrnsteinUhlenbeckProcess process_;
    private final YieldTermStructure rTS_;
    private final DividendSchedule dividends_;
    private final int tGrid_;
    private final int xGrid_;
    private final int dampingSteps_;
    private final double epsilon_;
    private final FdmSchemeDesc schemeDesc_;

    private final OneAssetOption.ArgumentsImpl a;
    private final OneAssetOption.ResultsImpl r;

    /** All-default constructor: {@code tGrid=100, xGrid=100, dampingSteps=0, epsilon=1e-4, Douglas scheme}. */
    public FdOrnsteinUhlenbeckVanillaEngine(final OrnsteinUhlenbeckProcess process, final YieldTermStructure rTS) {
        this(process, rTS, null, 100, 100, 0, 0.0001, FdmSchemeDesc.Douglas());
    }

    /** Grid-parameters constructor (no dividends). */
    public FdOrnsteinUhlenbeckVanillaEngine(final OrnsteinUhlenbeckProcess process, final YieldTermStructure rTS,
            final int tGrid, final int xGrid, final int dampingSteps, final double epsilon,
            final FdmSchemeDesc schemeDesc) {
        this(process, rTS, null, tGrid, xGrid, dampingSteps, epsilon, schemeDesc);
    }

    /**
     * Full constructor mirroring C++ v1.42.1
     * {@code FdOrnsteinUhlenbeckVanillaEngine(process, rTS, dividends, tGrid, xGrid, dampingSteps, epsilon, schemeDesc)}.
     */
    public FdOrnsteinUhlenbeckVanillaEngine(final OrnsteinUhlenbeckProcess process, final YieldTermStructure rTS,
            final DividendSchedule dividends, final int tGrid, final int xGrid, final int dampingSteps,
            final double epsilon, final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(process != null, "null OU process");
        QL.require(rTS != null, "null risk-free term structure");
        this.process_ = process;
        this.rTS_ = rTS;
        this.dividends_ = (dividends != null) ? dividends : new DividendSchedule();
        this.tGrid_ = tGrid;
        this.xGrid_ = xGrid;
        this.dampingSteps_ = dampingSteps;
        this.epsilon_ = epsilon;
        this.schemeDesc_ = schemeDesc;

        this.a = (OneAssetOption.ArgumentsImpl) arguments_;
        this.r = (OneAssetOption.ResultsImpl) results_;

        process.addObserver(this);
        rTS.addObserver(this);
    }

    @Override
    public void calculate() {

        // 1. Mesher
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        final DayCounter dc = rTS_.dayCounter();
        final Date referenceDate = rTS_.referenceDate();

        final double maturity = dc.yearFraction(referenceDate, a.exercise.lastDate());

        final Fdm1dMesher equityMesher = new FdmSimpleProcess1dMesher(xGrid_, process_, maturity, 1, epsilon_,
                Double.NaN);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher);

        // 2. Calculator — direct payoff(mesher.location(iter, 0))
        final FdmInnerValueCalculator calculator = new FdmOUInnerValue(payoff, mesher, 0);

        // 3. Step conditions
        final FdmStepConditionComposite conditions = FdmStepConditionComposite.vanillaComposite(dividends_, a.exercise,
                mesher, calculator, referenceDate, dc);

        // 4. Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid_,
                dampingSteps_);

        final FdmOrnsteinUhlenbeckOp op = new FdmOrnsteinUhlenbeckOp(mesher, process_, rTS_, 0);

        final Fdm1DimSolver solver = new Fdm1DimSolver(solverDesc, schemeDesc_, op);

        final double spot = process_.x0();

        r.value = solver.interpolateAt(spot);
        r.greeks().delta = solver.derivativeX(spot);
        r.greeks().gamma = solver.derivativeXX(spot);
        r.greeks().theta = solver.thetaAt(spot);
    }

    /**
     * Inner-value calculator for OU vanilla pricing: returns the payoff at the
     * raw mesh location (no exp/log mapping). Mirrors the anonymous
     * {@code FdmOUInnerValue} struct in C++.
     */
    private static final class FdmOUInnerValue implements FdmInnerValueCalculator {
        private final Payoff payoff_;
        private final FdmMesher mesher_;
        private final int direction_;

        FdmOUInnerValue(final Payoff payoff, final FdmMesher mesher, final int direction) {
            this.payoff_ = payoff;
            this.mesher_ = mesher;
            this.direction_ = direction;
        }

        @Override
        public double innerValue(final FdmLinearOpIterator iter, final double t) {
            final double s = mesher_.location(iter, direction_);
            return payoff_.get(s);
        }

        @Override
        public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
            return innerValue(iter, t);
        }
    }
}
