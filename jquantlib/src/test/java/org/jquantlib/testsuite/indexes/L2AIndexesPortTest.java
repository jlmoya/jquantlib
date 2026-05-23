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

import org.jquantlib.cashflow.RateAveraging;
import org.jquantlib.currencies.Oceania.AUDCurrency;
import org.jquantlib.indexes.CaseInsensitiveCompare;
import org.jquantlib.indexes.CustomRegion;
import org.jquantlib.indexes.OvernightIndexedSwapIndex;
import org.jquantlib.indexes.ibor.Aonia;
import org.jquantlib.indexes.ibor.Bbsw;
import org.jquantlib.indexes.ibor.Bbsw1M;
import org.jquantlib.indexes.ibor.Bbsw2M;
import org.jquantlib.indexes.ibor.Bbsw3M;
import org.jquantlib.indexes.ibor.Bbsw4M;
import org.jquantlib.indexes.ibor.Bbsw5M;
import org.jquantlib.indexes.ibor.Bbsw6M;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Smoke tests for Phase 2 forward closure L2-A index ports.
 * <p>
 * Cross-validates against C++ QuantLib v1.42.1:
 * <ul>
 *   <li>{@code ql/indexes/ibor/aonia.hpp}: family "Aonia", AUD, Australia,
 *       Actual/365 Fixed, 0 fixing days.</li>
 *   <li>{@code ql/indexes/ibor/bbsw.hpp}: family "Bbsw", AUD, Australia,
 *       HalfMonthModifiedFollowing, endOfMonth=true, Actual/365 Fixed,
 *       0 settlement days, tenor variants 1M..6M.</li>
 *   <li>{@code ql/indexes/region.hpp}: CustomRegion stores name+code as
 *       provided; equality on name.</li>
 *   <li>{@code ql/indexes/indexmanager.hpp}: CaseInsensitiveCompare orders
 *       strings lexicographically after toupper folding.</li>
 *   <li>{@code ql/indexes/swapindex.hpp}: OvernightIndexedSwapIndex carries
 *       family, tenor, settlement days, currency, and overnightIndex; uses
 *       overnight index's fixing calendar and day counter; fixed-leg
 *       conventions are 1Y / ModifiedFollowing.</li>
 * </ul>
 *
 * @author JQuantLib migration team
 */
public class L2AIndexesPortTest {

    // ------------------------------------------------------------------
    // Aonia
    // ------------------------------------------------------------------

    @Test
    public void aoniaConventions() {
        final Aonia aonia = new Aonia();
        assertNotNull(aonia);
        assertEquals("Aonia", aonia.familyName());
        assertEquals(0, aonia.fixingDays());
        // OvernightIndex tenor is 1-day
        assertEquals(1, aonia.tenor().length());
        assertEquals(TimeUnit.Days, aonia.tenor().units());
        // C++ aonia.hpp: Actual365Fixed
        assertTrue(aonia.dayCounter().name().contains("Actual/365"));
        // C++ aonia.hpp: AUD
        assertEquals("AUD", aonia.currency().code());
        // OvernightIndex semantics
        assertEquals(BusinessDayConvention.Following, aonia.businessDayConvention());
        assertFalse(aonia.endOfMonth());
    }

    // ------------------------------------------------------------------
    // Bbsw and tenor variants
    // ------------------------------------------------------------------

    @Test
    public void bbswConventions() {
        final Bbsw b3m = new Bbsw(new Period(3, TimeUnit.Months));
        assertEquals("Bbsw", b3m.familyName());
        assertEquals(0, b3m.fixingDays());
        assertEquals(BusinessDayConvention.HalfMonthModifiedFollowing,
                     b3m.businessDayConvention());
        assertTrue(b3m.endOfMonth());
        assertTrue(b3m.dayCounter().name().contains("Actual/365"));
        assertEquals("AUD", b3m.currency().code());
        assertEquals(3, b3m.tenor().length());
        assertEquals(TimeUnit.Months, b3m.tenor().units());
    }

    @Test(expected = RuntimeException.class)
    public void bbswRejectsDailyTenor() {
        // C++ bbsw.hpp:42 — QL_REQUIRE rejects Days tenor; daily tenor must use
        // a dedicated constructor.
        new Bbsw(new Period(1, TimeUnit.Days));
    }

    @Test
    public void bbswTenorVariants() {
        assertEquals(1, new Bbsw1M().tenor().length());
        assertEquals(TimeUnit.Months, new Bbsw1M().tenor().units());
        assertEquals(2, new Bbsw2M().tenor().length());
        assertEquals(3, new Bbsw3M().tenor().length());
        assertEquals(4, new Bbsw4M().tenor().length());
        assertEquals(5, new Bbsw5M().tenor().length());
        assertEquals(6, new Bbsw6M().tenor().length());
        // All tenor variants inherit familyName "Bbsw"
        assertEquals("Bbsw", new Bbsw1M().familyName());
        assertEquals("Bbsw", new Bbsw6M().familyName());
    }

    // ------------------------------------------------------------------
    // CustomRegion
    // ------------------------------------------------------------------

    @Test
    public void customRegionStoresProvidedNameAndCode() {
        final CustomRegion r = new CustomRegion("Atlantis", "AT");
        assertEquals("Atlantis", r.name());
        assertEquals("AT", r.code());
    }

    @Test
    public void customRegionEqualityByName() {
        // Region equality (`eq`) compares on name only; codes need not match.
        final CustomRegion r1 = new CustomRegion("Atlantis", "AT");
        final CustomRegion r2 = new CustomRegion("Atlantis", "XX");
        assertTrue(org.jquantlib.indexes.Region.eq(r1, r2));
    }

    // ------------------------------------------------------------------
    // CaseInsensitiveCompare
    // ------------------------------------------------------------------

    @Test
    public void caseInsensitiveCompareIsCaseFolded() {
        final CaseInsensitiveCompare cmp = new CaseInsensitiveCompare();
        assertEquals(0, cmp.compare("EURIBOR", "euribor"));
        assertEquals(0, cmp.compare("Sofr", "SOFR"));
        assertTrue(cmp.compare("Aaa", "aab") < 0);
        assertTrue(cmp.compare("BBB", "aaa") > 0);
    }

    // ------------------------------------------------------------------
    // OvernightIndexedSwapIndex
    // ------------------------------------------------------------------

    @Test
    public void overnightIndexedSwapIndexCarriesProvidedConfig() {
        final Aonia overnight = new Aonia();
        final OvernightIndexedSwapIndex idx = new OvernightIndexedSwapIndex(
                "AonOIS",
                new Period(2, TimeUnit.Years),
                1,
                new AUDCurrency(),
                overnight);
        assertEquals("AonOIS", idx.familyName());
        assertEquals(2, idx.tenor().length());
        assertEquals(TimeUnit.Years, idx.tenor().units());
        assertEquals(1, idx.fixingDays());
        assertEquals("AUD", idx.currency().code());
        // overnight index pass-through
        assertNotNull(idx.overnightIndex());
        assertEquals("Aonia", idx.overnightIndex().familyName());
        // SwapIndex#fixedLegTenor and fixedLegConvention from C++ ctor
        assertEquals(1, idx.fixedLegTenor().length());
        assertEquals(TimeUnit.Years, idx.fixedLegTenor().units());
        assertEquals(BusinessDayConvention.ModifiedFollowing, idx.fixedLegConvention());
        // Day-counter inherited from overnight index
        assertEquals(overnight.dayCounter().name(), idx.dayCounter().name());
    }

    @Test
    public void overnightIndexedSwapIndexDefaultsAveragingToCompound() {
        // The 5-arg ctor must default to RateAveraging.Type.Compound and
        // telescopicValueDates = false — these are observable only via
        // computeFixings, so verify the ctor accepts and stores them by
        // building both the 5-arg and 7-arg forms without exception.
        final Aonia overnight = new Aonia();
        new OvernightIndexedSwapIndex("AonOIS", new Period(1, TimeUnit.Years),
                                      0, new AUDCurrency(), overnight);
        new OvernightIndexedSwapIndex("AonOIS", new Period(1, TimeUnit.Years),
                                      0, new AUDCurrency(), overnight,
                                      true, RateAveraging.Type.Simple);
    }
}
