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

import org.jquantlib.currencies.Europe.NOKCurrency;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.calendars.Norway;

/**
 * NOK-NIBOR — the Norwegian Interbank Offered Rate published via Oslo Boers.
 * <p>
 * Faithful port of {@code ql/indexes/ibor/nibor.hpp} from QuantLib v1.43 @
 * {@code 6b57206e04598f092efee66e3b367efc84771995}. New in v1.43.
 *
 * <p><b>Note:</b> upstream carries a {@code \warning Check roll convention and
 * EOM.} caveat on this index; the port reproduces its settings verbatim
 * (2 fixing days, ModifiedFollowing, endOfMonth = false, Actual/360).
 *
 * @author Jose Moya
 */
public class Nibor extends IborIndex {

    public Nibor(final Period tenor, final Handle<YieldTermStructure> h) {
        super("NOK-NIBOR", tenor, 2, new NOKCurrency(), new Norway(),
                BusinessDayConvention.ModifiedFollowing, false, new Actual360(), h);
    }

    public Nibor(final Period tenor) {
        this(tenor, new Handle<YieldTermStructure>());
    }
}
