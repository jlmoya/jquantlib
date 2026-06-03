/*
 Copyright (C) 2008 Richard Gomes

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

package org.jquantlib.methods.lattices;

import java.util.List;

import org.jquantlib.cashflow.Dividend;
import org.jquantlib.daycounters.DayCounter;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.time.Date;
import org.jquantlib.time.TimeGrid;

/**
 * Binomial lattice approximating the Black-Scholes model with discrete dividends,
 * using the escrowed-spot model.
 * <p>
 * <b>Escrowed-spot model.</b> The discrete-dividend option is priced by running a
 * plain CRR binomial tree on an <i>escrowed</i> initial spot
 * {@code S0' = S0 - D}, where {@code D} is the present value of all dividends paid
 * in {@code (referenceDate, expiry]}:
 * <pre>
 *   D = sum_i amount_i * riskFreeDiscount(t_i) / dividendYieldDiscount(t_i)
 *     = sum_i amount_i * exp(-(rRate - qRate) * t_i)   (continuous flat rates).
 * </pre>
 * A CRR tree is multiplicative — every node value is {@code S0 * u^a * d^b} — so
 * scaling every node by {@code scale = (S0 - D) / S0} yields
 * {@code (S0 - D) * u^a * d^b}, i.e. exactly the tree that would be built starting
 * from the escrowed spot {@code S0 - D}. The escrow adjustment therefore stays
 * entirely inside {@link #underlying(int, int)} as a single multiplicative scale,
 * with no per-node {@code map}/{@code list} machinery.
 * <p>
 * For European options this converges (as {@code steps -> infinity}) to QuantLib's
 * {@code AnalyticDividendEuropeanEngine} (plain Black on the escrowed spot). For
 * American options it is the consistent escrowed approximation (no closed-form
 * oracle exists).
 * <p>
 * This corrects the previous implementation, which (a) dropped the dividend cash
 * amount from the escrow accumulator and (b) subtracted a node-{@code index}-keyed
 * escrow rather than a uniform spot shift — neither of which converges to the
 * analytic escrowed-dividend value.
 *
 * @category lattices
 *
 * @author Richard Gomes
 */
public class BlackScholesDividendLattice<T extends Tree> extends BlackScholesLattice<T> {

    final private T tree;
    final private double scale;

    public BlackScholesDividendLattice(
            final T tree,
            final double riskFreeRate,
            final double qRate,
            final /*@Time*/ double end,
            final int steps,
            final DayCounter dc,
            final TimeGrid grid,
            final Date referenceDate,
            final List<? extends Dividend> cashFlow) {
        super(tree, riskFreeRate, end, steps);
        this.tree = tree;

        // Escrowed-dividend PV over dividends in (referenceDate, expiry].
        double d = 0.0;
        for (int i = 0; i < cashFlow.size(); i++) {
            final double time = dc.yearFraction(referenceDate, cashFlow.get(i).date());
            // keep only dividends strictly after the reference date and up to maturity
            if (time > 0.0 && time <= end) {
                d += cashFlow.get(i).amount() * Math.exp(-(riskFreeRate - qRate) * time);
            }
        }

        // Root node = initial spot (CRR tree is centred: underlying(0,0) == S0).
        final double s0 = tree.underlying(0, 0);
        final double escrowedSpot = s0 - d;
        if (escrowedSpot <= 0.0)
            throw new LibraryException("negative underlying after subtracting dividends");
        this.scale = escrowedSpot / s0;
    }

    @Override
    public double underlying(final int i, final int index) {
        // Multiplicative escrow: scaling every CRR node by (S0 - D)/S0 reproduces
        // the tree built from the escrowed spot S0 - D.
        return tree.underlying(i, index) * scale;
    }

}
