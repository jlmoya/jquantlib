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

package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.Asia.ILSCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.OvernightIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.calendars.Israel;

/**
 * SHIR — Shekel Overnight Interest Rate, published by the Bank of Israel,
 * replacing the Telbor rate in interest-rate derivative transactions. SHIR is
 * the overnight rate for the day (same-day fixing).
 * <p>
 * Faithful port of {@code ql/indexes/ibor/shir.{hpp,cpp}} from QuantLib
 * v1.43 @ {@code 6b57206e04598f092efee66e3b367efc84771995}. New in v1.43.
 *
 * @see <a href="https://www.boi.org.il/en/economic-roles/financial-markets/shir/">Bank of Israel — SHIR</a>
 * @author Jose Moya
 */
public class Shir extends OvernightIndex {

    public Shir(final Handle<YieldTermStructure> h) {
        super("Shir", 0, new ILSCurrency(), new Israel(Israel.Market.SHIR), new Actual365Fixed(), h);
    }

    public Shir() {
        this(new Handle<YieldTermStructure>());
    }
}
