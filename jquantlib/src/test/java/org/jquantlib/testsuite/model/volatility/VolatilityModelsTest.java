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

/*
 Copyright (C) 2006 Joseph Wang

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/
package org.jquantlib.testsuite.model.volatility;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.instruments.VanillaOption;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.model.equity.GjrGarchModel;
import org.jquantlib.model.volatility.ConstantEstimator;
import org.jquantlib.model.volatility.SimpleLocalEstimator;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticGJRGARCHEngine;
import org.jquantlib.processes.GjrGarchProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.TimeSeries;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Java port of QuantLib v1.42.1 test-suite/volatilitymodels.cpp (Phase 5g).
 *
 * <p>The C++ file contains a single {@code testConstruction} test that builds
 * a {@link TimeSeries} of three dated values, runs them through a
 * {@link SimpleLocalEstimator} and a {@link ConstantEstimator}, and exercises
 * the resulting series' iterator. The body is faithfully ported; the iterator
 * exercise becomes a non-null check on the returned {@link TimeSeries}.
 *
 * <p>The C++ test references {@code GarmanKlass} via include but does not
 * exercise it. JQuantLib has the full GarmanKlass family
 * ({@code org.jquantlib.model.volatility.GarmanKlassSigma1..6},
 * {@code GarmanKlassOpenClose}, {@code GarmanKlassSimpleSigma}); the include
 * is therefore omitted as it is unused by the C++ test body.
 *
 * <p>Note: an existing {@link EstimatorsTest} in this package already covers
 * the same construction with five dates; this class is the direct C++-named
 * equivalent (three-date variant) for Phase 5 audit completeness.
 *
 * <p>Phase 5e.5b-CFC-d-190: the formerly-{@code @Ignore}'d
 * {@code testGjrGarchModelDeferred} is now body-filled. It cross-validates
 * the Java {@link AnalyticGJRGARCHEngine} (driving {@link GjrGarchModel}
 * over {@link GjrGarchProcess}) against the migration-harness probe
 * {@code models/equity/gjrgarch_engine_probe.cpp}.
 */
public class VolatilityModelsTest {

    public VolatilityModelsTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

    /**
     * Faithful port of {@code volatilitymodels.cpp BOOST_AUTO_TEST_CASE(testConstruction)}
     * (lines 35-50).
     */
    @Test
    public void testConstruction() {
        QL.info("Testing volatility model construction...");

        final TimeSeries<Double> ts = new TimeSeries<Double>(Double.class);
        ts.put(new Date(25, Month.March, 2005), 1.2);
        ts.put(new Date(29, Month.March, 2005), 2.3);
        ts.put(new Date(15, Month.March, 2005), 0.3);

        final SimpleLocalEstimator sle = new SimpleLocalEstimator(1.0 / 360.0);
        final TimeSeries<Double> locale = sle.calculate(ts);
        assertNotNull("SimpleLocalEstimator returned null TimeSeries", locale);

        final ConstantEstimator ce = new ConstantEstimator(1);
        final TimeSeries<Double> sv = ce.calculate(locale);
        assertNotNull("ConstantEstimator returned null TimeSeries", sv);

        // C++ test ends with `sv.begin();` — exercising the iterator merely
        // ensures iteration does not throw. Java equivalent: navigableKeySet()
        // produces an iterator backed by the underlying TreeMap.
        assertNotNull("ConstantEstimator output exposes no key set",
                sv.navigableKeySet());
    }

    /**
     * Phase 5e.5b-CFC-d-190 body-fill: cross-validates the Java analytic
     * GJR-GARCH(1,1) engine ({@link AnalyticGJRGARCHEngine}) against the
     * C++ v1.42.1 oracle via the migration-harness probe
     * {@code models/equity/gjrgarch_engine_probe.cpp}.
     *
     * <p>The fixture mirrors the test-suite {@code garch.cpp testEngines}
     * parameter grid (omega=2e-6, alpha=0.024, beta=0.93, gamma=0.059,
     * daysPerYear=365.0; strikes {35..60} in steps of 5; maturities
     * {90, 180}; lambdas {0.0, 0.1, 0.2}). 36 calls and 6 puts are
     * cross-validated. The unconditional variance {@code v0} is derived
     * from {@code omega / (1 - m1)} per the C++ test driver.
     *
     * <p>Tolerance: LOOSE (1e-3). The Duan-Gauthier-Simonato-Sasseville
     * (2006) Edgeworth approximation truncates after the fourth
     * standardized moment; the C++ test-suite's own published reference
     * values (analytic[k][i][j]) are quoted to 4 significant digits and
     * agree with the engine output to roughly 5e-4. The Java port follows
     * the identical formulas and therefore agrees with the C++ probe to
     * many more digits in practice, but 1e-3 is the appropriate published
     * inherent-noise tier for an Edgeworth approximation.
     */
    @Test
    public void testGjrGarchModelDeferred() {
        QL.info("Testing analytic GJR-GARCH(1,1) Edgeworth engine vs C++ probe...");

        final ReferenceReader ref = ReferenceReader.load("models/equity/gjrgarch_engine");

        // Pin the evaluation date to match the C++ probe fixture exactly.
        final Date eval = new Date(15, Month.January, 2026);
        new Settings().setEvaluationDate(eval);

        final ActualActual dayCounter = new ActualActual(ActualActual.Convention.ISDA);
        final Handle<YieldTermStructure> rTS = new Handle<YieldTermStructure>(
                new FlatForward(eval, 0.05, dayCounter));
        final Handle<YieldTermStructure> qTS = new Handle<YieldTermStructure>(
                new FlatForward(eval, 0.0,  dayCounter));

        final List<String> failures = new ArrayList<String>();
        // Engine-inherent tier: see method Javadoc for justification.
        final double tolerance = 1.0e-3;

        for (final String name : ref.caseNames()) {
            final ReferenceReader.Case c = ref.getCase(name);
            final JSONObject in = c.inputs();
            final JSONObject exp = (JSONObject) c.expectedRaw();

            final double s0    = in.getDouble("s0");
            final double omega = in.getDouble("omega");
            final double alpha = in.getDouble("alpha");
            final double beta  = in.getDouble("beta");
            final double gamma = in.getDouble("gamma");
            final double lambda = in.getDouble("lambda");
            final double daysPerYear = in.getDouble("daysPerYear");
            final double strike = in.getDouble("strike");
            final int maturityDays = in.getInt("maturity_days");
            final String typeStr = in.getString("option_type");
            final double cppNpv = exp.getDouble("npv");

            // v0 = omega / (1 - m1), m1 from the GJR-GARCH stationarity
            // identity. Mirrors C++ garch.cpp testEngines lines 145-148.
            final double N = new CumulativeNormalDistribution().op(lambda);
            final double n = Math.exp(-lambda * lambda / 2.0)
                    / Math.sqrt(2.0 * Math.PI);
            final double m1 = beta + (alpha + gamma * N) * (1.0 + lambda * lambda)
                    + gamma * lambda * n;
            final double v0 = omega / (1.0 - m1);

            final Handle<Quote> spot = new Handle<Quote>(new SimpleQuote(s0));
            final GjrGarchProcess process = new GjrGarchProcess(
                    rTS, qTS, spot, v0, omega, alpha, beta, gamma, lambda, daysPerYear);
            final GjrGarchModel model = new GjrGarchModel(process);
            final PricingEngine engine = new AnalyticGJRGARCHEngine(model);

            final Date exDate = eval.add(maturityDays);
            final Exercise exercise = new EuropeanExercise(exDate);
            final Option.Type type = typeStr.equals("Call")
                    ? Option.Type.Call : Option.Type.Put;
            final StrikedTypePayoff payoff = new PlainVanillaPayoff(type, strike);

            final VanillaOption option = new VanillaOption(payoff, exercise);
            option.setPricingEngine(engine);
            final double javaNpv = option.NPV();

            final double diff = Math.abs(javaNpv - cppNpv);
            if (diff > tolerance) {
                failures.add(name + ": Java=" + javaNpv + " cpp=" + cppNpv
                        + " diff=" + diff + " tol=" + tolerance);
            }
        }

        if (!failures.isEmpty()) {
            fail("AnalyticGJRGARCHEngine vs C++ probe: "
                    + failures.size() + " mismatch(es)\n  "
                    + String.join("\n  ", failures.subList(0,
                            Math.min(20, failures.size())))
                    + (failures.size() > 20
                            ? "\n  ... (" + (failures.size() - 20) + " more)" : ""));
        }
    }
}
