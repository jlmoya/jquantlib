/*
 Copyright (C) 2008 Roland Lichters
 Copyright (C) 2014 Jose Aparicio
 Copyright (C) 2026 JQuantLib migration contributors.

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
package org.jquantlib.experimental.credit;

import org.jquantlib.QL;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.util.DefaultObservable;
import org.jquantlib.util.Observable;
import org.jquantlib.util.Observer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Generic multifactor latent variable model.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class copulaPolicyImpl> class LatentModel} (declared in
 * {@code ql/experimental/math/latentmodel.hpp}). Pinned commit {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>One considers latent (random) variables {@code Y_i} described by:
 * <pre>
 *   Y_i = Σ_k a_{i,k} M_k + sqrt(1 - Σ_k a_{i,k}^2) Z_i,    Y_i ~ Φ_{Y_i}
 * </pre>
 * where the systemic {@code M_k} and idiosyncratic {@code Z_i} random variables have independent zero-mean
 * unit-variance distributions. The N idiosyncratic variables share the same probability law {@code Φ_Z}; the model is
 * normalised so that {@code -1 ≤ a_{i,k} ≤ 1} and {@code Σ_k a_{i,k}^2 < 1}. Correlation between {@code Y_i} and
 * {@code Y_j} is {@code Σ_k a_{i,k} a_{j,k}}.
 *
 * <p>The {@link CopulaPolicy} type parameter separates the copula function
 * (the distributions involved) from the functionality (what the latent model represents: a default probability, a
 * recovery, etc.). C++ uses non-virtual template-parameter dispatch; Java uses a generic bound.
 *
 * <p>Java vs C++ differences:
 * <ul>
 *   <li>The C++ {@code FactorSampler} nested template (Box-Muller / PolarT
 *       specialisations) is deferred to a follow-up phase.</li>
 *   <li>The C++ {@code Handle<Quote>} reactive single-factor constructor is
 *       supported via
 *       {@link #LatentModel(Handle, int, CopulaPolicy, Function)} which
 *       accepts an explicit copula-factory {@code Function} so the underlying
 *       copula can be rebuilt when the quote ticks (Java has no equivalent
 *       to C++ {@code copula_.getInitTraits()}). Phase 4m.7c-b WI-2.</li>
 *   <li>The {@code IntegrationFactory} static helper is replaced by the
 *       {@link #createLMIntegration(int, IntegrationType)} static factory
 *       method here.</li>
 * </ul>
 *
 * <p>The {@link LatentModel} implements both {@link Observer} (so it can
 * react to single-factor {@link Quote} updates) and {@link Observable} (so
 * downstream loss models can re-trigger their own calculations on copula
 * change). When constructed without a Handle&lt;Quote&gt;, observer/observable
 * traffic still works (a no-op {@link #update()} merely re-notifies).
 *
 * <p>Derived classes should implement a modelled magnitude (default time,
 * loss, ...) and provide probability distributions and conditional values.
 *
 * @param <P> the {@link CopulaPolicy} subtype controlling distributions
 */
public class LatentModel< P extends CopulaPolicy > implements Observer, Observable {

    /** Default Gauss-Hermite quadrature order used by {@link #createLMIntegration}. */
    public static final int DEFAULT_QUADRATURE_ORDER = 25;
    /** Backing observable for downstream notification. */
    private final Observable delegatedObservable = new DefaultObservable(this);
    /** Ordering: factorWeights_[iVariable].get(iFactor). */
    protected List< List< Double > > factorWeights_;
    /**
     * sqrt(1 - Σ_k β_{i,k}²); cached for performance. Length matches {@link #size}.
     */
    protected double[] idiosyncFctrs_;
    /** Number of systemic factors; matches {@code factorWeights_[0].size()}. */
    protected int nFactors_;
    /** Number of latent variables (model dimension); matches {@code factorWeights_.size()}. */
    protected int nVariables_;
    /** Copula policy instance; supplies distribution evaluations. */
    protected P copula_;
    /**
     * Optional reactive market-correlation quote handle. Non-null only when constructed via the
     * {@link #LatentModel(Handle, int, CopulaPolicy, Function)} single-factor reactive ctor (Phase 4m.7c-b WI-2).
     */
    protected Handle< Quote > cachedMktFactor_;
    /**
     * Optional copula factory used to rebuild {@link #copula_} when the cached market-factor quote changes. Required by
     * the reactive ctor.
     */
    private Function< List< List< Double > >, P > copulaFactory_;
    /**
     * Constructs a LM with an arbitrary number of latent variables and factors given by the dimensions of the passed
     * matrix.
     *
     * @param factorWeights ordering is {@code factorWeights[iVariable].get(iFactor)}
     * @param copula        copula-policy instance (already initialised with the same factor weights so
     *                      dimension/normalisation checks have run)
     */
    public LatentModel(final List< List< Double > > factorWeights, final P copula) {
        QL.require(factorWeights != null && !factorWeights.isEmpty(), "factorWeights must contain at least one row");
        QL.require(copula != null, "copula must not be null");
        this.factorWeights_ = deepCopy(factorWeights);
        this.nFactors_ = factorWeights.get(0).size();
        this.nVariables_ = factorWeights.size();
        this.copula_ = copula;
        this.idiosyncFctrs_ = new double[nVariables_];
        for ( int i = 0; i < nVariables_; ++i ) {
            QL.require(factorWeights.get(i).size() == nFactors_,
                    "Name " + i + " provides a different number of factors");
            double dot = 0.0;
            for ( final Double w : factorWeights.get(i) ) {
                dot += w * w;
            }
            idiosyncFctrs_[i] = Math.sqrt(1.0 - dot);
        }
    }

    /**
     * Constructs a LM with arbitrary number of latent variables depending only on one random factor but with different
     * per-variable weights.
     *
     * @param factorWeights ordering is {@code factorWeights[iVariable]}; each becomes a length-1 row
     */
    public LatentModel(final double[] factorWeights, final P copula) {
        this(rowVectorMatrix(factorWeights), copula);
    }

    /**
     * Constructs a LM with arbitrary number of latent variables depending only on one random factor with the same
     * weight for all latent variables.
     *
     * @param correlSqr  the common factor weight
     * @param nVariables number of latent variables
     * @param copula     copula-policy instance for the {@code (nVariables x 1)} factor matrix produced by replicating
     *                   {@code correlSqr}
     */
    public LatentModel(final double correlSqr, final int nVariables, final P copula) {
        this(uniformWeightMatrix(correlSqr, nVariables), copula);
    }

    /**
     * Reactive single-factor constructor — mirrors C++
     * {@code LatentModel(const Handle<Quote>& singleFactorCorrel, Size nVariables, …)}.
     *
     * <p>The {@link Quote#value()} read at construction time supplies the
     * common single-factor weight: {@code β = sqrt(quote.value())} and idiosyncratic factor
     * {@code √(1 − quote.value())} for every name. The model registers as an {@link Observer} on the quote handle so
     * that any later quote update re-derives weights and triggers a rebuild of the copula via the supplied
     * {@code copulaFactory}.
     *
     * @param singleFactorCorrel observable correlation-squared quote
     * @param nVariables         number of latent variables
     * @param copula             initial copula (must be consistent with {@code singleFactorCorrel.value()})
     * @param copulaFactory      factory that maps the (re-derived) factor weights to a fresh copula instance; called on
     *                           every quote update
     */
    public LatentModel(final Handle< Quote > singleFactorCorrel, final int nVariables, final P copula,
            final Function< List< List< Double > >, P > copulaFactory) {
        this(uniformWeightMatrix(Math.sqrt(singleFactorCorrel.currentLink().value()), nVariables), copula);
        QL.require(singleFactorCorrel != null && !singleFactorCorrel.empty(),
                "singleFactorCorrel handle must not be empty");
        QL.require(copulaFactory != null, "copulaFactory must not be null when using the Handle<Quote> ctor");
        this.cachedMktFactor_ = singleFactorCorrel;
        this.copulaFactory_ = copulaFactory;
        // Register as Observer on the quote handle so update() fires on tick.
        this.cachedMktFactor_.addObserver(this);
        // Override idiosyncratic factor: C++ uses sqrt(1 - quote.value())
        // (NOT sqrt(1 - sqrt(quote.value())^2) — the quote stores correlation
        // not factor weight). Recompute to match.
        final double w = Math.sqrt(singleFactorCorrel.currentLink().value());
        for ( int i = 0; i < nVariables_; ++i ) {
            this.idiosyncFctrs_[i] = Math.sqrt(1.0 - singleFactorCorrel.currentLink().value());
            // Also override factorWeights_ to use the unsquared weight
            this.factorWeights_.get(i).set(0, w);
        }
    }

    /**
     * Static factory for an {@link LMIntegration} of the requested type and dimension.
     *
     * <p>Mirrors C++ {@code LatentModel::IntegrationFactory::createLMIntegration}.
     *
     * @param dimension number of integration dimensions
     * @param type      backend selection
     * @return a freshly-built integration backend
     */
    public static LMIntegration createLMIntegration(final int dimension, final IntegrationType type) {
        switch ( type ) {
        case GaussianQuadrature:
            return new GaussianQuadLMIntegration(dimension, DEFAULT_QUADRATURE_ORDER);
        case Trapezoid: {
            final List< org.jquantlib.math.integrals.Integrator > integrals = new ArrayList<>(dimension);
            for ( int i = 0; i < dimension; ++i ) {
                integrals.add(new org.jquantlib.math.integrals.TrapezoidIntegral<>(
                        org.jquantlib.math.integrals.TrapezoidIntegral.Default.class, 1.0e-4, 20));
            }
            // Domain tailored for the T distribution; too wide for
            // normals or high-order Ts (matches C++ comment).
            return new MultidimIntegralLMIntegration(integrals, -35.0, 35.0);
        }
        default:
            throw new IllegalArgumentException("Unknown latent model integration type: " + type);
        }
    }

    /** Default Gauss-Hermite backend with {@link #DEFAULT_QUADRATURE_ORDER}. */
    public static LMIntegration createLMIntegration(final int dimension) {
        return createLMIntegration(dimension, IntegrationType.GaussianQuadrature);
    }

    private static List< List< Double > > deepCopy(final List< List< Double > > src) {
        final List< List< Double > > out = new ArrayList<>(src.size());
        for ( final List< Double > row : src ) {
            out.add(new ArrayList<>(row));
        }
        return out;
    }

    private static List< List< Double > > rowVectorMatrix(final double[] v) {
        final List< List< Double > > out = new ArrayList<>(v.length);
        for ( final double w : v ) {
            out.add(new ArrayList<>(List.of(w)));
        }
        return out;
    }

    private static List< List< Double > > uniformWeightMatrix(final double w, final int nVariables) {
        final List< List< Double > > out = new ArrayList<>(nVariables);
        for ( int i = 0; i < nVariables; ++i ) {
            out.add(new ArrayList<>(List.of(w)));
        }
        return out;
    }

    /** Number of latent variables modelled. */
    public final int size() {
        return nVariables_;
    }

    /** Number of systemic factors. */
    public final int numFactors() {
        return nFactors_;
    }

    // ------------------------------------------------------------------------
    // Copula interface delegation
    // ------------------------------------------------------------------------

    /** Number of total free random factors; systemic + idiosyncratic. */
    public final int numTotalFactors() {
        return nVariables_ + nFactors_;
    }

    /** Provides values of the factors {@code a_{i,k}}. */
    public final List< List< Double > > factorWeights() {
        return Collections.unmodifiableList(factorWeights_);
    }

    /** Provides values of the normalised idiosyncratic factors {@code Z_i}. */
    public final double[] idiosyncFctrs() {
        return idiosyncFctrs_.clone();
    }

    /** Read-only access to the copula policy. */
    public final P copula() {
        return copula_;
    }

    public final double cumulativeY(final double val, final int iVariable) {
        return copula_.cumulativeY(val, iVariable);
    }

    public final double cumulativeZ(final double z) {
        return copula_.cumulativeZ(z);
    }

    public final double density(final List< Double > m) {
        return copula_.density(m);
    }

    public final double inverseCumulativeDensity(final double p, final int iFactor) {
        return copula_.inverseCumulativeDensity(p, iFactor);
    }

    public final double inverseCumulativeY(final double p, final int iVariable) {
        return copula_.inverseCumulativeY(p, iVariable);
    }

    // ------------------------------------------------------------------------
    // Integration facility
    // ------------------------------------------------------------------------

    public final double inverseCumulativeZ(final double p) {
        return copula_.inverseCumulativeZ(p);
    }

    /**
     * Inverts every cumulative random factor probability in the model (systemic + idiosyncratic).
     */
    public final double[] allFactorCumulInverter(final double[] probs) {
        return copula_.allFactorCumulInverter(probs);
    }

    /**
     * Latent variable correlation between latent variables {@code iVar1} and {@code iVar2}. For {@code i == j} returns
     * 1 (modulo rounding) by construction.
     */
    public final double latentVariableCorrel(final int iVar1, final int iVar2) {
        double init = (iVar1 == iVar2) ? idiosyncFctrs_[iVar1] * idiosyncFctrs_[iVar1] : 0.0;
        final List< Double > w1 = factorWeights_.get(iVar1);
        final List< Double > w2 = factorWeights_.get(iVar2);
        for ( int k = 0; k < nFactors_; ++k ) {
            init += w1.get(k) * w2.get(k);
        }
        return init;
    }

    /**
     * Value of the latent variable {@code Y_iVar} conditional on a sample of the {@code numTotalFactors} independent
     * factors (systemic followed by idiosyncratic).
     */
    public final double latentVarValue(final double[] allFactors, final int iVar) {
        QL.require(allFactors.length == numTotalFactors(), "allFactors has wrong length; expected numTotalFactors()");
        // C++: inner_product(factorWeights_[iVar].begin(), end, allFactors.begin(),
        //                    allFactors[numFactors()+iVar] * idiosyncFctrs_[iVar])
        double sum = allFactors[numFactors() + iVar] * idiosyncFctrs_[iVar];
        final List< Double > w = factorWeights_.get(iVar);
        for ( int k = 0; k < nFactors_; ++k ) {
            sum += w.get(k) * allFactors[k];
        }
        return sum;
    }

    /**
     * Integrates an arbitrary scalar function over the density domain (i.e., computes its expected value).
     *
     * <p>Composes the integrand with the copula density through a product:
     * {@code ∫ f(x) ρ(x) dx}.
     */
    public final double integratedExpectedValue(final Function< double[], Double > f) {
        return integration().integrate((final double[] x) -> {
            final List< Double > xList = new ArrayList<>(x.length);
            for ( final double v : x ) {
                xList.add(v);
            }
            return copula_.density(xList) * f.apply(x);
        });
    }

    // ------------------------------------------------------------------------
    // Observer / Observable wiring (Phase 4m.7c-b WI-2)
    // ------------------------------------------------------------------------

    /**
     * Integrates an arbitrary vector function over the density domain.
     *
     * <p>Composes the integrand with the copula density through a product:
     * {@code ∫ f(x) ρ(x) dx} elementwise.
     */
    public final double[] integratedExpectedValueV(final Function< double[], double[] > f) {
        return integration().integrateV((final double[] x) -> {
            final List< Double > xList = new ArrayList<>(x.length);
            for ( final double v : x ) {
                xList.add(v);
            }
            final double rho = copula_.density(xList);
            final double[] fx = f.apply(x);
            final double[] result = new double[fx.length];
            for ( int i = 0; i < fx.length; ++i ) {
                result[i] = rho * fx[i];
            }
            return result;
        });
    }

    /**
     * Integrable models must provide their integrator. The base implementation throws; subclasses (or callers wiring an
     * {@link LMIntegration} via the factory below) override.
     */
    protected LMIntegration integration() {
        throw new UnsupportedOperationException("Integration not implemented in Latent model.");
    }

    /**
     * Reactive update triggered by {@link #cachedMktFactor_}'s {@link Quote} notifying observers. Mirrors C++
     * {@code LatentModel::update}: re-derives factor weights and idiosyncratic factors from the new quote value,
     * rebuilds the copula via {@link #copulaFactory_}, then forwards the notification downstream.
     *
     * <p>No-op when the model was not constructed with the
     * {@link #LatentModel(Handle, int, CopulaPolicy, Function)} ctor (the downstream observers are still notified —
     * matches the polymorphic Observer contract).
     */
    @Override
    public void update() {
        if ( cachedMktFactor_ != null && copulaFactory_ != null && !cachedMktFactor_.empty() ) {
            final double rho = cachedMktFactor_.currentLink().value();
            final double w = Math.sqrt(rho);
            // Single-factor: rebuild factor weights and idiosyncratic factors.
            this.factorWeights_ = uniformWeightMatrix(w, nVariables_);
            this.idiosyncFctrs_ = new double[nVariables_];
            for ( int i = 0; i < nVariables_; ++i ) {
                this.idiosyncFctrs_[i] = Math.sqrt(1.0 - rho);
            }
            this.copula_ = copulaFactory_.apply(this.factorWeights_);
        }
        notifyObservers();
    }

    @Override
    public final void addObserver(final Observer observer) {
        delegatedObservable.addObserver(observer);
    }

    @Override
    public final int countObservers() {
        return delegatedObservable.countObservers();
    }

    @Override
    public final List< Observer > getObservers() {
        return delegatedObservable.getObservers();
    }

    @Override
    public final void deleteObserver(final Observer observer) {
        delegatedObservable.deleteObserver(observer);
    }

    @Override
    public final void deleteObservers() {
        delegatedObservable.deleteObservers();
    }

    @Override
    public final void notifyObservers() {
        delegatedObservable.notifyObservers();
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    @Override
    public final void notifyObservers(final Object arg) {
        delegatedObservable.notifyObservers(arg);
    }

    /** Read-only access to the cached single-factor correlation quote handle (may be {@code null}). */
    public final Handle< Quote > cachedMktFactor() {
        return cachedMktFactor_;
    }

    /** Choice of integration backend in {@link #createLMIntegration}. */
    public enum IntegrationType {
        /** N-dimensional Gauss-Hermite tensor-product quadrature (default). */
        GaussianQuadrature,
        /** N-dimensional trapezoid integration over a hyper-rectangle. */
        Trapezoid
    }
}
