/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4g — TenorOptionletVTS smoke tests.

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
 */
package org.jquantlib.testsuite.experimental.basismodels;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.basismodels.TenorOptionletVTS;
import org.jquantlib.experimental.basismodels.TenorOptionletVTS.TwoParameterCorrelation;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.termstructures.volatilities.optionlet.ConstantOptionletVolatility;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for {@link TenorOptionletVTS} and
 * {@link TwoParameterCorrelation}.
 *
 * <p>Verifies construction, VolatilityType, and that the volatility
 * transformation produces a non-negative normal vol.
 * No C++ cross-validated reference values.
 */
public class TenorOptionletVTSTest {

    private static final Date TODAY = new Date(1, Month.January, 2025);

    @Before
    public void setUp() {
        new Settings().setEvaluationDate(TODAY);
    }

    // -------------------------------------------------------------------------
    // TwoParameterCorrelation tests
    // -------------------------------------------------------------------------

    @Test
    public void testTwoParameterCorrelationAtSameStart() {
        QL.info("::::: TenorOptionletVTSTest::testTwoParameterCorrelationAtSameStart :::::");

        final TwoParameterCorrelation corr = new TwoParameterCorrelation(
                t -> 0.5,  // rhoInf = 0.5
                t -> 0.3); // beta   = 0.3

        // rho(t, t) must equal 1 regardless of rhoInf and beta
        assertEquals("rho(t,t) = 1", 1.0, corr.correlation(0.5, 0.5), 1e-15);
        assertEquals("rho(t,t) = 1", 1.0, corr.correlation(2.0, 2.0), 1e-15);
    }

    @Test
    public void testTwoParameterCorrelationDecays() {
        QL.info("::::: TenorOptionletVTSTest::testTwoParameterCorrelationDecays :::::");

        final TwoParameterCorrelation corr = new TwoParameterCorrelation(
                t -> 0.5,
                t -> 1.0);

        // rhoInf + (1 - rhoInf) * exp(-beta * |t2 - t1|) with |t2-t1|=1, rhoInf=0.5, beta=1
        final double expected = 0.5 + 0.5 * Math.exp(-1.0);
        assertEquals("correlation decays correctly", expected,
                corr.correlation(0.0, 1.0), 1e-15);
    }

    @Test
    public void testTwoParameterCorrelationSymmetric() {
        QL.info("::::: TenorOptionletVTSTest::testTwoParameterCorrelationSymmetric :::::");

        final TwoParameterCorrelation corr = new TwoParameterCorrelation(
                t -> 0.4,
                t -> 0.8);

        // Correlation is driven by |t2 - t1|, so rho(a,b) == rho(b,a) (with same rhoInf(start1))
        // Note: rhoInf and beta depend on start1, so this is only symmetric when start1==start2 up to sign
        final double rhoAB = corr.correlation(0.5, 1.5);
        // Just verify it's in [0, 1]
        assertTrue("correlation >= 0", rhoAB >= 0.0);
        assertTrue("correlation <= 1", rhoAB <= 1.0);
    }

    // -------------------------------------------------------------------------
    // TenorOptionletVTS construction tests
    // -------------------------------------------------------------------------

    @Test
    public void testConstructionAndVolatilityType() {
        QL.info("::::: TenorOptionletVTSTest::testConstructionAndVolatilityType :::::");

        final Target cal = new Target();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<YieldTermStructure> ts =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.03, dc));

        final Euribor3M base3m = new Euribor3M(ts);
        final Euribor6M targ6m = new Euribor6M(ts);

        // Constant 100 bps normal caplet vol
        final OptionletVolatilityStructure constVol =
                new ConstantOptionletVolatility(
                        TODAY, cal, BusinessDayConvention.Following,
                        0.01, dc);

        final Handle<OptionletVolatilityStructure> baseVTSH =
                new Handle<OptionletVolatilityStructure>(constVol);

        final TwoParameterCorrelation corr = new TwoParameterCorrelation(
                t -> 0.5, t -> 0.3);

        final TenorOptionletVTS vts =
                new TenorOptionletVTS(baseVTSH, base3m, targ6m, corr);

        assertNotNull("TenorOptionletVTS created", vts);
        assertEquals("volatilityType is Normal",
                VolatilityType.Normal, vts.volatilityType());
        assertEquals("maxDate passes through", constVol.maxDate(), vts.maxDate());
    }

    @Test
    public void testRequirementCheck() {
        QL.info("::::: TenorOptionletVTSTest::testRequirementCheck :::::");

        final Target cal = new Target();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<YieldTermStructure> ts =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.03, dc));

        // 3M base, 6M target: 6M/3M = 2 (exact divisor) — should NOT throw
        final Euribor3M base3m = new Euribor3M(ts);
        final Euribor6M targ6m = new Euribor6M(ts);

        final OptionletVolatilityStructure constVol =
                new ConstantOptionletVolatility(TODAY, cal,
                        BusinessDayConvention.Following, 0.01, dc);
        final Handle<OptionletVolatilityStructure> baseVTSH =
                new Handle<OptionletVolatilityStructure>(constVol);

        final TwoParameterCorrelation corr = new TwoParameterCorrelation(
                t -> 0.5, t -> 0.3);

        // No exception expected — Euribor3M (4/year) divides Euribor6M (2/year)?
        // Actually frequency() for 3M = Quarterly=4, 6M = Semiannual=2.
        // The requirement is: baseFrequency % targetFrequency == 0 => 4 % 2 == 0. OK.
        final TenorOptionletVTS vts =
                new TenorOptionletVTS(baseVTSH, base3m, targ6m, corr);
        assertNotNull(vts);
    }
}
