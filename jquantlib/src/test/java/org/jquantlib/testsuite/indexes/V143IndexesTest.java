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

package org.jquantlib.testsuite.indexes;

import static org.junit.Assert.assertEquals;

import org.jquantlib.QL;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.indexes.ibor.Nibor;
import org.jquantlib.indexes.ibor.Shir;
import org.jquantlib.indexes.ibor.Zaronia;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.jquantlib.time.Date;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates the three interest-rate indexes introduced in C++ QuantLib
 * v1.43 — NOK-NIBOR, SHIR and ZARONIA — against the
 * {@code indexes/v143_indexes} probe reference.
 * <p>
 * Each index is a thin constructor wrapper, so the meaningful content is the
 * wiring: name, fixing days, currency, fixing calendar, day counter,
 * business-day convention and end-of-month. Those are easy to get subtly wrong
 * and stay invisible until a fixing date or accrual is off — hence the
 * value/maturity roll is pinned too.
 *
 * @author Jose Moya
 */
public class V143IndexesTest {

    private static JSONObject expected(final String caseName) {
        return (JSONObject) ReferenceReader.load("indexes/v143_indexes")
                .getCase(caseName).expectedRaw();
    }

    private static void checkWiring(final String caseName, final IborIndex idx) {
        final JSONObject e = expected(caseName);
        assertEquals(caseName + ": name", e.getString("name"), idx.name());
        assertEquals(caseName + ": fixingDays", e.getInt("fixingDays"), idx.fixingDays());
        assertEquals(caseName + ": currency", e.getString("currencyCode"), idx.currency().code());
        assertEquals(caseName + ": fixingCalendar",
                e.getString("fixingCalendar"), idx.fixingCalendar().name());
        assertEquals(caseName + ": dayCounter", e.getString("dayCounter"), idx.dayCounter().name());
        assertEquals(caseName + ": businessDayConvention",
                e.getInt("businessDayConvention"), idx.businessDayConvention().ordinal());
        assertEquals(caseName + ": endOfMonth", e.getBoolean("endOfMonth"), idx.endOfMonth());
    }

    private static void checkDates(final String caseName, final IborIndex idx) {
        final JSONObject e = expected(caseName);
        final Date fixing = new Date((int) e.getLong("fixingSerial"));
        final Date value = idx.valueDate(fixing);
        assertEquals(caseName + ": valueDate",
                e.getLong("valueDateSerial"), value.serialNumber());
        assertEquals(caseName + ": maturityDate",
                e.getLong("maturityDateSerial"), idx.maturityDate(value).serialNumber());
    }

    @Test
    public void testNibor3M() {
        QL.info("Testing NOK-NIBOR 3M against C++ v1.43...");
        final Nibor idx = new Nibor(new Period(3, TimeUnit.Months));
        checkWiring("nibor_3m", idx);
        checkDates("nibor_3m_dates", idx);
    }

    @Test
    public void testNibor6M() {
        QL.info("Testing NOK-NIBOR 6M against C++ v1.43...");
        checkWiring("nibor_6m", new Nibor(new Period(6, TimeUnit.Months)));
    }

    @Test
    public void testShir() {
        QL.info("Testing SHIR overnight index against C++ v1.43...");
        final Shir idx = new Shir();
        checkWiring("shir", idx);
        checkDates("shir_dates", idx);
    }

    @Test
    public void testZaronia() {
        QL.info("Testing ZARONIA overnight index against C++ v1.43...");
        final Zaronia idx = new Zaronia();
        checkWiring("zaronia", idx);
        checkDates("zaronia_dates", idx);
    }
}
