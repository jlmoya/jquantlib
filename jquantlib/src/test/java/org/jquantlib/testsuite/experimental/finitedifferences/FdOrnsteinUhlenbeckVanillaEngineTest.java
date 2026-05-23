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
 */
package org.jquantlib.testsuite.experimental.finitedifferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.experimental.finitedifferences.FdOrnsteinUhlenbeckVanillaEngine;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Smoke test for {@link FdOrnsteinUhlenbeckVanillaEngine}.
 *
 * <p>Cross-validates the FDM call-option price against the closed-form
 * normal-model price for an Ornstein-Uhlenbeck process with risk-free
 * discounting. For an OU process
 * {@code dx = a*(b - x) dt + sigma dW} the terminal distribution of
 * {@code x(T) | x(0)=x0} is Gaussian with
 * mean {@code mu = b + (x0 - b)*exp(-a*T)} and variance
 * {@code var = sigma^2 / (2 a) * (1 - exp(-2 a T))} (a > 0). The
 * undiscounted call expectation is therefore the standard Bachelier-style
 * normal-call formula:
 * {@code E[(x_T - K)+] = (mu - K) * Phi(d) + sd * phi(d)} with
 * {@code d = (mu - K)/sd}. The engine then multiplies by
 * {@code rTS.discount(T)}.
 *
 * <p>Tolerance: loose {@code 5e-3} absolute — sufficient for an
 * {@code xGrid=100, tGrid=50} smoke check; matches the typical FDM
 * convergence margin used for similar engine smoke tests in this suite.</p>
 */
public class FdOrnsteinUhlenbeckVanillaEngineTest {

    @Test
    public void testFdmCallVsNormalAnalytic() {
        final DayCounter dc = new Actual365Fixed();
        final Date today    = new Date(15, Month.January, 2020);
        new Settings().setEvaluationDate(today);

        final Date maturity = today.add(new Period(1, TimeUnit.Years));
        final double T = dc.yearFraction(today, maturity);

        // OU process: dx = a*(b - x) dt + sigma dW
        final double x0    = 0.10;
        final double speed = 1.5;     // mean-reversion 'a'
        final double level = 0.05;    // long-run mean 'b'
        final double sigma = 0.20;    // diffusion
        final OrnsteinUhlenbeckProcess ou = new OrnsteinUhlenbeckProcess(speed, sigma, x0, level);

        final double r = 0.03;
        final YieldTermStructure rTS = new FlatForward(today, r, dc);

        final double strike = 0.10;
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(Option.Type.Call, strike);
        final EuropeanExercise exercise = new EuropeanExercise(maturity);

        // --- FDM ---
        final VanillaOption opt = new VanillaOption(payoff, exercise);
        opt.setPricingEngine(new FdOrnsteinUhlenbeckVanillaEngine(ou, rTS, 50, 100, 0, 1e-4,
                org.jquantlib.methods.finitedifferences.schemes.FdmSchemeDesc.Douglas()));
        final double fdmNpv = opt.NPV();

        // --- Analytic normal expectation ---
        final double mu  = level + (x0 - level) * Math.exp(-speed * T);
        final double var = sigma * sigma / (2.0 * speed) * (1.0 - Math.exp(-2.0 * speed * T));
        final double sd  = Math.sqrt(var);
        final double d   = (mu - strike) / sd;
        final NormalDistribution npdf = new NormalDistribution();
        final CumulativeNormalDistribution ncdf = new CumulativeNormalDistribution();
        final double cnd = ncdf.op(d);
        final double pdfD = npdf.op(d);
        final double expectedUndisc = (mu - strike) * cnd + sd * pdfD;
        final double expected = rTS.discount(T) * expectedUndisc;

        // Loose tolerance — FDM smoke
        final double tol = 5e-3;
        assertEquals("FDM OU call NPV vs normal analytic", expected, fdmNpv, tol);

        // Sanity: greeks populated and finite
        assertTrue("delta finite", Double.isFinite(opt.delta()));
        assertTrue("gamma finite", Double.isFinite(opt.gamma()));
    }
}
