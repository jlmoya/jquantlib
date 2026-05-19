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

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.math.Complex;
import org.jquantlib.time.Date;

/**
 * FFT engine for vanilla options under a Variance Gamma process.
 *
 * <p>Phase 5e.5b-CFC-d-230 port of
 * {@code QuantLib::FFTVarianceGammaEngine} (v1.42.1 ql/experimental/variancegamma/fftvariancegammaengine.{hpp,cpp}).
 *
 * <p>Implements the Carr-Madan characteristic function of the Variance
 * Gamma process and plugs it into {@link FFTEngine}. The correctness of the returned values is tested by comparison
 * with known good values and with the analytic {@link VarianceGammaEngine}.
 *
 * @see FFTEngine
 * @see VarianceGammaEngine
 */
public class FFTVarianceGammaEngine extends FFTEngine {

    private final VarianceGammaProcess vgProcess_;

    // Cached per-expiry parameters (populated by precalculateExpiry).
    private double dividendDiscount_;
    private double riskFreeDiscount_;
    private double t_;
    private double sigma_;
    private double nu_;
    private double theta_;

    public FFTVarianceGammaEngine(final VarianceGammaProcess process) {
        this(process, 0.001);
    }

    public FFTVarianceGammaEngine(final VarianceGammaProcess process, final double logStrikeSpacing) {
        super(process, logStrikeSpacing);
        this.vgProcess_ = process;
    }

    @Override
    public FFTEngine clone1() {
        return new FFTVarianceGammaEngine(vgProcess_, lambda_);
    }

    @Override
    protected void precalculateExpiry(final Date d) {
        dividendDiscount_ = vgProcess_.dividendYield().currentLink().discount(d);
        riskFreeDiscount_ = vgProcess_.riskFreeRate().currentLink().discount(d);
        final DayCounter rfdc = vgProcess_.riskFreeRate().currentLink().dayCounter();
        t_ = rfdc.yearFraction(vgProcess_.riskFreeRate().currentLink().referenceDate(), d);
        sigma_ = vgProcess_.sigma();
        nu_ = vgProcess_.nu();
        theta_ = vgProcess_.theta();
    }

    @Override
    protected Complex complexFourierTransform(final Complex u) {
        final double s = vgProcess_.x0();
        final Complex i1 = Complex.I;

        // omega = log(1 - theta*nu - sigma^2*nu/2) / nu
        final double omega = Math.log(1.0 - theta_ * nu_ - sigma_ * sigma_ * nu_ / 2.0) / nu_;

        // phi = exp(i*u * (log(s) + omega*t))
        //         * (divDF / rfDF)^(i*u)
        //         * (1 - i*theta*nu*u + sigma^2*nu*u^2/2)^(-t/nu)
        final Complex iu = i1.mul(u);
        Complex phi = iu.mul(Math.log(s) + omega * t_).exp();
        phi = phi.mul(Complex.real(dividendDiscount_ / riskFreeDiscount_).pow(iu));

        final Complex uSq = u.mul(u);
        final Complex term = Complex.ONE.sub(i1.mul(theta_ * nu_).mul(u)).add(uSq.mul(sigma_ * sigma_ * nu_ / 2.0));
        phi = phi.mul(term.pow(-t_ / nu_));

        return phi;
    }

    @Override
    protected double discountFactor(final Date d) {
        return vgProcess_.riskFreeRate().currentLink().discount(d);
    }

    @Override
    protected double dividendYield(final Date d) {
        return vgProcess_.dividendYield().currentLink().discount(d);
    }
}
