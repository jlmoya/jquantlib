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

import org.jquantlib.QL;
import org.jquantlib.currencies.Currency;
import org.jquantlib.time.Date;

import java.util.Map;

/**
 * Bankruptcy event. Stronger than every other event type and triggers matching for any contract event
 * ({@link #matchesEventType} returns {@code true} unconditionally).
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::BankruptcyEvent}
 * ({@code ql/experimental/credit/defaultevent.{hpp,cpp}}).
 *
 * <p>Phase 4m foundation.
 */
public class BankruptcyEvent extends DefaultEvent {

    public BankruptcyEvent(final Date creditEventDate, final Currency curr, final Seniority bondsSen,
            final Date settleDate, final Map< Seniority, Double > recoveryRates) {
        super(creditEventDate, new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR), curr, bondsSen,
                settleDate, recoveryRates);
        if ( hasSettled() ) {
            QL.require(recoveryRates.size() == RecoveryRateQuote.makeIsdaConvMap().size(),
                    "Bankruptcy event should have settled for all seniorities.");
        }
    }

    public BankruptcyEvent(final Date creditEventDate, final Currency curr, final Seniority bondsSen,
            final Date settleDate, final double recoveryRate) {
        super(creditEventDate, new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR), curr, bondsSen,
                settleDate, recoveryRate);
    }

    /** This is stronger than all events and triggers all of them. */
    @Override
    public boolean matchesEventType(final DefaultType contractEvType) {
        return true;
    }
}
