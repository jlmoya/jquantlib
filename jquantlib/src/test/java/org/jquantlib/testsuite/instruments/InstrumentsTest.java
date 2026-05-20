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

/*
 Copyright (C) 2003 RiskMap srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
 */

package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.CompositeInstrument;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.Stock;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Flag;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Mirrors {@code test-suite/instruments.cpp} (QuantLib v1.42.1).
 *
 * <h2>Phase1-cert-D5-C-R4 + Phase1-closure-A3-E-v2-557</h2>
 * <ul>
 *   <li>{@code testObservable} — present below.</li>
 *   <li>{@code testCompositeWhenShiftingDates} — RE-ENABLED in this revision.
 *       The earlier BLOCKED note (Phase 1 closure A1-553-part2) suspected a
 *       Java observer-chain quirk, but a probe showed the chain works correctly
 *       <em>provided</em> term structures are built via the floating-reference
 *       (settlement-days) constructor — which registers with
 *       {@link org.jquantlib.Settings} per
 *       {@link org.jquantlib.termstructures.AbstractTermStructure}. Using
 *       {@link org.jquantlib.testsuite.util.Utilities#flatRate(double, org.jquantlib.daycounters.DayCounter)}
 *       and {@link org.jquantlib.testsuite.util.Utilities#flatVol(double, org.jquantlib.daycounters.DayCounter)}
 *       mirrors {@code flatRate(0.0, dc)} / {@code flatVol(0.1, dc)} in
 *       {@code test-suite/utilities.cpp} (settlement-days form). No production
 *       fix was required.</li>
 * </ul>
 */
public class InstrumentsTest {

    public InstrumentsTest() {
        QL.info("::::: "+this.getClass().getSimpleName()+" :::::");
    }

    @Test
    public void testObservable() {

        QL.info("Testing observability of instruments...");


        final SimpleQuote me1 = new SimpleQuote(0.0);
        final RelinkableHandle<Quote>  h = new RelinkableHandle<Quote>(me1);
        final Instrument s = new Stock(h);

        final Flag f = new Flag();
        s.addObserver(f); //f.registerWith(s);

        s.NPV();
        me1.setValue(3.14);
        if (!f.isUp()) {
            fail("Observer was not notified of instrument change");
        }

        s.NPV();
        f.lower();
        final SimpleQuote me2 = new SimpleQuote(0.0);

        h.linkTo(me2);
        if (!f.isUp()) {
            fail("Observer was not notified of instrument change");
        }

        f.lower();
        s.freeze();
        s.NPV();
        me2.setValue(2.71);
        if (f.isUp()) {
            fail("Observer was notified of frozen instrument change");
        }

        s.NPV();
        s.unfreeze();
        if (!f.isUp()) {
            fail("Observer was not notified of instrument change");
        }
    }

    @Test
    public void testCompositeWhenShiftingDates() {
        QL.info("Testing reaction of composite instrument to date changes...");

        // Snapshot the evaluation date as a value, not a reference into Settings —
        // Settings.evaluationDate() returns the live DateProxy, so without clone()
        // a later setEvaluationDate(...) would also mutate this local.
        final Date today = new Settings().evaluationDate().clone();
        final DayCounter dc = new Actual360();

        final StrikedTypePayoff payoff = new PlainVanillaPayoff(Option.Type.Call, 100.0);
        final Exercise exercise = new EuropeanExercise(today.clone().addAssign(new Period(30, TimeUnit.Days)));

        final VanillaOption option = new EuropeanOption(payoff, exercise);

        final SimpleQuote spot = new SimpleQuote(100.0);
        // C++ uses flatRate(0.0, dc) / flatVol(0.1, dc) — floating-reference form
        // that registers with Settings; required so that Settings changes
        // propagate through TS → process → engine → option → composite.
        final YieldTermStructure qTS = Utilities.flatRate(0.0, dc);
        final YieldTermStructure rTS = Utilities.flatRate(0.01, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(0.1, dc);

        final BlackScholesMertonProcess process =
            new BlackScholesMertonProcess(new Handle<Quote>(spot),
                                          new Handle<YieldTermStructure>(qTS),
                                          new Handle<YieldTermStructure>(rTS),
                                          new Handle<BlackVolTermStructure>(volTS));
        final PricingEngine engine = new AnalyticEuropeanEngine(process);

        option.setPricingEngine(engine);

        final CompositeInstrument composite = new CompositeInstrument();
        composite.add(option);

        new Settings().setEvaluationDate(today.clone().addAssign(new Period(45, TimeUnit.Days)));

        if (!composite.isExpired()) {
            fail("Composite didn't detect expiration");
        }
        if (composite.NPV() != 0.0) {
            fail("Composite didn't return a null NPV");
        }

        new Settings().setEvaluationDate(today);

        if (composite.isExpired()) {
            fail("Composite didn't detect aliveness");
        }
        if (composite.NPV() == 0.0) {
            fail("Composite didn't recalculate");
        }
    }

}
