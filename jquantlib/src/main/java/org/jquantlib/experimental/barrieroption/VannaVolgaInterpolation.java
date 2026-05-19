/*
 Copyright (C) 2026 JQuantLib migration

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
 Copyright (C) 2013 Yue Tian
*/

package org.jquantlib.experimental.barrieroption;

import org.jquantlib.QL;
import org.jquantlib.instruments.Option;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.pricingengines.BlackFormula;

/**
 * Vanna/Volga interpolation between three discrete (strike, vol) points used by the Vanna/Volga barrier engines to
 * imply a smile-corrected vanilla price/vol at any strike.
 * <p>
 * Java port of QuantLib v1.42.1 {@code ql/experimental/barrieroption/vannavolgainterpolation.hpp} (the only caller of
 * the C++ {@code VannaVolgaInterpolation} is the Vanna/Volga barrier engine family, so the helper is co-located here
 * rather than under {@code org.jquantlib.math.interpolations}).
 *
 * <p>The interpolator requires exactly 3 strikes (and matching vols), assumes
 * the second one is the ATM vol, and uses the analytical Black vega weighting scheme of Castagna & Mercurio.
 *
 * @author JQuantLib migration
 */
final class VannaVolgaInterpolation {

    private final double[] strikes;
    private final double[] vols;
    private final double spot;
    private final double dDiscount;
    private final double fDiscount;
    private final double T;

    private final double atmVol;
    private final double fwd;
    private final double[] premiaBS = new double[3];
    private final double[] premiaMKT = new double[3];
    private final double[] vegas = new double[3];

    VannaVolgaInterpolation(final double[] strikes, final double[] vols, final double spot, final double dDiscount,
            final double fDiscount, final double T) {
        QL.require(strikes.length == 3 && vols.length == 3,
                "Vanna Volga Interpolator only interpolates 3 volatilities in strike space");
        this.strikes = strikes.clone();
        this.vols = vols.clone();
        this.spot = spot;
        this.dDiscount = dDiscount;
        this.fDiscount = fDiscount;
        this.T = T;
        this.atmVol = vols[1];
        this.fwd = spot * fDiscount / dDiscount;
        for ( int i = 0; i < 3; i++ ) {
            premiaBS[i] = BlackFormula.blackFormula(Option.Type.Call, strikes[i], fwd, atmVol * Math.sqrt(T),
                    dDiscount);
            premiaMKT[i] = BlackFormula.blackFormula(Option.Type.Call, strikes[i], fwd, vols[i] * Math.sqrt(T),
                    dDiscount);
            vegas[i] = vega(strikes[i]);
        }
    }

    /**
     * Returns the smile-implied volatility at strike {@code k} (extrapolation always enabled, matching the C++ caller's
     * {@code interpolation.enableExtrapolation()}).
     */
    double value(final double k) {
        final double vegaK = vega(k);
        final double x1 = vegaK / vegas[0] * (Math.log(strikes[1] / k) * Math.log(strikes[2] / k)) / (
                Math.log(strikes[1] / strikes[0]) * Math.log(strikes[2] / strikes[0]));
        final double x2 = vegaK / vegas[1] * (Math.log(k / strikes[0]) * Math.log(strikes[2] / k)) / (
                Math.log(strikes[1] / strikes[0]) * Math.log(strikes[2] / strikes[1]));
        final double x3 = vegaK / vegas[2] * (Math.log(k / strikes[0]) * Math.log(k / strikes[1])) / (
                Math.log(strikes[2] / strikes[0]) * Math.log(strikes[2] / strikes[1]));

        final double cBS = BlackFormula.blackFormula(Option.Type.Call, k, fwd, atmVol * Math.sqrt(T), dDiscount);
        final double c =
                cBS + x1 * (premiaMKT[0] - premiaBS[0]) + x2 * (premiaMKT[1] - premiaBS[1]) + x3 * (premiaMKT[2]
                        - premiaBS[2]);
        final double std = BlackFormula.blackFormulaImpliedStdDev(Option.Type.Call, k, fwd, c, dDiscount);
        return std / Math.sqrt(T);
    }

    private double vega(final double k) {
        final double d1 = (Math.log(fwd / k) + 0.5 * atmVol * atmVol * T) / (atmVol * Math.sqrt(T));
        final NormalDistribution norm = new NormalDistribution();
        return spot * dDiscount * Math.sqrt(T) * norm.op(d1);
    }
}
