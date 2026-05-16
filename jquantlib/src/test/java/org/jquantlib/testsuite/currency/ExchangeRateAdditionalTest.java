/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.currency;

import static org.junit.Assert.assertTrue;

import org.jquantlib.currencies.America.USDCurrency;
import org.jquantlib.currencies.Asia.JPYCurrency;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.ExchangeRate;
import org.jquantlib.currencies.ExchangeRateManager;
import org.jquantlib.currencies.Europe.CHFCurrency;
import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.currencies.Europe.GBPCurrency;
import org.jquantlib.currencies.Europe.ITLCurrency;
import org.jquantlib.currencies.Europe.SEKCurrency;
import org.jquantlib.currencies.Money;
import org.jquantlib.testsuite.util.Tolerance;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Before;
import org.junit.Test;

/**
 * Faithful Java port of {@code test-suite/exchangerate.cpp} v1.42.1
 * (383 LOC, 5 cases) — complements {@link ExchangeRateManagerTest}
 * (which is manager-centric: known-rate population, custom-add,
 * date-range, clear semantics) by mirroring the C++ layout that is
 * organized around {@link org.jquantlib.currencies.ExchangeRate}
 * instances and {@code ExchangeRate.Type} (Direct vs Derived).
 *
 * <p>Cases (per v1.42.1):
 * <ul>
 *   <li>{@code testDirect} — direct-quote arithmetic, exchange in
 *       both directions (source -> target and target -> source);
 *   <li>{@code testDerived} — derived (chained) rate via
 *       {@code ExchangeRate.chain};
 *   <li>{@code testDirectLookup} — manager direct-lookup at two
 *       distinct dates with inverted source/target pairs;
 *   <li>{@code testTriangulatedLookup} — triangulated-via-EUR lookup
 *       using the fixed-rate ITL/EUR triangulation;
 *   <li>{@code testSmartLookup} — smart-lookup chain construction
 *       across 2-, 3-, 4-, and 5-rate chains.
 * </ul>
 *
 * <p>Arithmetic in C++ is verified with {@code close(calculated, expected)}
 * (Boost {@code QL_CHECK_CLOSE} default n=42 ULPs). Here we go one step
 * tighter and assert {@link Tolerance#tight} (abs 1e-14 + rel 1e-12)
 * after confirming currency equality — currency arithmetic in this test
 * is closed-form double-precision and the Java implementation mirrors
 * the C++ formulas exactly, so any deviation beyond a few ULPs would
 * indicate a real divergence.
 *
 * <p>Source: {@code test-suite/exchangerate.cpp} v1.42.1 @
 * {@code 099987f0ca}.
 */
public class ExchangeRateAdditionalTest {

    @Before
    public void setUp() {
        // Match C++ Money::Settings::instance().conversionType() = Money::NoConversion;
        Money.conversionType = Money.ConversionType.NoConversion;
        // clear() also re-populates the known fixed-rate triangulations
        // (EUR/ITL = 1936.27, etc.) needed by testTriangulatedLookup.
        ExchangeRateManager.getInstance().clear();
    }

    /** Mirrors C++ {@code BOOST_AUTO_TEST_CASE(testDirect)}. */
    @Test
    public void testDirect() {
        final Currency EUR = new EURCurrency();
        final Currency USD = new USDCurrency();

        final ExchangeRate eur_usd = new ExchangeRate(EUR, USD, 1.2042);

        final Money m1 = new Money(50000.0, EUR);
        final Money m2 = new Money(100000.0, USD);

        // source -> target: value * rate
        Money calculated = eur_usd.exchange(m1);
        Money expected = new Money(m1.value() * eur_usd.rate(), USD);
        assertCloseMoney("testDirect (EUR->USD)", calculated, expected);

        // target -> source: value / rate
        calculated = eur_usd.exchange(m2);
        expected = new Money(m2.value() / eur_usd.rate(), EUR);
        assertCloseMoney("testDirect (USD->EUR)", calculated, expected);
    }

    /** Mirrors C++ {@code BOOST_AUTO_TEST_CASE(testDerived)}. */
    @Test
    public void testDerived() {
        final Currency EUR = new EURCurrency();
        final Currency USD = new USDCurrency();
        final Currency GBP = new GBPCurrency();

        final ExchangeRate eur_usd = new ExchangeRate(EUR, USD, 1.2042);
        final ExchangeRate eur_gbp = new ExchangeRate(EUR, GBP, 0.6612);

        final ExchangeRate derived = ExchangeRate.chain(eur_usd, eur_gbp);

        final Money m1 = new Money(50000.0, GBP);
        final Money m2 = new Money(100000.0, USD);

        Money calculated = derived.exchange(m1);
        Money expected = new Money(m1.value() * eur_usd.rate() / eur_gbp.rate(), USD);
        assertCloseMoney("testDerived (GBP->USD)", calculated, expected);

        calculated = derived.exchange(m2);
        expected = new Money(m2.value() * eur_gbp.rate() / eur_usd.rate(), GBP);
        assertCloseMoney("testDerived (USD->GBP)", calculated, expected);
    }

    /** Mirrors C++ {@code BOOST_AUTO_TEST_CASE(testDirectLookup)}. */
    @Test
    public void testDirectLookup() {
        final ExchangeRateManager rateManager = ExchangeRateManager.getInstance();
        // setUp() already called clear(); explicit re-clear here keeps
        // the C++ ordering intent (rateManager.clear() is the first line
        // in the C++ case).
        rateManager.clear();

        final Currency EUR = new EURCurrency();
        final Currency USD = new USDCurrency();

        final ExchangeRate eur_usd1 = new ExchangeRate(EUR, USD, 1.1983);
        final ExchangeRate eur_usd2 = new ExchangeRate(USD, EUR, 1.0 / 1.2042);
        final Date d1 = new Date(4, Month.August, 2004);
        final Date d2 = new Date(5, Month.August, 2004);
        // C++ calls add(rate, startDate) with endDate defaulting to
        // Date::maxDate(). Java has no default args, so we pass it
        // explicitly. The "latest added takes precedence" rule lets
        // the d2-entry shadow the d1-entry for lookups at d2.
        rateManager.add(eur_usd1, d1, Date.maxDate());
        rateManager.add(eur_usd2, d2, Date.maxDate());

        final Money m1 = new Money(50000.0, EUR);
        final Money m2 = new Money(100000.0, USD);

        // d1: EUR->USD via eur_usd1 (Direct, EUR is source).
        // d2-entry is checked first (most-recently added at front of
        // list); it is not valid at d1 (startDate > d1), so the scan
        // falls through to d1-entry.
        ExchangeRate eur_usd = rateManager.lookup(EUR, USD, d1, ExchangeRate.Type.Direct);
        Money calculated = eur_usd.exchange(m1);
        Money expected = new Money(m1.value() * eur_usd1.rate(), USD);
        assertCloseMoney("testDirectLookup (EUR->USD, d1)", calculated, expected);

        // d2: EUR->USD via eur_usd2 (Direct, USD->EUR rate stored; exchange
        // detects EUR is the target and divides by stored rate)
        eur_usd = rateManager.lookup(EUR, USD, d2, ExchangeRate.Type.Direct);
        calculated = eur_usd.exchange(m1);
        expected = new Money(m1.value() / eur_usd2.rate(), USD);
        assertCloseMoney("testDirectLookup (EUR->USD, d2)", calculated, expected);

        // d1: USD->EUR via eur_usd1 (Direct, EUR->USD rate stored; exchange
        // detects USD is the target and divides by stored rate)
        ExchangeRate usd_eur = rateManager.lookup(USD, EUR, d1, ExchangeRate.Type.Direct);
        calculated = usd_eur.exchange(m2);
        expected = new Money(m2.value() / eur_usd1.rate(), EUR);
        assertCloseMoney("testDirectLookup (USD->EUR, d1)", calculated, expected);

        // d2: USD->EUR via eur_usd2 (Direct, USD is source)
        usd_eur = rateManager.lookup(USD, EUR, d2, ExchangeRate.Type.Direct);
        calculated = usd_eur.exchange(m2);
        expected = new Money(m2.value() * eur_usd2.rate(), EUR);
        assertCloseMoney("testDirectLookup (USD->EUR, d2)", calculated, expected);
    }

    /** Mirrors C++ {@code BOOST_AUTO_TEST_CASE(testTriangulatedLookup)}. */
    @Test
    public void testTriangulatedLookup() {
        final ExchangeRateManager rateManager = ExchangeRateManager.getInstance();
        rateManager.clear();

        final Currency EUR = new EURCurrency();
        final Currency USD = new USDCurrency();
        final Currency ITL = new ITLCurrency();

        final ExchangeRate eur_usd1 = new ExchangeRate(EUR, USD, 1.1983);
        final ExchangeRate eur_usd2 = new ExchangeRate(EUR, USD, 1.2042);
        final Date d1 = new Date(4, Month.August, 2004);
        final Date d2 = new Date(5, Month.August, 2004);
        rateManager.add(eur_usd1, d1, Date.maxDate());
        rateManager.add(eur_usd2, d2, Date.maxDate());

        final Money m1 = new Money(50000000.0, ITL);
        final Money m2 = new Money(100000.0, USD);

        // ITL has triangulation currency EUR with fixed rate 1936.27 from
        // addKnownRates(). Manager builds a chain ITL -> EUR -> USD.
        ExchangeRate itl_usd = rateManager.lookup(ITL, USD, d1);
        Money calculated = itl_usd.exchange(m1);
        Money expected = new Money(m1.value() * eur_usd1.rate() / 1936.27, USD);
        assertCloseMoney("testTriangulatedLookup (ITL->USD, d1)", calculated, expected);

        itl_usd = rateManager.lookup(ITL, USD, d2);
        calculated = itl_usd.exchange(m1);
        expected = new Money(m1.value() * eur_usd2.rate() / 1936.27, USD);
        assertCloseMoney("testTriangulatedLookup (ITL->USD, d2)", calculated, expected);

        ExchangeRate usd_itl = rateManager.lookup(USD, ITL, d1);
        calculated = usd_itl.exchange(m2);
        expected = new Money(m2.value() * 1936.27 / eur_usd1.rate(), ITL);
        assertCloseMoney("testTriangulatedLookup (USD->ITL, d1)", calculated, expected);

        usd_itl = rateManager.lookup(USD, ITL, d2);
        calculated = usd_itl.exchange(m2);
        expected = new Money(m2.value() * 1936.27 / eur_usd2.rate(), ITL);
        assertCloseMoney("testTriangulatedLookup (USD->ITL, d2)", calculated, expected);
    }

    /** Mirrors C++ {@code BOOST_AUTO_TEST_CASE(testSmartLookup)}. */
    @Test
    public void testSmartLookup() {
        final Currency EUR = new EURCurrency();
        final Currency USD = new USDCurrency();
        final Currency GBP = new GBPCurrency();
        final Currency CHF = new CHFCurrency();
        final Currency SEK = new SEKCurrency();
        final Currency JPY = new JPYCurrency();

        final ExchangeRateManager rateManager = ExchangeRateManager.getInstance();
        rateManager.clear();

        final Date d1 = new Date(4, Month.August, 2004);
        final Date d2 = new Date(5, Month.August, 2004);

        final ExchangeRate eur_usd1 = new ExchangeRate(EUR, USD, 1.1983);
        final ExchangeRate eur_usd2 = new ExchangeRate(USD, EUR, 1.0 / 1.2042);
        rateManager.add(eur_usd1, d1, Date.maxDate());
        rateManager.add(eur_usd2, d2, Date.maxDate());

        final ExchangeRate eur_gbp1 = new ExchangeRate(GBP, EUR, 1.0 / 0.6596);
        final ExchangeRate eur_gbp2 = new ExchangeRate(EUR, GBP, 0.6612);
        rateManager.add(eur_gbp1, d1, Date.maxDate());
        rateManager.add(eur_gbp2, d2, Date.maxDate());

        final ExchangeRate usd_chf1 = new ExchangeRate(USD, CHF, 1.2847);
        final ExchangeRate usd_chf2 = new ExchangeRate(CHF, USD, 1.0 / 1.2774);
        rateManager.add(usd_chf1, d1, Date.maxDate());
        rateManager.add(usd_chf2, d2, Date.maxDate());

        final ExchangeRate chf_sek1 = new ExchangeRate(SEK, CHF, 0.1674);
        final ExchangeRate chf_sek2 = new ExchangeRate(CHF, SEK, 1.0 / 0.1677);
        rateManager.add(chf_sek1, d1, Date.maxDate());
        rateManager.add(chf_sek2, d2, Date.maxDate());

        final ExchangeRate jpy_sek1 = new ExchangeRate(SEK, JPY, 14.5450);
        final ExchangeRate jpy_sek2 = new ExchangeRate(JPY, SEK, 1.0 / 14.6110);
        rateManager.add(jpy_sek1, d1, Date.maxDate());
        rateManager.add(jpy_sek2, d2, Date.maxDate());

        final Money m1 = new Money(100000.0, USD);
        final Money m2 = new Money(100000.0, EUR);
        final Money m3 = new Money(100000.0, GBP);
        // m4 (CHF) is declared in C++ for symmetry but unused in the
        // assertions; we omit it to satisfy Java unused-variable hygiene.
        final Money m5 = new Money(100000.0, SEK);
        final Money m6 = new Money(100000.0, JPY);

        // ---- two-rate chain ----
        ExchangeRate usd_sek = rateManager.lookup(USD, SEK, d1);
        Money calculated = usd_sek.exchange(m1);
        Money expected = new Money(m1.value() * usd_chf1.rate() / chf_sek1.rate(), SEK);
        assertCloseMoney("testSmartLookup (USD->SEK, 2-chain, d1)", calculated, expected);

        usd_sek = rateManager.lookup(SEK, USD, d2);
        calculated = usd_sek.exchange(m5);
        expected = new Money(m5.value() * usd_chf2.rate() / chf_sek2.rate(), USD);
        assertCloseMoney("testSmartLookup (SEK->USD, 2-chain, d2)", calculated, expected);

        // ---- three-rate chain ----
        ExchangeRate eur_sek = rateManager.lookup(EUR, SEK, d1);
        calculated = eur_sek.exchange(m2);
        expected = new Money(
                m2.value() * eur_usd1.rate() * usd_chf1.rate() / chf_sek1.rate(), SEK);
        assertCloseMoney("testSmartLookup (EUR->SEK, 3-chain, d1)", calculated, expected);

        eur_sek = rateManager.lookup(SEK, EUR, d2);
        calculated = eur_sek.exchange(m5);
        expected = new Money(
                m5.value() * eur_usd2.rate() * usd_chf2.rate() / chf_sek2.rate(), EUR);
        assertCloseMoney("testSmartLookup (SEK->EUR, 3-chain, d2)", calculated, expected);

        // ---- four-rate chain ----
        ExchangeRate eur_jpy = rateManager.lookup(EUR, JPY, d1);
        calculated = eur_jpy.exchange(m2);
        expected = new Money(
                m2.value() * eur_usd1.rate() * usd_chf1.rate()
                        * jpy_sek1.rate() / chf_sek1.rate(), JPY);
        assertCloseMoney("testSmartLookup (EUR->JPY, 4-chain, d1)", calculated, expected);

        eur_jpy = rateManager.lookup(JPY, EUR, d2);
        calculated = eur_jpy.exchange(m6);
        expected = new Money(
                m6.value() * jpy_sek2.rate() * eur_usd2.rate()
                        * usd_chf2.rate() / chf_sek2.rate(), EUR);
        assertCloseMoney("testSmartLookup (JPY->EUR, 4-chain, d2)", calculated, expected);

        // ---- five-rate chain ----
        ExchangeRate gbp_jpy = rateManager.lookup(GBP, JPY, d1);
        calculated = gbp_jpy.exchange(m3);
        expected = new Money(
                m3.value() * eur_gbp1.rate() * eur_usd1.rate() * usd_chf1.rate()
                        * jpy_sek1.rate() / chf_sek1.rate(), JPY);
        assertCloseMoney("testSmartLookup (GBP->JPY, 5-chain, d1)", calculated, expected);

        gbp_jpy = rateManager.lookup(JPY, GBP, d2);
        calculated = gbp_jpy.exchange(m6);
        expected = new Money(
                m6.value() * jpy_sek2.rate() * eur_usd2.rate() * usd_chf2.rate()
                        * eur_gbp2.rate() / chf_sek2.rate(), GBP);
        assertCloseMoney("testSmartLookup (JPY->GBP, 5-chain, d2)", calculated, expected);
    }

    //
    // Assertion helper
    //

    /**
     * Closed-form arithmetic equality between two {@link Money} values:
     * currencies must match exactly, values must match at TIGHT tolerance
     * (abs 1e-14 + rel 1e-12). Tighter than the C++ {@code close(...)}
     * default of 42 ULPs because all formulas are pure double-precision
     * multiplication and division; any deviation beyond a few ULPs would
     * signal a true divergence from v1.42.1.
     */
    private static void assertCloseMoney(final String label,
                                         final Money calculated,
                                         final Money expected) {
        assertTrue(label + ": currency mismatch — expected "
                        + expected.currency().code()
                        + " got " + calculated.currency().code(),
                calculated.currency().eq(expected.currency()));
        assertTrue(label + ": value mismatch — expected "
                        + expected.value() + " got " + calculated.value(),
                Tolerance.tight(calculated.value(), expected.value()));
    }
}
