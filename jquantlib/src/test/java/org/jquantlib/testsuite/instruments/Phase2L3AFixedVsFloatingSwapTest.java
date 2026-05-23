/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.Euribor;
import org.jquantlib.instruments.FixedVsFloatingSwap;
import org.jquantlib.instruments.VanillaSwap;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Wiring smoke tests for {@link FixedVsFloatingSwap}.
 *
 * <p>Validates: (a) Payer/Receiver type-sign mapping (payer = [-1, +1] / [+1, -1] on the
 * fixed/floating legs), (b) inspector accessors, (c) {@code nominal()} short-circuit when
 * nominals are constant + identical between legs, (d) error-paths for null index / non-constant
 * nominals.
 *
 * <p>We use a minimal concrete subclass {@link StubFixedVsFloatingSwap} that leaves the
 * floating leg empty (it's not exercised here) — these tests only cover the base class wiring,
 * not pricing.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class Phase2L3AFixedVsFloatingSwapTest {

    /** Minimal concrete subclass — engines are out of scope for these wiring tests. */
    static class StubFixedVsFloatingSwap extends FixedVsFloatingSwap {
        StubFixedVsFloatingSwap(final VanillaSwap.Type type, final List< Double > fixed, final List< Double > floating,
                final Schedule fixedSchedule, final Schedule floatingSchedule, final IborIndex iborIndex) {
            super(type, fixed, fixedSchedule, 0.04, new Actual360(),
                    floating, floatingSchedule, iborIndex, 0.0, new Actual360(),
                    BusinessDayConvention.ModifiedFollowing, 0, null);
        }

        @Override
        protected void setupFloatingArguments(final ArgumentsImpl args) {
            // unused — wiring-only tests don't price.
        }
    }

    private static Schedule scheduleOf(final Date start, final int years) {
        return new Schedule(
                start,
                start.add(new Period(years, TimeUnit.Years)),
                new Period(Frequency.Annual),
                new Target(),
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                org.jquantlib.time.DateGeneration.Rule.Forward,
                false);
    }

    @Test
    public void testPayerTypeSign() {
        final Date today = new Date(15, Month.January, 2026);
        final Schedule fixedSchedule = scheduleOf(today, 3);
        final Schedule floatSchedule = scheduleOf(today, 3);
        final IborIndex idx = new Euribor(new Period(6, TimeUnit.Months));
        final List< Double > nominals = Arrays.asList(1_000_000.0, 1_000_000.0, 1_000_000.0);

        final FixedVsFloatingSwap swap = new StubFixedVsFloatingSwap(
                VanillaSwap.Type.Payer, nominals, nominals, fixedSchedule, floatSchedule, idx);
        assertEquals(VanillaSwap.Type.Payer, swap.type());
        // Constant-nominal short-circuit
        assertEquals(1_000_000.0, swap.nominal(), 0.0);
        assertNotNull(swap.fixedLeg());
        assertNotNull(swap.floatingLeg());
    }

    @Test
    public void testReceiverTypeSign() {
        final Date today = new Date(15, Month.January, 2026);
        final Schedule fixedSchedule = scheduleOf(today, 3);
        final Schedule floatSchedule = scheduleOf(today, 3);
        final IborIndex idx = new Euribor(new Period(6, TimeUnit.Months));
        final List< Double > nominals = Arrays.asList(1_000_000.0, 1_000_000.0, 1_000_000.0);

        final FixedVsFloatingSwap swap = new StubFixedVsFloatingSwap(
                VanillaSwap.Type.Receiver, nominals, nominals, fixedSchedule, floatSchedule, idx);
        assertEquals(VanillaSwap.Type.Receiver, swap.type());
        assertEquals(1_000_000.0, swap.nominal(), 0.0);
    }

    @Test
    public void testNonConstantNominalsThrowsOnNominal() {
        final Date today = new Date(15, Month.January, 2026);
        final Schedule fixedSchedule = scheduleOf(today, 3);
        final Schedule floatSchedule = scheduleOf(today, 3);
        final IborIndex idx = new Euribor(new Period(6, TimeUnit.Months));
        final List< Double > amortising = Arrays.asList(1_000_000.0, 800_000.0, 600_000.0);

        final FixedVsFloatingSwap swap = new StubFixedVsFloatingSwap(
                VanillaSwap.Type.Receiver, amortising, amortising, fixedSchedule, floatSchedule, idx);
        try {
            swap.nominal();
            fail("expected: amortising nominals — nominal() must throw");
        } catch (final RuntimeException expected) {
            // OK
        }
        // But nominals() still works because same-on-both-legs.
        assertEquals(amortising, swap.nominals());
    }

    @Test
    public void testNullIndexRejected() {
        final Date today = new Date(15, Month.January, 2026);
        final Schedule fixedSchedule = scheduleOf(today, 3);
        final Schedule floatSchedule = scheduleOf(today, 3);
        final List< Double > nominals = Arrays.asList(1_000_000.0, 1_000_000.0, 1_000_000.0);
        try {
            new StubFixedVsFloatingSwap(VanillaSwap.Type.Payer, nominals, nominals,
                    fixedSchedule, floatSchedule, null);
            fail("expected: null IborIndex must be rejected");
        } catch (final RuntimeException expected) {
            // OK
        }
    }

    @Test
    public void testInspectors() {
        final Date today = new Date(15, Month.January, 2026);
        final Schedule fixedSchedule = scheduleOf(today, 3);
        final Schedule floatSchedule = scheduleOf(today, 3);
        final IborIndex idx = new Euribor(new Period(6, TimeUnit.Months));
        final List< Double > nominals = Arrays.asList(1_000_000.0, 1_000_000.0, 1_000_000.0);

        final FixedVsFloatingSwap swap = new StubFixedVsFloatingSwap(
                VanillaSwap.Type.Receiver, nominals, nominals, fixedSchedule, floatSchedule, idx);

        assertEquals(0.04, swap.fixedRate(), 0.0);
        assertEquals(0.0, swap.spread(), 0.0);
        assertEquals(BusinessDayConvention.ModifiedFollowing, swap.paymentConvention());
        assertEquals(idx, swap.iborIndex());
        assertEquals(fixedSchedule, swap.fixedSchedule());
        assertEquals(floatSchedule, swap.floatingSchedule());
        assertEquals(nominals, swap.fixedNominals());
        assertEquals(nominals, swap.floatingNominals());
    }
}
