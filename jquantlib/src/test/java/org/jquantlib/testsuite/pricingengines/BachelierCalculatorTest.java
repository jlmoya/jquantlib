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
import org.jquantlib.pricingengines.BlackFormula;
import org.junit.Ignore;
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
 * <p><b>Phase 5g.5 deferral for Greeks:</b> JQuantLib does not have a
 * {@code BachelierCalculator} class. The Bachelier formula is available
 * as the static methods
 * {@link BlackFormula#bachelierBlackFormula(Option.Type, double, double, double, double)}
 * but no calculator class with Greek accessors. Faithful port of the
 * Greek-bearing tests deferred to Phase 5g.5.
 *
 * <p>Tests that only exercise option <em>values</em> via
 * {@code bachelierBlackFormula} are ported below directly.
 */
public class BachelierCalculatorTest {

    public BachelierCalculatorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of {@code testBachelierCalculatorBasicValues} (lines 45-109)
     * via {@link BlackFormula#bachelierBlackFormula} (the static Bachelier
     * formula provides the same numerical answer as
     * {@code BachelierCalculator::value()}).
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

            final double v = BlackFormula.bachelierBlackFormula(
                    type, strike, forward, stdDev, discount);

            assertEquals("BachelierCalculator basic value (" + type + " K=" + strike + ")",
                    refValue, v, tol);

            // Zero volatility ⇒ intrinsic value.
            if (stdDev == 0.0) {
                final double intrinsic = discount * Math.max(0.0,
                        type == Option.Type.Call ? forward - strike : strike - forward);
                assertEquals("BachelierCalculator zero-vol intrinsic (" + type + ")",
                        intrinsic, v, tol);
            }

            // Non-negative.
            assertTrue("BachelierCalculator returned negative value: " + v, v >= -tol);
        }
    }

    /**
     * Faithful port of {@code testBachelierCalculatorPutCallParity} (lines 219-244)
     * via {@link BlackFormula#bachelierBlackFormula}: C - P = discount * (F - K).
     */
    @Test
    public void testBachelierCalculatorPutCallParity() {
        QL.info("Testing BachelierCalculator put-call parity...");

        final double forward = 100.0;
        final double strike = 105.0;
        final double stdDev = 25.0;
        final double discount = 0.95;
        final double tolerance = 1e-10;

        final double call = BlackFormula.bachelierBlackFormula(
                Option.Type.Call, strike, forward, stdDev, discount);
        final double put = BlackFormula.bachelierBlackFormula(
                Option.Type.Put, strike, forward, stdDev, discount);

        // Put-call parity: C - P = discount * (F - K)
        final double lhs = call - put;
        final double rhs = discount * (forward - strike);
        assertEquals("Bachelier put-call parity", rhs, lhs, tolerance);
    }

    @Test
    @Ignore("Phase 5g.5 — Java has no BachelierCalculator class with Greek "
            + "accessors. Bachelier formula is in BlackFormula.bachelierBlackFormula "
            + "but Greek decomposition (delta/gamma/theta/vega/rho/elasticity/"
            + "itmCash/itmAsset/dividendRho/strikeSensitivity/strikeGamma/"
            + "vanna/volga) requires a BachelierCalculator class. "
            + "C++ bacheliercalculator.cpp testBachelierCalculatorGreeks.")
    public void testBachelierCalculatorGreeks() { }

    @Test
    @Ignore("Phase 5g.5 — see testBachelierCalculatorGreeks. "
            + "C++ bacheliercalculator.cpp testBachelierCalculatorEdgeCases.")
    public void testBachelierCalculatorEdgeCases() { }

    @Test
    @Ignore("Phase 5g.5 — see testBachelierCalculatorGreeks. "
            + "C++ bacheliercalculator.cpp testBachelierCalculatorNumericalDerivatives.")
    public void testBachelierCalculatorNumericalDerivatives() { }

    @Test
    @Ignore("Phase 5g.5 — see testBachelierCalculatorGreeks. "
            + "C++ bacheliercalculator.cpp testBachelierCalculatorAgainstAnalyticalFormula.")
    public void testBachelierCalculatorAgainstAnalyticalFormula() { }

    @Test
    @Ignore("Phase 5g.5 — see testBachelierCalculatorGreeks. "
            + "C++ bacheliercalculator.cpp testBachelierCalculatorZeroVolatilityGreeks.")
    public void testBachelierCalculatorZeroVolatilityGreeks() { }

    @Test
    @Ignore("Phase 5g.5 — needs both BachelierCalculator and Black-vs-Bachelier "
            + "comparison loop. C++ bacheliercalculator.cpp testBachelierVsBlackConvergence.")
    public void testBachelierVsBlackConvergence() { }
}
