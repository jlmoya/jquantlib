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
package org.jquantlib.experimental.math;

import java.util.List;

/**
 * Common interface for the copula-policy template parameter
 * {@code copulaPolicyImpl} of QuantLib's {@code LatentModel} (v1.42.1
 * {@code ql/experimental/math/latentmodel.hpp}). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>Java cannot replicate C++ template parameterisation directly; this
 * interface formalises the duck-typed contract the C++ policies satisfy
 * (cumulativeY, cumulativeZ, density, inverseCumulativeY, ...) so that the
 * Java {@code LatentModel<P extends CopulaPolicy>} generic can dispatch
 * uniformly.
 *
 * <p>Concrete policies (GaussianCopulaPolicy, TCopulaPolicy, ...) implement
 * this interface and may carry policy-specific initialisation traits as
 * inner classes.
 */
public interface CopulaPolicy {

    /** Number of independent random factors (systemic + idiosyncratic). */
    int numFactors();

    /**
     * Cumulative probability of the latent variable {@code Y_iVariable} taking
     * the value {@code val}.
     */
    double cumulativeY(double val, int iVariable);

    /** Cumulative distribution of the idiosyncratic factor. */
    double cumulativeZ(double z);

    /**
     * Probability density evaluated at a vector realisation of the systemic
     * factors.
     */
    double density(List<Double> m);

    /**
     * Inverse cumulative distribution of the modelled latent variable
     * {@code Y_iVariable}.
     */
    double inverseCumulativeY(double p, int iVariable);

    /** Inverse cumulative distribution of the idiosyncratic factor. */
    double inverseCumulativeZ(double p);

    /** Inverse cumulative distribution of systemic factor {@code iFactor}. */
    double inverseCumulativeDensity(double p, int iFactor);

    /**
     * Maps a vector of uniform variates to the underlying factor distribution
     * via inverse-cumulative transformation.
     */
    double[] allFactorCumulInverter(double[] probs);
}
