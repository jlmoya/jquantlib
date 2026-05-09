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
 Copyright (C) 2014 Fabien Le Floc'h

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.
*/
package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.exercise.Exercise;
import org.jquantlib.instruments.OneAssetOption;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.model.equity.HestonModel;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.pricingengines.GenericModelEngine;
import org.jquantlib.processes.HestonProcess;
import org.jquantlib.time.Date;

/**
 * Heston-model engine for European options based on analytic expansions of
 * the implied volatility.
 *
 * <p>Phase 5h.5 port of {@code QuantLib::HestonExpansionEngine}
 * (v1.42.1 ql/pricingengines/vanilla/hestonexpansionengine.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>References:
 * <ul>
 *   <li>M. Forde, A. Jacquier, R. Lee — <i>The small-time smile and term
 *       structure of implied volatility under the Heston model.</i> SIAM
 *       Journal on Financial Mathematics, 2012.</li>
 *   <li>M. Lorig, S. Pagliarani, A. Pascucci — <i>Explicit implied vols for
 *       multifactor local-stochastic vol models.</i> arXiv 1306.5447v3, 2014.</li>
 * </ul>
 *
 * <p>Currently supported expansion formulas:
 * <ul>
 *   <li>{@link Formula#Forde} — Forde et al. small-time smile (degree-4
 *       polynomial in log-moneyness, coefficients via cumulant expansion).</li>
 *   <li>{@link Formula#LPP2} — Lorig-Pagliarani-Pascucci order-2 (deferred to
 *       Phase 5h.5b; throws {@link UnsupportedOperationException}).</li>
 *   <li>{@link Formula#LPP3} — Lorig-Pagliarani-Pascucci order-3 (deferred to
 *       Phase 5h.5b; throws {@link UnsupportedOperationException}).</li>
 * </ul>
 *
 * <p>The Forde formula is fully validated cross-platform vs C++ probe NPVs in
 * {@code HestonExpansionEngineTest}. LPP2/LPP3 are
 * 600-line Mathematica-emitted closed-forms whose port is intentionally
 * deferred to a follow-up commit (out of scope of the Phase 5h.5 ~90-min cap).
 *
 * <p>The Java {@link HestonModel} does not currently expose a {@code process()}
 * accessor; the engine therefore takes the {@link HestonProcess} as an
 * explicit constructor argument, mirroring the convention of
 * {@link AnalyticHestonEngine}.
 */
public class HestonExpansionEngine
        extends GenericModelEngine<HestonModel,
                                   OneAssetOption.Arguments,
                                   OneAssetOption.Results> {

    /** Heston-implied-volatility expansion formula choice. */
    public enum Formula {
        /** Lorig-Pagliarani-Pascucci order-2 expansion (deferred to Phase 5h.5b). */
        LPP2,
        /** Lorig-Pagliarani-Pascucci order-3 expansion (deferred to Phase 5h.5b). */
        LPP3,
        /** Forde-Jacquier-Lee small-time expansion. Fully implemented. */
        Forde
    }

    private final HestonProcess process_;
    private final Formula formula_;

    /**
     * Construct an expansion engine using the supplied formula.
     *
     * @param model   the Heston model (provides v0/kappa/theta/sigma/rho)
     * @param process the Heston process (provides s0/discount/div/time)
     * @param formula expansion formula to dispatch to
     */
    public HestonExpansionEngine(final HestonModel model,
                                 final HestonProcess process,
                                 final Formula formula) {
        super(model,
              new OneAssetOption.ArgumentsImpl(),
              new OneAssetOption.ResultsImpl());
        this.process_ = process;
        this.formula_ = formula;
    }

    @Override
    public void calculate() {
        final OneAssetOption.ArgumentsImpl args =
                (OneAssetOption.ArgumentsImpl) arguments_;

        QL.require(args.exercise.type() == Exercise.Type.European,
                   "not an European option");
        QL.require(args.payoff instanceof PlainVanillaPayoff,
                   "non plain vanilla payoff given");
        final PlainVanillaPayoff payoff = (PlainVanillaPayoff) args.payoff;

        final Date exerciseDate = args.exercise.lastDate();

        final double riskFreeDiscount = process_.riskFreeRate().currentLink().discount(exerciseDate);
        final double dividendDiscount = process_.dividendYield().currentLink().discount(exerciseDate);

        final double spotPrice = process_.s0().currentLink().value();
        QL.require(spotPrice > 0.0, "negative or null underlying given");

        final double strikePrice = payoff.strike();
        final double term = process_.time(exerciseDate);
        final double forward = spotPrice * dividendDiscount / riskFreeDiscount;

        final double vol;
        switch (formula_) {
            case Forde: {
                final FordeHestonExpansion expansion = new FordeHestonExpansion(
                        model.kappa(), model.theta(),
                        model.sigma(), model.v0(),
                        model.rho(), term);
                vol = expansion.impliedVolatility(strikePrice, forward);
                break;
            }
            case LPP2:
                throw new UnsupportedOperationException(
                        "LPP2 Heston expansion deferred to Phase 5h.5b "
                        + "(requires porting ~600 LOC of Mathematica-emitted formulas)");
            case LPP3:
                throw new UnsupportedOperationException(
                        "LPP3 Heston expansion deferred to Phase 5h.5b "
                        + "(requires porting ~600 LOC of Mathematica-emitted formulas)");
            default:
                throw new IllegalStateException("unknown expansion formula: " + formula_);
        }

        final double price = BlackFormula.blackFormula(
                payoff, strikePrice, forward,
                vol * Math.sqrt(term), riskFreeDiscount, 0.0);

        final OneAssetOption.ResultsImpl res = (OneAssetOption.ResultsImpl) results_;
        res.value = price;
    }
}
