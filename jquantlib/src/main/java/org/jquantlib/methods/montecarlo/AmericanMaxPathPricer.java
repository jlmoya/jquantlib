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
package org.jquantlib.methods.montecarlo;

import org.jquantlib.instruments.Payoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-asset early-exercise path pricer for American max-of-N options driven by the Longstaff-Schwartz Monte Carlo
 * regression.
 *
 * <p>Java port of the test-suite-internal C++ class
 * {@code AmericanMaxPathPricer} from {@code QuantLib v1.42.1 test-suite/mclongstaffschwartzengine.cpp} (Phase MC-extras
 * WI-3). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Promoted from test-internal to production
 * ({@code org.jquantlib.methods.montecarlo}) so it can be reused beyond the MCLongstaffSchwartzEngine test-suite — e.g.
 * by future Bermudan / basket-option engines that need a max-of-N early-exercise pricer.
 *
 * <p>Per-step payoff: {@code payoff(max(S_1[t], S_2[t], ..., S_n[t]))}.
 *
 * <p>Per-step regression state: {@code Array{S_1[t], ..., S_n[t]}} —
 * the basis is therefore {@code dim=n} multi-path basis evaluated coordinatewise.
 *
 * <p>Basis: {@link LsmBasisSystem#multiPathBasisSystem(int, int,
 * org.jquantlib.methods.montecarlo.LsmBasisSystem.PolynomialType)} with {@code dim=2}, {@code order=2},
 * {@link LsmBasisSystem.PolynomialType#Monomial} — exactly the basis used by the referenced C++ test ("Monte Carlo
 * Methods in Financial Engineering", Glasserman 2004, p. 462).
 *
 * <p>To use a different basis, instantiate with the desired
 * {@code basisSystem} via the explicit-basis constructor.
 */
public final class AmericanMaxPathPricer implements EarlyExercisePathPricer< MultiPath, Array > {

    private final Payoff payoff_;
    private final List< Ops.Op< Array, Double > > basisSystem_;

    /**
     * Default constructor: dim=2, order=2 Monomial basis (matches the C++ test).
     */
    public AmericanMaxPathPricer(final Payoff payoff) {
        this(payoff, defaultBasis(2, 2));
    }

    /**
     * Constructor with explicit dimension / order: dim must match the number of assets in the {@link MultiPath} this
     * pricer is applied to.
     */
    public AmericanMaxPathPricer(final Payoff payoff, final int dim, final int order,
            final LsmBasisSystem.PolynomialType polynomialType) {
        this(payoff, adaptToOpList(LsmBasisSystem.multiPathBasisSystem(dim, order, polynomialType)));
    }

    /**
     * Constructor with explicit basis system — for callers that want full control over the regression basis.
     */
    public AmericanMaxPathPricer(final Payoff payoff, final List< Ops.Op< Array, Double > > basisSystem) {
        this.payoff_ = payoff;
        this.basisSystem_ = basisSystem;
    }

    private static List< Ops.Op< Array, Double > > defaultBasis(final int dim, final int order) {
        return adaptToOpList(LsmBasisSystem.multiPathBasisSystem(dim, order, LsmBasisSystem.PolynomialType.Monomial));
    }

    /**
     * Adapt {@code Ops.ObjectToDouble<Array>} (returned by {@link LsmBasisSystem#multiPathBasisSystem}) to
     * {@code Ops.Op<Array, Double>} (the type {@link EarlyExercisePathPricer} declares).
     */
    private static List< Ops.Op< Array, Double > > adaptToOpList(final List< Ops.ObjectToDouble< Array > > src) {
        final List< Ops.Op< Array, Double > > dst = new ArrayList<>(src.size());
        for ( final Ops.ObjectToDouble< Array > b : src ) {
            dst.add(new Ops.Op< Array, Double >() {
                @Override
                public Double op(final Array a) {
                    return b.op(a);
                }
            });
        }
        return dst;
    }

    @Override
    public double operator(final MultiPath path, final int t) {
        // payoff(max_i path[i][t])
        double m = Double.NEGATIVE_INFINITY;
        for ( int i = 0; i < path.assetNumber(); ++i ) {
            m = Math.max(m, path.get(i).get(t));
        }
        return payoff_.get(m);
    }

    //
    // helpers
    //

    @Override
    public Array state(final MultiPath path, final int t) {
        final double[] tmp = new double[path.assetNumber()];
        for ( int i = 0; i < path.assetNumber(); ++i ) {
            tmp[i] = path.get(i).get(t);
        }
        return new Array(tmp);
    }

    @Override
    public List< ? extends Ops.Op< Array, Double > > basisSystem() {
        return basisSystem_;
    }
}
