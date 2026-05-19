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
package org.jquantlib.experimental.processes;

import org.jquantlib.QL;
import org.jquantlib.math.Ops;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.processes.OrnsteinUhlenbeckProcess;
import org.jquantlib.processes.StochasticProcess1D;

/**
 * Extended Ornstein-Uhlenbeck process.
 * <p>
 * Java port of v1.42.1 {@code ql/experimental/processes/extendedornsteinuhlenbeckprocess.{hpp,cpp}}.
 * <p>
 * Models {@code dx = a (b(t) - x_t) dt + sigma dW_t} where {@code b(t)} is a deterministic time-dependent level. The
 * expectation discretization is selectable: {@link Discretization#MidPoint} (default in C++),
 * {@link Discretization#Trapezodial} (note: misspelling preserved from C++ to keep the API identical), or
 * {@link Discretization#GaussLobatto} which integrates {@code b(s) exp(-a s)} over {@code [t0, t0+dt]} adaptively.
 *
 * @author Phase 4n WI port
 */
public class ExtendedOrnsteinUhlenbeckProcess extends StochasticProcess1D {

    private final double speed_;
    private final double vol_;
    private final Ops.DoubleOp b_;
    private final double intEps_;
    private final OrnsteinUhlenbeckProcess ouProcess_;
    private final Discretization discretization_;
    public ExtendedOrnsteinUhlenbeckProcess(final double speed, final double vol, final double x0,
            final Ops.DoubleOp b) {
        this(speed, vol, x0, b, Discretization.MidPoint, 1e-4);
    }

    public ExtendedOrnsteinUhlenbeckProcess(final double speed, final double vol, final double x0, final Ops.DoubleOp b,
            final Discretization discretization, final double intEps) {
        super();
        this.speed_ = speed;
        this.vol_ = vol;
        this.b_ = b;
        this.intEps_ = intEps;
        this.ouProcess_ = new OrnsteinUhlenbeckProcess(speed, vol, x0);
        this.discretization_ = discretization;
        QL.require(speed_ >= 0.0, "negative a given");
        QL.require(vol_ >= 0.0, "negative volatility given");
    }

    public double speed() {
        return speed_;
    }

    public double volatility() {
        return vol_;
    }

    @Override
    public double x0() {
        return ouProcess_.x0();
    }

    @Override
    public double drift(final double t, final double x) {
        return ouProcess_.drift(t, x) + speed_ * b_.op(t);
    }

    @Override
    public double diffusion(final double t, final double x) {
        return ouProcess_.diffusion(t, x);
    }

    @Override
    public double stdDeviation(final double t0, final double x0, final double dt) {
        return ouProcess_.stdDeviation(t0, x0, dt);
    }

    @Override
    public double variance(final double t0, final double x0, final double dt) {
        return ouProcess_.variance(t0, x0, dt);
    }

    @Override
    public double expectation(final double t0, final double x0, final double dt) {
        switch ( discretization_ ) {
        case MidPoint:
            return ouProcess_.expectation(t0, x0, dt) + b_.op(t0 + 0.5 * dt) * (1.0 - Math.exp(-speed_ * dt));
        case Trapezodial: {
            final double t = t0 + dt;
            final double u = t0;
            final double bt = b_.op(t);
            final double bu = b_.op(u);
            final double ex = Math.exp(-speed_ * dt);
            return ouProcess_.expectation(t0, x0, dt) + bt - ex * bu - (bt - bu) / (speed_ * dt) * (1 - ex);
        }
        case GaussLobatto: {
            final Ops.DoubleOp integrand = new Ops.DoubleOp() {
                @Override
                public double op(final double s) {
                    return b_.op(s) * Math.exp(speed_ * s);
                }
            };
            return ouProcess_.expectation(t0, x0, dt) + speed_ * Math.exp(-speed_ * (t0 + dt))
                    * new GaussLobattoIntegral(100000, intEps_).op(integrand, t0, t0 + dt);
        }
        default:
            throw new IllegalStateException("unknown discretization scheme");
        }
    }

    /** Discretization scheme for the expectation; matches C++ enum. */
    public enum Discretization {MidPoint, Trapezodial, GaussLobatto}
}
