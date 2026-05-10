/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.AmericanExercise;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.AverageBasketPayoff;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.MaxBasketPayoff;
import org.jquantlib.instruments.MinBasketPayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SpreadBasketPayoff;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.basket.BjerksundStenslandSpreadEngine;
import org.jquantlib.pricingengines.basket.KirkEngine;
import org.jquantlib.pricingengines.basket.MCAmericanBasketEngine;
import org.jquantlib.pricingengines.basket.StulzEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5k skeleton port of {@code test-suite/basketoption.cpp} v1.42.1
 * (2,578 LOC, 22 cases).
 *
 * <p>Phase 5k.5 (this revision) lands the analytic basket-option subsystem:
 * {@link BasketOption} instrument, {@link BasketPayoff} hierarchy
 * (Min/Max/Average/Spread), {@link StulzEngine} (analytic two-asset min/max
 * via Drezner bivariate normal CDF), {@link KirkEngine} (spread option,
 * Kirk 1995), and {@link BjerksundStenslandSpreadEngine} (Bjerksund-Stensland
 * 2014). Three test methods are now fully bodied:
 * <ul>
 *   <li>{@link #testEuroTwoValues()} — 47 Haug reference cases across Min/Max
 *       (Stulz) and Spread (Kirk) baskets;</li>
 *   <li>{@link #testBjerksundStenslandSpreadEngine()} — sanity vs Kirk;</li>
 *   <li>{@link #testBasketPayoffs()} — payoff-class unit tests.</li>
 * </ul>
 *
 * <p>Remaining 19 methods stay {@code @Ignore}'d pending Phase 5k.5b:
 * MC basket engines (META D12 prereq), American basket engines, FD basket
 * engines (2D, N-dim, FdmAmerican), local-vol spread, operator-splitting /
 * Strang / Deng-Li-Zhou spread engines, single-factor BSM basket, Choi
 * quadrature, root-of-sum-exponentials helper, and the cross-engine benchmark.
 *
 * <p>Source: {@code test-suite/basketoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class BasketOptionTest {

    public BasketOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_INSTRUMENT =
            "Phase 5k.5b — requires multi-asset infrastructure beyond analytic engines "
          + "(see ignore reasons below for specifics)";

    private static final String REASON_MC_BASKET =
            "Phase 5k.5b — requires MCEuropeanBasketEngine port (META D12 prereq); "
          + "depends on MultiPathGenerator + StochasticProcessArray + basket "
          + "payoff visitor wiring";

    private static final String REASON_AMERICAN_BASKET =
            "Phase 5k.5b — requires MCAmericanBasketEngine + Longstaff-Schwartz "
          + "regression for basket exercise (Tavella one-dimensional projection)";

    private static final String REASON_BARRAQUAND =
            "Phase 5k.5b — requires Barraquand-Martineau MC engine "
          + "(stratification + control variate over multi-asset basket)";

    private static final String REASON_OPERATOR_SPLITTING =
            "Phase 5k.5b — requires OperatorSplittingSpreadEngine "
          + "(2D PDE with Lo-Hayashi-Park splitting)";

    private static final String REASON_STRANG_SPLITTING =
            "Phase 5k.5b — requires Strang-splitting variant of the 2D PDE "
          + "spread engine (cross-validation against Mathematica reference)";

    private static final String REASON_DENG_LI_ZHOU =
            "Phase 5k.5b — requires DengLiZhouSpreadEngine "
          + "(third-order Taylor expansion spread approximation)";

    private static final String REASON_2D_PDE =
            "Phase 5k.5b — requires Fd2dBlackScholesVanillaEngine "
          + "for two-asset basket / spread Greeks";

    private static final String REASON_NDIM_PDE =
            "Phase 5k.5b — requires N-dimensional FdmNdimBlackScholesEngine "
          + "(N-asset basket FD engine; depends on FdmHestonOp generalisation)";

    private static final String REASON_LOCAL_VOL_SPREAD =
            "Phase 5k.5b — requires local-volatility two-asset spread engine "
          + "(depends on per-asset LocalVolTermStructure wiring)";

    private static final String REASON_FDM_AMERICAN =
            "Phase 5k.5b — requires FdmAmericanBasketEngine "
          + "(early-exercise FD basket engine)";

    private static final String REASON_BSM_BASKET =
            "Phase 5k.5b — requires SingleFactorBsmBasketEngine "
          + "(quadrature over single-factor BSM projection)";

    private static final String REASON_GOLDEN_CHOI =
            "Phase 5k.5b — requires GoldenChoiBasketEngine "
          + "(Choi 2018 quadrature scheme)";

    private static final String REASON_ROOT_SUM_EXP =
            "Phase 5k.5b — requires rootOfSumExponentials helper "
          + "(used by Choi quadrature engine)";

    private static final String REASON_BENCHMARK =
            "Phase 5k.5b — requires the full basket / spread engine stack "
          + "to populate the cross-engine benchmark table";

    private static final String REASON_NO_DIV_ZERO =
            "Phase 5k.5b — requires OperatorSplittingSpreadEngine; covers "
          + "the divide-by-zero regression on degenerate strike";

    private static final String REASON_KIRK_PDE =
            "Phase 5k.5b — Kirk vs PDE cross-validation requires Fd2dBlackScholesVanillaEngine";

    // ---- Bodied tests (Phase 5k.5) -----------------------------------

    private enum BasketType { MinBasket, MaxBasket, SpreadBasket }

    private static BasketPayoff basketTypeToPayoff(
            final BasketType basketType, final PlainVanillaPayoff p) {
        switch (basketType) {
            case MinBasket:    return new MinBasketPayoff(p);
            case MaxBasket:    return new MaxBasketPayoff(p);
            case SpreadBasket: return new SpreadBasketPayoff(p);
        }
        throw new IllegalArgumentException("unknown basket type");
    }

    private static class BasketOptionTwoData {
        final BasketType basketType;
        final Option.Type type;
        final double strike;
        final double s1, s2;
        final double q1, q2;
        final double r;
        final double t;       // years
        final double v1, v2;
        final double rho;
        final double result;
        final double tol;

        BasketOptionTwoData(final BasketType bt, final Option.Type type,
                final double strike, final double s1, final double s2,
                final double q1, final double q2, final double r, final double t,
                final double v1, final double v2, final double rho,
                final double result, final double tol) {
            this.basketType = bt;
            this.type = type;
            this.strike = strike;
            this.s1 = s1;
            this.s2 = s2;
            this.q1 = q1;
            this.q2 = q2;
            this.r = r;
            this.t = t;
            this.v1 = v1;
            this.v2 = v2;
            this.rho = rho;
            this.result = result;
            this.tol = tol;
        }
    }

    private static int timeToDays(final double t) {
        return (int) (t * 360 + 0.5);
    }

    private static GeneralizedBlackScholesProcess makeProcess(
            final Quote spot,
            final YieldTermStructure dividendTS,
            final YieldTermStructure riskFreeTS,
            final BlackVolTermStructure volTS) {
        return new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(dividendTS),
                new Handle<YieldTermStructure>(riskFreeTS),
                new Handle<BlackVolTermStructure>(volTS));
    }

    /**
     * Two-asset European basket options against Haug-derived reference values.
     *
     * <p>Mirrors C++ {@code testEuroTwoValues} restricted to the analytic
     * engine path: Stulz for min/max baskets, Kirk for spread baskets.
     * The MC + FD cross-validation path is deferred to Phase 5k.5b.</p>
     *
     * <p>Reference values from "Option pricing formulas", E.G. Haug,
     * McGraw-Hill 1998 (pag. 56-60), as embedded in the C++ test-suite.</p>
     */
    @Test
    public void testEuroTwoValues() {
        QL.info("Testing two-asset European basket options...");

        final BasketOptionTwoData[] values = new BasketOptionTwoData[] {
            // Min/Max — Haug pag 56-58 + maths.ox.ac.uk/~firth/computing/excel.shtml
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.90, 10.898, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.70,  8.483, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.50,  6.844, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30,  5.531, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.10,  4.413, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.50, 0.70, 0.00,  4.981, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.50, 0.30, 0.00,  4.159, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.50, 0.10, 0.00,  2.597, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.50, 0.10, 0.50,  4.030, 1.0e-3),

            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.90, 17.565, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.70, 19.980, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.50, 21.619, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30, 22.932, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.10, 24.049, 1.1e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0,  80.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30, 16.508, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0,  80.0,  80.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30,  8.049, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0,  80.0, 120.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30, 30.141, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,  100.0, 120.0, 120.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30, 42.889, 1.0e-3),

            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.90, 11.369, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.70, 12.856, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.50, 13.890, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30, 14.741, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.10, 15.485, 1.0e-3),

            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 0.50, 0.30, 0.30, 0.10, 11.893, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 0.25, 0.30, 0.30, 0.10,  8.881, 1.0e-3),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 2.00, 0.30, 0.30, 0.10, 19.268, 1.0e-3),

            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.90,  7.339, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.70,  5.853, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.50,  4.818, 1.0e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.30,  3.967, 1.1e-3),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Put,   100.0, 100.0, 100.0, 0.00, 0.00, 0.05, 1.00, 0.30, 0.30, 0.10,  3.223, 1.0e-3),

            // Haug "Option pricing formulas" pag 58 — non-zero dividends
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,   98.0, 100.0, 105.0, 0.00, 0.00, 0.05, 0.50, 0.11, 0.16, 0.63,  4.8177, 1.0e-4),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,   98.0, 100.0, 105.0, 0.00, 0.00, 0.05, 0.50, 0.11, 0.16, 0.63, 11.6323, 1.0e-4),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,    98.0, 100.0, 105.0, 0.00, 0.00, 0.05, 0.50, 0.11, 0.16, 0.63,  2.0376, 1.0e-4),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Put,    98.0, 100.0, 105.0, 0.00, 0.00, 0.05, 0.50, 0.11, 0.16, 0.63,  0.5731, 1.0e-4),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Call,   98.0, 100.0, 105.0, 0.06, 0.09, 0.05, 0.50, 0.11, 0.16, 0.63,  2.9340, 1.0e-4),
            new BasketOptionTwoData(BasketType.MinBasket, Option.Type.Put,    98.0, 100.0, 105.0, 0.06, 0.09, 0.05, 0.50, 0.11, 0.16, 0.63,  3.5224, 1.0e-4),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Call,   98.0, 100.0, 105.0, 0.06, 0.09, 0.05, 0.50, 0.11, 0.16, 0.63,  8.0701, 1.0e-4),
            new BasketOptionTwoData(BasketType.MaxBasket, Option.Type.Put,    98.0, 100.0, 105.0, 0.06, 0.09, 0.05, 0.50, 0.11, 0.16, 0.63,  1.2181, 1.0e-4),

            // Haug pag 59-60 — Kirk approx for European spread option on two futures.
            // C++ test uses BlackProcess(spot, rTS, volTS) which is GBS(spot, rTS, rTS, volTS),
            // i.e. q = r so forward = spot. Mirrored here by setting q1 = q2 = r.
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.20, 0.20, -0.5,  4.7530, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.20, 0.20,  0.0,  3.7970, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.20, 0.20,  0.5,  2.5537, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.25, 0.20, -0.5,  5.4275, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.25, 0.20,  0.0,  4.3712, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.25, 0.20,  0.5,  3.0086, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.20, 0.25, -0.5,  5.4061, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.20, 0.25,  0.0,  4.3451, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.10, 0.20, 0.25,  0.5,  2.9723, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.20, 0.20, -0.5, 10.7517, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.20, 0.20,  0.0,  8.7020, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.20, 0.20,  0.5,  6.0257, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.25, 0.20, -0.5, 12.1941, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.25, 0.20,  0.0,  9.9340, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.25, 0.20,  0.5,  7.0067, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.20, 0.25, -0.5, 12.1483, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.20, 0.25,  0.0,  9.8780, 1.0e-3),
            new BasketOptionTwoData(BasketType.SpreadBasket, Option.Type.Call, 3.0, 122.0, 120.0, 0.10, 0.10, 0.10, 0.50, 0.20, 0.25,  0.5,  6.9284, 1.0e-3),
        };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        // Strong references to keep observers from being GC'd (Phase 2x A.4).
        final SimpleQuote spot1 = new SimpleQuote(0.0);
        final SimpleQuote spot2 = new SimpleQuote(0.0);
        final SimpleQuote qRate1 = new SimpleQuote(0.0);
        final SimpleQuote qRate2 = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote vol1 = new SimpleQuote(0.0);
        final SimpleQuote vol2 = new SimpleQuote(0.0);

        final YieldTermStructure qTS1 = Utilities.flatRate(today, qRate1, dc);
        final YieldTermStructure qTS2 = Utilities.flatRate(today, qRate2, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final BlackVolTermStructure volTS1 = Utilities.flatVol(today, vol1, dc);
        final BlackVolTermStructure volTS2 = Utilities.flatVol(today, vol2, dc);

        for (final BasketOptionTwoData v : values) {
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(v.type, v.strike);
            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new EuropeanExercise(exDate);

            spot1.setValue(v.s1);
            spot2.setValue(v.s2);
            qRate1.setValue(v.q1);
            qRate2.setValue(v.q2);
            rRate.setValue(v.r);
            vol1.setValue(v.v1);
            vol2.setValue(v.v2);

            final GeneralizedBlackScholesProcess p1 = makeProcess(spot1, qTS1, rTS, volTS1);
            final GeneralizedBlackScholesProcess p2 = makeProcess(spot2, qTS2, rTS, volTS2);

            final PricingEngine engine;
            switch (v.basketType) {
                case MaxBasket:
                case MinBasket:
                    engine = new StulzEngine(p1, p2, v.rho);
                    break;
                case SpreadBasket:
                    engine = new KirkEngine(p1, p2, v.rho);
                    break;
                default:
                    throw new IllegalStateException("unknown basket type");
            }

            final BasketOption basketOption = new BasketOption(
                    basketTypeToPayoff(v.basketType, payoff), exercise);
            basketOption.setPricingEngine(engine);

            final double calculated = basketOption.NPV();
            final double error = Math.abs(calculated - v.result);
            if (error > v.tol) {
                fail("BasketOption " + v.basketType + " " + v.type + ":\n"
                        + "  s1 = " + v.s1 + ", s2 = " + v.s2 + "\n"
                        + "  q1 = " + v.q1 + ", q2 = " + v.q2 + "\n"
                        + "  r = " + v.r + ", t = " + v.t + "\n"
                        + "  v1 = " + v.v1 + ", v2 = " + v.v2 + "\n"
                        + "  rho = " + v.rho + "\n"
                        + "  expected = " + v.result + "\n"
                        + "  calculated = " + calculated + "\n"
                        + "  error = " + error + "\n"
                        + "  tolerance = " + v.tol);
            }
        }
    }

    /**
     * Bjerksund-Stensland (2014) spread option engine — closed-form
     * approximation cross-checked against Kirk on the same Haug spread-option
     * dataset. Both are second-order approximations; their difference is
     * generally within a few cents for realistic parameters.
     *
     * <p>Tolerance per case is loose (0.05 abs) since BS and Kirk are
     * different approximations; the goal is to verify correct formula wiring
     * (forwards, sigma, d1/d2/d3, sign).</p>
     */
    @Test
    public void testBjerksundStenslandSpreadEngine() {
        QL.info("Testing Bjerksund-Stensland spread option engine...");

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        // Strong references for observers.
        final SimpleQuote spot1 = new SimpleQuote(122.0);
        final SimpleQuote spot2 = new SimpleQuote(120.0);
        final SimpleQuote qRate = new SimpleQuote(0.10);
        final SimpleQuote rRate = new SimpleQuote(0.10);
        final SimpleQuote vol1 = new SimpleQuote(0.20);
        final SimpleQuote vol2 = new SimpleQuote(0.20);

        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final BlackVolTermStructure volTS1 = Utilities.flatVol(today, vol1, dc);
        final BlackVolTermStructure volTS2 = Utilities.flatVol(today, vol2, dc);

        final GeneralizedBlackScholesProcess p1 = makeProcess(spot1, qTS, rTS, volTS1);
        final GeneralizedBlackScholesProcess p2 = makeProcess(spot2, qTS, rTS, volTS2);

        // Reference: Haug spread option, K=3, T=0.1y, vol1=vol2=0.2.
        // BS-approx should track Kirk closely for at-the-money spreads.
        final double[] rhos = { -0.5, 0.0, 0.5 };
        final double[] kirkExpected = { 4.7530, 3.7970, 2.5537 };
        final double bsTol = 0.05;

        final Date exDate = today.add(timeToDays(0.10));
        final Exercise exercise = new EuropeanExercise(exDate);
        final PlainVanillaPayoff vanilla = new PlainVanillaPayoff(Option.Type.Call, 3.0);
        final SpreadBasketPayoff spreadPayoff = new SpreadBasketPayoff(vanilla);

        for (int i = 0; i < rhos.length; ++i) {
            final BasketOption opt = new BasketOption(spreadPayoff, exercise);
            opt.setPricingEngine(new BjerksundStenslandSpreadEngine(p1, p2, rhos[i]));
            final double calc = opt.NPV();
            final double error = Math.abs(calc - kirkExpected[i]);
            if (calc <= 0.0) {
                fail("BS spread option NPV non-positive at rho=" + rhos[i]
                        + ": " + calc);
            }
            if (error > bsTol) {
                fail("BS spread option diverges too far from Kirk at rho="
                        + rhos[i] + ": calc=" + calc
                        + ", kirk=" + kirkExpected[i]
                        + ", error=" + error + ", tol=" + bsTol);
            }
        }
    }

    /**
     * Sanity tests for the basket payoff classes themselves.
     */
    @Test
    public void testBasketPayoffs() {
        QL.info("Testing basket payoff classes...");

        final PlainVanillaPayoff call = new PlainVanillaPayoff(Option.Type.Call, 100.0);

        // Min basket: with strikes 90, 100, 110 → min = 90, call payoff = 0
        final MinBasketPayoff minP = new MinBasketPayoff(call);
        assertEquals(90.0, minP.accumulate(new double[]{ 90.0, 100.0, 110.0 }), 1e-15);
        assertEquals(0.0, minP.get(new double[]{ 90.0, 100.0, 110.0 }), 1e-15);
        assertEquals(10.0, minP.get(new double[]{ 110.0, 120.0, 130.0 }), 1e-15);

        // Max basket: max = 110, call payoff = 10
        final MaxBasketPayoff maxP = new MaxBasketPayoff(call);
        assertEquals(110.0, maxP.accumulate(new double[]{ 90.0, 100.0, 110.0 }), 1e-15);
        assertEquals(10.0, maxP.get(new double[]{ 90.0, 100.0, 110.0 }), 1e-15);

        // Average basket: equal weights → mean = 100, call payoff = 0
        final AverageBasketPayoff avgP = new AverageBasketPayoff(call, 3);
        assertEquals(100.0, avgP.accumulate(new double[]{ 90.0, 100.0, 110.0 }), 1e-12);
        assertEquals(0.0, avgP.get(new double[]{ 90.0, 100.0, 110.0 }), 1e-12);
        assertEquals(10.0, avgP.get(new double[]{ 110.0, 110.0, 110.0 }), 1e-12);

        // Spread basket: a[0] - a[1]; with 110, 100, K=100, call payoff = 10.
        final SpreadBasketPayoff spreadP = new SpreadBasketPayoff(call);
        assertEquals(10.0, spreadP.accumulate(new double[]{ 110.0, 100.0 }), 1e-15);
        assertEquals(10.0, spreadP.get(new double[]{ 210.0, 100.0 }), 1e-15);
    }

    // ---- Phase 4i.5c body-fills — MCAmericanBasketEngine consumers ----

    /** {@code C++ relativeError}: |x1-x2|/reference, fallback abs error if reference==0. */
    private static double relativeError(final double x1, final double x2, final double reference) {
        if (reference != 0.0) {
            return Math.abs(x1 - x2) / reference;
        }
        return Math.abs(x1 - x2);
    }

    /** Mirrors {@code C++ BasketOptionOneData} struct. */
    private static class BasketOptionOneData {
        final Option.Type type;
        final double strike;
        final double s;
        final double q;
        final double r;
        final double t;
        final double v;
        final double result;
        final double tol;

        BasketOptionOneData(final Option.Type type, final double strike, final double s,
                final double q, final double r, final double t, final double v,
                final double result, final double tol) {
            this.type = type;
            this.strike = strike;
            this.s = s;
            this.q = q;
            this.r = r;
            this.t = t;
            this.v = v;
            this.result = result;
            this.tol = tol;
        }
    }

    /** Mirrors {@code C++ BasketOptionThreeData} struct (subset used in testTavellaValues). */
    private static class BasketOptionThreeData {
        final BasketType basketType;
        final Option.Type type;
        final double strike;
        final double s1, s2, s3;
        final double r;
        final double t;       // years
        final double v1, v2, v3;
        final double rho;
        final double euroValue;
        final double amValue;

        BasketOptionThreeData(final BasketType bt, final Option.Type type,
                final double strike, final double s1, final double s2, final double s3,
                final double r, final double t,
                final double v1, final double v2, final double v3,
                final double rho, final double euroValue, final double amValue) {
            this.basketType = bt;
            this.type = type;
            this.strike = strike;
            this.s1 = s1;
            this.s2 = s2;
            this.s3 = s3;
            this.r = r;
            this.t = t;
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
            this.rho = rho;
            this.euroValue = euroValue;
            this.amValue = amValue;
        }
    }

    /**
     * One-asset American put values used by {@code testOneDAmericanValues}
     * and {@code testOddSamples}. Mirrors C++ {@code oneDataValues[]}.
     */
    private static final BasketOptionOneData[] ONE_D_VALUES = new BasketOptionOneData[] {
        // type, strike, spot, q, r, t, vol, value, tol
        new BasketOptionOneData(Option.Type.Put, 100.00,  80.00, 0.0, 0.06, 0.5, 0.4, 21.6059, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00,  85.00, 0.0, 0.06, 0.5, 0.4, 18.0374, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00,  90.00, 0.0, 0.06, 0.5, 0.4, 14.9187, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00,  95.00, 0.0, 0.06, 0.5, 0.4, 12.2314, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00, 100.00, 0.0, 0.06, 0.5, 0.4,  9.9458, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00, 105.00, 0.0, 0.06, 0.5, 0.4,  8.0281, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00, 110.00, 0.0, 0.06, 0.5, 0.4,  6.4352, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00, 115.00, 0.0, 0.06, 0.5, 0.4,  5.1265, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 100.00, 120.00, 0.0, 0.06, 0.5, 0.4,  4.0611, 1e-2),

        // Longstaff-Schwartz 1D example (Laguerre + 100k paths in original).
        new BasketOptionOneData(Option.Type.Put, 40.00, 36.00, 0.0, 0.06, 1.0, 0.2,  4.478, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 36.00, 0.0, 0.06, 2.0, 0.2,  4.840, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 36.00, 0.0, 0.06, 1.0, 0.4,  7.101, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 36.00, 0.0, 0.06, 2.0, 0.4,  8.508, 1e-2),

        new BasketOptionOneData(Option.Type.Put, 40.00, 38.00, 0.0, 0.06, 1.0, 0.2,  3.250, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 38.00, 0.0, 0.06, 2.0, 0.2,  3.745, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 38.00, 0.0, 0.06, 1.0, 0.4,  6.148, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 38.00, 0.0, 0.06, 2.0, 0.4,  7.670, 1e-2),

        new BasketOptionOneData(Option.Type.Put, 40.00, 40.00, 0.0, 0.06, 1.0, 0.2,  2.314, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 40.00, 0.0, 0.06, 2.0, 0.2,  2.885, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 40.00, 0.0, 0.06, 1.0, 0.4,  5.312, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 40.00, 0.0, 0.06, 2.0, 0.4,  6.920, 1e-2),

        new BasketOptionOneData(Option.Type.Put, 40.00, 42.00, 0.0, 0.06, 1.0, 0.2,  1.617, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 42.00, 0.0, 0.06, 2.0, 0.2,  2.212, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 42.00, 0.0, 0.06, 1.0, 0.4,  4.582, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 42.00, 0.0, 0.06, 2.0, 0.4,  6.248, 1e-2),

        new BasketOptionOneData(Option.Type.Put, 40.00, 44.00, 0.0, 0.06, 1.0, 0.2,  1.110, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 44.00, 0.0, 0.06, 2.0, 0.2,  1.690, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 44.00, 0.0, 0.06, 1.0, 0.4,  3.948, 1e-2),
        new BasketOptionOneData(Option.Type.Put, 40.00, 44.00, 0.0, 0.06, 2.0, 0.4,  5.647, 1e-2),
    };

    /**
     * Three-asset American max-call against Tavella 2002 reference 18.082.
     *
     * <p>Mirrors C++ {@code testTavellaValues}: {@code MaxBasket Call K=100,
     * S1=S2=S3=100, q=0.10, r=0.05, T=3y, sigma1=sigma2=sigma3=0.20,
     * rho_{12}=-0.25, rho_{13}=0.25, rho_{23}=0.30}.</p>
     *
     * <p>C++ uses 10000 samples + {@code calibrationSamples = samples/4 = 2500}
     * + 20 time steps + antithetic + seed 0; tolerance is 1% relative.
     * Mirrors C++ exactly; this also matches {@link
     * org.jquantlib.testsuite.pricingengines.basket.MCAmericanBasketEngineTest#testTavellaValues3D
     * MCAmericanBasketEngineTest.testTavellaValues3D} which already exercises
     * the identical scenario.</p>
     *
     * <p>Reference: Tavella, D. A., "Quantitative Methods in Derivatives
     * Pricing", Wiley 2002.</p>
     */
    @Test
    public void testTavellaValues() {
        QL.info("Testing three-asset American basket options against Tavella's values...");

        final BasketOptionThreeData[] values = new BasketOptionThreeData[] {
            // basketType, type, strike, s1, s2, s3, r, t (years), v1, v2, v3, rho, euroValue, amValue
            new BasketOptionThreeData(BasketType.MaxBasket, Option.Type.Call,
                    100.0, 100.0, 100.0, 100.0, 0.05, 3.0, 0.20, 0.20, 0.20, 0.0,
                    -999.0, 18.082),
        };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        // Strong references for observers (Phase 2x A.4).
        final SimpleQuote spot1 = new SimpleQuote(0.0);
        final SimpleQuote spot2 = new SimpleQuote(0.0);
        final SimpleQuote spot3 = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.10);
        final SimpleQuote rRate = new SimpleQuote(0.05);
        final SimpleQuote vol1 = new SimpleQuote(0.0);
        final SimpleQuote vol2 = new SimpleQuote(0.0);
        final SimpleQuote vol3 = new SimpleQuote(0.0);

        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final BlackVolTermStructure volTS1 = Utilities.flatVol(today, vol1, dc);
        final BlackVolTermStructure volTS2 = Utilities.flatVol(today, vol2, dc);
        final BlackVolTermStructure volTS3 = Utilities.flatVol(today, vol3, dc);

        final double mcRelativeErrorTolerance = 0.01;
        final int requiredSamples = 10000;
        final int timeSteps = 20;
        final long seed = 0L;

        final BasketOptionThreeData v = values[0];
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(v.type, v.strike);

        final Date exDate = today.add(timeToDays(v.t));
        final Exercise exercise = new AmericanExercise(today, exDate);

        spot1.setValue(v.s1);
        spot2.setValue(v.s2);
        spot3.setValue(v.s3);
        vol1.setValue(v.v1);
        vol2.setValue(v.v2);
        vol3.setValue(v.v3);

        final StochasticProcess1D p1 = new BlackScholesMertonProcess(
                new Handle<Quote>(spot1), new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS), new Handle<BlackVolTermStructure>(volTS1));
        final StochasticProcess1D p2 = new BlackScholesMertonProcess(
                new Handle<Quote>(spot2), new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS), new Handle<BlackVolTermStructure>(volTS2));
        final StochasticProcess1D p3 = new BlackScholesMertonProcess(
                new Handle<Quote>(spot3), new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS), new Handle<BlackVolTermStructure>(volTS3));

        final List<StochasticProcess1D> procs = new ArrayList<StochasticProcess1D>();
        procs.add(p1);
        procs.add(p2);
        procs.add(p3);

        final Matrix correlation = new Matrix(3, 3);
        for (int j = 0; j < 3; ++j) {
            correlation.set(j, j, 1.0);
        }
        correlation.set(1, 0, -0.25);
        correlation.set(0, 1, -0.25);
        correlation.set(2, 0, 0.25);
        correlation.set(0, 2, 0.25);
        correlation.set(2, 1, 0.30);
        correlation.set(1, 2, 0.30);

        final StochasticProcessArray process = new StochasticProcessArray(procs, correlation);

        final MCAmericanBasketEngine mcLSMCEngine = new MCAmericanBasketEngine(
                process,
                /* timeSteps */ timeSteps,
                /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                /* brownianBridge */ false,
                /* antitheticVariate */ true,
                /* requiredSamples */ requiredSamples,
                /* requiredTolerance */ McSimulation.NULL_TOLERANCE,
                /* maxSamples */ McSimulation.NULL_SAMPLES,
                /* seed */ seed,
                /* nCalibrationSamples */ requiredSamples / 4,
                /* polynomialOrder */ 2,
                LsmBasisSystem.PolynomialType.Monomial);

        final BasketOption basketOption = new BasketOption(
                basketTypeToPayoff(v.basketType, payoff), exercise);
        basketOption.setPricingEngine(mcLSMCEngine);

        final double calculated = basketOption.NPV();
        final double expected = v.amValue;
        final double relError = relativeError(calculated, expected, v.s1);
        if (relError > mcRelativeErrorTolerance) {
            fail("MC LSMC Tavella value: " + v.basketType + " " + v.type + ":\n"
                    + "  expected   = " + expected + "\n"
                    + "  calculated = " + calculated + "\n"
                    + "  relError   = " + relError + "\n"
                    + "  tolerance  = " + mcRelativeErrorTolerance);
        }
    }

    /**
     * One-asset American max basket options, sliced over the 29-row
     * {@link #ONE_D_VALUES} dataset. Mirrors C++
     * {@code testOneDAmericanValues} (which uses Boost slice templates
     * to split into 5 sub-cases). Java consolidates into a single test
     * since the slicing was a Boost runtime-throughput tweak, not a
     * semantic distinction.
     *
     * <p>Each row prices an American max-payoff basket containing a
     * single 1-D underlying (degenerate basket) using
     * {@link MCAmericanBasketEngine}; expected values are 1-D American
     * put references from Longstaff-Schwartz (2001) and other sources
     * (see C++ comment block).</p>
     *
     * <p>10000 samples + 52 time steps + antithetic + seed 0,
     * tolerance per row 1% relative.</p>
     */
    @Test
    public void testOneDAmericanValues() {
        QL.info("Testing basket American options against 1-D case...");

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        // Strong references for observers (Phase 2x A.4).
        final SimpleQuote spot1 = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.05);
        final SimpleQuote vol1 = new SimpleQuote(0.0);

        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final BlackVolTermStructure volTS1 = Utilities.flatVol(today, vol1, dc);

        final int requiredSamples = 10000;
        final int timeSteps = 52;
        final long seed = 0L;

        final StochasticProcess1D stochProcess1 = new BlackScholesMertonProcess(
                new Handle<Quote>(spot1), new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS1));

        final List<StochasticProcess1D> procs = new ArrayList<StochasticProcess1D>();
        procs.add(stochProcess1);

        final Matrix correlation = new Matrix(1, 1);
        correlation.set(0, 0, 1.0);

        final StochasticProcessArray process = new StochasticProcessArray(procs, correlation);

        final MCAmericanBasketEngine mcLSMCEngine = new MCAmericanBasketEngine(
                process,
                /* timeSteps */ timeSteps,
                /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                /* brownianBridge */ false,
                /* antitheticVariate */ true,
                /* requiredSamples */ requiredSamples,
                /* requiredTolerance */ McSimulation.NULL_TOLERANCE,
                /* maxSamples */ McSimulation.NULL_SAMPLES,
                /* seed */ seed,
                /* nCalibrationSamples */ requiredSamples / 4,
                /* polynomialOrder */ 2,
                LsmBasisSystem.PolynomialType.Monomial);

        for (int i = 0; i < ONE_D_VALUES.length; ++i) {
            final BasketOptionOneData v = ONE_D_VALUES[i];
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(v.type, v.strike);

            final Date exDate = today.add(timeToDays(v.t));
            final Exercise exercise = new AmericanExercise(today, exDate);

            spot1.setValue(v.s);
            vol1.setValue(v.v);
            rRate.setValue(v.r);
            qRate.setValue(v.q);

            final BasketOption basketOption = new BasketOption(
                    basketTypeToPayoff(BasketType.MaxBasket, payoff), exercise);
            basketOption.setPricingEngine(mcLSMCEngine);

            final double calculated = basketOption.NPV();
            final double expected = v.result;
            final double relError = relativeError(calculated, expected, v.s);

            if (relError > v.tol) {
                fail("Row " + i + " (S=" + v.s + ", t=" + v.t + ", vol=" + v.v
                        + ", r=" + v.r + "):\n"
                        + "  expected   = " + expected + "\n"
                        + "  calculated = " + calculated + "\n"
                        + "  relError   = " + relError + "\n"
                        + "  tol        = " + v.tol);
            }
        }
    }

    /**
     * Regression test for an antithetic-variate sample-array sizing crash
     * when {@code requiredSamples} is odd. Mirrors C++ {@code testOddSamples}
     * (Brennan-Schwartz). The single row is the in-the-money put from the
     * top of {@link #ONE_D_VALUES}; the goal is structural (no crash) +
     * value within 1% relative.
     *
     * <p>10001 samples (the odd number that triggered the historical bug),
     * 53 time steps, antithetic, seed 0.</p>
     */
    @Test
    public void testOddSamples() {
        QL.info("Testing antithetic engine using odd sample number...");

        final int requiredSamples = 10001; // The important line — odd.
        final int timeSteps = 53;
        final BasketOptionOneData[] values = new BasketOptionOneData[] {
            // type, strike, spot, q, r, t, vol, value, tol
            new BasketOptionOneData(Option.Type.Put, 100.00, 80.00, 0.0, 0.06, 0.5, 0.4, 21.6059, 1e-2),
        };

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        // Strong references for observers (Phase 2x A.4).
        final SimpleQuote spot1 = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.05);
        final SimpleQuote vol1 = new SimpleQuote(0.0);

        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final BlackVolTermStructure volTS1 = Utilities.flatVol(today, vol1, dc);

        final long seed = 0L;

        final StochasticProcess1D stochProcess1 = new BlackScholesMertonProcess(
                new Handle<Quote>(spot1), new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS1));

        final List<StochasticProcess1D> procs = new ArrayList<StochasticProcess1D>();
        procs.add(stochProcess1);

        final Matrix correlation = new Matrix(1, 1);
        correlation.set(0, 0, 1.0);

        final StochasticProcessArray process = new StochasticProcessArray(procs, correlation);

        final MCAmericanBasketEngine mcLSMCEngine = new MCAmericanBasketEngine(
                process,
                /* timeSteps */ timeSteps,
                /* timeStepsPerYear */ McSimulation.NULL_SAMPLES,
                /* brownianBridge */ false,
                /* antitheticVariate */ true,
                /* requiredSamples */ requiredSamples,
                /* requiredTolerance */ McSimulation.NULL_TOLERANCE,
                /* maxSamples */ McSimulation.NULL_SAMPLES,
                /* seed */ seed,
                /* nCalibrationSamples */ requiredSamples / 4,
                /* polynomialOrder */ 2,
                LsmBasisSystem.PolynomialType.Monomial);

        for (final BasketOptionOneData value : values) {
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(value.type, value.strike);

            final Date exDate = today.add(timeToDays(value.t));
            final Exercise exercise = new AmericanExercise(today, exDate);

            spot1.setValue(value.s);
            vol1.setValue(value.v);
            rRate.setValue(value.r);
            qRate.setValue(value.q);

            final BasketOption basketOption = new BasketOption(
                    basketTypeToPayoff(BasketType.MaxBasket, payoff), exercise);
            basketOption.setPricingEngine(mcLSMCEngine);

            final double calculated = basketOption.NPV();
            final double expected = value.result;
            final double relError = relativeError(calculated, expected, value.s);

            // Sanity: NPV strictly positive, no crash.
            assertTrue("NPV must be > 0 for ITM American put: " + calculated,
                    calculated > 0.0);
            if (relError > value.tol) {
                fail("Odd-sample MC: expected " + expected
                        + ", got " + calculated
                        + " (relError " + relError + " > tol " + value.tol + ")");
            }
        }
    }

    // ---- Skeleton placeholders (Phase 5k.5b carry-forward) -----------

    @Ignore(REASON_BARRAQUAND)         @Test public void testBarraquandThreeValues()                  { fail("not implemented"); }
    @Ignore(REASON_LOCAL_VOL_SPREAD)   @Test public void testLocalVolatilitySpreadOption()            { fail("not implemented"); }
    @Ignore(REASON_2D_PDE)             @Test public void test2DPDEGreeks()                            { fail("not implemented"); }
    @Ignore(REASON_OPERATOR_SPLITTING) @Test public void testOperatorSplittingSpreadEngine()          { fail("not implemented"); }
    @Ignore(REASON_STRANG_SPLITTING)   @Test public void testStrangSplittingSpreadEngineVsMathematica() { fail("not implemented"); }
    @Ignore(REASON_KIRK_PDE)           @Test public void testPDEvsApproximations()                    { fail("not implemented"); }
    @Ignore(REASON_NDIM_PDE)           @Test public void testNdimPDEvs2dimPDE()                       { fail("not implemented"); }
    @Ignore(REASON_NDIM_PDE)           @Test public void testNdimPDEinDifferentDims()                 { fail("not implemented"); }
    @Ignore(REASON_DENG_LI_ZHOU)       @Test public void testDengLiZhouVsPDE()                        { fail("not implemented"); }
    @Ignore(REASON_DENG_LI_ZHOU)       @Test public void testDengLiZhouWithNegativeStrike()           { fail("not implemented"); }
    @Ignore(REASON_ROOT_SUM_EXP)       @Test public void testRootOfSumExponentials()                  { fail("not implemented"); }
    @Ignore(REASON_BSM_BASKET)         @Test public void testSingleFactorBsmBasketEngine()            { fail("not implemented"); }
    @Ignore(REASON_GOLDEN_CHOI)        @Test public void testGoldenChoiBasketEngineExample()          { fail("not implemented"); }
    @Ignore(REASON_BENCHMARK)          @Test public void testSpreadAndBasketBenchmarks()              { fail("not implemented"); }
    @Ignore(REASON_FDM_AMERICAN)       @Test public void testFdmAmericanBasketOptions()               { fail("not implemented"); }
    @Ignore(REASON_FDM_AMERICAN)       @Test public void testAccurateAmericanBasketOptions()          { fail("not implemented"); }
    @Ignore(REASON_NO_DIV_ZERO)        @Test public void testNoDivByZeroOperatorSplitting()           { fail("not implemented"); }

    // Suppress unused-warning for the catch-all reasons (some have been
    // body-filled in Phase 4i.5c — REASON_AMERICAN_BASKET and
    // REASON_MC_BASKET no longer tag any active @Ignore).
    @SuppressWarnings("unused")
    private static final String UNUSED_INSTRUMENT_REASON = REASON_INSTRUMENT;
    @SuppressWarnings("unused")
    private static final String UNUSED_AMERICAN_BASKET_REASON = REASON_AMERICAN_BASKET;
    @SuppressWarnings("unused")
    private static final String UNUSED_MC_BASKET_REASON = REASON_MC_BASKET;
}
