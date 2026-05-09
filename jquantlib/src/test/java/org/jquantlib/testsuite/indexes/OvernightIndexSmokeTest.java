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

import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.indexes.ibor.Eonia;
import org.jquantlib.indexes.ibor.FedFunds;
import org.jquantlib.indexes.ibor.Sofr;
import org.jquantlib.indexes.ibor.Sonia;
import org.jquantlib.time.BusinessDayConvention;
import org.junit.Test;

/**
 * Smoke tests for {@link OvernightIndex} and the four canonical concrete
 * subclasses: {@link Eonia}, {@link Sonia}, {@link Sofr}, {@link FedFunds}.
 * <p>
 * Confirms ground-truth invariants from C++ QuantLib v1.42.1
 * {@code ql/indexes/iborindex.hpp:88} {@code OvernightIndex}:
 * <ul>
 *   <li>tenor is 1-business-day,</li>
 *   <li>convention is {@link BusinessDayConvention#Following},</li>
 *   <li>endOfMonth is {@code false},</li>
 *   <li>fixingDays() defaults to 0 for the four canonical OIS indexes.</li>
 * </ul>
 *
 * @author JQuantLib migration team
 */
public class OvernightIndexSmokeTest {

    @Test
    public void eoniaConventions() {
        final Eonia eonia = new Eonia();
        assertNotNull(eonia);
        assertEquals("Eonia", eonia.familyName());
        assertEquals(0, eonia.fixingDays());
        assertEquals(BusinessDayConvention.Following, eonia.businessDayConvention());
        assertFalse(eonia.endOfMonth());
        assertEquals("Actual/360", eonia.dayCounter().name());
        // tenor: 1 day
        assertEquals(1, eonia.tenor().length());
    }

    @Test
    public void soniaConventions() {
        final Sonia sonia = new Sonia();
        assertNotNull(sonia);
        assertEquals("Sonia", sonia.familyName());
        assertEquals(0, sonia.fixingDays());
        assertEquals(BusinessDayConvention.Following, sonia.businessDayConvention());
        assertFalse(sonia.endOfMonth());
        // Actual/365 (Fixed) per C++ sonia.cpp
        assertTrue(sonia.dayCounter().name().contains("Actual/365"));
    }

    @Test
    public void sofrConventions() {
        final Sofr sofr = new Sofr();
        assertNotNull(sofr);
        assertEquals("SOFR", sofr.familyName());
        assertEquals(0, sofr.fixingDays());
        assertEquals(BusinessDayConvention.Following, sofr.businessDayConvention());
        assertFalse(sofr.endOfMonth());
        assertEquals("Actual/360", sofr.dayCounter().name());
    }

    @Test
    public void fedFundsConventions() {
        final FedFunds fedFunds = new FedFunds();
        assertNotNull(fedFunds);
        assertEquals("FedFunds", fedFunds.familyName());
        assertEquals(0, fedFunds.fixingDays());
        assertEquals(BusinessDayConvention.Following, fedFunds.businessDayConvention());
        assertFalse(fedFunds.endOfMonth());
        assertEquals("Actual/360", fedFunds.dayCounter().name());
    }

    @Test
    public void overnightIndexCloneReturnsOvernightIndex() {
        final Eonia eonia = new Eonia();
        // clone() with empty handle; result should still report Eonia conventions
        assertNotNull(eonia.clone(new org.jquantlib.quotes.Handle<org.jquantlib.termstructures.YieldTermStructure>()));
    }
}
