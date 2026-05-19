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

import org.jquantlib.QL;
import org.jquantlib.math.distributions.CumulativeStudentDistribution;
import org.jquantlib.math.distributions.InverseCumulativeStudent;
import org.jquantlib.math.distributions.StudentDistribution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Student-T Latent Model's copula policy.
 *
 * <p>Java port of QuantLib v1.42.1
 * {@code ql/experimental/math/tcopulapolicy.{hpp,cpp}}.
 *
 * <p>Describes the copula of a set of normalised Student-T independent random
 * factors to be fed into the latent variable model. The latent model requires the independent variables to be of unit
 * variance, so the policy normalises each T variable by dividing by sqrt(nu/(nu-2)).
 *
 * <p>The C++ implementation uses {@code boost::math::students_t_distribution};
 * the Java port uses {@link org.jquantlib.math.distributions.StudentDistribution} (PDF),
 * {@link org.jquantlib.math.distributions.CumulativeStudentDistribution} (CDF) and
 * {@link org.jquantlib.math.distributions.InverseCumulativeStudent} (quantile via Newton iteration). The cumulative-Y /
 * inverse-cumulative-Y computations rely on the analytical convolution of odd-order T distributions implemented in
 * {@link CumulativeBehrensFisher} / {@link InverseCumulativeBehrensFisher}.
 *
 * <p>Phase 4m.6 — analog of Phase 4k {@link GaussianCopulaPolicy}.
 */
public class TCopulaPolicy implements CopulaPolicy {

    private final List< StudentDistribution > distributions_ = new ArrayList<>();
    private final List< CumulativeStudentDistribution > cumulatives_ = new ArrayList<>();
    private final List< InverseCumulativeStudent > inverseCumulatives_ = new ArrayList<>();
    private final List< Integer > tOrders_ = new ArrayList<>();
    private final List< Double > varianceFactors_ = new ArrayList<>();
    private final List< CumulativeBehrensFisher > latentVarsCumul_ = new ArrayList<>();
    private final List< InverseCumulativeBehrensFisher > latentVarsInverters_ = new ArrayList<>();
    public TCopulaPolicy() {
        this(Collections.emptyList(), new InitTraits());
    }

    public TCopulaPolicy(final List< List< Double > > factorWeights, final InitTraits vals) {
        for ( final int tOrder : vals.tOrders ) {
            QL.require(tOrder > 2, "Non finite variance T in latent model.");
            tOrders_.add(tOrder);
            distributions_.add(new StudentDistribution(tOrder));
            cumulatives_.add(new CumulativeStudentDistribution(tOrder));
            inverseCumulatives_.add(new InverseCumulativeStudent(tOrder));
            varianceFactors_.add(Math.sqrt((tOrder - 2.0) / tOrder));
        }

        for ( final List< Double > factorWeight : factorWeights ) {
            QL.require(vals.tOrders.size() == factorWeight.size() + 1,
                    "Incompatible number of T functions and number of factors.");

            double factorsNorm = 0.0;
            for ( final Double v : factorWeight ) {
                factorsNorm += v * v;
            }
            QL.require(factorsNorm < 1.0, "Non normal random factor combination.");
            final double idiosyncFctr = Math.sqrt(1.0 - factorsNorm);

            // linear comb factors adjusted for the variance renormalisation
            final List< Double > normFactorWeights = new ArrayList<>(factorWeight.size() + 1);
            for ( int iFactor = 0; iFactor < factorWeight.size(); ++iFactor ) {
                normFactorWeights.add(factorWeight.get(iFactor) * varianceFactors_.get(iFactor));
            }
            // idiosyncratic term — all Z factors assumed identical
            normFactorWeights.add(idiosyncFctr * varianceFactors_.get(varianceFactors_.size() - 1));
            latentVarsCumul_.add(new CumulativeBehrensFisher(vals.tOrders, normFactorWeights));
            latentVarsInverters_.add(new InverseCumulativeBehrensFisher(vals.tOrders, normFactorWeights));
        }
    }

    public TCopulaPolicy(final List< List< Double > > factorWeights) {
        this(factorWeights, new InitTraits());
    }

    // ---- helpers for tests ----
    static List< List< Double > > singleFactor(final double... weights) {
        final List< Double > row = new ArrayList<>(weights.length);
        for ( final double v : weights ) {
            row.add(v);
        }
        final List< List< Double > > w = new ArrayList<>();
        w.add(row);
        return w;
    }

    static List< Integer > orders(final int... vals) {
        return new ArrayList<>(Arrays.asList(boxArray(vals)));
    }

    private static Integer[] boxArray(final int[] vals) {
        final Integer[] r = new Integer[vals.length];
        for ( int i = 0; i < vals.length; ++i ) {
            r[i] = vals[i];
        }
        return r;
    }

    /** Number of independent random factors. */
    public int numFactors() {
        return latentVarsInverters_.size() + varianceFactors_.size() - 1;
    }

    /** Returns a copy of the initialisation arguments. */
    public InitTraits getInitTraits() {
        final List< Integer > orders = new ArrayList<>(tOrders_);
        return new InitTraits(orders);
    }

    public List< Double > varianceFactors() {
        return Collections.unmodifiableList(varianceFactors_);
    }

    /**
     * Cumulative probability of a given latent variable.
     *
     * @param val       argument
     * @param iVariable index of the requested variable
     */
    public double cumulativeY(final double val, final int iVariable) {
        return latentVarsCumul_.get(iVariable).op(val);
    }

    /** Cumulative probability of the idiosyncratic factor. */
    public double cumulativeZ(final double z) {
        final int last = cumulatives_.size() - 1;
        return cumulatives_.get(last).op(z / varianceFactors_.get(last));
    }

    /**
     * Probability density of a given realisation of values of the systemic factors. Independent factors → product of
     * individual T densities, each normalised by its variance factor.
     */
    public double density(final List< Double > m) {
        double prod = 1.0;
        for ( int i = 0; i < m.size(); ++i ) {
            final double s = varianceFactors_.get(i);
            prod *= distributions_.get(i).op(m.get(i) / s) / s;
        }
        return prod;
    }

    /** Inverse of the cumulative distribution of the modelled latent variable. */
    public double inverseCumulativeY(final double p, final int iVariable) {
        return latentVarsInverters_.get(iVariable).op(p);
    }

    /** Inverse of the cumulative distribution of the idiosyncratic factor. */
    public double inverseCumulativeZ(final double p) {
        final int last = inverseCumulatives_.size() - 1;
        return inverseCumulatives_.get(last).op(p) * varianceFactors_.get(last);
    }

    /** Inverse of the cumulative distribution of the systemic factor iFactor. */
    public double inverseCumulativeDensity(final double p, final int iFactor) {
        return inverseCumulatives_.get(iFactor).op(p) * varianceFactors_.get(iFactor);
    }

    /**
     * Maps a vector of uniform variates to the underlying factor distribution via inverse-cumulative transformation.
     * The first {@code varianceFactors_.size() - 1} entries map to systemic factors; the rest map to idiosyncratic
     * factors.
     */
    public double[] allFactorCumulInverter(final double[] probs) {
        final double[] result = new double[probs.length];
        final int systemicCount = varianceFactors_.size() - 1;
        for ( int i = 0; i < systemicCount; ++i ) {
            result[i] = inverseCumulativeDensity(probs[i], i);
        }
        for ( int i = systemicCount; i < probs.length; ++i ) {
            result[i] = inverseCumulativeZ(probs[i]);
        }
        return result;
    }

    /** Convenience overload taking a {@link List}. */
    public List< Double > allFactorCumulInverter(final List< Double > probs) {
        final double[] arr = new double[probs.size()];
        for ( int i = 0; i < probs.size(); ++i ) {
            arr[i] = probs.get(i);
        }
        final double[] out = allFactorCumulInverter(arr);
        final List< Double > result = new ArrayList<>(out.length);
        for ( final double v : out ) {
            result.add(v);
        }
        return result;
    }

    /**
     * Initialisation traits storing the per-factor T orders. Indices follow the convention
     * {@code [factor_1, ..., factor_N, idiosyncratic]}.
     */
    public static final class InitTraits {
        public final List< Integer > tOrders;

        public InitTraits() {
            this.tOrders = new ArrayList<>();
        }

        public InitTraits(final List< Integer > tOrders) {
            this.tOrders = new ArrayList<>(tOrders);
        }

        public InitTraits(final int... tOrders) {
            this.tOrders = new ArrayList<>(tOrders.length);
            for ( final int v : tOrders ) {
                this.tOrders.add(v);
            }
        }
    }
}
