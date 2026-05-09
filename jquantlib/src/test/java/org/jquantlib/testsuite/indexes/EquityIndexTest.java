/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Europe;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.EquityIndex;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Cross-validated tests for {@link EquityIndex} ported from
 * {@code test-suite/equityindex.cpp} v1.42.1 ({@code 099987f0ca}).
 *
 * <p>Phase 5d.5-EQ — un-ignores the 12 skeleton cases now that the
 * {@link EquityIndex} production class exists.
 *
 * <p>Tier: TIGHT (1e-8 absolute, mirroring C++ test tolerance).
 */
public class EquityIndexTest {

    private static final double TOL = 1.0e-8;

    /**
     * Mirrors C++ {@code CommonVars} struct from
     * {@code test-suite/equityindex.cpp:34-68}.
     */
    private static final class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final EquityIndex equityIndex;
        final RelinkableHandle<YieldTermStructure> interestHandle;
        final RelinkableHandle<YieldTermStructure> dividendHandle;
        final SimpleQuote spot;
        final RelinkableHandle<Quote> spotHandle;

        CommonVars(final boolean addTodaysFixing) {
            calendar = new Target();
            dayCount = new Actual365Fixed();

            interestHandle = new RelinkableHandle<YieldTermStructure>();
            dividendHandle = new RelinkableHandle<YieldTermStructure>();
            spotHandle = new RelinkableHandle<Quote>();

            equityIndex = new EquityIndex("eqIndex", calendar, new Europe.EURCurrency(),
                    interestHandle, dividendHandle, spotHandle);

            today = calendar.adjust(new Date(27, Month.January, 2023));
            equityIndex.clearFixings();

            if (addTodaysFixing) {
                equityIndex.addFixing(today, 8690.0);
            }

            new Settings().setEvaluationDate(today);

            interestHandle.linkTo(flatRate(0.03, dayCount));
            dividendHandle.linkTo(flatRate(0.01, dayCount));

            spot = new SimpleQuote(8700.0);
            spotHandle.linkTo(spot);
        }

        CommonVars() { this(true); }

        /**
         * Mirrors C++ {@code flatRate(Rate, DayCounter)} from
         * {@code test-suite/utilities.cpp:96-99} — settlement-relative
         * FlatForward against a NullCalendar.
         */
        static YieldTermStructure flatRate(final double rate, final DayCounter dc) {
            return new FlatForward(0, new NullCalendar(), rate, dc);
        }
    }

    @Test
    public void testTodaysFixing() {
        final CommonVars vars = new CommonVars();

        final double historicalIndex = 8690.0;
        final double todaysFixing = vars.equityIndex.fixing(vars.today);
        assertEquals("today's fixing should be equal to historical index",
                historicalIndex, todaysFixing, TOL);

        final double spot = 8700.0;
        final double forecastedFixing = vars.equityIndex.fixing(vars.today, true);
        assertEquals("today's fixing forecast should be equal to spot",
                spot, forecastedFixing, TOL);
    }

    @Test
    public void testTodaysFixingWithSpotAsProxy() {
        final CommonVars vars = new CommonVars(false);

        final double spot = 8700.0;
        final double fixing = vars.equityIndex.fixing(vars.today);
        assertEquals("today's fixing should be equal to spot when historical index not added",
                spot, fixing, TOL);
    }

    @Test
    public void testFixingForecast() {
        final CommonVars vars = new CommonVars();

        final Date forecastedDate = new Date(20, Month.May, 2030);

        final double forecast = vars.equityIndex.fixing(forecastedDate);
        final double expectedForecast =
                vars.spotHandle.currentLink().value() *
                vars.dividendHandle.currentLink().discount(forecastedDate) /
                vars.interestHandle.currentLink().discount(forecastedDate);

        assertEquals("could not replicate index forecast",
                expectedForecast, forecast, TOL);
    }

    @Test
    public void testFixingForecastWithoutDividend() {
        final CommonVars vars = new CommonVars();

        final Date forecastedDate = new Date(20, Month.May, 2030);

        final EquityIndex equityIndexExDiv = vars.equityIndex.clone(
                vars.interestHandle, new Handle<YieldTermStructure>(), vars.spotHandle);

        final double forecast = equityIndexExDiv.fixing(forecastedDate);
        final double expectedForecast =
                vars.spotHandle.currentLink().value() /
                vars.interestHandle.currentLink().discount(forecastedDate);

        assertEquals("could not replicate index forecast without dividend",
                expectedForecast, forecast, TOL);
    }

    @Test
    public void testFixingForecastWithoutSpot() {
        final CommonVars vars = new CommonVars();

        final Date forecastedDate = new Date(20, Month.May, 2030);

        final EquityIndex equityIndexExSpot = vars.equityIndex.clone(
                vars.interestHandle, vars.dividendHandle, new Handle<Quote>());

        final double forecast = equityIndexExSpot.fixing(forecastedDate);
        final double expectedForecast =
                equityIndexExSpot.pastFixing(vars.today) *
                vars.dividendHandle.currentLink().discount(forecastedDate) /
                vars.interestHandle.currentLink().discount(forecastedDate);

        assertEquals("could not replicate index forecast without spot handle",
                expectedForecast, forecast, TOL);
    }

    @Test
    public void testFixingForecastWithoutSpotAndHistoricalFixing() {
        final CommonVars vars = new CommonVars(false);

        final Date forecastedDate = new Date(20, Month.May, 2030);

        final EquityIndex equityIndexExSpot = vars.equityIndex.clone(
                vars.interestHandle, vars.dividendHandle, new Handle<Quote>());

        try {
            equityIndexExSpot.fixing(forecastedDate);
            fail("expected RuntimeException — missing both spot and historical index");
        } catch (final RuntimeException e) {
            assertTrue("unexpected exception message: " + e.getMessage(),
                    e.getMessage() != null
                    && e.getMessage().contains(
                            "Cannot forecast equity index, missing both spot and historical index"));
        }
    }

    @Test
    public void testSpotChange() {
        final CommonVars vars = new CommonVars();

        final SimpleQuote newSpot = new SimpleQuote(9000.0);
        vars.spotHandle.linkTo(newSpot);

        assertEquals("could not re-link spot quote to new value",
                newSpot.value(),
                vars.equityIndex.spot().currentLink().value(),
                TOL);

        vars.spotHandle.linkTo(vars.spot);

        assertEquals("could not re-link spot quote back to old value",
                vars.spot.value(),
                vars.equityIndex.spot().currentLink().value(),
                TOL);
    }

    @Test
    public void testErrorWhenInvalidFixingDate() {
        final CommonVars vars = new CommonVars();

        try {
            // 1-Jan-2023 is a TARGET holiday → not a valid fixing date.
            vars.equityIndex.fixing(new Date(1, Month.January, 2023));
            fail("expected RuntimeException — invalid fixing date");
        } catch (final RuntimeException e) {
            assertTrue("unexpected exception message: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("is not valid"));
        }
    }

    @Test
    public void testErrorWhenFixingMissing() {
        final CommonVars vars = new CommonVars();

        try {
            // 2-Jan-2023 is a valid TARGET business day in the past with no fixing.
            vars.equityIndex.fixing(new Date(2, Month.January, 2023));
            fail("expected RuntimeException — missing fixing");
        } catch (final RuntimeException e) {
            assertTrue("unexpected exception message: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("Missing eqIndex fixing"));
        }
    }

    @Test
    public void testErrorWhenInterestHandleMissing() {
        final CommonVars vars = new CommonVars();

        final Date forecastedDate = new Date(20, Month.May, 2030);

        final EquityIndex equityIndexExAll = vars.equityIndex.clone(
                new Handle<YieldTermStructure>(),
                new Handle<YieldTermStructure>(),
                new Handle<Quote>());

        try {
            equityIndexExAll.fixing(forecastedDate);
            fail("expected RuntimeException — null interest term structure");
        } catch (final RuntimeException e) {
            assertTrue("unexpected exception message: " + e.getMessage(),
                    e.getMessage() != null
                    && e.getMessage().contains(
                            "null interest rate term structure set to this instance of eqIndex"));
        }
    }

    @Test
    public void testFixingObservability() {
        final CommonVars vars = new CommonVars();

        final EquityIndex i1 = new EquityIndex(
                "observableEquityIndex", vars.calendar, new Europe.EURCurrency());
        i1.clearFixings();

        // Hold a strong reference to the observer so the WeakReference inside
        // Index doesn't garbage-collect it before the notification fires
        // (Phase 2x A.4 contract — see Index.delegatedObservable docstring).
        final boolean[] flag = new boolean[]{false};
        final org.jquantlib.util.Observer obs = new org.jquantlib.util.Observer() {
            @Override
            public void update() { flag[0] = true; }
        };
        i1.addObserver(obs);

        final EquityIndex i2 = new EquityIndex(
                "observableEquityIndex", vars.calendar, new Europe.EURCurrency());
        i2.addFixing(vars.today, 100.0);

        assertTrue("Observer was not notified of added equity index fixing", flag[0]);

        // Keep obs strongly reachable until end of test.
        if (obs == null) { throw new AssertionError(); }
    }

    @Test
    public void testNoErrorIfTodayIsNotBusinessDay() {
        final CommonVars vars = new CommonVars();

        final Date today = new Date(28, Month.January, 2023); // Saturday — TARGET holiday
        final Date forecastedDate = new Date(20, Month.May, 2030);

        new Settings().setEvaluationDate(today);

        // Mirrors the C++ BOOST_REQUIRE_NO_THROW: simply call and assert no
        // exception is raised. The clone is constructed for parity with C++,
        // even though the test exercises vars.equityIndex.
        vars.equityIndex.clone(vars.interestHandle, vars.dividendHandle, new Handle<Quote>());
        try {
            vars.equityIndex.fixing(forecastedDate);
        } catch (final LibraryException e) {
            fail("forecast on non-business-day evaluation date threw: " + e.getMessage());
        }
    }
}
