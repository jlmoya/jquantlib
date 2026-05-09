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

package org.jquantlib.testsuite.math;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.Rounding;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/rounding.cpp (Phase 5a).
 *
 * <p>Faithful port of all 5 BOOST_AUTO_TEST_CASE cases from the C++ suite.
 * Each case applies one of the {@link Rounding} subclass operators against
 * a fixed table of 21 (decimal, precision, expected) tuples and verifies
 * the rounded result matches with tolerance {@code n=1} per
 * {@link Closeness#isClose(double, double, int)}.
 */
public class RoundingTest {

    public RoundingTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final class TestCase {
        final double x;
        final int precision;
        final double closest;
        final double up;
        final double down;
        final double floor;
        final double ceiling;

        TestCase(final double x, final int precision,
                 final double closest, final double up, final double down,
                 final double floor, final double ceiling) {
            this.x = x;
            this.precision = precision;
            this.closest = closest;
            this.up = up;
            this.down = down;
            this.floor = floor;
            this.ceiling = ceiling;
        }
    }

    private static final TestCase[] TEST_DATA = new TestCase[] {
        new TestCase( 0.86313513, 5,  0.86314,  0.86314,  0.86313,  0.86314,  0.86313),
        new TestCase( 0.86313,    5,  0.86313,  0.86313,  0.86313,  0.86313,  0.86313),
        new TestCase(-7.64555346, 1, -7.6,     -7.7,     -7.6,     -7.6,     -7.6    ),
        new TestCase( 0.13961605, 2,  0.14,     0.14,     0.13,     0.14,     0.13   ),
        new TestCase( 0.14344179, 4,  0.1434,   0.1435,   0.1434,   0.1434,   0.1434 ),
        new TestCase(-4.74315016, 2, -4.74,    -4.75,    -4.74,    -4.74,    -4.74   ),
        new TestCase(-7.82772074, 5, -7.82772, -7.82773, -7.82772, -7.82772, -7.82772),
        new TestCase( 2.74137947, 3,  2.741,    2.742,    2.741,    2.741,    2.741  ),
        new TestCase( 2.13056714, 1,  2.1,      2.2,      2.1,      2.1,      2.1    ),
        new TestCase(-1.06228670, 1, -1.1,     -1.1,     -1.0,     -1.0,     -1.1    ),
        new TestCase( 8.29234094, 4,  8.2923,   8.2924,   8.2923,   8.2923,   8.2923 ),
        new TestCase( 7.90185598, 2,  7.90,     7.91,     7.90,     7.90,     7.90   ),
        new TestCase(-0.26738058, 1, -0.3,     -0.3,     -0.2,     -0.2,     -0.3    ),
        new TestCase( 1.78128713, 1,  1.8,      1.8,      1.7,      1.8,      1.7    ),
        new TestCase( 4.23537260, 1,  4.2,      4.3,      4.2,      4.2,      4.2    ),
        new TestCase( 3.64369953, 4,  3.6437,   3.6437,   3.6436,   3.6437,   3.6436 ),
        new TestCase( 6.34542470, 2,  6.35,     6.35,     6.34,     6.35,     6.34   ),
        new TestCase(-0.84754962, 4, -0.8475,  -0.8476,  -0.8475,  -0.8475,  -0.8475 ),
        new TestCase( 4.60998652, 1,  4.6,      4.7,      4.6,      4.6,      4.6    ),
        new TestCase( 6.28794223, 3,  6.288,    6.288,    6.287,    6.288,    6.287  ),
        new TestCase( 7.89428221, 2,  7.89,     7.90,     7.89,     7.89,     7.89   )
    };

    @Test
    public void testClosest() {
        QL.info("Testing closest decimal rounding...");
        for (final TestCase c : TEST_DATA) {
            final Rounding rounding = new Rounding.ClosestRounding(c.precision);
            final double calculated = rounding.operator(c.x);
            if (!Closeness.isClose(calculated, c.closest, 1)) {
                fail("Original number: " + c.x
                        + "\nExpected:        " + c.closest
                        + "\nCalculated:      " + calculated);
            }
        }
    }

    @Test
    public void testUp() {
        QL.info("Testing upward decimal rounding...");
        for (final TestCase c : TEST_DATA) {
            final Rounding rounding = new Rounding.UpRounding(c.precision);
            final double calculated = rounding.operator(c.x);
            if (!Closeness.isClose(calculated, c.up, 1)) {
                fail("Original number: " + c.x
                        + "\nExpected:        " + c.up
                        + "\nCalculated:      " + calculated);
            }
        }
    }

    @Test
    public void testDown() {
        QL.info("Testing downward decimal rounding...");
        for (final TestCase c : TEST_DATA) {
            final Rounding rounding = new Rounding.DownRounding(c.precision);
            final double calculated = rounding.operator(c.x);
            if (!Closeness.isClose(calculated, c.down, 1)) {
                fail("Original number: " + c.x
                        + "\nExpected:        " + c.down
                        + "\nCalculated:      " + calculated);
            }
        }
    }

    @Test
    public void testFloor() {
        QL.info("Testing floor decimal rounding...");
        for (final TestCase c : TEST_DATA) {
            final Rounding rounding = new Rounding.FloorTruncation(c.precision);
            final double calculated = rounding.operator(c.x);
            if (!Closeness.isClose(calculated, c.floor, 1)) {
                fail("Original number: " + c.x
                        + "\nExpected:        " + c.floor
                        + "\nCalculated:      " + calculated);
            }
        }
    }

    @Test
    public void testCeiling() {
        QL.info("Testing ceiling decimal rounding...");
        for (final TestCase c : TEST_DATA) {
            final Rounding rounding = new Rounding.CeilingTruncation(c.precision);
            final double calculated = rounding.operator(c.x);
            if (!Closeness.isClose(calculated, c.ceiling, 1)) {
                fail("Original number: " + c.x
                        + "\nExpected:        " + c.ceiling
                        + "\nCalculated:      " + calculated);
            }
        }
    }
}
