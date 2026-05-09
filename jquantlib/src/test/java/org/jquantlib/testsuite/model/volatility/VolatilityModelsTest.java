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
 Copyright (C) 2006 Joseph Wang

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.model.volatility;

import static org.junit.Assert.assertNotNull;

import org.jquantlib.QL;
import org.jquantlib.model.volatility.ConstantEstimator;
import org.jquantlib.model.volatility.SimpleLocalEstimator;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeSeries;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/volatilitymodels.cpp (Phase 5g).
 *
 * <p>The C++ file contains a single {@code testConstruction} test that builds
 * a {@link TimeSeries} of three dated values, runs them through a
 * {@link SimpleLocalEstimator} and a {@link ConstantEstimator}, and exercises
 * the resulting series' iterator. The body is faithfully ported; the iterator
 * exercise becomes a non-null check on the returned {@link TimeSeries}.
 *
 * <p>The C++ test references {@code GarmanKlass} via include but does not
 * exercise it. JQuantLib has the full GarmanKlass family
 * ({@code org.jquantlib.model.volatility.GarmanKlassSigma1..6},
 * {@code GarmanKlassOpenClose}, {@code GarmanKlassSimpleSigma}); the include
 * is therefore omitted as it is unused by the C++ test body.
 *
 * <p>Note: an existing {@link EstimatorsTest} in this package already covers
 * the same construction with five dates; this class is the direct C++-named
 * equivalent (three-date variant) for Phase 5 audit completeness.
 */
public class VolatilityModelsTest {

    public VolatilityModelsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of {@code volatilitymodels.cpp BOOST_AUTO_TEST_CASE(testConstruction)}
     * (lines 35-50).
     */
    @Test
    public void testConstruction() {
        QL.info("Testing volatility model construction...");

        final TimeSeries<Double> ts = new TimeSeries<Double>(Double.class);
        ts.put(new Date(25, Month.March, 2005), 1.2);
        ts.put(new Date(29, Month.March, 2005), 2.3);
        ts.put(new Date(15, Month.March, 2005), 0.3);

        final SimpleLocalEstimator sle = new SimpleLocalEstimator(1.0 / 360.0);
        final TimeSeries<Double> locale = sle.calculate(ts);
        assertNotNull("SimpleLocalEstimator returned null TimeSeries", locale);

        final ConstantEstimator ce = new ConstantEstimator(1);
        final TimeSeries<Double> sv = ce.calculate(locale);
        assertNotNull("ConstantEstimator returned null TimeSeries", sv);

        // C++ test ends with `sv.begin();` — exercising the iterator merely
        // ensures iteration does not throw. Java equivalent: navigableKeySet()
        // produces an iterator backed by the underlying TreeMap.
        assertNotNull("ConstantEstimator output exposes no key set",
                sv.navigableKeySet());
    }

    /**
     * Phase 5g.5 deferral: GjrGarchModel testing requires the full Java
     * production class {@code GjrGarchModel}, which is not present in the
     * current JQuantLib codebase (only {@code Garch11} is). The C++
     * test-suite file {@code gjrgarchmodel.cpp} cannot be ported until
     * the prerequisite Java class is implemented.
     */
    @Test
    @Ignore("Phase 5g.5 — GjrGarchModel Java production class not present; "
            + "see migration-harness deferral notes.")
    public void testGjrGarchModelDeferred() {
        // Placeholder for Phase 5g.5 follow-up.
    }
}
