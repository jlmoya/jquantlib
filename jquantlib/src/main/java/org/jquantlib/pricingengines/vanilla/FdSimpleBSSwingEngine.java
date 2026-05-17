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
 Copyright (C) 2010 Klaus Spanderen
 */
package org.jquantlib.pricingengines.vanilla;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.SwingExercise;
import org.jquantlib.instruments.VanillaSwingOption;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.finitedifferences.StepCondition;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.meshers.Uniform1dMesher;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmSimple2dBSSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmSimpleSwingCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.methods.finitedifferences.utilities.FdmZeroInnerValue;
import org.jquantlib.pricingengines.GenericEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;
import org.jquantlib.instruments.DividendSchedule;

/**
 * Finite-differences Black-Scholes engine for vanilla swing options.
 *
 * <p>Java port of v1.42.1
 * {@code ql/pricingengines/vanilla/fdsimplebsswingengine.{hpp,cpp}}.
 *
 * <p>Builds a 2D mesh ({@code xGrid} log-spot points × {@code maxRights+1}
 * uniform exercise-counter points) and rolls back a single-asset BS PDE
 * augmented with a {@link FdmSimpleSwingCondition} that consumes exercise
 * rights at the user-supplied exercise dates. Terminal payoff is zero
 * ({@link FdmZeroInnerValue}); cash flows accrue only at exercise times
 * via {@link FdmLogInnerValue} applied to the {@link StrikedTypePayoff}.
 *
 * <h3>Defaults (matching C++ v1.42.1)</h3>
 * {@code tGrid = 50, xGrid = 100, scheme = Douglas}.
 *
 * @author Phase 5e.5b-CFC-d-170 port
 */
public class FdSimpleBSSwingEngine
        extends GenericEngine<VanillaSwingOption.Arguments, VanillaSwingOption.Results>
        implements Observer {

    private final GeneralizedBlackScholesProcess process_;
    private final int tGrid_;
    private final int xGrid_;
    private final FdmSchemeDesc schemeDesc_;

    /** Convenience — all C++ defaults (tGrid=50, xGrid=100, scheme=Douglas). */
    public FdSimpleBSSwingEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 50, 100, FdmSchemeDesc.Douglas());
    }

    /** Grid parameters only (default scheme = Douglas). */
    public FdSimpleBSSwingEngine(final GeneralizedBlackScholesProcess process,
                                 final int tGrid, final int xGrid) {
        this(process, tGrid, xGrid, FdmSchemeDesc.Douglas());
    }

    /**
     * Full constructor mirroring C++ v1.42.1.
     */
    public FdSimpleBSSwingEngine(final GeneralizedBlackScholesProcess process,
                                 final int tGrid,
                                 final int xGrid,
                                 final FdmSchemeDesc schemeDesc) {
        super(new VanillaSwingOption.ArgumentsImpl(),
              new VanillaSwingOption.ResultsImpl());
        QL.require(process != null, "null GBS process");
        this.process_    = process;
        this.tGrid_      = tGrid;
        this.xGrid_      = xGrid;
        this.schemeDesc_ = schemeDesc;

        process.addObserver(this);
    }

    @Override
    public void calculate() {

        final VanillaSwingOption.ArgumentsImpl a =
                (VanillaSwingOption.ArgumentsImpl) arguments_;

        QL.require(a.exercise.type() == Exercise.Type.Bermudan,
                "Bermudan exercise supported only");

        // 1. Mesher
        final StrikedTypePayoff payoff = a.payoff;
        QL.require(payoff != null, "Strike type payoff expected");

        final Date maturityDate = a.exercise.lastDate();
        final double maturity = process_.time(maturityDate);

        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(
                xGrid_, process_, maturity, payoff.strike(),
                new DividendSchedule(), 0.0);

        final Fdm1dMesher exerciseMesher = new Uniform1dMesher(
                0.0, (double) a.maxExerciseRights,
                a.maxExerciseRights + 1);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher, exerciseMesher);

        // 2. Calculator (zero terminal payoff — swing cash flows enter via the
        // step condition, not at maturity)
        final FdmInnerValueCalculator calculator = new FdmZeroInnerValue();

        // 3. Step conditions: Bermudan-style exercise on the swing axis
        final List<List<Double>> stoppingTimes = new ArrayList<>();
        final FdmStepConditionComposite.Conditions stepConditions =
                new FdmStepConditionComposite.Conditions();

        // 3.1 collect exercise times
        final List<Double> exerciseTimes = new ArrayList<>();
        for (final Date d : a.exercise.dates()) {
            final double t = process_.time(d);
            QL.require(t >= 0.0, "exercise dates must not contain past date");
            exerciseTimes.add(t);
        }
        stoppingTimes.add(exerciseTimes);

        // The exercise cash-flow calculator operates on the spot (direction 0)
        // log-payoff.
        final FdmInnerValueCalculator exerciseCalculator =
                new FdmLogInnerValue(payoff, mesher, 0);

        final StepCondition<Array> swingCondition = new FdmSimpleSwingCondition(
                exerciseTimes, mesher, exerciseCalculator,
                1, a.minExerciseRights);
        stepConditions.add(swingCondition);

        final FdmStepConditionComposite conditions =
                new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 4. Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid_, 0);

        final FdmSimple2dBSSolver solver = new FdmSimple2dBSSolver(
                process_, payoff.strike(), solverDesc, schemeDesc_);

        final double spot = process_.x0();

        // Slice at "1 right used" — this matches C++ valueAt(spot, 1.0),
        // i.e. the value with one exercise already consumed (so the option
        // grants exactly maxRights-1 remaining rights from this slice).
        // C++ uses 1.0 as the y-coord directly; the uniform mesher places
        // node k at integer location k.
        final VanillaSwingOption.ResultsImpl r =
                (VanillaSwingOption.ResultsImpl) results_;
        r.value             = solver.valueAt(spot, 1.0);
        r.greeks().delta    = solver.deltaAt(spot, 1.0, spot * 0.01);
        r.greeks().gamma    = solver.gammaAt(spot, 1.0, spot * 0.01);
        r.greeks().theta    = solver.thetaAt(spot, 1.0);
    }

    @Override
    public void update() {
        notifyObservers();
    }
}
