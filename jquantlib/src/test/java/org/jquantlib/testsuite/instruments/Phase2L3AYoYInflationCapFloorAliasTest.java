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
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.cashflow.Leg;
import org.jquantlib.instruments.InflationCapFloor;
import org.jquantlib.instruments.YoYInflationCap;
import org.jquantlib.instruments.YoYInflationCapFloor;
import org.jquantlib.instruments.YoYInflationCollar;
import org.jquantlib.instruments.YoYInflationFloor;
import org.junit.Test;

/**
 * Wiring tests for the v1.42.1-named YoY inflation cap/floor top-level classes.
 *
 * <p>These classes are thin name aliases that subclass {@link InflationCapFloor}. The tests below
 * confirm: (a) the right {@code Type} flag is set by each constructor, (b) the {@code capRates}
 * / {@code floorRates} accessors return the expected populated/empty vectors, (c) all four are
 * still instances of {@link InflationCapFloor}.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class Phase2L3AYoYInflationCapFloorAliasTest {

    private static final List< Double > STRIKES_CAP = Arrays.asList(0.05);
    private static final List< Double > STRIKES_FLOOR = Arrays.asList(0.01);

    @Test
    public void testYoYInflationCapType() {
        final YoYInflationCap cap = new YoYInflationCap(new Leg(), STRIKES_CAP);
        assertSame(InflationCapFloor.Type.Cap, cap.type());
        assertTrue(cap instanceof InflationCapFloor);
        assertTrue(cap instanceof YoYInflationCapFloor);
        assertEquals(STRIKES_CAP, cap.capRates());
    }

    @Test
    public void testYoYInflationFloorType() {
        final YoYInflationFloor floor = new YoYInflationFloor(new Leg(), STRIKES_FLOOR);
        assertSame(InflationCapFloor.Type.Floor, floor.type());
        assertTrue(floor instanceof InflationCapFloor);
        assertTrue(floor instanceof YoYInflationCapFloor);
        assertEquals(STRIKES_FLOOR, floor.floorRates());
    }

    @Test
    public void testYoYInflationCollarType() {
        final YoYInflationCollar collar = new YoYInflationCollar(new Leg(), STRIKES_CAP, STRIKES_FLOOR);
        assertSame(InflationCapFloor.Type.Collar, collar.type());
        assertTrue(collar instanceof InflationCapFloor);
        assertTrue(collar instanceof YoYInflationCapFloor);
        assertEquals(STRIKES_CAP, collar.capRates());
        assertEquals(STRIKES_FLOOR, collar.floorRates());
    }

    @Test
    public void testYoYInflationCapFloorBaseConstructorTwoVectors() {
        final YoYInflationCapFloor inst = new YoYInflationCapFloor(
                InflationCapFloor.Type.Collar, new Leg(), STRIKES_CAP, STRIKES_FLOOR);
        assertSame(InflationCapFloor.Type.Collar, inst.type());
    }

    @Test
    public void testYoYInflationCapFloorBaseConstructorOneVector() {
        final YoYInflationCapFloor inst = new YoYInflationCapFloor(
                InflationCapFloor.Type.Cap, new Leg(), STRIKES_CAP);
        assertSame(InflationCapFloor.Type.Cap, inst.type());
    }
}
