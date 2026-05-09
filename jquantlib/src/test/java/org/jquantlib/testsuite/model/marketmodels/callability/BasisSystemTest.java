/*
 Copyright (C) 2026 Jose Moya. JQuantLib migration Phase 3k B.7-B.8.

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

package org.jquantlib.testsuite.model.marketmodels.callability;

import static org.junit.Assert.assertEquals;

import org.jquantlib.model.marketmodels.callability.SwapBasisSystem;
import org.jquantlib.model.marketmodels.callability.SwapForwardBasisSystem;
import org.jquantlib.model.marketmodels.curvestates.LMMCurveState;
import org.junit.Test;

/**
 * Phase 3k B.7-B.8 — basis systems.
 */
public class BasisSystemTest {

    private static final double TOL = 1.0e-12;

    @Test
    public void swapBasisSystemNumberOfFunctions() {
        // 5 rate times -> 4 forwards, exercise at first three -> 3 exercises;
        // last exerciseIndex = 2 -> rateIndex_[last] should be < n-2 (=3)
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {0.5, 1.0, 1.5};
        final SwapBasisSystem bs = new SwapBasisSystem(rateTimes, exerciseTimes);
        assertEquals(3, bs.numberOfExercises());
        // numberOfFunctions: each = 3 unless last rateIndex == n-2
        // For exerciseTimes[2]=1.5, lower-bound rate index in {0.5,1.0,1.5,2.0,2.5} = 2
        // n-2 = 3, so 2 != 3 -> 3 functions everywhere
        assertEquals(3, bs.numberOfFunctions().length);
        for (int s : bs.numberOfFunctions()) {
            assertEquals(3, s);
        }
    }

    @Test
    public void swapBasisSystemTailHas2Functions() {
        // exercise at the last possible time -> rateIndex == n-2  -> 2 funcs at tail
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {0.5, 2.0}; // last = 2.0 -> rateIndex = 3 = n-2
        final SwapBasisSystem bs = new SwapBasisSystem(rateTimes, exerciseTimes);
        assertEquals(3, bs.numberOfFunctions()[0]);
        assertEquals(2, bs.numberOfFunctions()[1]);
    }

    @Test
    public void swapBasisSystemValues() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {0.5, 1.0, 1.5};
        final SwapBasisSystem bs = new SwapBasisSystem(rateTimes, exerciseTimes);
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        final double[] fwds = {0.04, 0.045, 0.05, 0.055};
        cs.setOnForwardRates(fwds);

        bs.reset();
        // C++ behaviour: nextStep(state); then values(...) uses currentIndex_-1
        bs.nextStep(cs);
        // rateIndex_[0]: lower-bound for 0.5 in rateTimes -> index 0
        // rateIndex < n-2 (=3), so 3 functions
        final double[] r = new double[3];
        bs.values(cs, r);
        assertEquals(1.0, r[0], TOL);
        assertEquals(fwds[0], r[1], TOL);
        // r[2] = coterminalSwapRate(rateIndex+1) = coterminalSwapRate(1)
        assertEquals(cs.coterminalSwapRate(1), r[2], TOL);
    }

    @Test
    public void swapForwardBasisSystemNumberOfFunctions() {
        // 6 rate times -> 5 forwards, n=6
        // exerciseTimes spread across grid -> last rateIndex placement varies
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
        // tail at exerciseTimes[2] = 1.5 -> rateIndex = 2; n-3 = 3, n-2 = 4
        // 2 < 3 -> default 10 funcs
        final double[] exerciseTimes = {0.5, 1.0, 1.5};
        SwapForwardBasisSystem bs = new SwapForwardBasisSystem(rateTimes, exerciseTimes);
        for (int s : bs.numberOfFunctions()) {
            assertEquals(10, s);
        }

        // tail at 2.0 -> rateIndex = 3 = n-3 -> 6 funcs at tail
        bs = new SwapForwardBasisSystem(rateTimes, new double[]{0.5, 2.0});
        assertEquals(10, bs.numberOfFunctions()[0]);
        assertEquals(6, bs.numberOfFunctions()[1]);

        // tail at 2.5 -> rateIndex = 4 = n-2 -> 3 funcs at tail
        bs = new SwapForwardBasisSystem(rateTimes, new double[]{0.5, 2.5});
        assertEquals(10, bs.numberOfFunctions()[0]);
        assertEquals(3, bs.numberOfFunctions()[1]);
    }

    @Test
    public void swapForwardBasisSystemValues10Functions() {
        // Need rateIndex < n-3.  Pick rateTimes with n=6, exercise at 0.5 -> idx 0 < 3.
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
        final double[] exerciseTimes = {0.5};
        final SwapForwardBasisSystem bs = new SwapForwardBasisSystem(rateTimes, exerciseTimes);
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.045, 0.05, 0.055, 0.06});

        bs.reset();
        bs.nextStep(cs);
        final double[] r = new double[10];
        bs.values(cs, r);

        final double x = cs.forwardRate(0);
        final double y = cs.coterminalSwapRate(1);
        final double z = cs.discountRatio(0, rateTimes.length - 1);
        assertEquals(1.0, r[0], TOL);
        assertEquals(x, r[1], TOL);
        assertEquals(y, r[2], TOL);
        assertEquals(z, r[3], TOL);
        assertEquals(x * y, r[4], TOL);
        assertEquals(y * z, r[5], TOL);
        assertEquals(z * x, r[6], TOL);
        assertEquals(x * x, r[7], TOL);
        assertEquals(y * y, r[8], TOL);
        assertEquals(z * z, r[9], TOL);
    }

    @Test
    public void swapForwardBasisSystemValues3FunctionsTail() {
        // Tail at last possible time -> rateIndex = n-2 -> 3 functions
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0};
        final double[] exerciseTimes = {2.5}; // rateIndex = 4 = n-2
        final SwapForwardBasisSystem bs = new SwapForwardBasisSystem(rateTimes, exerciseTimes);
        final LMMCurveState cs = new LMMCurveState(rateTimes);
        cs.setOnForwardRates(new double[]{0.04, 0.045, 0.05, 0.055, 0.06});

        bs.reset();
        bs.nextStep(cs);
        final double[] r = new double[3];
        bs.values(cs, r);
        final double x = cs.forwardRate(4);
        assertEquals(1.0, r[0], TOL);
        assertEquals(x, r[1], TOL);
        assertEquals(x * x, r[2], TOL);
    }

    @Test
    public void cloneIndependence() {
        final double[] rateTimes = {0.5, 1.0, 1.5, 2.0, 2.5};
        final double[] exerciseTimes = {0.5, 1.0, 1.5};
        final SwapBasisSystem bs = new SwapBasisSystem(rateTimes, exerciseTimes);
        final SwapBasisSystem copy = bs.clone();
        assertEquals(bs.numberOfExercises(), copy.numberOfExercises());

        final SwapForwardBasisSystem fbs =
                new SwapForwardBasisSystem(rateTimes, exerciseTimes);
        final SwapForwardBasisSystem fbsCopy = fbs.clone();
        assertEquals(fbs.numberOfExercises(), fbsCopy.numberOfExercises());
    }
}
