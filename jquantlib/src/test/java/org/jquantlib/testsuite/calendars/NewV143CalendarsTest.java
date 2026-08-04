/*
 Copyright (C) 2026 Jose Moya

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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.testsuite.calendars;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jquantlib.QL;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Calendar;
import org.jquantlib.time.Date;
import org.jquantlib.time.Month;
import org.jquantlib.time.calendars.Croatia;
import org.jquantlib.time.calendars.Israel;
import org.jquantlib.time.calendars.Malta;
import org.jquantlib.time.calendars.Montenegro;
import org.jquantlib.time.calendars.NorthMacedonia;
import org.jquantlib.time.calendars.Serbia;
import org.jquantlib.time.calendars.Slovenia;
import org.jquantlib.time.calendars.Uzbekistan;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates the calendars introduced by C++ QuantLib v1.43 against the
 * {@code time/calendars/all} probe reference (holidays for 2020-2030, weekends
 * excluded).
 * <p>
 * The probe is new: JQuantLib previously had no calendar reference at all, so
 * its calendar tables were never cross-validated against C++. That is exactly
 * how the v1.43 India / South Korea / Israel changes slipped in silently while
 * the suite stayed green.
 *
 * @author Jose Moya
 */
public class NewV143CalendarsTest {

    private static final int FROM_YEAR = 2020;
    private static final int TO_YEAR = 2030;

    private static void checkAgainstCpp(final String key, final Calendar cal) {
        final ReferenceReader ref = ReferenceReader.load("time/calendars/all");
        final JSONObject expectedObj = (JSONObject) ref.getCase(key).expectedRaw();

        assertEquals("calendar name mismatch for " + key,
                expectedObj.getString("name"), cal.name());

        final Set<Date> expected = new LinkedHashSet<>();
        final JSONArray arr = expectedObj.getJSONArray("holidays");
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject h = arr.getJSONObject(i);
            expected.add(new Date(h.getInt("d"), Month.valueOf(h.getInt("m")), h.getInt("y")));
        }

        final Set<Date> actual = new LinkedHashSet<>(
                Calendar.holidayList(cal,
                        new Date(1, Month.January, FROM_YEAR),
                        new Date(31, Month.December, TO_YEAR),
                        false));

        final Set<Date> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        final Set<Date> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        assertEquals(key + ": missing=" + missing + " extra=" + extra,
                expected, actual);
    }

    @Test
    public void testCroatia() {
        QL.info("Testing Croatia (ZSE) calendar against C++ v1.43...");
        checkAgainstCpp("croatia", new Croatia());
    }

    @Test
    public void testMalta() {
        QL.info("Testing Malta (MSE) calendar against C++ v1.43...");
        // NOTE: C++ Malta::MseImpl overrides isWeekend to Friday+Saturday even
        // though it derives from WesternImpl. Almost certainly an upstream
        // quirk, but C++ is the ground truth and the port mirrors it.
        checkAgainstCpp("malta", new Malta());
    }

    @Test
    public void testMontenegro() {
        QL.info("Testing Montenegro (MNSE) calendar against C++ v1.43...");
        checkAgainstCpp("montenegro", new Montenegro());
    }

    @Test
    public void testSerbia() {
        QL.info("Testing Serbia (BSE) calendar against C++ v1.43...");
        checkAgainstCpp("serbia", new Serbia());
    }

    @Test
    public void testSlovenia() {
        QL.info("Testing Slovenia (LSE) calendar against C++ v1.43...");
        checkAgainstCpp("slovenia", new Slovenia());
    }

    @Test
    public void testNorthMacedonia() {
        QL.info("Testing North Macedonia (MSE) calendar against C++ v1.43...");
        // NOTE: C++ derives MseImpl from Calendar::OrthodoxImpl, so Easter
        // Monday follows the Orthodox (Julian) computation — e.g. 3-May-2021
        // rather than the Western 5-Apr-2021.
        checkAgainstCpp("north_macedonia", new NorthMacedonia());
    }

    @Test
    public void testUzbekistan() {
        QL.info("Testing Uzbekistan (UZSE) calendar against C++ v1.43...");
        checkAgainstCpp("uzbekistan", new Uzbekistan());
    }
    /**
     * The Telbor fixing calendar, new in C++ QuantLib v1.43. It shares Israel's Jewish-holiday tables with TASE and
     * SHIR but observes a different subset — Shushan Purim, Passover VII, Simchat Torah and a handful of one-off
     * election and abroad-holiday closings — so pinning all three markets together is what proves the right table is
     * wired to the right market.
     */
    @Test
    public void testIsraelMarkets() {
        QL.info("Testing the Israel calendar markets, including v1.43's Telbor, against C++...");
        checkAgainstCpp("israel", new Israel());
        checkAgainstCpp("israel_tase", new Israel(Israel.Market.TASE));
        checkAgainstCpp("israel_shir", new Israel(Israel.Market.SHIR));
        checkAgainstCpp("israel_telbor", new Israel(Israel.Market.Telbor));
    }
}
