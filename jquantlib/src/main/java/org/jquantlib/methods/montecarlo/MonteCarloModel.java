/*
 Copyright (C) 2007 Richard Gomes

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

/*
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.methods.montecarlo;

import org.jquantlib.math.statistics.Statistics;

/**
 * General-purpose Monte Carlo model for path samples.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/methods/montecarlo/montecarlomodel.hpp} (Phase 5h.5-MC-INFRA WI-5).
 *
 * <p>The C++ class is parameterized over three template arguments —
 * {@code MC} (the multi-/single-variate trait), {@code RNG} (the random-number trait) and {@code S} (the statistics
 * accumulator). Java lacks template-template parameters, so this port collapses the two relevant traits to the bare
 * path-generator and path-pricer types (parameter {@code PathType}). The statistics accumulator is the concrete
 * {@link Statistics} class hierarchy (which provides {@code add}, {@code mean}, {@code samples},
 * {@code errorEstimate}).
 *
 * <p>Both single-path ({@code PathType=Path}) and multi-path
 * ({@code PathType=MultiPath}) variants share the same logic: the generator returns a {@code Sample<PathType>}, the
 * pricer returns the payoff value, and the accumulator records (value, weight). Antithetic variates and the (price -
 * cv-price + cv-value) control-variate adjustment are supported.
 *
 * @param <PathType> {@link Path} for single-asset MC, {@link MultiPath} for multi-asset MC.
 * @author JQuantLib
 */
public class MonteCarloModel< PathType > {

    //
    // shared interfaces (decoupled from concrete classes so this model
    // works equally well with PathGenerator/MultiPathGenerator and any
    // PathPricer specialization).
    //

    private final PathGeneratorAdapter< PathType > pathGenerator_;
    private final PathPricer< PathType > pathPricer_;
    private final Statistics sampleAccumulator_;

    //
    // private fields
    //
    private final boolean isAntitheticVariate_;
    private final PathPricer< PathType > cvPathPricer_;
    private final double cvOptionValue_;
    private final boolean isControlVariate_;
    private final PathGeneratorAdapter< PathType > cvPathGenerator_;
    /**
     * Plain MC; no control variate.
     */
    public MonteCarloModel(final PathGeneratorAdapter< PathType > pathGenerator,
            final PathPricer< PathType > pathPricer, final Statistics sampleAccumulator,
            final boolean antitheticVariate) {
        this(pathGenerator, pathPricer, sampleAccumulator, antitheticVariate, null, 0.0, null);
    }
    /**
     * MC with optional control variate. {@code cvPathPricer == null} disables the CV adjustment.
     */
    public MonteCarloModel(final PathGeneratorAdapter< PathType > pathGenerator,
            final PathPricer< PathType > pathPricer, final Statistics sampleAccumulator,
            final boolean antitheticVariate, final PathPricer< PathType > cvPathPricer, final double cvOptionValue,
            final PathGeneratorAdapter< PathType > cvPathGenerator) {
        this.pathGenerator_ = pathGenerator;
        this.pathPricer_ = pathPricer;
        this.sampleAccumulator_ = sampleAccumulator;
        this.isAntitheticVariate_ = antitheticVariate;
        this.cvPathPricer_ = cvPathPricer;
        this.cvOptionValue_ = cvOptionValue;
        this.cvPathGenerator_ = cvPathGenerator;
        this.isControlVariate_ = (cvPathPricer != null);
    }

    /**
     * Adds {@code samples} new draws to the underlying statistics accumulator. Mirrors
     * {@code MonteCarloModel::addSamples} from C++.
     */
    public void addSamples(final int samples) {
        for ( int j = 1; j <= samples; j++ ) {
            final Sample< PathType > path = pathGenerator_.next();
            double price = pathPricer_.op(path.value());

            if ( isControlVariate_ ) {
                if ( cvPathGenerator_ == null ) {
                    price += cvOptionValue_ - cvPathPricer_.op(path.value());
                } else {
                    final Sample< PathType > cvPath = cvPathGenerator_.next();
                    price += cvOptionValue_ - cvPathPricer_.op(cvPath.value());
                }
            }

            if ( isAntitheticVariate_ ) {
                final Sample< PathType > atPath = pathGenerator_.antithetic();
                double price2 = pathPricer_.op(atPath.value());
                if ( isControlVariate_ ) {
                    if ( cvPathGenerator_ == null ) {
                        price2 += cvOptionValue_ - cvPathPricer_.op(atPath.value());
                    } else {
                        final Sample< PathType > cvPath = cvPathGenerator_.antithetic();
                        price2 += cvOptionValue_ - cvPathPricer_.op(cvPath.value());
                    }
                }
                sampleAccumulator_.add((price + price2) / 2.0, path.weight());
            } else {
                sampleAccumulator_.add(price, path.weight());
            }
        }
    }

    //
    // constructors
    //

    public Statistics sampleAccumulator() /* @ReadOnly */ {
        return sampleAccumulator_;
    }

    /**
     * Anything that can produce a {@code Sample<PathType>} on demand.
     */
    public interface PathGeneratorAdapter< PathType > {
        Sample< PathType > next();

        Sample< PathType > antithetic();
    }

    /**
     * Adapter for {@link PathGenerator} (single-asset).
     */
    public static final class PathGeneratorAdapterImpl implements PathGeneratorAdapter< Path > {
        private final PathGenerator< ? > g;

        public PathGeneratorAdapterImpl(final PathGenerator< ? > g) {
            this.g = g;
        }

        @Override
        public Sample< Path > next() {
            return g.next();
        }

        @Override
        public Sample< Path > antithetic() {
            return g.antithetic();
        }
    }

    /**
     * Adapter for {@link MultiPathGenerator} (multi-asset).
     */
    public static final class MultiPathGeneratorAdapterImpl implements PathGeneratorAdapter< MultiPath > {
        private final MultiPathGenerator< ? > g;

        public MultiPathGeneratorAdapterImpl(final MultiPathGenerator< ? > g) {
            this.g = g;
        }

        @Override
        public Sample< MultiPath > next() {
            return g.next();
        }

        @Override
        public Sample< MultiPath > antithetic() {
            return g.antithetic();
        }
    }
}
