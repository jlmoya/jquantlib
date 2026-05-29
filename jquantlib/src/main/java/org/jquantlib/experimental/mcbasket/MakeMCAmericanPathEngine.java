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
 Copyright (C) 2009 Andrea Odetti

 This file is part of QuantLib, a free-software/open-source library
 for financial quantitative analysts and developers - http://quantlib.org/
*/

package org.jquantlib.experimental.mcbasket;

import org.jquantlib.QL;
import org.jquantlib.math.Constants;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;

/**
 * Monte Carlo American basket-option engine factory (fluent builder).
 *
 * <p>Java port of C++ QuantLib v1.42.1
 * {@code ql/experimental/mcbasket/mcamericanpathengine.hpp}::
 * {@code template<class RNG = PseudoRandom> class MakeMCAmericanPathEngine}
 * (lines 62-85, builder bodies 164-269). Pinned commit
 * {@code 099987f0ca2c11c505dc4348cdb9ce01a598e1e5}.
 *
 * <p>The C++ class is templated on {@code <RNG = PseudoRandom>}; the Java port
 * specialises {@code RNG} to {@code PseudoRandom} (Mersenne-Twister +
 * InverseCumulativeNormal), consistent with the already-ported
 * {@link MCAmericanPathEngine}, which is itself RNG-collapsed. The terminal
 * conversion {@code operator ext::shared_ptr<PricingEngine>() const} (C++
 * lines 249-269) is realised as the {@link #value()} method, matching the
 * established JQuantLib builder idiom
 * ({@link org.jquantlib.pricingengines.basket.MakeMCAmericanBasketEngine#value()},
 * {@link org.jquantlib.pricingengines.vanilla.MakeMCAmericanEngine#value()}).
 *
 * <p>C++ defaults (constructor lines 164-170): {@code brownianBridge = false},
 * {@code antithetic = false}, {@code controlVariate = false},
 * {@code steps = Null<Size>()}, {@code stepsPerYear = Null<Size>()},
 * {@code samples = Null<Size>()}, {@code maxSamples = Null<Size>()},
 * {@code calibrationSamples = Null<Size>()}, {@code tolerance = Null<Real>()},
 * {@code seed = 0}. In Java {@code Null<Size>()} maps to
 * {@link McSimulation#NULL_SAMPLES} ({@code Integer.MAX_VALUE}) and
 * {@code Null<Real>()} to {@link McSimulation#NULL_TOLERANCE} ({@code NaN}).
 *
 * @see MCAmericanPathEngine
 */
public class MakeMCAmericanPathEngine {

    private final StochasticProcessArray process_;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private boolean controlVariate_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private int calibrationSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    // C++ mcamericanpathengine.hpp:165-170 — explicit ctor takes the process.
    public MakeMCAmericanPathEngine(final StochasticProcessArray process) {
        QL.require(process != null, "null process array");
        this.process_ = process;
    }

    // C++ mcamericanpathengine.hpp:172-177
    public MakeMCAmericanPathEngine withSteps(final int steps) {
        this.steps_ = steps;
        return this;
    }

    // C++ mcamericanpathengine.hpp:179-184
    public MakeMCAmericanPathEngine withStepsPerYear(final int steps) {
        this.stepsPerYear_ = steps;
        return this;
    }

    // C++ mcamericanpathengine.hpp:186-191 — withBrownianBridge(bool b = true)
    public MakeMCAmericanPathEngine withBrownianBridge(final boolean brownianBridge) {
        this.brownianBridge_ = brownianBridge;
        return this;
    }

    public MakeMCAmericanPathEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    // C++ mcamericanpathengine.hpp:193-198 — withAntitheticVariate(bool b = true)
    public MakeMCAmericanPathEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCAmericanPathEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    // C++ mcamericanpathengine.hpp:200-205 — withControlVariate(bool b = true)
    public MakeMCAmericanPathEngine withControlVariate(final boolean b) {
        this.controlVariate_ = b;
        return this;
    }

    public MakeMCAmericanPathEngine withControlVariate() {
        return withControlVariate(true);
    }

    // C++ mcamericanpathengine.hpp:207-214 — guards "tolerance already set"
    public MakeMCAmericanPathEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }

    // C++ mcamericanpathengine.hpp:216-226 — guards "number of samples already
    // set" and that the RNG policy allows an error estimate. PseudoRandom
    // always allows an error estimate, so the second guard is a no-op here.
    public MakeMCAmericanPathEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES, "number of samples already set");
        this.tolerance_ = tolerance;
        return this;
    }

    // C++ mcamericanpathengine.hpp:228-233
    public MakeMCAmericanPathEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    // C++ mcamericanpathengine.hpp:235-240
    public MakeMCAmericanPathEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    // C++ mcamericanpathengine.hpp:242-247
    public MakeMCAmericanPathEngine withCalibrationSamples(final int samples) {
        this.calibrationSamples_ = samples;
        return this;
    }

    /**
     * Terminal builder method — Java equivalent of C++
     * {@code operator ext::shared_ptr<PricingEngine>() const}
     * (mcamericanpathengine.hpp:249-269). Enforces the C++ steps/stepsPerYear
     * xor precondition (lines 253-256), then constructs the
     * {@link MCAmericanPathEngine} forwarding every named parameter in the
     * exact C++ argument order (lines 257-268).
     */
    public PricingEngine value() {
        // C++ mcamericanpathengine.hpp:253-254
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "number of steps not given");
        // C++ mcamericanpathengine.hpp:255-256
        QL.require(steps_ == McSimulation.NULL_SAMPLES || stepsPerYear_ == McSimulation.NULL_SAMPLES,
                "number of steps overspecified");
        // C++ mcamericanpathengine.hpp:257-268
        return new MCAmericanPathEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, controlVariate_,
                samples_, tolerance_, maxSamples_, seed_, calibrationSamples_);
    }
}
