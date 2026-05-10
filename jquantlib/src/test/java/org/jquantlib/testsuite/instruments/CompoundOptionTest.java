/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;

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
import org.junit.Test;

/* Helper struct mirroring C++ CompoundOptionData. */
class CompoundOptionData {
    final Option.Type typeMother;
    final Option.Type typeDaughter;
    final double strikeMother;
    final double strikeDaughter;
    final double s;        // spot
    final double q;        // dividend
    final double r;        // risk-free rate
    final double tMother;  // time to maturity
    final double tDaughter;
    final double v;        // volatility
    final double npv;      // expected NPV
    final double tol;      // tolerance
    final double delta;
    final double gamma;
    final double vega;
    final double theta;
    final String label;

    CompoundOptionData(Option.Type tm, Option.Type td, double sm, double sd,
                       double s, double q, double r, double tM, double tD, double v,
                       double npv, double tol, double delta, double gamma, double vega, double theta,
                       String label) {
        this.typeMother = tm; this.typeDaughter = td;
        this.strikeMother = sm; this.strikeDaughter = sd;
        this.s = s; this.q = q; this.r = r;
        this.tMother = tM; this.tDaughter = tD; this.v = v;
        this.npv = npv; this.tol = tol;
        this.delta = delta; this.gamma = gamma; this.vega = vega; this.theta = theta;
        this.label = label;
    }
}

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

    /**
     * Full 21-row Haug 2007 + sitmo + mathfinance VBA test matrix.
     * Mirrors the bulk of C++ test-suite/compoundoption.cpp::testValues.
     * <p>
     * Per the C++ comment, tolerance is 1e-3 since the price/theta is very
     * sensitive to the bivariate-normal implementation; Java uses
     * {@code BivariateNormalDistribution} (West 2004 DP) which differs slightly
     * from the C++ implementations referenced for the Haug/sitmo/VBA values.
     */
    @Test
    public void testValuesAllRows() {
        QL.info("Testing compound-option NPV + Greeks (full 21-row matrix)...");

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote vol = new SimpleQuote(0.0);

        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final PricingEngine engine = new AnalyticCompoundOptionEngine(stochProcess);

        final CompoundOptionData[] values = {
            // Haug 2007 + sitmo (rows 1-4)
            new CompoundOptionData(Option.Type.Put,  Option.Type.Call, 50.0, 520.0, 500.0, 0.03, 0.08, 0.25, 0.5, 0.35, 21.1965, 1.0e-3, -0.1966, 0.0007,  -32.1241,  -3.3837, "Haug2007 PoC"),
            new CompoundOptionData(Option.Type.Call, Option.Type.Call, 50.0, 520.0, 500.0, 0.03, 0.08, 0.25, 0.5, 0.35, 17.5945, 1.0e-3,  0.3219, 0.0038,  106.5185, -65.1614, "sitmo CoC"),
            new CompoundOptionData(Option.Type.Call, Option.Type.Put,  50.0, 520.0, 500.0, 0.03, 0.08, 0.25, 0.5, 0.35, 18.7128, 1.0e-3, -0.2906, 0.0036,  103.3856, -46.6982, "sitmo CoP"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Put,  50.0, 520.0, 500.0, 0.03, 0.08, 0.25, 0.5, 0.35, 15.2601, 1.0e-3,  0.1760, 0.0005,  -35.2570, -10.1126, "sitmo PoP"),
            // sitmo (rows 5-8)
            new CompoundOptionData(Option.Type.Call, Option.Type.Call, 0.05, 1.14, 1.20, 0.0,  0.01, 0.5, 2.0, 0.11, 0.0729, 1.0e-3,  0.6614, 2.5762,  0.5812, -0.0297, "sitmo CoC FX"),
            new CompoundOptionData(Option.Type.Call, Option.Type.Put,  0.05, 1.14, 1.20, 0.0,  0.01, 0.5, 2.0, 0.11, 0.0074, 1.0e-3, -0.1334, 1.9681,  0.2933, -0.0155, "sitmo CoP FX"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Call, 0.05, 1.14, 1.20, 0.0,  0.01, 0.5, 2.0, 0.11, 0.0021, 1.0e-3, -0.0426, 0.7252, -0.0052, -0.0058, "sitmo PoC FX"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Put,  0.05, 1.14, 1.20, 0.0,  0.01, 0.5, 2.0, 0.11, 0.0192, 1.0e-3,  0.1626, 0.1171, -0.2931, -0.0028, "sitmo PoP FX"),
            // sitmo (rows 9-12)
            new CompoundOptionData(Option.Type.Call, Option.Type.Call, 10.0, 122.0, 120.0, 0.06, 0.02, 0.1, 0.7, 0.22,  0.4419, 1.0e-3,  0.1049, 0.0195,  11.3368, -6.2871, "sitmo CoC eq"),
            new CompoundOptionData(Option.Type.Call, Option.Type.Put,  10.0, 122.0, 120.0, 0.06, 0.02, 0.1, 0.7, 0.22,  2.6112, 1.0e-3, -0.3618, 0.0337,  28.4843, -13.4124, "sitmo CoP eq"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Call, 10.0, 122.0, 120.0, 0.06, 0.02, 0.1, 0.7, 0.22,  4.1616, 1.0e-3, -0.3174, 0.0024, -26.6403,  -2.2720, "sitmo PoC eq"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Put,  10.0, 122.0, 120.0, 0.06, 0.02, 0.1, 0.7, 0.22,  1.0914, 1.0e-3,  0.1748, 0.0165,  -9.4928,  -4.8995, "sitmo PoP eq"),
            // mathfinance VBA (rows 13-20)
            new CompoundOptionData(Option.Type.Call, Option.Type.Call, 0.4, 8.2, 8.0, 0.05, 0.0, 2.0, 3.0, 0.08,  0.0099, 1.0e-3,  0.0285,  0.0688,  0.7764, -0.0027, "VBA CoC"),
            new CompoundOptionData(Option.Type.Call, Option.Type.Put,  0.4, 8.2, 8.0, 0.05, 0.0, 2.0, 3.0, 0.08,  0.9826, 1.0e-3, -0.7224,  0.2158,  2.7279, -0.3332, "VBA CoP"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Call, 0.4, 8.2, 8.0, 0.05, 0.0, 2.0, 3.0, 0.08,  0.3585, 1.0e-3, -0.0720, -0.0835, -1.5633, -0.0117, "VBA PoC"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Put,  0.4, 8.2, 8.0, 0.05, 0.0, 2.0, 3.0, 0.08,  0.0168, 1.0e-3,  0.0378,  0.0635,  0.3882,  0.0021, "VBA PoP"),
            new CompoundOptionData(Option.Type.Call, Option.Type.Call, 0.02, 1.6, 1.6, 0.013, 0.022, 0.45, 0.5, 0.17, 0.0680, 1.0e-3,  0.4937, 2.1271,  0.4418, -0.0843, "VBA2 CoC"),
            new CompoundOptionData(Option.Type.Call, Option.Type.Put,  0.02, 1.6, 1.6, 0.013, 0.022, 0.45, 0.5, 0.17, 0.0605, 1.0e-3, -0.4169, 2.0836,  0.4330, -0.0697, "VBA2 CoP"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Call, 0.02, 1.6, 1.6, 0.013, 0.022, 0.45, 0.5, 0.17, 0.0081, 1.0e-3, -0.0417, 0.0761, -0.0045, -0.0020, "VBA2 PoC"),
            new CompoundOptionData(Option.Type.Put,  Option.Type.Put,  0.02, 1.6, 1.6, 0.013, 0.022, 0.45, 0.5, 0.17, 0.0078, 1.0e-3,  0.0413, 0.0326, -0.0133, -0.0016, "VBA2 PoP"),
        };

        for (final CompoundOptionData v : values) {
            final StrikedTypePayoff payoffMother = new PlainVanillaPayoff(v.typeMother, v.strikeMother);
            final StrikedTypePayoff payoffDaughter = new PlainVanillaPayoff(v.typeDaughter, v.strikeDaughter);

            final Date matDateMom = today.add(timeToDays(v.tMother));
            final Date matDateDaughter = today.add(timeToDays(v.tDaughter));

            final Exercise exerciseMother = new EuropeanExercise(matDateMom);
            final Exercise exerciseDaughter = new EuropeanExercise(matDateDaughter);

            spot.setValue(v.s);
            qRate.setValue(v.q);
            rRate.setValue(v.r);
            vol.setValue(v.v);

            final CompoundOption option = new CompoundOption(payoffMother, exerciseMother,
                                                             payoffDaughter, exerciseDaughter);
            option.setPricingEngine(engine);

            assertEquals("[" + v.label + "] NPV", v.npv,   option.NPV(),   v.tol);
            assertEquals("[" + v.label + "] delta", v.delta, option.delta(), v.tol);
            assertEquals("[" + v.label + "] gamma", v.gamma, option.gamma(), v.tol);
            assertEquals("[" + v.label + "] vega",  v.vega,  option.vega(),  v.tol);
            assertEquals("[" + v.label + "] theta", v.theta, option.theta(), v.tol);
        }
    }

    /**
     * Mirrors C++ test-suite/compoundoption.cpp::testPutCallParity.
     * For each of the 11 base cases, exercises Wystup 2002 put-call parity:
     *   Cmom_call(K) + K*P(0,T) - Cmom_put(K) = D
     * where Cmom_call/put are compound options on the same daughter, and
     * D is the daughter European value. Tolerance 1e-8.
     */
    @Test
    public void testPutCallParity() {
        QL.info("Testing compound-option put-call parity...");

        final DayCounter dc = new Actual360();
        final Date today = new Settings().evaluationDate();

        final SimpleQuote spot = new SimpleQuote(0.0);
        final SimpleQuote rRate = new SimpleQuote(0.0);
        final SimpleQuote qRate = new SimpleQuote(0.0);
        final SimpleQuote vol = new SimpleQuote(0.0);

        final YieldTermStructure rTS = Utilities.flatRate(today, rRate, dc);
        final YieldTermStructure qTS = Utilities.flatRate(today, qRate, dc);
        final BlackVolTermStructure volTS = Utilities.flatVol(today, vol, dc);

        final BlackScholesMertonProcess stochProcess = new BlackScholesMertonProcess(
                new Handle<Quote>(spot),
                new Handle<YieldTermStructure>(qTS),
                new Handle<YieldTermStructure>(rTS),
                new Handle<BlackVolTermStructure>(volTS));

        final PricingEngine engineCompound = new AnalyticCompoundOptionEngine(stochProcess);
        final PricingEngine engineEuropean = new org.jquantlib.pricingengines
                .AnalyticEuropeanEngine(stochProcess);

        // 11 base cases (just inputs — parity test ignores typeMother)
        final double[][] inputs = {
            // strikeMother, strikeDaughter, spot, q, r, tMother, tDaughter, vol, daughterIsCall(0/1)
            {50.0, 520.0, 500.0, 0.03, 0.08, 0.25, 0.5,  0.35, 1},
            {50.0, 520.0, 500.0, 0.03, 0.08, 0.25, 0.5,  0.35, 1},
            {50.0, 520.0, 500.0, 0.03, 0.08, 0.25, 0.5,  0.35, 0},
            {0.05, 1.14, 1.20, 0.0,   0.01, 0.5,  2.0,  0.11, 1},
            {0.05, 1.14, 1.20, 0.0,   0.01, 0.5,  2.0,  0.11, 0},
            {10.0, 122.0, 120.0, 0.06, 0.02, 0.1, 0.7,  0.22, 1},
            {10.0, 122.0, 120.0, 0.06, 0.02, 0.1, 0.7,  0.22, 0},
            {0.4,  8.2,  8.0,    0.05, 0.0,  2.0, 3.0,  0.08, 1},
            {0.4,  8.2,  8.0,    0.05, 0.0,  2.0, 3.0,  0.08, 0},
            {0.02, 1.6,  1.6,    0.013, 0.022, 0.45, 0.5, 0.17, 1},
            {0.02, 1.6,  1.6,    0.013, 0.022, 0.45, 0.5, 0.17, 0},
        };

        for (int i = 0; i < inputs.length; i++) {
            final double[] in = inputs[i];
            final double strikeMother = in[0];
            final double strikeDaughter = in[1];
            final Option.Type typeDaughter = in[8] == 1.0 ? Option.Type.Call : Option.Type.Put;

            final StrikedTypePayoff pmCall = new PlainVanillaPayoff(Option.Type.Call, strikeMother);
            final StrikedTypePayoff pmPut  = new PlainVanillaPayoff(Option.Type.Put,  strikeMother);
            final StrikedTypePayoff pd     = new PlainVanillaPayoff(typeDaughter, strikeDaughter);

            final Date matMom = today.add(timeToDays(in[5]));
            final Date matD   = today.add(timeToDays(in[6]));

            final Exercise exMom = new EuropeanExercise(matMom);
            final Exercise exD   = new EuropeanExercise(matD);

            spot.setValue(in[2]);
            qRate.setValue(in[3]);
            rRate.setValue(in[4]);
            vol.setValue(in[7]);

            final CompoundOption coCall = new CompoundOption(pmCall, exMom, pd, exD);
            final CompoundOption coPut  = new CompoundOption(pmPut,  exMom, pd, exD);
            final org.jquantlib.instruments.VanillaOption vanilla =
                    new org.jquantlib.instruments.VanillaOption(pd, exD);

            coCall.setPricingEngine(engineCompound);
            coPut.setPricingEngine(engineCompound);
            vanilla.setPricingEngine(engineEuropean);

            final double discFact = rTS.discount(matMom);
            final double discStrike = strikeMother * discFact;

            // Wystup 2002 eqn 9.5: Cmom_call + K*P(0,T) - Cmom_put = D
            final double parity = coCall.NPV() + discStrike - coPut.NPV() - vanilla.NPV();
            assertEquals("Put-call parity violation at row " + i, 0.0, parity, 1.0e-8);
        }
    }

    private static int timeToDays(final double t) {
        // Match C++ utility used in QuantLib test-suite (rounds up):
        // Date matDateMom = today + timeToDays(value.tMother);
        return (int) (t * 360 + 0.5);
    }
}
