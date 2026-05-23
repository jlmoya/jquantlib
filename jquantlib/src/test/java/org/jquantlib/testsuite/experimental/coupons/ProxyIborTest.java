/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase2 L5-D — ProxyIbor smoke test.

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
package org.jquantlib.testsuite.experimental.coupons;

import static org.junit.Assert.assertEquals;

import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.experimental.coupons.ProxyIbor;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Smoke test for {@link ProxyIbor}: proxy fixing equals g * f * s where g, s
 * are quote handles and f is the underlying IborIndex fixing.
 */
public class ProxyIborTest {

    @Test
    public void testForecastFixing() {
        // Backing 3M Euribor-like index with a flat forward curve.
        final Date today = new Date(15, org.jquantlib.time.Month.January, 2024);
        new org.jquantlib.Settings().setEvaluationDate(today);

        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.03, new Actual360()));

        final IborIndex underlying = new IborIndex(
                "EUR3M", new Period(3, TimeUnit.Months), 2,
                new EURCurrency(), new Target(),
                BusinessDayConvention.ModifiedFollowing, false, new Actual360(), ts);

        final SimpleQuote g = new SimpleQuote(1.5);
        final SimpleQuote s = new SimpleQuote(1.0); // multiplicative spread in C++ formula
        final ProxyIbor proxy = new ProxyIbor(
                "ProxyEUR3M", new Period(3, TimeUnit.Months), 2,
                new EURCurrency(), new Target(),
                BusinessDayConvention.ModifiedFollowing, false, new Actual360(),
                new Handle<Quote>(g), underlying, new Handle<Quote>(s));

        final Date fixingDate = new Target().advance(today, 2, TimeUnit.Days);
        final double base = underlying.fixing(fixingDate);
        final double proxied = proxy.fixing(fixingDate);
        assertEquals(g.value() * base * s.value(), proxied, 1e-15);

        // Bumping the spread quote must change the proxy fixing in lock-step.
        s.setValue(0.8);
        final double proxied2 = proxy.fixing(fixingDate);
        assertEquals(g.value() * base * s.value(), proxied2, 1e-15);
    }
}
