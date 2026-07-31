/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.instruments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.Settings;
import org.jquantlib.currencies.America.USDCurrency;
import org.jquantlib.currencies.Asia.SGDCurrency;
import org.jquantlib.currencies.Currency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.FxForward;
import org.jquantlib.pricingengines.forward.DiscountingFxForwardEngine;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.RelinkableHandle;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.Target;
import org.junit.Test;

/**
 * Phase 5e.5b-CFC-d-69 port of {@code test-suite/fxforward.cpp} v1.42.1
 * (455 LOC, 13 cases).
 *
 * <p>Exercises {@link org.jquantlib.instruments.FxForward} and
 * {@link org.jquantlib.pricingengines.forward.DiscountingFxForwardEngine}:
 * construction (notional / contracted-rate variants), expiry handling,
 * fair-forward-rate solving, position direction (paySource/receiveSource),
 * discounting engine, IR curve and spot FX sensitivity, additional-results
 * map, and settlement-day handling (with and without an explicit calendar).
 *
 * <p>Tolerance: {@code 1.0e-4} relative for absolute closeness /
 * {@code 1.0e-10} for contracted-rate echo, mirroring the C++ test verbatim.
 *
 * <p>Source: {@code test-suite/fxforward.cpp} v1.42.1 @ {@code 099987f0ca}.
 */
public class FxForwardTest {

    // ── shared fixture (CommonVars in the C++ test) ─────────────────────

    /** Mirrors C++ {@code CommonVars} struct. */
    private static class CommonVars {
        final Date today;
        final Date maturityDate;
        final Currency usd = new USDCurrency();
        final Currency sgd = new SGDCurrency();
        final RelinkableHandle<YieldTermStructure> usdCurveHandle =
                new RelinkableHandle<YieldTermStructure>();
        final RelinkableHandle<YieldTermStructure> sgdCurveHandle =
                new RelinkableHandle<YieldTermStructure>();
        final RelinkableHandle<Quote> spotFxHandle =
                new RelinkableHandle<Quote>();
        final double tolerance = 1.0e-6;

        CommonVars() {
            today = new Date(15, Month.March, 2024);
            new Settings().setEvaluationDate(today);
            maturityDate = today.add(new Period(6, TimeUnit.Months));

            final DayCounter dc = new Actual365Fixed();
            // USD discount rate: 5 %
            usdCurveHandle.linkTo(flatRate(today, 0.05, dc));
            // SGD discount rate: 3.5 %
            sgdCurveHandle.linkTo(flatRate(today, 0.035, dc));

            // Spot FX: 1.35 SGD/USD (1 USD = 1.35 SGD)
            spotFxHandle.linkTo(new SimpleQuote(1.35));
        }
    }

    private static YieldTermStructure flatRate(final Date today,
                                               final double rate,
                                               final DayCounter dc) {
        return new FlatForward(today, new Handle<Quote>(new SimpleQuote(rate)), dc);
    }


    // ── 1. testFxForwardConstruction ────────────────────────────────────
    @Test
    public void testFxForwardConstruction() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        final FxForward fwd1 = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true); // pay USD, receive SGD

        assertEquals(usdNominal, fwd1.sourceNominal(), 0.0);
        assertEquals(sgdNominal, fwd1.targetNominal(), 0.0);
        assertTrue(fwd1.sourceCurrency().equals(vars.usd));
        assertTrue(fwd1.targetCurrency().equals(vars.sgd));
        assertEquals(vars.maturityDate, fwd1.maturityDate());
        assertTrue(fwd1.paySourceCurrency());
        assertEquals(false, fwd1.isExpired());
    }


    // ── 2. testFxForwardConstructionWithRate ────────────────────────────
    @Test
    public void testFxForwardConstructionWithRate() {
        final CommonVars vars = new CommonVars();
        final double nominal = 1_000_000.0;
        final double forwardRate = 1.36; // SGD/USD forward rate

        final FxForward fwd = new FxForward(
                nominal, vars.usd, vars.sgd, forwardRate,
                vars.maturityDate, true); // sell USD

        assertEquals(nominal, fwd.sourceNominal(), 0.0);
        assertEquals(nominal * forwardRate, fwd.targetNominal(),
                1.0e-4 * Math.abs(nominal * forwardRate));
        assertTrue(fwd.sourceCurrency().equals(vars.usd));
        assertTrue(fwd.targetCurrency().equals(vars.sgd));
    }


    // ── 3. testContractedForwardRate ────────────────────────────────────
    @Test
    public void testContractedForwardRate() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;
        final double expectedRate = sgdNominal / usdNominal; // 1.35

        final FxForward fwd1 = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true);

        assertEquals(expectedRate, fwd1.forwardRate(),
                1.0e-10 * Math.abs(expectedRate));

        final double inputRate = 1.36;
        final FxForward fwd2 = new FxForward(
                usdNominal, vars.usd, vars.sgd, inputRate,
                vars.maturityDate, true);

        assertEquals(inputRate, fwd2.forwardRate(),
                1.0e-10 * Math.abs(inputRate));

        // Verify contracted rate differs from fair-forward rate after pricing.
        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        fwd1.setPricingEngine(engine);

        final double fairRate = fwd1.fairForwardRate();
        final double contractedRate = fwd1.forwardRate();

        assertTrue("contracted rate should generally differ from fair rate",
                Math.abs(contractedRate - fairRate) > 1.0e-12);
    }


    // ── 4. testFxForwardExpiry ──────────────────────────────────────────
    @Test
    public void testFxForwardExpiry() {
        final CommonVars vars = new CommonVars();
        final Date pastDate = vars.today.sub(1);

        final FxForward expiredFwd = new FxForward(
                1_000_000.0, vars.usd, 1_350_000.0, vars.sgd, pastDate, true);

        assertEquals(true, expiredFwd.isExpired());
    }


    // ── 5. testDiscountingFxForwardEngine ───────────────────────────────
    @Test
    public void testDiscountingFxForwardEngine() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        final FxForward fwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true); // pay USD, receive SGD

        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        fwd.setPricingEngine(engine);

        final double npv = fwd.NPV();
        assertTrue("NPV must be finite", !Double.isNaN(npv) && !Double.isInfinite(npv));

        final double fairRate = fwd.fairForwardRate();
        assertTrue("fair forward rate must be positive", fairRate > 0.0);
    }


    // ── 6. testFairForwardRate ──────────────────────────────────────────
    @Test
    public void testFairForwardRate() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        final FxForward fwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true);

        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        fwd.setPricingEngine(engine);

        // Expected: F = S * (DF_source / DF_target), with DFs from settlement →
        // maturity. Here source = USD, target = SGD. C++ QuantLib v1.43
        // INVERTED this ratio in DiscountingFxForwardEngine::calculate
        // (v1.42.1 computed S * DF_target / DF_source).
        final Date settlementDate = fwd.settlementDate();
        final double spotFx = vars.spotFxHandle.currentLink().value();
        final double dfUsd =
                vars.usdCurveHandle.currentLink().discount(vars.maturityDate) /
                vars.usdCurveHandle.currentLink().discount(settlementDate);
        final double dfSgd =
                vars.sgdCurveHandle.currentLink().discount(vars.maturityDate) /
                vars.sgdCurveHandle.currentLink().discount(settlementDate);
        final double expectedFairRate = spotFx * dfUsd / dfSgd;

        final double calculatedFairRate = fwd.fairForwardRate();
        assertEquals(expectedFairRate, calculatedFairRate,
                1.0e-4 * Math.abs(expectedFairRate));
    }


    // ── 7. testAtTheMoney ───────────────────────────────────────────────
    @Test
    public void testAtTheMoney() {
        final CommonVars vars = new CommonVars();
        final double spotFx = vars.spotFxHandle.currentLink().value();
        final double usdNominal = 1_000_000.0;

        // Create a temporary forward to get the settlement date.
        final FxForward tempFwd = new FxForward(
                usdNominal, vars.usd, usdNominal, vars.sgd, vars.maturityDate, true);
        final Date settlementDate = tempFwd.settlementDate();

        final double dfUsd =
                vars.usdCurveHandle.currentLink().discount(vars.maturityDate) /
                vars.usdCurveHandle.currentLink().discount(settlementDate);
        final double dfSgd =
                vars.sgdCurveHandle.currentLink().discount(vars.maturityDate) /
                vars.sgdCurveHandle.currentLink().discount(settlementDate);

        // ATM: targetNominal = sourceNominal * dfUsd * spotFx / dfSgd
        final double sgdNominal = usdNominal * dfUsd * spotFx / dfSgd;

        final FxForward fwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true);
        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        fwd.setPricingEngine(engine);

        final double npv = fwd.NPV();
        assertEquals("ATM forward NPV should be ~0", 0.0, npv, 1.0e-4);
    }


    // ── 8. testPositionDirection ────────────────────────────────────────
    @Test
    public void testPositionDirection() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        // Long USD (pay SGD, receive USD) — paySourceCurrency = false
        final FxForward longUsd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, false);
        // Short USD (pay USD, receive SGD) — paySourceCurrency = true
        final FxForward shortUsd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true);

        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        longUsd.setPricingEngine(engine);
        shortUsd.setPricingEngine(engine);

        final double npvLong = longUsd.NPV();
        final double npvShort = shortUsd.NPV();

        assertEquals(npvLong, -npvShort, 1.0e-4 * Math.abs(npvShort));
    }


    // ── 9. testIRCurveSensitivity ───────────────────────────────────────
    @Test
    public void testIRCurveSensitivity() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        final FxForward fwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true);
        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        fwd.setPricingEngine(engine);

        final double npvBase = fwd.NPV();

        // Shift USD curve up by 10 bp.
        vars.usdCurveHandle.linkTo(flatRate(vars.today, 0.051, new Actual365Fixed()));
        final double npvUsdUp = fwd.NPV();

        // Reset USD and bump SGD up by 10 bp.
        vars.usdCurveHandle.linkTo(flatRate(vars.today, 0.05, new Actual365Fixed()));
        vars.sgdCurveHandle.linkTo(flatRate(vars.today, 0.036, new Actual365Fixed()));
        final double npvSgdUp = fwd.NPV();

        // Pay USD / receive SGD:
        //  - Higher USD rate → lower DF_USD → less negative USD-leg PV → NPV ↑
        //  - Higher SGD rate → lower DF_SGD → less positive SGD-leg PV → NPV ↓
        assertTrue("NPV must rise when USD rate rises", npvUsdUp > npvBase);
        assertTrue("NPV must fall when SGD rate rises", npvSgdUp < npvBase);
    }


    // ── 10. testSpotFxSensitivity ───────────────────────────────────────
    @Test
    public void testSpotFxSensitivity() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        final FxForward fwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true); // pay USD, receive SGD
        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        fwd.setPricingEngine(engine);

        final double npvBase = fwd.NPV();

        // Spot ↑ (USD strengthens / SGD weakens)
        vars.spotFxHandle.linkTo(new SimpleQuote(1.40));
        final double npvSpotUp = fwd.NPV();

        // Spot ↓ (USD weakens / SGD strengthens)
        vars.spotFxHandle.linkTo(new SimpleQuote(1.30));
        final double npvSpotDown = fwd.NPV();

        assertTrue("higher spot → NPV ↓ (SGD worth less in USD)", npvSpotUp < npvBase);
        assertTrue("lower spot → NPV ↑ (SGD worth more in USD)", npvSpotDown > npvBase);
    }


    // ── 11. testAdditionalResults ───────────────────────────────────────
    @Test
    public void testAdditionalResults() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        final FxForward fwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true);
        final DiscountingFxForwardEngine engine = new DiscountingFxForwardEngine(
                vars.usdCurveHandle, vars.sgdCurveHandle, vars.spotFxHandle);
        fwd.setPricingEngine(engine);

        fwd.NPV(); // trigger calculation

        final java.util.Map<String, Object> add = fwd.additionalResults();
        assertNotNull(add.get("spotFx"));
        assertNotNull(add.get("sourceCurrencyDiscountFactor"));
        assertNotNull(add.get("targetCurrencyDiscountFactor"));

        final double spotFx   = ((Double) add.get("spotFx")).doubleValue();
        final double dfSource = ((Double) add.get("sourceCurrencyDiscountFactor")).doubleValue();
        final double dfTarget = ((Double) add.get("targetCurrencyDiscountFactor")).doubleValue();

        assertEquals(1.35, spotFx, 1.0e-4 * 1.35);
        assertTrue("0 < dfSource < 1", dfSource > 0.0 && dfSource < 1.0);
        assertTrue("0 < dfTarget < 1", dfTarget > 0.0 && dfTarget < 1.0);
    }


    // ── 12. testSettlementDays ──────────────────────────────────────────
    @Test
    public void testSettlementDays() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        // Overnight (O/N): 0 days
        final FxForward overnightFwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true, 0);
        assertEquals(0, overnightFwd.settlementDays());
        assertEquals(vars.today, overnightFwd.settlementDate());

        // TomNext (T/N): 1 day
        final FxForward tomNextFwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true, 1);
        assertEquals(1, tomNextFwd.settlementDays());
        assertEquals(vars.today.add(1), tomNextFwd.settlementDate());

        // Spot (S/N): 2 days (default)
        final FxForward spotFwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true);
        assertEquals(2, spotFwd.settlementDays());
        assertEquals(vars.today.add(2), spotFwd.settlementDate());
    }


    // ── 13. testSettlementDaysWithCalendar ──────────────────────────────
    @Test
    public void testSettlementDaysWithCalendar() {
        final CommonVars vars = new CommonVars();
        final double usdNominal = 1_000_000.0;
        final double sgdNominal = 1_350_000.0;

        final Calendar cal = new Target();

        // 15-March-2024 is a Friday.
        final Date friday = new Date(15, Month.March, 2024);
        new Settings().setEvaluationDate(friday);

        // With 2 settlement days on Friday + TARGET, settlement should be Tuesday.
        final FxForward fwd = new FxForward(
                usdNominal, vars.usd, sgdNominal, vars.sgd,
                vars.maturityDate, true, 2, cal);

        final Date expectedSettlementDate = cal.advance(friday, 2, TimeUnit.Days);
        assertEquals(expectedSettlementDate, fwd.settlementDate());

        // Restore evaluation date (defensive — JVM state is global).
        new Settings().setEvaluationDate(vars.today);
        assertEquals(vars.today, new Settings().evaluationDate());
    }
}
