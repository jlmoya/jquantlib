/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.EquityCashFlow;
import org.jquantlib.cashflow.EquityQuantoCashFlowPricer;
import org.jquantlib.currencies.Europe;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.EquityIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Cross-validated tests for {@link EquityCashFlow} and
 * {@link EquityQuantoCashFlowPricer} ported from
 * {@code test-suite/equitycashflow.cpp} v1.42.1 ({@code 099987f0ca}).
 *
 * <p>Phase 5d.5-EQ — un-ignores the 8 skeleton cases now that the
 * {@code EquityCashFlow} family exists.
 *
 * <p>Tier: TIGHT (1e-6 absolute, mirroring C++ test tolerance).
 */
public class EquityCashFlowTest {

    private static final double TOL = 1.0e-6;

    /**
     * Mirrors C++ {@code CommonVars} struct from
     * {@code test-suite/equitycashflow.cpp:34-78}.
     */
    private static final class CommonVars {
        final Date today;
        final Calendar calendar;
        final DayCounter dayCount;
        final double notional;
        final EquityIndex equityIndex;
        final RelinkableHandle<YieldTermStructure> localCcyInterestHandle;
        final RelinkableHandle<YieldTermStructure> dividendHandle;
        final RelinkableHandle<YieldTermStructure> quantoCcyInterestHandle;
        final RelinkableHandle<BlackVolTermStructure> equityVolHandle;
        final RelinkableHandle<BlackVolTermStructure> fxVolHandle;
        final RelinkableHandle<Quote> spotHandle;
        final RelinkableHandle<Quote> correlationHandle;

        CommonVars() {
            calendar = new Target();
            dayCount = new Actual365Fixed();
            notional = 1.0e7;

            today = calendar.adjust(new Date(27, Month.January, 2023));
            new Settings().setEvaluationDate(today);

            localCcyInterestHandle = new RelinkableHandle<YieldTermStructure>();
            dividendHandle = new RelinkableHandle<YieldTermStructure>();
            quantoCcyInterestHandle = new RelinkableHandle<YieldTermStructure>();
            equityVolHandle = new RelinkableHandle<BlackVolTermStructure>();
            fxVolHandle = new RelinkableHandle<BlackVolTermStructure>();
            spotHandle = new RelinkableHandle<Quote>();
            correlationHandle = new RelinkableHandle<Quote>();

            equityIndex = new EquityIndex("eqIndex", calendar, new Europe.EURCurrency(),
                    localCcyInterestHandle, dividendHandle, spotHandle);
            equityIndex.clearFixings();
            equityIndex.addFixing(new Date(5, Month.January, 2023), 9010.0);
            equityIndex.addFixing(today, 8690.0);

            localCcyInterestHandle.linkTo(flatRate(0.0375, dayCount));
            dividendHandle.linkTo(flatRate(0.005, dayCount));
            quantoCcyInterestHandle.linkTo(flatRate(0.001, dayCount));

            equityVolHandle.linkTo(flatVol(0.4, dayCount));
            fxVolHandle.linkTo(flatVol(0.2, dayCount));

            spotHandle.linkTo(new SimpleQuote(8700.0));
            correlationHandle.linkTo(new SimpleQuote(0.4));
        }

        EquityCashFlow createEquityQuantoCashFlow(final EquityIndex index,
                                                  final Date start,
                                                  final Date end,
                                                  final boolean useQuantoPricer) {
            final EquityCashFlow cf = new EquityCashFlow(notional, index, start, end, end);
            if (useQuantoPricer) {
                final EquityQuantoCashFlowPricer pricer = new EquityQuantoCashFlowPricer(
                        quantoCcyInterestHandle, equityVolHandle, fxVolHandle, correlationHandle);
                cf.setPricer(pricer);
            }
            return cf;
        }

        EquityCashFlow createEquityQuantoCashFlow(final EquityIndex index,
                                                  final boolean useQuantoPricer) {
            final Date start = new Date(5, Month.January, 2023);
            final Date end = new Date(5, Month.April, 2023);
            return createEquityQuantoCashFlow(index, start, end, useQuantoPricer);
        }

        EquityCashFlow createEquityQuantoCashFlow(final boolean useQuantoPricer) {
            return createEquityQuantoCashFlow(equityIndex, useQuantoPricer);
        }
    }

    /** Settlement-relative FlatForward against NullCalendar. */
    private static YieldTermStructure flatRate(final double rate, final DayCounter dc) {
        return new FlatForward(0, new NullCalendar(), rate, dc);
    }

    /** Reference-date FlatForward (mirrors C++ flatRate(today, rate, dc)). */
    private static YieldTermStructure flatRateOn(final Date today, final double rate,
                                                 final DayCounter dc) {
        return new FlatForward(today, rate, dc);
    }

    /** Settlement-relative BlackConstantVol against NullCalendar. */
    private static BlackVolTermStructure flatVol(final double vol, final DayCounter dc) {
        return new BlackConstantVol(0, new NullCalendar(), vol, dc);
    }

    private static void checkRaisedError(final EquityCashFlow cf, final String expectedFragment) {
        try {
            cf.amount();
            fail("expected RuntimeException containing: " + expectedFragment);
        } catch (final RuntimeException e) {
            assertTrue("unexpected exception message: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains(expectedFragment));
        }
    }

    private static void bumpMarketData(final CommonVars vars) {
        vars.localCcyInterestHandle.linkTo(flatRate(0.04, vars.dayCount));
        vars.dividendHandle.linkTo(flatRate(0.01, vars.dayCount));
        vars.quantoCcyInterestHandle.linkTo(flatRate(0.03, vars.dayCount));
        vars.equityVolHandle.linkTo(flatVol(0.45, vars.dayCount));
        vars.fxVolHandle.linkTo(flatVol(0.25, vars.dayCount));
        vars.spotHandle.linkTo(new SimpleQuote(8710.0));
    }

    private static void checkQuantoCorrection(final boolean includeDividend, final boolean bumpData) {
        final CommonVars vars = new CommonVars();
        final EquityIndex equityIndex = includeDividend
                ? vars.equityIndex
                : vars.equityIndex.clone(vars.localCcyInterestHandle,
                        new Handle<YieldTermStructure>(), vars.spotHandle);

        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(equityIndex, true);

        if (bumpData) {
            bumpMarketData(vars);
        }

        final double strike = vars.equityIndex.fixing(cf.fixingDate());
        final double indexStart = vars.equityIndex.fixing(cf.baseDate());

        final double time = vars.localCcyInterestHandle.currentLink().timeFromReference(cf.fixingDate());
        final double rf = vars.localCcyInterestHandle.currentLink()
                .zeroRate(time, Compounding.Continuous, Frequency.NoFrequency, true).rate();
        final double q = includeDividend
                ? vars.dividendHandle.currentLink()
                        .zeroRate(time, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                : 0.0;
        final double eqVol = vars.equityVolHandle.currentLink().blackVol(cf.fixingDate(), strike, true);
        final double fxVol = vars.fxVolHandle.currentLink().blackVol(cf.fixingDate(), 1.0, true);
        final double rho = vars.correlationHandle.currentLink().value();
        final double spot = vars.spotHandle.currentLink().value();

        final double quantoForward = spot * Math.exp((rf - q - rho * eqVol * fxVol) * time);
        final double expectedAmount = (quantoForward / indexStart - 1.0) * vars.notional;

        final double actualAmount = cf.amount();
        assertEquals("could not replicate equity quanto correction"
                        + " (includeDividend=" + includeDividend + ", bumpData=" + bumpData + ")",
                expectedAmount, actualAmount, TOL);
    }

    @Test
    public void testSimpleEquityCashFlow() {
        final CommonVars vars = new CommonVars();

        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(false);

        final double indexStart = vars.equityIndex.fixing(cf.baseDate());
        final double indexEnd = vars.equityIndex.fixing(cf.fixingDate());

        final double expectedAmount = (indexEnd / indexStart - 1.0) * vars.notional;
        final double actualAmount = cf.amount();
        assertEquals("could not replicate simple equity quanto cash flow",
                expectedAmount, actualAmount, TOL);
    }

    @Test
    public void testQuantoCorrection() {
        checkQuantoCorrection(true, false);
        checkQuantoCorrection(false, false);
        // Observer notification on data bump
        checkQuantoCorrection(false, true);
    }

    @Test
    public void testErrorWhenBaseDateAfterFixingDate() {
        final CommonVars vars = new CommonVars();

        final Date end = new Date(5, Month.January, 2023);
        final Date start = new Date(5, Month.April, 2023);

        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(
                vars.equityIndex, start, end, true);
        checkRaisedError(cf, "Fixing date cannot fall before base date.");
    }

    @Test
    public void testErrorWhenQuantoCurveHandleIsEmpty() {
        final CommonVars vars = new CommonVars();
        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(true);
        vars.quantoCcyInterestHandle.linkTo(null);
        checkRaisedError(cf, "Quanto currency term structure handle cannot be empty.");
    }

    @Test
    public void testErrorWhenEquityVolHandleIsEmpty() {
        final CommonVars vars = new CommonVars();
        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(true);
        vars.equityVolHandle.linkTo(null);
        checkRaisedError(cf, "Equity volatility term structure handle cannot be empty.");
    }

    @Test
    public void testErrorWhenFXVolHandleIsEmpty() {
        final CommonVars vars = new CommonVars();
        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(true);
        vars.fxVolHandle.linkTo(null);
        checkRaisedError(cf, "FX volatility term structure handle cannot be empty.");
    }

    @Test
    public void testErrorWhenCorrelationHandleIsEmpty() {
        final CommonVars vars = new CommonVars();
        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(true);
        vars.correlationHandle.linkTo(null);
        checkRaisedError(cf, "Correlation handle cannot be empty.");
    }

    @Test
    public void testErrorWhenInconsistentMarketDataReferenceDate() {
        final CommonVars vars = new CommonVars();
        final EquityCashFlow cf = vars.createEquityQuantoCashFlow(true);
        // Re-link quanto-currency curve with a *different* reference date.
        vars.quantoCcyInterestHandle.linkTo(
                flatRateOn(new Date(26, Month.January, 2023), 0.02, vars.dayCount));
        checkRaisedError(cf,
                "Quanto currency term structure, equity and FX volatility need to have the same "
                        + "reference date.");
    }
}
