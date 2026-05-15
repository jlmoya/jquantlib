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
import org.jquantlib.pricingengines.BlackCalculator;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/blackcalculator.cpp (Phase 5g).
 *
 * <p>The C++ file has six test cases covering basic values, Greeks,
 * put-call parity, edge cases, numerical-derivative consistency, and
 * zero-volatility Greek behavior. All six are body-filled here against
 * the same C++ reference values.
 *
 * <p>Phase 5e.5b-CFC-d-19 (this commit): {@link BlackCalculator} now has
 * the missing {@code strikeGamma}, {@code vanna}, {@code volga} accessors
 * and finite-Greek behaviour at zero volatility, so the Greeks-Full and
 * ZeroVolatilityGreeks tests are unblocked.
 */
public class BlackCalculatorTest {

    public BlackCalculatorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of {@code testBlackCalculatorBasicValues} (lines 43-106).
     * Reference values come from C++ v1.42.1.
     */
    @Test
    public void testBlackCalculatorBasicValues() {
        QL.info("Testing BlackCalculator basic option values...");

        // {type, strike, forward, stdDev, discount, tolerance, refValue}
        final double[][] data = {
            { 0, 100.0, 100.0, 0.20, 1.0, 1e-8, 7.9655674554058038 }, // ATM Call
            { 1, 100.0, 100.0, 0.20, 1.0, 1e-8, 7.9655674554058038 }, // ATM Put
            { 0,  90.0, 100.0, 0.20, 1.0, 1e-8, 13.589108116054803 }, // ITM Call
            { 1, 110.0, 100.0, 0.20, 1.0, 1e-8, 14.292010941409899 }, // ITM Put
            { 0, 110.0, 100.0, 0.20, 1.0, 1e-8, 4.2920109414098846 }, // OTM Call
            { 1,  90.0, 100.0, 0.20, 1.0, 1e-8, 3.5891081160548062 }, // OTM Put
            { 0, 100.0, 100.0, 0.0,  1.0, 1e-8, 0.0 },                // Zero vol Call
            { 1, 100.0, 100.0, 0.0,  1.0, 1e-8, 0.0 },                // Zero vol Put
        };
        for (final double[] row : data) {
            final Option.Type type = (row[0] == 0) ? Option.Type.Call : Option.Type.Put;
            final double strike   = row[1];
            final double forward  = row[2];
            final double stdDev   = row[3];
            final double discount = row[4];
            final double tol      = row[5];
            final double refValue = row[6];

            // Java BlackCalculator only has the payoff-based constructor.
            final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
            final BlackCalculator calc = new BlackCalculator(payoff, forward, stdDev, discount);
            final double v = calc.value();

            assertEquals("BlackCalculator basic value (" + type + " K=" + strike + ")",
                    refValue, v, tol);

            // Zero volatility ⇒ intrinsic value (discounted).
            if (stdDev == 0.0) {
                final double intrinsic = discount * Math.max(0.0,
                        type == Option.Type.Call ? forward - strike : strike - forward);
                assertEquals("BlackCalculator zero-vol intrinsic (" + type + ")",
                        intrinsic, v, tol);
            }

            // Non-negative.
            assertTrue("BlackCalculator returned negative value: " + v, v >= -tol);
        }
    }

    /**
     * Faithful port of {@code testBlackCalculatorPutCallParity} (lines 219-244):
     * C - P = discount * (F - K).
     */
    @Test
    public void testBlackCalculatorPutCallParity() {
        QL.info("Testing BlackCalculator put-call parity...");

        final double forward = 100.0;
        final double strike = 105.0;
        final double stdDev = 0.25;
        final double discount = 0.95;
        final double tolerance = 1e-10;

        final BlackCalculator callCalc = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, strike), forward, stdDev, discount);
        final BlackCalculator putCalc = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Put, strike), forward, stdDev, discount);

        final double call = callCalc.value();
        final double put = putCalc.value();

        final double lhs = call - put;
        final double rhs = discount * (forward - strike);
        assertEquals("Black put-call parity", rhs, lhs, tolerance);
    }

    /**
     * Faithful port of {@code testBlackCalculatorEdgeCases} (lines 246-290).
     */
    @Test
    public void testBlackCalculatorEdgeCases() {
        QL.info("Testing BlackCalculator edge cases...");

        final double tolerance = 1e-10;

        // Zero volatility.
        {
            final BlackCalculator calc = new BlackCalculator(
                    new PlainVanillaPayoff(Option.Type.Call, 100.0), 100.0, 0.0, 1.0);
            assertEquals("zero-vol ATM call value", 0.0, calc.value(), tolerance);
        }

        // Very high volatility.
        {
            final BlackCalculator calc = new BlackCalculator(
                    new PlainVanillaPayoff(Option.Type.Call, 100.0), 100.0, 2.0, 1.0);
            assertEquals("high-vol ATM call value",
                    68.268949213708595, calc.value(), tolerance);
        }

        // Deep ITM call should be at least intrinsic.
        {
            final BlackCalculator calc = new BlackCalculator(
                    new PlainVanillaPayoff(Option.Type.Call, 50.0), 100.0, 0.20, 1.0);
            final double intrinsic = 100.0 - 50.0;
            assertTrue("deep ITM call below intrinsic: " + calc.value(),
                    calc.value() >= intrinsic - tolerance);
        }

        // Deep OTM call should be small positive.
        {
            final BlackCalculator calc = new BlackCalculator(
                    new PlainVanillaPayoff(Option.Type.Call, 150.0), 100.0, 0.20, 1.0);
            final double v = calc.value();
            assertTrue("deep OTM call out of bounds: " + v, v >= 0.0 && v <= 10.0);
        }
    }

    /**
     * Faithful port of {@code testBlackCalculatorNumericalDerivatives}
     * (lines 292-356) — verifies analytical delta, gamma, vanna and volga
     * against finite-difference approximations.
     */
    @Test
    public void testBlackCalculatorNumericalDerivatives() {
        QL.info("Testing BlackCalculator numerical derivative consistency...");

        final double forward = 100.0;
        final double strike = 100.0;
        final double stdDev = 0.20;
        final double discount = 0.95;
        final double maturity = 1.0;
        final double bump = 1.0e-4;
        final double tolerance = 1.0e-3;

        final BlackCalculator calc = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, strike), forward, stdDev, discount);

        // FD delta check.
        final BlackCalculator calcUp = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, strike), forward + bump, stdDev, discount);
        final BlackCalculator calcDown = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, strike), forward - bump, stdDev, discount);

        final double analyticalDelta = calc.deltaForward();
        final double numericalDelta = (calcUp.value() - calcDown.value()) / (2.0 * bump);
        assertEquals("FD delta vs deltaForward",
                analyticalDelta, numericalDelta, tolerance);

        // FD gamma check.
        final double analyticalGamma = calc.gammaForward();
        final double numericalGamma =
                (calcUp.deltaForward() - calcDown.deltaForward()) / (2.0 * bump);
        assertEquals("FD gamma vs gammaForward",
                analyticalGamma, numericalGamma, tolerance);

        // FD vanna check (C++ blackcalculator.cpp:332-343): dVega/dForward.
        // Note: C++ test calls calc.vanna(forward, maturity), i.e. spot==forward.
        final double analyticalVanna = calc.vanna(forward, maturity);
        final double numericalVanna =
                (calcUp.vega(maturity) - calcDown.vega(maturity)) / (2.0 * bump);
        assertEquals("FD vanna vs vanna(forward,maturity)",
                analyticalVanna, numericalVanna, tolerance);

        // FD volga check (C++ blackcalculator.cpp:345-355): dVega/dVol.
        final BlackCalculator calcVolUp = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, strike), forward, stdDev + bump, discount);
        final BlackCalculator calcVolDown = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, strike), forward, stdDev - bump, discount);
        final double analyticalVolga = calc.volga(maturity);
        final double numericalVolga =
                (calcVolUp.vega(maturity) - calcVolDown.vega(maturity)) / (2.0 * bump);
        assertEquals("FD volga vs volga(maturity)",
                analyticalVolga, numericalVolga, tolerance);
    }

    /**
     * Faithful port of {@code testBlackCalculatorZeroVolatilityGreeks}
     * (lines 358-481). Verifies finite-Greeks behavior at stdDev=0 across
     * ITM/ATM/OTM scenarios.
     *
     * <p>Un-ignored 2026-05-15 (Phase 5e.5b-CFC-d-19) once the Java
     * {@link BlackCalculator} was ported to the v1.42.1 zero-vol code
     * paths.
     */
    @Test
    public void testBlackCalculatorZeroVolatilityGreeks() {
        QL.info("Testing BlackCalculator Greeks with zero volatility...");

        final double tolerance = 1e-10;
        final double forward = 100.0;
        final double discount = 1.0;
        final double spot = 98.0;
        final double maturity = 1.0;
        final double stdDev = 0.0;

        // {type, strike, descr}
        final Object[][] cases = {
            { Option.Type.Call,  90.0, "ITM Call" },
            { Option.Type.Put,  110.0, "ITM Put" },
            { Option.Type.Call, 100.0, "ATM Call" },
            { Option.Type.Put,  100.0, "ATM Put" },
            { Option.Type.Call, 110.0, "OTM Call" },
            { Option.Type.Put,   90.0, "OTM Put" },
        };
        for (final Object[] row : cases) {
            final Option.Type type = (Option.Type) row[0];
            final double strike = (Double) row[1];
            final String descr = (String) row[2];
            final BlackCalculator calc = new BlackCalculator(
                    new PlainVanillaPayoff(type, strike), forward, stdDev, discount);

            final double deltaFwd = calc.deltaForward();
            final double delta = calc.delta(spot);
            final double gammaFwd = calc.gammaForward();
            final double gamma = calc.gamma(spot);
            final double vega = calc.vega(maturity);
            final double theta = calc.theta(spot, maturity);
            final double rho = calc.rho(maturity);
            final double divRho = calc.dividendRho(maturity);

            // Finite check.
            assertTrue(descr + ": non-finite Greeks",
                    Double.isFinite(deltaFwd) && Double.isFinite(delta)
                    && Double.isFinite(gammaFwd) && Double.isFinite(gamma)
                    && Double.isFinite(vega) && Double.isFinite(theta)
                    && Double.isFinite(rho) && Double.isFinite(divRho));

            // Gamma should be zero.
            assertTrue(descr + ": gammaForward should be zero",
                    Math.abs(gammaFwd) <= tolerance);
            assertTrue(descr + ": gamma should be zero",
                    Math.abs(gamma) <= tolerance);

            // Vega should be zero.
            assertTrue(descr + ": vega should be zero",
                    Math.abs(vega) <= tolerance);

            // Strike sensitivities finite (C++ lines 440-446).
            assertTrue(descr + ": strikeSensitivity should be finite",
                    Double.isFinite(calc.strikeSensitivity()));
            assertTrue(descr + ": strikeGamma should be finite",
                    Double.isFinite(calc.strikeGamma()));

            // Vanna and volga should be zero (C++ lines 448-460).
            final double vanna = calc.vanna(spot, maturity);
            final double volga = calc.volga(maturity);
            assertTrue(descr + ": vanna should be zero",
                    Math.abs(vanna) <= tolerance);
            assertTrue(descr + ": volga should be zero",
                    Math.abs(volga) <= tolerance);

            // ITM call delta ≈ 1.
            if (type == Option.Type.Call && strike < forward * 0.95) {
                assertTrue(descr + ": ITM call deltaForward should be ~1.0 ("
                        + deltaFwd + ")", deltaFwd >= 0.99 && deltaFwd <= 1.01);
            }
            // OTM call delta ≈ 0.
            if (type == Option.Type.Call && strike > forward * 1.05) {
                assertTrue(descr + ": OTM call deltaForward should be ~0.0 ("
                        + deltaFwd + ")", Math.abs(deltaFwd) <= tolerance);
            }
        }

        // Very small but non-zero vol still works.
        final double smallVol = 1.0e-12;
        final BlackCalculator calc = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, 100.0), forward, smallVol, discount);
        assertTrue("small vol Greeks finite",
                Double.isFinite(calc.deltaForward())
                && Double.isFinite(calc.gammaForward())
                && Double.isFinite(calc.vega(maturity)));
        assertTrue("small vol ATM delta unreasonable",
                Math.abs(calc.deltaForward() - discount * 0.5) <= 0.1);
    }

    /**
     * Faithful port of the full {@code testBlackCalculatorGreeks} (C++
     * v1.42.1 lines 108-217). Covers every Greek including the newly
     * ported {@code strikeGamma}, {@code vanna}, {@code volga}.
     *
     * <p>Un-ignored 2026-05-15 (Phase 5e.5b-CFC-d-19) once the missing
     * accessors were ported.
     */
    @Test
    public void testBlackCalculatorGreeksFull() {
        QL.info("Testing BlackCalculator Greeks (full)...");

        final double forward = 100.0;
        final double strike = 105.0;
        final double stdDev = 0.20;
        final double discount = 0.95;
        final double spot = 98.0;
        final double maturity = 1.0;
        final double tolerance = 1e-6;

        // C++ reference values from blackcalculator.cpp lines 118-133.
        final double refDelta = 0.42921547913932068;
        final double refDeltaFwd = 0.42063116955653351;
        final double refGamma = 0.019527733248736884;
        final double refGammaFwd = 0.018754435012086908;
        final double refTheta = -4.31290436588883;
        final double refVega = 37.508870024173795;
        final double refRho = 36.452803157675653;
        final double refElasticity = 7.4974624362037323;
        final double refElasticityFwd = 7.4974624362037199;
        final double refItmCashProb = 0.36544163566592136;
        final double refItmAssetProb = 0.44276965216477238;
        final double refDividendRho = -42.063116955653371;
        final double refStrikeSensitivity = -0.34716955388262527;
        final double refStrikeGamma = 0.017010825407788574;
        final double refVanna = 0.65822482825836837;
        final double refVolga = 9.2856964243177753;

        final BlackCalculator calc = new BlackCalculator(
                new PlainVanillaPayoff(Option.Type.Call, strike), forward, stdDev, discount);

        assertEquals("call deltaForward",       refDeltaFwd,         calc.deltaForward(),         tolerance);
        assertEquals("call delta",              refDelta,            calc.delta(spot),            tolerance);
        assertEquals("call gammaForward",       refGammaFwd,         calc.gammaForward(),         tolerance);
        assertEquals("call gamma",              refGamma,            calc.gamma(spot),            tolerance);
        assertEquals("call theta",              refTheta,            calc.theta(spot, maturity),  tolerance);
        assertEquals("call vega",               refVega,             calc.vega(maturity),         tolerance);
        assertEquals("call rho",                refRho,              calc.rho(maturity),          tolerance);
        assertEquals("call elasticityForward",  refElasticityFwd,    calc.elasticityForward(),    tolerance);
        assertEquals("call elasticity",         refElasticity,       calc.elasticity(spot),       tolerance);
        assertEquals("call itmCashProb",        refItmCashProb,      calc.itmCashProbability(),   tolerance);
        assertEquals("call itmAssetProb",       refItmAssetProb,     calc.itmAssetProbability(),  tolerance);
        assertEquals("call dividendRho",        refDividendRho,      calc.dividendRho(maturity),  tolerance);
        assertEquals("call strikeSensitivity",  refStrikeSensitivity, calc.strikeSensitivity(),   tolerance);
        assertEquals("call strikeGamma",        refStrikeGamma,      calc.strikeGamma(),          tolerance);
        // C++ uses spot=spot for vanna; matches blackcalculator.cpp:151.
        assertEquals("call vanna",              refVanna,            calc.vanna(spot, maturity),  tolerance);
        assertEquals("call volga",              refVolga,            calc.volga(maturity),        tolerance);
    }
}
