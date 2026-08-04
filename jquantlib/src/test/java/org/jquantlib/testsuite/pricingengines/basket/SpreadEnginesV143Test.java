/*
 Copyright (C) 2026 Jose Moya

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

package org.jquantlib.testsuite.pricingengines.basket;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.experimental.volatility.SviSmileSection;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SpreadBasketPayoff;
import org.jquantlib.pricingengines.basket.GaussianCopulaSpreadEngine;
import org.jquantlib.pricingengines.basket.PearsonSpreadEngine;
import org.jquantlib.processes.BlackProcess;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.volatilities.equityfx.PiecewiseBlackVarianceSurface;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.NullCalendar;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Cross-validates {@link PearsonSpreadEngine} and {@link GaussianCopulaSpreadEngine} — both new in C++ QuantLib v1.43
 * — against the {@code pricingengines/v143_spread_engines} probe.
 * <p>
 * The probe's cases carry the whole market description (setup, correlation, option type, strike, quadrature order), so
 * the sweep below reconstructs each one rather than restating it. Five markets are covered: the two upstream flat-vol
 * ones, a Merton pair with <em>different</em> dividend yields — which is the only way a wrong
 * {@code fwd = spot * qDF / rDF} shows up, since a {@code BlackProcess} has q == r and hides it — and two SVI markets
 * that differ only in whether {@code AtmSmileSection}'s ATM override is a no-op.
 *
 * @author Jose Moya
 */
public class SpreadEnginesV143Test {

    /**
     * LOOSE tier. Both engines are quadrature-based — adaptive Gauss-Lobatto for Pearson, a 64-point tensor
     * Gauss-Hermite rule over spline-inverted marginals for the copula — so agreement is governed by the quadrature,
     * not by the last bits of the arithmetic.
     */
    private static final double REL_TOL = 1.0e-8;
    private static final double ABS_TOL = 1.0e-9;

    /**
     * The copula engine needs a larger <em>absolute</em> floor than Pearson, and for a specific reason rather than as
     * a concession.
     * <p>
     * Its marginals come from {@link org.jquantlib.methods.finitedifferences.utilities.SmileSectionRNDCalculator},
     * whose CDF grid is built from finite differences of option prices. Those differences are cancellation-limited at
     * roughly {@code 4·C·eps/gap} — about 1e-9 absolute for the option magnitudes here. That error enters the grid,
     * propagates through the monotone spline into each of the 4096 sampled strikes, and lands in the sum as an
     * absolute perturbation of the same order. It does not shrink for a deep-OTM spread whose NPV is small, so a
     * purely relative bound tightens exactly where the arithmetic is least able to deliver.
     * <p>
     * Measured across the reference, the disagreement is ~3e-9 absolute regardless of NPV size. A genuine porting
     * error — wrong quadrature weights, wrong copula coupling, wrong forward — moves the NPV by percent, not by
     * nanounits, so this floor costs nothing in discriminating power.
     */
    private static final double COPULA_ABS_TOL = 1.0e-8;

    private static final Date TODAY = new Date(1, Month.March, 2025);
    private static final Date MATURITY = new Date(1, Month.March, 2026);
    private static final double RISK_FREE = 0.05;

    /** SVI parameters {a, b, sigma, rho, m}, from the v1.43 test-suite case testGaussianCopulaSpreadEngineSVI. */
    private static final double[] SVI_1 = { 0.04, 0.10, 0.30, -0.40, 0.0 };
    private static final double[] SVI_2 = { 0.02, 0.08, 0.25, -0.30, 0.0 };

    private Date savedEvaluationDate;

    /**
     * The single risk-free curve handle. {@link GaussianCopulaSpreadEngine} compares the two processes'
     * {@code riskFreeRate().currentLink()} by identity, so both legs must share this exact object.
     */
    private Handle< YieldTermStructure > riskFree;

    @Before
    public void setUp() {
        savedEvaluationDate = new Settings().evaluationDate();
        new Settings().setEvaluationDate(TODAY);
        riskFree = new Handle< YieldTermStructure >(new FlatForward(TODAY, RISK_FREE, dayCounter()));
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvaluationDate);
    }

    private static DayCounter dayCounter() {
        return new Actual365Fixed();
    }

    private static ReferenceReader ref() {
        return ReferenceReader.load("pricingengines/v143_spread_engines");
    }

    private static void assertClose(final String what, final double expected, final double actual) {
        assertEquals(what, expected, actual, Math.max(ABS_TOL, REL_TOL * Math.abs(expected)));
    }

    //
    // market construction — mirrors the probe exactly
    //

    private static Handle< Quote > quote(final double v) {
        return new Handle< Quote >(new SimpleQuote(v));
    }

    private Handle< YieldTermStructure > flatCurve(final double r) {
        return new Handle< YieldTermStructure >(new FlatForward(TODAY, r, dayCounter()));
    }

    private Handle< BlackVolTermStructure > flatVol(final double v) {
        return new Handle< BlackVolTermStructure >(
                new BlackConstantVol(TODAY, new NullCalendar(), v, dayCounter()));
    }

    private Handle< BlackVolTermStructure > sviVol(final double forward, final double[] params) {
        final double t = dayCounter().yearFraction(TODAY, MATURITY);
        final SviSmileSection smile = new SviSmileSection(t, forward, params);
        return new Handle< BlackVolTermStructure >(
                new PiecewiseBlackVarianceSurface(TODAY, MATURITY, smile, dayCounter()));
    }

    /** The two legs of one market. */
    private static final class Legs {
        final GeneralizedBlackScholesProcess p1;
        final GeneralizedBlackScholesProcess p2;

        Legs(final GeneralizedBlackScholesProcess p1, final GeneralizedBlackScholesProcess p2) {
            this.p1 = p1;
            this.p2 = p2;
        }
    }

    private Legs legsFor(final String setup) {
        switch ( setup ) {
        case "a":
            // upstream testPearsonSpreadEngine market; f1 - f2 = -10
            return new Legs(new BlackProcess(quote(100.0), riskFree, flatVol(0.25)),
                    new BlackProcess(quote(110.0), riskFree, flatVol(0.35)));
        case "b":
            // upstream testGaussianCopulaSpreadEngineFlatVol market; f1 - f2 = 4
            return new Legs(new BlackProcess(quote(100.0), riskFree, flatVol(0.20)),
                    new BlackProcess(quote(96.0), riskFree, flatVol(0.25)));
        case "c":
            // different dividend yields per leg, so fwd = spot * qDF / rDF is genuinely exercised
            return new Legs(new BlackScholesMertonProcess(quote(100.0), flatCurve(0.02), riskFree, flatVol(0.30)),
                    new BlackScholesMertonProcess(quote(95.0), flatCurve(0.06), riskFree, flatVol(0.15)));
        case "d": {
            // upstream SVI market verbatim: sections built at spot/df, but the legs are BlackProcess, so the engine
            // forwards are the spots and AtmSmileSection overrides the SVI's own ATM level.
            final double df = riskFree.currentLink().discount(MATURITY);
            return new Legs(new BlackProcess(quote(100.0), riskFree, sviVol(100.0 / df, SVI_1)),
                    new BlackProcess(quote(96.0), riskFree, sviVol(96.0 / df, SVI_2)));
        }
        case "e":
            // the same SVI shapes anchored at the engines' own forwards, so the override is a no-op
            return new Legs(new BlackProcess(quote(100.0), riskFree, sviVol(100.0, SVI_1)),
                    new BlackProcess(quote(96.0), riskFree, sviVol(96.0, SVI_2)));
        default:
            throw new IllegalArgumentException("unknown setup: " + setup);
        }
    }

    private static Exercise exercise() {
        return new EuropeanExercise(MATURITY);
    }

    private static BasketOption spreadOption(final Option.Type type, final double strike) {
        return new BasketOption(new SpreadBasketPayoff(new PlainVanillaPayoff(type, strike)), exercise());
    }

    private static Option.Type typeOf(final String s) {
        return "Call".equals(s) ? Option.Type.Call : Option.Type.Put;
    }

    //
    // tests
    //

    /**
     * Every Pearson case in the probe.
     */
    @Test
    public void testPearsonSpreadEngine() {
        QL.info("Testing PearsonSpreadEngine against C++ v1.43...");
        int checked = 0;
        for ( final String name : ref().caseNames() ) {
            if ( !name.startsWith("pearson_") || isGuardCase(name) ) {
                continue;
            }
            final JSONObject in = ref().getCase(name).inputs();
            final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();

            final Legs legs = legsFor(in.getString("setup"));
            final BasketOption option = spreadOption(typeOf(in.getString("option_type")), in.getDouble("strike"));
            option.setPricingEngine(new PearsonSpreadEngine(legs.p1, legs.p2, in.getDouble("correlation"),
                    in.optDouble("integration_tolerance", 1.0e-10), in.optInt("max_integration_iterations", 10000),
                    in.optDouble("n_std", 8.0)));

            assertClose(name + ": NPV", out.getDouble("npv"), option.NPV());
            ++checked;
        }
        assertEquals("expected the probe to carry Pearson cases", true, checked > 20);
    }

    /**
     * Every Gaussian-copula case in the probe.
     */
    @Test
    public void testGaussianCopulaSpreadEngine() {
        QL.info("Testing GaussianCopulaSpreadEngine against C++ v1.43...");
        int checked = 0;
        for ( final String name : ref().caseNames() ) {
            if ( !name.startsWith("copula_") || isGuardCase(name) ) {
                continue;
            }
            final JSONObject in = ref().getCase(name).inputs();
            final JSONObject out = (JSONObject) ref().getCase(name).expectedRaw();

            final Legs legs = legsFor(in.getString("setup"));
            final BasketOption option = spreadOption(typeOf(in.getString("option_type")), in.getDouble("strike"));
            option.setPricingEngine(new GaussianCopulaSpreadEngine(legs.p1, legs.p2, in.getDouble("correlation"),
                    in.optInt("n_points", 64)));

            final double expected = out.getDouble("npv");
            assertEquals(name + ": NPV", expected, option.NPV(),
                    Math.max(COPULA_ABS_TOL, REL_TOL * Math.abs(expected)));
            ++checked;
        }
        assertEquals("expected the probe to carry copula cases", true, checked > 20);
    }

    private static boolean isGuardCase(final String name) {
        return name.contains("_ctor_") || name.contains("_rejects_") || name.endsWith("_no_extra_results")
                || name.endsWith("_derived_inputs");
    }

    /**
     * The derived market quantities each setup produces. These are what every priced case is built on, so pinning
     * them separately turns a whole column of NPV failures into a single, obvious "the forward is wrong".
     */
    @Test
    public void testDerivedMarketInputs() {
        QL.info("Testing the spread engines' derived market inputs against C++ v1.43...");
        for ( final String setup : new String[] { "a", "b", "c", "d", "e" } ) {
            final JSONObject e = (JSONObject) ref().getCase(setup + "_derived_inputs").expectedRaw();
            final Legs legs = legsFor(setup);

            final double df = riskFree.currentLink().discount(MATURITY);
            assertClose(setup + ": discount", e.getDouble("discount"), df);

            final double fwd1 = legs.p1.stateVariable().currentLink().value()
                    * legs.p1.dividendYield().currentLink().discount(MATURITY)
                    / legs.p1.riskFreeRate().currentLink().discount(MATURITY);
            final double fwd2 = legs.p2.stateVariable().currentLink().value()
                    * legs.p2.dividendYield().currentLink().discount(MATURITY)
                    / legs.p2.riskFreeRate().currentLink().discount(MATURITY);
            assertClose(setup + ": forward1", e.getDouble("forward1"), fwd1);
            assertClose(setup + ": forward2", e.getDouble("forward2"), fwd2);

            assertClose(setup + ": time1", e.getDouble("time1"),
                    legs.p1.blackVolatility().currentLink().timeFromReference(MATURITY));
            assertClose(setup + ": time2", e.getDouble("time2"),
                    legs.p2.blackVolatility().currentLink().timeFromReference(MATURITY));
            assertClose(setup + ": variance1", e.getDouble("variance1"),
                    legs.p1.blackVolatility().currentLink().blackVariance(MATURITY, fwd1));
            assertClose(setup + ": variance2", e.getDouble("variance2"),
                    legs.p2.blackVolatility().currentLink().blackVariance(MATURITY, fwd2));
        }
    }

    /**
     * Constructor and payoff guards. The copula engine validates correlation and insists the two legs share one
     * risk-free curve <em>object</em>; Pearson validates neither. Both reject a non-spread payoff.
     */
    @Test
    public void testGuards() {
        QL.info("Testing the spread engines' argument validation against C++ v1.43...");
        final Legs legs = legsFor("a");

        assertEquals("copula rejects rho > 1", expectedThrows("copula_ctor_rejects_rho_above_one"),
                throwsFor(() -> new GaussianCopulaSpreadEngine(legs.p1, legs.p2, 1.0001)));
        assertEquals("copula rejects rho < -1", expectedThrows("copula_ctor_rejects_rho_below_minus_one"),
                throwsFor(() -> new GaussianCopulaSpreadEngine(legs.p1, legs.p2, -1.0001)));
        assertEquals("copula at rho == 1", expectedThrows("copula_ctor_accepts_rho_exactly_one"),
                throwsFor(() -> new GaussianCopulaSpreadEngine(legs.p1, legs.p2, 1.0)));
        assertEquals("copula at rho == -1", expectedThrows("copula_ctor_accepts_rho_exactly_minus_one"),
                throwsFor(() -> new GaussianCopulaSpreadEngine(legs.p1, legs.p2, -1.0)));

        // Two curves holding the same numbers but different objects: rejected, because the comparison is by identity.
        final Handle< YieldTermStructure > otherCurve = new Handle< YieldTermStructure >(
                new FlatForward(TODAY, RISK_FREE, dayCounter()));
        final GeneralizedBlackScholesProcess otherLeg = new BlackProcess(quote(110.0), otherCurve, flatVol(0.35));
        assertEquals("copula rejects distinct risk-free curves",
                expectedThrows("copula_ctor_rejects_distinct_risk_free_curves"),
                throwsFor(() -> new GaussianCopulaSpreadEngine(legs.p1, otherLeg, 0.5)));

        // Pearson validates nothing: |rho| > 1 is accepted, and only sqrt(max(1-rho^2, 0)) clamps downstream.
        assertEquals("Pearson does not validate correlation",
                expectedThrows("pearson_ctor_does_not_validate_correlation"),
                throwsFor(() -> new PearsonSpreadEngine(legs.p1, legs.p2, 5.0)));
    }

    /**
     * Whether the probe recorded that C++ threw for this guard case. Tying the assertion to the reference rather than
     * to a hard-coded {@code true} means the test tracks C++ if upstream changes its mind about what is rejected.
     */
    private static boolean expectedThrows(final String caseName) {
        return ((JSONObject) ref().getCase(caseName).expectedRaw()).getBoolean("throws");
    }

    private static boolean throwsFor(final Runnable r) {
        try {
            r.run();
            return false;
        } catch ( final RuntimeException expected ) {
            return true;
        }
    }

    /**
     * Neither engine populates greeks or additional results — only the value. Asserting the emptiness keeps a future
     * "helpful" addition from silently diverging from C++.
     */
    @Test
    public void testNoAdditionalResults() {
        QL.info("Testing that the spread engines report no additional results, per C++ v1.43...");
        final Legs legs = legsFor("a");

        final JSONObject pearsonExpected = (JSONObject) ref().getCase("pearson_no_extra_results").expectedRaw();
        final BasketOption pearson = spreadOption(Option.Type.Call, 5.0);
        pearson.setPricingEngine(new PearsonSpreadEngine(legs.p1, legs.p2, 0.75));
        assertClose("pearson_no_extra_results: NPV", pearsonExpected.getDouble("npv"), pearson.NPV());
        assertEquals("Pearson must expose delta exactly as C++ does",
                pearsonExpected.getBoolean("delta_throws_not_provided"), throwsFor(pearson::delta));

        final JSONObject copulaExpected = (JSONObject) ref().getCase("copula_no_extra_results").expectedRaw();
        final Legs legsB = legsFor("b");
        final BasketOption copula = spreadOption(Option.Type.Call, 3.0);
        copula.setPricingEngine(new GaussianCopulaSpreadEngine(legsB.p1, legsB.p2, 0.5));
        assertClose("copula_no_extra_results: NPV", copulaExpected.getDouble("npv"), copula.NPV());
        assertEquals("the copula engine must expose delta exactly as C++ does",
                copulaExpected.getBoolean("delta_throws_not_provided"), throwsFor(copula::delta));
    }
}
