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
package org.jquantlib.pricingengines.vanilla;

import java.util.ArrayList;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.instruments.Payoff;
import org.jquantlib.instruments.StrikedTypePayoff;
import org.jquantlib.math.Ops;
import org.jquantlib.methods.montecarlo.EarlyExercisePathPricer;
import org.jquantlib.methods.montecarlo.LsmBasisSystem;
import org.jquantlib.methods.montecarlo.LsmBasisSystem.PolynomialType;
import org.jquantlib.methods.montecarlo.Path;

/**
 * Single-path early-exercise pricer for vanilla American options under the
 * Longstaff-Schwartz Monte Carlo regression.
 *
 * <p>Phase 5h.5-MC port of {@code QuantLib::AmericanPathPricer}
 * (v1.42.1 ql/pricingengines/vanilla/mcamericanengine.{hpp,cpp}). Pinned
 * commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The basis system is built from the polynomial family at the requested
 * order, then augmented with one extra entry equal to the payoff itself —
 * mirrors C++ {@code v_.emplace_back([&](Real state){ return this->payoff(state); })}.
 *
 * <p>{@code state(path, t) = path[t] * scaling} where {@code scaling = 1/strike}
 * for {@link StrikedTypePayoff}, otherwise {@code 1}. The numerical-stability
 * scaling is unwound inside {@link #payoff(double)} so the public payoff value
 * is unscaled.
 *
 * <p>Allowed polynomial types match C++ exactly: Monomial, Laguerre, Hermite,
 * Hyperbolic, Chebyshev2nd. Other types (Legendre, Chebyshev) are rejected
 * via QL.require — same as the C++ {@code QL_REQUIRE} guard.
 */
public final class AmericanPathPricer
        implements EarlyExercisePathPricer<Path, Double> {

    private final Payoff payoff_;
    private final List<Ops.DoubleOp> v_;
    private final double scalingValue_;

    public AmericanPathPricer(final Payoff payoff,
                              final int polynomialOrder,
                              final PolynomialType polynomialType) {
        QL.require(
                polynomialType == PolynomialType.Monomial
                || polynomialType == PolynomialType.Laguerre
                || polynomialType == PolynomialType.Hermite
                || polynomialType == PolynomialType.Hyperbolic
                || polynomialType == PolynomialType.Chebyshev2nd,
                "insufficient polynomial type");

        this.payoff_ = payoff;
        // base basis system (order+1 functions)
        this.v_ = new ArrayList<>(
                LsmBasisSystem.pathBasisSystem(polynomialOrder, polynomialType));
        // append the payoff as an extra basis function — mirrors C++ lambda
        this.v_.add(new PayoffBasis(this));

        // scale by 1/strike for StrikedTypePayoff (numerical stability)
        if (payoff instanceof StrikedTypePayoff) {
            this.scalingValue_ = 1.0 / ((StrikedTypePayoff) payoff).strike();
        } else {
            this.scalingValue_ = 1.0;
        }
    }

    /**
     * Payoff at the given (already-scaled) {@code state}, with the scaling
     * unwound before evaluation. Mirrors C++ {@code Real payoff(Real state) const}.
     */
    public double payoff(final double state) {
        return payoff_.get(state / scalingValue_);
    }

    @Override
    public double operator(final Path path, final int t) {
        return payoff(state(path, t));
    }

    @Override
    public Double state(final Path path, final int t) {
        // scale path values for numerical stability — mirrors C++ path[t]*scalingValue_
        return path.getValues_(t) * scalingValue_;
    }

    @Override
    public List<? extends Ops.Op<Double, Double>> basisSystem() {
        // Adapt List<Ops.DoubleOp> to List<Ops.Op<Double,Double>>
        final List<Ops.Op<Double, Double>> ret = new ArrayList<>(v_.size());
        for (final Ops.DoubleOp f : v_) {
            ret.add(new Ops.Op<Double, Double>() {
                @Override public Double op(final Double x) { return f.op(x); }
            });
        }
        return ret;
    }

    /** Returns the underlying basis system (single-state {@code DoubleOp} form). */
    public List<Ops.DoubleOp> basisSystemDouble() {
        return v_;
    }

    /** Returns the scaling value used to rescale path values for stability. */
    public double scalingValue() {
        return scalingValue_;
    }

    /** Adapter wrapping the AmericanPathPricer's {@link #payoff(double)}. */
    private static final class PayoffBasis implements Ops.DoubleOp {
        private final AmericanPathPricer parent;
        PayoffBasis(final AmericanPathPricer parent) { this.parent = parent; }
        @Override public double op(final double state) { return parent.payoff(state); }
    }
}
