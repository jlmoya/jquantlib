/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2008 Yee Man Chan
 Copyright (C) 2014 Klaus Spanderen
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GjrGarchProcess;

/**
 * Fluent builder for {@link MCEuropeanGjrGarchEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCEuropeanGJRGARCHEngine}
 * (ql/pricingengines/vanilla/mceuropeangjrgarchengine.hpp). Phase 2 L3-D port.
 *
 * @see MCEuropeanGjrGarchEngine
 */
public class MakeMCEuropeanGJRGARCHEngine {

    private final GjrGarchProcess process_;
    private boolean antithetic_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCEuropeanGJRGARCHEngine(final GjrGarchProcess process) {
        QL.require(process != null, "null GJR-GARCH process");
        this.process_ = process;
    }

    public MakeMCEuropeanGJRGARCHEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES, "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }

    public MakeMCEuropeanGJRGARCHEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES, "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCEuropeanGJRGARCHEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCEuropeanGJRGARCHEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCEuropeanGJRGARCHEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCEuropeanGJRGARCHEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCEuropeanGJRGARCHEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCEuropeanGJRGARCHEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        return new MCEuropeanGjrGarchEngine(process_, steps_, stepsPerYear_, antithetic_, samples_,
                tolerance_, maxSamples_, seed_);
    }
}
