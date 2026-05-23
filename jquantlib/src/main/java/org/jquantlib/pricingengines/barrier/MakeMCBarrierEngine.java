/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2003 Neil Firth
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2003, 2004, 2005 StatPro Italia srl
*/

package org.jquantlib.pricingengines.barrier;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builder for {@link MCBarrierEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCBarrierEngine}
 * (ql/pricingengines/barrier/mcbarrierengine.hpp). Phase 2 L3-D port.
 *
 * @see MCBarrierEngine
 */
public class MakeMCBarrierEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean brownianBridge_ = false;
    private boolean antithetic_ = false;
    private boolean biased_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCBarrierEngine(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "null GBS process");
        this.process_ = process;
    }

    public MakeMCBarrierEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES, "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }

    public MakeMCBarrierEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES, "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCBarrierEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCBarrierEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCBarrierEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCBarrierEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCBarrierEngine withBias(final boolean b) {
        this.biased_ = b;
        return this;
    }

    public MakeMCBarrierEngine withBias() {
        return withBias(true);
    }

    public MakeMCBarrierEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCBarrierEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCBarrierEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCBarrierEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        return new MCBarrierEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, samples_,
                tolerance_, maxSamples_, biased_, seed_);
    }
}
