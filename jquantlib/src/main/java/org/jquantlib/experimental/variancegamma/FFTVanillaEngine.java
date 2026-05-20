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
*/

package org.jquantlib.experimental.variancegamma;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Complex;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.time.Date;

/**
 * FFT pricing engine for vanilla options under a Black-Scholes process.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/variancegamma/fftvanillaengine.{hpp,cpp}}
 * (Phase1-closure-A2-E-552-fft).
 *
 * <p>The characteristic function of the log-spot under Black-Scholes is
 * <pre>
 *   phi(u) = exp( i*u*(log(s) - 0.5*var*t) - 0.5*var*u^2*t )
 *          * (div_disc / rf_disc)^(i*u)
 * </pre>
 * with {@code var = vol^2} where {@code vol} is the at-the-money flat
 * volatility (Black-Scholes assumption: constant vol).
 *
 * <p>Correctness is tested via cross-validation against the closed-form
 * Black-Scholes price returned by {@code AnalyticEuropeanEngine}.
 *
 * @see FFTEngine
 */
public class FFTVanillaEngine extends FFTEngine {

    private final GeneralizedBlackScholesProcess bsProcess_;

    // Cached per-expiry parameters (populated by precalculateExpiry).
    private double dividendDiscount_;
    private double riskFreeDiscount_;
    private double t_;
    private double var_;

    public FFTVanillaEngine(final GeneralizedBlackScholesProcess process) {
        this(process, 0.001);
    }

    public FFTVanillaEngine(final GeneralizedBlackScholesProcess process, final double logStrikeSpacing) {
        super(process, logStrikeSpacing);
        this.bsProcess_ = process;
    }

    @Override
    public FFTEngine clone1() {
        return new FFTVanillaEngine(bsProcess_, lambda_);
    }

    @Override
    protected void precalculateExpiry(final Date d) {
        dividendDiscount_ = bsProcess_.dividendYield().currentLink().discount(d);
        riskFreeDiscount_ = bsProcess_.riskFreeRate().currentLink().discount(d);
        final DayCounter rfdc = bsProcess_.riskFreeRate().currentLink().dayCounter();
        t_ = rfdc.yearFraction(bsProcess_.riskFreeRate().currentLink().referenceDate(), d);
        // Mirror C++: dynamic_cast<BlackConstantVol> — constant volatility is
        // a hard requirement of this engine.
        QL.require(bsProcess_.blackVolatility().currentLink() instanceof BlackConstantVol,
                "Constant volatility required");
        final BlackConstantVol constVol = (BlackConstantVol) bsProcess_.blackVolatility().currentLink();
        final double vol = constVol.blackVol(0.0, 0.0);
        var_ = vol * vol;
    }

    @Override
    protected Complex complexFourierTransform(final Complex u) {
        final double s = bsProcess_.x0();
        final Complex i1 = Complex.I;

        // phi = exp(i*u * (log(s) - var*t/2) - var*u^2*t/2)
        // The C++ writes this as exp(i1*u*(log(s) - var*t/2) - var*u*u*t/2),
        // taking advantage of the implicit lifting of `Real` into `complex`.
        final Complex iu = i1.mul(u);
        final Complex term1 = iu.mul(Math.log(s) - var_ * t_ / 2.0);
        final Complex term2 = u.mul(u).mul(var_ * t_ / 2.0);
        Complex phi = term1.sub(term2).exp();
        phi = phi.mul(Complex.real(dividendDiscount_ / riskFreeDiscount_).pow(iu));
        return phi;
    }

    @Override
    protected double discountFactor(final Date d) {
        return bsProcess_.riskFreeRate().currentLink().discount(d);
    }

    @Override
    protected double dividendYield(final Date d) {
        return bsProcess_.dividendYield().currentLink().discount(d);
    }
}
