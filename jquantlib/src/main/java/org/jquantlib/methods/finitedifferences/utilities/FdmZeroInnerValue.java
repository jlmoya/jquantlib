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
 Copyright (C) 2010 Klaus Spanderen
 */
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * Zero inner-value calculator: returns {@code 0.0} unconditionally.
 *
 * <p>Java port of v1.42.1
 * {@code ql/methods/finitedifferences/utilities/fdminnervaluecalculator.hpp::FdmZeroInnerValue}.
 *
 * <p>Used by engines whose terminal payoff is identically zero (e.g.,
 * {@code FdSimpleBSSwingEngine}, where the only contribution comes from
 * intermediate exercise step conditions, not a terminal payoff).
 *
 * @author Phase 5e.5b-CFC-d-170 port
 */
public class FdmZeroInnerValue implements FdmInnerValueCalculator {

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        return 0.0;
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return 0.0;
    }
}
