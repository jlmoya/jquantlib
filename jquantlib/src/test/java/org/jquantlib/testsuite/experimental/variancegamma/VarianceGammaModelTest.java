/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4c — VarianceGammaModel smoke tests.

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
package org.jquantlib.testsuite.experimental.variancegamma;

import org.jquantlib.QL;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.variancegamma.VarianceGammaModel;
import org.jquantlib.experimental.variancegamma.VarianceGammaProcess;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Date;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Smoke tests for {@link VarianceGammaModel} and
 * {@link VarianceGammaProcess}.
 *
 * <p>The C++ class doesn't have a dedicated test in the QL test-suite (it
 * is exercised indirectly via the engine tests). We verify (a) constructor
 * + accessor round-trip, (b) {@code drift}/{@code diffusion} throw as in
 * v1.42.1, (c) the model exposes its underlying parameter array via
 * {@link VarianceGammaModel#sigma()} / {@code nu()} / {@code theta()},
 * and (d) {@code generateArguments} produces a fresh process.
 */
public class VarianceGammaModelTest {

    public VarianceGammaModelTest() {
        QL.info("::::: " + getClass().getSimpleName() + " :::::");
    }

    @Test
    public void processAccessors() {
        final Date today = Date.todaysDate();
        final DayCounter dc = new Actual360();
        final Handle<? extends Quote> spot =
                new Handle<SimpleQuote>(new SimpleQuote(100.0));
        final Handle<YieldTermStructure> q =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.02, dc));
        final Handle<YieldTermStructure> r =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.05, dc));

        final VarianceGammaProcess p = new VarianceGammaProcess(
                spot, q, r, 0.20, 0.05, -0.50);
        assertEquals(100.0, p.x0(), 0.0);
        assertEquals(0.20, p.sigma(), 0.0);
        assertEquals(0.05, p.nu(), 0.0);
        assertEquals(-0.50, p.theta(), 0.0);
        assertSame(spot, p.s0());
        assertSame(q, p.dividendYield());
        assertSame(r, p.riskFreeRate());

        // C++ raises QL_FAIL("not implemented yet") on drift/diffusion.
        try { p.drift(0.0, 0.0); fail("expected LibraryException"); } catch (LibraryException e) { /* expected */ }
        try { p.diffusion(0.0, 0.0); fail("expected LibraryException"); } catch (LibraryException e) { /* expected */ }
    }

    @Test
    public void modelExposesParameters() {
        final Date today = Date.todaysDate();
        final DayCounter dc = new Actual360();
        final Handle<? extends Quote> spot =
                new Handle<SimpleQuote>(new SimpleQuote(50.0));
        final Handle<YieldTermStructure> q =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.0, dc));
        final Handle<YieldTermStructure> r =
                new Handle<YieldTermStructure>(new FlatForward(today, 0.03, dc));

        final VarianceGammaProcess p = new VarianceGammaProcess(
                spot, q, r, 0.15, 0.10, -0.20);
        final VarianceGammaModel m = new VarianceGammaModel(p);

        assertEquals(0.15, m.sigma(), 0.0);
        assertEquals(0.10, m.nu(), 0.0);
        assertEquals(-0.20, m.theta(), 0.0);
        assertNotNull(m.process());
        // After construction, generateArguments() runs and creates a new
        // VarianceGammaProcess with the same parameters; not necessarily
        // the original instance.
        assertEquals(0.15, m.process().sigma(), 0.0);
        assertEquals(0.10, m.process().nu(), 0.0);
        assertEquals(-0.20, m.process().theta(), 0.0);
    }
}
