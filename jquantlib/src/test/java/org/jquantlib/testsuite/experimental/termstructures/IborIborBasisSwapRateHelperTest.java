/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4g — IborIborBasisSwapRateHelper smoke tests.

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
package org.jquantlib.testsuite.experimental.termstructures;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.experimental.termstructures.IborIborBasisSwapRateHelper;
import org.jquantlib.experimental.termstructures.OvernightIborBasisSwapRateHelper;
import org.jquantlib.indexes.Euribor3M;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Smoke tests for {@link IborIborBasisSwapRateHelper} and
 * {@link OvernightIborBasisSwapRateHelper}.
 *
 * <p>Verifies that helper objects construct without error and that key
 * date and rate-helper properties meet basic sanity checks.
 * No C++ cross-validated reference values — structural smoke test.
 */
public class IborIborBasisSwapRateHelperTest {

    private static final Date TODAY = new Date(1, Month.January, 2025);

    @Before
    public void setUp() {
        new Settings().setEvaluationDate(TODAY);
    }

    // -------------------------------------------------------------------------
    // IborIborBasisSwapRateHelper
    // -------------------------------------------------------------------------

    @Test
    public void testIborIborHelperConstruction() {
        QL.info("::::: IborIborBasisSwapRateHelperTest::testIborIborHelperConstruction :::::");

        final Target cal = new Target();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<YieldTermStructure> discH =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.03, dc));
        final Handle<YieldTermStructure> baseH =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.031, dc));

        final Euribor3M base3m = new Euribor3M(baseH);
        final Euribor6M other6m = new Euribor6M(discH);

        // Basis quote: 10 bps
        final Handle<Quote> basisQuote =
                new Handle<Quote>(new SimpleQuote(0.001));

        final IborIborBasisSwapRateHelper helper = new IborIborBasisSwapRateHelper(
                basisQuote,
                new Period(2, TimeUnit.Years),
                2,
                cal,
                BusinessDayConvention.ModifiedFollowing,
                false,
                base3m,
                other6m,
                discH,
                false /* bootstrap other index curve */);

        assertNotNull("helper created", helper);
        assertNotNull("earliestDate", helper.earliestDate());
        assertNotNull("latestDate",   helper.latestDate());
        assertTrue("latestDate > earliestDate",
                helper.latestDate().gt(helper.earliestDate()));
    }

    @Test
    public void testIborIborHelperBootstrapBase() {
        QL.info("::::: IborIborBasisSwapRateHelperTest::testIborIborHelperBootstrapBase :::::");

        final Target cal = new Target();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<YieldTermStructure> discH =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.03, dc));
        final Handle<YieldTermStructure> otherH =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.032, dc));

        final Euribor3M base3m = new Euribor3M(discH);
        final Euribor6M other6m = new Euribor6M(otherH);

        final Handle<Quote> basisQuote =
                new Handle<Quote>(new SimpleQuote(0.0005));

        // Bootstrap base curve flag = true
        final IborIborBasisSwapRateHelper helper = new IborIborBasisSwapRateHelper(
                basisQuote,
                new Period(1, TimeUnit.Years),
                2,
                cal,
                BusinessDayConvention.ModifiedFollowing,
                false,
                base3m,
                other6m,
                discH,
                true /* bootstrap base curve */);

        assertNotNull("helper created", helper);
        assertTrue("latestDate > earliestDate",
                helper.latestDate().gt(helper.earliestDate()));
    }

    // -------------------------------------------------------------------------
    // OvernightIborBasisSwapRateHelper
    // -------------------------------------------------------------------------

    @Test
    public void testOvernightIborHelperConstruction() {
        QL.info("::::: IborIborBasisSwapRateHelperTest::testOvernightIborHelperConstruction :::::");

        final Target cal = new Target();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<YieldTermStructure> overnightH =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.028, dc));
        final Handle<YieldTermStructure> iborH =
                new Handle<YieldTermStructure>(new FlatForward(TODAY, 0.031, dc));

        // Use Euribor3M as stand-in for OvernightIndex (documented deviation)
        final Euribor3M baseOvn = new Euribor3M(overnightH);
        final Euribor6M other6m = new Euribor6M(iborH);

        final Handle<Quote> basisQuote =
                new Handle<Quote>(new SimpleQuote(0.002));

        final OvernightIborBasisSwapRateHelper helper = new OvernightIborBasisSwapRateHelper(
                basisQuote,
                new Period(2, TimeUnit.Years),
                2,
                cal,
                BusinessDayConvention.ModifiedFollowing,
                false,
                baseOvn,
                other6m);

        assertNotNull("helper created", helper);
        assertNotNull("earliestDate", helper.earliestDate());
        assertNotNull("latestDate",   helper.latestDate());
        assertTrue("latestDate > earliestDate",
                helper.latestDate().gt(helper.earliestDate()));
        assertNotNull("swap()", helper.swap());
    }
}
