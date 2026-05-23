/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.experimental.credit.BlackCdsOptionEngine;
import org.jquantlib.experimental.credit.CdsOption;
import org.jquantlib.instruments.CreditDefaultSwap;
import org.jquantlib.instruments.Protection;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.credit.MidPointCdsEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.credit.FlatHazardRate;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 {@code test-suite/cdsoption.cpp} (121 LOC).
 * Mirrors {@code BOOST_AUTO_TEST_SUITE(CdsOptionTests)} verbatim.
 *
 * <p><strong>Tolerance tier</strong> — loose-cached. The C++ test asserts
 * the option NPV against the hard-coded cached value {@code 270.976348}
 * with tolerance {@code 1.0e-5} (see {@code cdsoption.cpp:95,112}). The
 * Java port mirrors both the cached literal and the C++ tolerance.
 */
public class CdsOptionTest {

    /** Mirrors C++ {@code testCached()}. */
    @Test
    public void testCached() {
        final Settings s = new Settings();
        final Date prevEval = s.evaluationDate();
        try {
            final Date cachedToday = new Date(10, Month.December, 2007);
            s.setEvaluationDate(cachedToday);

            final Calendar calendar = new Target();

            final RelinkableHandle<YieldTermStructure> riskFree = new RelinkableHandle<YieldTermStructure>();
            riskFree.linkTo(new FlatForward(cachedToday, 0.02, new Actual360()));

            final Date expiry = calendar.advance(cachedToday, 9, TimeUnit.Months);
            final Date startDate = calendar.advance(expiry, 1, TimeUnit.Months);
            final Date maturity = calendar.advance(startDate, 7, TimeUnit.Years);

            final DayCounter dayCounter = new Actual360();
            final BusinessDayConvention convention = BusinessDayConvention.ModifiedFollowing;
            final double notional = 1000000.0;

            final Handle<Quote> hazardRate = new Handle<Quote>(new SimpleQuote(0.001));

            final Schedule schedule = new Schedule(
                    startDate, maturity, new Period(Frequency.Quarterly),
                    calendar, convention, convention,
                    DateGeneration.Rule.Forward, false);

            final double recoveryRate = 0.4;
            final Handle<DefaultProbabilityTermStructure> defaultProbability =
                    new Handle<DefaultProbabilityTermStructure>(
                            new FlatHazardRate(0, calendar, hazardRate, dayCounter));

            final PricingEngine swapEngine = new MidPointCdsEngine(
                    defaultProbability, recoveryRate, riskFree);

            // Probe-only seller swap to extract fairSpread (used as option strike).
            final CreditDefaultSwap probeSwap = new CreditDefaultSwap(
                    Protection.Side.Seller, notional, 0.001, schedule, convention, dayCounter);
            probeSwap.setPricingEngine(swapEngine);
            final double strike = probeSwap.fairSpread();

            final Handle<Quote> cdsVol = new Handle<Quote>(new SimpleQuote(0.20));

            // -------- Option #1: underlying Seller CDS @ strike --------
            final CreditDefaultSwap underlying1 = new CreditDefaultSwap(
                    Protection.Side.Seller, notional, strike, schedule, convention, dayCounter);
            underlying1.setPricingEngine(swapEngine);

            final Exercise exercise = new EuropeanExercise(expiry);
            final CdsOption option1 = new CdsOption(underlying1, exercise);
            option1.setPricingEngine(new BlackCdsOptionEngine(
                    defaultProbability, recoveryRate, riskFree, cdsVol));

            final double cachedValue = 270.976348;
            assertEquals("option1 NPV vs cached value",
                    cachedValue, option1.NPV(), 1.0e-5);

            // -------- Option #2: underlying Buyer CDS @ strike --------
            final CreditDefaultSwap underlying2 = new CreditDefaultSwap(
                    Protection.Side.Buyer, notional, strike, schedule, convention, dayCounter);
            underlying2.setPricingEngine(swapEngine);

            final CdsOption option2 = new CdsOption(underlying2, exercise);
            option2.setPricingEngine(new BlackCdsOptionEngine(
                    defaultProbability, recoveryRate, riskFree, cdsVol));

            assertEquals("option2 NPV vs cached value",
                    cachedValue, option2.NPV(), 1.0e-5);
        } finally {
            s.setEvaluationDate(prevEval);
        }
    }
}
