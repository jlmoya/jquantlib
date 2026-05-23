/*
 Copyright (C) 2026 Jose Moya

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

package org.jquantlib.math.optimization;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.math.Constants;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.randomnumbers.MersenneTwisterUniformRng;

/**
 * Simulated Annealing optimizer (Numerical Recipes in C, 2nd ed., Chapter 10.9).
 * <p>
 * Faithful Java port of QuantLib v1.42.1 {@code ql/math/optimization/simulatedannealing.hpp}
 * (Peter Caspers, 2013). The C++ class is templated on {@code RNG}; the Java port uses
 * {@link MersenneTwisterUniformRng} directly to match the C++ default
 * {@code SimulatedAnnealing<MersenneTwisterUniformRng>}. Other RNGs are easily wired
 * via a future overload should a caller need them.
 * <p>
 * The C++ exit criterion in {@code f(x)} was replaced upstream with one in {@code x}
 * (see {@code simplex.cpp} for the GSL reference). This port mirrors that behavior.
 *
 * @author Jose Moya
 */
public class SimulatedAnnealing extends OptimizationMethod {

    public enum Scheme {
        ConstantFactor, ConstantBudget
    }

    private final Scheme scheme_;
    private final double lambda_;
    private final double T0_;
    private final double epsilon_;
    private final double alpha_;
    private final int K_;
    private final MersenneTwisterUniformRng rng_;

    private double T_;
    private List< Array > vertices_;
    private Array values_;
    private Array sum_;
    private int i_;
    private int ihi_;
    private int ilo_;
    private int j_;
    private int m_;
    private int n_;
    private double fac1_;
    private double fac2_;
    private double yflu_;
    private double rtol_;
    private double yhi_;
    private double ylo_;
    private double ynhi_;
    private double ysave_;
    private double yt_;
    private double ytry_;
    private double yb_;
    private double tt_;
    private Array pb_;
    private Array ptry_;
    private int iteration_;
    private int iterationT_;

    /**
     * Constant-factor cooling: reduce temperature {@code T} by {@code (1-epsilon)} after every {@code m} moves.
     */
    public SimulatedAnnealing(final double lambda, final double T0, final double epsilon, final int m,
            final MersenneTwisterUniformRng rng) {
        this.scheme_ = Scheme.ConstantFactor;
        this.lambda_ = lambda;
        this.T0_ = T0;
        this.epsilon_ = epsilon;
        this.alpha_ = 0.0;
        this.K_ = 0;
        this.rng_ = rng;
        this.m_ = m;
    }

    public SimulatedAnnealing(final double lambda, final double T0, final double epsilon, final int m) {
        this(lambda, T0, epsilon, m, new MersenneTwisterUniformRng(0L));
    }

    /**
     * Constant-budget cooling: budget {@code K} total moves; temperature follows
     * {@code T0 * (1 - k/K)^alpha} for the first {@code K} moves and {@code 0} afterwards
     * (deterministic simplex tail).
     */
    public SimulatedAnnealing(final double lambda, final double T0, final int K, final double alpha,
            final MersenneTwisterUniformRng rng) {
        this.scheme_ = Scheme.ConstantBudget;
        this.lambda_ = lambda;
        this.T0_ = T0;
        this.epsilon_ = 0.0;
        this.alpha_ = alpha;
        this.K_ = K;
        this.rng_ = rng;
    }

    public SimulatedAnnealing(final double lambda, final double T0, final int K, final double alpha) {
        this(lambda, T0, K, alpha, new MersenneTwisterUniformRng(0L));
    }

    private double simplexSize() {
        // Computed as in simplex.cpp.
        final int dim = vertices_.get(0).size();
        final double[] cdata = new double[dim];
        for ( final Array v : vertices_ ) {
            for ( int k = 0; k < dim; ++k ) {
                cdata[k] += v.get(k);
            }
        }
        for ( int k = 0; k < dim; ++k ) {
            cdata[k] /= vertices_.size();
        }
        final Array center = new Array(cdata);
        double result = 0.0;
        for ( final Array v : vertices_ ) {
            final Array temp = v.sub(center);
            // Norm2 here is Euclidean norm (Norm2 in QL Array).
            double s = 0.0;
            for ( int k = 0; k < temp.size(); ++k ) {
                s += temp.get(k) * temp.get(k);
            }
            result += Math.sqrt(s);
        }
        return result / vertices_.size();
    }

    private double rngNext() {
        // Match C++ MersenneTwisterUniformRng::next().value: (nextInt32() + 0.5) / 2^32.
        return (rng_.nextInt32() + 0.5) / 4294967296.0;
    }

    private void amotsa(final Problem P, final double fac) {
        fac1_ = (1.0 - fac) / ((double) n_);
        fac2_ = fac1_ - fac;
        for ( j_ = 0; j_ < n_; j_++ ) {
            ptry_.set(j_, sum_.get(j_) * fac1_ - vertices_.get(ihi_).get(j_) * fac2_);
        }
        if ( !P.constraint().test(ptry_) ) {
            ytry_ = Constants.QL_MAX_REAL;
        } else {
            ytry_ = P.value(ptry_);
        }
        if ( Double.isNaN(ytry_) ) {
            ytry_ = Constants.QL_MAX_REAL;
        }
        if ( ytry_ <= yb_ ) {
            yb_ = ytry_;
            pb_ = ptry_.clone();
        }
        yflu_ = ytry_ - tt_ * Math.log(rngNext());
        if ( yflu_ < yhi_ ) {
            values_.set(ihi_, ytry_);
            yhi_ = yflu_;
            for ( j_ = 0; j_ < n_; j_++ ) {
                sum_.set(j_, sum_.get(j_) + ptry_.get(j_) - vertices_.get(ihi_).get(j_));
                vertices_.get(ihi_).set(j_, ptry_.get(j_));
            }
        }
        ytry_ = yflu_;
    }

    @Override
    public EndCriteria.Type minimize(final Problem P, final EndCriteria ec) {

        final int[] stationaryStateIterations = { 0 };
        final EndCriteria.Type[] ecType = { EndCriteria.Type.None };
        P.reset();
        final Array x = P.currentValue();
        iteration_ = 0;
        n_ = x.size();
        ptry_ = new Array(n_);

        // build vertices
        vertices_ = new ArrayList<>(n_ + 1);
        for ( int k = 0; k <= n_; ++k ) {
            vertices_.add(x.clone());
        }
        for ( i_ = 0; i_ < n_; i_++ ) {
            final Array direction = new Array(n_);
            direction.set(i_, 1.0);
            P.constraint().update(vertices_.get(i_ + 1), direction, lambda_);
        }
        values_ = new Array(n_ + 1);
        for ( i_ = 0; i_ <= n_; i_++ ) {
            if ( !P.constraint().test(vertices_.get(i_)) ) {
                values_.set(i_, Constants.QL_MAX_REAL);
            } else {
                values_.set(i_, P.value(vertices_.get(i_)));
            }
            // C++ has a vestigial check `if (std::isnan(ytry_))` here that uses
            // an *uninitialized* ytry_ on the first iteration. Faithfully preserved
            // — it almost never triggers on the first iteration, and the check is
            // immediately followed in the inner loop by proper NaN clamping.
            if ( Double.isNaN(ytry_) ) {
                values_.set(i_, Constants.QL_MAX_REAL);
            }
        }

        // minimize
        T_ = T0_;
        yb_ = Constants.QL_MAX_REAL;
        pb_ = new Array(n_);
        while ( true ) {
            iterationT_ = iteration_;
            do {
                sum_ = new Array(n_);
                for ( i_ = 0; i_ <= n_; i_++ ) {
                    sum_.addAssign(vertices_.get(i_));
                }
                tt_ = -T_;
                ilo_ = 0;
                ihi_ = 1;
                ynhi_ = values_.get(0) + tt_ * Math.log(rngNext());
                ylo_ = ynhi_;
                yhi_ = values_.get(1) + tt_ * Math.log(rngNext());
                if ( ylo_ > yhi_ ) {
                    ihi_ = 0;
                    ilo_ = 1;
                    ynhi_ = yhi_;
                    yhi_ = ylo_;
                    ylo_ = ynhi_;
                }
                for ( i_ = 2; i_ < n_ + 1; i_++ ) {
                    yt_ = values_.get(i_) + tt_ * Math.log(rngNext());
                    if ( yt_ <= ylo_ ) {
                        ilo_ = i_;
                        ylo_ = yt_;
                    }
                    if ( yt_ > yhi_ ) {
                        ynhi_ = yhi_;
                        ihi_ = i_;
                        yhi_ = yt_;
                    } else {
                        if ( yt_ > ynhi_ ) {
                            ynhi_ = yt_;
                        }
                    }
                }

                // GSL end criterion in x.
                if ( ec.checkStationaryPoint(simplexSize(), 0.0, stationaryStateIterations, ecType)
                        || ec.checkMaxIterations(iteration_, ecType) ) {
                    // Return the best ever point regardless of which criterion fired.
                    P.setCurrentValue(pb_);
                    P.setFunctionValue(yb_);
                    return ecType[0];
                }

                iteration_ += 2;
                amotsa(P, -1.0);
                if ( ytry_ <= ylo_ ) {
                    amotsa(P, 2.0);
                } else if ( ytry_ >= ynhi_ ) {
                    ysave_ = yhi_;
                    amotsa(P, 0.5);
                    if ( ytry_ >= ysave_ ) {
                        for ( i_ = 0; i_ < n_ + 1; i_++ ) {
                            if ( i_ != ilo_ ) {
                                for ( j_ = 0; j_ < n_; j_++ ) {
                                    final double m = 0.5 * (vertices_.get(i_).get(j_) + vertices_.get(ilo_).get(j_));
                                    sum_.set(j_, m);
                                    vertices_.get(i_).set(j_, m);
                                }
                                values_.set(i_, P.value(sum_));
                            }
                        }
                        iteration_ += n_;
                        for ( i_ = 0; i_ < n_; i_++ ) {
                            sum_.set(i_, 0.0);
                        }
                        for ( i_ = 0; i_ <= n_; i_++ ) {
                            sum_.addAssign(vertices_.get(i_));
                        }
                    }
                } else {
                    iteration_ += 1;
                }
            } while ( iteration_ < iterationT_ + (scheme_ == Scheme.ConstantFactor ? m_ : 1) );

            switch ( scheme_ ) {
            case ConstantFactor:
                T_ *= (1.0 - epsilon_);
                break;
            case ConstantBudget:
                if ( iteration_ <= K_ ) {
                    T_ = T0_ * Math.pow(1.0 - (double) iteration_ / (double) K_, alpha_);
                } else {
                    T_ = 0.0;
                }
                break;
            }
        }
    }
}
