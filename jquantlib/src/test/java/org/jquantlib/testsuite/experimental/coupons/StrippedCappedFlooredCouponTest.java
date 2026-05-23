/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase2 L5-D — StrippedCappedFlooredCoupon smoke tests.

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jquantlib.cashflow.CappedFlooredCoupon;
import org.jquantlib.cashflow.CashFlow;
import org.jquantlib.cashflow.IborCoupon;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.experimental.coupons.StrippedCappedFlooredCoupon;
import org.jquantlib.experimental.coupons.StrippedCappedFlooredCoupon.StrippedCappedFlooredCouponLeg;
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

public class StrippedCappedFlooredCouponTest {

    private CappedFlooredCoupon makeCoupon(final double cap, final double floor) {
        final Date today = new Date(15, org.jquantlib.time.Month.January, 2024);
        new org.jquantlib.Settings().setEvaluationDate(today);
        final Handle<YieldTermStructure> ts = new Handle<YieldTermStructure>(
                new FlatForward(today, 0.03, new Actual360()));
        final IborIndex index = new IborIndex(
                "EUR3M", new Period(3, TimeUnit.Months), 2,
                new EURCurrency(), new Target(),
                BusinessDayConvention.ModifiedFollowing, false, new Actual360(), ts);
        final Date startDate = today;
        final Date endDate = startDate.add(new Period(3, TimeUnit.Months));
        final IborCoupon under = new IborCoupon(
                endDate, 100.0, startDate, endDate, 2, index,
                1.0, 0.0, startDate, endDate, new Actual360(), false);
        return new CappedFlooredCoupon(under, cap, floor);
    }

    @Test
    public void testFlagsForCapOnly() {
        final CappedFlooredCoupon cf = makeCoupon(0.05, Double.NaN);
        final StrippedCappedFlooredCoupon s = new StrippedCappedFlooredCoupon(cf);
        assertTrue(s.isCap());
        assertFalse(s.isFloor());
        assertFalse(s.isCollar());
    }

    @Test
    public void testFlagsForFloorOnly() {
        final CappedFlooredCoupon cf = makeCoupon(Double.NaN, 0.01);
        final StrippedCappedFlooredCoupon s = new StrippedCappedFlooredCoupon(cf);
        assertFalse(s.isCap());
        assertTrue(s.isFloor());
        assertFalse(s.isCollar());
    }

    @Test
    public void testFlagsForCollar() {
        final CappedFlooredCoupon cf = makeCoupon(0.05, 0.01);
        final StrippedCappedFlooredCoupon s = new StrippedCappedFlooredCoupon(cf);
        assertTrue(s.isCap());
        assertTrue(s.isFloor());
        assertTrue(s.isCollar());
        assertSame(cf, s.underlying());
        assertEquals(cf.cap(), s.cap(), 0.0);
        assertEquals(cf.floor(), s.floor(), 0.0);
        assertEquals(cf.effectiveCap(), s.effectiveCap(), 0.0);
        assertEquals(cf.effectiveFloor(), s.effectiveFloor(), 0.0);
    }

    @Test
    public void testLegConversionReplacesCappedFlooredOnly() {
        final CappedFlooredCoupon cf = makeCoupon(0.05, 0.01);
        final Leg input = new Leg();
        final CashFlow plain = new SimpleCashFlow(100.0, cf.date());
        input.add(cf);
        input.add(plain);

        final Leg out = new StrippedCappedFlooredCouponLeg(input).toLeg();
        assertEquals(2, out.size());
        assertTrue(out.get(0) instanceof StrippedCappedFlooredCoupon);
        assertSame(plain, out.get(1));
        assertSame(cf, ((StrippedCappedFlooredCoupon) out.get(0)).underlying());
    }
}
