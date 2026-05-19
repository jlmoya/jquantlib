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
 Copyright (C) 2009 Jose Aparicio
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.quotes.Handle;
import org.jquantlib.time.Date;
import org.jquantlib.util.Observer;

/**
 * Simple recovery-rate model returning the constant value of the quote independently of the date and the seniority.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::ConstantRecoveryModel}
 * ({@code ql/experimental/credit/recoveryratemodel.{hpp,cpp}}).
 *
 * <p>Phase 4m foundation.
 */
public class ConstantRecoveryModel extends RecoveryRateModel implements Observer {

    private final Handle< RecoveryRateQuote > quote;

    public ConstantRecoveryModel(final Handle< RecoveryRateQuote > quote) {
        this.quote = quote;
        this.quote.addObserver(this);
    }

    public ConstantRecoveryModel(final double recovery) {
        this(recovery, Seniority.NoSeniority);
    }

    public ConstantRecoveryModel(final double recovery, final Seniority sen) {
        this.quote = new Handle< RecoveryRateQuote >(new RecoveryRateQuote(recovery, sen));
    }

    @Override
    public void update() {
        notifyObservers();
    }

    @Override
    public boolean appliesToSeniority(final Seniority sen) {
        return true;
    }

    @Override
    protected double recoveryValueImpl(final Date date, final DefaultProbKey defaultKey) {
        // No match on requested seniority — all pass.
        return quote.currentLink().value();
    }
}
