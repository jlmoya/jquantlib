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
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2010 StatPro Italia srl

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */

package org.jquantlib.cashflow;

import org.jquantlib.time.Date;
import org.jquantlib.util.PolymorphicVisitor;
import org.jquantlib.util.Visitor;

/**
 * Amortizing payment.
 * <p>
 * This class specializes {@link SimpleCashFlow} so that visitors can perform more detailed cash-flow analysis. It
 * carries no extra state beyond {@link SimpleCashFlow}; the distinct type lets a {@link Visitor} dispatch an amortizing
 * principal payment differently from a generic {@link CashFlow}.
 * <p>
 * Port of C++ QuantLib v1.42.1 {@code ql/cashflows/simplecashflow.hpp:76} ({@code class AmortizingPayment}).
 *
 * @author StatPro Italia (C++ original)
 */
public class AmortizingPayment extends SimpleCashFlow {

    //
    // public constructors
    //

    public AmortizingPayment(final double amount, final Date date) {
        super(amount, date);
    }

    //
    // implements PolymorphicVisitable
    //

    @Override
    public void accept(final PolymorphicVisitor pv) {
        final Visitor< AmortizingPayment > v = (pv != null) ? pv.visitor(this.getClass()) : null;
        if ( v != null ) {
            v.visit(this);
        } else {
            super.accept(pv);
        }
    }

}
