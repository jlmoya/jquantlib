/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 5h.5-RND-b — FixedLocalVolSurface tests.

 This source code is release under the BSD License.

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the JQuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.
 */
package org.jquantlib.testsuite.termstructures.volatilities.equityfx;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.termstructures.volatilities.equityfx.FixedLocalVolSurface;
import org.jquantlib.termstructures.volatilities.equityfx.FixedLocalVolSurface.Extrapolation;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link FixedLocalVolSurface}.
 *
 * @author Phase 5h.5-RND-b
 */
public class FixedLocalVolSurfaceTest {

    private static final double TIGHT = 1e-12;

    public FixedLocalVolSurfaceTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void timeBasedConstructorReturnsExactValuesAtNodes() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);

        final double[] times   = {0.5, 1.0, 2.0};
        final double[] strikes = {80.0, 100.0, 120.0};
        // 3 strikes (rows) x 3 times (cols)
        final Matrix vols = new Matrix(3, 3);
        vols.set(0, 0, 0.30); vols.set(0, 1, 0.28); vols.set(0, 2, 0.26);
        vols.set(1, 0, 0.20); vols.set(1, 1, 0.18); vols.set(1, 2, 0.16);
        vols.set(2, 0, 0.30); vols.set(2, 1, 0.28); vols.set(2, 2, 0.26);

        final FixedLocalVolSurface s = new FixedLocalVolSurface(today, times, strikes, vols, dc,
                Extrapolation.ConstantExtrapolation, Extrapolation.ConstantExtrapolation);

        // At node times and strikes, the surface should reproduce the matrix.
        assertEquals(0.30, s.localVol(0.5,  80.0, false), TIGHT);
        assertEquals(0.20, s.localVol(0.5, 100.0, false), TIGHT);
        assertEquals(0.30, s.localVol(0.5, 120.0, false), TIGHT);
        assertEquals(0.28, s.localVol(1.0,  80.0, false), TIGHT);
        assertEquals(0.18, s.localVol(1.0, 100.0, false), TIGHT);
        assertEquals(0.26, s.localVol(2.0, 120.0, false), TIGHT);
    }

    @Test
    public void interpolatesLinearlyAcrossStrikesAndTimes() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);

        final double[] times   = {1.0, 2.0};
        final double[] strikes = {90.0, 110.0};
        final Matrix vols = new Matrix(2, 2);
        vols.set(0, 0, 0.20); vols.set(0, 1, 0.30);
        vols.set(1, 0, 0.40); vols.set(1, 1, 0.50);

        final FixedLocalVolSurface s = new FixedLocalVolSurface(today, times, strikes, vols, dc,
                Extrapolation.ConstantExtrapolation, Extrapolation.ConstantExtrapolation);

        // Across strikes at t=1: linear from 0.20 (k=90) to 0.40 (k=110)
        // → at k=100 → 0.30
        assertEquals(0.30, s.localVol(1.0, 100.0, false), TIGHT);
        // Across strikes at t=2: linear from 0.30 (k=90) to 0.50 (k=110)
        // → at k=100 → 0.40
        assertEquals(0.40, s.localVol(2.0, 100.0, false), TIGHT);
        // Across time at k=100: linear between 0.30 (t=1) and 0.40 (t=2)
        // → at t=1.5 → 0.35
        assertEquals(0.35, s.localVol(1.5, 100.0, false), TIGHT);
    }

    @Test
    public void perTimeStrikeVectors() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);

        final double[] times = {1.0, 2.0};
        // Per-time strike vectors (smile fan).
        final List<double[]> strikes = Arrays.asList(
                new double[]{80.0, 90.0, 100.0},
                new double[]{85.0, 95.0, 105.0});
        final Matrix vols = new Matrix(3, 2);
        vols.set(0, 0, 0.25); vols.set(0, 1, 0.27);
        vols.set(1, 0, 0.20); vols.set(1, 1, 0.22);
        vols.set(2, 0, 0.25); vols.set(2, 1, 0.27);

        final FixedLocalVolSurface s = new FixedLocalVolSurface(today, times, strikes, vols, dc,
                Extrapolation.ConstantExtrapolation, Extrapolation.ConstantExtrapolation);
        s.enableExtrapolation();

        // Direct nodes for t=1 — node strikes within last-slice range require extrapolation.
        assertEquals(0.25, s.localVol(1.0,  80.0, true), TIGHT);
        assertEquals(0.20, s.localVol(1.0,  90.0, true), TIGHT);
        assertEquals(0.25, s.localVol(1.0, 100.0, true), TIGHT);
        // Direct nodes for t=2
        assertEquals(0.27, s.localVol(2.0,  85.0, true), TIGHT);
        assertEquals(0.22, s.localVol(2.0,  95.0, true), TIGHT);
        assertEquals(0.27, s.localVol(2.0, 105.0, true), TIGHT);
    }

    @Test
    public void constantExtrapolationActsAcrossTimeBranch() {
        // ConstantExtrapolation kicks in only on the across-time branch
        // (not on exact-time match — matches C++ behaviour).
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);

        final double[] times   = {1.0, 2.0};
        final double[] strikes = {90.0, 110.0};
        final Matrix vols = new Matrix(2, 2);
        vols.set(0, 0, 0.20); vols.set(0, 1, 0.30);
        vols.set(1, 0, 0.40); vols.set(1, 1, 0.50);

        final FixedLocalVolSurface s = new FixedLocalVolSurface(today, times, strikes, vols, dc,
                Extrapolation.ConstantExtrapolation, Extrapolation.ConstantExtrapolation);
        s.enableExtrapolation();

        // At t=1.5 (across-time branch) with strike=50 (below 90):
        //   earlierStrike clamped to 90, laterStrike clamped to 90
        //   earlyVol = vol[t=1, k=90] = 0.20
        //   laterVol = vol[t=2, k=90] = 0.30
        //   linear in time: 0.20 + (0.30-0.20)/(2-1) * (1.5-1) = 0.25
        assertEquals(0.25, s.localVol(1.5,  50.0, true), TIGHT);
        // Above-band: clamps to vol@110, time-interp from 0.40 to 0.50 → 0.45
        assertEquals(0.45, s.localVol(1.5, 200.0, true), TIGHT);
    }

    @Test
    public void minMaxStrikeReturnedFromLastTimeSlice() {
        final DayCounter dc = new Actual365Fixed();
        final Date today = new Date(15, Month.January, 2026);

        final double[] times   = {1.0};
        final double[] strikes = {70.0, 100.0, 130.0};
        final Matrix vols = new Matrix(3, 1);
        vols.set(0, 0, 0.20);
        vols.set(1, 0, 0.20);
        vols.set(2, 0, 0.20);

        final FixedLocalVolSurface s = new FixedLocalVolSurface(today, times, strikes, vols, dc,
                Extrapolation.ConstantExtrapolation, Extrapolation.ConstantExtrapolation);

        assertEquals(70.0,  s.minStrike(), TIGHT);
        assertEquals(130.0, s.maxStrike(), TIGHT);
        assertEquals(1.0,   s.maxTime(), TIGHT);
        assertTrue(s.maxDate().gt(today));
    }
}
