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
 * Claim on the notional of a reference security, including accrued interest.
 *
 * <p>Mirrors C++ v1.42.1 {@code QuantLib::FaceValueAccrualClaim}
 * ({@code ql/instruments/claim.{hpp,cpp}}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Computes the CDS settlement amount as
 * {@code notional * (1 - recoveryRate - accrued/refNotional)}, where {@code accrued}
 * is the reference bond's accrued amount at the default date and {@code refNotional} is the
 * reference bond's notional at the default date.
 *
 * @author JQuantLib migration team (Phase 2 L3-A)
 */
public class FaceValueAccrualClaim extends Claim {

    private final Bond referenceSecurity_;

    public FaceValueAccrualClaim(final Bond referenceSecurity) {
        this.referenceSecurity_ = referenceSecurity;
        // registerWith(referenceSecurity) — C++ mechanism for re-notify-on-change
        if ( referenceSecurity != null ) {
            referenceSecurity.addObserver(this);
        }
    }

    @Override
    public double amount(final Date defaultDate, final double notional, final double recoveryRate) {
        final double accrual = referenceSecurity_.accruedAmount(defaultDate)
                / referenceSecurity_.notional(defaultDate);
        return notional * (1.0 - recoveryRate - accrual);
    }
}
