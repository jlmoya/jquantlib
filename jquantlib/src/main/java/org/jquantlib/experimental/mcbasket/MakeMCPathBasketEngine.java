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

/*
 Copyright (C) 2008 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import org.jquantlib.QL;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Monte Carlo path-dependent basket engine factory (fluent builder).
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcpathbasketengine.hpp}::
 * {@code template<class RNG = PseudoRandom, class S = Statistics> class MakeMCPathBasketEngine}
 * (lines 222-245, builder bodies 247-338). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is templated on {@code <RNG = PseudoRandom, S = Statistics>};
 * the Java port specialises both ({@code PseudoRandom} = Mersenne-Twister +
 * InverseCumulativeNormal, {@code Statistics}), consistent with the
 * already-ported {@link MCPathBasketEngine}, which is itself RNG/S-collapsed.
 * The terminal conversion {@code operator ext::shared_ptr<PricingEngine>() const}
 * (C++ lines 323-338) is realised as the {@link #value()} method, matching the
 * established JQuantLib builder idiom
 * ({@link org.jquantlib.pricingengines.basket.MakeMCEuropeanBasketEngine#value()}).
 *
 * <p>C++ defaults (member initialisers lines 240-244 + ctor 247-251):
 * {@code antithetic = false}, {@code controlVariate = false},
 * {@code steps = Null<Size>()}, {@code stepsPerYear = Null<Size>()},
 * {@code samples = Null<Size>()}, {@code maxSamples = Null<Size>()},
 * {@code tolerance = Null<Real>()}, {@code brownianBridge = false},
 * {@code seed = 0}. In Java {@code Null<Size>()} maps to
 * {@link McSimulation#NULL_SAMPLES} ({@code Integer.MAX_VALUE}) and
 * {@code Null<Real>()} to {@link McSimulation#NULL_TOLERANCE} ({@code NaN}).
 *
 * @see MCPathBasketEngine
 */
public class MakeMCPathBasketEngine {

    private final StochasticProcessArray process_;
    private boolean antithetic_ = false;
    private boolean controlVariate_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private boolean brownianBridge_ = false;
    private long seed_ = 0L;

    // C++ mcpathbasketengine.hpp:225, 247-251 — explicit ctor takes the process.
    public MakeMCPathBasketEngine(final StochasticProcessArray process) {
        QL.require(process != null, "null process array");
        this.process_ = process;
    }

    // C++ mcpathbasketengine.hpp:253-258
    public MakeMCPathBasketEngine withSteps(final int steps) {
        this.steps_ = steps;
        return this;
    }

    // C++ mcpathbasketengine.hpp:260-265
    public MakeMCPathBasketEngine withStepsPerYear(final int steps) {
        this.stepsPerYear_ = steps;
        return this;
    }

    // C++ mcpathbasketengine.hpp:267-274 — guards "tolerance already set"
    public MakeMCPathBasketEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }

    // C++ mcpathbasketengine.hpp:276-286 — guards "number of samples already
    // set" and that the RNG policy allows an error estimate. PseudoRandom
    // always allows an error estimate, so the second guard is a no-op here.
    public MakeMCPathBasketEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
        this.tolerance_ = tolerance;
        return this;
    }

    // C++ mcpathbasketengine.hpp:288-293
    public MakeMCPathBasketEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    // C++ mcpathbasketengine.hpp:295-300
    public MakeMCPathBasketEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    // C++ mcpathbasketengine.hpp:302-307 — withBrownianBridge(bool b = true)
    public MakeMCPathBasketEngine withBrownianBridge(final boolean brownianBridge) {
        this.brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCPathBasketEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    // C++ mcpathbasketengine.hpp:309-314 — withAntitheticVariate(bool b = true)
    public MakeMCPathBasketEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCPathBasketEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    // C++ mcpathbasketengine.hpp:316-321 — withControlVariate(bool b = true)
    public MakeMCPathBasketEngine withControlVariate(final boolean b) {
        this.controlVariate_ = b;
        return this;
    }

    public MakeMCPathBasketEngine withControlVariate() {
        return withControlVariate(true);
    }

    /**
     * Terminal builder method — Java equivalent of C++
     * {@code operator ext::shared_ptr<PricingEngine>() const}
     * (mcpathbasketengine.hpp:323-338). The C++ conversion operator has no
     * preconditions of its own; the steps/stepsPerYear xor check lives in the
     * {@link MCPathBasketEngine} constructor (C++ lines 125-130), which this
     * method invokes. Forwards every named parameter in the exact C++ argument
     * order (lines 327-337).
     */
    public PricingEngine value() {
        // C++ mcpathbasketengine.hpp:327-337
        return new MCPathBasketEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, controlVariate_,
                samples_, tolerance_, maxSamples_, seed_);
    }
}
