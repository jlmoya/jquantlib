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
 Copyright (C) 2010 Adrian O' Neill

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

package org.jquantlib.experimental.variancegamma;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.processes.EulerDiscretization;
import org.jquantlib.processes.StochasticProcess1D;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.YieldTermStructure;

/**
 * Variance Gamma stochastic process.
 *
 * <p>Phase 4c port of {@code QuantLib::VarianceGammaProcess}
 * (v1.42.1 ql/experimental/variancegamma/variancegammaprocess.{hpp,cpp}).
 *
 * <p>This class describes the stochastic volatility process. With a
 * Brownian motion given by db = theta dt + sigma dW_t, then a Variance
 * Gamma process X is defined by evaluating this Brownian motion at
 * sample times driven by a Gamma process. If T is the value of a Gamma
 * process with mean 1 and variance rate nu then the Variance Gamma
 * process is given by X(t) = B(T).
 *
 * <p>Like the C++ original (v1.42.1), {@link #drift(double, double)} and
 * {@link #diffusion(double, double)} are not implemented in this class,
 * since the Variance Gamma process is not described by an Ito SDE in the
 * usual sense. Engines such as {@link VarianceGammaEngine} read the raw
 * parameters via {@link #sigma()}, {@link #nu()} and {@link #theta()}
 * and price options analytically.
 *
 * @category processes
 */
public class VarianceGammaProcess extends StochasticProcess1D {

    private final Handle<? extends Quote> s0_;
    private final Handle<YieldTermStructure> dividendYield_;
    private final Handle<YieldTermStructure> riskFreeRate_;
    private final double sigma_;
    private final double nu_;
    private final double theta_;

    public VarianceGammaProcess(
            final Handle<? extends Quote> s0,
            final Handle<YieldTermStructure> dividendYield,
            final Handle<YieldTermStructure> riskFreeRate,
            final /*@Real*/ double sigma,
            final /*@Real*/ double nu,
            final /*@Real*/ double theta) {
        super(new EulerDiscretization());
        this.s0_ = s0;
        this.dividendYield_ = dividendYield;
        this.riskFreeRate_ = riskFreeRate;
        this.sigma_ = sigma;
        this.nu_ = nu;
        this.theta_ = theta;
        this.riskFreeRate_.addObserver(this);
        this.dividendYield_.addObserver(this);
        this.s0_.addObserver(this);
    }

    @Override
    public double x0() /*@ReadOnly*/ {
        return s0_.currentLink().value();
    }

    @Override
    public double drift(final /*@Time*/ double t, final /*@Real*/ double x) /*@ReadOnly*/ {
        throw new LibraryException("not implemented yet");
    }

    @Override
    public double diffusion(final /*@Time*/ double t, final /*@Real*/ double x) /*@ReadOnly*/ {
        throw new LibraryException("not implemented yet");
    }

    public double sigma() /*@ReadOnly*/ {
        return sigma_;
    }

    public double nu() /*@ReadOnly*/ {
        return nu_;
    }

    public double theta() /*@ReadOnly*/ {
        return theta_;
    }

    public Handle<? extends Quote> s0() /*@ReadOnly*/ {
        return s0_;
    }

    public Handle<YieldTermStructure> dividendYield() /*@ReadOnly*/ {
        return dividendYield_;
    }

    public Handle<YieldTermStructure> riskFreeRate() /*@ReadOnly*/ {
        return riskFreeRate_;
    }
}
