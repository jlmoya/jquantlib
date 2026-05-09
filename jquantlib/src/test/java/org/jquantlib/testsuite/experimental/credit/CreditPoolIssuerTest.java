/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.jquantlib.Settings;
import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Currency;
import org.jquantlib.currencies.Europe;
import org.jquantlib.experimental.credit.AtomicDefault;
import org.jquantlib.experimental.credit.BankruptcyEvent;
import org.jquantlib.experimental.credit.DefaultEvent;
import org.jquantlib.experimental.credit.DefaultProbKey;
import org.jquantlib.experimental.credit.DefaultType;
import org.jquantlib.experimental.credit.FailureToPay;
import org.jquantlib.experimental.credit.FailureToPayEvent;
import org.jquantlib.experimental.credit.Issuer;
import org.jquantlib.experimental.credit.NorthAmericaCorpDefaultKey;
import org.jquantlib.experimental.credit.Pool;
import org.jquantlib.experimental.credit.RecoveryRateQuote;
import org.jquantlib.experimental.credit.Restructuring;
import org.jquantlib.experimental.credit.Seniority;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Phase 4m foundation tests for {@code DefaultEvent}, {@code BankruptcyEvent},
 * {@code FailureToPayEvent}, {@code Issuer}, {@code Pool}.
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1
 * ({@code ql/experimental/credit/{defaultevent,issuer,pool}.{hpp,cpp}}).
 */
public class CreditPoolIssuerTest {

    private Date savedEvalDate;

    @Before
    public void setUp() {
        savedEvalDate = new Settings().evaluationDate();
        new Settings().setEvaluationDate(new Date(15, Month.June, 2010));
    }

    @After
    public void tearDown() {
        new Settings().setEvaluationDate(savedEvalDate);
    }

    // -------- DefaultEvent --------

    @Test
    public void defaultEventBasicAccessors() {
        final Currency usd = new America.USDCurrency();
        final Date d = new Date(1, Month.January, 2009);
        final DefaultType type = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        final DefaultEvent ev = new DefaultEvent(d, type, usd, Seniority.SnrFor,
                new Date(), 0.4);
        assertEquals(d, ev.date());
        assertEquals(type, ev.defaultType());
        assertEquals(usd, ev.currency());
        assertEquals(Seniority.SnrFor, ev.eventSeniority());
        // Bankruptcy is not a restructuring.
        assertFalse(ev.isRestructuring());
        assertTrue(ev.isDefault());
        // Default-constructed settle date is the null Date sentinel — hasSettled false.
        assertFalse(ev.hasSettled());
    }

    @Test
    public void defaultEventEqualityIgnoresSettlement() {
        final Currency usd = new America.USDCurrency();
        final Date d = new Date(1, Month.January, 2009);
        final DefaultType type = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        final DefaultEvent a = new DefaultEvent(d, type, usd, Seniority.SnrFor, new Date(), 0.4);
        final DefaultEvent b = new DefaultEvent(d, type, usd, Seniority.SnrFor, new Date(), 0.7);
        // Same currency / default type / date / seniority — equal regardless of recovery.
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        // Different seniority → not equal.
        final DefaultEvent c = new DefaultEvent(d, type, usd, Seniority.SubLT2, new Date(), 0.4);
        assertNotEquals(a, c);
    }

    @Test
    public void defaultEventMatchesDefaultKey() {
        final Currency usd = new America.USDCurrency();
        final Date d = new Date(1, Month.January, 2009);
        final DefaultType type = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        final DefaultEvent ev = new DefaultEvent(d, type, usd, Seniority.SnrFor,
                new Date(), 0.4);
        // Build a NA-Corp key for USD/SnrFor — matches our event's bankruptcy.
        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);
        assertTrue(ev.matchesDefaultKey(k));
        // Different currency → no match.
        final NorthAmericaCorpDefaultKey kEur = new NorthAmericaCorpDefaultKey(
                new Europe.EURCurrency(), Seniority.SnrFor);
        assertFalse(ev.matchesDefaultKey(kEur));
        // NoSeniority key matches all seniorities.
        final NorthAmericaCorpDefaultKey kNo = new NorthAmericaCorpDefaultKey(
                usd, Seniority.NoSeniority);
        assertTrue(ev.matchesDefaultKey(kNo));
    }

    @Test
    public void defaultEventSettlementWithMap() {
        final Currency usd = new America.USDCurrency();
        final Date d = new Date(1, Month.January, 2009);
        final Date settle = new Date(15, Month.January, 2009);
        final DefaultType type = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        final Map<Seniority, Double> recoveries = new EnumMap<>(Seniority.class);
        recoveries.put(Seniority.SecDom, 0.65);
        recoveries.put(Seniority.SnrFor, 0.42);
        recoveries.put(Seniority.SubLT2, 0.20);
        recoveries.put(Seniority.JrSubT2, 0.20);
        recoveries.put(Seniority.PrefT1, 0.15);

        final DefaultEvent ev = new DefaultEvent(d, type, usd, Seniority.SnrFor,
                settle, recoveries);
        assertTrue(ev.hasSettled());
        assertEquals(settle, ev.settlement().date());
        assertEquals(0.42, ev.recoveryRate(Seniority.SnrFor), 0.0);
        assertEquals(0.65, ev.recoveryRate(Seniority.SecDom), 0.0);
    }

    @Test
    public void defaultEventSettlementBeforeDefaultRejected() {
        final Currency usd = new America.USDCurrency();
        final Date d = new Date(1, Month.January, 2009);
        final Date earlierSettle = new Date(31, Month.December, 2008);
        final DefaultType type = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        final Map<Seniority, Double> recoveries = new EnumMap<>(Seniority.class);
        recoveries.put(Seniority.SnrFor, 0.40);
        try {
            new DefaultEvent(d, type, usd, Seniority.SnrFor, earlierSettle, recoveries);
            fail("Expected exception for settlement before default");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("settlement date"));
        }
    }

    // -------- BankruptcyEvent --------

    @Test
    public void bankruptcyMatchesAnyEventType() {
        final Currency usd = new America.USDCurrency();
        final Date d = new Date(1, Month.January, 2009);
        final BankruptcyEvent be = new BankruptcyEvent(d, usd, Seniority.SnrFor,
                new Date(), 0.4);
        // Any type matches — even FailureToPay.
        assertTrue(be.matchesEventType(new DefaultType(
                AtomicDefault.Type.FailureToPay, Restructuring.XR)));
        assertTrue(be.matchesEventType(new DefaultType(
                AtomicDefault.Type.Restructuring, Restructuring.MR)));
        assertTrue(be.matchesEventType(new FailureToPay(new Period(30, TimeUnit.Days))));
    }

    // -------- FailureToPayEvent --------

    @Test
    public void failureToPayEventBelowAmountNoMatch() {
        final Currency usd = new America.USDCurrency();
        // Default occurred far in the past so grace period doesn't matter.
        final Date d = new Date(1, Month.January, 2008);
        final FailureToPayEvent ftpe = new FailureToPayEvent(d, usd, Seniority.SnrFor,
                500_000.0, new Date(), 0.4);
        // Contract requires 1e6 — our event is 500k, below threshold.
        final FailureToPay contract = new FailureToPay(new Period(30, TimeUnit.Days), 1.0e6);
        assertFalse(ftpe.matchesEventType(contract));
    }

    @Test
    public void failureToPayEventAboveAmountAndPastGraceMatches() {
        final Currency usd = new America.USDCurrency();
        // Default occurred long before today, so today - gracePeriod is well after default.
        final Date d = new Date(1, Month.January, 2008);
        final FailureToPayEvent ftpe = new FailureToPayEvent(d, usd, Seniority.SnrFor,
                2.0e6, new Date(), 0.4);
        final FailureToPay contract = new FailureToPay(new Period(30, TimeUnit.Days), 1.0e6);
        assertTrue(ftpe.matchesEventType(contract));
    }

    @Test
    public void failureToPayEventNonFTPContractNoMatch() {
        final Currency usd = new America.USDCurrency();
        final Date d = new Date(1, Month.January, 2008);
        final FailureToPayEvent ftpe = new FailureToPayEvent(d, usd, Seniority.SnrFor,
                2.0e6, new Date(), 0.4);
        final DefaultType nonFtp = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        assertFalse(ftpe.matchesEventType(nonFtp));
    }

    // -------- Issuer --------

    @Test
    public void issuerEmptyDefaults() {
        final Issuer issuer = new Issuer();
        // No probabilities ⇒ defaultProbability throws.
        try {
            issuer.defaultProbability(new DefaultProbKey());
            fail("Expected exception when no probabilities defined");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("not available"));
        }
    }

    @Test
    public void issuerDefaultsBetweenEmptyReturnsEmpty() {
        final Issuer issuer = new Issuer();
        final Currency usd = new America.USDCurrency();
        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);
        // No events → no defaults found.
        assertNull(issuer.defaultedBetween(
                new Date(1, Month.January, 2008),
                new Date(31, Month.December, 2008), k));
        assertTrue(issuer.defaultsBetween(
                new Date(1, Month.January, 2008),
                new Date(31, Month.December, 2008), k, false).isEmpty());
    }

    @Test
    public void issuerDefaultedBetweenFindsMatchingEvent() {
        final Currency usd = new America.USDCurrency();
        final TreeSet<DefaultEvent> events = new TreeSet<>(Issuer.EARLIER_THAN);
        events.add(new BankruptcyEvent(new Date(15, Month.June, 2008),
                usd, Seniority.SnrFor, new Date(), 0.4));
        final Issuer issuer = new Issuer(new ArrayList<>(), events);

        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);
        // Search window covers the event date.
        final DefaultEvent found = issuer.defaultedBetween(
                new Date(1, Month.January, 2008),
                new Date(31, Month.December, 2008), k);
        assertNotNull(found);
        assertEquals(new Date(15, Month.June, 2008), found.date());
        // Search window before the event → no match.
        assertNull(issuer.defaultedBetween(
                new Date(1, Month.January, 2007),
                new Date(31, Month.December, 2007), k));
    }

    // -------- Pool --------

    @Test
    public void poolEmptyDefaults() {
        final Pool pool = new Pool();
        assertEquals(0, pool.size());
        assertTrue(pool.names().isEmpty());
        assertFalse(pool.has("anybody"));
    }

    @Test
    public void poolAddRetrieveOrdered() {
        final Pool pool = new Pool();
        final Issuer i1 = new Issuer();
        final Issuer i2 = new Issuer();
        pool.add("name-A", i1);
        pool.add("name-B", i2);
        assertEquals(2, pool.size());
        assertTrue(pool.has("name-A"));
        assertTrue(pool.has("name-B"));
        // Insertion order preserved.
        assertEquals("name-A", pool.names().get(0));
        assertEquals("name-B", pool.names().get(1));
        // get returns the issuer reference.
        assertSame(i1, pool.get("name-A"));
        assertSame(i2, pool.get("name-B"));
    }

    @Test
    public void poolAddDuplicateIsNoOp() {
        final Pool pool = new Pool();
        final Issuer i1 = new Issuer();
        final Issuer i2 = new Issuer();
        pool.add("name-A", i1);
        // C++ behaviour: silent no-op when name already present.
        pool.add("name-A", i2);
        assertEquals(1, pool.size());
        assertSame(i1, pool.get("name-A"));
    }

    @Test
    public void poolGetMissingThrows() {
        final Pool pool = new Pool();
        try {
            pool.get("missing");
            fail("Expected exception for missing name");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("not found"));
        }
    }

    @Test
    public void poolTimeAccessors() {
        final Pool pool = new Pool();
        pool.add("X", new Issuer());
        // Default time is 0.0.
        assertEquals(0.0, pool.getTime("X"), 0.0);
        pool.setTime("X", 3.5);
        assertEquals(3.5, pool.getTime("X"), 0.0);
    }

    @Test
    public void poolDefaultKeysExposed() {
        final Pool pool = new Pool();
        final Currency usd = new America.USDCurrency();
        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);
        pool.add("X", new Issuer(), k);
        assertEquals(k, pool.defaultKey("X"));
        final List<DefaultProbKey> keys = pool.defaultKeys();
        assertEquals(1, keys.size());
        assertEquals(k, keys.get(0));
    }

    @Test
    public void poolClearResetsAll() {
        final Pool pool = new Pool();
        pool.add("X", new Issuer());
        pool.add("Y", new Issuer());
        pool.clear();
        assertEquals(0, pool.size());
        assertTrue(pool.names().isEmpty());
        // After clear, defaultKeys should also be empty (regression Phase 4m.7b).
        assertTrue(pool.defaultKeys().isEmpty());
    }

    /**
     * Regression: {@code defaultKeys()} must align 1:1 with {@code names()} in
     * insertion order. Prior implementation backed the map with {@code HashMap},
     * which produced non-deterministic order — basket / latent-model consumers
     * rely on {@code defaultKeys()[i]} corresponding to {@code names()[i]}.
     * Phase 4m.7b: switched to {@code LinkedHashMap}.
     */
    @Test
    public void poolDefaultKeysAlignWithNamesInsertionOrder() {
        final Pool pool = new Pool();
        final Currency usd = new America.USDCurrency();
        final Currency eur = new Europe.EURCurrency();
        // Insert in reverse-alphabetical order to defeat any alphabetical-sort assumption.
        final NorthAmericaCorpDefaultKey kZ = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);
        final NorthAmericaCorpDefaultKey kM = new NorthAmericaCorpDefaultKey(eur, Seniority.SubLT2);
        final NorthAmericaCorpDefaultKey kA = new NorthAmericaCorpDefaultKey(usd, Seniority.SecDom);
        pool.add("Zeta", new Issuer(), kZ);
        pool.add("Mu",   new Issuer(), kM);
        pool.add("Alpha", new Issuer(), kA);
        // Names in insertion order.
        assertEquals("Zeta",  pool.names().get(0));
        assertEquals("Mu",    pool.names().get(1));
        assertEquals("Alpha", pool.names().get(2));
        // defaultKeys() in the SAME order — not alphabetical.
        final List<DefaultProbKey> keys = pool.defaultKeys();
        assertEquals(3, keys.size());
        assertEquals(kZ, keys.get(0));
        assertEquals(kM, keys.get(1));
        assertEquals(kA, keys.get(2));
    }

    @Test
    public void recoveryRateQuoteIsdaConvMapHasFiveSeniorities() {
        // Sanity: the map covers the 5 ISDA seniorities (SecDom..PrefT1).
        final Map<Seniority, Double> m = RecoveryRateQuote.makeIsdaConvMap();
        assertEquals(5, m.size());
        assertFalse(m.containsKey(Seniority.NoSeniority));
    }
}
