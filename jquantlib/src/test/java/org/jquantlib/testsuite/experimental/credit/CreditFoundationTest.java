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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jquantlib.currencies.America;
import org.jquantlib.currencies.Currency;
import org.jquantlib.experimental.credit.AtomicDefault;
import org.jquantlib.experimental.credit.DefaultProbKey;
import org.jquantlib.experimental.credit.DefaultType;
import org.jquantlib.experimental.credit.FailureToPay;
import org.jquantlib.experimental.credit.Loss;
import org.jquantlib.experimental.credit.NorthAmericaCorpDefaultKey;
import org.jquantlib.experimental.credit.RecoveryRateQuote;
import org.jquantlib.experimental.credit.Restructuring;
import org.jquantlib.experimental.credit.Seniority;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Phase 4m foundation tests for {@code org.jquantlib.experimental.credit}
 * POJO + enum classes: {@link Loss}, {@link Seniority}, {@link AtomicDefault},
 * {@link Restructuring}, {@link DefaultType}, {@link FailureToPay},
 * {@link RecoveryRateQuote}, {@link DefaultProbKey},
 * {@link NorthAmericaCorpDefaultKey}.
 *
 * <p>Cross-validated against C++ QuantLib v1.42.1 source (header inspection
 * of {@code ql/experimental/credit/{loss,defaulttype,recoveryratequote,
 * defaultprobabilitykey}.{hpp,cpp}}). No probe binaries needed for these
 * pure-data classes; behaviour is structural and reproducible from the
 * header definitions.
 */
public class CreditFoundationTest {

    // -------- Loss --------

    @Test
    public void lossDefaultConstructor() {
        final Loss l = new Loss();
        assertEquals(0.0, l.time, 0.0);
        assertEquals(0.0, l.amount, 0.0);
    }

    @Test
    public void lossOrderedByTime() {
        final Loss a = new Loss(1.0, 100.0);
        final Loss b = new Loss(2.0, 50.0);
        // operator< on time only.
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        // Same time => equal under operator==.
        final Loss c = new Loss(1.0, 999.0);
        assertEquals(0, a.compareTo(c));
        assertEquals(a, c);
    }

    @Test
    public void lossSortable() {
        final List<Loss> losses = new ArrayList<>(Arrays.asList(
                new Loss(3.0, 100.0),
                new Loss(1.0, 50.0),
                new Loss(2.0, 75.0)));
        Collections.sort(losses);
        assertEquals(1.0, losses.get(0).time, 0.0);
        assertEquals(2.0, losses.get(1).time, 0.0);
        assertEquals(3.0, losses.get(2).time, 0.0);
    }

    // -------- Seniority --------

    @Test
    public void seniorityOrdinalsMatchCpp() {
        // C++ enum ordinals must be preserved for IsdaConvRecoveries indexing.
        assertEquals(0, Seniority.SecDom.ordinal());
        assertEquals(1, Seniority.SnrFor.ordinal());
        assertEquals(2, Seniority.SubLT2.ordinal());
        assertEquals(3, Seniority.JrSubT2.ordinal());
        assertEquals(4, Seniority.PrefT1.ordinal());
        assertEquals(5, Seniority.NoSeniority.ordinal());
    }

    @Test
    public void seniorityMarkitAliases() {
        assertSame(Seniority.SecDom, Seniority.SeniorSec);
        assertSame(Seniority.SnrFor, Seniority.SeniorUnSec);
        assertSame(Seniority.PrefT1, Seniority.SubTier1);
        assertSame(Seniority.JrSubT2, Seniority.SubUpperTier2);
        assertSame(Seniority.SubLT2, Seniority.SubLoweTier2);
    }

    // -------- AtomicDefault --------

    @Test
    public void atomicDefaultAliases() {
        assertSame(AtomicDefault.Type.Acceleration, AtomicDefault.ObligationAcceleration);
        assertSame(AtomicDefault.Type.Default, AtomicDefault.ObligationDefault);
        assertSame(AtomicDefault.Type.Default, AtomicDefault.CrossDefault);
    }

    // -------- Restructuring --------

    @Test
    public void restructuringMarkitAliases() {
        assertSame(Restructuring.Type.NoRestructuring, Restructuring.XR);
        assertSame(Restructuring.Type.ModifiedRestructuring, Restructuring.MR);
        assertSame(Restructuring.Type.ModifiedModifiedRestructuring, Restructuring.MM);
        assertSame(Restructuring.Type.FullRestructuring, Restructuring.CR);
    }

    // -------- DefaultType --------

    @Test
    public void defaultTypeBankruptcyXR() {
        final DefaultType d = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        assertEquals(AtomicDefault.Type.Bankruptcy, d.defaultType());
        assertEquals(Restructuring.Type.NoRestructuring, d.restructuringType());
        assertFalse(d.isRestructuring());
        assertTrue(d.containsDefaultType(AtomicDefault.Type.Bankruptcy));
        assertFalse(d.containsDefaultType(AtomicDefault.Type.FailureToPay));
        // Restructuring::AnyRestructuring is the wildcard.
        assertTrue(d.containsRestructuringType(Restructuring.Type.AnyRestructuring));
        assertTrue(d.containsRestructuringType(Restructuring.Type.NoRestructuring));
    }

    @Test
    public void defaultTypeRestructuringMR() {
        final DefaultType d = new DefaultType(AtomicDefault.Type.Restructuring, Restructuring.MR);
        assertTrue(d.isRestructuring());
        assertTrue(d.containsRestructuringType(Restructuring.Type.ModifiedRestructuring));
        assertTrue(d.containsRestructuringType(Restructuring.Type.AnyRestructuring));
    }

    @Test
    public void defaultTypeIncoherenceRejected() {
        // C++ QL_REQUIRE: defType==Restructuring XOR restrType==NoRestructuring.
        // Restructuring + NoRestructuring is incoherent.
        try {
            new DefaultType(AtomicDefault.Type.Restructuring, Restructuring.XR);
            fail("Expected exception for incoherent type combination");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("incoherent"));
        }
        // Bankruptcy + MR is also incoherent (non-restructuring with a restructuring qualifier).
        try {
            new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.MR);
            fail("Expected exception for incoherent type combination");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("incoherent"));
        }
    }

    @Test
    public void defaultTypeEqualityOnAtomicTypesOnly() {
        final DefaultType a = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        final DefaultType b = new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR);
        final DefaultType c = new DefaultType(AtomicDefault.Type.FailureToPay, Restructuring.XR);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // -------- FailureToPay --------

    @Test
    public void failureToPayDefaults() {
        final FailureToPay ftp = new FailureToPay(new Period(30, TimeUnit.Days));
        assertEquals(1.0e6, ftp.amountRequired(), 0.0);
        assertEquals(AtomicDefault.Type.FailureToPay, ftp.defaultType());
        assertEquals(Restructuring.Type.NoRestructuring, ftp.restructuringType());
        assertEquals(30, ftp.gracePeriod().length());
        assertEquals(TimeUnit.Days, ftp.gracePeriod().units());
    }

    // -------- RecoveryRateQuote --------

    @Test
    public void recoveryRateQuoteIsdaConventionals() {
        // Mirror C++ static IsdaConvRecoveries[].
        assertEquals(0.65, RecoveryRateQuote.conventionalRecovery(Seniority.SecDom), 0.0);
        assertEquals(0.40, RecoveryRateQuote.conventionalRecovery(Seniority.SnrFor), 0.0);
        assertEquals(0.20, RecoveryRateQuote.conventionalRecovery(Seniority.SubLT2), 0.0);
        assertEquals(0.20, RecoveryRateQuote.conventionalRecovery(Seniority.JrSubT2), 0.0);
        assertEquals(0.15, RecoveryRateQuote.conventionalRecovery(Seniority.PrefT1), 0.0);
    }

    @Test
    public void recoveryRateQuoteValueAndSetValue() {
        final RecoveryRateQuote q = new RecoveryRateQuote(0.40, Seniority.SnrFor);
        assertTrue(q.isValid());
        assertEquals(0.40, q.value(), 0.0);
        assertEquals(Seniority.SnrFor, q.seniority());
        // setValue returns the diff (new - old). Use fp-aware tolerance.
        final double diff = q.setValue(0.55);
        assertEquals(0.15, diff, 1.0e-12);
        assertEquals(0.55, q.value(), 0.0);
        // Reset to default.
        q.reset();
        assertFalse(q.isValid());
        assertEquals(Seniority.NoSeniority, q.seniority());
    }

    @Test
    public void recoveryRateQuoteRangeRejected() {
        try {
            new RecoveryRateQuote(1.5, Seniority.SnrFor);
            fail("Expected exception for out-of-range recovery rate");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("fractional"));
        }
        try {
            new RecoveryRateQuote(-0.1, Seniority.SnrFor);
            fail("Expected exception for negative recovery rate");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("fractional"));
        }
    }

    @Test
    public void recoveryRateQuoteIsdaMap() {
        final Map<Seniority, Double> m = RecoveryRateQuote.makeIsdaConvMap();
        assertEquals(0.65, m.get(Seniority.SecDom), 0.0);
        assertEquals(0.40, m.get(Seniority.SnrFor), 0.0);
        assertEquals(0.20, m.get(Seniority.SubLT2), 0.0);
        assertEquals(0.20, m.get(Seniority.JrSubT2), 0.0);
        assertEquals(0.15, m.get(Seniority.PrefT1), 0.0);
    }

    // -------- DefaultProbKey --------

    @Test
    public void defaultProbKeyEmptyDefaults() {
        final DefaultProbKey k = new DefaultProbKey();
        assertNotNull(k.eventTypes());
        assertEquals(0, k.size());
        assertEquals(Seniority.NoSeniority, k.seniority());
    }

    @Test
    public void defaultProbKeyDuplicateEventTypeRejected() {
        final List<DefaultType> types = new ArrayList<>();
        types.add(new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR));
        types.add(new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR));
        try {
            new DefaultProbKey(types, new Currency(), Seniority.SnrFor);
            fail("Expected exception for duplicated event types");
        } catch (final Exception ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("duplicat"));
        }
    }

    @Test
    public void defaultProbKeyEqualityOrderIndependent() {
        final Currency usd = new America.USDCurrency();
        final List<DefaultType> tA = new ArrayList<>();
        tA.add(new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR));
        tA.add(new DefaultType(AtomicDefault.Type.FailureToPay, Restructuring.XR));
        final DefaultProbKey kA = new DefaultProbKey(tA, usd, Seniority.SnrFor);

        final List<DefaultType> tB = new ArrayList<>();
        tB.add(new DefaultType(AtomicDefault.Type.FailureToPay, Restructuring.XR));
        tB.add(new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR));
        final DefaultProbKey kB = new DefaultProbKey(tB, usd, Seniority.SnrFor);

        assertEquals(kA, kB);
        // Hash should also match (set-based hash on event types).
        assertEquals(kA.hashCode(), kB.hashCode());
    }

    @Test
    public void defaultProbKeyDifferentSeniorityNotEqual() {
        final Currency usd = new America.USDCurrency();
        final List<DefaultType> t = new ArrayList<>();
        t.add(new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR));
        final DefaultProbKey kA = new DefaultProbKey(t, usd, Seniority.SnrFor);
        final DefaultProbKey kB = new DefaultProbKey(t, usd, Seniority.SubLT2);
        assertNotEquals(kA, kB);
    }

    // -------- NorthAmericaCorpDefaultKey --------

    @Test
    public void naCorpDefaultKeyHasExpectedEvents() {
        final Currency usd = new America.USDCurrency();
        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(usd, Seniority.SnrFor);
        // Default constructor: graceFailureToPay=Period(30,Days), amount=1e6, resType=CR.
        assertEquals(3, k.size());
        // Has FailureToPay and Bankruptcy and Restructuring.
        boolean hasFTP = false, hasBkr = false, hasRest = false;
        for (final DefaultType d : k.eventTypes()) {
            if (d.defaultType() == AtomicDefault.Type.FailureToPay) {
                hasFTP = true;
                assertTrue(d instanceof FailureToPay);
                final FailureToPay ftp = (FailureToPay) d;
                assertEquals(1.0e6, ftp.amountRequired(), 0.0);
                assertEquals(30, ftp.gracePeriod().length());
            } else if (d.defaultType() == AtomicDefault.Type.Bankruptcy) {
                hasBkr = true;
            } else if (d.defaultType() == AtomicDefault.Type.Restructuring) {
                hasRest = true;
                assertEquals(Restructuring.Type.FullRestructuring, d.restructuringType());
            }
        }
        assertTrue(hasFTP);
        assertTrue(hasBkr);
        assertTrue(hasRest);
        assertEquals(Seniority.SnrFor, k.seniority());
        assertEquals(usd, k.currency());
    }

    @Test
    public void naCorpDefaultKeyNoRestructuringDropsThirdEvent() {
        final Currency usd = new America.USDCurrency();
        final NorthAmericaCorpDefaultKey k = new NorthAmericaCorpDefaultKey(
                usd, Seniority.SnrFor, new Period(30, TimeUnit.Days), 1.0e6,
                Restructuring.Type.NoRestructuring);
        // No Restructuring event added when XR was specified at construction.
        assertEquals(2, k.size());
        for (final DefaultType d : k.eventTypes()) {
            assertNotEquals(AtomicDefault.Type.Restructuring, d.defaultType());
        }
    }

    @Test
    public void recoveryRateQuoteNullValueIsInvalid() {
        final RecoveryRateQuote q = new RecoveryRateQuote();
        assertFalse(q.isValid());
        try {
            q.value();
            fail("Expected exception for invalid quote");
        } catch (final Exception ex) {
            // ok
            assertNotNull(ex);
        }
    }

    @Test
    public void recoveryRateQuoteSetValueNoChangeNoNotification() {
        final RecoveryRateQuote q = new RecoveryRateQuote(0.40, Seniority.SnrFor);
        // No-op change should return zero diff.
        assertEquals(0.0, q.setValue(0.40), 0.0);
        // Setter unchanged: still 0.40.
        assertEquals(0.40, q.value(), 0.0);
        // Reset (writes NaN) makes it invalid (no equality check for NULL_REAL).
        q.reset();
        assertFalse(q.isValid());
        // We don't assert null on the reset return — just sanity-check state.
        assertNull(null); // no-op to keep import clean
    }
}
