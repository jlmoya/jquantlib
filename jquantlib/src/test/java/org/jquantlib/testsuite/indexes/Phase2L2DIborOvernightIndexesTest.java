/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.
 */

package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jquantlib.indexes.EURLiborON;
import org.jquantlib.indexes.Euribor1W;
import org.jquantlib.indexes.ibor.Bibor;
import org.jquantlib.indexes.ibor.Bibor1M;
import org.jquantlib.indexes.ibor.Bibor1Y;
import org.jquantlib.indexes.ibor.Bibor2M;
import org.jquantlib.indexes.ibor.Bibor3M;
import org.jquantlib.indexes.ibor.Bibor6M;
import org.jquantlib.indexes.ibor.BiborSW;
import org.jquantlib.indexes.ibor.Bkbm;
import org.jquantlib.indexes.ibor.Bkbm1M;
import org.jquantlib.indexes.ibor.Bkbm2M;
import org.jquantlib.indexes.ibor.Bkbm3M;
import org.jquantlib.indexes.ibor.Bkbm4M;
import org.jquantlib.indexes.ibor.Bkbm5M;
import org.jquantlib.indexes.ibor.Bkbm6M;
import org.jquantlib.indexes.ibor.Corra;
import org.jquantlib.indexes.ibor.Destr;
import org.jquantlib.indexes.ibor.Kofr;
import org.jquantlib.indexes.ibor.Mosprime;
import org.jquantlib.indexes.ibor.Nzocr;
import org.jquantlib.indexes.ibor.Pribor;
import org.jquantlib.indexes.ibor.Robor;
import org.jquantlib.indexes.ibor.Saron;
import org.jquantlib.indexes.ibor.Shibor;
import org.jquantlib.indexes.ibor.Swestr;
import org.jquantlib.indexes.ibor.THBFIX;
import org.jquantlib.indexes.ibor.Tonar;
import org.jquantlib.indexes.ibor.Wibor;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Smoke tests for the 26 indexes ported in Phase 2 L2-D (currency-specific
 * IBOR and overnight indexes from C++ QuantLib v1.42.1 {@code ql/indexes/ibor}).
 * <p>
 * For each index we verify the canonical invariants extracted from the C++
 * header/body: family name, currency ISO code, fixing days, business-day
 * convention, end-of-month flag (where relevant) and day-counter name.
 *
 * @author JQuantLib migration team
 */
public class Phase2L2DIborOvernightIndexesTest {

    // ----- Overnight indexes (extend OvernightIndex, tenor 1 Day, Following, !EOM) -----

    @Test
    public void corraConventions() {
        final Corra idx = new Corra();
        assertNotNull(idx);
        assertEquals("CORRA", idx.familyName());
        assertEquals("CAD", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void destrConventions() {
        final Destr idx = new Destr();
        assertEquals("DESTR", idx.familyName());
        assertEquals("DKK", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals("Actual/360", idx.dayCounter().name());
    }

    @Test
    public void kofrConventions() {
        final Kofr idx = new Kofr();
        assertEquals("KOFR", idx.familyName());
        assertEquals("KRW", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void nzocrConventions() {
        final Nzocr idx = new Nzocr();
        assertEquals("Nzocr", idx.familyName());
        assertEquals("NZD", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void saronConventions() {
        final Saron idx = new Saron();
        assertEquals("SARON", idx.familyName());
        assertEquals("CHF", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals("Actual/360", idx.dayCounter().name());
    }

    @Test
    public void swestrConventions() {
        final Swestr idx = new Swestr();
        assertEquals("SWESTR", idx.familyName());
        assertEquals("SEK", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals("Actual/360", idx.dayCounter().name());
    }

    @Test
    public void tonarConventions() {
        final Tonar idx = new Tonar();
        assertEquals("Tonar", idx.familyName());
        assertEquals("JPY", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    // ----- BIBOR family (Thailand, 2 settlement days, Actual/365 Fixed) -----

    @Test
    public void biborConventions() {
        final Bibor idx = new Bibor(new Period(3, TimeUnit.Months));
        assertEquals("Bibor", idx.familyName());
        assertEquals("THB", idx.currency().code());
        assertEquals(2, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertTrue(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void biborSwHasWeekConvention() {
        final BiborSW idx = new BiborSW();
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals(TimeUnit.Weeks, idx.tenor().units());
        assertEquals(1, idx.tenor().length());
    }

    @Test
    public void biborTenorVariantsCarryParentSettings() {
        assertEquals(1, new Bibor1M().tenor().length());
        assertEquals(TimeUnit.Months, new Bibor1M().tenor().units());
        assertEquals(2, new Bibor2M().tenor().length());
        assertEquals(3, new Bibor3M().tenor().length());
        assertEquals(6, new Bibor6M().tenor().length());
        assertEquals(1, new Bibor1Y().tenor().length());
        assertEquals(TimeUnit.Years, new Bibor1Y().tenor().units());
        for (final Bibor b : new Bibor[] { new Bibor1M(), new Bibor2M(), new Bibor3M(), new Bibor6M(), new Bibor1Y() }) {
            assertEquals("Bibor", b.familyName());
            assertEquals(BusinessDayConvention.ModifiedFollowing, b.businessDayConvention());
            assertTrue(b.endOfMonth());
        }
    }

    // ----- BKBM family (NZ, 0 settlement days, ModifiedFollowing, EOM, Actual/365 Fixed) -----

    @Test
    public void bkbmConventions() {
        final Bkbm idx = new Bkbm(new Period(3, TimeUnit.Months));
        assertEquals("Bkbm", idx.familyName());
        assertEquals("NZD", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertTrue(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void bkbmTenorVariants() {
        assertEquals(1, new Bkbm1M().tenor().length());
        assertEquals(2, new Bkbm2M().tenor().length());
        assertEquals(3, new Bkbm3M().tenor().length());
        assertEquals(4, new Bkbm4M().tenor().length());
        assertEquals(5, new Bkbm5M().tenor().length());
        assertEquals(6, new Bkbm6M().tenor().length());
    }

    // ----- Other IBOR indexes -----

    @Test
    public void priborConventions() {
        final Pribor idx = new Pribor(new Period(3, TimeUnit.Months));
        assertEquals("PRIBOR", idx.familyName());
        assertEquals("CZK", idx.currency().code());
        assertEquals(2, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals("Actual/360", idx.dayCounter().name());
    }

    @Test
    public void priborDailyTenorHasZeroFixingDays() {
        final Pribor idx = new Pribor(new Period(1, TimeUnit.Days));
        assertEquals(0, idx.fixingDays());
    }

    @Test
    public void roborConventions() {
        final Robor idx = new Robor(new Period(6, TimeUnit.Months));
        assertEquals("ROBOR", idx.familyName());
        assertEquals("RON", idx.currency().code());
        assertEquals(2, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals("Actual/360", idx.dayCounter().name());
    }

    @Test
    public void mosprimeConventions() {
        final Mosprime idx = new Mosprime(new Period(3, TimeUnit.Months));
        assertEquals("MOSPRIME", idx.familyName());
        assertEquals("RUB", idx.currency().code());
        assertEquals(1, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        // ActualActual ISDA: name like "Actual/Actual (ISDA)"
        assertTrue(idx.dayCounter().name().contains("Actual/Actual"));
    }

    @Test
    public void wiborConventions() {
        final Wibor idx = new Wibor(new Period(3, TimeUnit.Months));
        assertEquals("WIBOR", idx.familyName());
        assertEquals("PLN", idx.currency().code());
        assertEquals(2, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void shiborConventionsMonths() {
        final Shibor idx = new Shibor(new Period(3, TimeUnit.Months));
        assertEquals("Shibor", idx.familyName());
        assertEquals("CNY", idx.currency().code());
        assertEquals(1, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals("Actual/360", idx.dayCounter().name());
    }

    @Test
    public void shiborConventionsDaysIsFollowing() {
        final Shibor idx = new Shibor(new Period(1, TimeUnit.Days));
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
    }

    @Test
    public void thbfixConventions() {
        final THBFIX idx = new THBFIX(new Period(3, TimeUnit.Months));
        assertEquals("THBFIX", idx.familyName());
        assertEquals("THB", idx.currency().code());
        assertEquals(2, idx.fixingDays());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.businessDayConvention());
        assertTrue(idx.endOfMonth());
        assertTrue(idx.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void euribor1WConventions() {
        final Euribor1W idx = new Euribor1W();
        assertEquals("Euribor", idx.familyName());
        assertEquals("EUR", idx.currency().code());
        assertEquals(2, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals(TimeUnit.Weeks, idx.tenor().units());
        assertEquals(1, idx.tenor().length());
    }

    @Test
    public void eurLiborOnConventions() {
        final EURLiborON idx = new EURLiborON();
        assertEquals("EURLibor", idx.familyName());
        assertEquals("EUR", idx.currency().code());
        assertEquals(0, idx.fixingDays());
        assertEquals(BusinessDayConvention.Following, idx.businessDayConvention());
        assertFalse(idx.endOfMonth());
        assertEquals(TimeUnit.Days, idx.tenor().units());
        assertEquals(1, idx.tenor().length());
        assertEquals("Actual/360", idx.dayCounter().name());
    }
}
