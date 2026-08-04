/*
 Copyright (C) 2026 Jose Moya

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
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.currencies.Asia.TWDCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.instruments.MakeVanillaSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.JointCalendar;
import org.jquantlib.time.calendars.Taiwan;
import org.jquantlib.time.calendars.UnitedStates;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Java equivalent of the {@code testSpotDateUsesFixingCalendar} case added to C++ QuantLib's test suite in v1.43
 * ({@code test-suite/swap.cpp}), a regression test for upstream issue #2546.
 * <p>
 * When the index's fixing calendar and the float/payment calendar disagree — as they do for a non-deliverable IRS
 * with a local fixing calendar and a joint payment calendar — the spot date used to be pre-adjusted on the payment
 * calendar and then advanced by {@code valueDate} on the fixing calendar, landing one business day late. The spot
 * date is defined entirely by the index, so both steps must happen on the index's own calendar.
 *
 * @author Jose Moya
 */
public class MakeVanillaSwapSpotDateTest {

    /**
     * 19 January 2026 is the third Monday of January (Martin Luther King's birthday): a US Federal Reserve holiday
     * but a Taiwan business day, so the two calendars diverge on exactly this date.
     */
    private static final Date TODAY = new Date(19, Month.January, 2026);

    private Date savedEvaluationDate;

    @Before
    public void setUp() {
        savedEvaluationDate = new Settings().evaluationDate();
        new Settings().setEvaluationDate(TODAY);
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvaluationDate);
    }

    @Test
    public void testSpotDateUsesFixingCalendar() {
        QL.info("Testing that MakeVanillaSwap derives the spot date on the index fixing calendar...");

        final RelinkableHandle< YieldTermStructure > yts = new RelinkableHandle< YieldTermStructure >();
        yts.linkTo(new FlatForward(TODAY, 0.03, new Actual365Fixed()));

        final Calendar fixingCalendar = new Taiwan();
        final Calendar paymentCalendar = new JointCalendar(new Taiwan(),
                new UnitedStates(UnitedStates.Market.FederalReserve));

        final IborIndex index = new IborIndex("Taibor3M", new Period(3, TimeUnit.Months), 2, new TWDCurrency(),
                fixingCalendar, BusinessDayConvention.ModifiedFollowing, true, new Actual365Fixed(), yts);

        // The correct spot date is defined entirely by the index: the evaluation date adjusted and then advanced on
        // the fixing calendar.
        final Date expectedStart = index.valueDate(fixingCalendar.adjust(TODAY));

        final VanillaSwap swap = new MakeVanillaSwap(new Period(1, TimeUnit.Years), index, 0.03)
                .withFixedLegTenor(new Period(1, TimeUnit.Years))
                .withFixedLegDayCount(new Actual365Fixed())
                .withFloatingLegCalendar(paymentCalendar)
                .withFixedLegCalendar(paymentCalendar)
                .value();

        assertEquals("MakeVanillaSwap spot date must be derived on the index fixing calendar", expectedStart,
                swap.startDate());

        // Sanity check that this market actually discriminates: the old path pre-adjusted on the joint calendar,
        // which rolls 19 January forward before the two-day fixing advance and lands a business day late. Without
        // this assertion the test could pass on a market where the two calendars happen to agree.
        final Date buggyStart = index.valueDate(paymentCalendar.adjust(TODAY));
        assertTrue("the two calendars must disagree here, or the test proves nothing",
                !buggyStart.equals(expectedStart));
    }
}
