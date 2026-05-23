/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.termstructures.yieldcurves;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.SimpleDayCounter;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.Discount;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.SwapRateHelper;
import org.jquantlib.termstructures.yieldcurves.UltimateForwardTermStructure;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-35 port of
 * {@code test-suite/ultimateforwardtermstructure.cpp} v1.42.1 (340 LOC,
 * 7 functional test cases + observability).
 *
 * <p>Production code lives in
 * {@link org.jquantlib.termstructures.yieldcurves.UltimateForwardTermStructure}.
 *
 * <p>Source: {@code test-suite/ultimateforwardtermstructure.cpp} v1.42.1
 * @ {@code 099987f0ca}.
 */
@SuppressWarnings("unchecked")
public class UltimateForwardTermStructureTest {

    //
    // ---- test-fixture (mirrors C++ struct CommonVars) ----
    //

    private static final class Datum {
        final int n;
        final TimeUnit units;
        final double rate;
        Datum(final int n, final TimeUnit units, final double rate) {
            this.n = n; this.units = units; this.rate = rate;
        }
    }

    private static final class LLFRWeight {
        final double ttm;
        final double weight;
        LLFRWeight(final double ttm, final double weight) {
            this.ttm = ttm; this.weight = weight;
        }
    }

    private static final class CommonVars {
        final Date today, settlement;
        final Calendar calendar;
        final int settlementDays;
        final Currency ccy;
        final BusinessDayConvention businessConvention;
        final DayCounter dayCount;
        final Frequency fixedFrequency;
        final Period floatingTenor;
        final IborIndex index;
        final RelinkableHandle<YieldTermStructure> ftkCurveHandle;
        final Quote ufrRate;
        final Period fsp;
        final double alpha;

        CommonVars() {
            settlementDays = 2;
            businessConvention = BusinessDayConvention.Unadjusted;
            dayCount = new SimpleDayCounter();
            calendar = new NullCalendar();
            ccy = new EURCurrency();
            fixedFrequency = Frequency.Annual;
            floatingTenor = new Period(6, TimeUnit.Months);

            ftkCurveHandle = new RelinkableHandle<YieldTermStructure>();

            index = new IborIndex(
                    "FTK_IDX", floatingTenor, settlementDays, ccy, calendar,
                    businessConvention, false, dayCount, ftkCurveHandle);

            // Equivalent rate of 2.3% Annual/Compounded re-expressed as
            // continuously-compounded annually-frequencied at t=1.
            final InterestRate ufr = new InterestRate(
                    0.023, dayCount, Compounding.Compounded, Frequency.Annual);
            ufrRate = new SimpleQuote(
                    ufr.equivalentRate(1.0, Compounding.Continuous, Frequency.Annual).rate());
            fsp = new Period(20, TimeUnit.Years);
            alpha = 0.1;

            today = calendar.adjust(new Date(29, Month.March, 2019));
            new Settings().setEvaluationDate(today);
            settlement = calendar.advance(today, settlementDays, TimeUnit.Days);

            final Datum[] swapData = new Datum[] {
                new Datum(1, TimeUnit.Years, -0.00315),
                new Datum(2, TimeUnit.Years, -0.00205),
                new Datum(3, TimeUnit.Years, -0.00144),
                new Datum(4, TimeUnit.Years, -0.00068),
                new Datum(5, TimeUnit.Years,  0.00014),
                new Datum(6, TimeUnit.Years,  0.00103),
                new Datum(7, TimeUnit.Years,  0.00194),
                new Datum(8, TimeUnit.Years,  0.00288),
                new Datum(9, TimeUnit.Years,  0.00381),
                new Datum(10, TimeUnit.Years, 0.00471),
                new Datum(12, TimeUnit.Years, 0.0063),
                new Datum(15, TimeUnit.Years, 0.00808),
                new Datum(20, TimeUnit.Years, 0.00973),
                new Datum(25, TimeUnit.Years, 0.01035),
                new Datum(30, TimeUnit.Years, 0.01055),
                new Datum(40, TimeUnit.Years, 0.0103),
                new Datum(50, TimeUnit.Years, 0.0103),
            };
            final RateHelper[] instruments = new RateHelper[swapData.length];
            for (int i = 0; i < swapData.length; i++) {
                instruments[i] = new SwapRateHelper(
                        swapData[i].rate,
                        new Period(swapData[i].n, swapData[i].units),
                        calendar, fixedFrequency, businessConvention,
                        dayCount, index);
            }

            @SuppressWarnings("rawtypes")
            final PiecewiseYieldCurve ftkCurve = new PiecewiseYieldCurve(
                    Discount.class, LogLinear.class, IterativeBootstrap.class,
                    settlement, instruments, dayCount);
            ftkCurve.enableExtrapolation();
            ftkCurveHandle.linkTo(ftkCurve);
        }
    }

    //
    // ---- helpers (mirror free functions in the C++ test-suite) ----
    //

    private static Quote calculateLLFR(
            final Handle<YieldTermStructure> ts, final Period fsp) {
        final double omega = 8.0 / 15.0;
        final double cutOff = ts.currentLink()
                .timeFromReference(ts.currentLink().referenceDate().add(fsp));

        final LLFRWeight[] weights = new LLFRWeight[] {
            new LLFRWeight(25.0, 1.0),
            new LLFRWeight(30.0, 0.5),
            new LLFRWeight(40.0, 0.25),
            new LLFRWeight(50.0, 0.125),
        };
        double llfr = 0.0;
        for (final LLFRWeight w : weights) {
            llfr += w.weight * ts.currentLink()
                    .forwardRate(cutOff, w.ttm,
                            Compounding.Continuous, Frequency.NoFrequency, true)
                    .rate();
        }
        return new SimpleQuote(omega * llfr);
    }

    private static double calculateExtrapolatedForward(
            final double t, final double fsp,
            final double llfr, final double ufr, final double alpha) {
        final double deltaT = t - fsp;
        final double beta = (1.0 - Math.exp(-alpha * deltaT)) / (alpha * deltaT);
        return ufr + (llfr - ufr) * beta;
    }

    private static void checkDutchBankRates(
            final Datum[] expectedRates,
            final Integer rounding,
            final Compounding compounding,
            final Frequency frequency,
            final double tolerance) {
        final CommonVars vars = new CommonVars();

        final Quote llfr = calculateLLFR(vars.ftkCurveHandle, vars.fsp);

        final YieldTermStructure ufrTs = new UltimateForwardTermStructure(
                vars.ftkCurveHandle,
                new Handle<Quote>(llfr),
                new Handle<Quote>(vars.ufrRate),
                vars.fsp, vars.alpha,
                rounding, compounding, frequency);

        for (final Datum d : expectedRates) {
            final Period p = new Period(d.n, d.units);
            final Date maturity = vars.settlement.add(p);
            final double actual = ufrTs.zeroRate(
                    maturity, vars.dayCount, compounding, frequency).rate();
            final double expected = d.rate;
            if (Math.abs(actual - expected) > tolerance) {
                fail(String.format(
                        "unable to reproduce zero yield rate from the UFR curve%n"
                        + "    calculated: %.5f%n    expected:   %.5f%n    tenor:       %s%n",
                        actual, expected, p));
            }
        }
    }


    //
    // ---- test cases ----
    //

    @Test
    public void testDutchCentralBankRates() {
        final Datum[] expected = new Datum[] {
            new Datum(10, TimeUnit.Years, 0.00477),
            new Datum(20, TimeUnit.Years, 0.01004),
            new Datum(30, TimeUnit.Years, 0.01223),
            new Datum(40, TimeUnit.Years, 0.01433),
            new Datum(50, TimeUnit.Years, 0.01589),
            new Datum(60, TimeUnit.Years, 0.01702),
            new Datum(70, TimeUnit.Years, 0.01785),
            new Datum(80, TimeUnit.Years, 0.01849),
            new Datum(90, TimeUnit.Years, 0.01899),
            new Datum(100, TimeUnit.Years, 0.01939),
        };
        // C++ default: no rounding, Compounded/Annual config, tolerance 1e-4.
        checkDutchBankRates(expected, null,
                Compounding.Compounded, Frequency.Annual, 1.0e-4);
    }

    @Test
    public void testDutchCentralBankRatesWithRounding() {
        final Datum[] expected = new Datum[] {
            new Datum(10, TimeUnit.Years, 0.005),
            new Datum(20, TimeUnit.Years, 0.01),
            new Datum(30, TimeUnit.Years, 0.012),
            new Datum(40, TimeUnit.Years, 0.014),
            new Datum(50, TimeUnit.Years, 0.016),
            new Datum(60, TimeUnit.Years, 0.017),
            new Datum(70, TimeUnit.Years, 0.018),
            new Datum(80, TimeUnit.Years, 0.018),
            new Datum(90, TimeUnit.Years, 0.019),
            new Datum(100, TimeUnit.Years, 0.019),
        };
        // C++: rounding=3, Compounded/Annual, tolerance 1e-12 (TIGHT).
        checkDutchBankRates(expected, Integer.valueOf(3),
                Compounding.Compounded, Frequency.Annual, 1.0e-12);
    }

    @Test
    public void testDutchCentralBankRatesWithRoundingAndContinuousCompounding() {
        final Datum[] expected = new Datum[] {
            new Datum(10, TimeUnit.Years, 0.00477),
            new Datum(20, TimeUnit.Years, 0.01002),
            new Datum(30, TimeUnit.Years, 0.01211),
            new Datum(40, TimeUnit.Years, 0.01417),
            new Datum(50, TimeUnit.Years, 0.01571),
            new Datum(60, TimeUnit.Years, 0.01683),
            new Datum(70, TimeUnit.Years, 0.01766),
            new Datum(80, TimeUnit.Years, 0.01829),
            new Datum(90, TimeUnit.Years, 0.01878),
            new Datum(100, TimeUnit.Years, 0.01917),
        };
        // C++: rounding=5, Continuous/NoFrequency, tolerance 1e-12 (TIGHT).
        checkDutchBankRates(expected, Integer.valueOf(5),
                Compounding.Continuous, Frequency.NoFrequency, 1.0e-12);
    }

    @Test
    public void testExtrapolatedForward() {
        final CommonVars vars = new CommonVars();
        final SimpleQuote llfr = new SimpleQuote(0.0125);
        final YieldTermStructure ufrTs = new UltimateForwardTermStructure(
                vars.ftkCurveHandle,
                new Handle<Quote>(llfr),
                new Handle<Quote>(vars.ufrRate),
                vars.fsp, vars.alpha);

        final double cutOff = ufrTs.timeFromReference(
                ufrTs.referenceDate().add(vars.fsp));

        final Period[] tenors = new Period[] {
            new Period(20, TimeUnit.Years),
            new Period(30, TimeUnit.Years),
            new Period(40, TimeUnit.Years),
            new Period(50, TimeUnit.Years),
            new Period(60, TimeUnit.Years),
            new Period(70, TimeUnit.Years),
            new Period(80, TimeUnit.Years),
            new Period(90, TimeUnit.Years),
            new Period(100, TimeUnit.Years),
        };
        final double tolerance = 1.0e-10;
        for (final Period p : tenors) {
            final Date maturity = vars.settlement.add(p);
            final double t = ufrTs.timeFromReference(maturity);
            final double actual = ufrTs.forwardRate(
                    cutOff, t, Compounding.Continuous, Frequency.NoFrequency, true).rate();
            final double expected = calculateExtrapolatedForward(
                    t, cutOff, llfr.value(), vars.ufrRate.value(), vars.alpha);
            if (Math.abs(actual - expected) > tolerance) {
                fail(String.format(
                        "unable to replicate the forward rate from the UFR curve%n"
                        + "    calculated: %.5f%n    expected:   %.5f%n    tenor:       %s%n",
                        actual, expected, p));
            }
        }
    }

    @Test
    public void testZeroRateAtFirstSmoothingPoint() {
        final CommonVars vars = new CommonVars();
        final SimpleQuote llfr = new SimpleQuote(0.0125);
        final YieldTermStructure ufrTs = new UltimateForwardTermStructure(
                vars.ftkCurveHandle,
                new Handle<Quote>(llfr),
                new Handle<Quote>(vars.ufrRate),
                vars.fsp, vars.alpha);
        final double cutOff = ufrTs.timeFromReference(
                ufrTs.referenceDate().add(vars.fsp));

        final double actual = ufrTs.zeroRate(
                cutOff, Compounding.Continuous, Frequency.NoFrequency, true).rate();
        final double expected = vars.ftkCurveHandle.currentLink().zeroRate(
                cutOff, Compounding.Continuous, Frequency.NoFrequency, true).rate();

        final double tolerance = 1.0e-10;
        assertEquals(
                "zero rate on First Smoothing Point should equal base curve's",
                expected, actual, tolerance);
    }

    @Test
    public void testThatInspectorsEqualToBaseCurve() {
        final CommonVars vars = new CommonVars();
        final SimpleQuote llfr = new SimpleQuote(0.0125);
        final YieldTermStructure ufrTs = new UltimateForwardTermStructure(
                vars.ftkCurveHandle,
                new Handle<Quote>(llfr),
                new Handle<Quote>(vars.ufrRate),
                vars.fsp, vars.alpha);

        assertEquals("day counter must match the base curve",
                vars.ftkCurveHandle.currentLink().dayCounter(),
                ufrTs.dayCounter());
        assertEquals("reference date must match the base curve",
                vars.ftkCurveHandle.currentLink().referenceDate(),
                ufrTs.referenceDate());
        assertNotEquals("max date on the UFR curve must NOT match the base curve",
                vars.ftkCurveHandle.currentLink().maxDate(),
                ufrTs.maxDate());
        assertTrue("max time on the UFR curve must NOT match the base curve",
                vars.ftkCurveHandle.currentLink().maxTime() != ufrTs.maxTime());
    }

    @Test
    public void testExceptionWhenFspLessOrEqualZero() {
        final CommonVars vars = new CommonVars();
        final SimpleQuote llfr = new SimpleQuote(0.0125);

        boolean thrownZero = false;
        try {
            new UltimateForwardTermStructure(
                    vars.ftkCurveHandle,
                    new Handle<Quote>(llfr),
                    new Handle<Quote>(vars.ufrRate),
                    new Period(0, TimeUnit.Years), vars.alpha);
        } catch (final Exception e) {
            thrownZero = true;
        }
        assertTrue("zero-length first smoothing point must throw", thrownZero);

        boolean thrownNeg = false;
        try {
            new UltimateForwardTermStructure(
                    vars.ftkCurveHandle,
                    new Handle<Quote>(llfr),
                    new Handle<Quote>(vars.ufrRate),
                    new Period(-1, TimeUnit.Years), vars.alpha);
        } catch (final Exception e) {
            thrownNeg = true;
        }
        assertTrue("negative-length first smoothing point must throw", thrownNeg);
    }

    @Test
    public void testObservability() {
        final CommonVars vars = new CommonVars();
        final SimpleQuote llfr = new SimpleQuote(0.0125);
        final Handle<Quote> llfrHandle = new Handle<Quote>(llfr);
        final SimpleQuote ufr = new SimpleQuote(0.02);
        final Handle<Quote> ufrHandle = new Handle<Quote>(ufr);
        final YieldTermStructure ufrTs = new UltimateForwardTermStructure(
                vars.ftkCurveHandle, llfrHandle, ufrHandle, vars.fsp, vars.alpha);

        final Flag flag = new Flag();
        ufrTs.addObserver(flag);
        llfr.setValue(0.012);
        if (!flag.isUp()) {
            fail("Observer was not notified of LLFR change.");
        }
        flag.lower();
        ufr.setValue(0.019);
        if (!flag.isUp()) {
            fail("Observer was not notified of UFR change.");
        }
    }
}
