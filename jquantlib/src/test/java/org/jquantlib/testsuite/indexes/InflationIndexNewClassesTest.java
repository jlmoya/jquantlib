/*
 Copyright (C) 2026 JQuantLib migration contributors.

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
 */
package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.jquantlib.indexes.inflation.AUCPI;
import org.jquantlib.indexes.inflation.EUHICPXT;
import org.jquantlib.indexes.inflation.FRHICP;
import org.jquantlib.indexes.inflation.UKHICP;
import org.jquantlib.indexes.inflation.USCPI;
import org.jquantlib.indexes.inflation.YYAUCPI;
import org.jquantlib.indexes.inflation.YYEUHICPXT;
import org.jquantlib.indexes.inflation.YYFRHICP;
import org.jquantlib.indexes.inflation.YYUKHICP;
import org.jquantlib.indexes.inflation.YYUSCPI;
import org.jquantlib.indexes.inflation.YYZACPI;
import org.jquantlib.indexes.inflation.ZACPI;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.junit.Test;

/**
 * Smoke tests for the 12 inflation index classes ported in Phase 2v L0 A.1.
 *
 * <p>Each test verifies the constructor wires up the correct {@code name},
 * {@code familyName}, {@code frequency} and {@code availabilityLag}
 * matching the C++ source-of-truth headers in
 * {@code ql/indexes/inflation/{aucpi,ukhicp,uscpi,frhicp,zacpi,euhicp}.hpp}.
 *
 * <p>The {@code name()} for an inflation index is
 * {@code <region.name()> <familyName>}, so {@code AUCPI} reports
 * {@code "Australia CPI"}, {@code USCPI} reports {@code "USA CPI"},
 * {@code ZACPI} reports {@code "South Africa CPI"}, etc.
 */
public class InflationIndexNewClassesTest {

    private static final Period ONE_MONTH = new Period(1, TimeUnit.Months);
    private static final Period TWO_MONTHS = new Period(2, TimeUnit.Months);

    // -----------------------------------------------------------------
    // AUCPI / YYAUCPI — Australia, frequency configurable, 2-month lag
    // -----------------------------------------------------------------
    @Test
    public void aucpi_quarterlyHasCanonicalNameAndLag() {
        final AUCPI idx = new AUCPI(Frequency.Quarterly, false, false);
        assertNotNull(idx);
        assertEquals("Australia CPI", idx.name());
        assertEquals("CPI", idx.familyName());
        assertEquals(Frequency.Quarterly, idx.frequency());
        assertEquals(TWO_MONTHS, idx.availabilityLag());
    }

    @Test
    public void yyaucpi_annualHasCanonicalNameAndLag() {
        final YYAUCPI idx = new YYAUCPI(Frequency.Annual, false, false);
        assertNotNull(idx);
        assertEquals("Australia YY_CPI", idx.name());
        assertEquals("YY_CPI", idx.familyName());
        assertEquals(Frequency.Annual, idx.frequency());
        assertEquals(TWO_MONTHS, idx.availabilityLag());
    }

    // -----------------------------------------------------------------
    // UKHICP / YYUKHICP — UK, Monthly, 1-month lag
    // -----------------------------------------------------------------
    @Test
    public void ukhicp_hasCanonicalNameAndLag() {
        final UKHICP idx = new UKHICP(false);
        assertNotNull(idx);
        assertEquals("UK HICP", idx.name());
        assertEquals("HICP", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    @Test
    public void yyukhicp_hasCanonicalNameAndLag() {
        final YYUKHICP idx = new YYUKHICP(false);
        assertNotNull(idx);
        assertEquals("UK YY_HICP", idx.name());
        assertEquals("YY_HICP", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    // -----------------------------------------------------------------
    // USCPI / YYUSCPI — USA, Monthly, 1-month lag
    // -----------------------------------------------------------------
    @Test
    public void uscpi_hasCanonicalNameAndLag() {
        final USCPI idx = new USCPI(false);
        assertNotNull(idx);
        assertEquals("USA CPI", idx.name());
        assertEquals("CPI", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    @Test
    public void yyuscpi_hasCanonicalNameAndLag() {
        final YYUSCPI idx = new YYUSCPI(false);
        assertNotNull(idx);
        assertEquals("USA YY_CPI", idx.name());
        assertEquals("YY_CPI", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    // -----------------------------------------------------------------
    // FRHICP / YYFRHICP — France, Monthly, 1-month lag
    // -----------------------------------------------------------------
    @Test
    public void frhicp_hasCanonicalNameAndLag() {
        final FRHICP idx = new FRHICP(false);
        assertNotNull(idx);
        assertEquals("France HICP", idx.name());
        assertEquals("HICP", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    @Test
    public void yyfrhicp_hasCanonicalNameAndLag() {
        final YYFRHICP idx = new YYFRHICP(false);
        assertNotNull(idx);
        assertEquals("France YY_HICP", idx.name());
        assertEquals("YY_HICP", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    // -----------------------------------------------------------------
    // ZACPI / YYZACPI — South Africa, Monthly, 1-month lag
    // -----------------------------------------------------------------
    @Test
    public void zacpi_hasCanonicalNameAndLag() {
        final ZACPI idx = new ZACPI(false);
        assertNotNull(idx);
        assertEquals("South Africa CPI", idx.name());
        assertEquals("CPI", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    @Test
    public void yyzacpi_hasCanonicalNameAndLag() {
        final YYZACPI idx = new YYZACPI(false);
        assertNotNull(idx);
        assertEquals("South Africa YY_CPI", idx.name());
        assertEquals("YY_CPI", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    // -----------------------------------------------------------------
    // EUHICPXT / YYEUHICPXT — EU, Monthly, 1-month lag
    // -----------------------------------------------------------------
    @Test
    public void euhicpxt_hasCanonicalNameAndLag() {
        final EUHICPXT idx = new EUHICPXT(false);
        assertNotNull(idx);
        assertEquals("EU HICPXT", idx.name());
        assertEquals("HICPXT", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }

    @Test
    public void yyeuhicpxt_hasCanonicalNameAndLag() {
        final YYEUHICPXT idx = new YYEUHICPXT(false);
        assertNotNull(idx);
        assertEquals("EU YY_HICPXT", idx.name());
        assertEquals("YY_HICPXT", idx.familyName());
        assertEquals(Frequency.Monthly, idx.frequency());
        assertEquals(ONE_MONTH, idx.availabilityLag());
    }
}
