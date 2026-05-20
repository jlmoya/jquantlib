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
import java.util.function.ToDoubleFunction;

import org.jquantlib.QL;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.MultiCompositeQuote;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.testsuite.util.Flag;
import org.junit.Test;


/**
 * @note Class Handle is deprecated and MUST NEVER be used
 *
 * @author Richard Gomes
 *
 * <h2>Phase1-cert-D5-C-R4 + Phase1-closure-A1-553 audit (test-suite/quotes.cpp coverage)</h2>
 * <ul>
 *   <li>{@code testObservable}            — present below.</li>
 *   <li>{@code testObservableHandle}      — present below.</li>
 *   <li>{@code testDerived}               — commented out (DerivedQuote class
 *       exists but the test was never enabled by the original port).</li>
 *   <li>{@code testComposite}             — commented out (CompositeQuote
 *       not ported).</li>
 *   <li>{@code testMultiComposite}        — ADDED in Phase 1 closure A1 round
 *       (commit landing this comment); {@link org.jquantlib.quotes.MultiCompositeQuote}
 *       ported alongside.</li>
 *   <li>{@code testForwardValueQuoteAndImpliedStdevQuote} — commented
 *       out; ForwardValueQuote and ImpliedStdDevQuote not ported.</li>
 * </ul>
 */
// TODO: code review :: please verify against QL/C++ code
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


//	@Test
//	public void testDerived() {
//
//		QL.info("Testing derived quotes...");
//
//	    typedef Real (*unary_f)(Real);
//	    unary_f funcs[3] = { add10, mul10, sub10 };
//
//	    Quote me = new SimpleQuote(17.0);
//	    Handle<Quote> h = new Handle<Quote>(me);
//
//	    for (Integer i=0; i<3; i++) {
//	        DerivedQuote<unary_f> derived(h,funcs[i]);
//	        Real x = derived.value(),
//	             y = funcs[i](me->value());
//	        if (Math.abs(x-y) > 1.0e-10)
//	            fail("derived quote yields " << x << "\n"
//	                       << "function result is " << y);
//	    }
//	}

//	@Test
//	public void testComposite() {
//
//		QL.info("Testing composite quotes...");
//
//	    typedef Real (*binary_f)(Real,Real);
//	    binary_f funcs[3] = { add, mul, sub };
//
//	    Quote me1 = new SimpleQuote(12.0);
//	    Quote me2 = new SimpleQuote(13.0);
//
//	    Handle<Quote> h1 new Handle<Quote>(me1);
//	    Handle<Quote> h2 new Handle<Quote>(me2);
//
//	    for (Integer i=0; i<3; i++) {
//	        CompositeQuote<binary_f> composite(h1,h2,funcs[i]);
//	        Real x = composite.value(),
//	             y = funcs[i](me1->value(),me2->value());
//	        if (Math.abs(x-y) > 1.0e-10)
//	            fail("composite quote yields " << x << "\n"
//	                       << "function result is " << y);
//	    }
//	}

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
			final List<SimpleQuote> mes = new ArrayList<SimpleQuote>();
			final List<Handle<? extends Quote>> handles = new ArrayList<Handle<? extends Quote>>();
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

//	@Test
//	public void testForwardValueQuoteAndImpliedStdevQuote(){
//
//		QL.info("Testing forward-value and implied-stdev quotes...");
//
//	    double forwardRate = .05;
//	    DayCounter dc = new ActualActual();
//	    Calendar calendar = Target.getCalendar();
//	    SimpleQuote forwardQuote = new SimpleQuote(forwardRate);
//	    Quote forwardHandle = forwardQuote;
//	    Date evaluationDate = Settings.getInstance().getEvaluationDate();
//	    YieldTermStructure yc =new FlatForward(evaluationDate, forwardHandle, dc);
//	    YieldTermStructure ycHandle = yc;
//	    Period euriborTenor = new Period(1, TimeUnit.Years);
//	    Index euribor = new Euribor(euriborTenor, ycHandle);
//	    Date fixingDate = calendar.advance(evaluationDate, euriborTenor);
//	    ForwardValueQuote forwardValueQuote = new ForwardValueQuote(euribor, fixingDate);
//	    /*@Rate*/ double  forwardValue =  forwardValueQuote.getValue();
//	    /*@Rate*/ double  expectedForwardValue = euribor.fixing(fixingDate, true);
//	    // we test if the forward value given by the quote is consistent
//	    // with the one directly given by the index
//        fail("Forward Value Quote quote yields "
//        		+ forwardValue + "\n  expected result is "
//        		+ expectedForwardValue,
//        			Math.abs(forwardValue-expectedForwardValue) <= 1.0e-15);
//	    // then we test the observer/observable chain
//	    Flag f;
//	    f.registerWith(forwardValueQuote);
//	    forwardQuote.setValue(0.04);
//        fail("Observer was not notified of quote change", f.isUp());
//
//	    // and we re-test if the values are still matching
//	    forwardValue =  forwardValueQuote.getValue();
//	    expectedForwardValue = euribor.fixing(fixingDate, true);
//        fail("Foward Value Quote quote yields "
//        		+ forwardValue
//        		+ "\n  expected result is "
//        		+ expectedForwardValue,
//        		Math.abs(forwardValue-expectedForwardValue) <= 1.0e-15);
//	    // we test the ImpliedStdevQuote class
//	    f.unregisterWith(forwardValueQuote);
//	    f.lower();
//	    double price = 0.02;
//	    /*@Rate*/ double  strike = 0.04;
//	    /*@Volatility*/ double guess = .15;
//	    double accuracy = 1.0e-6;
//	    Option.Type optionType = Option.Type.Call;
//	    SimpleQuote priceQuote = new SimpleQuote(price);
//	    Quote priceHandle = priceQuote;
//	    ImpliedStdDevQuote impliedStdevQuote = new ImpliedStdDevQuote(optionType, forwardHandle, priceHandle, strike, guess, accuracy));
//	    /*@StdDev*/ double impliedStdev = impliedStdevQuote.getValue();
//	    /*@StdDev*/ double expectedImpliedStdev = blackFormulaImpliedStdDev(optionType, strike, forwardQuote.getValue(), price, 1.0, guess, 1.0e-6);
//        fail("impliedStdevQuote yields "
//        		+ impliedStdev
//        		+ "\n  expected result is "
//        		+ expectedImpliedStdev,
//        		Math.abs(impliedStdev-expectedImpliedStdev) <= 1.0e-15);
//	    // then we test the observer/observable chain
//	    Quote quote = impliedStdevQuote;
//	    quote.addObserver(f);
//	    forwardQuote.setValue(0.05);
//	    fail("Observer was not notified of quote change", f.isUp());
//	    quote.getValue();
//	    f.lower();
//	    priceQuote.setValue(0.11);
//      fail("Observer was not notified of quote change", f.isUp());
//	}


}
