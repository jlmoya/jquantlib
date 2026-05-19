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
 Copyright (C) 2004 Neil Firth
 Copyright (C) 2006 Klaus Spanderen
*/

package org.jquantlib.pricingengines.basket;

import org.jquantlib.QL;
import org.jquantlib.instruments.BasketPayoff;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.math.matrixutilities.Array;
import org.jquantlib.methods.montecarlo.EarlyExercisePathPricer;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.methods.montecarlo.MultiPath;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-asset early-exercise path pricer for American basket options driven by the Longstaff-Schwartz Monte Carlo
 * regression.
 *
 * <p>Java port of C++ class {@code AmericanBasketPathPricer} from
 * {@code QuantLib v1.42.1 ql/pricingengines/basket/mcamericanbasketengine.{hpp,cpp}} (Phase 4i.5b WI-1). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class extends {@code EarlyExercisePathPricer<MultiPath>} and
 * provides:
 * <ul>
 *   <li>{@code state(path,t)} — returns {@code Array{S_1[t]*scaling, ...,
 *       S_n[t]*scaling}} where {@code scaling = 1/strike} if the base payoff
 *       is a {@link StrikedTypePayoff}, else 1.</li>
 *   <li>{@code operator(path,t)} — returns
 *       {@code basketPayoff.basePayoff(basketPayoff.accumulate(state) /
 *       scaling)} (i.e. the unscaled basket payoff).</li>
 *   <li>{@code basisSystem()} — multi-path basis (dim=assetNumber, given
 *       polynomial order/type) plus one extra basis function which evaluates
 *       to the payoff itself (see C++ ctor's {@code v_.emplace_back([&](const
 *       Array& state){ return this->payoff(state); })}).</li>
 * </ul>
 *
 * <p>Restricted to polynomial types Monomial / Laguerre / Hermite /
 * Hyperbolic / Chebyshev2nd (mirrors C++ {@code QL_REQUIRE} guard);
 * Legendre and Chebyshev are rejected.
 *
 * <p>Pricer state semantics:
 * <pre>
 *   payoff_       = base BasketPayoff (Min/Max/Average/Spread, wrapping a
 *                   single-asset Payoff such as PlainVanillaPayoff)
 *   scalingValue_ = 1/strike if basket's base payoff is a
 *                   StrikedTypePayoff, else 1
 *   basisSystem_  = multiPathBasisSystem(dim,order,type) ++ [stateAsPayoff]
 * </pre>
 *
 * @author JQuantLib
 */
public class AmericanBasketPathPricer implements EarlyExercisePathPricer< MultiPath, Array > {

    //
    // protected fields (mirror C++ exactly)
    //

    protected final int assetNumber_;
    protected final Payoff payoff_;
    protected final List< Ops.Op< Array, Double > > v_;
    /**
     * Reciprocal of the strike when the basket wraps a {@link StrikedTypePayoff}; otherwise 1.0. Mirrors C++
     * {@code Real scalingValue_ = 1.0; ... scalingValue_/=strikePayoff->strike();}.
     */
    protected double scalingValue_ = 1.0;

    //
    // public constructors
    //

    /**
     * Default-arity constructor matching C++ default arguments ({@code polynomialOrder = 2},
     * {@code polynomialType = Monomial}).
     */
    public AmericanBasketPathPricer(final int assetNumber, final Payoff payoff) {
        this(assetNumber, payoff, 2, LsmBasisSystem.PolynomialType.Monomial);
    }

    /**
     * Mirrors C++
     * {@code AmericanBasketPathPricer(Size assetNumber, shared_ptr<Payoff> payoff, Size polynomialOrder = 2,
     * LsmBasisSystem::PolynomialType polynomialType = Monomial)}.
     *
     * @param assetNumber     number of underlying assets in the basket.
     * @param payoff          base payoff — must be a {@link BasketPayoff}.
     * @param polynomialOrder regression-basis total-degree cap.
     * @param polynomialType  polynomial family (Monomial/Laguerre/Hermite/ Hyperbolic/Chebyshev2nd).
     */
    public AmericanBasketPathPricer(final int assetNumber, final Payoff payoff, final int polynomialOrder,
            final LsmBasisSystem.PolynomialType polynomialType) {
        QL.require(polynomialType == LsmBasisSystem.PolynomialType.Monomial
                || polynomialType == LsmBasisSystem.PolynomialType.Laguerre
                || polynomialType == LsmBasisSystem.PolynomialType.Hermite
                || polynomialType == LsmBasisSystem.PolynomialType.Hyperbolic
                || polynomialType == LsmBasisSystem.PolynomialType.Chebyshev2nd, "insufficient polynomial type");

        if ( !(payoff instanceof BasketPayoff) ) {
            throw new RuntimeException("payoff not a basket payoff");
        }
        final BasketPayoff basketPayoff = (BasketPayoff) payoff;

        this.assetNumber_ = assetNumber;
        this.payoff_ = payoff;

        // base multi-variate basis system: dim=assetNumber, total-degree<=order
        final List< Ops.ObjectToDouble< Array > > raw = LsmBasisSystem.multiPathBasisSystem(assetNumber_,
                polynomialOrder, polynomialType);
        this.v_ = new ArrayList< Ops.Op< Array, Double > >(raw.size() + 1);
        for ( final Ops.ObjectToDouble< Array > b : raw ) {
            v_.add(new Ops.Op< Array, Double >() {
                @Override
                public Double op(final Array a) {
                    return b.op(a);
                }
            });
        }

        // scaling — mirror C++ {@code if (strikePayoff != nullptr) scalingValue_ /= strikePayoff->strike();}
        final Payoff base = basketPayoff.basePayoff();
        if ( base instanceof StrikedTypePayoff ) {
            this.scalingValue_ /= ((StrikedTypePayoff) base).strike();
        }

        // last basis function = the (scaled-state) payoff itself.
        // Mirrors C++ {@code v_.emplace_back([&](const Array& state){ return this->payoff(state); });}
        v_.add(new Ops.Op< Array, Double >() {
            @Override
            public Double op(final Array a) {
                return AmericanBasketPathPricer.this.payoff(a);
            }
        });
    }

    //
    // EarlyExercisePathPricer<MultiPath, Array>
    //

    /**
     * Mirrors C++ {@code Array state(const MultiPath& path, Size t)}. Returns
     * {@code Array{S_1[t]*scaling, ..., S_n[t]*scaling}}.
     */
    @Override
    public Array state(final MultiPath path, final int t) {
        if ( path.assetNumber() != assetNumber_ ) {
            throw new RuntimeException("invalid multipath");
        }
        final double[] tmp = new double[assetNumber_];
        for ( int i = 0; i < assetNumber_; ++i ) {
            tmp[i] = path.get(i).get(t) * scalingValue_;
        }
        return new Array(tmp);
    }

    /**
     * Mirrors C++ {@code Real operator()(const MultiPath& path, Size t)}. Returns {@code payoff(state(path,t))} — the
     * unscaled basket payoff at time step {@code t}.
     */
    @Override
    public double operator(final MultiPath path, final int t) {
        return this.payoff(this.state(path, t));
    }

    /**
     * Mirrors C++ {@code std::vector<std::function<Real(Array)>> basisSystem()}.
     */
    @Override
    public List< ? extends Ops.Op< Array, Double > > basisSystem() {
        return v_;
    }

    //
    // protected
    //

    /**
     * Mirrors C++ private/protected {@code Real payoff(const Array& state)}: undoes the scaling and applies the base
     * payoff to {@code basketPayoff.accumulate(state)/scaling}.
     */
    protected double payoff(final Array state) {
        final BasketPayoff basketPayoff = (BasketPayoff) payoff_;
        // Convert Array -> double[] for the BasketPayoff.accumulate API.
        final double[] s = new double[state.size()];
        for ( int i = 0; i < s.length; ++i ) {
            s[i] = state.get(i);
        }
        final double value = basketPayoff.accumulate(s);
        return payoff_.get(value / scalingValue_);
    }
}
