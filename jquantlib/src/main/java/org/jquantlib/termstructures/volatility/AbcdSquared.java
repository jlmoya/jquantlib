/*
 Copyright (C) 2026 Jose Moya

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
 Copyright (C) 2006, 2007 Ferdinando Ametrano
 Copyright (C) 2006 Cristina Duminuco
 Copyright (C) 2005, 2006 Klaus Spanderen
 Copyright (C) 2007 Giorgio Facchinetti
*/

package org.jquantlib.termstructures.volatility;

/**
 * Square of an {@link AbcdFunction} evaluated as the instantaneous covariance integrand
 * {@code f(T - t) * f(S - t)}. Helper used by unit tests / numerical integrators (e.g.
 * {@code SegmentIntegral}) to cross-check the analytic primitive in {@link AbcdFunction}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code ql/termstructures/volatility/abcd.{hpp,cpp}}
 * (the {@code AbcdSquared} helper section).
 */
public class AbcdSquared {

    private final AbcdFunction abcd_;
    private final double T_, S_;

    public AbcdSquared(final double a, final double b, final double c, final double d, final double T,
            final double S) {
        this.abcd_ = new AbcdFunction(a, b, c, d);
        this.T_ = T;
        this.S_ = S;
    }

    /**
     * Returns {@code f(T - t) * f(S - t)} via {@link AbcdFunction#covariance(double, double, double)}.
     */
    public double op(final double t) {
        return abcd_.covariance(t, T_, S_);
    }
}
