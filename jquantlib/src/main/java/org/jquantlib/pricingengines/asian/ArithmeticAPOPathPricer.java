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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2003, 2004 Ferdinando Ametrano
*/

package org.jquantlib.pricingengines.asian;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;

/**
 * Path pricer for discrete arithmetic-average-price Asian payoffs.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/asian/mc_discr_arith_av_price.{hpp,cpp}}
 * {@code ArithmeticAPOPathPricer} (Phase 5e.5b-CFC-d-114).
 *
 * @author JQuantLib
 */
public final class ArithmeticAPOPathPricer extends PathPricer<Path> {

    private final PlainVanillaPayoff payoff_;
    private final double discount_;
    private final double runningSum_;
    private final int pastFixings_;

    public ArithmeticAPOPathPricer(final Option.Type type,
                                   final double strike,
                                   final double discount) {
        this(type, strike, discount, 0.0, 0);
    }

    public ArithmeticAPOPathPricer(final Option.Type type,
                                   final double strike,
                                   final double discount,
                                   final double runningSum,
                                   final int pastFixings) {
        QL.require(strike >= 0.0, "strike less than zero not allowed");
        this.payoff_ = new PlainVanillaPayoff(type, strike);
        this.discount_ = discount;
        this.runningSum_ = runningSum;
        this.pastFixings_ = pastFixings;
    }

    @Override
    public Double op(final Path path) {
        final int n = path.length();
        QL.require(n > 1, "the path cannot be empty");

        double sum;
        int fixings;
        if (path.timeGrid().mandatoryTimes().get(0) == 0.0) {
            sum = runningSum_;
            for (int i = 0; i < n; i++) {
                sum += path.get(i);
            }
            fixings = pastFixings_ + n;
        } else {
            sum = runningSum_;
            for (int i = 1; i < n; i++) {
                sum += path.get(i);
            }
            fixings = pastFixings_ + n - 1;
        }
        final double averagePrice = sum / fixings;
        return discount_ * payoff_.get(averagePrice);
    }
}
