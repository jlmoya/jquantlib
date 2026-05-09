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
 Copyright (C) 2022 Skandinaviska Enskilda Banken AB (publ)

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.experimental.volatility;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.experimental.volatility.SviSmileSection;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/svivolatility.cpp (Phase 5g).
 *
 * <p>Direct-named equivalent. The C++ file has a single test
 * {@code testSviSmileSection} which exercises the time- and date-based
 * constructors of {@link SviSmileSection} with five SVI parameters
 * {@code (a, b, sigma, rho, m)}, choosing a strike such that the
 * log-moneyness equals {@code m} so that the variance reduces to
 * {@code a + b * sigma}.
 *
 * <p>Existing {@link SviSmileSectionTest} covers Gatheral-baseline cases
 * with different parameters and a smile-shape sanity check; this class
 * mirrors the C++ test exactly to support the Phase 5 audit.
 */
public class SviVolatilityTest {

    public SviVolatilityTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of {@code svivolatility.cpp BOOST_AUTO_TEST_CASE(testSviSmileSection)}
     * (lines 31-66).
     */
    @Test
    public void testSviSmileSection() {
        QL.info("Testing SviSmileSection construction...");

        final Date today = new Settings().evaluationDate();

        // Time-based constructor.
        final double tte = 11.0 / 365.0;
        final double forward = 123.45;
        final double a = -0.0666;
        final double b = 0.229;
        final double sigma = 0.337;
        final double rho = 0.439;
        final double m = 0.193;
        final double[] sviParameters = {a, b, sigma, rho, m};

        // Strike chosen so log(K/F) == m, then w(m) = a + b*sigma.
        final double strike = forward * Math.exp(m);

        final SviSmileSection timeSection = new SviSmileSection(tte, forward, sviParameters);
        assertEquals("time-section atmLevel", forward, timeSection.atmLevel(), 0.0);
        assertEquals("time-section variance(strike)",
                a + b * sigma, timeSection.variance(strike), 1.0e-10);

        // Date-based constructor.
        final Date date = today.add(new Period(11, TimeUnit.Days));
        final SviSmileSection dateSection = new SviSmileSection(date, forward, sviParameters);
        assertEquals("date-section atmLevel", forward, dateSection.atmLevel(), 0.0);
        assertEquals("date-section variance(strike)",
                a + b * sigma, dateSection.variance(strike), 1.0e-10);
    }
}
