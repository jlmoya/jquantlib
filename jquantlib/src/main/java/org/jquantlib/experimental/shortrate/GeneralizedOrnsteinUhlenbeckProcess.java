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
 Copyright (C) 2010 SunTrust Bank
 Copyright (C) 2010 Cavit Hafizoglu

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.
*/

package org.jquantlib.experimental.shortrate;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.math.Ops;
import org.jquantlib.processes.StochasticProcess1D;

/**
 * Piecewise-linear Ornstein-Uhlenbeck process.
 *
 * <p>Phase 4c port of {@code QuantLib::GeneralizedOrnsteinUhlenbeckProcess}
 * (v1.42.1 ql/experimental/shortrate/generalizedornsteinuhlenbeckprocess.{hpp,cpp}).
 *
 * <p>This class describes the Ornstein-Uhlenbeck process governed by
 * dx = a (level - x_t) dt + sigma dW_t, where the coefficients {@code a (= speed)} and {@code sigma (= vol)} are
 * piecewise-linear functions of time, supplied as {@link Ops.DoubleOp} callables.
 *
 * @category processes
 */
public class GeneralizedOrnsteinUhlenbeckProcess extends StochasticProcess1D {

    private final double x0_;
    private final double level_;
    private final Ops.DoubleOp speed_;
    private final Ops.DoubleOp volatility_;

    public GeneralizedOrnsteinUhlenbeckProcess(final Ops.DoubleOp speed, final Ops.DoubleOp vol) {
        this(speed, vol, 0.0, 0.0);
    }

    public GeneralizedOrnsteinUhlenbeckProcess(final Ops.DoubleOp speed, final Ops.DoubleOp vol,
            final /*@Real*/ double x0) {
        this(speed, vol, x0, 0.0);
    }

    public GeneralizedOrnsteinUhlenbeckProcess(final Ops.DoubleOp speed, final Ops.DoubleOp vol,
            final /*@Real*/ double x0, final /*@Real*/ double level) {
        super();
        QL.require(x0 >= 0.0, "negative initial data given");
        QL.require(level >= 0.0, "negative level given");
        this.x0_ = x0;
        this.level_ = level;
        this.speed_ = speed;
        this.volatility_ = vol;
    }

    @Override
    public double x0() /*@ReadOnly*/ {
        return x0_;
    }

    @Override
    public double drift(final /*@Time*/ double t, final /*@Real*/ double x) /*@ReadOnly*/ {
        return speed_.op(t) * (level_ - x);
    }

    @Override
    public double diffusion(final /*@Time*/ double t, final /*@Real*/ double x) /*@ReadOnly*/ {
        return volatility_.op(t);
    }

    @Override
    public double expectation(final /*@Time*/ double t, final /*@Real*/ double x0,
            final /*@Time*/ double dt) /*@ReadOnly*/ {
        return level_ + (x0 - level_) * Math.exp(-speed_.op(t) * dt);
    }

    @Override
    public double stdDeviation(final /*@Time*/ double t, final /*@Real*/ double x0,
            final /*@Time*/ double dt) /*@ReadOnly*/ {
        return Math.sqrt(variance(t, x0, dt));
    }

    @Override
    public double variance(final /*@Time*/ double t, final /*@Real*/ double x0,
            final /*@Time*/ double dt) /*@ReadOnly*/ {
        final double speed = speed_.op(t);
        final double vol = volatility_.op(t);
        if ( speed < Math.sqrt(Constants.QL_EPSILON) ) {
            // algebraic limit for small speed
            return vol * vol * dt;
        }
        return 0.5 * vol * vol / speed * (1.0 - Math.exp(-2.0 * speed * dt));
    }

    public double speed(final /*@Time*/ double t) /*@ReadOnly*/ {
        return speed_.op(t);
    }

    public double volatility(final /*@Time*/ double t) /*@ReadOnly*/ {
        return volatility_.op(t);
    }

    public double level() /*@ReadOnly*/ {
        return level_;
    }
}
