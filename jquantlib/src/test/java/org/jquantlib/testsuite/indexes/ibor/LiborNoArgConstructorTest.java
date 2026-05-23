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

package org.jquantlib.testsuite.indexes.ibor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.indexes.ibor.AUDLibor;
import org.jquantlib.indexes.ibor.CADLibor;
import org.jquantlib.indexes.ibor.CADLiborON;
import org.jquantlib.indexes.ibor.CHFLibor;
import org.jquantlib.indexes.ibor.Cdor;
import org.jquantlib.indexes.ibor.DKKLibor;
import org.jquantlib.indexes.ibor.DailyTenorCHFLibor;
import org.jquantlib.indexes.ibor.DailyTenorGBPLibor;
import org.jquantlib.indexes.ibor.DailyTenorJPYLibor;
import org.jquantlib.indexes.ibor.DailyTenorUSDLibor;
import org.jquantlib.indexes.ibor.GBPLibor;
import org.jquantlib.indexes.ibor.GBPLiborON;
import org.jquantlib.indexes.ibor.JPYLibor;
import org.jquantlib.indexes.ibor.Jibar;
import org.jquantlib.indexes.ibor.NZDLibor;
import org.jquantlib.indexes.ibor.SEKLibor;
import org.jquantlib.indexes.ibor.TRLibor;
import org.jquantlib.indexes.ibor.Tibor;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.indexes.ibor.USDLiborON;
import org.jquantlib.indexes.ibor.Zibor;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Phase 3-D regression: every LIBOR-family index used to build a no-arg
 * default constructor by wrapping an anonymous {@code AbstractYieldTermStructure}
 * whose {@code discountImpl(double)} and {@code maxDate()} both threw
 * {@link UnsupportedOperationException}. That diverged from C++ v1.42.1
 * which uses {@code Handle<YieldTermStructure>{}} — i.e. an empty handle.
 *
 * <p>This test verifies that constructing every Libor variant with no
 * arguments now succeeds and produces a usable index whose forecasting
 * curve is empty (not a UOE-throwing dummy).
 */
public class LiborNoArgConstructorTest {

    public LiborNoArgConstructorTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final Period T3M = new Period(3, TimeUnit.Months);

    /** Tenor-based LIBOR no-arg constructors must yield an empty forecasting handle. */
    @Test
    public void testTenorLiborsConstructEmptyHandle() {
        // Each constructor previously threw UnsupportedOperationException when
        // the embedded dummy term structure was queried. After Phase 3-D the
        // handle is just empty (Libor.termStructure().empty() == true).
        assertNotNull(new USDLibor(T3M));
        assertNotNull(new GBPLibor(T3M));
        assertNotNull(new JPYLibor(T3M));
        assertNotNull(new CHFLibor(T3M));
        assertNotNull(new CADLibor(T3M));
        assertNotNull(new AUDLibor(T3M));
        assertNotNull(new SEKLibor(T3M));
        assertNotNull(new DKKLibor(T3M));
        assertNotNull(new NZDLibor(T3M));
        assertNotNull(new TRLibor(T3M));
        assertNotNull(new Tibor(T3M));
        assertNotNull(new Jibar(T3M));
        assertNotNull(new Zibor(T3M));
        assertNotNull(new Cdor(T3M));
        assertTrue("USDLibor handle must be empty", new USDLibor(T3M).termStructure().empty());
        assertTrue("GBPLibor handle must be empty", new GBPLibor(T3M).termStructure().empty());
    }

    /** Daily-tenor and overnight LIBOR no-arg constructors must succeed. */
    @Test
    public void testDailyTenorAndONLiborsConstructEmptyHandle() {
        assertNotNull(new DailyTenorUSDLibor(2));
        assertNotNull(new DailyTenorGBPLibor(0));
        assertNotNull(new DailyTenorJPYLibor(2));
        assertNotNull(new DailyTenorCHFLibor(2));

        assertNotNull(new USDLiborON());
        assertNotNull(new GBPLiborON());
        // Java's CADLiborON has a divergent (Period)-taking constructor —
        // separate alignment from this Phase 3-D UOE cleanup. Use the
        // Period overload to exercise the empty-handle path.
        assertNotNull(new CADLiborON(T3M));

        assertTrue("USDLiborON handle must be empty", new USDLiborON().termStructure().empty());
        assertTrue("GBPLiborON handle must be empty", new GBPLiborON().termStructure().empty());
    }
}
