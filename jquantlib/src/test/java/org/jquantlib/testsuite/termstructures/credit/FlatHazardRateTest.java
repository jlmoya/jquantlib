/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Smoke tests for FlatHazardRate (Phase 3a L0).
 Cross-validated against the C++ closed forms:
   S(t) = exp(-h t)
   p(d1,d2) = S(d1) - S(d2)
*/
package org.jquantlib.testsuite.termstructures.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Java port of the C++ test-suite cases that exercise FlatHazardRate without
 * needing CDS support: {@code testDefaultProbability} and
 * {@code testFlatHazardRate} (both inlined here as L0 smoke tests).
 *
 * <p>Phase 3a L0 — first credit-subsystem tests. Validates that:
 * <ul>
 *   <li>{@code FlatHazardRate.survivalProbability(t)} matches {@code exp(-h t)}.
 *   <li>{@code defaultProbability(d1, d2)} matches {@code p(d2) - p(d1)}.
 *   <li>Date- and time-based accessors agree.
 * </ul>
 */
public class FlatHazardRateTest {

    @Test
    public void flatHazardRate_closedFormSurvival() {
        final double hazardRate = 0.0100;
        final DayCounter dc = new Actual360();
        final Calendar cal = new Target();
        final int n = 20;
        final double tol = 1.0e-10;

        final Date today = new Settings().evaluationDate();
        Date startDate = today;
        Date endDate = startDate;

        final FlatHazardRate flat = new FlatHazardRate(today, hazardRate, dc);

        for (int i = 0; i < n; ++i) {
            endDate = cal.advance(endDate, new Period(1, TimeUnit.Years));
            final double t = dc.yearFraction(startDate, endDate);
            final double expected = 1.0 - Math.exp(-hazardRate * t);
            final double computed = flat.defaultProbability(t);
            assertEquals("year " + (i + 1), expected, computed, tol);
        }
    }

    @Test
    public void flatHazardRate_dateVsTimeConsistent() {
        final double hazardRate = 0.0100;
        final Handle<Quote> q = new Handle<Quote>(new SimpleQuote(hazardRate));
        final DayCounter dc = new Actual360();
        final Calendar cal = new Target();
        final int n = 20;
        final double tol = 1.0e-10;

        final Date today = new Settings().evaluationDate();
        Date startDate = today;
        Date endDate = startDate;

        final FlatHazardRate flat = new FlatHazardRate(today, q, dc);

        for (int i = 0; i < n; ++i) {
            startDate = endDate;
            endDate = cal.advance(endDate, new Period(1, TimeUnit.Years));

            final double pStart = flat.defaultProbability(startDate);
            final double pEnd = flat.defaultProbability(endDate);
            final double pBetweenComputed = flat.defaultProbability(startDate, endDate);
            final double pBetween = pEnd - pStart;
            assertEquals("p(d1,d2) " + i, pBetween, pBetweenComputed, tol);

            final double t2 = dc.yearFraction(today, endDate);
            assertEquals("p(t)/p(d) " + i,
                    flat.defaultProbability(t2),
                    flat.defaultProbability(endDate),
                    tol);

            final double t1 = dc.yearFraction(today, startDate);
            assertEquals("p(t1,t2)/p(d1,d2) " + i,
                    flat.defaultProbability(t1, t2),
                    flat.defaultProbability(startDate, endDate),
                    tol);
        }
    }

    @Test
    public void flatHazardRate_hazardRateAccessor() {
        final double h = 0.0250;
        final Date today = new Date(15, Month.July, 2023);
        final FlatHazardRate flat = new FlatHazardRate(today, h, new Actual360());
        // hazardRate(time) constant should equal the input.
        assertEquals(h, flat.hazardRate(0.5), 1.0e-15);
        assertEquals(h, flat.hazardRate(5.0), 1.0e-15);
    }

    @Test
    public void flatHazardRate_maxDateIsInfinite() {
        final Date today = new Date(15, Month.July, 2023);
        final FlatHazardRate flat = new FlatHazardRate(today, 0.01, new Actual360());
        // C++: maxDate() == Date::maxDate().
        assertTrue("max date past today", flat.maxDate().gt(today));
    }
}
