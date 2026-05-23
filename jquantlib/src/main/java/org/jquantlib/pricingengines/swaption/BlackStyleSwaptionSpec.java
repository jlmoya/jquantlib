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
 Copyright (C) 2015, 2016, 2017 Peter Caspers

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
 */
package org.jquantlib.pricingengines.swaption;

import org.jquantlib.instruments.Option;
import org.jquantlib.model.VolatilityType;

/**
 * Value/vega/delta spec for the generic Black-style swaption engine.
 * <p>
 * Port of the {@code detail::Black76Spec} / {@code detail::BachelierSpec}
 * structs nested in C++ v1.42.1
 * {@code ql/pricingengines/swaption/blackswaptionengine.hpp}. C++ uses these
 * as compile-time template parameters of {@code BlackStyleSwaptionEngine<Spec>};
 * Java cannot capture an entire functor at compile time, so the spec is a
 * sealed interface with a fixed pair of permitted implementations
 * ({@link Black76Spec}, {@link BachelierSpec}).
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 * <li>C++ {@code Black76Spec::value} accepts a {@code displacement} that is
 *     forwarded to {@code blackFormula}; C++ {@code BachelierSpec::value} also
 *     accepts a {@code displacement} parameter but discards it. The Java
 *     interface keeps the parameter on every method so call sites are uniform;
 *     {@link BachelierSpec} simply ignores it. This matches the C++ signature
 *     exactly (both specs share the same five-arg arity).</li>
 * <li>The C++ specs are stateless structs (no fields). Java uses
 *     parameterless {@code record}s with no components; instances are
 *     interchangeable, so {@link #BLACK76} and {@link #BACHELIER} singletons
 *     are exposed for convenience.</li>
 * </ul>
 *
 * @see BlackStyleSwaptionEngine
 * @see BlackSwaptionEngine
 * @see BachelierSwaptionEngine
 */
public sealed interface BlackStyleSwaptionSpec permits BlackStyleSwaptionSpec.Black76Spec,
        BlackStyleSwaptionSpec.BachelierSpec {

    /** Singleton {@link Black76Spec}. */
    Black76Spec BLACK76 = new Black76Spec();

    /** Singleton {@link BachelierSpec}. */
    BachelierSpec BACHELIER = new BachelierSpec();

    /** Volatility convention this spec computes prices under. */
    VolatilityType type();

    /**
     * Black-style swaption value. Mirrors C++ {@code Spec::value}.
     */
    double value(Option.Type type, double strike, double atmForward, double stdDev, double annuity,
            double displacement);

    /**
     * Vega (sensitivity to volatility, scaled by {@code sqrt(exerciseTime)}).
     * Mirrors C++ {@code Spec::vega}.
     */
    double vega(double strike, double atmForward, double stdDev, double exerciseTime, double annuity,
            double displacement);

    /** Delta (sensitivity to forward). Mirrors C++ {@code Spec::delta}. */
    double delta(Option.Type type, double strike, double atmForward, double stdDev, double annuity,
            double displacement);

    /**
     * Shifted-lognormal Black-76 spec. Mirrors C++ v1.42.1
     * {@code detail::Black76Spec} (blackswaptionengine.hpp:81-102).
     */
    record Black76Spec() implements BlackStyleSwaptionSpec {

        @Override
        public VolatilityType type() {
            return VolatilityType.ShiftedLognormal;
        }

        @Override
        public double value(final Option.Type type, final double strike, final double atmForward, final double stdDev,
                final double annuity, final double displacement) {
            return org.jquantlib.pricingengines.BlackFormula.blackFormula(type, strike, atmForward, stdDev, annuity,
                    displacement);
        }

        @Override
        public double vega(final double strike, final double atmForward, final double stdDev, final double exerciseTime,
                final double annuity, final double displacement) {
            return Math.sqrt(exerciseTime) * org.jquantlib.pricingengines.BlackFormula
                    .blackFormulaStdDevDerivative(strike, atmForward, stdDev, annuity, displacement);
        }

        @Override
        public double delta(final Option.Type type, final double strike, final double atmForward, final double stdDev,
                final double annuity, final double displacement) {
            return org.jquantlib.pricingengines.BlackFormula
                    .blackFormulaForwardDerivative(type, strike, atmForward, stdDev, annuity, displacement);
        }
    }

    /**
     * Normal Bachelier spec. Mirrors C++ v1.42.1
     * {@code detail::BachelierSpec} (blackswaptionengine.hpp:105-125).
     * The {@code displacement} argument is accepted for signature symmetry with
     * {@link Black76Spec} but discarded by all three methods, mirroring the C++
     * spec exactly.
     */
    record BachelierSpec() implements BlackStyleSwaptionSpec {

        @Override
        public VolatilityType type() {
            return VolatilityType.Normal;
        }

        @Override
        public double value(final Option.Type type, final double strike, final double atmForward, final double stdDev,
                final double annuity, final double displacement) {
            return org.jquantlib.pricingengines.BlackFormula.bachelierBlackFormula(type, strike, atmForward, stdDev,
                    annuity);
        }

        @Override
        public double vega(final double strike, final double atmForward, final double stdDev, final double exerciseTime,
                final double annuity, final double displacement) {
            return Math.sqrt(exerciseTime) * org.jquantlib.pricingengines.BlackFormula
                    .bachelierBlackFormulaStdDevDerivative(strike, atmForward, stdDev, annuity);
        }

        @Override
        public double delta(final Option.Type type, final double strike, final double atmForward, final double stdDev,
                final double annuity, final double displacement) {
            return org.jquantlib.pricingengines.BlackFormula
                    .bachelierBlackFormulaForwardDerivative(type, strike, atmForward, stdDev, annuity);
        }
    }
}
