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

import java.util.function.Function;

import org.jquantlib.cashflow.Dividend;
import org.jquantlib.instruments.DividendSchedule;
import org.jquantlib.quotes.Handle;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.time.Date;

/**
 * Escrowed-dividend spot adjustment used by the FdBlackScholes finite-difference engine.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/escroweddividendadjustment.{hpp,cpp}}.
 * <p>
 * The escrowed-dividend model represents discrete cash dividends as a deterministic flow whose present value
 * (discounted by the risk-free rate and accumulated using the dividend yield) is subtracted from the spot. This class
 * encapsulates the helper that computes the deterministic part:
 *
 * <pre>
 *   divAdj(t) = -sum_{divs with t &le; t_div &le; T} d_i * P_r(t_div) / P_r(t) * P_q(t) / P_q(t_div)
 * </pre>
 *
 * where {@code P_r} is the risk-free discount factor and {@code P_q} the dividend-yield discount factor.
 *
 * @author Phase 1-closure A2-C-555 port
 */
public final class EscrowedDividendAdjustment {

    private final DividendSchedule dividendSchedule;
    private final Handle< YieldTermStructure > rTS;
    private final Handle< YieldTermStructure > qTS;
    private final Function< Date, Double > toTime;
    private final double maturity;

    /**
     * @param dividendSchedule discrete cash dividends
     * @param rTS              risk-free yield term structure handle
     * @param qTS              dividend yield term structure handle
     * @param toTime           maps a {@link Date} to year fraction (typically {@code process.time(...)} of the GBS
     *                         process)
     * @param maturity         option maturity in years
     */
    public EscrowedDividendAdjustment(final DividendSchedule dividendSchedule,
            final Handle< YieldTermStructure > rTS, final Handle< YieldTermStructure > qTS,
            final Function< Date, Double > toTime, final double maturity) {
        this.dividendSchedule = dividendSchedule;
        this.rTS = rTS;
        this.qTS = qTS;
        this.toTime = toTime;
        this.maturity = maturity;
    }

    /**
     * Spot adjustment at year fraction {@code t}: sum of PV-equivalent cash dividends.
     */
    public double dividendAdjustment(final double t) {
        double divAdj = 0.0;
        for ( final Dividend dividend : dividendSchedule ) {
            final double divTime = toTime.apply(dividend.date()).doubleValue();
            if ( divTime >= t && divTime <= maturity ) {
                divAdj -= dividend.amount() * rTS.currentLink().discount(divTime) / rTS.currentLink().discount(t)
                        * qTS.currentLink().discount(t) / qTS.currentLink().discount(divTime);
            }
        }
        return divAdj;
    }

    public Handle< YieldTermStructure > riskFreeRate() {
        return rTS;
    }

    public Handle< YieldTermStructure > dividendYield() {
        return qTS;
    }
}
