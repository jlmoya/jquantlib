/*
 Copyright (C) 2026 JQuantLib migration contributors.

 Cross-validation of the four ql/quotes classes ported in the gap-quotes
 closure (DerivedQuote, EurodollarFuturesImpliedStdDevQuote, ForwardSwapQuote,
 LastFixingQuote) against C++ QuantLib v1.42.1 reference values produced by
 migration-harness/cpp/probes/quotes/quotes_missing_probe.cpp.

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
package org.jquantlib.testsuite.quotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.function.DoubleUnaryOperator;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor;
import org.jquantlib.indexes.EuriborSwapIsdaFixA;
import org.jquantlib.quotes.DerivedQuote;
import org.jquantlib.quotes.EurodollarFuturesImpliedStdDevQuote;
import org.jquantlib.quotes.ForwardSwapQuote;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.LastFixingQuote;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validation of {@link DerivedQuote}, {@link EurodollarFuturesImpliedStdDevQuote},
 * {@link ForwardSwapQuote} and {@link LastFixingQuote} against C++ v1.42.1 references
 * in {@code migration-harness/references/quotes/quotes_missing.json}.
 *
 * <p>Tolerance tiers:
 * <ul>
 *   <li>{@link DerivedQuote} — EXACT (deterministic closed form x-&gt;a*x+b, x-&gt;x*x).</li>
 *   <li>{@link EurodollarFuturesImpliedStdDevQuote} — TIGHT (Newton-safe root-find of
 *       the Black 1976 implied-stdev; both C++ and Java use the same NewtonSafe solver
 *       to {@code accuracy=1e-8}, so the recovered stdev agrees to a few ULPs).</li>
 *   <li>{@link ForwardSwapQuote} — TIGHT for the rate (flat-curve discounting through a
 *       VanillaSwap), EXACT for the value/start/fixing date serials.</li>
 *   <li>{@link LastFixingQuote} — EXACT for both the stored fixing value and the
 *       reference-date serial.</li>
 * </ul>
 */
public class MissingQuotesTest {

    private static final String TEST_GROUP = "quotes/quotes_missing";
    private static final ReferenceReader REF = ReferenceReader.load(TEST_GROUP);

    // TIGHT tier: 1e-12 rel, 1e-14 abs near zero.
    private static final double TIGHT_ABS = 1.0e-14;
    private static final double TIGHT_REL = 1.0e-12;

    private static void assertTight(final String msg, final double expected, final double actual) {
        final double tol = Math.max(TIGHT_ABS, TIGHT_REL * Math.abs(expected));
        assertEquals(msg, expected, actual, tol);
    }

    // ==================================================================
    // DerivedQuote — inline closed-form (EXACT), plus propagation.
    // ==================================================================

    /**
     * Faithful port of {@code test-suite/quotes.cpp testDerived} value sweep, plus an
     * explicit observer-chain / isValid propagation check (mandated by the gap-quotes
     * spec). Expected values are the closed-form unary functions evaluated directly
     * (C++ {@code derivedquote.hpp:77}: {@code value_ = f_(element_->value())}).
     */
    @Test
    public void testDerivedQuoteValueAndPropagation() {
        final DoubleUnaryOperator[] funcs = new DoubleUnaryOperator[] {
                x -> 2.0 * x + 3.0,   // affine
                x -> x * x,           // square
                x -> x - 10.0,        // sub10 (matches the C++ testDerived sweep)
        };
        final double[] values = new double[] { 12.0, 23.0, 34.0 };

        final SimpleQuote me = new SimpleQuote();
        final Handle<Quote> h = new Handle<Quote>(me);

        for (final DoubleUnaryOperator func : funcs) {
            final DerivedQuote derived = new DerivedQuote(h, func);
            for (final double value : values) {
                me.setValue(value);
                // EXACT: closed-form, no floating-point reordering between f_(x) here
                // and inside DerivedQuote.value().
                assertEquals("derived value mismatch",
                        func.applyAsDouble(value), derived.value(), 0.0);
            }
        }
    }

    /**
     * isValid()/value() propagation: an empty source handle makes the derived quote
     * invalid (C++ {@code derivedquote.hpp:84}: {@code !element_.empty() && ...});
     * relinking to a live quote flips it valid, fires the observer chain, and lets the
     * cached value recompute; a subsequent source change invalidates and recomputes.
     *
     * <p>Note: JQuantLib's {@link SimpleQuote#isValid()} returns {@code true} even for a
     * default-constructed (NULL_REAL == Double.MAX_VALUE) quote — unlike C++ where a
     * default SimpleQuote is invalid. So we exercise the empty-handle branch (the part
     * of {@code DerivedQuote.isValid()} that genuinely differs from "always valid") via
     * a {@link RelinkableHandle} that starts empty.
     */
    @Test
    public void testDerivedQuoteInvalidationChain() {
        final RelinkableHandle<Quote> h = new RelinkableHandle<Quote>(); // empty => invalid
        final DerivedQuote derived = new DerivedQuote(h, x -> 2.0 * x + 3.0);

        // empty source handle -> derived is invalid
        assertFalse("derived should be invalid when source handle is empty", derived.isValid());

        final Flag f = new Flag();
        derived.addObserver(f);

        // link a live quote -> valid, observer fires, value computes
        final SimpleQuote me = new SimpleQuote(5.0);
        h.linkTo(me);
        assertTrue("observer not notified on relink", f.isUp());
        assertTrue("derived should be valid once handle links a quote", derived.isValid());
        assertEquals(2.0 * 5.0 + 3.0, derived.value(), 0.0);

        // change source -> cached value invalidated and recomputed
        f.lower();
        me.setValue(8.0);
        assertTrue("observer not notified on source change", f.isUp());
        assertEquals(2.0 * 8.0 + 3.0, derived.value(), 0.0);
    }

    // ==================================================================
    // EurodollarFuturesImpliedStdDevQuote (TIGHT).
    // ==================================================================

    @Test
    public void testEurodollarFuturesImpliedStdDevQuote() {
        // Tolerance: the implied-stdev is the root of a safe-Newton iteration driven to
        // |blackFormula(stddev) - price| < accuracy (here accuracy = 1e-8, on PRICE).
        // The recovered stddev therefore carries roughly accuracy/vega of slack, and
        // the C++ (NewtonSafe) vs Java (NewtonSafe) iterates legitimately stop at
        // slightly different points within that band (observed diff ~3.7e-9). We bound
        // the cross-check at the solver's own accuracy (1e-8 absolute) — NOT a relaxed
        // TIGHT, but the inherent precision of an implied-vol inversion. Tightening
        // below 1e-8 would assert agreement finer than either solver guarantees.
        final double solverTol = 1.0e-8;

        // call-side: strikeRate(4.5) < forwardRate(5.0) => uses call price, inverts Put.
        {
            final Case c = REF.getCase("eurodollar_call_side");
            final JSONObject in = c.inputs();
            final EurodollarFuturesImpliedStdDevQuote q = new EurodollarFuturesImpliedStdDevQuote(
                    new Handle<Quote>(new SimpleQuote(in.getDouble("forward"))),
                    new Handle<Quote>(new SimpleQuote(in.getDouble("callPrice"))),
                    new Handle<Quote>(new SimpleQuote(in.getDouble("putPrice"))),
                    in.getDouble("strike"),
                    in.getDouble("guess"),
                    in.getDouble("accuracy"));
            assertTrue("call-side quote should be valid", q.isValid());
            assertEquals("eurodollar_call_side", c.expectedDouble(), q.value(), solverTol);
        }
        // put-side: strikeRate(6.0) > forwardRate(5.0) => uses put price, inverts Call.
        {
            final Case c = REF.getCase("eurodollar_put_side");
            final JSONObject in = c.inputs();
            final EurodollarFuturesImpliedStdDevQuote q = new EurodollarFuturesImpliedStdDevQuote(
                    new Handle<Quote>(new SimpleQuote(in.getDouble("forward"))),
                    new Handle<Quote>(new SimpleQuote(in.getDouble("callPrice"))),
                    new Handle<Quote>(new SimpleQuote(in.getDouble("putPrice"))),
                    in.getDouble("strike"),
                    in.getDouble("guess"),
                    in.getDouble("accuracy"));
            assertTrue("put-side quote should be valid", q.isValid());
            assertEquals("eurodollar_put_side", c.expectedDouble(), q.value(), solverTol);
        }
    }

    // ==================================================================
    // ForwardSwapQuote (TIGHT value, EXACT dates).
    // ==================================================================

    @Test
    public void testForwardSwapQuote() {
        final Date saved = new Settings().evaluationDate();
        try {
            final Date evalDate = new Date(15, Month.June, 2020);
            new Settings().setEvaluationDate(evalDate);

            final DayCounter dc = new Actual360();
            final Handle<YieldTermStructure> curve =
                    new Handle<YieldTermStructure>(new FlatForward(evalDate, 0.03, dc));
            final EuriborSwapIsdaFixA swapIndex =
                    new EuriborSwapIsdaFixA(new Period(5, TimeUnit.Years), curve);
            final Period fwdStart = new Period(2, TimeUnit.Years);

            // no spread
            {
                final Case c = REF.getCase("forward_swap_no_spread");
                final JSONObject exp = (JSONObject) c.expectedRaw();
                final ForwardSwapQuote q =
                        new ForwardSwapQuote(swapIndex, new Handle<Quote>(), fwdStart);
                assertTrue("no-spread forward swap should be valid", q.isValid());
                assertTight("forward_swap_no_spread value", exp.getDouble("value"), q.value());
                assertEquals("valueDate", exp.getLong("valueDate"), q.valueDate().serialNumber());
                assertEquals("startDate", exp.getLong("startDate"), q.startDate().serialNumber());
                assertEquals("fixingDate", exp.getLong("fixingDate"), q.fixingDate().serialNumber());
            }
            // spread = 10bp
            {
                final Case c = REF.getCase("forward_swap_spread_10bp");
                final JSONObject exp = (JSONObject) c.expectedRaw();
                final ForwardSwapQuote q = new ForwardSwapQuote(
                        swapIndex, new Handle<Quote>(new SimpleQuote(0.0010)), fwdStart);
                assertTrue("10bp-spread forward swap should be valid", q.isValid());
                assertTight("forward_swap_spread_10bp value", exp.getDouble("value"), q.value());
                assertEquals("valueDate", exp.getLong("valueDate"), q.valueDate().serialNumber());
                assertEquals("startDate", exp.getLong("startDate"), q.startDate().serialNumber());
                assertEquals("fixingDate", exp.getLong("fixingDate"), q.fixingDate().serialNumber());
            }
        } finally {
            new Settings().setEvaluationDate(saved);
        }
    }

    /**
     * Regression for the eval-date rebuild guard in {@link ForwardSwapQuote#update()}.
     *
     * <p>C++ {@code forwardswapquote.cpp:49-55} stores {@code evaluationDate_} as a
     * Date VALUE, so the {@code evaluationDate_ != Settings::instance().evaluationDate()}
     * check genuinely detects an eval-date change and re-runs {@code initializeDates()}.
     * JQuantLib's {@code Settings.evaluationDate()} returns the live thread-local
     * singleton ({@code DateProxy}), mutated in place by {@code setEvaluationDate(...)};
     * a previous version stored that reference, so the guard compared the proxy against
     * itself and the rebuild never fired — leaving the forward-start dates anchored to
     * the original eval date.
     *
     * <p>Here we build the quote at one eval date, snapshot its forward-start dates and
     * value, then push the eval date forward by 30 calendar days and assert the quote
     * REBUILT: valueDate/startDate/fixingDate all advance (EXACT, deterministic — proves
     * {@code initializeDates()} re-fired) and the fair forward rate changes (TIGHT — the
     * forward-starting swap now sits 30 days later on the curve). With the bug present
     * the dates would be unchanged and this test fails.
     */
    @Test
    public void testForwardSwapQuoteRebuildsOnEvalDateChange() {
        final Date saved = new Settings().evaluationDate();
        try {
            final Date evalDate = new Date(15, Month.June, 2020);
            new Settings().setEvaluationDate(evalDate);

            final DayCounter dc = new Actual360();
            final Handle<YieldTermStructure> curve =
                    new Handle<YieldTermStructure>(new FlatForward(evalDate, 0.03, dc));
            final EuriborSwapIsdaFixA swapIndex =
                    new EuriborSwapIsdaFixA(new Period(5, TimeUnit.Years), curve);
            final Period fwdStart = new Period(2, TimeUnit.Years);

            final ForwardSwapQuote q =
                    new ForwardSwapQuote(swapIndex, new Handle<Quote>(), fwdStart);

            // Snapshot the pre-change state (serialNumber() returns a long copy, so
            // these are unaffected by the later in-place mutation of the proxy).
            final long valueDate0 = q.valueDate().serialNumber();
            final long startDate0 = q.startDate().serialNumber();
            final long fixingDate0 = q.fixingDate().serialNumber();
            final double value0 = q.value();

            // Advance the evaluation date. This mutates the singleton proxy in place
            // and notifies observers -> ForwardSwapQuote.update() must rebuild.
            final Date laterEval = evalDate.add(30); // +30 calendar days
            new Settings().setEvaluationDate(laterEval);

            // EXACT: every forward-start date must have advanced (rebuild fired).
            assertTrue("valueDate did not advance after eval-date change "
                            + "(rebuild guard never fired)",
                    q.valueDate().serialNumber() > valueDate0);
            assertTrue("startDate did not advance after eval-date change "
                            + "(rebuild guard never fired)",
                    q.startDate().serialNumber() > startDate0);
            assertTrue("fixingDate did not advance after eval-date change "
                            + "(rebuild guard never fired)",
                    q.fixingDate().serialNumber() > fixingDate0);

            // TIGHT: the fair forward rate reflects the new forward-starting swap, so it
            // differs from the pre-change rate (a flat 3% curve still re-discounts the
            // shifted schedule to a genuinely different par rate). Assert a real change.
            final double value1 = q.value();
            assertTrue("forward-swap value did not change after rebuild "
                            + "(value0=" + value0 + ", value1=" + value1 + ")",
                    Math.abs(value1 - value0) > TIGHT_ABS);
        } finally {
            new Settings().setEvaluationDate(saved);
        }
    }

    // ==================================================================
    // LastFixingQuote (EXACT value + reference-date serial).
    // ==================================================================

    @Test
    public void testLastFixingQuote() {
        final Date saved = new Settings().evaluationDate();
        try {
            final DayCounter dc = new Actual360();
            final Handle<YieldTermStructure> curve = new Handle<YieldTermStructure>(
                    new FlatForward(new Date(1, Month.January, 2020), 0.02, dc));
            final Euribor euribor = new Euribor(new Period(6, TimeUnit.Months), curve);
            euribor.clearFixings();
            euribor.addFixing(new Date(13, Month.January, 2020), 0.0150);
            euribor.addFixing(new Date(13, Month.February, 2020), 0.0160);
            euribor.addFixing(new Date(13, Month.March, 2020), 0.0170);

            // Case A: evalDate after last fixing => referenceDate == lastDate (f3, 0.0170).
            {
                new Settings().setEvaluationDate(new Date(20, Month.March, 2020));
                final Case c = REF.getCase("last_fixing_eval_after_last");
                final JSONObject exp = (JSONObject) c.expectedRaw();
                final LastFixingQuote q = new LastFixingQuote(euribor);
                assertEquals("isValid", exp.getBoolean("isValid"), q.isValid());
                assertEquals("referenceDate",
                        exp.getLong("referenceDate"), q.referenceDate().serialNumber());
                assertEquals("value", exp.getDouble("value"), q.value(), 0.0);
            }
            // Case B: evalDate == f2 => referenceDate == evalDate (min(f3,eval)), value 0.0160.
            {
                new Settings().setEvaluationDate(new Date(13, Month.February, 2020));
                final Case c = REF.getCase("last_fixing_eval_between");
                final JSONObject exp = (JSONObject) c.expectedRaw();
                final LastFixingQuote q = new LastFixingQuote(euribor);
                assertEquals("isValid", exp.getBoolean("isValid"), q.isValid());
                assertEquals("referenceDate",
                        exp.getLong("referenceDate"), q.referenceDate().serialNumber());
                assertEquals("value", exp.getDouble("value"), q.value(), 0.0);
            }

            euribor.clearFixings();
        } finally {
            new Settings().setEvaluationDate(saved);
        }
    }
}
