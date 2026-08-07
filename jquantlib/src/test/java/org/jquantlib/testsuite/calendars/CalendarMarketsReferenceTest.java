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
import org.jquantlib.time.calendars.Australia;
import org.jquantlib.time.calendars.Germany;
import org.jquantlib.time.calendars.Poland;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates the per-market variants of the Australia, Germany and Poland
 * calendars against the {@code time/calendars/all} probe reference (holidays
 * for 2020-2030, weekends excluded).
 * <p>
 * C++ realises each market as a private nested {@code Calendar::Impl}
 * ({@code Australia::AsxImpl}, {@code Germany::EuwaxImpl},
 * {@code Poland::WseImpl}, …) selected by a {@code Market} enumerator, and
 * JQuantLib mirrors that shape. A market that exists in name but carries the
 * wrong holiday rules is worse than a missing one, so every enumerator gets its
 * own case here — the market-name-to-rules wiring is the thing under test.
 * <p>
 * Tolerance tier: exact — holiday lists are compared as sets of dates.
 *
 * @author Jose Moya
 */
public class CalendarMarketsReferenceTest {

    private static final int FROM_YEAR = 2020;
    private static final int TO_YEAR = 2030;

    public CalendarMarketsReferenceTest() {
        QL.info("::::: " + this.getClass().getSimpleName() + " :::::");
    }

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
        assertEquals(key + ": missing=" + missing + " extra=" + extra, expected, actual);
    }

    /** C++ {@code Australia::SettlementImpl} (australia.hpp:54) — the default market. */
    @Test
    public void testAustraliaSettlement() {
        QL.info("Testing the Australia settlement calendar against C++ v1.43...");
        checkAgainstCpp("australia", new Australia());
    }

    /** C++ {@code Germany::FrankfurtStockExchangeImpl} — the default market. */
    @Test
    public void testGermanyDefaultIsFrankfurt() {
        QL.info("Testing the Germany default (Frankfurt) calendar against C++ v1.43...");
        checkAgainstCpp("germany", new Germany());
        checkAgainstCpp("germany", new Germany(Germany.Market.FrankfurtStockExchange));
    }

    /** C++ {@code Germany::SettlementImpl} (germany.hpp:113). */
    @Test
    public void testGermanySettlement() {
        QL.info("Testing the Germany settlement calendar against C++ v1.43...");
        checkAgainstCpp("germany_settlement", new Germany(Germany.Market.Settlement));
    }

    /** C++ {@code Germany::XetraImpl} (germany.hpp:123). */
    @Test
    public void testGermanyXetra() {
        QL.info("Testing the Germany Xetra calendar against C++ v1.43...");
        checkAgainstCpp("germany_xetra", new Germany(Germany.Market.Xetra));
    }

    /** C++ {@code Germany::EurexImpl} (germany.hpp:128) — the only German market closing on 31 December. */
    @Test
    public void testGermanyEurex() {
        QL.info("Testing the Germany Eurex calendar against C++ v1.43...");
        checkAgainstCpp("germany_eurex", new Germany(Germany.Market.Eurex));
    }

    /** C++ {@code Poland::SettlementImpl} (poland.hpp:53) — the default market. */
    @Test
    public void testPolandSettlement() {
        QL.info("Testing the Poland settlement calendar against C++ v1.43...");
        checkAgainstCpp("poland", new Poland());
    }
}
