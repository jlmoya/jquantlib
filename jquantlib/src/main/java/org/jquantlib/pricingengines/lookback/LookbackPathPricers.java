/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Lew Wei Hao

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/

 QuantLib is free software: you can redistribute it and/or modify it
 under the terms of the QuantLib license.  You should have received a
 copy of the license along with this program; if not, please email
 <quantlib-dev@lists.sf.net>. The license is also available online at
 <https://www.quantlib.org/license.shtml>.
*/

package org.jquantlib.pricingengines.lookback;

import org.jquantlib.QL;
import org.jquantlib.instruments.FloatingTypePayoff;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.lang.exceptions.LibraryException;
import org.jquantlib.methods.montecarlo.Path;
import org.jquantlib.methods.montecarlo.PathPricer;
import org.jquantlib.time.TimeGrid;

/**
 * Path-pricers used by {@link MCLookbackEngine} variants.
 *
 * <p>Java port of the four C++ {@code Lookback*PathPricer} classes from
 * {@code ql/pricingengines/lookback/mclookbackengine.cpp} (QuantLib
 * v1.42.1, Phase 5e.5b-CFC-d-183). One pricer per instrument flavour:
 * fixed strike, partial-time fixed strike, floating strike, partial-time
 * floating strike — each continuous-monitoring (sample running extremum
 * over all path nodes after the initial point).
 */
final class LookbackPathPricers {

    private LookbackPathPricers() { /* static-only */ }

    //
    // Continuous fixed-strike — plain-vanilla payoff vs. running extremum.
    //
    static final class Fixed extends PathPricer<Path> {
        private final PlainVanillaPayoff payoff_;
        private final double discount_;

        Fixed(final Option.Type type, final double strike, final double discount) {
            QL.require(strike >= 0.0, "strike less than zero not allowed");
            this.payoff_ = new PlainVanillaPayoff(type, strike);
            this.discount_ = discount;
        }

        @Override
        public Double op(final Path path) {
            QL.require(!path.empty(), "the path cannot be empty");
            final double[] v = path.values();
            final double underlying;
            switch (payoff_.optionType()) {
                case Put:
                    underlying = min(v, 1, v.length);
                    break;
                case Call:
                    underlying = max(v, 1, v.length);
                    break;
                default:
                    throw new LibraryException("unknown option type");
            }
            return payoff_.get(underlying) * discount_;
        }
    }

    //
    // Continuous partial-time fixed-strike: max/min taken from lookback
    // start onwards.
    //
    static final class PartialFixed extends PathPricer<Path> {
        private final double lookbackStart_;
        private final PlainVanillaPayoff payoff_;
        private final double discount_;

        PartialFixed(final double lookbackStart, final Option.Type type,
                     final double strike, final double discount) {
            QL.require(strike >= 0.0, "strike less than zero not allowed");
            this.lookbackStart_ = lookbackStart;
            this.payoff_ = new PlainVanillaPayoff(type, strike);
            this.discount_ = discount;
        }

        @Override
        public Double op(final Path path) {
            QL.require(!path.empty(), "the path cannot be empty");
            final TimeGrid grid = path.timeGrid();
            final int startIndex = grid.closestIndex(lookbackStart_);
            final double[] v = path.values();
            final double underlying;
            switch (payoff_.optionType()) {
                case Put:
                    underlying = min(v, startIndex + 1, v.length);
                    break;
                case Call:
                    underlying = max(v, startIndex + 1, v.length);
                    break;
                default:
                    throw new LibraryException("unknown option type");
            }
            return payoff_.get(underlying) * discount_;
        }
    }

    //
    // Continuous floating-strike: strike = running extremum, payoff
    // against terminal price.
    //
    static final class Floating extends PathPricer<Path> {
        private final FloatingTypePayoff payoff_;
        private final double discount_;

        Floating(final Option.Type type, final double discount) {
            this.payoff_ = new FloatingTypePayoff(type);
            this.discount_ = discount;
        }

        @Override
        public Double op(final Path path) {
            QL.require(!path.empty(), "the path cannot be empty");
            final double[] v = path.values();
            final double terminalPrice = path.back();
            final double strike;
            switch (payoff_.optionType()) {
                case Call:
                    strike = min(v, 1, v.length);
                    break;
                case Put:
                    strike = max(v, 1, v.length);
                    break;
                default:
                    throw new LibraryException("unknown option type");
            }
            return payoff_.get(terminalPrice, strike) * discount_;
        }
    }

    //
    // Continuous partial-time floating-strike: extremum taken only over
    // [start, lookbackEnd].
    //
    static final class PartialFloating extends PathPricer<Path> {
        private final double lookbackEnd_;
        private final FloatingTypePayoff payoff_;
        private final double discount_;

        PartialFloating(final double lookbackEnd, final Option.Type type,
                        final double discount) {
            this.lookbackEnd_ = lookbackEnd;
            this.payoff_ = new FloatingTypePayoff(type);
            this.discount_ = discount;
        }

        @Override
        public Double op(final Path path) {
            QL.require(!path.empty(), "the path cannot be empty");
            final TimeGrid grid = path.timeGrid();
            final int endIndex = grid.closestIndex(lookbackEnd_);
            final double[] v = path.values();
            final double terminalPrice = path.back();
            final double strike;
            switch (payoff_.optionType()) {
                case Call:
                    strike = min(v, 1, endIndex + 1);
                    break;
                case Put:
                    strike = max(v, 1, endIndex + 1);
                    break;
                default:
                    throw new LibraryException("unknown option type");
            }
            return payoff_.get(terminalPrice, strike) * discount_;
        }
    }

    //
    // local extremum helpers (mirror C++ std::min_element / max_element
    // over [begin, end) — end is exclusive).
    //
    private static double min(final double[] v, final int fromInclusive, final int toExclusive) {
        double m = v[fromInclusive];
        for (int i = fromInclusive + 1; i < toExclusive; i++) {
            if (v[i] < m) {
                m = v[i];
            }
        }
        return m;
    }

    private static double max(final double[] v, final int fromInclusive, final int toExclusive) {
        double m = v[fromInclusive];
        for (int i = fromInclusive + 1; i < toExclusive; i++) {
            if (v[i] > m) {
                m = v[i];
            }
        }
        return m;
    }
}
