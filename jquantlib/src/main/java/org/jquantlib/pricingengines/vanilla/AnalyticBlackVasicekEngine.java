/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Lew Wei Hao
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.integrals.SimpsonIntegral;
import org.jquantlib.model.shortrate.onefactormodels.Vasicek;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Analytic pricing engine for European vanilla options under stochastic Vasicek short-rate dynamics.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/pricingengines/vanilla/analyticeuropeanvasicekengine.{hpp,cpp}} (Phase 2 L3-D). The closed form combines a
 * Black-Scholes asset process with a Vasicek short-rate process via the correlation parameter; see
 * <a href="http://hsrm-mathematik.de/WS201516/master/option-pricing/Black-Scholes-Vasicek-Model.pdf">
 * Black-Scholes-Vasicek hybrid model</a>.
 *
 * <p>Mirrors C++ class name change history — the C++ file is named
 * {@code analyticeuropeanvasicekengine} but the class is
 * {@code AnalyticBlackVasicekEngine}. The Java port uses the class-name spelling.
 */
public class AnalyticBlackVasicekEngine extends OneAssetOption.EngineImpl {

    private static final double SIMPSON_ACCURACY = 1e-5;
    private static final int SIMPSON_MAX_ITERATIONS = 1000;

    private final GeneralizedBlackScholesProcess blackProcess_;
    private final Vasicek vasicekProcess_;
    private final double correlation_;
    private final SimpsonIntegral simpsonIntegral_;

    public AnalyticBlackVasicekEngine(final GeneralizedBlackScholesProcess blackProcess, final Vasicek vasicekProcess,
            final double correlation) {
        super();
        QL.require(blackProcess != null, "null Black-Scholes process");
        QL.require(vasicekProcess != null, "null Vasicek process");
        this.blackProcess_ = blackProcess;
        this.vasicekProcess_ = vasicekProcess;
        this.correlation_ = correlation;
        this.simpsonIntegral_ = new SimpsonIntegral(SIMPSON_ACCURACY, SIMPSON_MAX_ITERATIONS);
        this.blackProcess_.addObserver(this);
        this.vasicekProcess_.addObserver(this);
    }

    @Override
    public void calculate() /* @ReadOnly */ {
        final OneAssetOption.ArgumentsImpl a = (OneAssetOption.ArgumentsImpl) arguments_;
        final OneAssetOption.ResultsImpl r = (OneAssetOption.ResultsImpl) results_;

        QL.require(a.exercise.type() == Exercise.Type.European, "not an European option");

        final StrikedTypePayoff payoff;
        try {
            payoff = (StrikedTypePayoff) a.payoff;
        } catch ( final ClassCastException e ) {
            throw new RuntimeException("non-striked payoff given");
        }
        QL.require(payoff != null, "non-striked payoff given");

        final CumulativeNormalDistribution f = new CumulativeNormalDistribution();
        final double t = 0.0;
        final YieldTermStructure rfTs = blackProcess_.riskFreeRate().currentLink();
        final DayCounter rfDc = rfTs.dayCounter();
        final double T = rfDc.yearFraction(rfTs.referenceDate(), a.exercise.lastDate());

        final double kappa = vasicekProcess_.a();
        final double S_t = blackProcess_.x0();
        final double K = payoff.strike();
        final double sigma_s = blackProcess_.blackVolatility().currentLink().blackVol(t, K);
        final double sigma_r = vasicekProcess_.sigma();
        final double r_t = vasicekProcess_.r0();

        final double zcb = vasicekProcess_.discountBond(t, T, r_t);
        final double epsilon = (payoff.optionType() == Option.Type.Call) ? 1.0 : -1.0;
        final double upsilon = simpsonIntegral_.op(new VasicekIntegrand(sigma_s, sigma_r, correlation_, kappa, T),
                t, T);
        final double d_positive = (Math.log((S_t / K) / zcb) + upsilon / 2.0) / Math.sqrt(upsilon);
        final double d_negative = (Math.log((S_t / K) / zcb) - upsilon / 2.0) / Math.sqrt(upsilon);
        final double n_d1 = f.op(epsilon * d_positive);
        final double n_d2 = f.op(epsilon * d_negative);

        r.value = epsilon * ((S_t * n_d1) - (zcb * K * n_d2));
    }

    private static double g_k(final double t, final double kappa) {
        return (1.0 - Math.exp(-kappa * t)) / kappa;
    }

    /** Inner Simpson integrand. Mirrors C++ anonymous-namespace {@code integrand_vasicek}. */
    private static final class VasicekIntegrand implements Ops.DoubleOp {
        private final double sigma_s_;
        private final double sigma_r_;
        private final double correlation_;
        private final double kappa_;
        private final double T_;

        VasicekIntegrand(final double sigma_s, final double sigma_r, final double correlation, final double kappa,
                final double T) {
            this.sigma_s_ = sigma_s;
            this.sigma_r_ = sigma_r;
            this.correlation_ = correlation;
            this.kappa_ = kappa;
            this.T_ = T;
        }

        @Override
        public double op(final double u) {
            final double g = g_k(T_ - u, kappa_);
            return (sigma_s_ * sigma_s_) + (2.0 * correlation_ * sigma_s_ * sigma_r_ * g)
                    + (sigma_r_ * sigma_r_ * g * g);
        }
    }
}
