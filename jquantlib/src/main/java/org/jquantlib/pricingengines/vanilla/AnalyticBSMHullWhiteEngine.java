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

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2007 Klaus Spanderen
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Constants;
import org.jquantlib.model.shortrate.onefactormodels.HullWhite;
import org.jquantlib.pricingengines.AnalyticEuropeanEngine;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;

/**
 * Analytic European-option pricer including stochastic interest rates (Black-Scholes equity + Hull-White short-rate).
 *
 * <p>Phase 5h.5-HHW WI-2 port of {@code QuantLib::AnalyticBSMHullWhiteEngine}
 * (v1.42.1 ql/pricingengines/vanilla/analyticbsmhullwhiteengine.{hpp,cpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p><strong>Method.</strong> Brigo-Mercurio (Interest Rate Models, ch.3) show
 * that under deterministic equity vol the Black-Scholes price under stochastic Hull-White rates can be recovered by
 * adding to the Black variance an offset
 *
 * <p>{@code v + 2 mu}, where
 * <ul>
 *   <li>{@code v = sigma^2/a^2 * (T + 2/a*exp(-aT) - 1/(2a)*exp(-2aT) - 3/(2a))}</li>
 *   <li>{@code mu = 2 rho sigma eta /a * (T - 1/a*(1 - exp(-aT)))}</li>
 * </ul>
 *
 * <p>then pricing the option with the standard {@link AnalyticEuropeanEngine}
 * on a vol surface uniformly shifted by that offset (see
 * {@code ShiftedBlackVolTermStructure}). For {@code aT} below the small-a
 * cutoff a third-order Taylor expansion is used to avoid catastrophic
 * cancellation in the closed-form coefficients.
 *
 * <p>References: Brigo D., Mercurio F., <i>Interest Rate Models</i>.
 *
 * @category vanillaengines
 */
public class AnalyticBSMHullWhiteEngine
        extends GenericModelEngine< HullWhite, OneAssetOption.Arguments, OneAssetOption.Results > {

    private final double rho_;
    private final GeneralizedBlackScholesProcess process_;

    /**
     * @param equityShortRateCorrelation correlation rho between equity and short-rate.
     * @param process                    Black-Scholes process for equity (deterministic vol).
     * @param model                      calibrated Hull-White model providing {@code a} and {@code sigma} via
     *                                   {@code params()}.
     */
    public AnalyticBSMHullWhiteEngine(final double equityShortRateCorrelation,
            final GeneralizedBlackScholesProcess process, final HullWhite model) {
        super(model, new OneAssetOption.ArgumentsImpl(), new OneAssetOption.ResultsImpl());
        QL.require(process != null, "no Black-Scholes process specified");
        QL.require(model != null, "no Hull-White model specified");
        this.rho_ = equityShortRateCorrelation;
        this.process_ = process;
        // Mirror C++ registerWith(process_): observe state-changes so the
        // engine recomputes when the BS process re-emits.
        this.process_.addObserver(this);
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args = (OneAssetOption.ArgumentsImpl) arguments_;
        QL.require(process_.x0() > 0.0, "negative or null underlying given");

        final StrikedTypePayoff payoff = (StrikedTypePayoff) args.payoff;
        QL.require(payoff != null, "non-striked payoff given");

        final Exercise exercise = args.exercise;

        final double t = process_.riskFreeRate().currentLink().dayCounter()
                .yearFraction(process_.riskFreeRate().currentLink().referenceDate(), exercise.lastDate());

        final double a = model.params().get(0);
        final double sigma = model.params().get(1);
        final double eta = process_.blackVolatility().currentLink().blackVol(exercise.lastDate(), payoff.strike());

        final double varianceOffset;
        if ( a * t > Math.pow(Constants.QL_EPSILON, 0.25) ) {
            final double v =
                    sigma * sigma / (a * a) * (t + 2.0 / a * Math.exp(-a * t) - 1.0 / (2.0 * a) * Math.exp(-2.0 * a * t)
                            - 3.0 / (2.0 * a));
            final double mu = 2.0 * rho_ * sigma * eta / a * (t - 1.0 / a * (1.0 - Math.exp(-a * t)));
            varianceOffset = v + mu;
        } else {
            // low-a algebraic limit (3rd-order Taylor)
            final double v = sigma * sigma * t * t * t * (1.0 / 3.0 - 0.25 * a * t + 7.0 / 60.0 * a * a * t * t);
            final double mu = rho_ * sigma * eta * t * t * (1.0 - a * t / 3.0 + a * a * t * t / 12.0);
            varianceOffset = v + mu;
        }

        final Handle< BlackVolTermStructure > volTS = new Handle< BlackVolTermStructure >(
                new ShiftedBlackVolTermStructure(varianceOffset, process_.blackVolatility()));

        final GeneralizedBlackScholesProcess adjProcess = new GeneralizedBlackScholesProcess(process_.stateVariable(),
                process_.dividendYield(), process_.riskFreeRate(), volTS);

        final AnalyticEuropeanEngine bsmEngine = new AnalyticEuropeanEngine(adjProcess);

        // Forward Arguments from this engine to the inner engine.
        final OneAssetOption.ArgumentsImpl bsmArgs = (OneAssetOption.ArgumentsImpl) bsmEngine.getArguments();
        bsmArgs.payoff = args.payoff;
        bsmArgs.exercise = args.exercise;
        bsmArgs.validate();

        bsmEngine.calculate();

        // Copy results back. OneAssetOption.ResultsImpl bundles greeks +
        // moreGreeks under one container; copy-fields field-by-field.
        final OneAssetOption.ResultsImpl bsmRes = (OneAssetOption.ResultsImpl) bsmEngine.getResults();
        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;

        res.value = bsmRes.value;
        res.errorEstimate = bsmRes.errorEstimate;
        res.greeks().delta = bsmRes.greeks().delta;
        res.greeks().gamma = bsmRes.greeks().gamma;
        res.greeks().theta = bsmRes.greeks().theta;
        res.greeks().vega = bsmRes.greeks().vega;
        res.greeks().rho = bsmRes.greeks().rho;
        res.greeks().dividendRho = bsmRes.greeks().dividendRho;
        res.moreGreeks().deltaForward = bsmRes.moreGreeks().deltaForward;
        res.moreGreeks().elasticity = bsmRes.moreGreeks().elasticity;
        res.moreGreeks().thetaPerDay = bsmRes.moreGreeks().thetaPerDay;
        res.moreGreeks().strikeSensitivity = bsmRes.moreGreeks().strikeSensitivity;
        res.moreGreeks().itmCashProbability = bsmRes.moreGreeks().itmCashProbability;
    }

    /**
     * BlackVolTermStructure decorator that adds a constant {@code variance} offset to the underlying surface. Mirrors
     * the file-local {@code ShiftedBlackVolTermStructure} in analyticbsmhullwhiteengine.cpp.
     */
    private static final class ShiftedBlackVolTermStructure extends BlackVolTermStructure {
        private final double varianceOffset_;
        private final Handle< BlackVolTermStructure > volTS_;

        ShiftedBlackVolTermStructure(final double varianceOffset, final Handle< BlackVolTermStructure > volTS) {
            super(volTS.currentLink().referenceDate(), volTS.currentLink().calendar(), BusinessDayConvention.Following,
                    volTS.currentLink().dayCounter());
            this.varianceOffset_ = varianceOffset;
            this.volTS_ = volTS;
        }

        @Override
        public double minStrike() {
            return volTS_.currentLink().minStrike();
        }

        @Override
        public double maxStrike() {
            return volTS_.currentLink().maxStrike();
        }

        @Override
        public Date maxDate() {
            return volTS_.currentLink().maxDate();
        }

        @Override
        protected double blackVarianceImpl(final double t, final double strike) {
            return volTS_.currentLink().blackVariance(t, strike, true) + varianceOffset_;
        }

        @Override
        protected double blackVolImpl(final double t, final double strike) {
            final double nonZeroMaturity = (t == 0.0 ? 0.00001 : t);
            final double var = blackVarianceImpl(nonZeroMaturity, strike);
            return Math.sqrt(var / nonZeroMaturity);
        }
    }
}
