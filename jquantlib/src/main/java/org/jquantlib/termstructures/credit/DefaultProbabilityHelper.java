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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
*/
/*
 Copyright (C) 2008, 2009 Jose Aparicio
 Copyright (C) 2008 Chris Kenyon
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.termstructures.credit;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BootstrapHelper;
import org.jquantlib.termstructures.DefaultProbabilityTermStructure;

/**
 * Bootstrap helper for default-probability term structures — Java port of the C++ v1.42.1 typedef
 * {@code typedef BootstrapHelper<DefaultProbabilityTermStructure> DefaultProbabilityHelper}
 * ({@code ql/termstructures/credit/defaultprobabilityhelpers.hpp:42-43}).
 *
 * <p>Java subclasses (CDS-spread and CDS-upfront helpers) are deferred to
 * Phase 3b because they require {@code CreditDefaultSwap} and its pricing engines, which are not yet ported. This
 * abstract base nonetheless lets {@link PiecewiseDefaultCurve} compile against a type-safe helper handle.
 */
public abstract class DefaultProbabilityHelper extends BootstrapHelper< DefaultProbabilityTermStructure > {

    public DefaultProbabilityHelper(final Handle< Quote > quote) {
        super(quote);
    }

    public DefaultProbabilityHelper(final double quote) {
        super(quote);
    }
}
