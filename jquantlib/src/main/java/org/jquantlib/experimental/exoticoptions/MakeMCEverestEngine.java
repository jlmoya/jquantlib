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
 Copyright (C) 2008 Master IMAFA - Polytech'Nice Sophia - Universite de Nice Sophia Antipolis
*/

package org.jquantlib.experimental.exoticoptions;

import org.jquantlib.QL;
import org.jquantlib.model.shortrate.StochasticProcessArray;
import org.jquantlib.pricingengines.McSimulation;

/**
 * Fluent builder for {@link MCEverestEngine}.
 *
 * <p>Java port of {@code QuantLib v1.42.1
 * ql/experimental/exoticoptions/mceverestengine.hpp}'s
 * {@code MakeMCEverestEngine<RNG,S>} factory (Phase 5e.5b-CFC-d-15).
 * Specialised for {@code RNG = PseudoRandom} (Mersenne-Twister +
 * InverseCumulativeNormal); quasi-random variants are deferred to Phase
 * 5e.5b-CFC-d-15b.
 *
 * <h3>Java port deviations from C++ v1.42.1</h3>
 * <ul>
 *  <li>{@code operator ext::shared_ptr<PricingEngine>()} → {@link #value()}
 *      (matches the JQuantLib {@code MakeCapFloor} / {@code MakeOIS}
 *      idiom).</li>
 *  <li>{@code Null<Size>()} → {@link McSimulation#NULL_SAMPLES}
 *      ({@code Integer.MAX_VALUE}).</li>
 *  <li>{@code Null<Real>()} → {@link McSimulation#NULL_TOLERANCE}
 *      ({@link Double#NaN}).</li>
 * </ul>
 *
 * <p>Source: {@code ql/experimental/exoticoptions/mceverestengine.hpp}
 * v1.42.1 @ {@code 099987f0ca}.
 */
public class MakeMCEverestEngine {

    //
    // private fields
    //

    private final StochasticProcessArray process_;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;


    //
    // public constructors
    //

    public MakeMCEverestEngine(final StochasticProcessArray process) {
        this.process_ = process;
    }


    //
    // named-parameter setters (return this for chaining)
    //

    public MakeMCEverestEngine withSteps(final int steps) {
        this.steps_ = steps;
        return this;
    }

    public MakeMCEverestEngine withStepsPerYear(final int steps) {
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCEverestEngine withBrownianBridge(final boolean brownianBridge) {
        this.brownianBridge_ = brownianBridge;
        return this;
    }

    /** Convenience: {@code withBrownianBridge(true)}. */
    public MakeMCEverestEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCEverestEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    /** Convenience: {@code withAntitheticVariate(true)}. */
    public MakeMCEverestEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCEverestEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "tolerance already set");
        this.samples_ = samples;
        return this;
    }

    public MakeMCEverestEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples already set");
        // PseudoRandom (MT) allows error estimate; nothing to gate here
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCEverestEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCEverestEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }


    //
    // build (Java analogue of C++ conversion operator)
    //

    /**
     * Java analogue of C++ {@code operator ext::shared_ptr<PricingEngine>()}.
     */
    public MCEverestEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES
                || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "number of steps not given");
        QL.require(steps_ == McSimulation.NULL_SAMPLES
                || stepsPerYear_ == McSimulation.NULL_SAMPLES,
                "number of steps overspecified");
        return new MCEverestEngine(
                process_,
                steps_,
                stepsPerYear_,
                brownianBridge_,
                antithetic_,
                samples_,
                tolerance_,
                maxSamples_,
                seed_);
    }
}
