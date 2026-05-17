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

package org.jquantlib.testsuite.patterns;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Stock;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.util.LazyObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/lazyobject.cpp (Phase 5a).
 *
 * <p>5 BOOST_AUTO_TEST_CASE methods. Phase 5e.5b-CFC-d-54: the global
 * {@link LazyObject.Defaults} singleton is now present, so the
 * "global default + per-instance opt-out" matrix is fully testable.
 *
 * <p>Cycle-detection ({@code QL_THROW_IN_CYCLES}) is Java-specific -
 * Java {@link LazyObject#update()} silently breaks recursive notification
 * loops via the {@code updating_} guard (matches C++ default behaviour
 * without {@code QL_THROW_IN_CYCLES}). The full cycle test
 * ({@code testNotificationLoop}) requires a Stock-Stock observer-cycle
 * setup that depends on Instrument's internal addObserver wiring and
 * is deferred.
 */
public class LazyObjectTest {

    public LazyObjectTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Mirror of C++ {@code TearDown} fixture: capture the global default
     * before each test, restore it after - so individual tests can flip
     * the default without polluting subsequent tests.
     */
    private boolean savedDefault;

    @Before
    public void saveDefault() {
        savedDefault = LazyObject.Defaults.instance().forwardsAllNotifications();
    }

    @After
    public void restoreDefault() {
        if (savedDefault) {
            LazyObject.Defaults.instance().alwaysForwardNotifications();
        } else {
            LazyObject.Defaults.instance().forwardFirstNotificationOnly();
        }
    }

    @Test
    public void testDiscardingNotifications() {
        QL.info("Testing that lazy objects can discard notifications after the first against default...");

        // Flip the global default to alwaysForward, then opt this single
        // Stock back to forwardFirstOnly via the per-instance method.
        LazyObject.Defaults.instance().alwaysForwardNotifications();

        final SimpleQuote q = new SimpleQuote(0.0);
        final Instrument s = new Stock(new Handle<Quote>(q));

        final Flag f = new Flag();
        s.addObserver(f);

        s.forwardFirstNotificationOnly();

        s.NPV();
        q.setValue(1.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change");
        }

        f.lower();
        q.setValue(2.0);
        if (f.isUp()) {
            fail("Observer was notified of second change");
        }

        f.lower();
        s.NPV();
        q.setValue(3.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change after recalculation");
        }
    }

    @Test
    public void testDiscardingNotificationsByDefault() {
        QL.info("Testing that lazy objects can discard notifications after the first by default...");

        LazyObject.Defaults.instance().forwardFirstNotificationOnly();

        final SimpleQuote q = new SimpleQuote(0.0);
        final Instrument s = new Stock(new Handle<Quote>(q));

        final Flag f = new Flag();
        s.addObserver(f);

        s.NPV();
        q.setValue(1.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change");
        }

        f.lower();
        q.setValue(2.0);
        if (f.isUp()) {
            fail("Observer was notified of second change");
        }

        f.lower();
        s.NPV();
        q.setValue(3.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change after recalculation");
        }
    }

    @Test
    public void testForwardingNotificationsByDefault() {
        QL.info("Testing that lazy objects can forward all notifications by default...");

        LazyObject.Defaults.instance().alwaysForwardNotifications();

        final SimpleQuote q = new SimpleQuote(0.0);
        final Instrument s = new Stock(new Handle<Quote>(q));

        final Flag f = new Flag();
        s.addObserver(f);

        s.NPV();
        q.setValue(1.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change");
        }

        f.lower();
        q.setValue(2.0);
        if (!f.isUp()) {
            fail("Observer was not notified of second change");
        }
    }

    @Test
    public void testForwardingNotifications() {
        QL.info("Testing that lazy objects can forward all notifications against default...");

        LazyObject.Defaults.instance().forwardFirstNotificationOnly();

        final SimpleQuote q = new SimpleQuote(0.0);
        final Instrument s = new Stock(new Handle<Quote>(q));

        final Flag f = new Flag();
        s.addObserver(f);

        s.alwaysForwardNotifications();

        s.NPV();
        q.setValue(1.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change");
        }

        f.lower();
        q.setValue(2.0);
        if (!f.isUp()) {
            fail("Observer was not notified of second change");
        }
    }

    @Ignore("Phase 5a.5 carry-forward - the C++ testNotificationLoop wires Stock1 -> "
            + "Stock2 -> Stock3 -> Stock1 via Stock-as-Observer registerWith calls. "
            + "Java's LazyObject.update() silently breaks the cycle via the 'updating_' "
            + "guard (matches C++ default behaviour without QL_THROW_IN_CYCLES) but the "
            + "full Stock-cycle setup requires more Instrument plumbing; deferred.")
    @Test
    public void testNotificationLoop() {
    }

    /**
     * Java port of C++ {@code testNotificationAfterFailedCalculation} -
     * verifies that after a {@link LazyObject#performCalculations()} throws,
     * the lazy object still forwards a single notification on the next
     * input change (so observers learn the failed state has been invalidated).
     */
    @Test
    public void testNotificationAfterFailedCalculation() {
        QL.info("Testing that lazy objects forward notifications after a failed calculation...");

        LazyObject.Defaults.instance().forwardFirstNotificationOnly();

        final Failing s = new Failing();
        final SimpleQuote q = new SimpleQuote(0.0);
        q.addObserver(s);

        final Flag f = new Flag();
        s.addObserver(f);

        // successful calculation, then change => observer should be notified
        s.doCalculate();
        q.setValue(1.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change after successful calculation");
        }

        f.lower();

        // failed calculation
        s.failOnCalculation(true);
        try {
            s.doCalculate();
            fail("Expected intentional failure was not thrown");
        } catch (final RuntimeException expected) {
            if (!expected.getMessage().contains("intentional failure")) {
                fail("Wrong exception thrown: " + expected);
            }
        }

        if (f.isUp()) {
            fail("Observer was notified by failed calculation itself");
        }

        // fix the object
        s.failOnCalculation(false);

        // change input => observer should be notified despite the prior failure
        q.setValue(2.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change after failed calculation");
        }

        f.lower();

        // verify it can actually recalculate now
        try {
            s.doCalculate();
        } catch (final RuntimeException e) {
            fail("Unexpected exception on recovery recalculation: " + e);
        }

        if (f.isUp()) {
            fail("Observer was notified by successful recalculation itself");
        }

        // verify the "forward first only" contract is preserved:
        // after recalculation, one notification should be forwarded...
        q.setValue(3.0);
        if (!f.isUp()) {
            fail("Observer was not notified of change after recovery");
        }

        f.lower();

        // ...but a second change without recalculation should be discarded
        q.setValue(4.0);
        if (f.isUp()) {
            fail("Observer was notified of second change without recalculation");
        }
    }

    /**
     * Test-only subclass mirroring C++ {@code Failing} (inline class in
     * lazyobject.cpp): a LazyObject whose performCalculations can be
     * toggled to throw at will.
     */
    private static final class Failing extends LazyObject {
        private boolean fail = false;

        void failOnCalculation(final boolean b) {
            this.fail = b;
        }

        /** Public proxy to the protected {@link LazyObject#calculate()}. */
        void doCalculate() {
            calculate();
        }

        @Override
        protected void performCalculations() {
            if (fail) {
                throw new RuntimeException("intentional failure");
            }
        }
    }
}
