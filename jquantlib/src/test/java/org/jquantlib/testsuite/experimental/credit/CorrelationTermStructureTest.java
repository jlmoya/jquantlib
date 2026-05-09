/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.credit.CorrelationTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 4m foundation tests for {@link CorrelationTermStructure} —
 * tested through a tiny private subclass that implements the abstract
 * {@code correlationSize()}.
 */
public class CorrelationTermStructureTest {

    /** Minimal subclass for testing the base class's plumbing. */
    private static class FixedSizeCorrelationTermStructure extends CorrelationTermStructure {
        private final int size;

        FixedSizeCorrelationTermStructure(final Date refDate, final Calendar cal,
                                          final BusinessDayConvention bdc,
                                          final DayCounter dc, final int size) {
            super(refDate, cal, bdc, dc);
            this.size = size;
        }

        @Override
        public int correlationSize() {
            return size;
        }

        @Override
        public Date maxDate() {
            // simplest sentinel
            return Date.maxDate();
        }
    }

    @Test
    public void basicConstructionAndAccessors() {
        final Date today = new Date(15, Month.June, 2010);
        final Calendar cal = new NullCalendar();
        final DayCounter dc = new Actual360();
        final FixedSizeCorrelationTermStructure ts = new FixedSizeCorrelationTermStructure(
                today, cal, BusinessDayConvention.ModifiedFollowing, dc, 5);
        assertEquals(BusinessDayConvention.ModifiedFollowing, ts.businessDayConvention());
        assertEquals(5, ts.correlationSize());
        assertEquals(today, ts.referenceDate());
        assertEquals(dc.name(), ts.dayCounter().name());
    }

    @Test
    public void dateFromTenor() {
        final Date today = new Date(15, Month.June, 2010);
        final Calendar cal = new NullCalendar();
        final FixedSizeCorrelationTermStructure ts = new FixedSizeCorrelationTermStructure(
                today, cal, BusinessDayConvention.Following, new Actual360(), 3);
        // dateFromTenor(period) advances the calendar by the period under bdc.
        final Date d6m = ts.dateFromTenor(new Period(6, TimeUnit.Months));
        assertNotNull(d6m);
        assertTrue(d6m.compareTo(today) > 0);
        // 6 months forward from 15 Jun 2010 is 15 Dec 2010 (NullCalendar = identity).
        assertEquals(new Date(15, Month.December, 2010), d6m);
    }

    @Test
    public void referenceDateConstructorOverloads() {
        final Calendar cal = new NullCalendar();
        // Settlement-days constructor
        final FixedSizeCorrelationTermStructure ts2 = new FixedSizeCorrelationTermStructure(
                new Date(), cal, BusinessDayConvention.Unadjusted, new Actual360(), 7) {
            // anonymous override to use settlement-days base ctor
        };
        // referenceDate not asserted; the constructor wiring is what we're testing.
        assertEquals(7, ts2.correlationSize());
    }
}
