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
 */

package org.jquantlib.testsuite.model.marketmodels;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.model.marketmodels.EvolutionDescription;
import org.junit.Test;

/**
 * Tests for {@link EvolutionDescription} — Phase 3h A.2.
 *
 * <p>Cross-validated against {@code ql/models/marketmodels/evolutiondescription.cpp}
 * (QuantLib v1.42.1).
 */
public class EvolutionDescriptionTest {

    public EvolutionDescriptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final double TOL = 1e-12;

    /**
     * Standard 5-rate grid as used in the C++ test-suite (marketmodel.cpp setup):
     * rateTimes = {1, 2, 3, 4, 5, 6}, evolutionTimes default = {1, 2, 3, 4, 5}.
     */
    @Test
    public void testFiveRateGridDefaultEvolution() {
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);

        assertEquals(5, ev.numberOfRates());
        assertEquals(5, ev.numberOfSteps());

        // rateTaus = differences = {1, 1, 1, 1, 1}
        final double[] expectedTaus = {1.0, 1.0, 1.0, 1.0, 1.0};
        assertArrayEquals(expectedTaus, ev.rateTaus(), TOL);

        // evolutionTimes = first n elements = {1, 2, 3, 4, 5}
        final double[] expectedEv = {1.0, 2.0, 3.0, 4.0, 5.0};
        assertArrayEquals(expectedEv, ev.evolutionTimes(), TOL);

        // firstAliveRate: at evolution time t_i, the firstAliveRate is the smallest j
        // such that rateTimes[j] > evolutionTimes[i-1] (where evolutionTimes[-1] = 0).
        //
        // C++ algorithm:
        //   currentEvolutionTime = 0; firstAliveRate = 0
        //   for j in 0..numberOfSteps:
        //     while rateTimes[firstAliveRate] <= currentEvolutionTime: ++firstAliveRate
        //     firstAliveRate_[j] = firstAliveRate
        //     currentEvolutionTime = evolutionTimes_[j]
        //
        // For our grid:
        //   j=0: cur=0; while rateTimes[0]=1 <= 0? no. firstAlive[0]=0. cur=1.
        //   j=1: cur=1; while rateTimes[0]=1 <= 1? yes ++. while rateTimes[1]=2 <= 1? no. firstAlive[1]=1. cur=2.
        //   j=2: while rateTimes[1]=2 <= 2? yes ++. while rateTimes[2]=3 <= 2? no. firstAlive[2]=2. cur=3.
        //   j=3: firstAlive[3]=3. cur=4.
        //   j=4: firstAlive[4]=4. cur=5.
        final int[] expectedFirst = {0, 1, 2, 3, 4};
        assertArrayEquals(expectedFirst, ev.firstAliveRate());
    }

    @Test
    public void testRelevanceRatesDefault() {
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);

        // Default relevance ranges from 0..numberOfRates_ for each step
        final EvolutionDescription.Range[] r = ev.relevanceRates();
        assertEquals(3, r.length);
        for (final EvolutionDescription.Range rr : r) {
            assertEquals(0, rr.first());
            assertEquals(3, rr.second());
        }
    }

    @Test
    public void testTerminalMeasure() {
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);

        final int[] terminal = EvolutionDescription.terminalMeasure(ev);
        assertEquals(5, terminal.length);
        // terminal: every numeraire equals rateTimes.length-1 = 5
        for (final int n : terminal) {
            assertEquals(5, n);
        }
        assertTrue(EvolutionDescription.isInTerminalMeasure(ev, terminal));
    }

    @Test
    public void testMoneyMarketMeasure() {
        // For a uniform grid, money-market measure picks the first unexpired
        // bond at each evolution time.
        // rateTimes={1,2,3,4,5,6}, evolutionTimes={1,2,3,4,5}
        //
        // C++ algorithm:
        //   j=0; for i=0..n-1:
        //     while rateTimes[j] < evolutionTimes[i]: j++
        //     numeraires[i] = min(j+offset, maxNumeraire)
        //
        // For offset=0:
        //   i=0: rateTimes[0]=1 < 1? no. j=0. numeraires[0]=0. (offset=0 → min(0,5)=0)
        //   i=1: rateTimes[0]=1 < 2? yes j=1. rateTimes[1]=2 < 2? no. numeraires[1]=1.
        //   i=2: rateTimes[1]=2 < 3? yes j=2. rateTimes[2]=3 < 3? no. numeraires[2]=2.
        //   i=3: numeraires[3]=3.
        //   i=4: numeraires[4]=4.
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);

        final int[] mm = EvolutionDescription.moneyMarketMeasure(ev);
        final int[] expected = {0, 1, 2, 3, 4};
        assertArrayEquals(expected, mm);
        assertTrue(EvolutionDescription.isInMoneyMarketMeasure(ev, mm));
        assertFalse(EvolutionDescription.isInTerminalMeasure(ev, mm));
    }

    @Test
    public void testMoneyMarketPlusMeasureOffset1() {
        // With offset=1, numeraires are shifted +1 (capped at maxNumeraire=5)
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);

        final int[] mmp = EvolutionDescription.moneyMarketPlusMeasure(ev, 1);
        final int[] expected = {1, 2, 3, 4, 5};
        assertArrayEquals(expected, mmp);
        assertTrue(EvolutionDescription.isInMoneyMarketPlusMeasure(ev, mmp, 1));
    }

    @Test
    public void testCheckCompatibilityValid() {
        final double[] rateTimes = {1.0, 2.0, 3.0, 4.0};
        final EvolutionDescription ev = new EvolutionDescription(rateTimes);
        // numeraires per step must be valid (rateTimes[numeraires[i]] >= evolutionTimes[i])
        // for i < n-1.
        final int[] numeraires = {3, 3, 3};
        EvolutionDescription.checkCompatibility(ev, numeraires);
        // no exception → pass
    }
}
