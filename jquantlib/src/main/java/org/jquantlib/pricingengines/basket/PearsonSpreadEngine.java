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

package org.jquantlib.pricingengines.basket;

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.math.integrals.GaussLobattoIntegral;
import org.jquantlib.pricingengines.BlackCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Spread-option engine following Pearson (1995), "An Efficient Approach for Pricing Spread Options", Journal of
 * Derivatives 3, 76-91.
 * <p>
 * The two-dimensional expectation is reduced to a one-dimensional integral over the second asset's Brownian factor;
 * conditional on it, the first asset is lognormal and the payoff is priced in closed form by Black.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/pricingengines/basket/pearsonspreadengine.{hpp,cpp}} — new in that release.
 *
 * @author Jose Moya
 * @category basketengines
 */
public class PearsonSpreadEngine extends SpreadBlackScholesVanillaEngine {

    private final double integrationTolerance;
    private final int maxIntegrationIterations;
    private final double nStd;

    public PearsonSpreadEngine(final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2, final double correlation) {
        this(process1, process2, correlation, 1.0e-10, 10000, 8.0);
    }

    /**
     * @param integrationTolerance     absolute accuracy demanded of the Gauss-Lobatto quadrature
     * @param maxIntegrationIterations iteration cap for that quadrature
     * @param nStd                     half-width of the integration range, in standard deviations of the second
     *                                 asset's driving Brownian motion
     */
    public PearsonSpreadEngine(final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2, final double correlation,
            final double integrationTolerance, final int maxIntegrationIterations, final double nStd) {
        super(process1, process2, correlation);
        this.integrationTolerance = integrationTolerance;
        this.maxIntegrationIterations = maxIntegrationIterations;
        this.nStd = nStd;
    }

    @Override
    protected double calculateSpread(final double f1, final double f2, final double strike,
            final Option.Type optionType, final double variance1, final double variance2, final double df) {
        final double sigma1 = Math.sqrt(variance1);
        final double sigma2 = Math.sqrt(variance2);
        final double sigma1Cond = sigma1 * Math.sqrt(Math.max(1.0 - rho * rho, 0.0));
        final NormalDistribution phi = new NormalDistribution();

        final Ops.DoubleOp integrand = z -> {
            final double f2z = f2 * Math.exp(-0.5 * variance2 + sigma2 * z);
            final double effectiveStrike = f2z + strike;

            if ( effectiveStrike <= 0.0 ) {
                /*
                 * Reproduced exactly as in C++ v1.43, including the fact that this branch returns the *call*
                 * intrinsic regardless of optionType. For a sufficiently negative strike a put therefore picks up a
                 * non-zero value here where zero would be correct, and put-call parity breaks. This is upstream
                 * behaviour, not a porting slip; deviating from it would make the two libraries disagree.
                 */
                return phi.op(z) * Math.max(0.0,
                        f1 * Math.exp(rho * sigma1 * z - 0.5 * rho * rho * variance1) - effectiveStrike);
            }

            final double f1Cond = f1 * Math.exp(rho * sigma1 * z - 0.5 * rho * rho * variance1);
            final BlackCalculator black = new BlackCalculator(new PlainVanillaPayoff(optionType, effectiveStrike),
                    f1Cond, sigma1Cond, 1.0);
            return phi.op(z) * black.value();
        };

        final GaussLobattoIntegral integrator = new GaussLobattoIntegral(maxIntegrationIterations,
                integrationTolerance);
        final double undiscounted = integrator.op(integrand, -nStd, nStd);
        return df * undiscounted;
    }
}
