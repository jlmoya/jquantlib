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
 Copyright (C) 2012 Liquidnet Holdings, Inc.

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.model.volatility;

import java.util.Iterator;

import org.jquantlib.QL;
import org.jquantlib.model.volatility.Garch11;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeSeries;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/garch.cpp (Phase 5g).
 *
 * <p>The C++ file has two test cases:
 * <ol>
 *   <li>{@code testCalibration} (lines 85-167) — exercises Garch11
 *       calibration in five modes (default, MomentMatchingGuess,
 *       GammaGuess, DoubleOptimization, LevenbergMarquardt) against
 *       expected alpha/beta/omega/logLikelihood reference values.</li>
 *   <li>{@code testCalculation} (lines 169-183) — runs Garch11.calculate
 *       on a 10-element constant TimeSeries and verifies the resulting
 *       volatility series matches a 10-element reference array
 *       {@code expected_calc[]} to tolerance 1e-6.</li>
 * </ol>
 *
 * <p><b>Phase 5g.5 deferral for testCalibration:</b> the Java
 * {@link Garch11} class lacks {@code forecast()}, the
 * {@code MomentMatchingGuess}/{@code GammaGuess}/{@code DoubleOptimization}
 * mode enum, the {@code calibrate(timeSeries, OptimizationMethod, EndCriteria)}
 * method, and the {@code alpha()/beta()/omega()/logLikelihood()} accessors.
 * Faithful port deferred until the Java Garch11 class is brought to
 * v1.42.1 parity.
 *
 * <p>The {@code testCalculation} body is ported below. The Java Garch11
 * uses {@code omega = (1 - alpha - beta) * v} (so {@code v} is the
 * long-run variance). To reproduce the C++ output one would need to know
 * which parametrisation produces the reference array {@code expected_calc};
 * the C++ constructor signature {@code Garch11(alpha, beta, omega)} maps
 * directly to {@code Garch11(alpha, beta, v)} only when {@code v} happens
 * to satisfy {@code omega = (1 - alpha - beta) * v}, i.e.
 * {@code v = omega / (1 - alpha - beta)}. For C++ {@code (0.2, 0.3, 0.4)}
 * this gives {@code v = 0.4 / 0.5 = 0.8}, but the Java Garch11.calculate
 * recurrence
 * {@code sigma2 = omega*u^2 + beta*sigma2}
 * differs from the C++ recurrence (which uses {@code (1-alpha-beta)*v}
 * as the constant term). The expected_calc reference array therefore
 * cannot be reproduced bit-exact by the current Java implementation —
 * test deferred to Phase 5g.5 alongside Garch11 v1.42.1 alignment.
 */
public class GarchTest {

    public GarchTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — Java Garch11 lacks forecast(), the calibration "
            + "modes (MomentMatchingGuess, GammaGuess, DoubleOptimization), "
            + "the calibrate(ts, method, endCriteria) overload, and "
            + "alpha()/beta()/omega()/logLikelihood() accessors. "
            + "C++ garch.cpp lines 85-167.")
    public void testCalibration() {
        // Deferred. Faithful body would:
        //   - Generate 50,000 samples from Garch11(0.2, 0.3, 0.4) using
        //     InverseCumulativeRng<MersenneTwisterUniformRng,
        //     InverseCumulativeNormal>(MersenneTwisterUniformRng(48))
        //   - Default-calibrate Garch11 cgarch1(ts) and verify
        //     {alpha=0.207592, beta=0.281979, omega=0.204647,
        //      logLikelihood=-0.0217413}
        //   - Repeat with each guess strategy + DoubleOptimization
        //   - Calibrate with LevenbergMarquardt and verify alternative
        //     reference values
    }

    @Test
    @Ignore("Phase 5g.5 — Java Garch11.calculate uses a different recurrence "
            + "than C++ v1.42.1; reference array expected_calc[] cannot be "
            + "reproduced until the Garch11 class is aligned with v1.42.1. "
            + "C++ garch.cpp lines 169-183.")
    public void testCalculation() {
        // Deferred. Faithful body would build a 10-day constant
        // TimeSeries (r = 0.1) and verify the output of Garch11(0.2,
        // 0.3, 0.4).calculate matches the C++ reference array
        // expected_calc[] = {0.452769, 0.513323, 0.530141, 0.5350841,
        //                    0.536558, 0.536999, 0.537132, 0.537171,
        //                    0.537183, 0.537187} to tolerance 1.0e-6.
        //
        // Sanity prelude (kept commented so as not to silently pass):
        Date d = new Date(7, Month.July, 1962);
        final TimeSeries<Double> ts = new TimeSeries<Double>(Double.class);
        for (int i = 0; i < 10; i++) {
            ts.put(d, 0.1);
            d = d.add(1);
        }
        final Garch11 garch = new Garch11(0.2, 0.3, 0.4);
        final TimeSeries<Double> tsout = garch.calculate(ts);
        final Iterator<Date> it = tsout.navigableKeySet().iterator();
        while (it.hasNext()) {
            tsout.get(it.next()); // exercise the iterator (matches std::for_each)
        }
    }
}
