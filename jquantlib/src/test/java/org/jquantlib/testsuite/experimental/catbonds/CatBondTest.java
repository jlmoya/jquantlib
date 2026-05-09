/*
 Copyright (C) 2012, 2013 Grzegorz Andruszkiewicz
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/

package org.jquantlib.testsuite.experimental.catbonds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.IborCouponPricer;
import org.jquantlib.cashflow.PricerSetter;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.experimental.catbonds.CatRisk;
import org.jquantlib.experimental.catbonds.CatSimulation;
import org.jquantlib.experimental.catbonds.DateRealPair;
import org.jquantlib.experimental.catbonds.DigitalNotionalRisk;
import org.jquantlib.experimental.catbonds.EventPaymentOffset;
import org.jquantlib.experimental.catbonds.EventSet;
import org.jquantlib.experimental.catbonds.FloatingCatBond;
import org.jquantlib.experimental.catbonds.MonteCarloCatBondEngine;
import org.jquantlib.experimental.catbonds.NoOffset;
import org.jquantlib.experimental.catbonds.NotionalRisk;
import org.jquantlib.experimental.catbonds.ProportionalNotionalRisk;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.calendars.UnitedStates;
import org.jquantlib.quotes.SimpleQuote;

import org.junit.Test;

/**
 * Port of {@code QuantLib/test-suite/catbonds.cpp}.
 *
 * <p>Tests the catastrophe-bond machinery: event-set simulations, notional-risk
 * models, and the Monte Carlo pricing engine.
 */
public class CatBondTest {

    // --- shared event data (mirrors the C++ global sampleEvents) ---
    private static final List<DateRealPair> SAMPLE_EVENTS = new ArrayList<>();
    private static final Date EVENTS_START = new Date(1,  Month.January,  2011);
    private static final Date EVENTS_END   = new Date(31, Month.December, 2014);

    static {
        SAMPLE_EVENTS.add(new DateRealPair(new Date(1,  Month.February, 2012), 100.0));
        SAMPLE_EVENTS.add(new DateRealPair(new Date(1,  Month.July,     2013), 150.0));
        SAMPLE_EVENTS.add(new DateRealPair(new Date(5,  Month.January,  2014),  50.0));
    }

    private static final double FACE_AMOUNT = 1_000_000.0;
    private static final double TOLERANCE   = 1.0e-6;

    // -----------------------------------------------------------------------
    // Helper: build a flat yield term structure wrapped in a Handle
    // -----------------------------------------------------------------------
    private static Handle<YieldTermStructure> flatRate(
            final Date today, final double rate) {
        return new Handle<YieldTermStructure>(
                new FlatForward(today, new Handle<org.jquantlib.quotes.Quote>(new SimpleQuote(rate)), new Actual360()));
    }

    // -----------------------------------------------------------------------
    // testEventSetForWholeYears
    // -----------------------------------------------------------------------
    @Test
    public void testEventSetForWholeYears() {
        final EventSet catRisk = new EventSet(SAMPLE_EVENTS, EVENTS_START, EVENTS_END);
        final CatSimulation simulation = catRisk.newSimulation(
                new Date(1, Month.January,  2015),
                new Date(31, Month.December, 2015));

        final List<DateRealPair> path = new ArrayList<>();

        // Path 0: maps to 2011 → no events in 2011
        assertTrue("expected nextPath() == true for path 0",  simulation.nextPath(path));
        assertEquals("path 0 size", 0, path.size());

        // Path 1: maps to 2012 → Feb 1 event
        assertTrue("expected nextPath() == true for path 1",  simulation.nextPath(path));
        assertEquals("path 1 size", 1, path.size());
        assertEquals("path 1 date",  new Date(1, Month.February, 2015), path.get(0).date);
        assertEquals("path 1 value", 100.0, path.get(0).value, 0.0);

        // Path 2: maps to 2013 → Jul 1 event
        assertTrue("expected nextPath() == true for path 2",  simulation.nextPath(path));
        assertEquals("path 2 size", 1, path.size());
        assertEquals("path 2 date",  new Date(1, Month.July, 2015), path.get(0).date);
        assertEquals("path 2 value", 150.0, path.get(0).value, 0.0);

        // Path 3: maps to 2014 → Jan 5 event
        assertTrue("expected nextPath() == true for path 3",  simulation.nextPath(path));
        assertEquals("path 3 size", 1, path.size());
        assertEquals("path 3 date",  new Date(5, Month.January, 2015), path.get(0).date);
        assertEquals("path 3 value", 50.0, path.get(0).value, 0.0);

        // No more data
        assertFalse("expected nextPath() == false after exhaustion", simulation.nextPath(path));
    }

    // -----------------------------------------------------------------------
    // testEventSetForIrregularPeriods
    // -----------------------------------------------------------------------
    @Test
    public void testEventSetForIrregularPeriods() {
        final EventSet catRisk = new EventSet(SAMPLE_EVENTS, EVENTS_START, EVENTS_END);
        final CatSimulation simulation = catRisk.newSimulation(
                new Date(2,  Month.January,  2015),
                new Date(5,  Month.January,  2016));

        final List<DateRealPair> path = new ArrayList<>();

        // Path 0 (Jan 2011 – Jan 2012 segment): no events
        assertTrue(simulation.nextPath(path));
        assertEquals(0, path.size());

        // Path 1 (Jan 2012 – Jan 2013 segment): Jul 2013 and Jan 2014 fall
        // inside Jan 2013–Jan 2015 → translated to Jul 2015 and Jan 2016
        assertTrue(simulation.nextPath(path));
        assertEquals(2, path.size());
        assertEquals(new Date(1, Month.July,    2015), path.get(0).date);
        assertEquals(150.0, path.get(0).value, 0.0);
        assertEquals(new Date(5, Month.January, 2016), path.get(1).date);
        assertEquals(50.0, path.get(1).value, 0.0);

        // No more data
        assertFalse(simulation.nextPath(path));
    }

    // -----------------------------------------------------------------------
    // testEventSetForNoEvents
    // -----------------------------------------------------------------------
    @Test
    public void testEventSetForNoEvents() {
        final EventSet catRisk = new EventSet(
                new ArrayList<>(), EVENTS_START, EVENTS_END);
        final CatSimulation simulation = catRisk.newSimulation(
                new Date(2,  Month.January, 2015),
                new Date(5,  Month.January, 2016));

        final List<DateRealPair> path = new ArrayList<>();

        assertTrue(simulation.nextPath(path));
        assertEquals(0, path.size());

        assertTrue(simulation.nextPath(path));
        assertEquals(0, path.size());

        assertFalse(simulation.nextPath(path));
    }

    // -----------------------------------------------------------------------
    // testCatBondInDoomScenario
    //   Event with loss = 1000 >= threshold = 100 → every path defaults
    //   Price should be 0, all risk metrics should be 1.
    // -----------------------------------------------------------------------
    @Test
    public void testCatBondInDoomScenario() {
        final Date today = new Date(22, Month.November, 2004);
        new Settings().setEvaluationDate(today);

        final int settlementDays = 1;

        final Handle<YieldTermStructure> discountCurve = flatRate(today, 0.03);

        final USDLibor index = new USDLibor(new Period(6, TimeUnit.Months),
                flatRate(today, 0.025));
        final int fixingDays = 1;

        final IborCouponPricer pricer = new BlackIborCouponPricer(
                new Handle<OptionletVolatilityStructure>());

        final Calendar usGovt = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Schedule sch = new Schedule(
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2008),
                new Period(Frequency.Semiannual),
                usGovt,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Backward,
                false);

        // Doom: single event with loss 1000 exceeds threshold 100 → full default
        final List<DateRealPair> events = new ArrayList<>();
        events.add(new DateRealPair(new Date(30, Month.November, 2004), 1000.0));
        final CatRisk doomCatRisk = new EventSet(events,
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2008));

        final EventPaymentOffset paymentOffset = new NoOffset();
        final NotionalRisk notionalRisk = new DigitalNotionalRisk(paymentOffset, 100.0);

        final FloatingCatBond catBond = new FloatingCatBond(
                settlementDays, FACE_AMOUNT, sch, index,
                new ActualActual(ActualActual.Convention.ISMA),
                notionalRisk,
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(new double[0]), new Array(new double[0]),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final PricingEngine catBondEngine = new MonteCarloCatBondEngine(doomCatRisk, discountCurve);
        catBond.setPricingEngine(catBondEngine);
        PricerSetter.setCouponPricer(catBond.cashflows(), pricer);

        // Trigger pricing to populate risk metrics
        final double price = catBond.cleanPrice();
        assertEquals("doom scenario price should be 0", 0.0, price, TOLERANCE);

        final double lossProbability       = catBond.lossProbability();
        final double exhaustionProbability = catBond.exhaustionProbability();
        final double expectedLoss          = catBond.expectedLoss();
        // After cleanPrice(), the risk metrics are available

        assertEquals("doom lossProbability",       1.0, lossProbability,       TOLERANCE);
        assertEquals("doom exhaustionProbability", 1.0, exhaustionProbability, TOLERANCE);
        assertEquals("doom expectedLoss",          1.0, expectedLoss,          TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // testCatBondWithDoomOnceInTenYears
    //   One event at end of bond period replicated over 40 years → 10% chance
    //   per 4-year bond period.
    // -----------------------------------------------------------------------
    @Test
    public void testCatBondWithDoomOnceInTenYears() {
        final Date today = new Date(22, Month.November, 2004);
        new Settings().setEvaluationDate(today);

        final int settlementDays = 1;
        final Handle<YieldTermStructure> discountCurve = flatRate(today, 0.03);
        final Handle<YieldTermStructure> noCatDiscountCurve = flatRate(today, 0.03);

        final USDLibor index = new USDLibor(new Period(6, TimeUnit.Months),
                flatRate(today, 0.025));
        final int fixingDays = 1;

        final IborCouponPricer pricer = new BlackIborCouponPricer(
                new Handle<OptionletVolatilityStructure>());

        final Calendar usGovt = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Schedule sch = new Schedule(
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2008),
                new Period(Frequency.Semiannual),
                usGovt,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Backward,
                false);

        // 1 event at maturity in first period (Nov 2008), replicated over 40
        // years → 10% chance in any 4-year bond period
        final List<DateRealPair> events = new ArrayList<>();
        events.add(new DateRealPair(new Date(30, Month.November, 2008), 1000.0));
        final CatRisk doomCatRisk = new EventSet(events,
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2044));

        final CatRisk noCatRisk = new EventSet(
                new ArrayList<>(),
                new Date(1, Month.January, 2000),
                new Date(31, Month.December, 2010));

        final EventPaymentOffset paymentOffset = new NoOffset();
        final NotionalRisk notionalRisk = new DigitalNotionalRisk(paymentOffset, 100.0);

        final FloatingCatBond catBond = new FloatingCatBond(
                settlementDays, FACE_AMOUNT, sch, index,
                new ActualActual(ActualActual.Convention.ISMA),
                notionalRisk,
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(new double[0]), new Array(new double[0]),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final PricingEngine catBondEngine = new MonteCarloCatBondEngine(doomCatRisk, discountCurve);
        catBond.setPricingEngine(catBondEngine);
        PricerSetter.setCouponPricer(catBond.cashflows(), pricer);

        // Trigger pricing: loss metrics are populated as a side effect of NPV calculation
        final double catPrice = catBond.cleanPrice();

        assertEquals("10% loss probability",       0.1, catBond.lossProbability(),       TOLERANCE);
        assertEquals("10% exhaustion probability", 0.1, catBond.exhaustionProbability(), TOLERANCE);
        assertEquals("10% expected loss",          0.1, catBond.expectedLoss(),          TOLERANCE);

        // Risk-free run (no events) → 0% loss metrics
        catBond.setPricingEngine(new MonteCarloCatBondEngine(noCatRisk, discountCurve));
        final double riskFreePrice = catBond.cleanPrice();
        assertEquals("rf loss probability",       0.0, catBond.lossProbability(),       TOLERANCE);
        assertEquals("rf exhaustion probability", 0.0, catBond.exhaustionProbability(), TOLERANCE);
        assertTrue("rf expected loss near 0", Math.abs(catBond.expectedLoss()) < TOLERANCE);

        // Price relationship: catPrice = 90% of riskFreePrice (10% of paths default)
        assertEquals("cat price = 90% of rf price",
                riskFreePrice * 0.9, catPrice, TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // testCatBondWithProportionalNotional
    //   ProportionalNotionalRisk: attachment=500, exhaustion=1500.
    //   Event loss = 1000 → 50% notional remains → expectedLoss = 0.05 per path
    //   that has event (10% probability) → mean expectedLoss = 0.05
    // -----------------------------------------------------------------------
    @Test
    public void testCatBondWithProportionalNotional() {
        final Date today = new Date(22, Month.November, 2004);
        new Settings().setEvaluationDate(today);

        final int settlementDays = 1;
        final Handle<YieldTermStructure> discountCurve = flatRate(today, 0.03);

        final USDLibor index = new USDLibor(new Period(6, TimeUnit.Months),
                flatRate(today, 0.025));
        final int fixingDays = 1;

        final IborCouponPricer pricer = new BlackIborCouponPricer(
                new Handle<OptionletVolatilityStructure>());

        final Calendar usGovt = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Schedule sch = new Schedule(
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2008),
                new Period(Frequency.Semiannual),
                usGovt,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Backward,
                false);

        final List<DateRealPair> events = new ArrayList<>();
        events.add(new DateRealPair(new Date(30, Month.November, 2008), 1000.0));
        final CatRisk doomCatRisk = new EventSet(events,
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2044));

        final CatRisk noCatRisk = new EventSet(
                new ArrayList<>(),
                new Date(1, Month.January, 2000),
                new Date(31, Month.December, 2010));

        final EventPaymentOffset paymentOffset = new NoOffset();
        // attachment=500, exhaustion=1500; loss=1000 → notional=(1500-1000)/(1500-500)=0.5
        final NotionalRisk notionalRisk = new ProportionalNotionalRisk(paymentOffset, 500.0, 1500.0);

        final FloatingCatBond catBond = new FloatingCatBond(
                settlementDays, FACE_AMOUNT, sch, index,
                new ActualActual(ActualActual.Convention.ISMA),
                notionalRisk,
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(new double[0]), new Array(new double[0]),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final PricingEngine catBondEngine = new MonteCarloCatBondEngine(doomCatRisk, discountCurve);
        catBond.setPricingEngine(catBondEngine);
        PricerSetter.setCouponPricer(catBond.cashflows(), pricer);

        // Trigger pricing
        final double catPrice = catBond.cleanPrice();

        assertEquals("proportional: lossProbability",       0.1,  catBond.lossProbability(),       TOLERANCE);
        assertEquals("proportional: exhaustionProbability", 0.0,  catBond.exhaustionProbability(), TOLERANCE);
        assertEquals("proportional: expectedLoss",          0.05, catBond.expectedLoss(),          TOLERANCE);

        // Risk-free
        catBond.setPricingEngine(new MonteCarloCatBondEngine(noCatRisk, discountCurve));
        final double riskFreePrice = catBond.cleanPrice();
        assertEquals("rf lossProbability",  0.0, catBond.lossProbability(),  TOLERANCE);
        assertTrue("rf expectedLoss near 0", Math.abs(catBond.expectedLoss()) < TOLERANCE);

        assertEquals("cat price = 95% of rf price", riskFreePrice * 0.95, catPrice, TOLERANCE);
    }
}
