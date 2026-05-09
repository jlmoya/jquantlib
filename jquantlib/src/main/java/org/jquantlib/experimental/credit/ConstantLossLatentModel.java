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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jquantlib.QL;
import org.jquantlib.experimental.math.CopulaPolicy;
import org.jquantlib.time.Date;

/**
 * Constant deterministic loss-amount default latent model.
 *
 * <p>Java port of QuantLib v1.42.1 template
 * {@code template <class copulaPolicy> class ConstantLossLatentmodel}
 * (declared in {@code ql/experimental/credit/constantlosslatentmodel.hpp}).
 *
 * <p>Adds constant per-name recoveries to the {@link DefaultLatentModel}
 * base. Integrable implementation; serves as the recovery-supplying
 * "engine" of the homogeneous and inhomogeneous pool loss models and the
 * binomial loss model.
 *
 * @param <P> the {@link CopulaPolicy} subtype controlling distributions
 */
public class ConstantLossLatentModel<P extends CopulaPolicy> extends DefaultLatentModel<P> {

    private final List<Double> recoveries_;

    public ConstantLossLatentModel(final List<List<Double>> factorWeights,
                                   final List<Double> recoveries,
                                   final P copula,
                                   final IntegrationType integralType) {
        super(factorWeights, copula, integralType);
        QL.require(recoveries.size() == factorWeights.size(),
                "Incompatible factors and recovery sizes.");
        this.recoveries_ = new ArrayList<>(recoveries);
    }

    public ConstantLossLatentModel(final double correlSqr,
                                   final List<Double> recoveries,
                                   final int nVariables,
                                   final P copula,
                                   final IntegrationType integralType) {
        super(correlSqr, nVariables, copula, integralType);
        QL.require(recoveries.size() == nVariables,
                "Incompatible model and recovery sizes.");
        this.recoveries_ = new ArrayList<>(recoveries);
    }

    /** Conditional recovery, no time / market factor dependence in this model. */
    public final double conditionalRecovery(final Date d,
                                            final int iName,
                                            final double[] mktFactors) {
        return recoveries_.get(iName);
    }

    /** Conditional recovery taking unconditional default probability. */
    public final double conditionalRecovery(final double uncondDefP,
                                            final int iName,
                                            final double[] mktFactors) {
        return recoveries_.get(iName);
    }

    /** Conditional recovery taking the inverse cumulative directly. */
    public final double conditionalRecoveryInvP(final double invUncondDefP,
                                                final int iName,
                                                final double[] mktFactors) {
        return recoveries_.get(iName);
    }

    /** Conditional recovery taking a latent variable sample. */
    public final double conditionalRecovery(final double latentVarSample,
                                            final int iName,
                                            final Date d) {
        return recoveries_.get(iName);
    }

    /** Read-only access to the constant per-name recoveries. */
    public final List<Double> recoveries() {
        return Collections.unmodifiableList(recoveries_);
    }

    /** Expected recovery (no time dependence in this model). */
    public final double expectedRecovery(final Date d, final int iName, final DefaultProbKey defKeys) {
        return recoveries_.get(iName);
    }
}
