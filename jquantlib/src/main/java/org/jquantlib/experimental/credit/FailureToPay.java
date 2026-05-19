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

import org.jquantlib.time.Period;

/**
 * Failure-to-Pay atomic event type.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::FailureToPay}
 * ({@code ql/experimental/credit/defaulttype.hpp}). Atomic construction only, with grace period and minimum default
 * amount triggering the event.
 *
 * <p>The C++ default amount is {@code 1.e+6} (one million units) per ISDA
 * docs. Note the C++ comment that the contract is in dollars by default and not in the contract currency — this is
 * preserved as-is.
 *
 * <p>Phase 4m foundation.
 */
public class FailureToPay extends DefaultType {

    private final Period gracePeriod;
    private final double amountRequired;

    public FailureToPay(final Period grace) {
        this(grace, 1.0e6);
    }

    public FailureToPay(final Period grace, final double amount) {
        super(AtomicDefault.Type.FailureToPay, Restructuring.XR);
        this.gracePeriod = grace;
        this.amountRequired = amount;
    }

    public double amountRequired() {
        return amountRequired;
    }

    public Period gracePeriod() {
        return gracePeriod;
    }
}
