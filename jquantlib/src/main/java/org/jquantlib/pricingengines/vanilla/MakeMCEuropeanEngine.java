/*
 Copyright (C) 2026 JQuantLib migration contributors.

 This source code is release under the BSD License.
 See LICENSE.TXT in the project root for licence terms.

 JQuantLib is based on QuantLib. http://quantlib.org/
 When applicable, the original copyright notice follows this notice.
 */

/*
 Copyright (C) 2003 Ferdinando Ametrano
 Copyright (C) 2000, 2001, 2002, 2003 RiskMap srl
 Copyright (C) 2007 StatPro Italia srl
*/

package org.jquantlib.pricingengines.vanilla;

import org.jquantlib.QL;
import org.jquantlib.pricingengines.McSimulation;
import org.jquantlib.pricingengines.PricingEngine;
import org.jquantlib.processes.GeneralizedBlackScholesProcess;

/**
 * Fluent builder for {@link MCEuropeanEngine}.
 *
 * <p>Java port of QuantLib v1.42.1 {@code MakeMCEuropeanEngine}
 * (ql/pricingengines/vanilla/mceuropeanengine.hpp). The C++ class is a template parameterised by an {@code RNG} traits
 * type (PseudoRandom or LowDiscrepancy) and a {@code Statistics} accumulator; the Java {@link MCEuropeanEngine} is
 * specialised for {@code RNG=PseudoRandom} only (the quasi-random variant is deferred — see {@code MCEuropeanEngine}
 * JavaDoc).
 *
 * <p>Phase 2 L3-D port.
 *
 * @see MCEuropeanEngine
 */
public class MakeMCEuropeanEngine {

    private final GeneralizedBlackScholesProcess process_;
    private boolean antithetic_ = false;
    private boolean brownianBridge_ = false;
    private int steps_ = McSimulation.NULL_SAMPLES;
    private int stepsPerYear_ = McSimulation.NULL_SAMPLES;
    private int samples_ = McSimulation.NULL_SAMPLES;
    private int maxSamples_ = McSimulation.NULL_SAMPLES;
    private double tolerance_ = McSimulation.NULL_TOLERANCE;
    private long seed_ = 0L;

    public MakeMCEuropeanEngine(final GeneralizedBlackScholesProcess process) {
        QL.require(process != null, "null GBS process");
        this.process_ = process;
    }

    public MakeMCEuropeanEngine withSteps(final int steps) {
        QL.require(stepsPerYear_ == McSimulation.NULL_SAMPLES, "number of steps per year already set");
        this.steps_ = steps;
        return this;
    }

    public MakeMCEuropeanEngine withStepsPerYear(final int steps) {
        QL.require(steps_ == McSimulation.NULL_SAMPLES, "number of steps already set");
        this.stepsPerYear_ = steps;
        return this;
    }

    public MakeMCEuropeanEngine withBrownianBridge(final boolean b) {
        this.brownianBridge_ = b;
        return this;
    }

    public MakeMCEuropeanEngine withBrownianBridge() {
        return withBrownianBridge(true);
    }

    public MakeMCEuropeanEngine withAntitheticVariate(final boolean b) {
        this.antithetic_ = b;
        return this;
    }

    public MakeMCEuropeanEngine withAntitheticVariate() {
        return withAntitheticVariate(true);
    }

    public MakeMCEuropeanEngine withSamples(final int samples) {
        QL.require(Double.isNaN(tolerance_), "number of samples and absolute tolerance are mutually exclusive");
        this.samples_ = samples;
        return this;
    }

    public MakeMCEuropeanEngine withAbsoluteTolerance(final double tolerance) {
        QL.require(samples_ == McSimulation.NULL_SAMPLES,
                "number of samples and absolute tolerance are mutually exclusive");
        this.tolerance_ = tolerance;
        return this;
    }

    public MakeMCEuropeanEngine withMaxSamples(final int samples) {
        this.maxSamples_ = samples;
        return this;
    }

    public MakeMCEuropeanEngine withSeed(final long seed) {
        this.seed_ = seed;
        return this;
    }

    /**
     * Build the configured {@link MCEuropeanEngine}. At least one of {@link #withSteps(int)} /
     * {@link #withStepsPerYear(int)} must have been called.
     */
    public PricingEngine value() {
        QL.require(steps_ != McSimulation.NULL_SAMPLES || stepsPerYear_ != McSimulation.NULL_SAMPLES,
                "no time steps provided");
        return new MCEuropeanEngine(process_, steps_, stepsPerYear_, brownianBridge_, antithetic_, samples_,
                tolerance_, maxSamples_, seed_);
    }
}
