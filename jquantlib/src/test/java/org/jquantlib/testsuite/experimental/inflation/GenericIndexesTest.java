/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is released under the BSD License.

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.experimental.inflation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jquantlib.currencies.Europe.EURCurrency;
import org.jquantlib.experimental.inflation.GenericIndexes.GenericCPI;
import org.jquantlib.experimental.inflation.GenericIndexes.GenericRegion;
import org.jquantlib.experimental.inflation.GenericIndexes.YYGenericCPI;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.testsuite.util.ReferenceReader.Case;
import org.jquantlib.time.Frequency;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validated metadata test for {@link GenericIndexes} against
 * C++ v1.42.1 {@code QuantLib::GenericRegion}, {@code GenericCPI} and
 * {@code YYGenericCPI} reference values.
 *
 * <p>All checks are string-exact (TIGHT — metadata is deterministic).
 *
 * @author JQuantLib migration contributors (Phase 2s L0)
 */
public class GenericIndexesTest {

    private static final ReferenceReader READER =
            ReferenceReader.load("experimental/inflation/generic_indexes");

    // -------------------------------------------------------------------------
    // GenericRegion
    // -------------------------------------------------------------------------

    @Test
    public void genericRegion_name_matchesCpp() {
        final Case c = READER.getCase("generic_region_metadata");
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final GenericRegion region = new GenericRegion();

        assertEquals("GenericRegion name should match C++ reference",
                exp.getString("name"), region.name());
    }

    @Test
    public void genericRegion_code_matchesCpp() {
        final Case c = READER.getCase("generic_region_metadata");
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final GenericRegion region = new GenericRegion();

        assertEquals("GenericRegion code should match C++ reference",
                exp.getString("code"), region.code());
    }

    // -------------------------------------------------------------------------
    // GenericCPI
    // -------------------------------------------------------------------------

    @Test
    public void genericCPI_familyName_matchesCpp() {
        final Case c = READER.getCase("generic_cpi_metadata");
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final GenericCPI cpi = new GenericCPI(
                Frequency.Monthly, false,
                new Period(3, TimeUnit.Months),
                new EURCurrency());

        assertEquals("GenericCPI familyName should match C++ reference",
                exp.getString("familyName"), cpi.familyName());
    }

    @Test
    public void genericCPI_name_matchesCpp() {
        final Case c = READER.getCase("generic_cpi_metadata");
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final GenericCPI cpi = new GenericCPI(
                Frequency.Monthly, false,
                new Period(3, TimeUnit.Months),
                new EURCurrency());

        assertEquals("GenericCPI name should match C++ reference",
                exp.getString("name"), cpi.name());
    }

    // -------------------------------------------------------------------------
    // YYGenericCPI
    // -------------------------------------------------------------------------

    @Test
    public void yyGenericCPI_familyName_matchesCpp() {
        final Case c = READER.getCase("yy_generic_cpi_metadata");
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final YYGenericCPI yycpi = new YYGenericCPI(
                Frequency.Monthly, false,
                new Period(3, TimeUnit.Months),
                new EURCurrency());

        assertEquals("YYGenericCPI familyName should match C++ reference",
                exp.getString("familyName"), yycpi.familyName());
    }

    @Test
    public void yyGenericCPI_name_matchesCpp() {
        final Case c = READER.getCase("yy_generic_cpi_metadata");
        final JSONObject exp = (JSONObject) c.expectedRaw();

        final YYGenericCPI yycpi = new YYGenericCPI(
                Frequency.Monthly, false,
                new Period(3, TimeUnit.Months),
                new EURCurrency());

        assertEquals("YYGenericCPI name should match C++ reference",
                exp.getString("name"), yycpi.name());
    }

    // -------------------------------------------------------------------------
    // Structural smoke tests
    // -------------------------------------------------------------------------

    @Test
    public void genericRegion_isNotNull() {
        final GenericRegion region = new GenericRegion();
        assertTrue("GenericRegion name should be non-empty", !region.name().isEmpty());
        assertTrue("GenericRegion code should be non-empty", !region.code().isEmpty());
    }

    @Test
    public void genericCPI_withTermStructure_constructsWithoutError() {
        // Smoke test: construction with null handle should succeed.
        final GenericCPI cpi = new GenericCPI(
                Frequency.Monthly, false,
                new Period(3, TimeUnit.Months),
                new EURCurrency());
        assertEquals("GenericCPI family name check", "CPI", cpi.familyName());
    }

}
