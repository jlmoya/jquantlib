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
 Copyright (C) 2008 Yee Man Chan

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.processes;

import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.math.matrixutilities.Matrix;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Stochastic-volatility GJR-GARCH(1,1) process.
 *
 * <p>Faithful Java port of C++ QuantLib v1.42.1
 * {@code ql/processes/gjrgarchprocess.{hpp,cpp}}. Parameters supplied
 * to the constructor are daily constants and are annualized internally
 * via {@code daysPerYear} (default {@code 252.0}).
 *
 * <p>The process is governed by
 * {@latex[
 *   dS(t, S)  = \mu S\,dt + \sqrt{v}\,S\,dW_1 \\
 *   dv(t, S)  = (\omega + (\beta + \alpha q_{2} + \gamma q_{3} - 1) v)\,dt
 *               + (\alpha \sigma_{12} + \gamma \sigma_{13})\,v\,dW_1
 *               + \sqrt{\alpha^{2} (\sigma^{2}_{2} - \sigma^{2}_{12})
 *                       + \gamma^{2} (\sigma^{2}_{3} - \sigma^{2}_{13})
 *                       + 2 \alpha \gamma (\sigma_{23} - \sigma_{12} \sigma_{13})}\,v\,dW_2
 * }
 * with auxiliary moment constants derived from the standard normal mass
 * left of {@code lambda} and the corresponding density.
 *
 * <p>Reference: Glosten, Jagannathan, Runkle (1993).
 */
public class GjrGarchProcess extends StochasticProcess {

    /** Lord/Koekkoek/van Dijk variance-treatment scheme. */
    public enum Discretization {
        PartialTruncation, FullTruncation, Reflection
    }

    private final Handle<YieldTermStructure> riskFreeRate_;
    private final Handle<YieldTermStructure> dividendYield_;
    private final Handle<Quote> s0_;
    private final double v0_;
    private final double omega_;
    private final double alpha_;
    private final double beta_;
    private final double gamma_;
    private final double lambda_;
    private final double daysPerYear_;
    private final Discretization discretization_;

    /** Convenience constructor: 252 days/year, full truncation. */
    public GjrGarchProcess(
            final Handle<YieldTermStructure> riskFreeRate,
            final Handle<YieldTermStructure> dividendYield,
            final Handle<Quote> s0,
            final double v0,
            final double omega,
            final double alpha,
            final double beta,
            final double gamma,
            final double lambda) {
        this(riskFreeRate, dividendYield, s0, v0, omega, alpha, beta, gamma,
             lambda, 252.0, Discretization.FullTruncation);
    }

    /** Convenience constructor: custom days/year, full truncation. */
    public GjrGarchProcess(
            final Handle<YieldTermStructure> riskFreeRate,
            final Handle<YieldTermStructure> dividendYield,
            final Handle<Quote> s0,
            final double v0,
            final double omega,
            final double alpha,
            final double beta,
            final double gamma,
            final double lambda,
            final double daysPerYear) {
        this(riskFreeRate, dividendYield, s0, v0, omega, alpha, beta, gamma,
             lambda, daysPerYear, Discretization.FullTruncation);
    }

    public GjrGarchProcess(
            final Handle<YieldTermStructure> riskFreeRate,
            final Handle<YieldTermStructure> dividendYield,
            final Handle<Quote> s0,
            final double v0,
            final double omega,
            final double alpha,
            final double beta,
            final double gamma,
            final double lambda,
            final double daysPerYear,
            final Discretization d) {
        this.riskFreeRate_ = riskFreeRate;
        this.dividendYield_ = dividendYield;
        this.s0_ = s0;
        this.v0_ = v0;
        this.omega_ = omega;
        this.alpha_ = alpha;
        this.beta_ = beta;
        this.gamma_ = gamma;
        this.lambda_ = lambda;
        this.daysPerYear_ = daysPerYear;
        this.discretization_ = d;

        this.riskFreeRate_.addObserver(this);
        this.dividendYield_.addObserver(this);
        this.s0_.addObserver(this);
    }

    //
    // accessors
    //

    public double v0()           { return v0_; }
    public double lambda()       { return lambda_; }
    public double omega()        { return omega_; }
    public double alpha()        { return alpha_; }
    public double beta()         { return beta_; }
    public double gamma()        { return gamma_; }
    public double daysPerYear()  { return daysPerYear_; }

    public Handle<Quote> s0()                            { return s0_; }
    public Handle<YieldTermStructure> dividendYield()    { return dividendYield_; }
    public Handle<YieldTermStructure> riskFreeRate()     { return riskFreeRate_; }

    //
    // StochasticProcess overrides
    //

    @Override
    public int size() {
        return 2;
    }

    @Override
    public Array initialValues() {
        return new Array(new double[] { s0_.currentLink().value(), daysPerYear_ * v0_ });
    }

    @Override
    public Array drift(final double t, final Array x) {
        final double x1 = x.get(1);
        final double N = new CumulativeNormalDistribution().op(lambda_);
        final double n = Math.exp(-lambda_ * lambda_ / 2.0) / Math.sqrt(2.0 * Constants.M_PI);
        final double q2 = 1.0 + lambda_ * lambda_;
        final double q3 = lambda_ * n + N + lambda_ * lambda_ * N;
        final double vol = (x1 > 0.0)
                ? Math.sqrt(x1)
                : (discretization_ == Discretization.Reflection
                        ? -Math.sqrt(-x1)
                        : 0.0);

        final double[] result = new double[2];
        result[0] = riskFreeRate_.currentLink().forwardRate(t, t, Compounding.Continuous).rate()
                - dividendYield_.currentLink().forwardRate(t, t, Compounding.Continuous).rate()
                - 0.5 * vol * vol;
        final double v = (discretization_ == Discretization.PartialTruncation) ? x1 : vol * vol;
        result[1] = daysPerYear_ * daysPerYear_ * omega_
                + daysPerYear_ * (beta_ + alpha_ * q2 + gamma_ * q3 - 1.0) * v;
        return new Array(result);
    }

    @Override
    public Matrix diffusion(final double t, final Array x) {
        // Correlation matrix
        //   |  1   rho |
        //   | rho   1  |
        // whose square root (used here) is
        //   |  1                0       |
        //   | rho   sqrt(1-rho^2)       |
        final double x1 = x.get(1);
        final double N = new CumulativeNormalDistribution().op(lambda_);
        final double n = Math.exp(-lambda_ * lambda_ / 2.0) / Math.sqrt(2.0 * Constants.M_PI);
        final double sigma2 = 2.0 + 4.0 * lambda_ * lambda_;
        final double q3 = lambda_ * n + N + lambda_ * lambda_ * N;
        final double Eml_e4 = lambda_ * lambda_ * lambda_ * n + 5.0 * lambda_ * n
                + 3.0 * N + lambda_ * lambda_ * lambda_ * lambda_ * N
                + 6.0 * lambda_ * lambda_ * N;
        final double sigma3 = Eml_e4 - q3 * q3;
        final double sigma12 = -2.0 * lambda_;
        final double sigma13 = -2.0 * n - 2.0 * lambda_ * N;
        final double sigma23 = 2.0 * N + sigma12 * sigma13;
        final double vol = (x1 > 0.0)
                ? Math.sqrt(x1)
                : (discretization_ == Discretization.Reflection
                        ? -Math.sqrt(-x1)
                        : 1e-8); // set vol to (almost) zero but still
                                 // expose some correlation information
        final double rho1 = Math.sqrt(daysPerYear_)
                * (alpha_ * sigma12 + gamma_ * sigma13) * vol * vol;
        final double rho2 = vol * vol * Math.sqrt(daysPerYear_)
                * Math.sqrt(alpha_ * alpha_ * (sigma2 - sigma12 * sigma12)
                        + gamma_ * gamma_ * (sigma3 - sigma13 * sigma13)
                        + 2.0 * alpha_ * gamma_ * (sigma23 - sigma12 * sigma13));

        final Matrix tmp = new Matrix(2, 2);
        tmp.set(0, 0, vol);
        tmp.set(0, 1, 0.0);
        tmp.set(1, 0, rho1);
        tmp.set(1, 1, rho2);
        return tmp;
    }

    @Override
    public Array apply(final Array x0, final Array dx) {
        return new Array(new double[] { x0.get(0) * Math.exp(dx.get(0)), x0.get(1) + dx.get(1) });
    }

    @Override
    public Array evolve(final double t0, final Array x0, final double dt, final Array dw) {
        final double[] retVal = new double[2];
        double vol;
        double mu;
        double nu;

        final double sdt = Math.sqrt(dt);
        final double N = new CumulativeNormalDistribution().op(lambda_);
        final double n = Math.exp(-lambda_ * lambda_ / 2.0) / Math.sqrt(2.0 * Constants.M_PI);
        final double sigma2 = 2.0 + 4.0 * lambda_ * lambda_;
        final double q2 = 1.0 + lambda_ * lambda_;
        final double q3 = lambda_ * n + N + lambda_ * lambda_ * N;
        final double Eml_e4 = lambda_ * lambda_ * lambda_ * n + 5.0 * lambda_ * n
                + 3.0 * N + lambda_ * lambda_ * lambda_ * lambda_ * N
                + 6.0 * lambda_ * lambda_ * N;
        final double sigma3 = Eml_e4 - q3 * q3;
        final double sigma12 = -2.0 * lambda_;
        final double sigma13 = -2.0 * n - 2.0 * lambda_ * N;
        final double sigma23 = 2.0 * N + sigma12 * sigma13;
        final double rho1 = Math.sqrt(daysPerYear_) * (alpha_ * sigma12 + gamma_ * sigma13);
        final double rho2 = Math.sqrt(daysPerYear_)
                * Math.sqrt(alpha_ * alpha_ * (sigma2 - sigma12 * sigma12)
                        + gamma_ * gamma_ * (sigma3 - sigma13 * sigma13)
                        + 2.0 * alpha_ * gamma_ * (sigma23 - sigma12 * sigma13));

        final double x00 = x0.get(0);
        final double x01 = x0.get(1);
        final double dw0 = dw.get(0);
        final double dw1 = dw.get(1);

        switch (discretization_) {
        case PartialTruncation:
            vol = (x01 > 0.0) ? Math.sqrt(x01) : 0.0;
            mu = riskFreeRate_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                    - dividendYield_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                    - 0.5 * vol * vol;
            nu = daysPerYear_ * daysPerYear_ * omega_
                    + daysPerYear_ * (beta_ + alpha_ * q2 + gamma_ * q3 - 1.0) * x01;

            retVal[0] = x00 * Math.exp(mu * dt + vol * dw0 * sdt);
            retVal[1] = x01 + nu * dt + sdt * vol * vol * (rho1 * dw0 + rho2 * dw1);
            break;
        case FullTruncation:
            vol = (x01 > 0.0) ? Math.sqrt(x01) : 0.0;
            mu = riskFreeRate_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                    - dividendYield_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                    - 0.5 * vol * vol;
            nu = daysPerYear_ * daysPerYear_ * omega_
                    + daysPerYear_ * (beta_ + alpha_ * q2 + gamma_ * q3 - 1.0) * vol * vol;

            retVal[0] = x00 * Math.exp(mu * dt + vol * dw0 * sdt);
            retVal[1] = x01 + nu * dt + sdt * vol * vol * (rho1 * dw0 + rho2 * dw1);
            break;
        case Reflection:
            vol = Math.sqrt(Math.abs(x01));
            mu = riskFreeRate_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                    - dividendYield_.currentLink().forwardRate(t0, t0 + dt, Compounding.Continuous).rate()
                    - 0.5 * vol * vol;
            nu = daysPerYear_ * daysPerYear_ * omega_
                    + daysPerYear_ * (beta_ + alpha_ * q2 + gamma_ * q3 - 1.0) * vol * vol;

            retVal[0] = x00 * Math.exp(mu * dt + vol * dw0 * sdt);
            retVal[1] = vol * vol + nu * dt + sdt * vol * vol * (rho1 * dw0 + rho2 * dw1);
            break;
        default:
            throw new LibraryException("unknown discretization schema");
        }
        return new Array(retVal);
    }

    @Override
    public double time(final Date d) {
        return riskFreeRate_.currentLink().dayCounter().yearFraction(
                riskFreeRate_.currentLink().referenceDate(), d);
    }
}
