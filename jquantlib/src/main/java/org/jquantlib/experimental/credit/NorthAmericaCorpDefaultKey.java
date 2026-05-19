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
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;

import java.util.ArrayList;

/**
 * ISDA standard default contractual key for corporate US debt.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::NorthAmericaCorpDefaultKey}
 * ({@code ql/experimental/credit/defaultprobabilitykey.{hpp,cpp}}).
 *
 * <p>Configures three event types: {@link FailureToPay} (with grace period
 * + amount required), {@link AtomicDefault.Type#Bankruptcy}, and {@link AtomicDefault.Type#Restructuring} (unless
 * {@code resType} is {@link Restructuring.Type#NoRestructuring}). Default grace period is 30 days; default amount is
 * 1.e6.
 *
 * <p>Phase 4m foundation.
 */
public class NorthAmericaCorpDefaultKey extends DefaultProbKey {

    public NorthAmericaCorpDefaultKey(final Currency currency, final Seniority sen) {
        this(currency, sen, new Period(30, TimeUnit.Days), 1.0e6, Restructuring.CR);
    }

    public NorthAmericaCorpDefaultKey(final Currency currency, final Seniority sen, final Period graceFailureToPay) {
        this(currency, sen, graceFailureToPay, 1.0e6, Restructuring.CR);
    }

    public NorthAmericaCorpDefaultKey(final Currency currency, final Seniority sen, final Period graceFailureToPay,
            final double amountFailure) {
        this(currency, sen, graceFailureToPay, amountFailure, Restructuring.CR);
    }

    public NorthAmericaCorpDefaultKey(final Currency currency, final Seniority sen, final Period graceFailureToPay,
            final double amountFailure, final Restructuring.Type resType) {
        super(new ArrayList<>(), currency, sen);
        eventTypes.add(new FailureToPay(graceFailureToPay, amountFailure));
        eventTypes.add(new DefaultType(AtomicDefault.Type.Bankruptcy, Restructuring.XR));
        if ( resType != Restructuring.Type.NoRestructuring ) {
            eventTypes.add(new DefaultType(AtomicDefault.Type.Restructuring, resType));
        }
    }
}
