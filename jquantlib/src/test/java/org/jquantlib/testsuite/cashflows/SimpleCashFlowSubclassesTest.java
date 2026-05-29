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

package org.jquantlib.testsuite.cashflows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jquantlib.QL;
import org.jquantlib.cashflow.AmortizingPayment;
import org.jquantlib.cashflow.Redemption;
import org.jquantlib.cashflow.SimpleCashFlow;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;
import org.junit.Test;

/**
 * Deterministic tests for the thin {@link SimpleCashFlow} subclasses {@link Redemption} and {@link AmortizingPayment}
 * ported from C++ QuantLib v1.42.1 ql/cashflows/simplecashflow.hpp.
 *
 * <p>These classes carry no extra state beyond {@link SimpleCashFlow}
 * (confirmed against simplecashflow.hpp:61 / :76 — the only override is {@code accept}). We therefore verify (a)
 * {@code amount()}/{@code date()} pass-through and (b) the type-specific visitor dispatch that is the entire reason the
 * subclasses exist.
 *
 * <p>EXACT tier — all values are transcribed/deterministic, no tolerance.
 */
public class SimpleCashFlowSubclassesTest {

    public SimpleCashFlowSubclassesTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    // ------------------------------------------------------------------
    // amount() / date() pass-through (SimpleCashFlow semantics)
    // ------------------------------------------------------------------

    @Test
    public void testRedemptionAmountAndDate() {
        // C++ simplecashflow.hpp:61-70 — Redemption forwards (amount,date)
        // straight to SimpleCashFlow; no extra state.
        final Date d = new Date(15, Month.June, 2026);
        final Redemption r = new Redemption(100.0, d);
        assertEquals(100.0, r.amount(), 0.0);
        assertTrue(d.eq(r.date()));
    }

    @Test
    public void testAmortizingPaymentAmountAndDate() {
        // C++ simplecashflow.hpp:76-85 — AmortizingPayment forwards
        // (amount,date) straight to SimpleCashFlow; no extra state.
        final Date d = new Date(15, Month.June, 2026);
        final AmortizingPayment a = new AmortizingPayment(2500.0, d);
        assertEquals(2500.0, a.amount(), 0.0);
        assertTrue(d.eq(a.date()));
    }

    // ------------------------------------------------------------------
    // Visitor dispatch — the distinguishing behaviour
    // ------------------------------------------------------------------

    /**
     * A visitor that handles {@link Redemption} specifically dispatches to its {@code visit(Redemption)} — mirror of
     * C++ {@code Redemption::accept} resolving {@code dynamic_cast<Visitor<Redemption>*>} (simplecashflow.hpp:98-104).
     */
    @Test
    public void testRedemptionVisitorSpecificDispatch() {
        final RecordingVisitor rec = new RecordingVisitor();
        final Date d = new Date(15, Month.June, 2026);
        final Redemption r = new Redemption(100.0, d);

        r.accept(new RedemptionOnlyVisitor(rec));

        assertEquals("Redemption", rec.lastKind);
        assertSame(r, rec.lastVisited);
    }

    /**
     * When the visitor handles {@link SimpleCashFlow} (and any subclass, via {@code isAssignableFrom}), a
     * {@link Redemption} is dispatched to the {@code SimpleCashFlow} handler — mirror of C++ {@code Redemption::accept}
     * resolving as a {@code SimpleCashFlow} (simplecashflow.hpp:98-104).
     *
     * <p>Java-port note: JQuantLib's {@link PolymorphicVisitor} dispatch keys
     * on the cashflow's runtime {@code getClass()} (passed to {@code visitor(element)}) rather than on the visitor's
     * static type as C++ {@code dynamic_cast} does. A generically-written visitor therefore resolves the subtype via
     * {@code SimpleCashFlow.class.isAssignableFrom(element)}.
     */
    @Test
    public void testRedemptionDispatchesAsSimpleCashFlow() {
        final RecordingVisitor rec = new RecordingVisitor();
        final Date d = new Date(15, Month.June, 2026);
        final Redemption r = new Redemption(100.0, d);

        r.accept(new SimpleCashFlowFamilyVisitor(rec));

        assertEquals("SimpleCashFlow", rec.lastKind);
        assertSame(r, rec.lastVisited);
    }

    /** {@link AmortizingPayment} — specific dispatch and SimpleCashFlow-family dispatch (simplecashflow.hpp:106-112). */
    @Test
    public void testAmortizingPaymentVisitorSpecificAndFamily() {
        final Date d = new Date(15, Month.June, 2026);
        final AmortizingPayment a = new AmortizingPayment(2500.0, d);

        final RecordingVisitor rec1 = new RecordingVisitor();
        a.accept(new AmortizingPaymentOnlyVisitor(rec1));
        assertEquals("AmortizingPayment", rec1.lastKind);
        assertSame(a, rec1.lastVisited);

        final RecordingVisitor rec2 = new RecordingVisitor();
        a.accept(new SimpleCashFlowFamilyVisitor(rec2));
        assertEquals("SimpleCashFlow", rec2.lastKind);
        assertSame(a, rec2.lastVisited);
    }

    /**
     * A {@link Redemption}-only visitor does NOT match a plain {@link SimpleCashFlow} (which is not a
     * {@code Redemption}); dispatch falls through the {@code accept} chain to {@code Event.accept}, which throws when no
     * handler resolves. This confirms the {@code Redemption} subtype distinction is real, not accidental.
     */
    @Test
    public void testPlainSimpleCashFlowIsNotARedemption() {
        final RecordingVisitor rec = new RecordingVisitor();
        final Date d = new Date(15, Month.June, 2026);
        final SimpleCashFlow scf = new SimpleCashFlow(100.0, d);

        boolean threw = false;
        try {
            scf.accept(new RedemptionOnlyVisitor(rec));
        } catch ( final RuntimeException e ) {
            // Event.accept(): "null event visitor" — no handler matched
            // SimpleCashFlow for a Redemption-only visitor.
            threw = true;
        }
        assertTrue("plain SimpleCashFlow must not resolve a Redemption-only visitor", threw);
        assertFalse("Redemption".equals(rec.lastKind));
    }

    // ------------------------------------------------------------------
    // test helpers
    // ------------------------------------------------------------------

    /** Records the last visited object and a tag describing which visit() overload fired. */
    private static final class RecordingVisitor {
        String lastKind = null;
        Object lastVisited = null;
    }

    private static final class RedemptionOnlyVisitor implements PolymorphicVisitor, Visitor< Redemption > {
        private final RecordingVisitor rec;

        RedemptionOnlyVisitor(final RecordingVisitor rec) {
            this.rec = rec;
        }

        @Override
        @SuppressWarnings("unchecked")
        public < T > Visitor< T > visitor(final Class< ? extends T > element) {
            if ( Redemption.class.isAssignableFrom(element) ) {
                return (Visitor< T >) this;
            }
            return null;
        }

        @Override
        public void visit(final Redemption element) {
            rec.lastKind = "Redemption";
            rec.lastVisited = element;
        }
    }

    private static final class AmortizingPaymentOnlyVisitor
            implements PolymorphicVisitor, Visitor< AmortizingPayment > {
        private final RecordingVisitor rec;

        AmortizingPaymentOnlyVisitor(final RecordingVisitor rec) {
            this.rec = rec;
        }

        @Override
        @SuppressWarnings("unchecked")
        public < T > Visitor< T > visitor(final Class< ? extends T > element) {
            if ( AmortizingPayment.class.isAssignableFrom(element) ) {
                return (Visitor< T >) this;
            }
            return null;
        }

        @Override
        public void visit(final AmortizingPayment element) {
            rec.lastKind = "AmortizingPayment";
            rec.lastVisited = element;
        }
    }

    private static final class SimpleCashFlowFamilyVisitor implements PolymorphicVisitor, Visitor< SimpleCashFlow > {
        private final RecordingVisitor rec;

        SimpleCashFlowFamilyVisitor(final RecordingVisitor rec) {
            this.rec = rec;
        }

        @Override
        @SuppressWarnings("unchecked")
        public < T > Visitor< T > visitor(final Class< ? extends T > element) {
            // Resolve a SimpleCashFlow handler for SimpleCashFlow and any of
            // its subclasses (Redemption / AmortizingPayment), mirroring the
            // C++ behaviour where Redemption::accept resolves as a
            // SimpleCashFlow when no Redemption-specific visitor exists.
            if ( SimpleCashFlow.class.isAssignableFrom(element) ) {
                return (Visitor< T >) this;
            }
            return null;
        }

        @Override
        public void visit(final SimpleCashFlow element) {
            rec.lastKind = "SimpleCashFlow";
            rec.lastVisited = element;
        }
    }

}
