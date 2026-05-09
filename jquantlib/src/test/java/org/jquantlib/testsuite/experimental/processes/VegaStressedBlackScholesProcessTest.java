/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4n — VegaStressedBlackScholesProcess smoke tests.

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
package org.jquantlib.testsuite.experimental.processes;

import org.jquantlib.QL;
import org.jquantlib.experimental.processes.VegaStressedBlackScholesProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.daycounters.Actual365Fixed;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Smoke tests for {@link VegaStressedBlackScholesProcess}.
 *
 * <p>Verifies that the diffusion is shifted by {@code stressLevel} when both
 * time {@code t} and asset {@code x} fall in the stress region; outside the
 * region, the diffusion equals the underlying GBM diffusion.
 */
public class VegaStressedBlackScholesProcessTest {

    private static final double TIGHT = 1e-12;

    public VegaStressedBlackScholesProcessTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void diffusionShiftedInsideStressRegion() {
        final Date today = Date.todaysDate();
        final Calendar cal = new NullCalendar();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<SimpleQuote> spot = new Handle<>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> r = new Handle<>(
                new FlatForward(today, 0.04, dc));
        final Handle<YieldTermStructure> q = new Handle<>(
                new FlatForward(today, 0.02, dc));
        final Handle<BlackVolTermStructure> vol = new Handle<>(
                new BlackConstantVol(today, cal, 0.20, dc));

        // Stress region [0,2]x[50,150], stress = 0.05
        final VegaStressedBlackScholesProcess vs = new VegaStressedBlackScholesProcess(
                spot, q, r, vol,
                0.0, 2.0, 50.0, 150.0, 0.05);

        // Inside region: diffusion = 0.20 + 0.05 = 0.25
        assertEquals(0.25, vs.diffusion(1.0, 100.0), TIGHT);

        // Outside region (time): diffusion = 0.20
        assertEquals(0.20, vs.diffusion(3.0, 100.0), TIGHT);

        // Outside region (asset): diffusion = 0.20
        assertEquals(0.20, vs.diffusion(1.0, 200.0), TIGHT);

        // Asset below lower border
        assertEquals(0.20, vs.diffusion(1.0, 30.0), TIGHT);
    }

    @Test
    public void accessorsAndSetters() {
        final Date today = Date.todaysDate();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<SimpleQuote> spot = new Handle<>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> r = new Handle<>(
                new FlatForward(today, 0.04, dc));
        final Handle<YieldTermStructure> q = new Handle<>(
                new FlatForward(today, 0.02, dc));
        final Handle<BlackVolTermStructure> vol = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(), 0.20, dc));

        final VegaStressedBlackScholesProcess vs = new VegaStressedBlackScholesProcess(
                spot, q, r, vol);
        // Defaults
        assertEquals(0.0, vs.getLowerTimeBorderForStressTest(), TIGHT);
        assertEquals(1000000.0, vs.getUpperTimeBorderForStressTest(), TIGHT);
        assertEquals(0.0, vs.getLowerAssetBorderForStressTest(), TIGHT);
        assertEquals(1000000.0, vs.getUpperAssetBorderForStressTest(), TIGHT);
        assertEquals(0.0, vs.getStressLevel(), TIGHT);

        vs.setLowerTimeBorderForStressTest(0.5);
        vs.setUpperTimeBorderForStressTest(1.5);
        vs.setLowerAssetBorderForStressTest(80.0);
        vs.setUpperAssetBorderForStressTest(120.0);
        vs.setStressLevel(0.03);

        assertEquals(0.5, vs.getLowerTimeBorderForStressTest(), TIGHT);
        assertEquals(1.5, vs.getUpperTimeBorderForStressTest(), TIGHT);
        assertEquals(80.0, vs.getLowerAssetBorderForStressTest(), TIGHT);
        assertEquals(120.0, vs.getUpperAssetBorderForStressTest(), TIGHT);
        assertEquals(0.03, vs.getStressLevel(), TIGHT);
    }

    @Test
    public void inheritsGeneralizedBlackScholesProcess() {
        // Sanity: instance is-a GeneralizedBlackScholesProcess
        final Date today = Date.todaysDate();
        final Actual365Fixed dc = new Actual365Fixed();
        final Handle<SimpleQuote> spot = new Handle<>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> r = new Handle<>(
                new FlatForward(today, 0.04, dc));
        final Handle<YieldTermStructure> q = new Handle<>(
                new FlatForward(today, 0.02, dc));
        final Handle<BlackVolTermStructure> vol = new Handle<>(
                new BlackConstantVol(today, new NullCalendar(), 0.20, dc));

        final VegaStressedBlackScholesProcess vs = new VegaStressedBlackScholesProcess(
                spot, q, r, vol);
        assertEquals(true, vs instanceof GeneralizedBlackScholesProcess);
    }
}
