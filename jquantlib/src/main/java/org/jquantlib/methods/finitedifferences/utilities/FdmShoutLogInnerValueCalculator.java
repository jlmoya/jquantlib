/*
 Copyright (C) 2021 Klaus Spanderen

 This file is part of JQuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://jquantlib.org/

 JQuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <jquant-devel@lists.sourceforge.net>. The license is also available online at
 <http://www.jquantlib.org/index.php/LICENSE.TXT>.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE.  See the license for more details.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */
package org.jquantlib.methods.finitedifferences.utilities;

import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;
import org.jquantlib.pricingengines.BlackFormula;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.BlackVolTermStructure;

/**
 * Inner-value calculator for a shout option in the escrowed-dividend FDM engine.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdmshoutloginnervaluecalculator.{hpp,cpp}}.
 * <p>
 * A shout option grants the holder one opportunity to lock in (shout) the current intrinsic value while retaining the
 * payoff optionality from spot at maturity. At any time {@code t}, the immediate-shout value is the Black-Scholes value
 * of an at-spot forward option plus the discounted intrinsic payoff at the current spot.
 *
 * <h3>Formula</h3>
 * Given mesh state {@code s_t = exp(location)} (the escrowed log-spot), the shout intrinsic at time {@code t} is:
 *
 * <pre>
 *   qf      = q.discount(T) / q.discount(t)
 *   df      = r.discount(T) / r.discount(t)
 *   fwd     = s_t * qf / df
 *   stdDev  = blackForwardVol(t, T, s_t) * sqrt(T - t)
 *   npv     = blackFormula(payoffType, s_t, fwd, stdDev, df)
 *   spot    = s_t - divAdj(t)
 *   intr    = (Call: spot - strike, Put: strike - spot)
 *   inner   = max(0, npv + intr * df)
 * </pre>
 *
 * @author Phase 1-closure A3-B-546-shout-helper port
 */
public final class FdmShoutLogInnerValueCalculator implements FdmInnerValueCalculator {

    private final Handle< BlackVolTermStructure > blackVolatility;
    private final EscrowedDividendAdjustment escrowedDividendAdj;
    private final double maturity;
    private final PlainVanillaPayoff payoff;
    private final FdmMesher mesher;
    private final int direction;

    public FdmShoutLogInnerValueCalculator(final Handle< BlackVolTermStructure > blackVolatility,
            final EscrowedDividendAdjustment escrowedDividendAdj, final double maturity,
            final PlainVanillaPayoff payoff, final FdmMesher mesher, final int direction) {
        this.blackVolatility = blackVolatility;
        this.escrowedDividendAdj = escrowedDividendAdj;
        this.maturity = maturity;
        this.payoff = payoff;
        this.mesher = mesher;
        this.direction = direction;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final double sT = JQuantMath.exp(mesher.location(iter, direction));

        final double qf = escrowedDividendAdj.dividendYield().currentLink().discount(maturity)
                / escrowedDividendAdj.dividendYield().currentLink().discount(t);

        final double df = escrowedDividendAdj.riskFreeRate().currentLink().discount(maturity)
                / escrowedDividendAdj.riskFreeRate().currentLink().discount(t);

        final double fwd = sT * qf / df;
        final double stdDev = blackVolatility.currentLink().blackForwardVol(t, maturity, sT, true)
                * Math.sqrt(maturity - t);

        // Note: per C++ v1.42.1 fdmshoutloginnervaluecalculator.cpp the strike argument is s_t (the current
        // log-spot location) — the shout option locks in optionality struck at the current spot evolving
        // forward to maturity. The literal payoff.strike() is only used in the intrinsic term below.
        final double npv = BlackFormula.blackFormula(payoff.optionType(), sT, fwd, stdDev, df);

        final double spot = sT - escrowedDividendAdj.dividendAdjustment(t);

        final double intrinsic = (payoff.optionType() == Option.Type.Call) ? spot - payoff.strike()
                : payoff.strike() - spot;

        return Math.max(0.0, npv + intrinsic * df);
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return innerValue(iter, t);
    }
}
