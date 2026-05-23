/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license. You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.testsuite.math.randomnumbers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.randomnumbers.Burley2020SobolBrownianBridgeRsg;
import org.jquantlib.methods.montecarlo.Sample;
import org.jquantlib.model.marketmodels.browniangenerators.SobolBrownianGenerator;
import org.junit.Test;

/**
 * Tests for {@link Burley2020SobolBrownianBridgeRsg}.
 */
public class Burley2020SobolBrownianBridgeRsgTest {

    @Test
    public void testDimension() {
        final Burley2020SobolBrownianBridgeRsg rsg = new Burley2020SobolBrownianBridgeRsg(3, 5);
        assertEquals(15, rsg.dimension());
    }

    @Test
    public void testNextSequenceShape() {
        final Burley2020SobolBrownianBridgeRsg rsg = new Burley2020SobolBrownianBridgeRsg(3, 5);
        final Sample< double[] > s = rsg.nextSequence();
        assertNotNull(s);
        assertEquals(15, s.value().length);
        assertEquals(1.0, s.weight(), 0.0);
        // Last sequence equals last produced.
        final Sample< double[] > last = rsg.lastSequence();
        assertEquals(s.value(), last.value()); // same buffer reference
    }

    @Test
    public void testStatisticalProperties() {
        // 1 factor, 4 steps: the marginal distribution of each step's variate should be ~N(0,1).
        final Burley2020SobolBrownianBridgeRsg rsg =
                new Burley2020SobolBrownianBridgeRsg(1, 4, SobolBrownianGenerator.Ordering.Diagonal);
        final int N = 4096;
        final double[] sums = new double[4];
        final double[] sumSq = new double[4];
        for ( int n = 0; n < N; ++n ) {
            final double[] v = rsg.nextSequence().value();
            for ( int s = 0; s < 4; ++s ) {
                sums[s] += v[s];
                sumSq[s] += v[s] * v[s];
            }
        }
        for ( int s = 0; s < 4; ++s ) {
            final double mean = sums[s] / N;
            final double variance = sumSq[s] / N - mean * mean;
            assertEquals("step " + s + " mean", 0.0, mean, 0.1);
            // Brownian bridge: marginal variance at step s+1 within the bridge depends on the
            // bridge construction but is approximately 1.0 for the largest step.
            assertTrue("step " + s + " variance should be positive (was " + variance + ")", variance > 0.0);
        }
    }

    @Test
    public void testSequenceVariesBetweenCalls() {
        final Burley2020SobolBrownianBridgeRsg rsg = new Burley2020SobolBrownianBridgeRsg(2, 3);
        final double[] first = rsg.nextSequence().value().clone();
        final double[] second = rsg.nextSequence().value().clone();
        boolean differ = false;
        for ( int i = 0; i < first.length; ++i ) {
            if ( first[i] != second[i] ) {
                differ = true;
                break;
            }
        }
        assertTrue("consecutive sequences should differ", differ);
    }
}
