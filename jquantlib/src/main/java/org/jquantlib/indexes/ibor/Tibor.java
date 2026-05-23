/*
 Copyright (C) 2011 Tim Blackler

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
/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004, 2005, 2006 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.indexes.ibor;

import org.jquantlib.currencies.Asia.JPYCurrency;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.indexes.IborIndex;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Period;
import org.jquantlib.time.calendars.Japan;

/**
 * Tokyo Interbank Offered Rate This is the rate fixed in Tokio by JBA. Use JPYLibor if you're interested in the London
 * fixing by BBA.
 *
 */
public class Tibor extends IborIndex {

    public Tibor(final Period tenor) {
        this(tenor, new Handle< YieldTermStructure >());
    }

    public Tibor(final Period tenor, final Handle< YieldTermStructure > h) {
        // align(indexes.ibor): match C++ v1.42.1 tibor.hpp settlementDays=2
        // (was 0). Tokyo Interbank Offered Rate (TIBOR) is fixed by JBA with
        // the standard 2-day Tokyo value-date convention; Java port's 0
        // caused fixing/value/maturity-date misalignment of the same shape
        // as the JPYLibor bug fixed in Phase Bug-Fix-4.
        super("Tibor", tenor, 2, new JPYCurrency(), new Japan(), BusinessDayConvention.ModifiedFollowing, false,
                new Actual365Fixed(), h);
    }

}
