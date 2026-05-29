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

package org.jquantlib.testsuite.quotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.ToDoubleFunction;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.indexes.Euribor;
import org.jquantlib.indexes.Index;
import org.jquantlib.instruments.Option;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.CompositeQuote;
import org.jquantlib.quotes.ForwardValueQuote;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.ImpliedStdDevQuote;
import org.jquantlib.quotes.MultiCompositeQuote;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;


/**
 * @note Class Handle is deprecated and MUST NEVER be used
 *
 * @author Richard Gomes
 *
 * <h2>Phase1-cert-D5-C-R4 + Phase1-closure-A1-553 + Phase1.3-B audit (test-suite/quotes.cpp coverage)</h2>
 * <ul>
 *   <li>{@code testObservable}            — present below.</li>
 *   <li>{@code testObservableHandle}      — present below.</li>
 *   <li>{@code testDerived}               — commented out (DerivedQuote class
 *       exists but the test was never enabled by the original port).</li>
 *   <li>{@code testComposite}             — ADDED in Phase 1.3 closure (round B);
 *       {@link org.jquantlib.quotes.CompositeQuote} (binary form) ported alongside.</li>
 *   <li>{@code testMultiComposite}        — ADDED in Phase 1 closure A1 round;
 *       {@link org.jquantlib.quotes.MultiCompositeQuote} ported alongside.</li>
 *   <li>{@code testForwardValueQuoteAndImpliedStdevQuote} — ADDED in Phase 1.3
 *       closure (round B); {@link org.jquantlib.quotes.ForwardValueQuote} and
 *       {@link org.jquantlib.quotes.ImpliedStdDevQuote} ported alongside.</li>
 * </ul>
 */
// TODO: code review :: please verify against QL/C++ code
@SuppressWarnings("unchecked")
public class QuotesTest {

	public QuotesTest() {
		QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
	}

//	private double add10(final double x) { return x+10; }
//	private double mul10(final double x) { return x*10; }
//	private double sub10(final double x) { return x-10; }
//
//	private double add(final double x, final double y) { return x+y; }
//	private double mul(final double x, final double y) { return x*y; }
//	private double sub(final double x, final double y) { return x-y; }

	@Test
	public void testObservable() {

	    QL.info("Testing observability of quotes...");

	    final SimpleQuote me = new SimpleQuote(0.0);
	    final Flag f = new Flag();
	    me.addObserver(f);
	    me.setValue(3.14);
	    if (!f.isUp()) {
            fail("Observer was not notified of quote change");
        }
	}

	@Test
	public void testObservableHandle() {

		QL.info("Testing observability of quote handles...");

	    final SimpleQuote me1 = new SimpleQuote(0.0);
	    final RelinkableHandle<Quote> h = new RelinkableHandle(me1);

	    final Flag f = new Flag();
	    h.addObserver(f);

	    me1.setValue(3.14);
	    if (!f.isUp()) {
            fail("Observer was not notified of quote change");
        }

	    f.lower();
	    final SimpleQuote me2 = new SimpleQuote(0.0);

	    h.linkTo(me2);
	    if (!f.isUp()) {
            fail("Observer was not notified of quote change");
        }
	}


	/**
	 * Faithful port of {@code test-suite/quotes.cpp BOOST_AUTO_TEST_CASE(testDerived)}
	 * (v1.42.1). Verifies that {@link DerivedQuote} applied to a {@link SimpleQuote}
	 * handle with the {@code add10}, {@code mul10}, and {@code sub10} unary functors
	 * matches direct computation across a sweep of underlying values.
	 *
	 * <p>Ported alongside {@link DerivedQuote} (gap-quotes closure). The C++ test uses
	 * function pointers {@code Real(*)(Real)}; the Java analogue is
	 * {@link java.util.function.DoubleUnaryOperator}.
	 */
	@Test
	public void testDerived() {

		QL.info("Testing derived quotes...");

		final java.util.function.DoubleUnaryOperator[] funcs =
				new java.util.function.DoubleUnaryOperator[] {
						x -> x + 10.0,
						x -> x * 10.0,
						x -> x - 10.0,
				};
		final double[] values = new double[] { 12.0, 23.0, 34.0 };

		final SimpleQuote me = new SimpleQuote();
		final Handle<Quote> h = new Handle<Quote>(me);

		for (final java.util.function.DoubleUnaryOperator func : funcs) {
			final org.jquantlib.quotes.DerivedQuote derived =
					new org.jquantlib.quotes.DerivedQuote(h, func);
			for (final double value : values) {
				me.setValue(value);
				final double x = derived.value();
				final double y = func.applyAsDouble(value);
				if (Math.abs(x - y) > 1.0e-10) {
					fail("derived quote yields " + x + ", function result is " + y);
				}
			}
		}
	}

	/**
	 * Faithful port of {@code test-suite/quotes.cpp BOOST_AUTO_TEST_CASE(testComposite)}
	 * (v1.42.1). Verifies that {@link CompositeQuote} (binary form) applied to a pair of
	 * handles with {@code add}, {@code mul}, and {@code sub} binary functors matches direct
	 * computation across a sweep of underlying values.
	 *
	 * <p>Ported alongside {@link CompositeQuote} in Phase 1.3 closure (round B).
	 */
	@Test
	public void testComposite() {

		QL.info("Testing composite quotes...");

		final DoubleBinaryOperator[] funcs = new DoubleBinaryOperator[] {
				(a, b) -> a + b,
				(a, b) -> a * b,
				(a, b) -> a - b,
		};
		final double[] values = new double[] { 12.0, 23.0, 34.0 };

		final SimpleQuote me1 = new SimpleQuote();
		final SimpleQuote me2 = new SimpleQuote();
		final Handle<Quote> h1 = new Handle<Quote>(me1);
		final Handle<Quote> h2 = new Handle<Quote>(me2);

		for (final DoubleBinaryOperator func : funcs) {
			final CompositeQuote composite = new CompositeQuote(h1, h2, func);
			for (final double value : values) {
				me1.setValue(value);
				me2.setValue(value + 1);
				final double x = composite.value();
				final double y = func.applyAsDouble(value, value + 1);
				if (Math.abs(x - y) > 1.0e-10) {
					fail("composite quote yields " + x + ", function result is " + y);
				}
			}
		}
	}

	/**
	 * Faithful port of {@code test-suite/quotes.cpp BOOST_AUTO_TEST_CASE(testMultiComposite)}
	 * (v1.42.1). Verifies that {@link MultiCompositeQuote} applied to an N-handle list with
	 * {@code addAll}, {@code mulAll}, and {@code norm2} array functors matches direct
	 * computation across all underlying-quote value updates, and that {@code inputValue(i)}
	 * exposes the current i-th underlying value.
	 */
	@Test
	public void testMultiComposite() {
		QL.info("Testing multi composite quotes...");

		@SuppressWarnings("unchecked")
		final ToDoubleFunction<double[]>[] funcs = new ToDoubleFunction[] {
			(ToDoubleFunction<double[]>) (a -> {
				double s = 0.0;
				for (final double v : a) {
					s += v;
				}
				return s;
			}),
			(ToDoubleFunction<double[]>) (a -> {
				double p = 1.0;
				for (final double v : a) {
					p *= v;
				}
				return p;
			}),
			(ToDoubleFunction<double[]>) (a -> {
				double s = 0.0;
				for (final double v : a) {
					s += v * v;
				}
				return Math.sqrt(s);
			}),
		};

		for (final ToDoubleFunction<double[]> func : funcs) {
			final List<SimpleQuote> mes = new ArrayList<>();
			final List<Handle<? extends Quote>> handles = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				mes.add(new SimpleQuote(i + 1));
				handles.add(new Handle<SimpleQuote>(mes.get(mes.size() - 1)));
				final MultiCompositeQuote composite = new MultiCompositeQuote(handles, func);
				for (int j = 0; j <= i; j++) {
					mes.get(j).setValue(j * 10 + 1);
					final double[] args = new double[mes.size()];
					for (int k = 0; k < mes.size(); k++) {
						args[k] = mes.get(k).value();
					}
					final double x = composite.value();
					final double y = func.applyAsDouble(args);
					if (Math.abs(x - y) > 1.0e-10) {
						fail("composite quote yields " + x + ", function result is " + y);
					}
					for (int k = 0; k < mes.size(); k++) {
						assertEquals(mes.get(k).value(), composite.inputValue(k), 0.0);
					}
				}
			}
		}
	}

	/**
	 * Faithful port of {@code test-suite/quotes.cpp BOOST_AUTO_TEST_CASE(testForwardValueQuoteAndImpliedStdevQuote)}
	 * (v1.42.1). Exercises {@link ForwardValueQuote} and {@link ImpliedStdDevQuote}:
	 * <ul>
	 *   <li>Forward-value quote agrees with {@code Euribor.fixing()} on a flat-forward curve.</li>
	 *   <li>Observer chain fires when the underlying forward-rate quote moves.</li>
	 *   <li>Implied-stdev quote matches {@link BlackFormula#blackFormulaImpliedStdDev}.</li>
	 *   <li>Observer chain fires for both forward and price quote moves.</li>
	 * </ul>
	 *
	 * <p>Ported alongside {@link ForwardValueQuote} and {@link ImpliedStdDevQuote} in Phase 1.3 closure (round B).
	 */
	@Test
	public void testForwardValueQuoteAndImpliedStdevQuote() {

		QL.info("Testing forward-value and implied-standard-deviation quotes...");

		final double forwardRate = 0.05;
		final DayCounter dc = new ActualActual(ActualActual.Convention.ISDA);
		final Calendar calendar = new Target();
		final SimpleQuote forwardQuote = new SimpleQuote(forwardRate);
		final Handle<Quote> forwardHandle = new Handle<Quote>(forwardQuote);
		final Date evaluationDate = new Settings().evaluationDate();
		final YieldTermStructure yc = new FlatForward(evaluationDate, forwardHandle, dc);
		final Handle<YieldTermStructure> ycHandle = new Handle<YieldTermStructure>(yc);
		final Period euriborTenor = new Period(1, TimeUnit.Years);
		final Index euribor = new Euribor(euriborTenor, ycHandle);
		final Date fixingDate = calendar.advance(evaluationDate, euriborTenor);
		final ForwardValueQuote forwardValueQuote = new ForwardValueQuote(euribor, fixingDate);
		double forwardValue = forwardValueQuote.value();
		double expectedForwardValue = euribor.fixing(fixingDate, true);
		// the forward value given by the quote is consistent with the
		// one directly given by the index
		if (Math.abs(forwardValue - expectedForwardValue) > 1.0e-15) {
			fail("Foward Value Quote quote yields " + forwardValue
					+ "\nexpected result is " + expectedForwardValue);
		}

		// then we test the observer/observable chain
		final Flag f = new Flag();
		forwardValueQuote.addObserver(f);
		forwardQuote.setValue(0.04);
		if (!f.isUp()) {
			fail("Observer was not notified of quote change");
		}

		// and we retest if the values are still matching
		forwardValue = forwardValueQuote.value();
		expectedForwardValue = euribor.fixing(fixingDate, true);
		if (Math.abs(forwardValue - expectedForwardValue) > 1.0e-15) {
			fail("Foward Value Quote quote yields " + forwardValue
					+ "\nexpected result is " + expectedForwardValue);
		}

		// we test the ImpliedStdevQuote class
		forwardValueQuote.deleteObserver(f);
		f.lower();
		final double price = 0.02;
		final double strike = 0.04;
		final double guess = 0.15;
		final double accuracy = 1.0e-6;
		final Option.Type optionType = Option.Type.Call;
		final SimpleQuote priceQuote = new SimpleQuote(price);
		final Handle<Quote> priceHandle = new Handle<Quote>(priceQuote);
		final ImpliedStdDevQuote impliedStdevQuote = new ImpliedStdDevQuote(
				optionType, forwardHandle, priceHandle, strike, guess, accuracy);
		final double impliedStdev = impliedStdevQuote.value();
		final double expectedImpliedStdev = BlackFormula.blackFormulaImpliedStdDev(
				optionType, strike, forwardQuote.value(), price, 1.0, guess, 1.0e-6, 0.0);
		if (Math.abs(impliedStdev - expectedImpliedStdev) > 1.0e-15) {
			fail("\nimpliedStdevQuote yields :" + impliedStdev
					+ "\nexpected result is       :" + expectedImpliedStdev);
		}

		// then we test the observer/observable chain
		impliedStdevQuote.addObserver(f);
		forwardQuote.setValue(0.05);
		if (!f.isUp()) {
			fail("Observer was not notified of quote change");
		}
		impliedStdevQuote.value();
		f.lower();
		impliedStdevQuote.value();
		priceQuote.setValue(0.11);
		if (!f.isUp()) {
			fail("Observer was not notified of quote change");
		}
	}


}
