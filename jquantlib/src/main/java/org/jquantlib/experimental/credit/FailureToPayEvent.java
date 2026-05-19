/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2009 StatPro Italia srl
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.currencies.Currency;
import org.jquantlib.time.Date;

import java.util.Map;

/**
 * FailureToPay event. Records the defaulted amount and overrides {@link #matchesEventType(DefaultType)} with
 * FTP-specific logic.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::FailureToPayEvent}
 * ({@code ql/experimental/credit/defaultevent.{hpp,cpp}}).
 *
 * <p>The C++ {@code matchesEventType} checks (a) the contract event type
 * is itself a {@link FailureToPay}, (b) the defaulted amount meets or exceeds the contract's {@code amountRequired},
 * and (c) the event occurred at-or-before {@code today - gracePeriod}. The Java port keeps the same logic.
 *
 * <p>Phase 4m foundation.
 */
public class FailureToPayEvent extends DefaultEvent {

    private final double defaultedAmount;

    public FailureToPayEvent(final Date creditEventDate, final Currency curr, final Seniority bondsSen,
            final double defaultedAmount, final Date settleDate, final Map< Seniority, Double > recoveryRates) {
        super(creditEventDate, new DefaultType(AtomicDefault.Type.FailureToPay, Restructuring.XR), curr, bondsSen,
                settleDate, recoveryRates);
        this.defaultedAmount = defaultedAmount;
    }

    public FailureToPayEvent(final Date creditEventDate, final Currency curr, final Seniority bondsSen,
            final double defaultedAmount, final Date settleDate, final double recoveryRate) {
        super(creditEventDate, new DefaultType(AtomicDefault.Type.FailureToPay, Restructuring.XR), curr, bondsSen,
                settleDate, recoveryRate);
        this.defaultedAmount = defaultedAmount;
    }

    public double amountDefaulted() {
        return defaultedAmount;
    }

    @Override
    public boolean matchesEventType(final DefaultType contractEvType) {
        if ( !(contractEvType instanceof FailureToPay) ) {
            return false;
        }
        final FailureToPay eveType = (FailureToPay) contractEvType;
        if ( defaultedAmount < eveType.amountRequired() ) {
            return false;
        }
        final Date today = evaluationDate();
        // Mirrors C++ hasOccurred(today - gracePeriod, true)
        final Date threshold = today.sub(eveType.gracePeriod());
        return this.hasOccurred(threshold, true);
    }
}
