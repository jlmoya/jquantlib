/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.testsuite.termstructures.volatilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.volatilities.InterpolatedSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.model.VolatilityType;
import org.jquantlib.termstructures.volatilities.equityfx.PiecewiseBlackVarianceSurface;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Java equivalent of {@code testSmileSectionFromBlackVolSurface}, added to C++ QuantLib's test suite in v1.43
 * ({@code test-suite/piecewiseblackvariancesurface.cpp}), covering the smile API that release added to
 * {@code BlackVolTermStructure}.
 * <p>
 * There are two paths and they behave differently on purpose: a vol-native surface has no smile of its own, so the
 * base class wraps its {@code blackVol} in an adapter; a smile-native surface hands back the section it was built
 * from. The second is what makes the API worth having — at a stored tenor nothing is lost to reconstruction.
 *
 * @author Jose Moya
 */
public class SmileSectionFromSurfaceTest {

    private static final Date TODAY = new Date(15, Month.January, 2026);
    private static final double TOLERANCE = 1.0e-12;

    private Date savedEvaluationDate;

    @Before
    public void setUp() {
        savedEvaluationDate = new Settings().evaluationDate();
        new Settings().setEvaluationDate(TODAY);
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvaluationDate);
    }

    /**
     * A {@link BlackConstantVol} has no native smile, so the base-class adapter reads volatilities straight off the
     * surface. Every strike must come back at the flat vol.
     */
    @Test
    public void testAdapterPathOnAVolNativeSurface() {
        QL.info("Testing the default SmileSection adapter on a vol-native surface...");

        final DayCounter dc = new Actual365Fixed();
        final double flatVol = 0.20;
        final BlackConstantVol constVol = new BlackConstantVol(TODAY, new NullCalendar(), flatVol, dc);

        final Date maturity = TODAY.add(new Period(1, TimeUnit.Years));
        final SmileSection smile = constVol.smileSection(maturity);

        for ( final double strike : new double[] { 80.0, 100.0, 120.0 } ) {
            assertEquals("adapter must reproduce the flat vol at strike " + strike, flatVol,
                    smile.volatility(strike), TOLERANCE);
        }
    }

    /**
     * A {@link PiecewiseBlackVarianceSurface} stores its sections, so at a stored tenor it returns the very object it
     * was built from — asserted by identity, which is the only way to tell "handed back" from "reconstructed to
     * within tolerance".
     */
    @Test
    public void testOverridePathOnASmileNativeSurface() {
        QL.info("Testing the SmileSection override on a smile-native surface...");

        final DayCounter dc = new Actual365Fixed();
        final Date d1 = TODAY.add(new Period(6, TimeUnit.Months));
        final Date d2 = TODAY.add(new Period(1, TimeUnit.Years));

        final double[] strikes = { 80.0, 90.0, 100.0, 110.0, 120.0 };
        final double[] vols1 = { 0.30, 0.25, 0.20, 0.22, 0.28 };
        final double[] vols2 = { 0.28, 0.23, 0.19, 0.21, 0.26 };

        final double sqrtT1 = Math.sqrt(dc.yearFraction(TODAY, d1));
        final double sqrtT2 = Math.sqrt(dc.yearFraction(TODAY, d2));

        final double[] stdDevs1 = new double[vols1.length];
        final double[] stdDevs2 = new double[vols2.length];
        for ( int i = 0; i < strikes.length; ++i ) {
            stdDevs1[i] = vols1[i] * sqrtT1;
            stdDevs2[i] = vols2[i] * sqrtT2;
        }

        final SmileSection section1 = new InterpolatedSmileSection(d1, strikes, stdDevs1, 100.0, dc, new Linear(),
                TODAY, VolatilityType.ShiftedLognormal, 0.0, false);
        final SmileSection section2 = new InterpolatedSmileSection(d2, strikes, stdDevs2, 100.0, dc, new Linear(),
                TODAY, VolatilityType.ShiftedLognormal, 0.0, false);

        final PiecewiseBlackVarianceSurface surface = new PiecewiseBlackVarianceSurface(TODAY,
                new Date[] { d1, d2 }, new SmileSection[] { section1, section2 }, dc);

        assertSame("at a stored tenor the surface must return its own section", section1,
                surface.smileSection(d1));
        assertSame("at a stored tenor the surface must return its own section", section2,
                surface.smileSection(d2));

        for ( int i = 0; i < strikes.length; ++i ) {
            assertEquals("stored section must reproduce its own vol at strike " + strikes[i], vols1[i],
                    surface.smileSection(d1).volatility(strikes[i]), TOLERANCE);
        }

        // Between stored tenors there is no section to hand back, so the adapter takes over and reads the surface's
        // interpolated variance. It must still agree with the surface itself.
        final Date between = TODAY.add(new Period(9, TimeUnit.Months));
        final SmileSection interpolated = surface.smileSection(between);
        for ( final double strike : strikes ) {
            assertEquals("adapter must agree with the surface at strike " + strike,
                    surface.blackVol(between, strike, true), interpolated.volatility(strike), TOLERANCE);
        }
    }
}
