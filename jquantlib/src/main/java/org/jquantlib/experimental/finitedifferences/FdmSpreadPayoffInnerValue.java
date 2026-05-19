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
 Copyright (C) 2011 Klaus Spanderen
 */
package org.jquantlib.experimental.finitedifferences;

import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.methods.finitedifferences.utilities.FdmInnerValueCalculator;

/**
 * Inner-value calculator for a two-asset spread payoff on an FD mesh.
 *
 * <p>Ported from C++ QuantLib v1.42.1
 * {@code ql/experimental/finitedifferences/fdmspreadpayoffinnervalue.hpp}.</p>
 *
 * <p>Composes two inner-value calculators (one per asset) and applies a
 * {@link BasketPayoff} to the resulting pair. Used by the FD spread-option engines (e.g.
 * {@link FdKlugeExtOUSpreadEngine}) to evaluate the spark- spread payoff on a 3D mesh.</p>
 *
 * @author Phase 5e.5b-CFC-d-164 port
 */
public class FdmSpreadPayoffInnerValue implements FdmInnerValueCalculator {

    private final BasketPayoff payoff_;
    private final FdmInnerValueCalculator calc1_;
    private final FdmInnerValueCalculator calc2_;

    public FdmSpreadPayoffInnerValue(final BasketPayoff payoff, final FdmInnerValueCalculator calc1,
            final FdmInnerValueCalculator calc2) {
        this.payoff_ = payoff;
        this.calc1_ = calc1;
        this.calc2_ = calc2;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final double[] a = new double[2];
        a[0] = calc1_.innerValue(iter, t);
        a[1] = calc2_.innerValue(iter, t);
        return payoff_.get(a);
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return innerValue(iter, t);
    }
}
