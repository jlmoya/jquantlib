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

package org.jquantlib.testsuite.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.time.TimeGrid;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/timegrid.cpp (Phase 5a).
 *
 * <p>10 BOOST_AUTO_TEST_CASE methods exercising the four constructors of
 * {@link TimeGrid} and the index/closestIndex/closestTime/mandatoryTimes
 * accessors.
 */
public class TimeGridTest {

    public TimeGridTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Test
    public void testConstructorAdditionalSteps() {
        QL.info("Testing TimeGrid construction with additional steps...");

        final List<Double> mandatory = Arrays.asList(1.0, 2.0, 4.0);
        final TimeGrid tg = new TimeGrid(mandatory, 8);

        // Expect 8 evenly sized steps over the interval [0, 4].
        final double[] expected = {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0};
        assertEquals(expected.length, tg.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("element " + i, expected[i], tg.get(i), 1e-12);
        }
    }

    @Test
    public void testConstructorMandatorySteps() {
        QL.info("Testing TimeGrid construction with only mandatory points...");

        final List<Double> mandatory = Arrays.asList(0.0, 1.0, 2.0, 4.0);
        final TimeGrid tg = new TimeGrid(mandatory);

        final double[] expected = {0.0, 1.0, 2.0, 4.0};
        assertEquals(expected.length, tg.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("element " + i, expected[i], tg.get(i), 1e-12);
        }
    }

    @Test
    public void testConstructorAdditionalStepsAutomatically() {
        QL.info("Testing TimeGrid construction with time step length determined automatically...");

        final List<Double> mandatory = Arrays.asList(0.0, 1.0, 2.0, 4.0);
        final TimeGrid tg = new TimeGrid(mandatory, 0);

        // Time step length is determined by minimal adjacent distance in given times.
        final double[] expected = {0.0, 1.0, 2.0, 3.0, 4.0};
        assertEquals(expected.length, tg.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("element " + i, expected[i], tg.get(i), 1e-12);
        }
    }

    @Test
    public void testConstructorEvenSteps() {
        QL.info("Testing TimeGrid construction with n evenly spaced points...");

        final double endTime = 10.0;
        final int steps = 5;
        final TimeGrid tg = new TimeGrid(endTime, steps);

        final double[] expected = {0.0, 2.0, 4.0, 6.0, 8.0, 10.0};
        assertEquals(expected.length, tg.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("element " + i, expected[i], tg.get(i), 1e-12);
        }
    }

    @Test
    public void testConstructorEmptyIterator() {
        QL.info("Testing that the TimeGrid constructor raises an error for empty iterators...");

        final List<Double> times = Collections.emptyList();
        try {
            new TimeGrid(times);
            fail("expected exception for empty iterator");
        } catch (final RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void testConstructorNegativeValuesInIterator() {
        QL.info("Testing that the TimeGrid constructor raises an error for negative time values...");

        final List<Double> times = Arrays.asList(-3.0, 1.0, 4.0, 5.0);
        try {
            new TimeGrid(times);
            fail("expected exception for negative time values");
        } catch (final RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void testIndex() {
        QL.info("Testing that querying an index by floating-point time works for exact time nodes and "
                + "throws otherwise...");

        // Will automatically insert additional point at t=0.
        final TimeGrid tg = new TimeGrid(Arrays.asList(1.0, 2.0, 5.0));

        try { tg.index(-2.0); fail("expected"); } catch (final RuntimeException e) { /* ok */ }
        assertEquals(4, tg.size());

        try { tg.index(-0.1); fail("expected"); } catch (final RuntimeException e) { /* ok */ }
        assertEquals(0, tg.index(0.0));
        try { tg.index(0.5); fail("expected"); } catch (final RuntimeException e) { /* ok */ }
        assertEquals(1, tg.index(1.0));
        try { tg.index(1.1); fail("expected"); } catch (final RuntimeException e) { /* ok */ }
        assertEquals(2, tg.index(2.0));
        try { tg.index(2.9); fail("expected"); } catch (final RuntimeException e) { /* ok */ }
        assertEquals(3, tg.index(5.0));
        try { tg.index(5.1); fail("expected"); } catch (final RuntimeException e) { /* ok */ }
    }

    @Test
    public void testClosestIndex() {
        QL.info("Testing that the returned index is closest to the requested time...");

        final TimeGrid tg = new TimeGrid(Arrays.asList(1.0, 2.0, 5.0));
        final int expectedIndex = 3;
        assertEquals(expectedIndex, tg.closestIndex(4.0));
    }

    @Test
    public void testClosestTime() {
        QL.info("Testing that the returned time matches the requested index...");

        final TimeGrid tg = new TimeGrid(Arrays.asList(1.0, 2.0, 5.0));
        final double expectedTime = 5.0;
        assertEquals(expectedTime, tg.closestTime(4.0), 1e-12);
    }

    @Test
    public void testMandatoryTimes() {
        QL.info("Testing that mandatory times are recalled correctly...");

        final List<Double> testTimes = Arrays.asList(1.0, 2.0, 4.0);
        final TimeGrid tg = new TimeGrid(testTimes, 8);

        // Mandatory times are those provided by the original iterator.
        final org.jquantlib.math.matrixutilities.Array mandatoryTimes = tg.mandatoryTimes();
        assertEquals(testTimes.size(), mandatoryTimes.size());
        for (int i = 0; i < testTimes.size(); i++) {
            assertEquals("mandatory[" + i + "]", testTimes.get(i), mandatoryTimes.get(i), 1e-12);
        }
    }
}
