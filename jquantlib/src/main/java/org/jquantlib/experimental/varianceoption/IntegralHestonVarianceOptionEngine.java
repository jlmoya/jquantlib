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
 Copyright (C) 2008 Lorella Fatone
 Copyright (C) 2008 Francesca Mariani
 Copyright (C) 2008 Maria Cristina Recchioni
 Copyright (C) 2008 Francesco Zirilli
 Copyright (C) 2008 StatPro Italia srl

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

package org.jquantlib.experimental.varianceoption;

import org.jquantlib.QL;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Complex;
import org.jquantlib.math.Constants;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.Compounding;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Integral Heston-model variance-option engine.
 *
 * <p>Phase 4a A.2 port of {@code QuantLib::IntegralHestonVarianceOptionEngine}
 * (v1.42.1 ql/experimental/varianceoption/integralhestonvarianceoptionengine.{hpp,cpp}).
 *
 * <p>The pricer evaluates the realised-variance option price via a
 * Bailey-Swarztrauber DFT-based oscillatory integral, as described in Recchioni et al., "An explicitly solvable Heston
 * model with stochastic interest rate" (2008). The 1-D specialisation is used for plain-vanilla call payoffs; a 2-D
 * variant handles arbitrary payoff functions.
 *
 * <p>This is a structural port: complex arithmetic uses {@link Complex},
 * Java's bit-faithfulness caveat (within-1-ULP {@code Math.exp/sin/cos}) applies — see {@link Complex} javadoc. Smoke
 * tests use loose tolerance.
 *
 * @category pricingengines
 */
public class IntegralHestonVarianceOptionEngine extends VarianceOption.EngineImpl {

    private static final double PI = 3.14159265358979324;
    // Suppress unused-import warning: Constants is imported via static field
    @SuppressWarnings( "unused" )
    private static final double SUPPRESS_UNUSED = Constants.NULL_REAL;
    private final HestonProcess process_;

    public IntegralHestonVarianceOptionEngine(final HestonProcess process) {
        this.process_ = process;
        process_.addObserver(this);
    }

    @Override
    public void calculate() /*@ReadOnly*/ {

        // v1.43 made this explicit rather than leaving it to the caller. The engine's analytic formula has no
        // dividend term at all, so a non-empty dividend curve would be silently ignored — which is exactly the kind
        // of quiet wrong answer worth refusing. Mirrors C++
        // {@code IntegralHestonVarianceOptionEngine::calculate}
        // ({@code ql/experimental/varianceoption/integralhestonvarianceoptionengine.cpp:366}).
        QL.require(process_.dividendYield().empty(), "this engine does not manage dividend yields");


        final Handle< YieldTermStructure > riskFreeRate = process_.riskFreeRate();
        final DayCounter dc = riskFreeRate.currentLink().dayCounter();

        final double epsilon = process_.sigma().currentLink().value();
        final double chi = process_.kappa().currentLink().value();
        final double theta = process_.theta().currentLink().value();
        // rho is unused in the current C++ implementation (commented stub).
        // process_.rho() retained for parity but not consumed:
        // (Real rho = process_->rho(); — unused inside IvopOneDim/IvopTwoDim.)
        final double v0 = process_.v0().currentLink().value();

        final Date evalDate = new Settings().evaluationDate();
        final /*@Time*/ double tau = dc.yearFraction(evalDate, arguments_.maturityDate);

        final double r = riskFreeRate.currentLink().zeroRate(arguments_.maturityDate, dc, Compounding.Continuous)
                .rate();

        final Payoff payoff = arguments_.payoff;
        if (payoff instanceof PlainVanillaPayoff p) {
            if ( p.optionType() == Option.Type.Call ) {
                results_.value = ivopOneDim(epsilon, chi, theta, v0, p.strike(), tau, r) * arguments_.notional;
                return;
            }
        }
        results_.value = ivopTwoDim(epsilon, chi, theta, v0, tau, r, payoff) * arguments_.notional;
    }

    /**
     * 1-D specialisation for plain-vanilla call payoffs (eq. (4) in the reference paper).
     *
     * @param eps    Heston volatility-of-variance
     * @param chi    Heston mean-reversion speed (kappa)
     * @param theta  Heston long-run variance
     * @param v0     initial variance
     * @param eprice realised-variance strike
     * @param tau    time to maturity (years)
     * @param rtax   continuously-compounded risk-free rate
     * @return call price (ex-notional)
     */
    private double ivopOneDim(final double eps, final double chi, final double theta, final double v0,
            final double eprice, final /*@Time*/ double tau, final double rtax) {

        final double pi2 = 2.0 * PI;
        final double s = 2.0 * chi * theta / (eps * eps) - 1.0;
        QL.require(s > 0.0, "this parameter must be greater than zero-> " + s);
        final double ss = s + 1.0;

        final double dstep = 256.0;
        final double nris = Math.sqrt(pi2) / dstep;
        final int mm = (int) (pi2 / (nris * nris));

        final double[] xiv = new double[mm + 1];
        final Complex[] ff = new Complex[mm];

        for ( int j = 0; j < mm; j++ ) {
            xiv[j + 1] = (j - mm / 2.0) * nris;
        }

        final Complex ui = new Complex(0.0, 1.0);
        final double i0 = 0.0;

        for ( int j = 0; j < mm; j++ ) {
            final Complex xi = new Complex(xiv[j + 1], 0.0);

            // caux = chi*chi
            // caux1 = 2.0*eps*eps * xi * ui
            // caux2 = caux1 + caux
            Complex caux = new Complex(chi * chi, 0.0);
            Complex caux1 = xi.mul(2.0 * eps * eps).mul(ui);
            final Complex caux2 = caux1.add(caux);

            final Complex zita = caux2.sqrt().mul(0.5);

            caux1 = zita.mul(-2.0 * tau).exp();

            // beta = 0.5*chi + zita + caux1*(zita - 0.5*chi)
            Complex beta = zita.add(0.5 * chi);
            beta = beta.add(caux1.mul(zita.sub(0.5 * chi)));

            final Complex gamma = new Complex(1.0, 0.0).sub(caux1);

            // caux = -ss*tau (real)
            // caux2 = -ss*tau * (zita - 0.5*chi)
            // caux = ss * log(2 * (zita/beta))
            final Complex c1 = zita.div(beta).mul(2.0).log().mul(ss);
            final Complex c2 = zita.sub(0.5 * chi).mul(-ss * tau);
            final Complex c3 = ui.mul(-v0).mul(xi).mul(gamma.div(beta));
            final Complex sum = c1.add(c2).add(c3);

            Complex contrib;
            final double absxi = Math.hypot(xi.imag(), xi.real());
            if ( absxi > 1.0e-06 ) {
                // contrib = -eprice/(ui*xi) + (exp(ui*xi*eprice)-1)/((ui*xi)^2)
                final Complex ux = ui.mul(xi);
                contrib = ux.mul(-1.0).pow(1.0); // placeholder — overwritten below
                // Recompute -eprice/(ui*xi):
                final Complex t1 = new Complex(-eprice, 0.0).div(ux);
                final Complex t2 = ux.mul(eprice).exp().sub(1.0).div(ux.mul(ux));
                contrib = t1.add(t2);
            } else {
                contrib = new Complex(eprice * eprice * 0.5, 0.0);
            }
            ff[j] = sum.exp().mul(contrib);
        }

        Complex csum = Complex.ZERO;
        for ( int j = 0; j < mm; j++ ) {
            // caux = pow(-1.0, j) (real ±1)
            final double sign = ((j & 1) == 0) ? 1.0 : -1.0;
            // caux2 = -2*pi*mm*j*0.5/mm = -pi*j (real)
            final double phase = -PI * j;
            final Complex term = new Complex(Math.cos(phase), Math.sin(phase));
            csum = csum.add(ff[j].mul(sign).mul(term));
        }
        // csum *= sqrt(pow(-1, mm)) * nris/pi2
        final double mmSign = ((mm & 1) == 0) ? 1.0 : -1.0;
        Complex mmRoot;
        if ( mmSign >= 0.0 ) {
            mmRoot = new Complex(1.0, 0.0);
        } else {
            // sqrt(-1) = i in std::complex principal branch
            mmRoot = new Complex(0.0, 1.0);
        }
        csum = csum.mul(mmRoot).mul(nris / pi2);

        // vero = i0 - eprice + theta*tau + (1 - exp(-chi*tau))*(v0 - theta)/chi
        final double vero = i0 - eprice + theta * tau + (1.0 - Math.exp(-chi * tau)) * (v0 - theta) / chi;
        csum = csum.add(vero);

        final double option = Math.exp(-rtax * tau) * csum.real();
        final double impart = csum.imag();
        QL.ensure(impart <= 1.0e-12, "imaginary part option (must be zero) = " + impart);
        return option;
    }

    /**
     * 2-D variant for arbitrary payoffs (eq. handling generic g(I_T)).
     */
    private double ivopTwoDim(final double eps, final double chi, final double theta, final double v0,
            final /*@Time*/ double tau, final double rtax, final Payoff payoff) {

        final double pi2 = 2.0 * PI;
        final double s = 2.0 * chi * theta / (eps * eps) - 1.0;
        QL.require(s > 0.0, "this parameter must be greater than zero-> " + s);
        final double ss = s + 1.0;

        final double dstep = 64.0;
        final double nris = Math.sqrt(pi2) / dstep;
        final int mm = (int) (pi2 / (nris * nris));

        final double[] xiv = new double[mm + 1];
        final double[] ivet = new double[mm + 1];
        final Complex[] ff = new Complex[mm];

        for ( int j = 0; j < mm; j++ ) {
            xiv[j + 1] = (j - mm / 2.0) * nris;
            ivet[j + 1] = (j - mm / 2.0) * pi2 / (mm * nris);
        }

        final Complex ui = new Complex(0.0, 1.0);
        final double i0 = 0.0;

        for ( int j = 0; j < mm; j++ ) {
            final Complex xi = new Complex(xiv[j + 1], 0.0);
            final Complex caux = new Complex(chi * chi, 0.0);
            final Complex caux1Step = xi.mul(2.0 * eps * eps).mul(ui);
            final Complex caux2 = caux1Step.add(caux);
            final Complex zita = caux2.sqrt().mul(0.5);
            final Complex caux1 = zita.mul(-2.0 * tau).exp();

            Complex beta = zita.add(0.5 * chi);
            beta = beta.add(caux1.mul(zita.sub(0.5 * chi)));
            final Complex gamma = new Complex(1.0, 0.0).sub(caux1);

            final Complex c1 = zita.div(beta).mul(2.0).log().mul(ss);
            final Complex c2 = zita.sub(0.5 * chi).mul(-ss * tau);
            final Complex c3 = ui.mul(-v0).mul(xi).mul(gamma.div(beta));
            ff[j] = c1.add(c2).add(c3).exp();
        }

        double sumr = 0.0;
        for ( int k = 0; k < mm; k++ ) {
            final double ip = i0 - ivet[k + 1];
            final double payoffval = payoff.get(ip);

            final Complex dxi = ui.mul(2.0 * PI * k / mm);
            Complex csum = Complex.ZERO;
            for ( int j = 0; j < mm; j++ ) {
                final Complex z = dxi.mul(-(double) j);
                final double sign = ((j & 1) == 0) ? 1.0 : -1.0;
                csum = csum.add(ff[j].mul(sign).mul(z.exp()));
            }
            final double kSign = ((k & 1) == 0) ? 1.0 : -1.0;
            csum = csum.mul(kSign).mul(nris / pi2);

            sumr += payoffval * csum.real();
        }
        sumr *= nris;

        return Math.exp(-rtax * tau) * sumr;
    }
}
