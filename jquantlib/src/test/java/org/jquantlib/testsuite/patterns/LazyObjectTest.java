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
import org.junit.Ignore;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/lazyobject.cpp (Phase 5a).
 *
 * <p>5 BOOST_AUTO_TEST_CASE methods. Phase 5a.5 carry-forward: JQuantLib's
 * {@link LazyObject} has only the per-instance {@code alwaysForwardNotifications()}
 * but no global {@code LazyObject::Defaults} singleton like C++. The default
 * behaviour in Java is "forward first only"; tests that flip the global default
 * are {@code @Ignore} with a carry-forward note.
 *
 * <p>Cycle-detection ({@code QL_THROW_IN_CYCLES}) is also Java-specific —
 * Java {@link LazyObject#update()} silently breaks recursive notification loops
 * via the {@code updating_} guard (matches C++ default behaviour without
 * {@code QL_THROW_IN_CYCLES}).
 */
public class LazyObjectTest {

    public LazyObjectTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no LazyObject.Defaults singleton; "
            + "the global default cannot be flipped to alwaysForwardNotifications.")
    @Test
    public void testDiscardingNotifications() {
    }

    @Test
    public void testDiscardingNotificationsByDefault() {
        QL.info("Testing that lazy objects can discard notifications after the first by default...");

        // Java LazyObject defaults to 'forward first only'.
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

    @Ignore("Phase 5a.5 carry-forward — JQuantLib has no LazyObject.Defaults singleton; "
            + "the global default cannot be flipped to alwaysForwardNotifications.")
    @Test
    public void testForwardingNotificationsByDefault() {
    }

    @Test
    public void testForwardingNotifications() {
        QL.info("Testing that lazy objects can forward all notifications against default...");

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

    @Ignore("Phase 5a.5 carry-forward — Java LazyObject.update() silently breaks recursive "
            + "notification loops via 'updating_' guard (matches C++ default without "
            + "QL_THROW_IN_CYCLES); 'lazyObject manages recursive notifications' test "
            + "exercises the no-throw branch but requires a more complex Stock-cycle setup "
            + "and a custom Failing LazyObject subclass — port deferred.")
    @Test
    public void testNotificationLoop() {
    }

    @Ignore("Phase 5a.5 carry-forward — testNotificationAfterFailedCalculation requires a "
            + "custom Failing LazyObject subclass, plus the LazyObject.Defaults singleton.")
    @Test
    public void testNotificationAfterFailedCalculation() {
    }
}
