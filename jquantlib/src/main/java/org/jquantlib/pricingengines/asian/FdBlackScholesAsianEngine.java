/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2009 Ralph Schreyer
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageType;
import org.jquantlib.instruments.DiscreteAveragingAsianOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.distributions.InverseCumulativeNormal;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.FdmSimple2dBSSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmArithmeticAverageCondition;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogInnerValue;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

import java.util.ArrayList;
import java.util.List;

/**
 * Finite-Differences Black-Scholes engine for arithmetic-average discrete Asian options.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/fdblackscholesasianengine.{hpp,cpp}}.
 *
 * <p>The engine builds a 2-D log-spot x log-average mesh, applies a discrete
 * arithmetic-average step condition ({@link FdmArithmeticAverageCondition}) at
 * each fixing date and rolls back via {@link FdmSimple2dBSSolver} (single-asset
 * Black-Scholes operator on the spot axis only — the average axis is updated
 * by the step condition rather than a stochastic PDE term).
 *
 * <p>Supports European exercise and arithmetic averaging only. Running averages
 * are supplied through {@code runningAccumulator / pastFixings}; if both are
 * zero the engine treats the option as un-fixed and uses the spot as the
 * initial average level.
 */
public class FdBlackScholesAsianEngine extends DiscreteAveragingAsianOption.EngineImpl {

    private final GeneralizedBlackScholesProcess process_;
    private final int tGrid_;
    private final int xGrid_;
    private final int aGrid_;
    private final FdmSchemeDesc schemeDesc_;

    public FdBlackScholesAsianEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int aGrid, final FdmSchemeDesc schemeDesc) {
        super();
        QL.require(process != null, "null GBS process");
        this.process_ = process;
        this.tGrid_ = tGrid;
        this.xGrid_ = xGrid;
        this.aGrid_ = aGrid;
        this.schemeDesc_ = (schemeDesc != null) ? schemeDesc : FdmSchemeDesc.Douglas();
    }

    public FdBlackScholesAsianEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 100, 100, 50, FdmSchemeDesc.Douglas());
    }

    /** Convenience constructor matching the C++ default-scheme variant
     *  {@code FdBlackScholesAsianEngine(process, tGrid, xGrid, aGrid)}. */
    public FdBlackScholesAsianEngine(final GeneralizedBlackScholesProcess process, final int tGrid, final int xGrid,
            final int aGrid) {
        this(process, tGrid, xGrid, aGrid, FdmSchemeDesc.Douglas());
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final DiscreteAveragingAsianOption.ArgumentsImpl a =
                (DiscreteAveragingAsianOption.ArgumentsImpl) arguments_;
        final DiscreteAveragingAsianOption.ResultsImpl r = (DiscreteAveragingAsianOption.ResultsImpl) results_;
        final org.jquantlib.instruments.Option.GreeksImpl greeks = r.greeks();

        QL.require(a.exercise.type() == Exercise.Type.European, "European exercise supported only");
        QL.require(a.averageType == AverageType.Arithmetic, "Arithmetic averaging supported only");
        QL.require(a.runningAccumulator == 0.0 || a.pastFixings > 0,
                "Running average requires at least one past fixing");

        // 1. Mesher — matches C++ FdBlackScholesAsianEngine: no concentration point
        //    (i.e. cPoint = (Null, Null) ⇒ uniform mesh in log-space).
        final StrikedTypePayoff payoff = (StrikedTypePayoff) a.payoff;
        final double maturity = process_.time(a.exercise.lastDate());
        final Fdm1dMesher equityMesher = new FdmBlackScholesMesher(xGrid_, process_, maturity, payoff.strike(),
                Double.NaN, Double.NaN, 0.0001, 1.5, Double.NaN, Double.NaN, null, 0.0);

        final double spot = process_.x0();
        QL.require(spot > 0.0, "negative or null underlying given");

        final double avg = (a.runningAccumulator == 0.0)
                ? spot
                : a.runningAccumulator / a.pastFixings;

        final InverseCumulativeNormal icn = new InverseCumulativeNormal();
        final double normInvEps = icn.op(1.0 - 0.0001);
        final double sigmaSqrtT = process_.blackVolatility().currentLink().blackVol(maturity, payoff.strike())
                * Math.sqrt(maturity);
        final double rRange = sigmaSqrtT * normInvEps;

        final double xMin = Math.min(Math.log(avg) - 0.25 * rRange, Math.log(spot) - 1.5 * rRange);
        final double xMax = Math.max(Math.log(avg) + 0.25 * rRange, Math.log(spot) + 1.5 * rRange);

        final Fdm1dMesher averageMesher = new FdmBlackScholesMesher(aGrid_, process_, maturity, payoff.strike(),
                xMin, xMax, 0.0001, 1.5, Double.NaN, Double.NaN, null, 0.0);

        final FdmMesher mesher = new FdmMesherComposite(equityMesher, averageMesher);

        // 2. Calculator (FdmLogInnerValue on the average axis = direction 1)
        final FdmInnerValueCalculator calculator = new FdmLogInnerValue(payoff, mesher, 1);

        // 3. Step conditions — arithmetic-average condition at every fixing date
        final FdmStepConditionComposite.Conditions stepConditions = new FdmStepConditionComposite.Conditions();
        final List< List< Double > > stoppingTimes = new ArrayList<>();

        final List< Double > averageTimes = new ArrayList<>(a.fixingDates.size());
        for ( final Date d : a.fixingDates ) {
            final double t = process_.time(d);
            QL.require(t >= 0.0, "Fixing dates must not contain past date");
            averageTimes.add(t);
        }
        stoppingTimes.add(averageTimes);
        stepConditions.add(new FdmArithmeticAverageCondition(averageTimes, a.runningAccumulator, a.pastFixings, mesher,
                0));

        final FdmStepConditionComposite conditions = new FdmStepConditionComposite(stoppingTimes, stepConditions);

        // 4. Boundary conditions (empty set)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 5. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(mesher, boundaries, conditions, calculator, maturity, tGrid_,
                0);
        final FdmSimple2dBSSolver solver = new FdmSimple2dBSSolver(process_, payoff.strike(), solverDesc, schemeDesc_);

        r.value = solver.valueAt(spot, avg);
        greeks.delta = solver.deltaAt(spot, avg, spot * 0.01);
        greeks.gamma = solver.gammaAt(spot, avg, spot * 0.01);
    }
}
