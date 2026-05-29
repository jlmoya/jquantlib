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
 * Garman-Kohlhagen (1983) stochastic process.
 * <p>
 * This class describes the stochastic process {@latex$ S } for an exchange rate
 * given by
 * {@latex[ d\ln S(t) = (r(t) - r_f(t) - \frac{\sigma(t, S)^2}{2}) dt + \sigma dW_t. }
 * <p>
 * It is a {@link GeneralizedBlackScholesProcess} where the <b>foreign</b>
 * risk-free curve plays the role of the dividend yield and the <b>domestic</b>
 * risk-free curve is the discounting curve. Mirrors C++ v1.42.1
 * {@code ql/processes/blackscholesprocess.cpp:265-273}.
 *
 * @author Richard Gomes
 */
public class GarmanKohlagenProcess extends GeneralizedBlackScholesProcess {

    public GarmanKohlagenProcess(final Handle< ? extends Quote > x0,
            final Handle< YieldTermStructure > foreignRiskFreeTS,
            final Handle< YieldTermStructure > domesticRiskFreeTS,
            final Handle< BlackVolTermStructure > blackVolTS) {
        this(x0, foreignRiskFreeTS, domesticRiskFreeTS, blackVolTS, new EulerDiscretization());
    }

    public GarmanKohlagenProcess(final Handle< ? extends Quote > x0,
            final Handle< YieldTermStructure > foreignRiskFreeTS,
            final Handle< YieldTermStructure > domesticRiskFreeTS,
            final Handle< BlackVolTermStructure > blackVolTS,
            final StochasticProcess1D.Discretization1D discretization) {
        // foreign risk-free curve takes the dividend slot; domestic is the
        // risk-free (discounting) curve.
        super(x0, foreignRiskFreeTS, domesticRiskFreeTS, blackVolTS, discretization);
    }

}
