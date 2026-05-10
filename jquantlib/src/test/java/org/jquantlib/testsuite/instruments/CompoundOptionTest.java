/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.CompoundOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticCompoundOptionEngine;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.testsuite.util.Utilities;
import org.jquantlib.time.Date;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5k port of {@code test-suite/compoundoption.cpp} v1.42.1
 * (346 LOC, 2 cases).
 *
 * <p>Exercises the compound option (option-on-option, Geske 1979 / Wystup
 * 2002): put-call parity across all four child/mother combinations and
 * the Haug 2007 / Hull 2009 / Wystup 2002 analytic-engine reference values.
 *
 * <p><strong>Phase 4h.5 partial: testValues bodied (single anchor case)</strong>
 * — uses the newly ported {@link AnalyticCompoundOptionEngine} +
 * {@link CompoundOption}. Full 21-case coverage and put-call parity stay
 * Phase 5k.5 carry-forwards.
 *
 * <p>Source: {@code test-suite/compoundoption.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CompoundOptionTest {

    public CompoundOptionTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    private static final String REASON_PARITY =
            "Phase 5k.5 — full put-call parity sweep across all four mother/daughter "
          + "type combinations (11-row matrix in C++ test); single-case bodied here.";

    /**
     * Mirrors anchor case from C++ test-suite/compoundoption.cpp::testValues
     * (first row of the 21-row matrix). Source: Haug 2007. Tolerance 1e-3
     * per C++ comment ("price/theta is very sensitive with respect to the
     * implementation of the bivariate normal").
     * <p>
     * Inputs: Put-on-Call, strikeMother=50, strikeDaughter=520, S=500,
     * q=0.03, r=0.08, t_mother=0.25y, t_daughter=0.5y, vol=0.35.
     * Expected NPV=21.1965, delta=-0.1966, gamma=0.0007.
     */
    @Test
    public void testValues() {
        QL.info("Testing compound-option NPV (Haug 2007 anchor case)...");

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spot = new SimpleQuote(500.0);
        final SimpleQuote rRate = new SimpleQuote(0.08);
        final SimpleQuote qRate = new SimpleQuote(0.03);
        final SimpleQuote vol = new SimpleQuote(0.35);

        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        // Mother: put with strike 50; Daughter: call with strike 520
        final StrikedTypePayoff payoffMother = new PlainVanillaPayoff(Option.Type.Put, 50.0);
        final StrikedTypePayoff payoffDaughter = new PlainVanillaPayoff(Option.Type.Call, 520.0);

        final Date matDateMom = today.add(timeToDays(0.25));
        final Date matDateDaughter = today.add(timeToDays(0.5));

        final Exercise exerciseMother = new EuropeanExercise(matDateMom);
        final Exercise exerciseDaughter = new EuropeanExercise(matDateDaughter);

        final CompoundOption option = new CompoundOption(payoffMother, exerciseMother,
                                                         payoffDaughter, exerciseDaughter);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final PricingEngine engine = new AnalyticCompoundOptionEngine(stochProcess);
        option.setPricingEngine(engine);

        final double calculated = option.NPV();
        final double expected = 21.1965; // Haug 2007
        final double tolerance = 1.0e-3;
        assertEquals("Compound NPV (Haug 2007 anchor case)", expected, calculated, tolerance);
    }

    @Ignore(REASON_PARITY) @Test public void testPutCallParity() { fail("not implemented"); }

    private static int timeToDays(final double t) {
        // Match C++ utility used in QuantLib test-suite (rounds up):
        // Date matDateMom = today + timeToDays(value.tMother);
        return (int) (t * 360 + 0.5);
    }
}
