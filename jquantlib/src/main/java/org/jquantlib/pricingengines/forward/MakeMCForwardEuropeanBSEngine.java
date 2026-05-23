/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2020 Jack Gillett
*/

package org.jquantlib.pricingengines.forward;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builder for {@link MCForwardEuropeanBSEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCForwardEuropeanBSEngine}
 * (ql/pricingengines/forward/mcforwardeuropeanbsengine.hpp). Phase 2 L3-D port.
 *
 * @see MCForwardEuropeanBSEngine
 */
public class MakeMCForwardEuropeanBSEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean antithetic_ = false;
    private boolean brownianBridge_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCForwardEuropeanBSEngine(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "null GBS process");
        this.process_ = process;
    }

    public MakeMCForwardEuropeanBSEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES, "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }

    public MakeMCForwardEuropeanBSEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES, "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCForwardEuropeanBSEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCForwardEuropeanBSEngine withBrownianBridge() {
        return withBrownianBridge(false);
    }

    public MakeMCForwardEuropeanBSEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCForwardEuropeanBSEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCForwardEuropeanBSEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCForwardEuropeanBSEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCForwardEuropeanBSEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCForwardEuropeanBSEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        return new MCForwardEuropeanBSEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, samples_,
                tolerance_, maxSamples_, seed_);
    }
}
