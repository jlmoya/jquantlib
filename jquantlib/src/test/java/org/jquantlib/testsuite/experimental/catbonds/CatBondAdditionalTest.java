/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.catbonds;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;

import org.jquantlib.Settings;
import org.jquantlib.cashflow.BlackIborCouponPricer;
import org.jquantlib.cashflow.IborCouponPricer;
import org.jquantlib.cashflow.PricerSetter;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.ActualActual;
import org.jquantlib.experimental.catbonds.BetaRisk;
import org.jquantlib.experimental.catbonds.CatRisk;
import org.jquantlib.experimental.catbonds.DateRealPair;
import org.jquantlib.experimental.catbonds.DigitalNotionalRisk;
import org.jquantlib.experimental.catbonds.EventPaymentOffset;
import org.jquantlib.experimental.catbonds.EventSet;
import org.jquantlib.experimental.catbonds.FloatingCatBond;
import org.jquantlib.experimental.catbonds.MonteCarloCatBondEngine;
import org.jquantlib.experimental.catbonds.NoOffset;
import org.jquantlib.experimental.catbonds.NotionalRisk;
import org.jquantlib.experimental.catbonds.ProportionalNotionalRisk;
import org.jquantlib.indexes.ibor.USDLibor;
import org.jquantlib.instruments.bonds.FloatingRateBond;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.pricingengines.bond.DiscountingBondEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.optionlet.OptionletVolatilityStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.DateGeneration;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.Schedule;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.UnitedStates;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Phase 5d additional skeleton port of {@code test-suite/catbonds.cpp}
 * v1.42.1 (665 LOC, 9 cases) — gap-fill for cases not in
 * {@link CatBondTest}.
 *
 * <p>{@link CatBondTest} (Phase 4 experimental port) already covers 6
 * of the 9 cases:
 * {@code testEventSetForWholeYears},
 * {@code testEventSetForIrregularPeriods},
 * {@code testEventSetForNoEvents},
 * {@code testCatBondInDoomScenario},
 * {@code testCatBondWithDoomOnceInTenYears},
 * {@code testCatBondWithProportionalNotional} (Java rename for one of
 * the proportional-notional C++ cases).
 *
 * <p>This companion file holds the 3 missing cases:
 * <ul>
 *   <li>{@code testBetaRisk} — beta-distributed catastrophe loss
 *       severity (still @Ignore'd, RNG mismatch — see REASON_BETA);
 *   <li>{@code testRiskFreeAgainstFloatingRateBond} — sanity check that
 *       a risk-free CAT bond converges to a {@link
 *       org.jquantlib.instruments.bonds.FloatingRateBond} when loss
 *       probability is zero (body-filled Phase 5e.5b-CFC-d-242);
 *   <li>{@code testCatBondWithGeneratedEventsProportional} — generated
 *       (vs historical) event set with proportional notional risk
 *       (body-filled Phase 5e.5b-CFC-d-242).
 * </ul>
 *
 * <p>Source: {@code test-suite/catbonds.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class CatBondAdditionalTest {

    private static final double FACE_AMOUNT = 1_000_000.0;

    private static final String REASON_BETA =
            "Phase 5e.5b-CFC-d-239 (Round-3): BetaRisk + BetaRiskSimulation are "
          + "now ported in experimental/catbonds.  However the C++ test relies "
          + "on std::mt19937 + std::exponential_distribution + std::gamma_distribution "
          + "for its compound-Poisson/Beta sample stream and asserts "
          + "QL_CHECK_CLOSE on the empirical mean/variance at 1-2% (libstdc++) "
          + "or 5-10% (libc++) percentage tolerances. Java's BetaRiskSimulation "
          + "uses java.util.Random + Marsaglia-Tsang gamma; the resulting "
          + "sample stream produces ~2-3%-relative drift on the Poisson mean "
          + "and ~10-20% drift on the compound variance at the C++ N=1e6, which "
          + "trips the C++ tolerances on roughly 60% of seed-untreated runs. "
          + "Un-ignore requires either (a) a deterministic-seed-capable "
          + "BetaRiskSimulation overload (currently the ctor takes no seed; "
          + "production-side change, out of test-only allowlist) or "
          + "(b) widening tolerances beyond the C++ libc++ tier (5%->10% "
          + "Poisson mean, 5%->20% compound variance), which violates the "
          + "CLAUDE.md \"never loosen tolerance\" rule.";

    @Ignore(REASON_BETA)
    @Test
    public void testBetaRisk() { fail("not implemented"); }

    // ------------------------------------------------------------------
    // Helper: flat yield term structure wrapped in a Handle
    // (mirrors CatBondTest.flatRate, locally inlined to keep this file
    // self-contained for the test-only allowlist).
    // ------------------------------------------------------------------
    private static Handle<YieldTermStructure> flatRate(
            final Date today, final double rate) {
        return new Handle<YieldTermStructure>(
                new FlatForward(today,
                                new Handle<Quote>(new SimpleQuote(rate)),
                                new Actual360()));
    }

    // ------------------------------------------------------------------
    // testRiskFreeAgainstFloatingRateBond
    //
    // Port of catbonds.cpp v1.42.1 lines 200-364.
    //
    // A floating-rate CAT bond with a *no-event* risk model and
    // DigitalNotionalRisk(threshold=100) should price identically to a
    // plain {@link FloatingRateBond} with the same schedule, since no
    // catastrophe paths ever cross the digital threshold and the
    // notional is never written down.
    //
    // The C++ test asserts both (a) FRN price matches a cached value
    // and (b) CatBond price matches the FRN price.  We drop (a) because
    // the Java FRN cached-price assertion already lives in
    // {@code BondTest.testCachedFloating}, and assert only (b) — the
    // *new* claim under test here is the CatBond/FRN equivalence on
    // the no-event path.
    //
    // C++ tolerance is 1e-6 absolute; we keep the same.  This is not
    // an MC tolerance — the no-event path is deterministic
    // (MonteCarloCatBondEngine computes a single riskFreeNPV before
    // the path loop and reuses it for every empty-event path).
    // ------------------------------------------------------------------
    @Test
    public void testRiskFreeAgainstFloatingRateBond() {
        final Date today = new Date(22, Month.November, 2004);
        new Settings().setEvaluationDate(today);

        final int settlementDays = 1;

        final Handle<YieldTermStructure> riskFreeRate  = flatRate(today, 0.025);
        final Handle<YieldTermStructure> discountCurve = flatRate(today, 0.030);

        final USDLibor index = new USDLibor(new Period(6, TimeUnit.Months), riskFreeRate);
        final int fixingDays = 1;

        final double tolerance = 1.0e-6;

        final IborCouponPricer pricer = new BlackIborCouponPricer(
                new Handle<OptionletVolatilityStructure>());

        final Calendar usGovt = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Schedule sch = new Schedule(
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2008),
                new Period(Frequency.Semiannual),
                usGovt,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Backward,
                false);

        // No-event CatRisk: empty event set spanning Jan-2000..Dec-2010
        // → every simulated path has zero events →
        // DigitalNotionalRisk never triggers → notional stays at par.
        final CatRisk noCatRisk = new EventSet(
                new ArrayList<DateRealPair>(),
                new Date(1, Month.January, 2000),
                new Date(31, Month.December, 2010));

        final EventPaymentOffset paymentOffset = new NoOffset();
        final NotionalRisk notionalRisk = new DigitalNotionalRisk(paymentOffset, 100.0);

        // ---------- plain: discount and forwarding curve both risk-free ----------

        final FloatingRateBond bond1 = new FloatingRateBond(
                settlementDays, FACE_AMOUNT, sch,
                index, new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(0), new Array(0),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final FloatingCatBond catBond1 = new FloatingCatBond(
                settlementDays, FACE_AMOUNT, sch,
                index, new ActualActual(ActualActual.Convention.ISMA),
                notionalRisk,
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(0), new Array(0),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final PricingEngine bondEngine = new DiscountingBondEngine(riskFreeRate);
        bond1.setPricingEngine(bondEngine);
        PricerSetter.setCouponPricer(bond1.cashflows(), pricer);

        final PricingEngine catBondEngine =
                new MonteCarloCatBondEngine(noCatRisk, riskFreeRate);
        catBond1.setPricingEngine(catBondEngine);
        PricerSetter.setCouponPricer(catBond1.cashflows(), pricer);

        final double price1    = bond1.cleanPrice();
        final double catPrice1 = catBond1.cleanPrice();
        assertEquals("plain: catBond clean price must match FRN clean price",
                price1, catPrice1, tolerance);

        // ---------- different risk-free and discount curve ----------

        final FloatingRateBond bond2 = new FloatingRateBond(
                settlementDays, FACE_AMOUNT, sch,
                index, new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(0), new Array(0),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final FloatingCatBond catBond2 = new FloatingCatBond(
                settlementDays, FACE_AMOUNT, sch,
                index, new ActualActual(ActualActual.Convention.ISMA),
                notionalRisk,
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(0), new Array(0),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final PricingEngine bondEngine2 = new DiscountingBondEngine(discountCurve);
        bond2.setPricingEngine(bondEngine2);
        PricerSetter.setCouponPricer(bond2.cashflows(), pricer);

        final PricingEngine catBondEngine2 =
                new MonteCarloCatBondEngine(noCatRisk, discountCurve);
        catBond2.setPricingEngine(catBondEngine2);
        PricerSetter.setCouponPricer(catBond2.cashflows(), pricer);

        final double price2    = bond2.cleanPrice();
        final double catPrice2 = catBond2.cleanPrice();
        assertEquals("different-curve: catBond clean price must match FRN clean price",
                price2, catPrice2, tolerance);

        // ---------- varying spread ----------

        final double[] spreads = new double[] { 0.001, 0.0012, 0.0014, 0.0016 };

        final FloatingRateBond bond3 = new FloatingRateBond(
                settlementDays, FACE_AMOUNT, sch,
                index, new ActualActual(ActualActual.Convention.ISMA),
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(0), new Array(spreads),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final FloatingCatBond catBond3 = new FloatingCatBond(
                settlementDays, FACE_AMOUNT, sch,
                index, new ActualActual(ActualActual.Convention.ISMA),
                notionalRisk,
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(0), new Array(spreads),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        bond3.setPricingEngine(bondEngine2);
        PricerSetter.setCouponPricer(bond3.cashflows(), pricer);

        catBond3.setPricingEngine(catBondEngine2);
        PricerSetter.setCouponPricer(catBond3.cashflows(), pricer);

        final double price3    = bond3.cleanPrice();
        final double catPrice3 = catBond3.cleanPrice();
        assertEquals("spread: catBond clean price must match FRN clean price",
                price3, catPrice3, tolerance);
    }

    // ------------------------------------------------------------------
    // testCatBondWithGeneratedEventsProportional
    //
    // Port of catbonds.cpp v1.42.1 lines 588-661.
    //
    // Uses {@link BetaRisk}(maxLoss=5000, years=50, mean=500, stdDev=500)
    // to *generate* synthetic catastrophe paths (vs the historical-event
    // EventSet used by sibling tests), combined with
    // ProportionalNotionalRisk(attachment=500, exhaustion=1500).
    //
    // The C++ test asserts only directional invariants:
    //   - 0 < lossProbability       < 1
    //   - 0 < exhaustionProbability < 1
    //   - expectedLoss > 0
    //   - riskFree run: lossProbability == 0, expectedLoss ~ 0
    //   - riskFreePrice > catPrice  (cat-risk premium > 0)
    //   - riskFreeYield < catYield  (cat-risk yield uplift > 0)
    //
    // Tolerances mirror C++ (1e-6) for the zero-loss assertions on the
    // no-event control path, which is deterministic.  The directional
    // checks (>0, <1, etc.) are tolerance-free.  No "MC tolerance"
    // applies because we never compare the MC-derived numbers against
    // a fixed expected value — only against each other / 0 / 1.
    //
    // The Java BetaRiskSimulation uses java.util.Random with the
    // default (time-based) seed, so the absolute MC values differ
    // run-to-run.  The directional invariants are robust under any
    // well-formed pseudo-random stream because they are structural
    // properties of the model parameters: with mean=500 < maxLoss=5000
    // and lambda=1/50/yr over a 4-year horizon, the loss probability
    // sits comfortably strictly between 0 and 1 in any reasonable
    // sample; with attachment=500 < mean=500 < exhaustion=1500 and
    // maxLoss=5000, both partial-loss and full-exhaustion events are
    // achievable, giving non-trivial probabilities for both.
    // ------------------------------------------------------------------
    @Test
    public void testCatBondWithGeneratedEventsProportional() {
        final Date today = new Date(22, Month.November, 2004);
        new Settings().setEvaluationDate(today);

        final int settlementDays = 1;

        final Handle<YieldTermStructure> riskFreeRate  = flatRate(today, 0.025);
        final Handle<YieldTermStructure> discountCurve = flatRate(today, 0.030);

        final USDLibor index = new USDLibor(new Period(6, TimeUnit.Months), riskFreeRate);
        final int fixingDays = 1;

        final double tolerance = 1.0e-6;

        final IborCouponPricer pricer = new BlackIborCouponPricer(
                new Handle<OptionletVolatilityStructure>());

        final Calendar usGovt = new UnitedStates(UnitedStates.Market.GOVERNMENTBOND);
        final Schedule sch = new Schedule(
                new Date(30, Month.November, 2004),
                new Date(30, Month.November, 2008),
                new Period(Frequency.Semiannual),
                usGovt,
                BusinessDayConvention.ModifiedFollowing,
                BusinessDayConvention.ModifiedFollowing,
                DateGeneration.Rule.Backward,
                false);

        // BetaRisk(maxLoss=5000, years=50, mean=500, stdDev=500): a
        // compound-Poisson process with Beta-distributed event severity.
        final CatRisk betaCatRisk = new BetaRisk(5000.0, 50.0, 500.0, 500.0);

        // Risk-free control: empty EventSet → no events on every path.
        final CatRisk noCatRisk = new EventSet(
                new ArrayList<DateRealPair>(),
                new Date(1, Month.January, 2000),
                new Date(31, Month.December, 2010));

        final EventPaymentOffset paymentOffset = new NoOffset();
        // ProportionalNotionalRisk: linear write-down between
        // attachment=500 and exhaustion=1500.
        final NotionalRisk notionalRisk = new ProportionalNotionalRisk(
                paymentOffset, 500.0, 1500.0);

        final FloatingCatBond catBond = new FloatingCatBond(
                settlementDays, FACE_AMOUNT, sch,
                index, new ActualActual(ActualActual.Convention.ISMA),
                notionalRisk,
                BusinessDayConvention.ModifiedFollowing, fixingDays,
                new Array(0), new Array(0),
                new Array(0), new Array(0),
                false, 100.0, new Date(30, Month.November, 2004));

        final PricingEngine catBondEngine =
                new MonteCarloCatBondEngine(betaCatRisk, discountCurve);
        catBond.setPricingEngine(catBondEngine);
        PricerSetter.setCouponPricer(catBond.cashflows(), pricer);

        final double catPrice = catBond.cleanPrice();
        final double catYield = catBond.yield(
                new ActualActual(ActualActual.Convention.ISMA),
                Compounding.Simple, Frequency.Annual);
        final double lossProbability       = catBond.lossProbability();
        final double exhaustionProbability = catBond.exhaustionProbability();
        final double expectedLoss          = catBond.expectedLoss();

        assertTrue("loss probability in (0,1): " + lossProbability,
                lossProbability > 0.0 && lossProbability < 1.0);
        assertTrue("exhaustion probability in (0,1): " + exhaustionProbability,
                exhaustionProbability > 0.0 && exhaustionProbability < 1.0);
        assertTrue("expected loss > 0: " + expectedLoss, expectedLoss > 0.0);

        // ---------- risk-free control ----------
        catBond.setPricingEngine(new MonteCarloCatBondEngine(noCatRisk, discountCurve));
        final double riskFreePrice = catBond.cleanPrice();
        final double riskFreeYield = catBond.yield(
                new ActualActual(ActualActual.Convention.ISMA),
                Compounding.Simple, Frequency.Annual);
        final double riskFreeLossProbability = catBond.lossProbability();
        final double riskFreeExpectedLoss    = catBond.expectedLoss();

        assertEquals("rf lossProbability == 0",
                0.0, riskFreeLossProbability, tolerance);
        assertTrue("rf expectedLoss near 0: " + riskFreeExpectedLoss,
                Math.abs(riskFreeExpectedLoss) < tolerance);

        assertTrue("riskFreePrice (" + riskFreePrice + ") > catPrice (" + catPrice + ")",
                riskFreePrice > catPrice);
        assertTrue("riskFreeYield (" + riskFreeYield + ") < catYield (" + catYield + ")",
                riskFreeYield < catYield);
    }
}
