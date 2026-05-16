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
 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.pricingengines;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.pricingengines.BachelierCalculator;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.pricingengines.BlackFormula;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/bacheliercalculator.cpp (Phase 5g).
 *
 * <p>The C++ file has eight test cases exercising
 * {@code BachelierCalculator} (the Bachelier-model analogue of
 * {@code BlackCalculator}): basic values, Greeks, put-call parity, edge
 * cases, numerical derivatives, comparison vs analytical formula, zero
 * volatility Greeks, and Bachelier-vs-Black convergence.
 *
 * <p>Phase 5e.5b-CFC-d-32: {@link BachelierCalculator} ported, six
 * previously-deferred Greek-bearing cases now body-filled.
 */
public class BachelierCalculatorTest {

    public BachelierCalculatorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of {@code testBachelierCalculatorBasicValues} (lines 45-109).
     * Now exercises {@link BachelierCalculator} via both constructor flavours
     * and cross-checks against {@link BlackFormula#bachelierBlackFormula}.
     */
    @Test
    public void testBachelierCalculatorBasicValues() {
        QL.info("Testing BachelierCalculator basic option values...");

        // Reference values from C++ v1.42.1.
        // {type, strike, forward, stdDev, discount, tolerance, refValue}
        final double[][] data = {
            { 0, 100.0, 100.0, 20.0, 1.0, 1e-8, 7.9788456080286538 }, // ATM Call
            { 1, 100.0, 100.0, 20.0, 1.0, 1e-8, 7.9788456080286538 }, // ATM Put
            { 0,  90.0, 100.0, 20.0, 1.0, 1e-8, 13.955931148026121 }, // ITM Call
            { 1, 110.0, 100.0, 20.0, 1.0, 1e-8, 13.955931148026121 }, // ITM Put
            { 0, 110.0, 100.0, 20.0, 1.0, 1e-8, 3.9559311480261217 }, // OTM Call
            { 1,  90.0, 100.0, 20.0, 1.0, 1e-8, 3.9559311480261217 }, // OTM Put
            { 0, 100.0, 100.0,  0.0, 1.0, 1e-8, 0.0 },                // Zero vol Call
            { 1, 100.0, 100.0,  0.0, 1.0, 1e-8, 0.0 },                // Zero vol Put
            { 0,   0.0, 100.0, 20.0, 1.0, 1e-8, 100.00000106923312 }, // Zero strike
        };
        for (final double[] row : data) {
            final Option.Type type = (row[0] == 0) ? Option.Type.Call : Option.Type.Put;
            final double strike   = row[1];
            final double forward  = row[2];
            final double stdDev   = row[3];
            final double discount = row[4];
            final double tol      = row[5];
            final double refValue = row[6];

            // C++ test uses both constructors (Option::Type + Payoff) and
            // asserts they produce the same answer. Mirror that here.
            final BachelierCalculator calc1 = new BachelierCalculator(
                    type, strike, forward, stdDev, discount);
            final double value1 = calc1.value();

            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
            final BachelierCalculator calc2 = new BachelierCalculator(
                    payoff, forward, stdDev, discount);
            final double value2 = calc2.value();

            assertEquals("BachelierCalculator constructor mismatch (" + type
                    + " K=" + strike + ")", value1, value2, tol);

            assertEquals("BachelierCalculator basic value (" + type + " K=" + strike + ")",
                    refValue, value1, tol);

            // Cross-check vs the static Bachelier formula.
            final double vStatic = BlackFormula.bachelierBlackFormula(
                    type, strike, forward, stdDev, discount);
            assertEquals("BachelierCalculator vs static formula (" + type
                    + " K=" + strike + ")", vStatic, value1, tol);

            // Zero volatility ⇒ intrinsic value.
            if (stdDev == 0.0) {
                final double intrinsic = discount * Math.max(0.0,
                        type == Option.Type.Call ? forward - strike : strike - forward);
                assertEquals("BachelierCalculator zero-vol intrinsic (" + type + ")",
                        intrinsic, value1, tol);
            }

            // Non-negative.
            assertTrue("BachelierCalculator returned negative value: " + value1,
                    value1 >= -tol);
        }
    }

    /**
     * Faithful port of {@code testBachelierCalculatorGreeks} (C++ lines 111-221).
     * Reference values are the inline doubles from C++ — these are the
     * canonical numbers produced by running the C++ unit test.
     */
    @Test
    public void testBachelierCalculatorGreeks() {
        QL.info("Testing BachelierCalculator Greeks calculations...");

        final double forward = 100.0;
        final double strike = 105.0;
        final double stdDev = 20.0;
        final double discount = 0.95;
        final double spot = 98.0;
        final double maturity = 1.0;
        final double tolerance = 1e-6;

        // Reference Greek values from C++ v1.42.1 test-suite/bacheliercalculator.cpp.
        final double refDelta              =  0.38900917408288;
        final double refDeltaFwd           =  0.38122899060122245;
        final double refGamma              =  0.019124047842706517;
        final double refGammaFwd           =  0.018366735548135338;
        final double refTheta              = -4.3159316452046594;
        final double refVega               =  0.36733471096270676;
        final double refRho                = 32.682349793874224;
        final double refElasticity         =  7.0071783554334042;
        final double refElasticityFwd      =  7.0071783554334051;
        final double refItmCashProb        =  0.4012936743170763;
        final double refItmAssetProb       =  0.4012936743170763;
        final double refDividendRho        = -38.122899060122243;
        final double refStrikeSensitivity  = -0.38122899060122245;
        final double refStrikeGamma        =  0.018366735548135338;
        final double refVanna              =  0.0048333514600356151;
        final double refVolga              =  0.0011479209717584586;

        final BachelierCalculator calc = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev, discount);

        assertEquals("Bachelier deltaForward",     refDeltaFwd,           calc.deltaForward(),         tolerance);
        assertEquals("Bachelier delta",            refDelta,              calc.delta(spot),            tolerance);
        assertEquals("Bachelier gammaForward",     refGammaFwd,           calc.gammaForward(),         tolerance);
        assertEquals("Bachelier gamma",            refGamma,              calc.gamma(spot),            tolerance);
        assertEquals("Bachelier theta",            refTheta,              calc.theta(spot, maturity),  tolerance);
        assertEquals("Bachelier vega",             refVega,               calc.vega(maturity),         tolerance);
        assertEquals("Bachelier rho",              refRho,                calc.rho(maturity),          tolerance);
        assertEquals("Bachelier elasticity",       refElasticity,         calc.elasticity(spot),       tolerance);
        assertEquals("Bachelier elasticityFwd",    refElasticityFwd,      calc.elasticityForward(),    tolerance);
        assertEquals("Bachelier itmCashProb",      refItmCashProb,        calc.itmCashProbability(),   tolerance);
        assertEquals("Bachelier itmAssetProb",     refItmAssetProb,       calc.itmAssetProbability(),  tolerance);
        assertEquals("Bachelier dividendRho",      refDividendRho,        calc.dividendRho(maturity),  tolerance);
        assertEquals("Bachelier strikeSensitivity",refStrikeSensitivity,  calc.strikeSensitivity(),    tolerance);
        assertEquals("Bachelier strikeGamma",      refStrikeGamma,        calc.strikeGamma(),          tolerance);
        assertEquals("Bachelier vanna",            refVanna,              calc.vanna(maturity),        tolerance);
        assertEquals("Bachelier volga",            refVolga,              calc.volga(maturity),        tolerance);
    }

    /**
     * Faithful port of {@code testBachelierCalculatorPutCallParity} (lines 223-248):
     * {@code C - P = discount * (F - K)}.
     */
    @Test
    public void testBachelierCalculatorPutCallParity() {
        QL.info("Testing BachelierCalculator put-call parity...");

        final double forward = 100.0;
        final double strike = 105.0;
        final double stdDev = 25.0;
        final double discount = 0.95;
        final double tolerance = 1e-10;

        final BachelierCalculator callCalc = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev, discount);
        final BachelierCalculator putCalc = new BachelierCalculator(
                Option.Type.Put, strike, forward, stdDev, discount);

        final double lhs = callCalc.value() - putCalc.value();
        final double rhs = discount * (forward - strike);
        assertEquals("Bachelier put-call parity", rhs, lhs, tolerance);

        // Cross-check vs the static Bachelier formula.
        final double callStatic = BlackFormula.bachelierBlackFormula(
                Option.Type.Call, strike, forward, stdDev, discount);
        final double putStatic = BlackFormula.bachelierBlackFormula(
                Option.Type.Put, strike, forward, stdDev, discount);
        assertEquals("Bachelier call vs static formula",
                callStatic, callCalc.value(), tolerance);
        assertEquals("Bachelier put vs static formula",
                putStatic, putCalc.value(), tolerance);
    }

    /**
     * Faithful port of {@code testBachelierCalculatorEdgeCases} (lines 250-317):
     * zero vol, very high vol, negative strike, negative forward, deep ITM,
     * deep OTM. Reference values inline from C++.
     */
    @Test
    public void testBachelierCalculatorEdgeCases() {
        QL.info("Testing BachelierCalculator edge cases...");

        final double tolerance = 1e-8;

        // Zero volatility — ATM call → 0.
        {
            final BachelierCalculator calc = new BachelierCalculator(
                    Option.Type.Call, 100.0, 100.0, 0.0, 1.0);
            assertEquals("Zero-vol ATM call", 0.0, calc.value(), tolerance);
        }

        // Very high volatility.
        {
            final BachelierCalculator calc = new BachelierCalculator(
                    Option.Type.Call, 100.0, 100.0, 200.0, 1.0);
            assertEquals("Very high vol", 79.788456080286537, calc.value(), tolerance);
        }

        // Negative strike (valid in Bachelier model). Value should be at
        // least intrinsic - 10 (the C++ test allows that slack).
        {
            final BachelierCalculator calc = new BachelierCalculator(
                    Option.Type.Call, -50.0, 100.0, 20.0, 1.0);
            final double v = calc.value();
            final double intrinsic = 100.0 - (-50.0);
            assertTrue("Negative-strike call below intrinsic - 10: "
                    + v + " vs " + intrinsic, v >= intrinsic - 10.0);
        }

        // Negative forward (valid in Bachelier model).
        {
            final BachelierCalculator calc = new BachelierCalculator(
                    Option.Type.Call, 50.0, -100.0, 20.0, 1.0);
            final double v = calc.value();
            final double intrinsic = -100.0 - 50.0; // -150
            assertTrue("Negative-forward call vs intrinsic: "
                    + v + " vs " + intrinsic, v >= intrinsic + 10.0);
        }

        // Deep ITM call (strike 50, forward 100): should be >= intrinsic.
        {
            final BachelierCalculator calc = new BachelierCalculator(
                    Option.Type.Call, 50.0, 100.0, 20.0, 1.0);
            final double v = calc.value();
            final double intrinsic = 100.0 - 50.0;
            assertTrue("Deep ITM call below intrinsic: "
                    + v + " vs " + intrinsic, v >= intrinsic - tolerance);
        }

        // Deep OTM call (strike 150, forward 100). C++ reference value.
        {
            final BachelierCalculator calc = new BachelierCalculator(
                    Option.Type.Call, 150.0, 100.0, 20.0, 1.0);
            assertEquals("Deep OTM call", 0.040082743582562863,
                    calc.value(), tolerance);
        }
    }

    /**
     * Faithful port of {@code testBachelierCalculatorNumericalDerivatives}
     * (lines 319-400). Finite-difference consistency checks for delta,
     * gamma, vega, vanna, volga.
     */
    @Test
    public void testBachelierCalculatorNumericalDerivatives() {
        QL.info("Testing BachelierCalculator numerical derivative consistency...");

        final double forward = 100.0;
        final double strike = 100.0;
        final double stdDev = 20.0;
        final double discount = 0.95;
        final double maturity = 1.0;
        final double bump = 1e-4;
        final double tolerance = 1e-3;

        final BachelierCalculator calc = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev, discount);

        // Delta via finite differences on forward.
        final BachelierCalculator calcUp = new BachelierCalculator(
                Option.Type.Call, strike, forward + bump, stdDev, discount);
        final BachelierCalculator calcDown = new BachelierCalculator(
                Option.Type.Call, strike, forward - bump, stdDev, discount);

        final double analyticalDelta = calc.deltaForward();
        final double numericalDelta = (calcUp.value() - calcDown.value()) / (2.0 * bump);
        assertEquals("Bachelier dV/dF finite-diff", analyticalDelta, numericalDelta, tolerance);

        // Gamma via finite differences on forward.
        final double analyticalGamma = calc.gammaForward();
        final double numericalGamma = (calcUp.deltaForward() - calcDown.deltaForward())
                / (2.0 * bump);
        assertEquals("Bachelier d2V/dF2 finite-diff", analyticalGamma, numericalGamma, tolerance);

        // Vega via finite differences on stdDev. C++ multiplies the
        // numerical derivative by sqrt(maturity) before comparison
        // (vega is dV/dsigma * sqrt(T) in QuantLib convention).
        final BachelierCalculator calcVolUp = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev + bump, discount);
        final BachelierCalculator calcVolDown = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev - bump, discount);
        final double analyticalVega = calc.vega(maturity);
        final double numericalVega = (calcVolUp.value() - calcVolDown.value()) / (2.0 * bump);
        assertEquals("Bachelier vega finite-diff",
                analyticalVega, numericalVega * Math.sqrt(maturity), tolerance);

        // Vanna = dVega/dForward via finite differences.
        final double vanna = calc.vanna(maturity);
        final double vegaFwdUp = calcUp.vega(maturity);
        final double vegaFwdDown = calcDown.vega(maturity);
        final double vannaFD = (vegaFwdUp - vegaFwdDown) / (2.0 * bump);
        assertEquals("Bachelier vanna finite-diff", vanna, vannaFD, tolerance);

        // Volga = dVega/dVol via finite differences.
        final double volga = calc.volga(maturity);
        final double vegaVolUp = calcVolUp.vega(maturity);
        final double vegaVolDown = calcVolDown.vega(maturity);
        final double volgaFD = (vegaVolUp - vegaVolDown) / (2.0 * bump);
        assertEquals("Bachelier volga finite-diff", volga, volgaFD, tolerance);
    }

    /**
     * Faithful port of {@code testBachelierCalculatorAgainstAnalyticalFormula}
     * (lines 402-429). Verifies {@code BachelierCalculator.value()} matches
     * the closed-form Bachelier formula {@code discount * [(F-K) N(d) + sigma n(d)]}.
     */
    @Test
    public void testBachelierCalculatorAgainstAnalyticalFormula() {
        QL.info("Testing BachelierCalculator against analytical Bachelier formula...");

        final double forward = 100.0;
        final double strike = 95.0;
        final double stdDev = 15.0;
        final double discount = 0.98;
        final double tolerance = 1e-10;

        final BachelierCalculator calc = new BachelierCalculator(
                Option.Type.Call, strike, forward, stdDev, discount);
        final double calculated = calc.value();

        final double d = (forward - strike) / stdDev;
        final CumulativeNormalDistribution N = new CumulativeNormalDistribution();
        final NormalDistribution n = new NormalDistribution();

        final double analytical = discount * ((forward - strike) * N.op(d) + stdDev * n.op(d));
        assertEquals("Bachelier vs analytical formula",
                analytical, calculated, tolerance);
    }

    /**
     * Faithful port of {@code testBachelierCalculatorZeroVolatilityGreeks}
     * (lines 431-558). Asserts Greeks are finite, gamma/vega/vanna/volga
     * are zero, deltaForward is the correct {0, 0.5, 1} step for the
     * appropriate moneyness, and ITM probabilities are {0, 0.5, 1}.
     */
    @Test
    public void testBachelierCalculatorZeroVolatilityGreeks() {
        QL.info("Testing BachelierCalculator Greeks with zero volatility...");

        final double tolerance = 1e-10;
        final double forward = 100.0;
        final double discount = 1.0;
        final double spot = 98.0;
        final double maturity = 1.0;
        final double stdDev = 0.0;

        // {type, strike, description}
        final Object[][] testCases = {
            { Option.Type.Call,  90.0, "ITM Call" },
            { Option.Type.Put,  110.0, "ITM Put" },
            { Option.Type.Call, 100.0, "ATM Call" },
            { Option.Type.Put,  100.0, "ATM Put" },
            { Option.Type.Call,  90.0, "OTM Call (mislabeled in C++; actually ITM)" },
            { Option.Type.Put,  110.0, "OTM Put (mislabeled in C++; actually ITM)" },
            { Option.Type.Call, -10.0, "Negative Strike Call" },
            { Option.Type.Put,  200.0, "High Strike Put" },
        };

        for (final Object[] tc : testCases) {
            final Option.Type type = (Option.Type) tc[0];
            final double strike = (Double) tc[1];
            final String desc = (String) tc[2];

            final BachelierCalculator calc = new BachelierCalculator(
                    type, strike, forward, stdDev, discount);

            final double deltaForward = calc.deltaForward();
            final double delta = calc.delta(spot);
            final double gammaForward = calc.gammaForward();
            final double gamma = calc.gamma(spot);
            final double vega = calc.vega(maturity);
            final double theta = calc.theta(spot, maturity);
            final double rho = calc.rho(maturity);
            final double dividendRho = calc.dividendRho(maturity);

            // Finiteness.
            assertTrue(desc + " deltaForward not finite: " + deltaForward,
                    Double.isFinite(deltaForward));
            assertTrue(desc + " delta not finite: " + delta, Double.isFinite(delta));
            assertTrue(desc + " gammaForward not finite", Double.isFinite(gammaForward));
            assertTrue(desc + " gamma not finite", Double.isFinite(gamma));
            assertTrue(desc + " vega not finite", Double.isFinite(vega));
            assertTrue(desc + " theta not finite", Double.isFinite(theta));
            assertTrue(desc + " rho not finite", Double.isFinite(rho));
            assertTrue(desc + " dividendRho not finite", Double.isFinite(dividendRho));

            // Gamma, vega, vanna, volga must all be exactly zero.
            assertEquals(desc + " gammaForward should be 0", 0.0, gammaForward, tolerance);
            assertEquals(desc + " gamma should be 0", 0.0, gamma, tolerance);
            assertEquals(desc + " vega should be 0", 0.0, vega, tolerance);
            assertEquals(desc + " vanna should be 0", 0.0, calc.vanna(maturity), tolerance);
            assertEquals(desc + " volga should be 0", 0.0, calc.volga(maturity), tolerance);

            // Clearly-ITM call ⇒ deltaForward ≈ 1.
            if (strike < forward - 5.0 && type == Option.Type.Call) {
                assertEquals(desc + " ITM call deltaForward should be ~1",
                        1.0, deltaForward, 0.01);
            }
            // Clearly-OTM call ⇒ deltaForward ≈ 0.
            if (strike > forward + 5.0 && type == Option.Type.Call) {
                assertEquals(desc + " OTM call deltaForward should be ~0",
                        0.0, deltaForward, tolerance);
            }

            // Strike sensitivities should be finite.
            assertTrue(desc + " strikeSensitivity not finite",
                    Double.isFinite(calc.strikeSensitivity()));
            assertTrue(desc + " strikeGamma not finite",
                    Double.isFinite(calc.strikeGamma()));

            // ITM probabilities are {0, 0.5, 1} based on moneyness.
            final double itmCashProb = calc.itmCashProbability();
            final double itmAssetProb = calc.itmAssetProbability();
            assertTrue(desc + " itmCashProb not finite", Double.isFinite(itmCashProb));
            assertTrue(desc + " itmAssetProb not finite", Double.isFinite(itmAssetProb));

            final double expectedProb;
            if (type == Option.Type.Call) {
                expectedProb = (forward > strike) ? 1.0
                        : (forward == strike ? 0.5 : 0.0);
            } else {
                expectedProb = (forward < strike) ? 1.0
                        : (forward == strike ? 0.5 : 0.0);
            }
            assertEquals(desc + " itmCashProb incorrect",
                    expectedProb, itmCashProb, tolerance);
        }
    }

    /**
     * Faithful port of {@code testBachelierVsBlackConvergence} (lines 560-586).
     * For small relative vol, Bachelier should approximate Black.
     */
    @Test
    public void testBachelierVsBlackConvergence() {
        QL.info("Testing BachelierCalculator vs BlackCalculator convergence...");

        final double forward = 100.0;
        final double strike = 100.0;
        final double relativeVol = 0.01;
        final double absoluteVol = relativeVol * forward;
        final double discount = 1.0;
        final double tolerance = 1e-2;

        final BachelierCalculator bachelierCalc = new BachelierCalculator(
                Option.Type.Call, strike, forward, absoluteVol, discount);
        final BlackCalculator blackCalc = new BlackCalculator(
                Option.Type.Call, strike, forward, relativeVol, discount);

        final double bachelierValue = bachelierCalc.value();
        final double blackValue = blackCalc.value();

        final double relativeError = Math.abs(bachelierValue - blackValue) / blackValue;
        assertTrue("Bachelier-vs-Black convergence failed: bachelier="
                + bachelierValue + " black=" + blackValue
                + " relErr=" + relativeError,
                relativeError <= tolerance);
    }
}
