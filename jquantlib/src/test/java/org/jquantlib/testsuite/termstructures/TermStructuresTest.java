/*
 Copyright (C) 2007 Richard Gomes

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

/*
Copyright (C) 2003 RiskMap srl

This file is part of QuantLib, a free-software/open-source library
for financial quantitative analysts and developers - http://quantlib.org/

QuantLib is free software: you can redistribute it and/or modify it
under the terms of the QuantLib license.  You should have received a
copy of the license along with this program; if not, please email
<quantlib-dev@lists.sf.net>. The license is also available online at
<http://quantlib.org/license.shtml>.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.testsuite.termstructures;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.math.Closeness;
import org.jquantlib.math.interpolations.BackwardFlatInterpolation;
import org.jquantlib.math.interpolations.factories.BackwardFlat;
import org.jquantlib.math.interpolations.factories.ForwardFlat;
import org.jquantlib.math.interpolations.factories.Linear;
import org.jquantlib.math.interpolations.factories.LogLinear;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.AbstractYieldTermStructure;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.InterestRate;
import org.jquantlib.termstructures.IterativeBootstrap;
import org.jquantlib.termstructures.RateHelper;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.DepositRateHelper;
import org.jquantlib.termstructures.yieldcurves.Discount;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.termstructures.yieldcurves.ForwardSpreadedTermStructure;
import org.jquantlib.termstructures.yieldcurves.ImpliedTermStructure;
import org.jquantlib.termstructures.yieldcurves.InterpolatedPiecewiseForwardSpreadedTermStructure;
import org.jquantlib.termstructures.yieldcurves.PiecewiseYieldCurve;
import org.jquantlib.termstructures.yieldcurves.SwapRateHelper;
import org.jquantlib.termstructures.yieldcurves.ZeroSpreadedTermStructure;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.NullCalendar;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;


public class TermStructuresTest {

    private final Calendar calendar;
    private final int settlementDays;
    private final YieldTermStructure termStructure;
    private final YieldTermStructure dummyTermStructure;


    public TermStructuresTest() {
        QL.info("::::: "+this.getClass().getSimpleName()+" :::::");

        this.calendar = new Target();
        this.settlementDays = 2;

        // Mirror C++ test-suite/termstructures.cpp CommonVars setup:
        //   calendar = TARGET(); settlementDays = 2;
        //   today = calendar.adjust(Date::todaysDate()); Settings::instance().evaluationDate() = today;
        //   settlement = calendar.advance(today, settlementDays, Days);
        //   Datum depositData[] = { 1m 4.581, 2m 4.573, 3m 4.557, 6m 4.496, 9m 4.490 }
        //   Datum swapData[]    = { 1y 4.54, 5y 4.99, 10y 5.47, 20y 5.89, 30y 5.96 }
        //   PiecewiseYieldCurve<Discount,LogLinear>(settlement, instruments, Actual360())

        final Date today = this.calendar.adjust(Date.todaysDate());
        new Settings().setEvaluationDate(today);
        final Date settlement = this.calendar.advance(today, this.settlementDays, TimeUnit.Days);

        final Datum depositData[] = new Datum[] {
                new Datum(1, TimeUnit.Months, 4.581),
                new Datum(2, TimeUnit.Months, 4.573),
                new Datum(3, TimeUnit.Months, 4.557),
                new Datum(6, TimeUnit.Months, 4.496),
                new Datum(9, TimeUnit.Months, 4.490)
        };
        final Datum swapData[] = new Datum[] {
                new Datum(1, TimeUnit.Years, 4.54),
                new Datum(5, TimeUnit.Years, 4.99),
                new Datum(10, TimeUnit.Years, 5.47),
                new Datum(20, TimeUnit.Years, 5.89),
                new Datum(30, TimeUnit.Years, 5.96)
        };
        final int deposits = depositData.length;
        final int swaps = swapData.length;

        final RateHelper[] instruments = new RateHelper[deposits + swaps];
        for (int i = 0; i < deposits; i++) {
            instruments[i] = new DepositRateHelper(
                    depositData[i].rate / 100,
                    new Period(depositData[i].n, depositData[i].units),
                    this.settlementDays, this.calendar,
                    BusinessDayConvention.ModifiedFollowing, true,
                    new Actual360());
        }
        final IborIndex index = new IborIndex(
                "dummy",
                new Period(6, TimeUnit.Months),
                this.settlementDays,
                new Currency(),
                this.calendar,
                BusinessDayConvention.ModifiedFollowing,
                false,
                new Actual360());
        for (int i = 0; i < swaps; i++) {
            instruments[i + deposits] = new SwapRateHelper(
                    swapData[i].rate / 100,
                    new Period(swapData[i].n, swapData[i].units),
                    this.calendar, Frequency.Annual,
                    BusinessDayConvention.Unadjusted,
                    new Thirty360(),
                    index);
        }

        this.termStructure = new PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>(
                Discount.class, LogLinear.class, IterativeBootstrap.class,
                settlement, instruments, new Actual360());
        this.dummyTermStructure = new PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>(
                Discount.class, LogLinear.class, IterativeBootstrap.class,
                settlement, instruments, new Actual360());
    }


    @Test
    public void testReferenceChange() {
        QL.info("Testing term structure against evaluation date change...");

        final YieldTermStructure localTermStructure = new FlatForward(settlementDays, new NullCalendar(), 0.03, new Actual360());

        final int days[] = { 10, 30, 60, 120, 360, 720 };
        /*@DiscountFactor*/ final double[] expected = new /*@DiscountFactor*/ double[days.length];

        final Date today = new Settings().evaluationDate();

        for (int i=0; i<days.length; i++) {
            final Date anotherDay = today.add(days[i]);
            expected[i] = localTermStructure.discount(anotherDay);
        }

        final Date nextMonth = today.add(30);
        new Settings().setEvaluationDate(nextMonth);
        /*@DiscountFactor*/ final double[] calculated = new /*@DiscountFactor*/ double[days.length];

        for (int i=0; i<days.length; i++) {
            final Date anotherDay = nextMonth.add(days[i]);
            calculated[i] = localTermStructure.discount(anotherDay);
        }

        for (int i=0; i<days.length; i++) {
            if (!Closeness.isClose(expected[i],calculated[i])) {
                fail("\n  Discount at " + days[i] + " days:\n"
                        + "    before date change: " + expected[i] + "\n"
                        + "    after date change:  " + calculated[i]);
            }
        }
    }


    /**
     * C++ ref: test-suite/termstructures.cpp BOOST_AUTO_TEST_CASE(testImplied).
     * Tolerance 1.0e-10 per C++.
     */
    @Test
    public void testImplied() {
        QL.info("Testing consistency of implied term structure...");

        final double tolerance = 1.0e-10;
        final Date today = new Settings().evaluationDate();
        final Date newToday = today.add(new Period(3, TimeUnit.Years));
        final Date newSettlement = calendar.advance(newToday, settlementDays, TimeUnit.Days);
        final Date testDate = newSettlement.add(new Period(5, TimeUnit.Years));

        final YieldTermStructure implied = new ImpliedTermStructure<YieldTermStructure>(
                new Handle<YieldTermStructure>(termStructure), newSettlement);

        final /*@DiscountFactor*/ double baseDiscount = termStructure.discount(newSettlement);
        final /*@DiscountFactor*/ double discount = termStructure.discount(testDate);
        final /*@DiscountFactor*/ double impliedDiscount = implied.discount(testDate);

        if (Math.abs(discount - baseDiscount * impliedDiscount) > tolerance) {
            fail("unable to reproduce discount from implied curve\n"
                    + "    calculated: " + (baseDiscount * impliedDiscount) + "\n"
                    + "    expected:   " + discount);
        }
    }


    /**
     * C++ ref: test-suite/termstructures.cpp BOOST_AUTO_TEST_CASE(testImpliedObs).
     * Body-filled to match C++: build a {@link RelinkableHandle}, attach observer,
     * then {@link RelinkableHandle#linkTo(Object)} and assert the observer fired.
     * <p>
     * Historical note: the prior in-tree {@code testImpliedObs} used a
     * {@code FlatForward} placeholder because {@link PiecewiseYieldCurve} was not
     * yet wired in. With the curve now bootstrapped in the constructor we follow
     * the C++ code verbatim.
     */
    @Test
    public void testImpliedObs() {
        QL.info("Testing observability of implied term structure...");

        final Date today = new Settings().evaluationDate();
        final Date newToday = today.add(new Period(3, TimeUnit.Years));
        final Date newSettlement = calendar.advance(newToday, settlementDays, TimeUnit.Days);

        final RelinkableHandle<YieldTermStructure> h = new RelinkableHandle<YieldTermStructure>(
                new AbstractYieldTermStructure() {
                    @Override
                    protected double discountImpl(final double t) {
                        throw new UnsupportedOperationException();
                    }
                    @Override
                    public Date maxDate() {
                        throw new UnsupportedOperationException();
                    }
                } );

        final YieldTermStructure implied = new ImpliedTermStructure<YieldTermStructure>(h, newSettlement);

        final Flag flag = new Flag();
        implied.addObserver(flag);

        h.linkTo(termStructure);
        if (!flag.isUp()) {
            fail("Observer was not notified of term structure change");
        }
    }


    /**
     * C++ ref: test-suite/termstructures.cpp BOOST_AUTO_TEST_CASE(testFSpreaded).
     * Tolerance 1.0e-10 per C++.
     */
    @Test
    public void testFSpreaded() {
        QL.info("Testing consistency of forward-spreaded term structure...");

        final double tolerance = 1.0e-10;
        final Quote me = new SimpleQuote(0.01);
        final Handle<Quote> mh = new Handle<Quote>(me);

        final YieldTermStructure spreaded = new ForwardSpreadedTermStructure(
                new Handle<YieldTermStructure>(termStructure), mh);
        final Date testDate = termStructure.referenceDate().add(new Period(5, TimeUnit.Years));
        final DayCounter tsdc = termStructure.dayCounter();
        final DayCounter sprdc = spreaded.dayCounter();

        final /*@Rate*/ double forward = termStructure.forwardRate(
                testDate, testDate, tsdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final /*@Rate*/ double spreadedForward = spreaded.forwardRate(
                testDate, testDate, sprdc, Compounding.Continuous, Frequency.NoFrequency).rate();

        if (Math.abs(forward - (spreadedForward - me.value())) > tolerance) {
            fail("unable to reproduce forward from spreaded curve\n"
                    + "    calculated: " + (spreadedForward - me.value()) + "\n"
                    + "    expected:   " + forward);
        }
    }


    /**
     * C++ ref: test-suite/termstructures.cpp BOOST_AUTO_TEST_CASE(testFSpreadedObs).
     * Asserts the observer fires on (a) underlying-curve relink and (b) spread change.
     */
    @Test
    public void testFSpreadedObs() {
        QL.info("Testing observability of forward-spreaded term structure...");

        final SimpleQuote me = new SimpleQuote(0.01);
        final Handle<Quote> mh = new Handle<Quote>(me);
        final RelinkableHandle<YieldTermStructure> h = new RelinkableHandle<YieldTermStructure>();
        final YieldTermStructure spreaded = new ForwardSpreadedTermStructure(h, mh);

        final Flag flag = new Flag();
        spreaded.addObserver(flag);
        h.linkTo(termStructure);
        if (!flag.isUp()) {
            fail("Observer was not notified of term structure change");
        }

        flag.lower();
        me.setValue(0.005);
        if (!flag.isUp()) {
            fail("Observer was not notified of spread change");
        }
    }


    /**
     * C++ ref: test-suite/termstructures.cpp BOOST_AUTO_TEST_CASE(testZSpreaded).
     * Tolerance 1.0e-10 per C++.
     */
    @Test
    public void testZSpreaded() {
        QL.info("Testing consistency of zero-spreaded term structure...");

        final double tolerance = 1.0e-10;
        final Quote me = new SimpleQuote(0.01);
        final Handle<Quote> mh = new Handle<Quote>(me);

        final YieldTermStructure spreaded = new ZeroSpreadedTermStructure(
                new Handle<YieldTermStructure>(termStructure), mh);
        final Date testDate = termStructure.referenceDate().add(new Period(5, TimeUnit.Years));
        final DayCounter rfdc = termStructure.dayCounter();

        final /*@Rate*/ double zero = termStructure.zeroRate(
                testDate, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();
        final /*@Rate*/ double spreadedZero = spreaded.zeroRate(
                testDate, rfdc, Compounding.Continuous, Frequency.NoFrequency).rate();

        if (Math.abs(zero - (spreadedZero - me.value())) > tolerance) {
            fail("unable to reproduce zero yield from spreaded curve\n"
                    + "    calculated: " + (spreadedZero - me.value()) + "\n"
                    + "    expected:   " + zero);
        }
    }


    /**
     * C++ ref: test-suite/termstructures.cpp BOOST_AUTO_TEST_CASE(testZSpreadedObs).
     * Like {@link #testFSpreadedObs} but with the {@link ZeroSpreadedTermStructure}.
     * C++ seeds the {@link RelinkableHandle} with {@code dummyTermStructure} so the
     * spread-on-curve can be constructed before the live link arrives.
     */
    @Test
    public void testZSpreadedObs() {
        QL.info("Testing observability of zero-spreaded term structure...");

        final SimpleQuote me = new SimpleQuote(0.01);
        final Handle<Quote> mh = new Handle<Quote>(me);

        final RelinkableHandle<YieldTermStructure> h = new RelinkableHandle<YieldTermStructure>(dummyTermStructure);
        final YieldTermStructure spreaded = new ZeroSpreadedTermStructure(h, mh);

        final Flag flag = new Flag();
        spreaded.addObserver(flag);
        h.linkTo(termStructure);
        if (!flag.isUp()) {
            fail("Observer was not notified of term structure change");
        }

        flag.lower();
        me.setValue(0.005);
        if (!flag.isUp()) {
            fail("Observer was not notified of spread change");
        }
    }



    /**
     * Faithful port of {@code test-suite/termstructures.cpp:302}
     * {@code BOOST_AUTO_TEST_CASE(testCreateWithNullUnderlying)}.
     * Verifies that {@link ZeroSpreadedTermStructure} accepts an empty
     * {@link RelinkableHandle} at construction and only requires a live
     * underlying when the structure is actually used.
     */
    @Test
    public void testCreateWithNullUnderlying() {
        QL.info("Testing that a zero-spreaded curve can be created with a null underlying curve...");

        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.01));
        final RelinkableHandle<YieldTermStructure> underlying = new RelinkableHandle<YieldTermStructure>();
        // this shouldn't throw
        final YieldTermStructure spreaded = new ZeroSpreadedTermStructure(underlying, spread);
        // if we do this, the curve can work
        underlying.linkTo(termStructure);
        // check that we can use it
        spreaded.referenceDate();
    }


    /**
     * Faithful port of {@code test-suite/termstructures.cpp:518}
     * {@code BOOST_AUTO_TEST_CASE(testLinkToNullUnderlying)}. Build the
     * spreaded curve over a live underlying, use it, then reset the
     * underlying handle: the structure must not throw as long as it isn't
     * touched after reset.
     */
    @Test
    public void testLinkToNullUnderlying() {
        QL.info("Testing that an underlying curve can be relinked to a null underlying curve...");

        final Handle<Quote> spread = new Handle<Quote>(new SimpleQuote(0.01));
        final RelinkableHandle<YieldTermStructure> underlying = new RelinkableHandle<YieldTermStructure>(termStructure);
        final YieldTermStructure spreaded = new ZeroSpreadedTermStructure(underlying, spread);
        // check that we can use it
        spreaded.referenceDate();
        // if we do this, the curve can't work anymore. But it shouldn't
        // throw as long as we don't try to use it.
        underlying.linkTo(null);
    }


    /**
     * Phase 5e.5b-CFC-d-40 regression: PiecewiseYieldCurve.forwardRate /
     * zeroRate / parRate overrides must invoke calculate() so the lazy
     * bootstrap runs on the first read. Before the fix these delegated
     * straight to the (unbootstrapped) baseCurve and NPE'd on a fresh
     * curve. We build brand-new PiecewiseYieldCurves here and call
     * forwardRate/zeroRate BEFORE any other trigger (no maxDate(), no
     * discount()) - the calls must succeed and return finite values.
     */
    @Test
    public void testLazyBootstrapTriggers() {
        QL.info("Testing lazy-bootstrap triggers on PiecewiseYieldCurve.forwardRate/zeroRate...");

        final Calendar cal = new Target();
        final int settle = 2;
        final Date today = cal.adjust(Date.todaysDate());
        new Settings().setEvaluationDate(today);
        final Date settlement = cal.advance(today, settle, TimeUnit.Days);

        final Datum[] deposits = new Datum[] {
                new Datum(1, TimeUnit.Months, 4.581),
                new Datum(2, TimeUnit.Months, 4.573),
                new Datum(3, TimeUnit.Months, 4.557),
        };

        final RateHelper[] helpers = new RateHelper[deposits.length];
        for (int i = 0; i < deposits.length; ++i) {
            helpers[i] = new DepositRateHelper(
                    deposits[i].rate / 100.0,
                    new Period(deposits[i].n, deposits[i].units),
                    settle,
                    cal,
                    BusinessDayConvention.ModifiedFollowing,
                    true,
                    new Actual360());
        }

        // Curve A: forwardRate(Date,Date,...) is the very first call.
        final YieldTermStructure freshA = new PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>(
                Discount.class, LogLinear.class, IterativeBootstrap.class,
                settlement, helpers, new Actual360());

        final Date d = settlement.add(new Period(1, TimeUnit.Months));
        final DayCounter dc = new Actual360();
        final double fwd = freshA.forwardRate(
                d, d, dc, Compounding.Continuous, Frequency.NoFrequency).rate();
        if (Double.isNaN(fwd) || Double.isInfinite(fwd)) {
            fail("forwardRate on fresh PiecewiseYieldCurve returned non-finite: " + fwd);
        }

        // Curve B: zeroRate(Date,...) is the very first call.
        final YieldTermStructure freshB = new PiecewiseYieldCurve<Discount, LogLinear, IterativeBootstrap>(
                Discount.class, LogLinear.class, IterativeBootstrap.class,
                settlement, helpers, new Actual360());

        final double z = freshB.zeroRate(
                d, dc, Compounding.Continuous, Frequency.NoFrequency).rate();
        if (Double.isNaN(z) || Double.isInfinite(z)) {
            fail("zeroRate on fresh PiecewiseYieldCurve returned non-finite: " + z);
        }
    }


    /**
     * Faithful port of {@code test-suite/termstructures.cpp:320}
     * {@code BOOST_AUTO_TEST_CASE(testLinearInterpolationSpreadedForwardRate)}.
     * Tolerance 1e-9 per C++.
     */
    @Test
    public void testLinearInterpolationSpreadedForwardRate() {
        QL.info("Testing linear interpolation of forward rates between two dates...");

        final DayCounter dc = termStructure.dayCounter();
        final Date today = new Settings().evaluationDate();
        final Date settlement = calendar.advance(today, settlementDays, TimeUnit.Days);

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        @SuppressWarnings("unchecked")
        final Handle<Quote>[] spreads = (Handle<Quote>[]) new Handle<?>[] {
                new Handle<Quote>(spread1), new Handle<Quote>(spread2) };

        final Date[] spreadDates = new Date[] {
                calendar.advance(today, 100, TimeUnit.Days),
                calendar.advance(today, 150, TimeUnit.Days)
        };

        final Date interpolationDate = calendar.advance(today, 120, TimeUnit.Days);

        final YieldTermStructure spreaded = new InterpolatedPiecewiseForwardSpreadedTermStructure(
                new Handle<YieldTermStructure>(termStructure), spreads, spreadDates, new Linear());

        final Date d0 = calendar.advance(today, 100, TimeUnit.Days);
        final Date d1 = calendar.advance(today, 150, TimeUnit.Days);
        final Date d2 = calendar.advance(today, 120, TimeUnit.Days);

        final double time0 = dc.yearFraction(settlement, d0);
        final double time1 = dc.yearFraction(settlement, d1);
        final double time2 = dc.yearFraction(settlement, d2);

        final double m = (0.03 - 0.02) / (time1 - time0);

        final double t = dc.yearFraction(settlement, interpolationDate);
        final double expectedForward = termStructure.forwardRate(
                t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                + (m * (time2 - time0) + 0.02);
        final double interpolatedForward = spreaded.forwardRate(
                t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate();

        final double tolerance = 1e-9;
        if (Math.abs(interpolatedForward - expectedForward) > tolerance) {
            fail("unable to reproduce interpolated forward rate\n"
                    + "    calculated: " + interpolatedForward + "\n"
                    + "    expected:   " + expectedForward);
        }
    }


    /**
     * Faithful port of {@code test-suite/termstructures.cpp:373}
     * {@code BOOST_AUTO_TEST_CASE(testForwardFlatInterpolationSpreadedForwardRate)}.
     * Tolerance 1e-9 per C++.
     */
    @Test
    public void testForwardFlatInterpolationSpreadedForwardRate() {
        QL.info("Testing forward flat interpolation of forward rates between two dates...");

        final DayCounter dc = termStructure.dayCounter();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        @SuppressWarnings("unchecked")
        final Handle<Quote>[] spreads = (Handle<Quote>[]) new Handle<?>[] {
                new Handle<Quote>(spread1), new Handle<Quote>(spread2) };

        final Date[] spreadDates = new Date[] {
                calendar.advance(today, 75, TimeUnit.Days),
                calendar.advance(today, 260, TimeUnit.Days)
        };

        final Date interpolationDate = calendar.advance(today, 100, TimeUnit.Days);

        final YieldTermStructure spreaded = new InterpolatedPiecewiseForwardSpreadedTermStructure(
                new Handle<YieldTermStructure>(termStructure), spreads, spreadDates, new ForwardFlat());

        final double t = dc.yearFraction(today, interpolationDate);
        final double expectedForward = termStructure.forwardRate(
                t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                + spread1.value();
        final double interpolatedForward = spreaded.forwardRate(
                t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate();

        final double tolerance = 1e-9;
        if (Math.abs(interpolatedForward - expectedForward) > tolerance) {
            fail("unable to reproduce interpolated forward rate\n"
                    + "    calculated: " + interpolatedForward + "\n"
                    + "    expected:   " + expectedForward);
        }
    }


    /**
     * Faithful port of {@code test-suite/termstructures.cpp:416}
     * {@code BOOST_AUTO_TEST_CASE(testBackwardFlatInterpolationSpreadedForwardRate)}.
     * Tolerance 1e-9 per C++.
     */
    @Test
    public void testBackwardFlatInterpolationSpreadedForwardRate() {
        QL.info("Testing backward flat interpolation of forward rates between two dates...");

        final DayCounter dc = termStructure.dayCounter();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final SimpleQuote spread3 = new SimpleQuote(0.04);
        @SuppressWarnings("unchecked")
        final Handle<Quote>[] spreads = (Handle<Quote>[]) new Handle<?>[] {
                new Handle<Quote>(spread1), new Handle<Quote>(spread2), new Handle<Quote>(spread3) };

        final Date[] spreadDates = new Date[] {
                calendar.advance(today, 100, TimeUnit.Days),
                calendar.advance(today, 200, TimeUnit.Days),
                calendar.advance(today, 300, TimeUnit.Days)
        };

        final Date interpolationDate = calendar.advance(today, 110, TimeUnit.Days);

        final YieldTermStructure spreaded = new InterpolatedPiecewiseForwardSpreadedTermStructure(
                new Handle<YieldTermStructure>(termStructure), spreads, spreadDates, new BackwardFlat());

        final double t = dc.yearFraction(today, interpolationDate);
        final double expectedForward = termStructure.forwardRate(
                t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate()
                + spread2.value();
        final double interpolatedForward = spreaded.forwardRate(
                t, t, Compounding.Continuous, Frequency.NoFrequency, true).rate();

        final double tolerance = 1e-9;
        if (Math.abs(interpolatedForward - expectedForward) > tolerance) {
            fail("unable to reproduce interpolated forward rate\n"
                    + "    calculated: " + interpolatedForward + "\n"
                    + "    expected:   " + expectedForward);
        }
    }


    /**
     * Faithful port of {@code test-suite/termstructures.cpp:462}
     * {@code BOOST_AUTO_TEST_CASE(testBackwardFlatInterpolationZeroRate)}.
     * Tolerance 1e-9 per C++.
     */
    @Test
    public void testBackwardFlatInterpolationZeroRate() {
        QL.info("Testing backward flat interpolation of zero rates between two dates...");

        final DayCounter dc = termStructure.dayCounter();
        final Date today = new Settings().evaluationDate();
        final Date referenceDate = termStructure.referenceDate();

        final SimpleQuote spread1 = new SimpleQuote(0.02);
        final SimpleQuote spread2 = new SimpleQuote(0.03);
        final SimpleQuote spread3 = new SimpleQuote(0.04);
        @SuppressWarnings("unchecked")
        final Handle<Quote>[] spreads = (Handle<Quote>[]) new Handle<?>[] {
                new Handle<Quote>(spread1), new Handle<Quote>(spread2), new Handle<Quote>(spread3) };

        final Date[] spreadDates = new Date[] {
                calendar.advance(today, 100, TimeUnit.Days),
                calendar.advance(today, 200, TimeUnit.Days),
                calendar.advance(today, 300, TimeUnit.Days)
        };
        final double[] times = new double[spreadDates.length];
        final double[] spreadValues = new double[spreadDates.length];
        for (int i = 0; i < spreadDates.length; ++i) {
            times[i] = dc.yearFraction(referenceDate, spreadDates[i]);
            spreadValues[i] = spreads[i].currentLink().value();
        }

        final Date interpolationDate = calendar.advance(today, 110, TimeUnit.Days);

        final YieldTermStructure spreaded = new InterpolatedPiecewiseForwardSpreadedTermStructure(
                new Handle<YieldTermStructure>(termStructure), spreads, spreadDates, new BackwardFlat());

        final BackwardFlatInterpolation bckFlat = new BackwardFlatInterpolation(
                new Array(times), new Array(spreadValues));
        bckFlat.update();

        final double t = dc.yearFraction(today, interpolationDate);
        final InterestRate nonSpreaded = termStructure.zeroRate(
                t, Compounding.Continuous, Frequency.NoFrequency, true);
        final double spreadPrimitive = bckFlat.primitive(t, true) / t;
        final double expectedZero = nonSpreaded.rate() + spreadPrimitive;

        final double interpolatedZero = spreaded.zeroRate(
                t, Compounding.Continuous, Frequency.NoFrequency, true).rate();

        final double tolerance = 1e-9;
        if (Math.abs(interpolatedZero - expectedZero) > tolerance) {
            fail("unable to reproduce interpolated zero rate\n"
                    + "    calculated: " + interpolatedZero + "\n"
                    + "    expected:   " + expectedZero);
        }
    }


    /**
     * Faithful port of {@code test-suite/termstructures.cpp:536}
     * {@code BOOST_AUTO_TEST_CASE(testCompositeZeroYieldStructures)}.
     * Builds two zero-yield curves from dated forward arrays, composes them
     * via subtraction, and asserts the resulting zero rates match C++ to
     * 1e-10 absolute on a set of probe dates.
     *
     * <p>Phase 1.2 closure — exercises the new
     * {@link org.jquantlib.termstructures.yieldcurves.CompositeZeroYieldStructure}
     * with a SAM-style {@link
     * org.jquantlib.termstructures.yieldcurves.CompositeZeroYieldStructure.BinaryFunction}
     * implementing subtraction ({@code (a, b) -> a - b}).
     */
    @Test
    public void testCompositeZeroYieldStructures() {
        QL.info("Testing composite zero yield structures...");

        new org.jquantlib.Settings().setEvaluationDate(
                new Date(10, org.jquantlib.time.Month.November, 2017));

        // First curve
        Date[] dates1 = new Date[] {
                new Date(10, org.jquantlib.time.Month.November, 2017),
                new Date(13, org.jquantlib.time.Month.November, 2017),
                new Date(12, org.jquantlib.time.Month.February, 2018),
                new Date(10, org.jquantlib.time.Month.May, 2018),
                new Date(10, org.jquantlib.time.Month.August, 2018),
                new Date(12, org.jquantlib.time.Month.November, 2018),
                new Date(21, org.jquantlib.time.Month.December, 2018),
                new Date(15, org.jquantlib.time.Month.January, 2020),
                new Date(31, org.jquantlib.time.Month.March, 2021),
                new Date(28, org.jquantlib.time.Month.February, 2023),
                new Date(21, org.jquantlib.time.Month.December, 2026),
                new Date(31, org.jquantlib.time.Month.January, 2030),
                new Date(28, org.jquantlib.time.Month.February, 2031),
                new Date(31, org.jquantlib.time.Month.March, 2036),
                new Date(28, org.jquantlib.time.Month.February, 2041),
                new Date(28, org.jquantlib.time.Month.February, 2048),
                new Date(31, org.jquantlib.time.Month.December, 2141)
        };

        double[] rates1 = new double[] {
                0.0655823213132524, 0.0655823213132524, 0.0699455024156877,
                0.0799107139233497, 0.0813931951022577, 0.0841615820666691,
                0.0501297919004145, 0.0823483583439658, 0.0860720030924466,
                0.0922887604375688, 0.10588902278996,   0.117021968693922,
                0.109824660896137,  0.109231572878364,  0.119218123236241,
                0.128647300167664,  0.0506086995288751
        };

        final YieldTermStructure termStructure1 =
                new org.jquantlib.termstructures.yieldcurves.InterpolatedForwardCurve<
                        org.jquantlib.math.interpolations.factories.BackwardFlat>(
                                org.jquantlib.math.interpolations.factories.BackwardFlat.class,
                                dates1, rates1, new org.jquantlib.daycounters.Actual365Fixed(),
                                new NullCalendar());

        // Second curve
        Date[] dates2 = new Date[] {
                new Date(10, org.jquantlib.time.Month.November, 2017),
                new Date(13, org.jquantlib.time.Month.November, 2017),
                new Date(11, org.jquantlib.time.Month.December, 2017),
                new Date(12, org.jquantlib.time.Month.February, 2018),
                new Date(10, org.jquantlib.time.Month.May, 2018),
                new Date(31, org.jquantlib.time.Month.January, 2022),
                new Date(7,  org.jquantlib.time.Month.December, 2023),
                new Date(31, org.jquantlib.time.Month.January, 2025),
                new Date(31, org.jquantlib.time.Month.March, 2028),
                new Date(7,  org.jquantlib.time.Month.December, 2033),
                new Date(1,  org.jquantlib.time.Month.February, 2038),
                new Date(2,  org.jquantlib.time.Month.April, 2046),
                new Date(2,  org.jquantlib.time.Month.January, 2051),
                new Date(31, org.jquantlib.time.Month.December, 2141)
        };

        double[] rates2 = new double[] {
                0.056656806197189,  0.056656806197189,  0.0419541633454473,
                0.0286681050019797, 0.0148840226959593, 0.0246680238374363,
                0.0255349067810599, 0.0298907184711927, 0.0263943927922053,
                0.0291924526539802, 0.0270049276163556, 0.028775807327614,
                0.0293567711641792, 0.010518655099659
        };

        final YieldTermStructure termStructure2 =
                new org.jquantlib.termstructures.yieldcurves.InterpolatedForwardCurve<
                        org.jquantlib.math.interpolations.factories.BackwardFlat>(
                                org.jquantlib.math.interpolations.factories.BackwardFlat.class,
                                dates2, rates2, new org.jquantlib.daycounters.Actual365Fixed(),
                                new NullCalendar());

        // C++: typedef Real(*binary_f)(Real, Real); CompositeZeroYieldStructure<binary_f>(..., sub);
        // The C++ test uses subtraction (sub at termstructures.cpp:119).
        final org.jquantlib.termstructures.yieldcurves.CompositeZeroYieldStructure compoundCurve =
                new org.jquantlib.termstructures.yieldcurves.CompositeZeroYieldStructure(
                        new Handle<YieldTermStructure>(termStructure1),
                        new Handle<YieldTermStructure>(termStructure2),
                        (a, b) -> a - b);

        Date[] probeDates = new Date[] {
                new Date(10, org.jquantlib.time.Month.November, 2017),
                new Date(15, org.jquantlib.time.Month.December, 2017),
                new Date(15, org.jquantlib.time.Month.June, 2018),
                new Date(15, org.jquantlib.time.Month.September, 2029),
                new Date(15, org.jquantlib.time.Month.September, 2038),
                new Date(15, org.jquantlib.time.Month.March, 2046),
                new Date(15, org.jquantlib.time.Month.December, 2141)
        };

        double[] expectedRates = new double[] {
                0.00892551511527986, 0.0278755322562788, 0.0512001768603456,
                0.0729941474263546,  0.0778333309498459, 0.0828451659139004,
                0.0503573807521742
        };

        final double tolerance = 1.0e-10;
        for (int i = 0; i < probeDates.length; ++i) {
            final double actual = compoundCurve.zeroRate(probeDates[i],
                    new org.jquantlib.daycounters.Actual365Fixed(),
                    Compounding.Continuous).rate();
            final double expected = expectedRates[i];

            if (Math.abs(actual - expected) > tolerance) {
                fail("unable to reproduce zero yield rate from composite input curve\n"
                        + String.format("    calculated: %.10f%n", actual)
                        + String.format("    expected:   %.10f", expected));
            }
        }
    }


    /**
     * Faithful port of {@code test-suite/termstructures.cpp:600}
     * {@code BOOST_AUTO_TEST_CASE(testNullTimeToReference)}. When the
     * day-count between the reference date and the query date is exactly
     * zero (e.g. Aug 30 -> Aug 31 under Thirty360 BondBasis), the zero
     * rate must collapse to the input flat rate without division-by-zero
     * blow-up. Tolerance 1e-10 per C++.
     *
     * <p>Phase 1 closure Round A8-E.
     */
    @Test
    public void testNullTimeToReference() {
        QL.info("Testing zero-rate calculation for null time-to-reference...");

        final double rate = 0.02;
        final DayCounter dayCount = new Thirty360(Thirty360.Convention.BondBasis);
        final FlatForward curve = new FlatForward(
                new Date(30, org.jquantlib.time.Month.August, 2023), rate, dayCount);

        // The time between August 30th and 31st is null for the 30/360
        // day-count convention.
        final double expected = rate;
        final double calculated = curve.zeroRate(
                new Date(31, org.jquantlib.time.Month.August, 2023),
                dayCount, Compounding.Continuous).rate();
        final double tolerance = 1.0e-10;

        if (Math.abs(calculated - expected) > tolerance) {
            fail("unable to reproduce zero yield rate from curve\n"
                    + "    calculated: " + calculated + "\n"
                    + "    expected:   " + expected);
        }
    }


    //
    // private inner classes
    //

    private static class Datum {
        public int n;
        public TimeUnit units;
        public double rate;

        public Datum(final int n, final TimeUnit units, final double rate) {
            this.n = n;
            this.units = units;
            this.rate = rate;
        }
    }

}
