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
import static org.junit.Assert.assertTrue;

import org.jquantlib.math.randomnumbers.RandomNumberGenerator;
import org.jquantlib.math.randomnumbers.Ranlux64UniformRng;
import org.junit.Test;

/**
 * Tests for {@link Ranlux64UniformRng} facade.
 * Bit-exactness of the underlying algorithm is covered in
 * {@code RanluxUniformRngTest}; here we verify the facade dispatches to the
 * correct (P, R) pair.
 */
public class Ranlux64UniformRngTest {

    @Test
    public void testRanlux3Seed42First5() {
        // Cross-validated against /tmp/l1c_rng_probe (C++ Ranlux3UniformRng(42UL)).
        final RandomNumberGenerator rng = Ranlux64UniformRng.ranlux3(42L);
        final double[] expected = {
                0.44359375777861487,
                0.49039988112563293,
                0.29105130325944728,
                0.96392937463021866,
                0.20402265316086599,
        };
        for ( int i = 0; i < expected.length; ++i ) {
            assertEquals("output[" + i + "]", expected[i], rng.next().value(), 1e-15);
        }
    }

    @Test
    public void testRanlux4DistinctFromRanlux3() {
        final RandomNumberGenerator r3 = Ranlux64UniformRng.ranlux3(42L);
        final RandomNumberGenerator r4 = Ranlux64UniformRng.ranlux4(42L);
        // Different block sizes (P=223 vs P=389) ⇒ first kept-block matches, but discard cadence diverges.
        // After several draws, the streams must diverge.
        for ( int i = 0; i < 30; ++i ) {
            r3.next();
            r4.next();
        }
        // The 30th draw should differ for the two luxuries.
        final double a = r3.next().value();
        final double b = r4.next().value();
        assertTrue("ranlux3 and ranlux4 should diverge after the first block", a != b);
    }
}
