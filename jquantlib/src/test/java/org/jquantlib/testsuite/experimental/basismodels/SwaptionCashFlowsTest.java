/*
 Copyright (C) 2026 JQuantLib migration contributors.
 Phase 4g — SwaptionCashFlows smoke tests.

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
package org.jquantlib.testsuite.experimental.basismodels;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.cashflow.FixedRateLeg;
import org.jquantlib.cashflow.IborLeg;
import org.jquantlib.cashflow.Leg;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.Thirty360;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.experimental.basismodels.SwaptionCashFlows;
import org.jquantlib.indexes.Euribor6M;
import org.jquantlib.instruments.Swaption;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.MakeSchedule;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Smoke tests for {@link SwaptionCashFlows}.
 *
 * <p>Verifies that construction completes without error and that the resulting
 * cash-flow arrays have the expected sizes and pass basic sanity checks.
 * No C++ cross-validated reference values — this is a structural smoke test.
 */
public class SwaptionCashFlowsTest {

    // Use a past evaluation date so all swap cash flows lie in the future,
    // avoiding the historical-fixing lookup path in IborCoupon.rate().
    private static final Date TODAY = new Date(2, Month.January, 2020);

    @Before
    public void setUp() {
        new Settings().setEvaluationDate(TODAY);
    }

    /**
     * Build a simple 2-year 6M-Euribor swap, wrap it in a European swaption,
     * and verify SwaptionCashFlows constructs without throwing and returns
     * non-empty float and fixed cash-flow lists.
     */
    @Test
    public void testSwaptionCashFlowsConstruction() {
        QL.info("::::: SwaptionCashFlowsTest :::::");

        final Target cal = new Target();
        final Actual365Fixed dc = new Actual365Fixed();

        final Handle<YieldTermStructure> discH =
                new Handle<YieldTermStructure>(
                        new FlatForward(TODAY, 0.03, dc));

        final Euribor6M idx = new Euribor6M(discH);

        // 1-year into a 2-year underlying
        final Date start    = cal.advance(TODAY, 2, TimeUnit.Days,
                BusinessDayConvention.Following, false);
        final Date end      = cal.advance(start, new Period(2, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing, false);
        final Date exercise = cal.advance(TODAY, new Period(1, TimeUnit.Years),
                BusinessDayConvention.ModifiedFollowing, false);

        final Schedule floatSch = new MakeSchedule(
                start, end, idx.tenor(), cal,
                BusinessDayConvention.ModifiedFollowing)
                .backwards()
                .schedule();

        final Schedule fixedSch = new MakeSchedule(
                start, end, new Period(Frequency.Annual), cal,
                BusinessDayConvention.ModifiedFollowing)
                .backwards()
                .schedule();

        final Leg floatLeg = new IborLeg(floatSch, idx)
                .withNotionals(1.0)
                .Leg();

        final Leg fixedLeg = new FixedRateLeg(fixedSch, new Thirty360())
                .withNotionals(1.0)
                .withCouponRates(0.03)
                .Leg();

        final VanillaSwap swap = new VanillaSwap(
                VanillaSwap.Type.Payer,
                1.0,
                fixedSch,
                0.03,
                new Thirty360(),
                floatSch,
                idx,
                0.0,
                idx.dayCounter());

        final Swaption swaption = new Swaption(
                swap, new EuropeanExercise(exercise));

        final SwaptionCashFlows cfs = new SwaptionCashFlows(
                swaption, discH, false /* simple tenor spread */);

        // Float leg must be non-empty
        assertNotNull("floatLeg", cfs.floatLeg());
        assertFalse("floatLeg must be non-empty", cfs.floatLeg().isEmpty());

        // Fixed leg must be non-empty
        assertNotNull("fixedLeg", cfs.fixedLeg());
        assertFalse("fixedLeg must be non-empty", cfs.fixedLeg().isEmpty());

        // Exercise times list must have exactly 1 entry (European)
        assertNotNull("exerciseTimes", cfs.exerciseTimes());
        assertEquals("exerciseTimes size", 1, cfs.exerciseTimes().size());

        // Float and fixed time lists must match their cash-flow counts
        assertEquals("floatTimes matches floatLeg",
                cfs.floatLeg().size(), cfs.floatTimes().size());
        assertEquals("fixedTimes matches fixedLeg",
                cfs.fixedLeg().size(), cfs.fixedTimes().size());
    }
}
