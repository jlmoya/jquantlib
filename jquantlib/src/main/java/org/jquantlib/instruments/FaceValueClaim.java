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
 Copyright (C) 2008 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.instruments;

import org.jquantlib.time.Date;

/**
 * Claim on a notional.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::FaceValueClaim}
 * ({@code ql/instruments/claim.cpp:24-28}). Computes the standard
 * {@code notional * (1 - recoveryRate)} CDS payoff at default.
 *
 * @category instruments
 */
public class FaceValueClaim extends Claim {

    @Override
    public double amount(final Date defaultDate, final double notional, final double recoveryRate) {
        return notional * (1.0 - recoveryRate);
    }
}
