/*
 Copyright (C) 2008 Richard Gomes

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
 Copyright (C) 2001, 2002, 2003 Sadruddin Rejeb
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2004, 2005, 2006, 2007, 2009 StatPro Italia srl
 Copyright (C) 2015 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <http://quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.processes;

import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Black (1976) stochastic process.
 * <p>
 * This class describes the stochastic process {@latex$ S } for a forward or
 * futures contract given by
 * {@latex[ d\ln S(t) = -\frac{\sigma(t, S)^2}{2} dt + \sigma dW_t. }
 * <p>
 * It is a {@link GeneralizedBlackScholesProcess} in which the risk-free curve
 * is used <b>both</b> as the discounting curve and as the dividend (cost-of-carry)
 * curve, yielding a zero net drift (Black/forward measure). Mirrors C++ v1.42.1
 * {@code ql/processes/blackscholesprocess.cpp:256-262}.
 *
 * @author Richard Gomes
 */
public class BlackProcess extends GeneralizedBlackScholesProcess {

    public BlackProcess(final Handle< ? extends Quote > x0,
            final Handle< YieldTermStructure > riskFreeTS,
            final Handle< BlackVolTermStructure > blackVolTS) {
        this(x0, riskFreeTS, blackVolTS, new EulerDiscretization());
    }

    public BlackProcess(final Handle< ? extends Quote > x0,
            final Handle< YieldTermStructure > riskFreeTS,
            final Handle< BlackVolTermStructure > blackVolTS,
            final StochasticProcess1D.Discretization1D discretization) {
        // risk-free curve passed as BOTH dividend and risk-free => zero cost of carry
        super(x0, riskFreeTS, riskFreeTS, blackVolTS, discretization);
    }

}
