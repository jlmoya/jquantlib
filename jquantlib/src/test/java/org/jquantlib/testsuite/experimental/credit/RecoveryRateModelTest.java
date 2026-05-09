/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */
package org.jquantlib.testsuite.experimental.credit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.experimental.credit.ConstantRecoveryModel;
import org.jquantlib.experimental.credit.DefaultProbKey;
import org.jquantlib.experimental.credit.RecoveryRateModel;
import org.jquantlib.experimental.credit.RecoveryRateQuote;
import org.jquantlib.experimental.credit.Seniority;
import org.jquantlib.quotes.Handle;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.junit.Test;

/**
 * Phase 4m foundation tests for {@link RecoveryRateModel} and
 * {@link ConstantRecoveryModel}.
 *
 * <p>Cross-validation: trivial constant model — value flows from the
 * underlying {@link RecoveryRateQuote}.
 */
public class RecoveryRateModelTest {

    @Test
    public void constantModelFromRecoveryAndSeniority() {
        final ConstantRecoveryModel m = new ConstantRecoveryModel(0.40, Seniority.SnrFor);
        // Same value regardless of date or seniority.
        final Date d = new Date(15, Month.June, 2010);
        assertEquals(0.40, m.recoveryValue(d), 0.0);
        assertEquals(0.40, m.recoveryValue(d, new DefaultProbKey()), 0.0);
        // appliesToSeniority is universal (true for any seniority).
        assertTrue(m.appliesToSeniority(Seniority.SnrFor));
        assertTrue(m.appliesToSeniority(Seniority.SubLT2));
        assertTrue(m.appliesToSeniority(Seniority.NoSeniority));
    }

    @Test
    public void constantModelFromQuoteHandle() {
        final RecoveryRateQuote q = new RecoveryRateQuote(0.55, Seniority.SnrFor);
        final Handle<RecoveryRateQuote> h = new Handle<RecoveryRateQuote>(q);
        final ConstantRecoveryModel m = new ConstantRecoveryModel(h);
        final Date d = new Date(15, Month.June, 2010);
        assertEquals(0.55, m.recoveryValue(d), 0.0);
        // Live link: change quote, value tracks.
        q.setValue(0.65);
        assertEquals(0.65, m.recoveryValue(d), 0.0);
    }

    @Test
    public void constantModelDefaultRecoveryNoSeniority() {
        final ConstantRecoveryModel m = new ConstantRecoveryModel(0.40);
        // appliesToSeniority returns true for any seniority — even NoSeniority.
        assertTrue(m.appliesToSeniority(Seniority.NoSeniority));
        final Date d = new Date(15, Month.June, 2010);
        assertEquals(0.40, m.recoveryValue(d), 0.0);
    }
}
