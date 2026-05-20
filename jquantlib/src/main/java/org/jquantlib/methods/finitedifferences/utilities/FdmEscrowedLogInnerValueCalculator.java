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

import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.transcendental.JQuantMath;
import org.jquantlib.methods.finitedifferences.meshers.FdmMesher;
import org.jquantlib.methods.finitedifferences.operators.FdmLinearOpIterator;

/**
 * Inner-value calculator for the escrowed-dividend FdBlackScholes finite-difference engine.
 * <p>
 * Java port of v1.42.1 {@code ql/methods/finitedifferences/utilities/fdmescrowedloginnervaluecalculator.{hpp,cpp}}.
 * <p>
 * In the escrowed model the FDM state variable {@code s_t = exp(location)} represents spot + future-dividend
 * accumulation; the actual spot used by the payoff at time {@code t} is {@code spot = s_t - divAdj(t)} where
 * {@code divAdj} comes from {@link EscrowedDividendAdjustment#dividendAdjustment(double)}.
 *
 * @author Phase 1-closure A2-C-555 port
 */
public final class FdmEscrowedLogInnerValueCalculator implements FdmInnerValueCalculator {

    private final EscrowedDividendAdjustment escrowedDividendAdj;
    private final Payoff payoff;
    private final FdmMesher mesher;
    private final int direction;

    public FdmEscrowedLogInnerValueCalculator(final EscrowedDividendAdjustment escrowedDividendAdj, final Payoff payoff,
            final FdmMesher mesher, final int direction) {
        this.escrowedDividendAdj = escrowedDividendAdj;
        this.payoff = payoff;
        this.mesher = mesher;
        this.direction = direction;
    }

    @Override
    public double innerValue(final FdmLinearOpIterator iter, final double t) {
        final double sT = JQuantMath.exp(mesher.location(iter, direction));
        final double spot = sT - escrowedDividendAdj.dividendAdjustment(t);
        return payoff.get(spot);
    }

    @Override
    public double avgInnerValue(final FdmLinearOpIterator iter, final double t) {
        return innerValue(iter, t);
    }
}
