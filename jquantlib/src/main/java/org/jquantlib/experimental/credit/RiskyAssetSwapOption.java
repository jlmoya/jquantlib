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
 Copyright (C) 2009 Roland Lichters
*/

package org.jquantlib.experimental.credit;

import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.instruments.Instrument;
import org.jquantlib.math.distributions.CumulativeNormalDistribution;
import org.jquantlib.math.distributions.NormalDistribution;
import org.jquantlib.time.Date;

/**
 * Option on risky asset swap.
 *
 * <p>Java port of QuantLib v1.42.1 {@code QuantLib::RiskyAssetSwapOption}
 * ({@code ql/experimental/credit/riskyassetswapoption.{hpp,cpp}}).
 *
 * <p>Uses a normal Black-style formula on the asset-swap spread:
 * {@code NPV = A_0 sigma sqrt(T) (w d Phi(w d) + phi(d))} with
 * {@code d = (s - s_market) / (sigma sqrt(T))}.
 *
 * <p>Phase 4m.5 work-item 12 (option half).
 */
public class RiskyAssetSwapOption extends Instrument {

    private final RiskyAssetSwap asw;
    private final Date expiry;
    private final double marketSpread;
    private final double spreadVolatility;

    public RiskyAssetSwapOption(final RiskyAssetSwap asw,
                                final Date expiry,
                                final double marketSpread,
                                final double spreadVolatility) {
        this.asw = asw;
        this.expiry = expiry;
        this.marketSpread = marketSpread;
        this.spreadVolatility = spreadVolatility;
    }

    @Override
    public boolean isExpired() {
        return expiry.compareTo(new Settings().evaluationDate()) <= 0;
    }

    @Override
    protected void performCalculations() {
        // strike receiver = asw call = spread put
        final double w = asw.fixedPayer() ? -1.0 : 1.0;
        final Date today = new Settings().evaluationDate();
        final double expiryTime = new Actual365Fixed().yearFraction(today, expiry);
        final double stdDev = spreadVolatility * Math.sqrt(expiryTime);
        final double d = (asw.spread() - marketSpread) / stdDev;
        final double a0 = asw.nominal() * asw.floatAnnuity();
        NPV = a0 * stdDev * (w * d * new CumulativeNormalDistribution().op(w * d)
                + new NormalDistribution().op(d));
    }
}
