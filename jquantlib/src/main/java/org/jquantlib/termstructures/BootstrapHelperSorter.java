/*
Copyright (C) 2009 John Martin

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
 Copyright (C) 2005, 2006, 2007, 2008 StatPro Italia srl
 Copyright (C) 2007 Ferdinando Ametrano

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

package org.jquantlib.termstructures;

import java.io.Serializable;
import java.util.Comparator;

public class BootstrapHelperSorter< Helper extends BootstrapHelper > implements Comparator< Helper >, Serializable {

    /**
     * Orders helpers by pillar date.
     * <p>
     * Mirrors C++ v1.43 {@code detail::BootstrapHelperSorter}
     * ({@code ql/termstructures/bootstraphelper.hpp:243-254}), which compares {@code pillarDate()}. For helpers that
     * do not set a distinct pillar, {@code pillarDate()} falls back to {@code latestDate()}, so this is unchanged
     * behaviour for them.
     */
    @Override
    public int compare(final Helper h1, final Helper h2) {
        if ( h1.pillarDate().lt(h2.pillarDate()) )
            return -1;
        if ( h1.pillarDate().equals(h2.pillarDate()) )
            return 0;
        return 1;
    }
}
