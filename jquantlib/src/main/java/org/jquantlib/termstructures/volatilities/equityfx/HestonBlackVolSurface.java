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
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.termstructures.volatilities.equityfx;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine.ComplexLogFormula;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine.Integration;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Black volatility surface backed by a Heston model.
 *
 * <p>For each ({@code t}, {@code K}) query the surface (a) computes the
 * undiscounted Heston price of a plain-vanilla payoff via
 * {@link AnalyticHestonEngine#priceVanillaPayoff(PlainVanillaPayoff, double)}, (b) inverts that price to a
 * Black-Scholes implied total standard deviation with {@link BlackFormula#blackFormulaImpliedStdDev}, and (c) returns
 * the implied volatility {@code sigma_BS(t, K)}. Variance is exposed via {@code sigma_BS^2 * t}.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/termstructures/volatility/equityfx/hestonblackvolsurface.{hpp,cpp}} (170 LOC). The port mirrors the C++
 * implementation function-for-function; the only structural deviation is that the Java {@link AnalyticHestonEngine}
 * holds both {@link HestonModel} and {@link HestonProcess} (the C++ engine derives the process from the model), so the
 * surface accepts the model directly and forwards {@code model.process()} to the engine.
 *
 * <h3>Heston-only safeguards</h3>
 * <ul>
 *   <li>If the Heston price is non-positive (deep OTM, numerical noise) the
 *       surface returns the long-run vol {@code sqrt(theta)} as a fallback —
 *       inversion would otherwise be ill-defined.</li>
 *   <li>Strike side is auto-selected: when {@code fwd > strike} the Put price
 *       is inverted (intrinsic-friendly), else the Call price; this matches
 *       C++ verbatim.</li>
 * </ul>
 *
 * <p>Source: QuantLib v1.42.1 @ {@code 099987f0ca}.
 *
 * @see AnalyticHestonEngine
 * @see HestonModel
 */
public class HestonBlackVolSurface extends BlackVolTermStructure {

    private final HestonModel hestonModel_;
    private final ComplexLogFormula cpxLogFormula_;
    private final Integration integration_;

    /**
     * Convenience constructor — Gauss-Laguerre order 160 with {@link ComplexLogFormula#Gatheral} (the only complex-log
     * formula implemented in the Java port; the C++ default is {@link ComplexLogFormula#AngledContour}).
     *
     * <p>The Java {@link AnalyticHestonEngine} currently drives only the
     * Gatheral integrand from {@code calculate()} / {@code priceVanillaPayoff()}; passing other enum values is accepted
     * by the engine constructor but silently falls back to Gatheral pricing.
     */
    public HestonBlackVolSurface(final HestonModel hestonModel) {
        this(hestonModel, ComplexLogFormula.Gatheral, Integration.gaussLaguerre(160));
    }

    /**
     * Full constructor mirroring C++ v1.42.1.
     *
     * @param hestonModel   Heston model providing process, calibrated parameters and the underlying yield curves.
     * @param cpxLogFormula complex-log formula (only {@link ComplexLogFormula#Gatheral} is implemented by the Java
     *                      engine).
     * @param integration   Fourier-integration configurator passed to {@link AnalyticHestonEngine}.
     */
    public HestonBlackVolSurface(final HestonModel hestonModel, final ComplexLogFormula cpxLogFormula,
            final Integration integration) {
        super(hestonModel.process().riskFreeRate().currentLink().referenceDate(), new NullCalendar(),
                BusinessDayConvention.Following, hestonModel.process().riskFreeRate().currentLink().dayCounter());
        this.hestonModel_ = hestonModel;
        this.cpxLogFormula_ = cpxLogFormula;
        this.integration_ = integration;
        hestonModel_.addObserver(this);
    }

    //
    // BlackVolTermStructure / TermStructure overrides
    //

    @Override
    public DayCounter dayCounter() {
        return hestonModel_.process().riskFreeRate().currentLink().dayCounter();
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public double minStrike() {
        return 0.0;
    }

    @Override
    public double maxStrike() {
        return Double.MAX_VALUE;
    }

    /**
     * At-the-money level at time {@code t}. Mirrors C++ v1.43
     * ({@code hestonblackvolsurface.cpp}):
     *
     * <pre>
     *   return process-&gt;s0()-&gt;value()
     *        * process-&gt;dividendYield()-&gt;discount(t, true)
     *        / process-&gt;riskFreeRate()-&gt;discount(t, true);
     * </pre>
     *
     * — the same forward {@link #blackVolImpl(double, double)} already computes to choose the option type,
     * including the {@code extrapolate = true} on both discounts.
     */
    @Override
    public double atmLevel(final double t) {
        final HestonProcess process = hestonModel_.process();
        return process.s0().currentLink().value()
                * process.dividendYield().currentLink().discount(t, true)
                / process.riskFreeRate().currentLink().discount(t, true);
    }

    @Override
    protected double blackVarianceImpl(final double t, final double strike) {
        final double vol = blackVolImpl(t, strike);
        return vol * vol * t;
    }

    @Override
    protected double blackVolImpl(final double t, final double strike) {
        final AnalyticHestonEngine hestonEngine = new AnalyticHestonEngine(hestonModel_, hestonModel_.process(),
                cpxLogFormula_, integration_);

        final HestonProcess process = hestonModel_.process();

        final double df = process.riskFreeRate().currentLink().discount(t, true);
        final double fwd =
                process.s0().currentLink().value() * process.dividendYield().currentLink().discount(t, true) / df;

        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(fwd > strike ? Option.Type.Put : Option.Type.Call,
                strike);

        final double npv = hestonEngine.priceVanillaPayoff(payoff, t);

        final double theta = hestonModel_.theta();
        if ( npv <= 0.0 ) {
            return Math.sqrt(theta);
        }

        // Brent inversion: solve for vol such that blackFormula(...) == npv.
        // We invert the implied total stddev directly (sqrt(t) * vol) via
        // BlackFormula.blackFormulaImpliedStdDev which already implements
        // the C++ ChambersNawalkha algorithm; the surface returns vol so we
        // divide by sqrt(t). C++ wraps Brent around the price residual; the
        // Java port leverages the existing blackFormulaImpliedStdDev helper
        // (it shares the same Brent core but with a robust initial guess).
        //
        // C++ behaviour: solver.setMaxEvaluations(10000), accuracy =
        // numeric_limits<double>::epsilon(), guess = sqrt(theta), step = 0.01.
        // We replicate that wrapper to stay function-for-function with the
        // C++ source rather than delegating to blackFormulaImpliedStdDev
        // (whose default accuracy is 1e-6 and whose default initial guess is
        // the Brenner-Subrahmanyam approximation, not sqrt(theta)).
        final double accuracy = Math.ulp(1.0); // numeric_limits<double>::epsilon()
        final double guess = Math.sqrt(theta);
        final double step = 0.01;
        final double fwdF = fwd;
        final double dfF = df;
        final Option.Type optType = payoff.optionType();
        final double strikeF = strike;

        final Brent solver = new Brent();
        solver.setMaxEvaluations(10000);

        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double v) {
                final double stddev = Math.max(0.0, v) * Math.sqrt(t);
                return BlackFormula.blackFormula(optType, strikeF, fwdF, stddev, dfF) - npv;
            }
        };

        return solver.solve(f, accuracy, guess, step);
    }
}
