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

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.BasketOption;
import org.jquantlib.instruments.MultiAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.instruments.SpreadBasketPayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.integrals.GaussHermiteIntegration;
import org.jquantlib.methods.finitedifferences.utilities.SmileSectionRNDCalculator;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.volatilities.AtmSmileSection;
import org.jquantlib.termstructures.volatilities.SmileSection;
import org.jquantlib.time.Date;

/**
 * Spread-option engine using nested Gauss-Hermite quadrature over a Gaussian copula with smile-implied marginals.
 * <p>
 * Each asset's terminal distribution is taken from its own smile via the Breeden-Litzenberger identity
 * ({@link SmileSectionRNDCalculator}), and the two are coupled by a bivariate normal copula with the given
 * correlation. That decouples the marginals from the dependence structure, so each leg keeps its full smile rather
 * than a single effective volatility.
 * <p>
 * Ported from C++ QuantLib v1.43 {@code ql/pricingengines/basket/gaussiancopulaspreadengine.{hpp,cpp}} — new in that
 * release.
 *
 * @author Jose Moya
 * @category basketengines
 */
public class GaussianCopulaSpreadEngine extends BasketOption.Engine {

    private static final double SQRT2 = Math.sqrt(2.0);

    private final GeneralizedBlackScholesProcess process1;
    private final GeneralizedBlackScholesProcess process2;
    private final double rho;
    private final int nPoints;

    public GaussianCopulaSpreadEngine(final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2, final double correlation) {
        this(process1, process2, correlation, 64);
    }

    /**
     * @param correlation copula correlation; must lie in [-1, 1]
     * @param nPoints     order of the Gauss-Hermite rule used on each axis, so the work is quadratic in it
     */
    public GaussianCopulaSpreadEngine(final GeneralizedBlackScholesProcess process1,
            final GeneralizedBlackScholesProcess process2, final double correlation, final int nPoints) {
        QL.require(correlation >= -1.0 && correlation <= 1.0, "correlation must be in [-1, 1], got " + correlation);
        // Identity, not equality: C++ compares the two handles' current links by pointer, so two curves that merely
        // hold the same numbers are rejected. The payoff is discounted on process1's curve, so sharing it is the
        // only way the result is well defined.
        QL.require(process1.riskFreeRate().currentLink() == process2.riskFreeRate().currentLink(),
                "process1 and process2 must share the risk-free term structure "
                        + "(used for discounting the spread payoff)");
        this.process1 = process1;
        this.process2 = process2;
        this.rho = correlation;
        this.nPoints = nPoints;
        this.process1.addObserver(this);
        this.process2.addObserver(this);
    }

    @Override
    public void calculate() {
        final MultiAssetOption.ArgumentsImpl a = arguments_;
        final MultiAssetOption.ResultsImpl r = results_;

        QL.require(a.exercise.type() == Exercise.Type.European, "not a European exercise");
        QL.require(a.payoff instanceof SpreadBasketPayoff, "spread payoff expected");
        final SpreadBasketPayoff spreadPayoff = (SpreadBasketPayoff) a.payoff;
        QL.require(spreadPayoff.basePayoff() instanceof PlainVanillaPayoff, "non-plain payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) spreadPayoff.basePayoff();

        final Date maturityDate = a.exercise.lastDate();

        final double fwd1 = process1.stateVariable().currentLink().value()
                * process1.dividendYield().currentLink().discount(maturityDate)
                / process1.riskFreeRate().currentLink().discount(maturityDate);
        final double fwd2 = process2.stateVariable().currentLink().value()
                * process2.dividendYield().currentLink().discount(maturityDate)
                / process2.riskFreeRate().currentLink().discount(maturityDate);

        final double df = process1.riskFreeRate().currentLink().discount(maturityDate);

        final double t1 = process1.blackVolatility().currentLink().timeFromReference(maturityDate);
        final double t2 = process2.blackVolatility().currentLink().timeFromReference(maturityDate);

        // AtmSmileSection re-anchors each smile at the engine's own forward, overriding whatever ATM level the
        // underlying section reports.
        final SmileSection smile1 = new AtmSmileSection(
                process1.blackVolatility().currentLink().smileSection(t1), fwd1);
        final SmileSection smile2 = new AtmSmileSection(
                process2.blackVolatility().currentLink().smileSection(t2), fwd2);

        final SmileSectionRNDCalculator rnd1 = new SmileSectionRNDCalculator(smile1);
        final SmileSectionRNDCalculator rnd2 = new SmileSectionRNDCalculator(smile2);

        final GaussHermiteIntegration gh = new GaussHermiteIntegration(nPoints);
        final CumulativeNormalDistribution phi = new CumulativeNormalDistribution();

        final double rhoComp = Math.sqrt(Math.max(1.0 - rho * rho, 0.0));

        /*
         * QuantLib's GaussianQuadrature divides out the weight function (w_i = mu_0 * ev[0][i]^2 / w(x_i)), so
         * sum_i w_i f(x_i) approximates the *unweighted* integral. The exp(-x^2) factors below therefore have to be
         * reinstated explicitly, and the 1/pi normalises the resulting double Gaussian integral. Using classical
         * Gauss-Hermite weights instead would be wrong by a factor of exp(x^2) at every node.
         */
        final double normFactor = 1.0 / Math.PI;

        double sum = 0.0;
        for ( int i = 0; i < gh.order(); ++i ) {
            final double xi = gh.x(i);
            final double z1 = SQRT2 * xi;
            final double u1 = Math.min(Math.max(phi.op(z1), Constants.QL_EPSILON), 1.0 - Constants.QL_EPSILON);
            final double s1 = Math.exp(rnd1.invcdf(u1));
            final double expX1sq = Math.exp(-xi * xi);

            double innerSum = 0.0;
            for ( int j = 0; j < gh.order(); ++j ) {
                final double xj = gh.x(j);
                final double z2perp = SQRT2 * xj;
                final double z2 = rho * z1 + rhoComp * z2perp;
                final double u2 = Math.min(Math.max(phi.op(z2), Constants.QL_EPSILON), 1.0 - Constants.QL_EPSILON);
                final double s2 = Math.exp(rnd2.invcdf(u2));

                final double payoffVal = payoff.get(s1 - s2);
                final double kernel = expX1sq * Math.exp(-xj * xj);
                innerSum += gh.weight(j) * kernel * payoffVal;
            }
            sum += gh.weight(i) * innerSum;
        }

        r.value = df * normFactor * sum;
    }
}
