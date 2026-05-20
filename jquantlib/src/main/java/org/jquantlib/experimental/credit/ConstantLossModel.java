/*
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

import java.util.List;
import java.util.function.Function;

import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.time.Date;

/**
 * {@link DefaultLossModel} adapter around {@link ConstantLossLatentModel}.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class copulaPolicy> class ConstantLossModel} (declared in
 * {@code ql/experimental/credit/constantlosslatentmodel.hpp}, pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}).
 *
 * <p>The C++ class multiply-inherits from
 * {@code ConstantLossLatentmodel<copulaPolicy>} (which extends
 * {@link DefaultLatentModel}) and {@link DefaultLossModel}. Java has no
 * multiple inheritance, so this port mirrors the existing
 * {@link GaussianLHPLossModel} pattern: extend {@link DefaultLossModel} and
 * compose a {@link ConstantLossLatentModel} as a delegate. The
 * {@code DefaultLossModel} virtual methods ({@code defaultCorrelation},
 * {@code probAtLeastNEvents}, {@code expectedRecovery}) forward to the
 * latent-model implementations.
 *
 * <p>While the latent model alone could not provide distribution-type losses
 * (e.g. expected tranche losses) because it lacks an integration algorithm,
 * this model still serves to allow pricing of digital-type products like
 * {@link NthToDefault}.
 *
 * <p>Phase 1 closure (A3-A-v2 TODO #553).
 *
 * @param <P> the {@link CopulaPolicy} subtype controlling distributions
 */
public class ConstantLossModel< P extends CopulaPolicy > extends DefaultLossModel {

    private final ConstantLossLatentModel< P > latentModel_;

    /** Multi-factor constructor (mirrors C++ ctor taking {@code factorWeights}). */
    public ConstantLossModel(final List< List< Double > > factorWeights, final List< Double > recoveries,
            final P copula, final LatentModel.IntegrationType integralType) {
        this.latentModel_ = new ConstantLossLatentModel<>(factorWeights, recoveries, copula, integralType);
        this.latentModel_.addObserver(this);
    }

    /** Single-factor constant-correlation constructor (mirrors C++ ctor taking {@code correlSqr}). */
    public ConstantLossModel(final double correlSqr, final List< Double > recoveries, final int nVariables,
            final P copula, final LatentModel.IntegrationType integralType) {
        this.latentModel_ = new ConstantLossLatentModel<>(correlSqr, recoveries, nVariables, copula, integralType);
        this.latentModel_.addObserver(this);
    }

    /**
     * Reactive single-factor constructor — mirrors C++
     * {@code ConstantLossModel(const Handle<Quote>& mktCorrel, …)}. The model registers as an observer on the
     * underlying {@link ConstantLossLatentModel}, which itself observes {@code singleFactorCorrel}, so quote ticks
     * propagate through this adapter into the {@link Basket}.
     */
    public ConstantLossModel(final Handle< Quote > singleFactorCorrel, final List< Double > recoveries,
            final int nVariables, final P copula, final LatentModel.IntegrationType integralType,
            final Function< List< List< Double > >, P > copulaFactory) {
        this.latentModel_ = new ConstantLossLatentModel<>(singleFactorCorrel, recoveries, nVariables, copula,
                integralType, copulaFactory);
        this.latentModel_.addObserver(this);
    }

    /** Read-only access to the per-name recoveries. */
    public final List< Double > recoveries() {
        return latentModel_.recoveries();
    }

    /** Forwards to {@link ConstantLossLatentModel#expectedRecovery(Date, int, DefaultProbKey)}. */
    @Override
    public double expectedRecovery(final Date d, final int iName, final DefaultProbKey key) {
        return latentModel_.expectedRecovery(d, iName, key);
    }

    /** Forwards to {@link DefaultLatentModel#defaultCorrelation(Date, int, int)}. */
    @Override
    public double defaultCorrelation(final Date d, final int iName, final int jName) {
        return latentModel_.defaultCorrelation(d, iName, jName);
    }

    /** Forwards to {@link DefaultLatentModel#probAtLeastNEvents(int, Date)}. */
    @Override
    public double probAtLeastNEvents(final int n, final Date d) {
        return latentModel_.probAtLeastNEvents(n, d);
    }

    /** Direct delegate access (e.g. to call {@code conditionalDefaultProbability}). */
    public final ConstantLossLatentModel< P > latentModel() {
        return latentModel_;
    }

    @Override
    protected void resetModel() {
        // update the default latent model we delegate to so it picks up the
        // new basket (forces interface — mirrors C++ resetBasket call)
        latentModel_.resetBasket(basket);
    }
}
