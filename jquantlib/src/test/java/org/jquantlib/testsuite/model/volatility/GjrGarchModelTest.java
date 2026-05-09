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
 Copyright (C) 2008 Yee Man Chan

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.model.volatility;

import org.jquantlib.QL;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/gjrgarchmodel.cpp (Phase 5g).
 *
 * <p>The C++ file has two test cases:
 * <ol>
 *   <li>{@code testEngines} — compares Monte Carlo GJR-GARCH engine to
 *       analytic GJR-GARCH engine across a 3 × 2 × 6 grid of (Lambda,
 *       maturity, strike) for a 50/strike European option.</li>
 *   <li>{@code testDAXCalibration} — calibrates a GJR-GARCH model to
 *       DAX option quotes via HestonModelHelper.</li>
 * </ol>
 *
 * <p><b>Phase 5g.5 deferral:</b> JQuantLib has neither a {@code GjrGarchModel}
 * production class, a {@code GjrGarchProcess}, an {@code AnalyticGjrGarchEngine},
 * a {@code MCEuropeanGjrGarchEngine}, nor the {@code HestonModelHelper}
 * calibration helper. Faithful port deferred until the Java GJR-GARCH
 * subsystem is implemented (cross-referenced with Phase 5h Heston cluster
 * design concern D8).
 */
public class GjrGarchModelTest {

    public GjrGarchModelTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    @Ignore("Phase 5g.5 — GjrGarchModel/Process and analytic+MC engines "
            + "not present in JQuantLib. C++ gjrgarchmodel.cpp testEngines.")
    public void testEngines() {
        // Deferred. Faithful body would compare MCEuropeanGjrGarchEngine
        // and AnalyticGjrGarchEngine for European calls priced under the
        // GJR-GARCH model with parameters s0=50, omega=2e-6, alpha=0.024,
        // beta=0.93, gamma=0.059, daysPerYear=365, across maturities
        // {90, 180}, strikes {35,40,45,50,55,60}, and Lambdas {0,0.1,0.2}.
    }

    @Test
    @Ignore("Phase 5g.5 — HestonModelHelper / GjrGarchModel calibration not "
            + "present in JQuantLib. C++ gjrgarchmodel.cpp testDAXCalibration.")
    public void testDAXCalibration() {
        // Deferred.
    }
}
