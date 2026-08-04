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

package org.jquantlib.testsuite.currencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.testsuite.util.ReferenceReader;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Cross-validates every currency in the {@code currencies/missing_currencies} probe reference against C++.
 * <p>
 * The reference existed with 44 pinned currencies and <em>no consumer</em> — generated ground truth that nothing
 * checked. That is the mirror image of an orphan reference: not stale, just unread, and equally good at hiding a
 * wrong ISO code or fraction count. This test closes it, and does so by walking the reference rather than a
 * hand-written list, so a currency added to the probe is checked the moment it lands.
 *
 * @author Jose Moya
 */
public class CurrenciesReferenceTest {

    /** The packages a currency class may live in, mirroring C++'s per-continent headers. */
    private static final String[] PACKAGES = { "org.jquantlib.currencies.Africa",
            "org.jquantlib.currencies.America", "org.jquantlib.currencies.Asia",
            "org.jquantlib.currencies.Europe", "org.jquantlib.currencies.Crypto",
            "org.jquantlib.currencies.Oceania" };

    /**
     * Instantiates {@code <CODE>Currency} by reflection. Java splits the currencies across continent classes exactly
     * as C++ splits them across headers, and which continent a given code lives in is not something this test should
     * care about — only that the class exists somewhere and carries the right data.
     */
    private static Currency currencyFor(final String code) {
        for ( final String pkg : PACKAGES ) {
            try {
                final Class< ? > c = Class.forName(pkg + "$" + code + "Currency");
                final Constructor< ? > ctor = c.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (Currency) ctor.newInstance();
            } catch ( final ReflectiveOperationException notHere ) {
                // try the next continent
            }
        }
        return null;
    }

    @Test
    public void testEveryPinnedCurrencyMatchesCpp() {
        QL.info("Testing every currency in the reference against C++ v1.43...");

        final ReferenceReader ref = ReferenceReader.load("currencies/missing_currencies");
        final List< String > absent = new ArrayList<>();
        int checked = 0;

        for ( final String code : ref.caseNames() ) {
            final Currency ccy = currencyFor(code);
            if ( ccy == null ) {
                absent.add(code);
                continue;
            }

            final JSONObject e = (JSONObject) ref.getCase(code).expectedRaw();
            assertEquals(code + ": name", e.getString("name"), ccy.name());
            assertEquals(code + ": code", e.getString("code"), ccy.code());
            assertEquals(code + ": numeric code", e.getInt("numericCode"), ccy.numericCode());
            assertEquals(code + ": symbol", e.getString("symbol"), ccy.symbol());
            assertEquals(code + ": fraction symbol", e.getString("fractionSymbol"), ccy.fractionSymbol());
            assertEquals(code + ": fractions per unit", e.getInt("fractionsPerUnit"), ccy.fractionsPerUnit());
            ++checked;
        }

        if ( !absent.isEmpty() ) {
            fail("no Java class for pinned " + (absent.size() == 1 ? "currency" : "currencies") + ": " + absent);
        }
        assertTrue("expected the reference to carry currencies, found " + checked, checked >= 40);
    }
}
