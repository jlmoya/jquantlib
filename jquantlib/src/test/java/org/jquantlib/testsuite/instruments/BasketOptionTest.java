/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
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
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.basket.BjerksundStenslandSpreadEngine;
import org.jquantlib.pricingengines.basket.KirkEngine;
import org.jquantlib.pricingengines.basket.StulzEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
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

    // ---- Skeleton placeholders (Phase 5k.5b carry-forward) -----------

    @Ignore(REASON_BARRAQUAND)         @Test public void testBarraquandThreeValues()                  { fail("not implemented"); }
    @Ignore(REASON_AMERICAN_BASKET)    @Test public void testTavellaValues()                          { fail("not implemented"); }
    @Ignore(REASON_AMERICAN_BASKET)    @Test public void testOneDAmericanValues()                     { fail("not implemented"); }
    @Ignore(REASON_MC_BASKET)          @Test public void testOddSamples()                             { fail("not implemented"); }
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

    // Suppress unused-warning for the catch-all reason.
    @SuppressWarnings("unused")
    private static final String UNUSED_INSTRUMENT_REASON = REASON_INSTRUMENT;
}
