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
 Copyright (C) 2013 Gary Kennedy
 Copyright (C) 2015, 2024 Peter Caspers
 Copyright (C) 2017 Klaus Spanderen
 Copyright (C) 2020 Marcin Rybacki

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.pricingengines;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.pricingengines.BlackFormula;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/blackformula.cpp (Phase 5g).
 *
 * <p>The C++ file has 9 test cases:
 * <ol>
 *   <li>{@code testBachelierImpliedVol} — Bachelier exact + Choi inverse.</li>
 *   <li>{@code testChambersImpliedVol} — Chambers-Nawalkha approximation.</li>
 *   <li>{@code testRadoicicStefanicaImpliedVol} — RS approximation.</li>
 *   <li>{@code testRadoicicStefanicaLowerBound} — RS lower-bound theorem.</li>
 *   <li>{@code testImpliedVolAdaptiveSuccessiveOverRelaxation} — Li-RS implied
 *       vol with displacement.</li>
 *   <li>{@code testBlackFormulaForwardDerivative} — analytical d(price)/d(forward).</li>
 *   <li>{@code testBlackFormulaForwardDerivativeWithZeroStrike} — same, K=0.</li>
 *   <li>{@code testBlackFormulaForwardDerivativeWithZeroVolatility} — same, σ=0.</li>
 *   <li>{@code testBachelierBlackFormulaForwardDerivative} +
 *       {@code testBachelierBlackFormulaForwardDerivativeWithZeroVolatility}.</li>
 * </ol>
 *
 * <p><b>Phase 5g.5 deferral:</b> Java {@link BlackFormula} is missing
 * {@code bachelierBlackFormulaImpliedVolChoi},
 * {@code bachelierBlackFormulaImpliedVol},
 * {@code blackFormulaImpliedStdDevApproximationRS},
 * {@code blackFormulaImpliedStdDevChambers},
 * {@code blackFormulaForwardDerivative},
 * {@code bachelierBlackFormulaForwardDerivative}. Faithful ports of those
 * tests deferred to Phase 5g.5; only
 * {@code testImpliedVolAdaptiveSuccessiveOverRelaxation} is portable today.
 */
public class BlackFormulaTest {

    public BlackFormulaTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of {@code testImpliedVolAdaptiveSuccessiveOverRelaxation}
     * (lines 208-264). Uses the existing
     * {@link BlackFormula#blackFormulaImpliedStdDevLiRS} (Li-RS adaptive
     * SOR) inverted against {@code blackFormula} prices.
     *
     * <p>Date arithmetic is replaced by an explicit
     * {@code exerciseTime = 15/12 = 1.25} (Actual/365 fraction for 15 months
     * across [12-Jul-2017, 12-Oct-2018] — the precise discount factor
     * {@code df = exp(-0.10 * 1.25) = 0.8824969...}; see comment below).
     * The C++ test computes {@code df} via an Actual365Fixed yield curve;
     * we hard-code the equivalent value to keep the test self-contained.
     */
    @Test
    public void testImpliedVolAdaptiveSuccessiveOverRelaxation() {
        QL.info("Testing implied volatility calculation via "
                + "adaptive successive over-relaxation...");

        // 15 months from 12-Jul-2017 to 12-Oct-2018 = 458 days / 365 = 1.2547945...
        // Match C++ Actual365Fixed yield-fraction precisely.
        final double exerciseTime = 458.0 / 365.0;

        // Flat-rate yield: r=0.10, q=0.06; df = exp(-r*T)
        final double df = Math.exp(-0.10 * exerciseTime);
        final double dq = Math.exp(-0.06 * exerciseTime);

        final double vol = 0.20;
        final double stdDev = vol * Math.sqrt(exerciseTime);

        final double s0 = 100.0;
        final double forward = s0 * dq / df;

        final Option.Type[] types = { Option.Type.Call, Option.Type.Put };
        final double[] strikes = { 50, 60, 70, 80, 90, 100, 110, 125, 150, 200 };
        final double[] displacements = { 0, 25, 50, 100 };

        final double tol = 1.0e-8;

        for (final double strike : strikes) {
            for (final Option.Type type : types) {
                final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);
                for (final double displacement : displacements) {

                    final double marketValue = BlackFormula.blackFormula(
                            payoff, strike, forward, stdDev, df, displacement);

                    final double impliedStdDev = BlackFormula.blackFormulaImpliedStdDevLiRS(
                            type, strike, forward, marketValue, df, displacement,
                            Double.NaN, 1.0, tol, 100);

                    final double error = Math.abs(impliedStdDev - stdDev);
                    assertEquals(
                            "Li-RS implied stdDev mismatch at type=" + type
                            + " strike=" + strike + " displacement=" + displacement,
                            stdDev, impliedStdDev, 10.0 * tol);
                    QL.ensure(error <= 10.0 * tol,
                            "implied stdDev error too large: " + error);
                }
            }
        }
    }

    @Test
    @Ignore("Phase 5g.5 — Java BlackFormula missing bachelierBlackFormulaImpliedVolChoi "
            + "and bachelierBlackFormulaImpliedVol. C++ blackformula.cpp testBachelierImpliedVol.")
    public void testBachelierImpliedVol() { }

    @Test
    @Ignore("Phase 5g.5 — Java BlackFormula missing blackFormulaImpliedStdDevChambers. "
            + "C++ blackformula.cpp testChambersImpliedVol.")
    public void testChambersImpliedVol() { }

    @Test
    @Ignore("Phase 5g.5 — Java BlackFormula missing blackFormulaImpliedStdDevApproximationRS. "
            + "C++ blackformula.cpp testRadoicicStefanicaImpliedVol.")
    public void testRadoicicStefanicaImpliedVol() { }

    @Test
    @Ignore("Phase 5g.5 — see testRadoicicStefanicaImpliedVol. "
            + "C++ blackformula.cpp testRadoicicStefanicaLowerBound.")
    public void testRadoicicStefanicaLowerBound() { }

    @Test
    @Ignore("Phase 5g.5 — Java BlackFormula missing blackFormulaForwardDerivative "
            + "(d(price)/d(forward) analytical Greek).")
    public void testBlackFormulaForwardDerivative() { }

    @Test
    @Ignore("Phase 5g.5 — see testBlackFormulaForwardDerivative.")
    public void testBlackFormulaForwardDerivativeWithZeroStrike() { }

    @Test
    @Ignore("Phase 5g.5 — see testBlackFormulaForwardDerivative.")
    public void testBlackFormulaForwardDerivativeWithZeroVolatility() { }

    @Test
    @Ignore("Phase 5g.5 — Java BlackFormula missing bachelierBlackFormulaForwardDerivative.")
    public void testBachelierBlackFormulaForwardDerivative() { }

    @Test
    @Ignore("Phase 5g.5 — see testBachelierBlackFormulaForwardDerivative.")
    public void testBachelierBlackFormulaForwardDerivativeWithZeroVolatility() { }
}
