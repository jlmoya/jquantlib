/*
 Copyright (C) 2015 Johannes Göttker-Schnetmann
 Copyright (C) 2015 Klaus Spanderen
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

package org.jquantlib.experimental.volatility;

import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.solvers1D.Brent;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.vanilla.AnalyticHestonEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.Date;
import org.jquantlib.time.calendars.NullCalendar;

/**
 * Black volatility surface backed by a calibrated {@link HestonModel}.
 *
 * <p>Java port of v1.42.1
 * {@code ql/termstructures/volatility/equityfx/hestonblackvolsurface.{hpp,cpp}}.
 *
 * <p>For each {@code (t, strike)} query:
 * <ol>
 *   <li>price the corresponding plain-vanilla option (call if
 *       {@code forward &lt;= strike}, put otherwise — minimises the
 *       intrinsic-value subtraction in Brent) under the Heston model
 *       via {@link AnalyticHestonEngine#priceVanillaPayoff(PlainVanillaPayoff, double)};</li>
 *   <li>invert the Black formula via {@link Brent} to recover the
 *       implied Black volatility consistent with that price.</li>
 * </ol>
 *
 * <p>Variance is supplied as {@code blackVol^2 * t} (no separate
 * formula — mirrors C++ verbatim).
 *
 * <p><b>Java port deviations from C++ v1.42.1:</b>
 * <ul>
 *   <li>The C++ constructor takes
 *       {@link AnalyticHestonEngine.ComplexLogFormula} and
 *       {@code AnalyticHestonEngine::Integration} parameters; the Java
 *       {@link AnalyticHestonEngine} only implements the {@code Gatheral}
 *       formula and {@code GaussLaguerre} integration. We therefore
 *       expose only the simplified constructor — additional knobs would
 *       be wired through once the corresponding engine knobs land
 *       (deferred to a follow-up phase).</li>
 *   <li>{@code maxStrike()} returns {@link Double#MAX_VALUE} (matches
 *       C++ {@code std::numeric_limits<Real>::max()}).</li>
 *   <li>The Brent {@code accuracy} argument is hard-coded to
 *       {@code 1e-15} (slightly looser than C++'s
 *       {@code std::numeric_limits<double>::epsilon() ≈ 2.22e-16}).
 *       In practice the implied-vol inversion is stable to machine
 *       epsilon for non-degenerate cases.</li>
 * </ul>
 *
 * @author Phase Production-Audit
 */
public class HestonBlackVolSurface extends BlackVolTermStructure {

    private final Handle<HestonModel> hestonModel_;

    /**
     * Construct from a {@link Handle} to a {@link HestonModel}. Uses the
     * model's process for spot/discount/dividend access and reference
     * date.
     */
    public HestonBlackVolSurface(final Handle<HestonModel> hestonModel) {
        // C++:
        //   BlackVolTermStructure(hestonModel->process()->riskFreeRate()->referenceDate(),
        //                         NullCalendar(), Following,
        //                         hestonModel->process()->riskFreeRate()->dayCounter())
        super(hestonModel.currentLink().process().riskFreeRate().currentLink()
                       .referenceDate(),
              new NullCalendar(),
              BusinessDayConvention.Following,
              hestonModel.currentLink().process().riskFreeRate().currentLink()
                       .dayCounter());
        this.hestonModel_ = hestonModel;
        // C++: registerWith(hestonModel_) — Java equivalent.
        this.hestonModel_.addObserver(this);
    }

    @Override
    public DayCounter dayCounter() {
        return hestonModel_.currentLink().process().riskFreeRate()
                .currentLink().dayCounter();
    }

    @Override
    public Date maxDate() {
        return Date.maxDate();
    }

    @Override
    public /*@Real*/ double minStrike() {
        return 0.0;
    }

    @Override
    public /*@Real*/ double maxStrike() {
        return Double.MAX_VALUE;
    }

    @Override
    protected /*@Real*/ double blackVarianceImpl(final /*@Time*/ double t,
                                                 final /*@Real*/ double strike) {
        // C++: return squared(blackVolImpl(t, strike)) * t
        final double v = blackVolImpl(t, strike);
        return v * v * t;
    }

    @Override
    protected /*@Volatility*/ double blackVolImpl(final /*@Time*/ double t,
                                                  final /*@Real*/ double strike) {
        final HestonModel model = hestonModel_.currentLink();
        final HestonProcess process = model.process();

        // Re-instantiate the engine for each query — matches C++ which
        // constructs a local AnalyticHestonEngine in blackVolImpl.
        // Java port: GaussLaguerreIntegration only supports n=128 (the
        // embedded quadrature table). C++ default is 160; the small order
        // gap accounts for the 1e-6 (not 1e-12) tolerance tier in the
        // cross-validation tests.
        final AnalyticHestonEngine engine =
                new AnalyticHestonEngine(model, process, 128);

        final double df  = process.riskFreeRate().currentLink().discount(t, true);
        final double fwd = process.s0().currentLink().value()
                * process.dividendYield().currentLink().discount(t, true) / df;

        // C++: pick OTM payoff — minimises the intrinsic-value contribution
        // and hence the Brent search instability near deep ITM/OTM points.
        final Option.Type type = (fwd > strike) ? Option.Type.Put : Option.Type.Call;
        final PlainVanillaPayoff payoff = new PlainVanillaPayoff(type, strike);

        final double npv = engine.priceVanillaPayoff(payoff, t);

        final double theta = model.theta();
        if (npv <= 0.0) {
            return Math.sqrt(theta);
        }

        final Brent solver = new Brent();
        solver.setMaxEvaluations(10000);
        final double guess = Math.sqrt(theta);
        // C++ uses std::numeric_limits<double>::epsilon() ≈ 2.22e-16; Brent
        // in JQuantLib needs a fractionally looser tolerance to terminate
        // reliably across all (t, strike) combinations. 1e-15 still meets
        // the TIGHT tolerance tier (rel 1e-12 / abs 1e-14).
        final double accuracy = 1e-15;

        final Option.Type optionType = payoff.optionType();
        final double npvFinal = npv;

        final Ops.DoubleOp f = new Ops.DoubleOp() {
            @Override
            public double op(final double v) {
                // blackValue(optionType, strike, fwd, t, v, df, npv) - npv
                final double stddev = Math.max(0.0, v) * Math.sqrt(t);
                return BlackFormula.blackFormula(optionType, strike, fwd,
                        stddev, df) - npvFinal;
            }
        };

        return solver.solve(f, accuracy, guess, 0.01);
    }
}
