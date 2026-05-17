/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

/*
 Copyright (C) 2010 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.methods.finitedifferences.meshers.Fdm1dMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmBlackScholesMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesherComposite;
import org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc;
import org.jquantlib.methods.finitedifferences.solvers.Fdm2dBlackScholesSolver;
import org.jquantlib.methods.finitedifferences.solvers.FdmSolverDesc;
import org.jquantlib.methods.finitedifferences.stepconditions.FdmStepConditionComposite;
import org.jquantlib.methods.finitedifferences.utilities.FdmBoundaryConditionSet;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;
import org.jquantlib.methods.finitedifferences.utilities.FdmLogBasketInnerValue;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.time.Date;

/**
 * Two-dimensional finite-differences Black-Scholes vanilla engine for basket
 * options on two underlyings (Min / Max / Average / Spread payoffs).
 * <p>
 * Java port of v1.42.1
 * {@code ql/pricingengines/basket/fd2dblackscholesvanillaengine.{hpp,cpp}}.
 * <p>
 * The PDE for {@code V(S1, S2, t)} is rolled back on a tensor-product
 * log-space mesh via {@link Fdm2dBlackScholesSolver}. Per-asset meshes use
 * {@link FdmBlackScholesMesher} with the spot as the concentration point
 * (matching C++ {@code std::pair<Real,Real>(p1->x0(), 0.1)}). The inner-value
 * calculator is {@link FdmLogBasketInnerValue}.
 *
 * <h3>Greeks</h3>
 * The engine fills {@code value, delta, gamma, theta} via:
 * <ul>
 *   <li>{@code value = solver.valueAt(x, y)}</li>
 *   <li>{@code delta = solver.deltaXat(x, y) + solver.deltaYat(x, y)}</li>
 *   <li>{@code gamma = solver.gammaXat + solver.gammaYat + 2*solver.gammaXYat}</li>
 *   <li>{@code theta = solver.thetaAt(x, y)}</li>
 * </ul>
 * exactly mirroring C++ {@code Fd2dBlackScholesVanillaEngine::calculate}.
 *
 * <h3>Defaults (matching C++ v1.42.1)</h3>
 * {@code xGrid = yGrid = 100, tGrid = 50, dampingSteps = 0,
 *  scheme = Hundsdorfer, localVol = false}.
 *
 * @author Phase 5e.5b-CFC-d port
 */
public class Fd2dBlackScholesVanillaEngine extends BasketOption.Engine {

    private final GeneralizedBlackScholesProcess p1;
    private final GeneralizedBlackScholesProcess p2;
    private final double correlation;
    private final int xGrid;
    private final int yGrid;
    private final int tGrid;
    private final int dampingSteps;
    private final FdmSchemeDesc schemeDesc;
    private final boolean localVol;
    private final double illegalLocalVolOverwrite;

    /** All C++ defaults. */
    public Fd2dBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess p1,
                                         final GeneralizedBlackScholesProcess p2,
                                         final double correlation) {
        this(p1, p2, correlation,
                100, 100, 50, 0,
                FdmSchemeDesc.Hundsdorfer(), false, Double.NaN);
    }

    /** Grid parameters only (default scheme = Hundsdorfer, no localVol). */
    public Fd2dBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess p1,
                                         final GeneralizedBlackScholesProcess p2,
                                         final double correlation,
                                         final int xGrid,
                                         final int yGrid,
                                         final int tGrid) {
        this(p1, p2, correlation,
                xGrid, yGrid, tGrid, 0,
                FdmSchemeDesc.Hundsdorfer(), false, Double.NaN);
    }

    /**
     * Full constructor mirroring C++ v1.42.1
     * {@code Fd2dBlackScholesVanillaEngine}.
     *
     * @param p1                       GBS process for asset 1
     * @param p2                       GBS process for asset 2
     * @param correlation              asset correlation
     * @param xGrid                    number of grid points along ln S1
     * @param yGrid                    number of grid points along ln S2
     * @param tGrid                    number of time steps
     * @param dampingSteps             number of implicit-Euler damping steps
     * @param schemeDesc               FDM scheme descriptor (Hundsdorfer in
     *                                 C++ default)
     * @param localVol                 local-volatility mode (not yet ported;
     *                                 must be {@code false})
     * @param illegalLocalVolOverwrite fallback vol when local-vol lookup
     *                                 fails (unused while
     *                                 {@code localVol = false})
     */
    public Fd2dBlackScholesVanillaEngine(final GeneralizedBlackScholesProcess p1,
                                         final GeneralizedBlackScholesProcess p2,
                                         final double correlation,
                                         final int xGrid,
                                         final int yGrid,
                                         final int tGrid,
                                         final int dampingSteps,
                                         final FdmSchemeDesc schemeDesc,
                                         final boolean localVol,
                                         final double illegalLocalVolOverwrite) {
        super();
        QL.require(p1 != null && p2 != null, "null GBS process given");

        this.p1 = p1;
        this.p2 = p2;
        this.correlation = correlation;
        this.xGrid = xGrid;
        this.yGrid = yGrid;
        this.tGrid = tGrid;
        this.dampingSteps = dampingSteps;
        this.schemeDesc = schemeDesc;
        this.localVol = localVol;
        this.illegalLocalVolOverwrite = illegalLocalVolOverwrite;

        p1.addObserver(this);
        p2.addObserver(this);
    }

    @Override
    public void calculate() {

        // 1. Payoff (must be a BasketPayoff)
        final BasketPayoff payoff = (BasketPayoff) arguments_.payoff;
        QL.require(payoff != null, "non-basket payoff given");

        // 2. Mesher (per-asset log-space mesh; concentration point = spot)
        final Date exerciseDate = arguments_.exercise.lastDate();
        final double maturity = p1.time(exerciseDate);

        final Fdm1dMesher em1 = new FdmBlackScholesMesher(
                xGrid, p1, maturity, p1.x0(),
                Double.NaN, Double.NaN, 0.0001, 1.5,
                p1.x0(), 0.1,
                new DividendSchedule(), 0.0);

        final Fdm1dMesher em2 = new FdmBlackScholesMesher(
                yGrid, p2, maturity, p2.x0(),
                Double.NaN, Double.NaN, 0.0001, 1.5,
                p2.x0(), 0.1,
                new DividendSchedule(), 0.0);

        final FdmMesher mesher = new FdmMesherComposite(em1, em2);

        // 3. Inner-value calculator
        final FdmInnerValueCalculator calculator =
                new FdmLogBasketInnerValue(payoff, mesher);

        // 4. Step conditions (no dividends for basket options here)
        final Date refDate = p1.riskFreeRate().currentLink().referenceDate();
        final DayCounter dc = p1.riskFreeRate().currentLink().dayCounter();
        final FdmStepConditionComposite conditions =
                FdmStepConditionComposite.vanillaComposite(
                        new DividendSchedule(), arguments_.exercise,
                        mesher, calculator, refDate, dc);

        // 5. Boundary conditions (empty)
        final FdmBoundaryConditionSet boundaries = new FdmBoundaryConditionSet();

        // 6. Solver
        final FdmSolverDesc solverDesc = new FdmSolverDesc(
                mesher, boundaries, conditions, calculator,
                maturity, tGrid, dampingSteps);

        final Fdm2dBlackScholesSolver solver = new Fdm2dBlackScholesSolver(
                p1, p2, correlation, solverDesc, schemeDesc,
                localVol, illegalLocalVolOverwrite);

        final double x = p1.x0();
        final double y = p2.x0();

        final MultiAssetOption.ResultsImpl r =
                (MultiAssetOption.ResultsImpl) results_;
        r.value = solver.valueAt(x, y);
        r.greeks().delta = solver.deltaXat(x, y) + solver.deltaYat(x, y);
        r.greeks().gamma = solver.gammaXat(x, y) + solver.gammaYat(x, y)
                + 2.0 * solver.gammaXYat(x, y);
        r.greeks().theta = solver.thetaAt(x, y);
    }
}
